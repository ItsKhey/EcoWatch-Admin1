plugins {
    alias(libs.plugins.android.library)
}

android {
    // Screen components shared by more than one feature (report detail dialog,
    // report adapters, reporter account rows, cross-feature navigation).
    namespace = "com.example.ecowatchadmin1.uicommon"
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
}
