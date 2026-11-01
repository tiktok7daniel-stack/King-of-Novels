plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ireader.core"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        minSdk = 27
        consumerProguardFiles("consumer-proguard.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // IReader extensions are built against these and call them directly: same versions as MANHWA, where they work
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    api("io.ktor:ktor-client-core:3.6.0")
    api("io.ktor:ktor-client-okhttp:3.6.0")
    api("io.ktor:ktor-client-content-negotiation:3.6.0")
    api("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
    api("com.fleeksoft.ksoup:ksoup:0.2.6")
    api("co.touchlab:kermit:2.2.0")
    api("com.squareup.okhttp3:okhttp:5.5.0")
}
