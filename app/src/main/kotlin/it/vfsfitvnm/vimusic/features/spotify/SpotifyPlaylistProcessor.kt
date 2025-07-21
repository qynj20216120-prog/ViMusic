package it.vfsfitvnm.vimusic.features.spotify

import android.util.Log
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.models.Playlist
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.models.SongPlaylistMap
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.providers.innertube.Innertube
import it.vfsfitvnm.providers.innertube.models.MusicResponsiveListItemRenderer
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
import kotlinx.serialization.decodeFromString

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
            Log.d("SpotifyProcessor", "Parsing complete. Starting concurrent search for ${tracks.size} tracks.")

            val songsToAdd = coroutineScope {
                val deferredSongs = tracks.map { track ->
                    async(Dispatchers.IO) {
                        val searchQuery = "${track.title} ${track.artist}"

                        val localSong = Database.search("%${track.title}%").firstOrNull()?.firstOrNull { song ->
                            song.artistsText?.contains(track.artist, ignoreCase = true) == true
                        }

                        if (localSong != null) {
                            Log.d("SpotifyProcessor", "Found local match for \"$searchQuery\"")
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
                            Log.w("SpotifyProcessor", "Online search returned no results for \"$searchQuery\"")
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

                deferredSongs.awaitAll().filterNotNull()
            }

            Log.d("SpotifyProcessor", "All searches complete. Found ${songsToAdd.size} songs to import.")

            transaction {
                val newPlaylist = Playlist(name = playlistName)
                val newPlaylistId = Database.insert(newPlaylist)

                if (newPlaylistId != -1L) {
                    Log.d("SpotifyProcessor", "Successfully created playlist '$playlistName' with ID: $newPlaylistId")

                    songsToAdd.forEachIndexed { index, song ->
                        Database.insert(song)
                        val songPlaylistMap = SongPlaylistMap(
                            songId = song.id,
                            playlistId = newPlaylistId,
                            position = index
                        )
                        Database.insert(songPlaylistMap)
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

        Log.d("SpotifyProcessor", "Candidates for '${spotifyTrack.title}':")
        scoredCandidates.forEach { (candidate, score) ->
            Log.d("SpotifyProcessor", "  - Score: $score, Title: ${candidate.info?.name}")
        }

        return scoredCandidates
            .filter { (_, score) -> score > 0 } // Any song with a score of 0 is invalid
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun calculateMatchScore(spotifyTrack: SpotifyTrack, candidate: Innertube.SongItem): Int {
        val candidateTitle = candidate.info?.name?.lowercase() ?: return 0
        val candidateArtists = candidate.authors?.joinToString { it.name.toString() }?.lowercase() ?: ""

        // --- Gate #1: Check Artist ---
        val primarySpotifyArtist = spotifyTrack.artist.split(",")[0].trim().lowercase()
        if (!candidateArtists.contains(primarySpotifyArtist)) {
            return 0 // DISQUALIFIED
        }

        // --- Gate #2: Check for Bad Keywords ---
        val badKeywords = listOf(
            "cover", "remix", "live", "instrumental", "karaoke", "reaction",
             "lyrics", "unplugged", "acoustic", "reverb", "slowed"
        )
        if (badKeywords.any { candidateTitle.contains(it) }) {
            return 0 // DISQUALIFIED
        }

        // --- Gate #3: Check if Title is Contained ---
        val cleanedSpotifyTitle = spotifyTrack.title.substringBefore(" (").substringBefore(" -").trim().lowercase()
        if (!candidateTitle.contains(cleanedSpotifyTitle)) {
            return 0 // DISQUALIFIED
        }

        // --- If all gates passed, calculate a positive score ---
        var score = 100

        // Penalize extra characters in the title. A perfect match gets no penalty.
        val lengthDifference = candidateTitle.length - cleanedSpotifyTitle.length
        score -= lengthDifference

        // Give a bonus for a perfect, exact match.
        if (candidateTitle == cleanedSpotifyTitle) {
            score += 10
        }

        return score
    }
}
