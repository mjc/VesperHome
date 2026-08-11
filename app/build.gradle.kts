import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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

fun localBuildConfigValue(name: String): String =
    "\"${localProperties.getProperty(name).orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.sergioasenjo.vesperhome"
    compileSdk = 36

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

    defaultConfig {
        applicationId = "com.sergioasenjo.vesperhome"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SONARR_URL", "\"\"")
        buildConfigField("String", "SONARR_API_KEY", "\"\"")
        buildConfigField("String", "RADARR_URL", "\"\"")
        buildConfigField("String", "RADARR_API_KEY", "\"\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "SONARR_URL", localBuildConfigValue("SONARR_URL"))
            buildConfigField("String", "SONARR_API_KEY", localBuildConfigValue("SONARR_API_KEY"))
            buildConfigField("String", "RADARR_URL", localBuildConfigValue("RADARR_URL"))
            buildConfigField("String", "RADARR_API_KEY", localBuildConfigValue("RADARR_API_KEY"))
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.named("check") {
    dependsOn(rootProject.tasks.named("spotlessCheck"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.emoji2)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
