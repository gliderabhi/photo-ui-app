import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.ktor.client.android)
            implementation(libs.work.runtime.ktx)
            implementation(libs.datastore.preferences)
            implementation(libs.accompanist.permissions)
            implementation(libs.coil.network.ktor)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.core.ktx)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.ui)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.navigation.compose)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.coil.compose)
        }
    }
}

android {
    namespace = "com.sevis.photos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sevis.photos"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // https://photos.sevis.store proxies /user-service, /photo-service and
        // /stream-service straight through to the gateway (same as the web app),
        // so it works from any real device on or off the home network — unlike
        // 10.0.2.2, which only resolves inside the Android emulator.
        buildConfigField("String", "API_BASE_URL", "\"https://photos.sevis.store\"")
    }

    // "mobile" is the existing phone/tablet app unchanged. "tv" reuses the exact
    // same Compose UI/screens/API layer (Compose's default focus system already
    // handles D-pad up/down/left/right/OK navigation for clickable composables)
    // — it's a distinct installable APK with its own applicationId, a leanback
    // banner, and a LEANBACK_LAUNCHER intent filter so it shows up in the
    // Android TV launcher, added purely via the src/tv manifest overlay.
    flavorDimensions += "platform"
    productFlavors {
        create("mobile") {
            dimension = "platform"
        }
        create("tv") {
            dimension = "platform"
            applicationIdSuffix = ".tv"
            versionNameSuffix = "-tv"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
