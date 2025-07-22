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
data class SpotifyTrack(
    val title: String,
    val artist: String
)

@Serializable
data class SpotifyPlaylistResponse(
    val tracks: List<SpotifyTrack>
)

class SpotifyPlaylistProcessor {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun processAndImportPlaylist(rawJson: String, playlistName: String) {
        try {
            val playlistResponse = json.decodeFromString<SpotifyPlaylistResponse>(rawJson)
            val tracks = playlistResponse.tracks
            Log.d("SpotifyProcessor", "Parsing complete. Starting batched search for ${tracks.size} tracks.")

            val songsToAdd = mutableListOf<Song>()

            // --- FIX: Process the full list in smaller, safer batches ---
            val batchSize = 15 // A safe number of concurrent requests.
            tracks.chunked(batchSize).forEachIndexed { index, batch ->
                Log.d("SpotifyProcessor", "Processing batch ${index + 1}...")
                coroutineScope {
                    val deferredSongsInBatch = batch.map { track ->
                        async(Dispatchers.IO) {
                            val searchQuery = "${track.title} ${track.artist}"

                            val localSong = Database.instance.search("%${track.title}%").firstOrNull()?.firstOrNull { song ->
                                song.artistsText?.contains(track.artist, ignoreCase = true) == true
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
                                    thumbnailUrl = bestMatch.thumbnail?.url,
                                    explicit = bestMatch.explicit
                                ).takeIf { it.id.isNotEmpty() }
                            } else {
                                Log.w("SpotifyProcessor", "No suitable match found for \"$searchQuery\"")
                                null
                            }
                        }
                    }

                    // Add the results from the completed batch to the main list
                    songsToAdd.addAll(deferredSongsInBatch.awaitAll().filterNotNull())
                }
                Log.d("SpotifyProcessor", "Batch ${index + 1} complete. Total songs found: ${songsToAdd.size}")
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
        } catch (e: Exception) {
            Log.e("SpotifyProcessor", "An error occurred during the import process.", e)
        }
    }

    private fun findBestMatchInResults(spotifyTrack: SpotifyTrack, candidates: List<Innertube.SongItem>): Innertube.SongItem? {
        val scoredCandidates = candidates.map { candidate ->
            candidate to calculateMatchScore(spotifyTrack, candidate)
        }

        return scoredCandidates
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun calculateMatchScore(spotifyTrack: SpotifyTrack, candidate: Innertube.SongItem): Int {
        val candidateTitle = candidate.info?.name?.lowercase() ?: return 0
        val candidateArtists = candidate.authors?.joinToString { it.name.toString() }?.lowercase() ?: ""

        val primarySpotifyArtist = spotifyTrack.artist.split(",")[0].trim().lowercase()
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

        val cleanedSpotifyTitle = spotifyTrack.title.substringBefore(" (").substringBefore(" -").trim().lowercase()
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
