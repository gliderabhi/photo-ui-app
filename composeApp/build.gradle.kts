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
        // Change this to your server's IP / hostname
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
