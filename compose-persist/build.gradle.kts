plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "it.vfsfitvnm.compose.persist"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        //noinspection OldTargetApi
        targetSdk = 35
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    sourceSets.all {
        kotlin.srcDir("src/$name/kotlin")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.compose.foundation)
}