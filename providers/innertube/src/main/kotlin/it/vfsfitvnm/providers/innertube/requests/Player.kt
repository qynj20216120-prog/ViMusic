package it.vfsfitvnm.providers.innertube.requests

import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.util.generateNonce
import it.vfsfitvnm.providers.innertube.Innertube
import it.vfsfitvnm.providers.innertube.json
import it.vfsfitvnm.providers.innertube.models.Context
import it.vfsfitvnm.providers.innertube.models.PlayerResponse
import it.vfsfitvnm.providers.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.providers.utils.runCatchingCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Iterates through a series of client contexts (IOS, Web, etc.) to find a valid player response.
 */
private suspend fun Innertube.tryContexts(
    body: PlayerBody,
    vararg contexts: Context
): PlayerResponse? {
    for (context in contexts) {
        if (!currentCoroutineContext().isActive) return null

        logger.info("Trying ${context.client.clientName} ${context.client.clientVersion} ${context.client.platform}")

        val config = context.client.getConfiguration()
        if (config == null) {
            logger.warn("Failed to get configuration for client ${context.client.clientName}")
            continue // Skip to the next context
        }

        val cpn = generateNonce(16).decodeToString()

        val result: Result<PlayerResponse>? = runCatchingCancellable {
            val httpResponse = client.post(if (context.client.music) PLAYER_MUSIC else PLAYER) {
                setBody(
                    body.copy(
                        context = context,
                        cpn = cpn
                    )
                )
                context.apply()
                parameter("t", generateNonce(12))
                header("X-Goog-Api-Format-Version", "2")
                parameter("id", body.videoId)
            }

            val responseAsText = httpResponse.bodyAsText()
            json.decodeFromString<PlayerResponse>(responseAsText)
        }

        if (result == null) {
            logger.warn("Context ${context.client.clientName} was cancelled or returned null.")
            continue
        }

        val response = result.getOrNull()

        if (response != null && response.isValid) {
            return response.copy(cpn = cpn, context = context)
        }
    }

    return null
}

/**
 * An extension property to check if a PlayerResponse is valid for playback.
 */
private val PlayerResponse.isValid
    get() = playabilityStatus?.status == "OK" &&
        streamingData?.adaptiveFormats?.any { it.url != null || it.signatureCipher != null } == true

/**
 * Fetches the PlayerResponse for a given video by trying multiple client contexts.
 */
suspend fun Innertube.player(body: PlayerBody): Result<PlayerResponse?>? = runCatchingCancellable {
    tryContexts(
        body = body,
        Context.DefaultIOS,
        Context.DefaultWeb,
        Context.DefaultTV,
        Context.DefaultAndroid,
        Context.OnlyWeb,
        Context.WebCreator,
        Context.DefaultVR
    )
}
