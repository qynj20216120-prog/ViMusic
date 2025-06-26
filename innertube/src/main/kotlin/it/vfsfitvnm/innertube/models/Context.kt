package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        val platform: String,
        val hl: String = "en",
        val visitorData: String = "CgtEUlRINDFjdm1YayjX1pSaBg%3D%3D",
        val androidSdkVersion: Int? = null,
        val userAgent: String? = null
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )

    companion object {
        val DefaultWeb = Context(
            client = Client(
                clientName = "WEB_REMIX",
                clientVersion = "1.20241111.01.00",
                platform = "DESKTOP",
            )
        )

        val DefaultAndroid = Context(
            client = Client(
                clientName = "ANDROID_MUSIC",
                clientVersion = "7.11.50",
                platform = "MOBILE",
                androidSdkVersion = 34,
                userAgent = "com.google.android.apps.youtube.music/7.11.50 (Linux; U; Android 14) gzip"
            )
        )

        val DefaultAgeRestrictionBypass = Context(
            client = Client(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                platform = "TV"
            )
        )

        // Add this new context for better compatibility
        val DefaultiOS = Context(
            client = Client(
                clientName = "IOS_MUSIC",
                clientVersion = "7.08.2",
                platform = "MOBILE",
                userAgent = "com.google.ios.youtubemusic/7.08.2 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"
            )
        )
    }
}