plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.ecowatchadmin1.feature.profile"
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
    api(project(":app:feature:auth"))
    api(project(":app:feature:announcements"))
    api(project(":app:feature:analytics"))
    api(project(":app:feature:personnel"))
    api(project(":app:feature:residents"))
}
