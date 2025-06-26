package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import kotlinx.serialization.Serializable

suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    // Try with updated Android context first
    val response = client.post(player) {
        setBody(body.copy(context = Context.DefaultAndroid))
        mask("playabilityStatus.status,videoDetails.videoId,streamingData.adaptiveFormats,streamingData.hlsManifestUrl,playerConfig.audioConfig")
    }.body<PlayerResponse>()

    if (response.playabilityStatus?.status == "OK") {
        return@runCatchingNonCancellable response
    }

    // If Android fails, try iOS context
    val iOSResponse = client.post(player) {
        setBody(body.copy(context = Context.DefaultiOS))
        mask("playabilityStatus.status,videoDetails.videoId,streamingData.adaptiveFormats,streamingData.hlsManifestUrl,playerConfig.audioConfig")
    }.body<PlayerResponse>()

    if (iOSResponse.playabilityStatus?.status == "OK") {
        return@runCatchingNonCancellable iOSResponse
    }

    // Fallback to age restriction bypass if both fail
    @Serializable
    data class AudioStream(
        val url: String,
        val bitrate: Long
    )

    @Serializable
    data class PipedResponse(
        val audioStreams: List<AudioStream>
    )

    val safePlayerResponse = client.post(player) {
        setBody(
            body.copy(
                context = Context.DefaultAgeRestrictionBypass.copy(
                    thirdParty = Context.ThirdParty(
                        embedUrl = "https://www.youtube.com/watch?v=${body.videoId}"
                    )
                ),
            )
        )
        mask("playabilityStatus.status,videoDetails.videoId,streamingData.adaptiveFormats,streamingData.hlsManifestUrl,playerConfig.audioConfig")
    }.body<PlayerResponse>()

    if (safePlayerResponse.playabilityStatus?.status == "OK") {
        return@runCatchingNonCancellable safePlayerResponse
    }

    // Final fallback to Piped API
    try {
        val audioStreams = client.get("https://watchapi.whatever.social/streams/${body.videoId}") {
            contentType(ContentType.Application.Json)
        }.body<PipedResponse>().audioStreams

        return@runCatchingNonCancellable safePlayerResponse.copy(
            streamingData = safePlayerResponse.streamingData?.copy(
                adaptiveFormats = safePlayerResponse.streamingData.adaptiveFormats?.map { adaptiveFormat ->
                    adaptiveFormat.copy(
                        url = audioStreams.find { it.bitrate == adaptiveFormat.bitrate }?.url
                    )
                }
            )
        )
    } catch (e: Exception) {
        // Return the best response we got, even if it failed
        return@runCatchingNonCancellable response
    }
}