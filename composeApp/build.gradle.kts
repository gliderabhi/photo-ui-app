import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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
            implementation(libs.zxing.core)
            implementation(libs.credentials)
            implementation(libs.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation(libs.room.runtime)
            implementation(libs.room.ktx)
            implementation(libs.mlkit.face.detection)
            implementation(libs.exifinterface)
            implementation(libs.lifecycle.process)
            implementation(libs.onnxruntime.android)
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
            // Android-only API (BackHandler), but androidTarget is the only real
            // compiled target in this project today — if a non-Android target is
            // ever added, ShellScreen's BackHandler usage will need expect/actual.
            implementation(libs.activity.compose)
            implementation(libs.ktor.client.core)
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
    compileSdk = 36

    // The Google "Android" OAuth client (see Cloud Console) is registered
    // against this keystore's SHA-1, not the debug keystore's — so a release
    // build must be signed with it for Credential Manager Google sign-in to
    // find any credentials. Passwords come from local.properties (gitignored),
    // never committed in source.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("../../../photos.jks")
            storePassword = localProperties.getProperty("keystore.storePassword", "photos")
            keyAlias = localProperties.getProperty("keystore.keyAlias", "key0")
            keyPassword = localProperties.getProperty("keystore.keyPassword", "photos")
        }
    }

    defaultConfig {
        applicationId = "com.sevis.photos"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        // https://photos.sevis.store proxies /user-service, /photo-service and
        // /stream-service straight through to the gateway (same as the web app),
        // so it works from any real device on or off the home network — unlike
        // 10.0.2.2, which only resolves inside the Android emulator.
        buildConfigField("String", "API_BASE_URL", "\"https://photos.sevis.store\"")
        // See local.properties (gitignored, not committed) — GitHub's push
        // protection rejects commits containing these directly in source.
        buildConfigField("String", "GOOGLE_TV_CLIENT_ID", "\"${localProperties.getProperty("google.tv.client.id", "")}\"")
        buildConfigField("String", "GOOGLE_TV_CLIENT_SECRET", "\"${localProperties.getProperty("google.tv.client.secret", "")}\"")
        // Web-type OAuth client used as the "audience" for Credential Manager's
        // Google ID token on mobile — a different client type than the TV one
        // above, since Google only accepts device-authorization for TV/limited-
        // input clients and only accepts Credential Manager's Google ID token
        // requests for Web-type clients.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperties.getProperty("google.web.client.id", "")}\"")
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

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // The bundled sface.onnx (see assets/) is already dense binary weight data,
    // not worth re-compressing into the APK's zip.
    androidResources {
        noCompress += "onnx"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}
