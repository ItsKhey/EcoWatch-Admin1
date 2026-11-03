plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.ecowatchadmin1.feature.analytics"
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
}
