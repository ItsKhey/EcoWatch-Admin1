plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.ecowatchadmin1.feature.auth"
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
    api(project(":app:data"))
    api(project(":app:ui-common"))
    // LoginActivity asks the FCM retry worker to register the new session's token.
    implementation(project(":app:notifications"))
}
