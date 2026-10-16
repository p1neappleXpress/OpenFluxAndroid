plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/** The app version: -PappVersion=1.2.3 (the release workflow passes the tag). */
val appVersion = (findProperty("appVersion") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "2.0.0"

/** 1.2.3 -> 10203: grows with every release as Android requires. */
val appVersionCode = appVersion.split('.', '-').take(3).map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

/** The id that names this build to the OpenFlux service (an identifier, not a secret): -Popenflux.appId=ofa_… or OPENFLUX_APP_ID. */
val appId = ((findProperty("openflux.appId") as String?) ?: System.getenv("OPENFLUX_APP_ID")).orEmpty().trim()

/** The Keycloak client of the OpenFlux sign-in page: -Popenflux.clientId=… or OPENFLUX_CLIENT_ID; ours by default. */
val clientId = ((findProperty("openflux.clientId") as String?) ?: System.getenv("OPENFLUX_CLIENT_ID")).orEmpty().trim().ifEmpty { "openflux-android" }

/** The core the gomobile library was built from, written by scripts/build-android-core.sh. */
val coreVersion = file("libs/openflux-core.version").takeIf { it.isFile }?.readText()?.trim() ?: "встроенное"

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "io.openflux.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Its own id: installs beside the Java app (io.openflux.app).
        applicationId = "io.openflux.client"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "CORE_VERSION", "\"$coreVersion\"")
        buildConfigField("String", "APP_ID", "\"$appId\"")
        buildConfigField("String", "CLIENT_ID", "\"$clientId\"")
    }

    buildFeatures {
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val keystore = System.getenv("ANDROID_KEYSTORE_FILE")
            // A dry run of the release workflow names the file but does not restore it.
            if (!keystore.isNullOrBlank() && file(keystore).exists()) {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // The Go core loads as a plain .so; extracting it keeps startup simple.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":shared"))
    // The OpenFlux core (gomobile), built by scripts/build-android-core.sh.
    implementation(files("libs/openflux.aar"))
    implementation(libs.androidx.browser)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.components.resources)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android)
}
