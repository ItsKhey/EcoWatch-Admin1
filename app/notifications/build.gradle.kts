plugins {
    alias(libs.plugins.android.library)
}

android {
    // Firebase Cloud Messaging service and the WorkManager retry that keeps the
    // FCM token registered with the server.
    namespace = "com.example.ecowatchadmin1.notifications"
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

    // WorkManager backs the FCM token registration retry.
    api(libs.work.runtime)

    // Firebase
    api(platform("com.google.firebase:firebase-bom:34.3.0"))
    api("com.google.firebase:firebase-messaging")
}
