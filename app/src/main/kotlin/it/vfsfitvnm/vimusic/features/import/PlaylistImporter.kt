package it.vfsfitvnm.vimusic.features.import

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

// A simple, universal data class for any song from any source
data class SongImportInfo(
    val title: String,
    val artist: String,
)

// The generic status class remains the same
sealed class ImportStatus {
    data object Idle : ImportStatus()
    data class InProgress(val processed: Int, val total: Int) : ImportStatus()
    data class Complete(val imported: Int, val failed: Int, val total: Int) : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}

class PlaylistImporter {
    suspend fun import(
        songList: List<SongImportInfo>,
        playlistName: String,
        unknownErrorMessage: String,
        onProgressUpdate: (ImportStatus) -> Unit
    ) {
        try {
            val totalTracks = songList.size
            Log.d("PlaylistImporter", "Starting import for $totalTracks tracks.")

            val songsToAdd = mutableListOf<Song>()
            var processedCount = 0

            onProgressUpdate(ImportStatus.InProgress(processed = 0, total = totalTracks))

            val batchSize = 15
            songList.chunked(batchSize).forEachIndexed { index, batch ->
                Log.d("PlaylistImporter", "Processing batch ${index + 1}...")
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
                                    thumbnailUrl = bestMatch.thumbnail?.url
                                ).takeIf { it.id.isNotEmpty() }
                            } else {
                                Log.w("PlaylistImporter", "No suitable match found for \"$searchQuery\"")
                                null
                            }
                        }
                    }
                    songsToAdd.addAll(deferredSongsInBatch.awaitAll().filterNotNull())
                }

                processedCount += batch.size
                Log.d("PlaylistImporter", "Batch ${index + 1} complete. Total songs found: ${songsToAdd.size}")
                onProgressUpdate(ImportStatus.InProgress(processed = processedCount, total = totalTracks))
            }

            Log.d("PlaylistImporter", "All batches complete. Found ${songsToAdd.size} total songs to import.")

            transaction {
                val newPlaylist = Playlist(name = playlistName)
                val newPlaylistId = Database.instance.insert(newPlaylist)
                if (newPlaylistId != -1L) {
                    songsToAdd.forEachIndexed { index, song ->
                        Database.instance.insert(song)
                        Database.instance.insert(
                            SongPlaylistMap(
                                songId = song.id,
                                playlistId = newPlaylistId,
                                position = index
                            )
                        )
                    }
                }
            }

            val failedCount = totalTracks - songsToAdd.size
            onProgressUpdate(ImportStatus.Complete(imported = songsToAdd.size, failed = failedCount, total = totalTracks))

        } catch (e: Exception) {
            Log.e("PlaylistImporter", "An error occurred during the import process.", e)
            onProgressUpdate(ImportStatus.Error(e.message ?: unknownErrorMessage))
        }
    }

    private fun findBestMatchInResults(importTrack: SongImportInfo, candidates: List<Innertube.SongItem>): Innertube.SongItem? {
        val scoredCandidates = candidates.map { candidate ->
            candidate to calculateMatchScore(importTrack, candidate)
        }

        return scoredCandidates
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun calculateMatchScore(importTrack: SongImportInfo, candidate: Innertube.SongItem): Int {
        val candidateTitle = candidate.info?.name?.lowercase() ?: return 0
        val candidateArtists = candidate.authors?.joinToString { it.name.toString() }?.lowercase() ?: ""
        val primaryImportArtist = importTrack.artist.split(",")[0].trim().lowercase()

        if (!candidateArtists.contains(primaryImportArtist)) {
            return 0
        }

        val badKeywords = listOf("cover", "remix", "live", "instrumental", "karaoke", "reaction", "lyrics", "unplugged", "acoustic", "reverb", "slowed")
        if (badKeywords.any { candidateTitle.contains(it) }) {
            return 0
        }

        val cleanedImportTitle = importTrack.title.substringBefore(" (").substringBefore(" -").trim().lowercase()
        if (!candidateTitle.contains(cleanedImportTitle)) {
            return 0
        }

        var score = 100
        val lengthDifference = candidateTitle.length - cleanedImportTitle.length
        score -= lengthDifference
        if (candidateTitle == cleanedImportTitle) score += 10
        return score
    }
}
