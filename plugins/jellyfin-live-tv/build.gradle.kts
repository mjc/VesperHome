import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val releaseKeystore = file(
    localProperties.getProperty("VESPER_RELEASE_STORE_FILE")
        ?: "${System.getProperty("user.home")}/.android/vesper-home-release.jks"
)
val releasePasswordFile = file(
    localProperties.getProperty("VESPER_RELEASE_PASSWORD_FILE")
        ?: "${System.getProperty("user.home")}/.android/vesper-home-release.pass"
)
val releaseSigningAvailable = releaseKeystore.isFile && releasePasswordFile.isFile

android {
    namespace = "com.sergioasenjo.vesperhome.plugin.jellyfinlivetv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sergioasenjo.vesperhome.plugin.jellyfinlivetv"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releasePasswordFile.readText().trim()
                keyAlias = "vesper-home"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(project(":plugin-api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}
