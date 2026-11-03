plugins {
    alias(libs.plugins.android.library)
}

android {
    // Data layer: Room entities/DAOs, Retrofit services, JSON parsers and the
    // repositories every feature talks to.
    namespace = "com.example.ecowatchadmin1.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":app:core"))

    // Room
    api(libs.room.runtime)
    api(libs.room.ktx)
    annotationProcessor(libs.room.compiler)

    // Retrofit / networking
    api(libs.retrofit)
    api(libs.retrofit.gson)
    api(libs.okhttp)
    api(libs.okhttp.logging)
}
