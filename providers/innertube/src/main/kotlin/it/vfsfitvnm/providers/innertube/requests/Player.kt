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

private suspend fun Innertube.tryContexts(
    body: PlayerBody,
    vararg contexts: Context
): PlayerResponse? {
    contexts.forEach { context ->
        if (!currentCoroutineContext().isActive) return null

        logger.info("Trying ${context.client.clientName} ${context.client.clientVersion} ${context.client.platform}")
        val cpn = generateNonce(16).decodeToString()
        runCatchingCancellable {
            // The network call is stored in a variable now
            val httpResponse = client.post(if (context.client.music) PLAYER_MUSIC else PLAYER) {
                setBody(
                    body.copy(
                        context = context,
                        cpn = cpn,
                        playbackContext = PlayerBody.PlaybackContext(
                            contentPlaybackContext = PlayerBody.PlaybackContext.ContentPlaybackContext(
                                // Note: This old signature logic is part of what we are replacing.
                                // It might fail, but the raw response will tell us why.
                                signatureTimestamp = getSignatureTimestamp(context)
                            )
                        )
                    )
                )

                context.apply()

                parameter("t", generateNonce(12))
                header("X-Goog-Api-Format-Version", "2")
                parameter("id", body.videoId)
            }

            val responseAsText = httpResponse.bodyAsText()

            json.decodeFromString<PlayerResponse>(responseAsText)
                .also { logger.info("Got $it") }

        }
            ?.getOrNull()
            ?.takeIf { it.isValid }
            ?.let {
                return it.copy(
                    cpn = cpn,
                    context = context
                )
            }
    }

    return null
}

private val PlayerResponse.isValid
    get() = playabilityStatus?.status == "OK" &&
        streamingData?.adaptiveFormats?.any { it.url != null || it.signatureCipher != null } == true

suspend fun Innertube.player(body: PlayerBody): Result<PlayerResponse?>? = runCatchingCancellable {
    tryContexts(
        body = body,
        Context.DefaultIOS,
        Context.DefaultWeb,
        Context.DefaultTV,
        Context.DefaultAndroidMusic
    )
}
