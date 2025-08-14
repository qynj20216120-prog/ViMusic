@file:Suppress("JSON_FORMAT_REDUNDANT")

package it.vfsfitvnm.providers.lyricsplus

import it.vfsfitvnm.providers.lyricsplus.models.LyricLine
import it.vfsfitvnm.providers.lyricsplus.models.LyricWord
import it.vfsfitvnm.providers.lyricsplus.models.LyricsResponse
import io.ktor.client.*
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object LyricsPlus {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun fetchLyrics(
        baseUrl: String,
        title: String,
        artist: String,
        album: String? = null,
    ): List<LyricLine>? {
        val url = "$baseUrl/v2/lyrics/get"

        return try {
            // First attempt: Request lyrics with the album
            makeLyricsRequest(url, title, artist, album)
        } catch (e: ClientRequestException) {
            // If it fails with a 404 and an album was provided, try again without it
            if (e.response.status == HttpStatusCode.NotFound && album != null) {
                try {
                    // Second attempt: Request lyrics without the album
                    makeLyricsRequest(url, title, artist, null)
                } catch (_: Exception) {
                    null // Return null if the second attempt also fails
                }
            } else {
                null // Return null for other HTTP errors
            }
        } catch (_: Exception) {
            null // Return null for any other type of exception (e.g., network issues)
        }
    }

    private suspend fun makeLyricsRequest(
        url: String,
        title: String,
        artist: String,
        album: String?
    ): List<LyricLine> {
        val response: HttpResponse = client.get(url) {
            parameter("title", title)
            parameter("artist", artist)
            parameter("album", album)
        }

        val responseText = response.bodyAsText()

        val body = Json { ignoreUnknownKeys = true }
            .decodeFromString<LyricsResponse>(responseText)

        return body.lyrics.map { line ->
            LyricLine(
                fullText = line.text,
                startTimeMs = line.time,
                durationMs = line.duration,
                words = line.syllabus.map {
                    LyricWord(
                        text = it.text,
                        startTimeMs = it.time,
                        durationMs = it.duration
                    )
                }
            )
        }
    }
}
