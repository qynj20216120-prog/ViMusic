package it.vfsfitvnm.vimusic.features.spotify

import android.util.Log
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.models.Playlist
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.models.SongPlaylistMap
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.providers.innertube.Innertube
import it.vfsfitvnm.providers.innertube.models.bodies.SearchBody
import it.vfsfitvnm.providers.innertube.requests.searchPage
import it.vfsfitvnm.providers.innertube.utils.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SpotifyPlaylistResponse(
    val name: String,
    val description: String? = null,
    val tracks: TracksObject
)

@Serializable
data class TracksObject(
    val items: List<TrackItem>
)

@Serializable
data class TrackItem(
    val track: TrackDetails?
)

@Serializable
data class TrackDetails(
    val name: String,
    val artists: List<Artist>,
    val album: Album
)

@Serializable
data class Artist(
    val name: String
)

@Serializable
data class Album(
    val name: String
)

sealed class ImportStatus {
    data object Idle : ImportStatus()
    data class InProgress(val processed: Int, val total: Int) : ImportStatus()
    data class Complete(val imported: Int, val failed: Int, val total: Int) : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}

class SpotifyPlaylistProcessor {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun processAndImportPlaylist(
        rawJson: String,
        playlistName: String,
        unknownErrorMessage: String,
        onProgressUpdate: (ImportStatus) -> Unit
    ) {
        try {
            val playlistResponse = json.decodeFromString<SpotifyPlaylistResponse>(rawJson)
            val tracks = playlistResponse.tracks.items.mapNotNull { it.track }
            val totalTracks = tracks.size
            Log.d("SpotifyProcessor", "Parsing complete. Starting batched search for $totalTracks tracks.")

            val songsToAdd = mutableListOf<Song>()
            var processedCount = 0

            onProgressUpdate(ImportStatus.InProgress(processed = 0, total = totalTracks))

            val batchSize = 15
            tracks.chunked(batchSize).forEachIndexed { index, batch ->
                Log.d("SpotifyProcessor", "Processing batch ${index + 1}...")
                coroutineScope {
                    val deferredSongsInBatch = batch.map { track ->
                        async(Dispatchers.IO) {
                            val artistNames = track.artists.joinToString { it.name }
                            val searchQuery = "${track.name} $artistNames"
                            val localSong = Database.instance.search("%${track.name}%").firstOrNull()?.firstOrNull { song ->
                                song.artistsText?.let { artistsText ->
                                    track.artists.any { artist -> artistsText.contains(artist.name, ignoreCase = true) }
                                } == true
                            }

                            if (localSong != null) {
                                return@async localSong
                            }

                            val searchCandidates = Innertube.searchPage(
                                body = SearchBody(query = searchQuery, params = Innertube.SearchFilter.Song.value)
                            ) { content ->
                                content.musicResponsiveListItemRenderer?.let { renderer ->
                                    Innertube.SongItem.from(renderer)
                                }
                            }?.getOrNull()?.items

                            if (searchCandidates.isNullOrEmpty()) {
                                return@async null
                            }

                            val bestMatch = findBestMatchInResults(track, searchCandidates)
                            if (bestMatch != null) {
                                Song(
                                    id = bestMatch.info?.endpoint?.videoId ?: "",
                                    title = bestMatch.info?.name ?: "",
                                    artistsText = bestMatch.authors?.joinToString { it.name.toString() },
                                    durationText = bestMatch.durationText,
                                    thumbnailUrl = bestMatch.thumbnail?.url
                                ).takeIf { it.id.isNotEmpty() }
                            } else {
                                Log.w("SpotifyProcessor", "No suitable match found for \"$searchQuery\"")
                                null
                            }
                        }
                    }
                    songsToAdd.addAll(deferredSongsInBatch.awaitAll().filterNotNull())
                }

                processedCount += batch.size
                Log.d("SpotifyProcessor", "Batch ${index + 1} complete. Total songs found: ${songsToAdd.size}")
                onProgressUpdate(ImportStatus.InProgress(processed = processedCount, total = totalTracks))
            }

            Log.d("SpotifyProcessor", "All batches complete. Found ${songsToAdd.size} total songs to import.")

            transaction {
                val newPlaylist = Playlist(name = playlistName)
                val newPlaylistId = Database.instance.insert(newPlaylist)
                if (newPlaylistId != -1L) {
                    Log.d("SpotifyProcessor", "Successfully created playlist '$playlistName' with ID: $newPlaylistId")
                    songsToAdd.forEachIndexed { index, song ->
                        Database.instance.insert(song)
                        val songPlaylistMap = SongPlaylistMap(
                            songId = song.id,
                            playlistId = newPlaylistId,
                            position = index
                        )
                        Database.instance.insert(songPlaylistMap)
                    }
                    Log.d("SpotifyProcessor", "Finished importing ${songsToAdd.size} songs to '$playlistName'.")
                } else {
                    Log.e("SpotifyProcessor", "Failed to create playlist '$playlistName'. It might already exist.")
                }
            }

            val failedCount = totalTracks - songsToAdd.size
            onProgressUpdate(ImportStatus.Complete(imported = songsToAdd.size, failed = failedCount, total = totalTracks))

        } catch (e: Exception) {
            Log.e("SpotifyProcessor", "An error occurred during the import process.", e)
            onProgressUpdate(ImportStatus.Error(e.message ?: unknownErrorMessage))
        }
    }

    private fun findBestMatchInResults(spotifyTrack: TrackDetails, candidates: List<Innertube.SongItem>): Innertube.SongItem? {
        val scoredCandidates = candidates.map { candidate ->
            candidate to calculateMatchScore(spotifyTrack, candidate)
        }

        return scoredCandidates
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun calculateMatchScore(spotifyTrack: TrackDetails, candidate: Innertube.SongItem): Int {
        val candidateTitle = candidate.info?.name?.lowercase() ?: return 0
        val candidateArtists = candidate.authors?.joinToString { it.name.toString() }?.lowercase() ?: ""

        val primarySpotifyArtist = spotifyTrack.artists.firstOrNull()?.name?.trim()?.lowercase() ?: return 0
        if (!candidateArtists.contains(primarySpotifyArtist)) {
            return 0
        }

        val badKeywords = listOf(
            "cover", "remix", "live", "instrumental", "karaoke", "reaction",
            "lyrics", "unplugged", "acoustic", "reverb", "slowed"
        )
        if (badKeywords.any { candidateTitle.contains(it) }) {
            return 0
        }

        val cleanedSpotifyTitle = spotifyTrack.name.substringBefore(" (").substringBefore(" -").trim().lowercase()
        if (!candidateTitle.contains(cleanedSpotifyTitle)) {
            return 0
        }

        var score = 100
        val lengthDifference = candidateTitle.length - cleanedSpotifyTitle.length
        score -= lengthDifference

        if (candidateTitle == cleanedSpotifyTitle) {
            score += 10
        }

        return score
    }
}
