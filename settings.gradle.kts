enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }

    versionCatalogs {
        create("libs") {
            // Keep modern Kotlin for Java 21 compatibility
            version("kotlin", "1.7.20")
            plugin("kotlin-serialization","org.jetbrains.kotlin.plugin.serialization").versionRef("kotlin")

            // Rest stays the same as before...
            library("kotlin-coroutines","org.jetbrains.kotlinx", "kotlinx-coroutines-core").version("1.6.4")


            // Add KSP plugin
            version("ksp", "1.7.20-1.0.8")
            plugin("ksp", "com.google.devtools.ksp").versionRef("ksp")

            library("kotlin-coroutines","org.jetbrains.kotlinx", "kotlinx-coroutines-core").version("1.6.4")

            // Use ORIGINAL Compose versions from the code
            version("compose-compiler", "1.3.2")
            version("compose", "1.3.0-rc01")

            library("compose-foundation", "androidx.compose.foundation", "foundation").versionRef("compose")
            library("compose-ui", "androidx.compose.ui", "ui").versionRef("compose")
            library("compose-ui-util", "androidx.compose.ui", "ui-util").versionRef("compose")
            library("compose-ripple", "androidx.compose.material", "material-ripple").versionRef("compose")

            library("compose-shimmer", "com.valentinilk.shimmer", "compose-shimmer").version("1.0.3")
            library("compose-activity", "androidx.activity", "activity-compose").version("1.7.0-alpha01")
            library("compose-coil", "io.coil-kt", "coil-compose").version("2.2.2")

            // Updated Room for KSP support - but compatible version
            version("room", "2.5.0-beta01")
            library("room", "androidx.room", "room-ktx").versionRef("room")
            library("room-compiler", "androidx.room", "room-compiler").versionRef("room")

            // Use original Media3 version
            version("media3", "1.0.0-beta03")
            library("exoplayer", "androidx.media3", "media3-exoplayer").versionRef("media3")

            // Use original Ktor version
            version("ktor", "2.1.2")
            library("ktor-client-core", "io.ktor", "ktor-client-core").versionRef("ktor")
            library("ktor-client-cio", "io.ktor", "ktor-client-okhttp").versionRef("ktor")
            library("ktor-client-content-negotiation", "io.ktor", "ktor-client-content-negotiation").versionRef("ktor")
            library("ktor-client-encoding", "io.ktor", "ktor-client-encoding").versionRef("ktor")
            library("ktor-client-serialization", "io.ktor", "ktor-client-serialization").versionRef("ktor")
            library("ktor-serialization-json", "io.ktor", "ktor-serialization-kotlinx-json").versionRef("ktor")

            library("brotli", "org.brotli", "dec").version("0.1.2")
            library("palette", "androidx.palette", "palette").version("1.0.0")
            library("desugaring", "com.android.tools", "desugar_jdk_libs").version("1.1.5")
        }

        create("testLibs") {
            library("junit", "junit", "junit").version("4.13.2")
        }
    }
}

rootProject.name = "ViMusic"
include(":app")
include(":compose-routing")
include(":compose-reordering")
include(":compose-persist")
include(":innertube")
include(":ktor-client-brotli")
include(":kugou")