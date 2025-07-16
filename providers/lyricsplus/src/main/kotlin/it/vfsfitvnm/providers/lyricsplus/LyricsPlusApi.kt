package it.vfsfitvnm.providers.lyricsplus

import it.vfsfitvnm.providers.lyricsplus.models.LyricsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LyricsPlusApi {
    @GET("v2/lyrics/get")
    suspend fun getLyrics(
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("album") album: String? = null,
        @Query("duration") duration: Int? = null,
        @Query("source") source: String = "musixmatch-word"
    ): LyricsResponse
}
