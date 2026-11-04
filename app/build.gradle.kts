plugins {
    alias(libs.plugins.android.application)
}

// The checked-in project builds without Firebase credentials. Production CI or a
// developer workstation supplies app/google-services.json; the Google Services
// plugin is only applied when that real configuration file exists.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Release artifacts must be signed into a real Firebase project, so a release
// task fails fast without the configuration. Debug builds stay possible so the
// rest of the app can be developed without Firebase secrets.
val requestingReleaseTask = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (requestingReleaseTask && !file("google-services.json").exists()) {
    throw GradleException(
        "Release builds require app/google-services.json for Firebase Cloud Messaging."
    )
}

/**
 * Where the app looks for the EcoWatch API.
 *
 * The target is compiled in by Gradle rather than hardcoded in Java, so the same
 * source tree builds against a local server or a hosted deployment without a code
 * edit.
 *
 * The default is the Android emulator's alias for the host machine's loopback,
 * which is exactly what `npm start` in the backend project serves. Nothing else
 * has to be configured to run the app in the emulator.
 *
 * A physical phone cannot use 10.0.2.2, so point a device build at this
 * machine's address on the same WiFi instead. The address is resolved in this
 * order, first match wins:
 *
 *   1. the -PecowatchApiBaseUrl flag
 *   2. the ECOWATCH_API_BASE_URL environment variable
 *   3. the emulator default above
 *
 * The environment variable is what makes the Run button in Android Studio work
 * without typing anything: set it once in the shell, and every build from the
 * IDE picks it up.
 *
 *   gradlew installDebug -PecowatchApiBaseUrl=http://192.168.1.20:5000/api/
 *
 * The machine's address is printed by `ipconfig` and the Windows firewall has to
 * allow inbound TCP 5000.
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
    namespace = "com.example.ecowatchadmin1"
    compileSdk = 37

    // BuildConfig is what carries the API target above into the app.
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.ecowatchadmin1"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "API_BASE_URL", "\"$ecowatchApiBaseUrlNormalized\"")
        buildConfigField("String", "SERVER_ORIGIN", "\"$ecowatchServerOrigin\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    implementation(libs.play.services.location)
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation(libs.swiperefreshlayout)
    implementation(libs.ucrop)
    implementation(libs.biometric)

    // Charts on the Reports & Analytics screen.
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

    // WorkManager backs the FCM token registration retry (Phase 3A).
    implementation(libs.work.runtime)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.3.0"))
    implementation("com.google.firebase:firebase-messaging")

    testImplementation(libs.junit)
    // Runs the Room migration statements against a real SQLite engine. A migration
    // whose schema disagrees with the @Entity only fails when the database is
    // opened on a device, which is the worst moment to find out.
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

}