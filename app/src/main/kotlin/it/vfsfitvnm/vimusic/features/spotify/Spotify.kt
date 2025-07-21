package it.vfsfitvnm.vimusic.features.spotify

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class Spotify {
    private val client = HttpClient(CIO)

    private val backendUrls = listOf(
        "https://vimusic-backend.onrender.com",
        "https://vimusic-backend-a04v.onrender.com"
    )

    /**
     * Fetches a playlist from the backend, with fallback support.
     *
     * @param playlistId The ID of the Spotify playlist to fetch.
     * @return The raw JSON string on success, or null on failure.
     */
    suspend fun getPlaylist(playlistId: String): String? {
        for (baseUrl in backendUrls) {
            val endpointUrl = "$baseUrl/playlist?playlist_id=$playlistId"
            try {
                Log.d("SpotifyFeature", "Trying to request playlist from: $endpointUrl")
                val response: HttpResponse = client.get(endpointUrl)

                if (response.status.isSuccess()) {
                    val rawJson = response.bodyAsText()
                    Log.d("SpotifyFeature", "Successfully fetched playlist from $endpointUrl")
                    return rawJson
                } else {
                    Log.w("SpotifyFeature", "Request to $endpointUrl failed with status: ${response.status.value}")
                }
            } catch (e: Exception) {
                Log.e("SpotifyFeature", "Error fetching from $endpointUrl", e)
            }
        }

        Log.e("SpotifyFeature", "Failed to fetch playlist from all available backend URLs.")
        return null
    }
}
