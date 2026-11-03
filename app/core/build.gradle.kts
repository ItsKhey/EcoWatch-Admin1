plugins {
    alias(libs.plugins.android.library)
}

/**
 * Where the app looks for the EcoWatch API.
 *
 * The value is compiled into the shared core library (which owns the app's
 * `BuildConfig`) rather than hardcoded in Java, so every module reads the same
 * endpoint and the same source tree can be pointed at a local server or a
 * hosted deployment without a code edit.
 *
 * The default is the Android emulator's alias for the host machine's loopback,
 * which is exactly what `npm start` in the backend project serves. A physical
 * phone cannot use 10.0.2.2, so a device build points at this machine's address
 * on the same WiFi instead. The address is resolved in this order, first match
 * wins:
 *
 *   1. the -PecowatchApiBaseUrl flag
 *   2. the ECOWATCH_API_BASE_URL environment variable
 *   3. the emulator default above
 */
val ecowatchApiBaseUrl: String =
    (project.findProperty("ecowatchApiBaseUrl") as String?)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("ECOWATCH_API_BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
        ?: "http://10.0.2.2:5000/api/"

/** Retrofit requires a base URL with a trailing slash, so normalise it once here. */
val ecowatchApiBaseUrlNormalized: String =
    if (ecowatchApiBaseUrl.endsWith("/")) ecowatchApiBaseUrl else "$ecowatchApiBaseUrl/"

/**
 * The same server without the `/api` path, used to expand the relative image
 * paths the database stores (`/uploads/...`) into something Glide can load.
 */
val ecowatchServerOrigin: String = run {
    val withoutTrailingSlash = ecowatchApiBaseUrlNormalized.trimEnd('/')
    val withoutApiPath =
        if (withoutTrailingSlash.endsWith("/api")) withoutTrailingSlash.removeSuffix("/api")
        else withoutTrailingSlash
    "$withoutApiPath/"
}

android {
    // The core module keeps the project's original namespace so the shared R
    // class (all resources live here) and BuildConfig keep their established
    // package. Every other module imports that R class explicitly.
    namespace = "com.example.ecowatchadmin1"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24

        buildConfigField("String", "API_BASE_URL", "\"$ecowatchApiBaseUrlNormalized\"")
        buildConfigField("String", "SERVER_ORIGIN", "\"$ecowatchServerOrigin\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Shared UI toolkit. Exposed with `api` because every feature screen is a
    // Fragment/Activity built on these.
    api(libs.activity.ktx)
    api(libs.appcompat)
    api(libs.constraintlayout)
    api(libs.material)
    api(libs.swiperefreshlayout)
    api(libs.lifecycle.viewmodel.ktx)
    api(libs.lifecycle.livedata.ktx)

    // Image loading, used by helpers such as WasteScaleDisplay and by features.
    api(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // Resource themes/attrs referenced by the merged manifest and layouts.
    api(libs.play.services.location)
    api("com.google.android.gms:play-services-maps:19.0.0")
    api(libs.ucrop)
    api(libs.biometric)
    api("com.github.PhilJay:MPAndroidChart:3.1.0")
    api(libs.mpandroidchart)
}
