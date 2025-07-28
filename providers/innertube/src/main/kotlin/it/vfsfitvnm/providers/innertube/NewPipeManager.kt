package it.vfsfitvnm.providers.innertube

import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import it.vfsfitvnm.providers.innertube.models.PlayerResponse
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException

/**
 * A custom Downloader implementation that uses OkHttp to make requests.
 * This makes the module self-contained and avoids needing other Android-specific dependencies.
 */
private class NewPipeDownloaderImpl : Downloader() {
    private val client = OkHttpClient.Builder().build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val requestBuilder = okhttp3.Request.Builder()
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .url(request.url())

        request.headers().forEach { (headerName, headerValueList) ->
            requestBuilder.addHeader(headerName, headerValueList.joinToString(","))
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", request.url())
        }

        val responseBodyToReturn = response.body?.string()
        val latestUrl = response.request.url.toString()
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, latestUrl)
    }
}

/**
 * Singleton object to manage NewPipeExtractor functionality.
 * It handles its own initialization.
 */
object NewPipeManager {
    init {
        // Initialize NewPipe with our custom downloader implementation.
        NewPipe.init(NewPipeDownloaderImpl())
    }

    /**
     * Deciphers the signature and throttling parameters to return a playable stream URL.
     * @param format The AdaptiveFormat containing the signatureCipher.
     * @param videoId The ID of the video to process.
     * @return A Result containing the playable URL or an exception.
     */
    fun getStreamUrl(format: PlayerResponse.StreamingData.AdaptiveFormat, videoId: String): Result<String> =
        runCatching {
            val url = format.url ?: format.signatureCipher?.let { signatureCipher ->
                val params = parseQueryString(signatureCipher)
                val obfuscatedSignature = params["s"] ?: throw ParsingException("Could not parse cipher signature")
                val signatureParam = params["sp"] ?: throw ParsingException("Could not parse cipher signature parameter")
                val baseUrl = params["url"]?.let { URLBuilder(it) } ?: throw ParsingException("Could not parse cipher url")

                baseUrl.parameters[signatureParam] = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)
                baseUrl.toString()
            } ?: throw ParsingException("Could not find format url")

            return@runCatching YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
        }
            .onFailure { error ->
                error.printStackTrace()
            }
}
