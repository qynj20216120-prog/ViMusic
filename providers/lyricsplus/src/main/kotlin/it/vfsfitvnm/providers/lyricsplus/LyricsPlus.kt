package it.vfsfitvnm.providers.lyricsplus

import it.vfsfitvnm.providers.lyricsplus.models.LyricLine
import it.vfsfitvnm.providers.lyricsplus.models.LyricWord
import it.vfsfitvnm.providers.lyricsplus.models.LyricsResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

object LyricsPlus {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        duration: Int? = null
    ): List<LyricLine>? {
        val url = "https://lyricsplus.prjktla.workers.dev/v2/lyrics/get"

        return try {
            val response: HttpResponse = client.get(url) {
                parameter("title", title)
                parameter("artist", artist)
                parameter("album", album)
                parameter("duration", duration)
                parameter("source", "musixmatch-word")
            }

            val responseText = response.bodyAsText()
            println("[LyricsPlus] Raw response: $responseText")

            if (!responseText.trim().startsWith("{\"type\"")) {
                println("[LyricsPlus] Response is not a valid lyrics object. Skipping.")
                return null
            }

            val body = Json { ignoreUnknownKeys = true }
                .decodeFromString<LyricsResponse>(responseText)

            if (body.type != "Word") {
                println("[LyricsPlus] Unsupported type: ${body.type}")
                return null
            }

            body.lyrics.map { line ->
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
            }.also {
                println("[LyricsPlus] Parsed ${it.size} word-synced lines")
            }
        } catch (e: Exception) {
            println("[LyricsPlus] Exception during fetch: ${e.message}")
            null
        }
    }
}

