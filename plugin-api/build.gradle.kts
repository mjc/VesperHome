plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.sergioasenjo.vesperhome.plugin.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    buildFeatures {
        aidl = true
    }
}
