package it.vfsfitvnm.vimusic.features.spotify

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Serializable
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("public") val isPublic: Boolean,
)

@Serializable
data class SpotifyUserPlaylistsResponse(val items: List<SpotifyPlaylist>)

@Serializable
data class SpotifyUserProfile(
    @SerialName("display_name") val displayName: String,
    val id: String
)

class Spotify(private val context: Context) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val profilePreferences: SharedPreferences = context.getSharedPreferences("spotify_profile", Context.MODE_PRIVATE)

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val tokenFile = File(context.filesDir, "spotify_token.txt")

    private val encryptedFile = EncryptedFile.Builder(
        context,
        tokenFile,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    private var accessToken: String? = null
        get() {
            if (field != null) return field
            return try {
                val token = encryptedFile.openFileInput().bufferedReader().use { it.readText() }
                field = token
                token
            } catch (e: Exception) {
                null
            }
        }
        private set(value) {
            if (value == null) {
                if (tokenFile.exists()) {
                    tokenFile.delete()
                }
            } else {
                try {
                    encryptedFile.openFileOutput().bufferedWriter().use { it.write(value) }
                } catch (e: Exception) {
                    Log.e("SpotifyProfile", "Failed to write token to file", e)
                }
            }
            field = value
        }

    fun isLoggedIn(): Boolean = tokenFile.exists() && tokenFile.length() > 0

    fun getAuthenticationRequest(): AuthorizationRequest {
        return AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
            .setScopes(arrayOf("user-read-private", "playlist-read-private", "playlist-read-collaborative"))
            .build()
    }

    fun setToken(token: String) {
        this.accessToken = token
    }

    fun logout() {
        this.accessToken = null
        profilePreferences.edit().remove("display_name").apply()
    }

    suspend fun fetchAndSaveUserProfile(): Result<Unit> = runCatching {
        if (!isLoggedIn()) error("Not logged in")

        val userProfile: SpotifyUserProfile = client.get("https://api.spotify.com/v1/me") {
            bearerAuth(accessToken!!)
        }.body()

        profilePreferences.edit().putString("display_name", userProfile.displayName).apply()
    }.onFailure { error ->
        Log.e("SpotifyProfile", "Failed to fetch profile", error)
    }

    fun getUsername(): String? {
        return profilePreferences.getString("display_name", null)
    }

    suspend fun getUserPlaylists(): Result<List<SpotifyPlaylist>> = runCatching {
        if (!isLoggedIn()) error("Not logged in")
        val response: SpotifyUserPlaylistsResponse = client.get("https://api.spotify.com/v1/me/playlists") {
            bearerAuth(accessToken!!)
            parameter("limit", 50)
        }.body()
        return@runCatching response.items
    }

    suspend fun getPlaylist(playlistId: String): String? = runCatching {
        if (!isLoggedIn()) return@runCatching null

        val jsonParser = Json { ignoreUnknownKeys = true }

        // Step 1: Get the playlist's basic info (name, description)
        val playlistInfoText = client.get("https://api.spotify.com/v1/playlists/$playlistId") {
            bearerAuth(accessToken!!)
            parameter("fields", "name,description")
        }.bodyAsText()
        val playlistInfoObject = jsonParser.parseToJsonElement(playlistInfoText).jsonObject

        // Step 2: Fetch all tracks using the dedicated tracks endpoint, handling pagination
        val allTrackItems = mutableListOf<JsonElement>()
        var offset = 0
        val limit = 100
        var total: Int

        do {
            val tracksPageText = client.get("https://api.spotify.com/v1/playlists/$playlistId/tracks") {
                bearerAuth(accessToken!!)
                parameter("fields", "items(track(name,artists(name),album(name))),total")
                parameter("limit", limit)
                parameter("offset", offset)
            }.bodyAsText()

            val tracksPageObject = jsonParser.parseToJsonElement(tracksPageText).jsonObject
            tracksPageObject["items"]?.jsonArray?.let { allTrackItems.addAll(it) }

            total = tracksPageObject["total"]?.jsonPrimitive?.intOrNull ?: 0
            offset += limit

            Log.d("SpotifyPlaylist", "Fetched ${allTrackItems.size} tracks so far, total: $total")
        } while (offset < total)

        Log.d("SpotifyPlaylist", "Completed fetching playlist. Total tracks: ${allTrackItems.size}")

        // Step 3: Combine the playlist info with the complete list of tracks
        val finalTracksObject = JsonObject(mapOf(
            "items" to JsonArray(allTrackItems),
            "total" to JsonElement.serializer().let { jsonParser.parseToJsonElement(total.toString()) }
        ))
        val finalPlaylistObject = JsonObject(playlistInfoObject + ("tracks" to finalTracksObject))

        return@runCatching finalPlaylistObject.toString()

    }.onFailure { error ->
        Log.e("SpotifyPlaylist", "Failed to fetch playlist", error)
    }.getOrNull()

    companion object {
        const val CLIENT_ID = "12a12f1a6d834753b8143c91cb425bec"
        const val REDIRECT_URI = "vimusic://spotify-callback"
        const val AUTH_REQUEST_CODE = 1337
    }
}
