package it.vfsfitvnm.providers.innertube.models


import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import it.vfsfitvnm.providers.innertube.NewPipeManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.exceptions.ParsingException
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
    @Transient
    val context: Context? = null,
    @Transient
    val cpn: String? = null
) {

    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
        val errorScreen: ErrorScreen? = null
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig?
    ) {
        @Serializable
        data class AudioConfig(
            internal val loudnessDb: Double?,
            internal val perceptualLoudnessDb: Double?
        ) {
            val normalizedLoudnessDb: Float?
                get() = (loudnessDb ?: perceptualLoudnessDb?.plus(7))?.plus(7)?.toFloat()
        }
    }

    @Serializable
    data class StreamingData(
        val adaptiveFormats: List<AdaptiveFormat>?,
        val expiresInSeconds: Long?
    ) {
        // In PlayerResponse.kt

        val highestQualityFormat: AdaptiveFormat?
            get() = adaptiveFormats?.let { formats ->
                // First, find the best dedicated audio-only format.
                // It prioritizes common high-quality formats (itags 251 & 140)
                // and then falls back to the one with the highest bitrate.
                val audioOnlyFormat = formats.filter { it.mimeType.startsWith("audio/") }
                    .let { audioFormats ->
                        audioFormats.findLast { it.itag == 251 || it.itag == 140 }
                            ?: audioFormats.maxByOrNull { it.bitrate ?: 0L }
                    }

                // If an audio-only format is found, return it.
                // Otherwise, fall back to finding the best combined video/audio stream.
                audioOnlyFormat ?: formats
                    .filter { it.mimeType.startsWith("video/") && it.audioQuality != null }
                    .maxByOrNull { it.bitrate ?: 0L }
            }

        @Serializable
        data class AdaptiveFormat(
            val itag: Int,
            val mimeType: String,
            val bitrate: Long?,
            val averageBitrate: Long?,
            val contentLength: Long?,
            val audioQuality: String?,
            val approxDurationMs: Long?,
            val lastModified: Long?,
            val loudnessDb: Double?,
            val audioSampleRate: Int?,
            val url: String?,
            val signatureCipher: String?
        ) {
            fun findUrl(videoId: String): String? {
                if (url != null) {
                    return url
                }

                if (signatureCipher != null) {
                    val params = parseQueryString(signatureCipher)
                    val obfuscatedSignature = params["s"] ?: throw ParsingException("Could not parse cipher signature")
                    val signatureParam = params["sp"] ?: throw ParsingException("Could not parse cipher signature parameter")
                    val urlBuilder = params["url"]?.let { URLBuilder(it) } ?: throw ParsingException("Could not parse cipher url")

                    val signatureTimestamp = NewPipeManager.getSignatureTimestamp(videoId).getOrNull()?.toString()
                        ?: "0"

                    val deobfuscatedSignature =
                        YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                            obfuscatedSignature,
                            signatureTimestamp
                        )

                    urlBuilder.parameters.append(signatureParam, deobfuscatedSignature)

                    return YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(urlBuilder.toString(), deobfuscatedSignature)
                }

                return null
            }
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String?,
        val title: String?,
        val author: String?,
        val thumbnail: Thumbnail?
    )

    @Serializable
    data class Thumbnail(
        val thumbnails: List<ThumbnailUrl>
    ) {
        @Serializable
        data class ThumbnailUrl(
            val url: String,
            val width: Int,
            val height: Int
        )
    }
}

@Serializable
data class ErrorScreen(
    val playerErrorMessageRenderer: PlayerErrorMessageRenderer? = null
) {
    @Serializable
    data class PlayerErrorMessageRenderer(
        val subreason: Runs? = null
    )
}
