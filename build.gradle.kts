plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint("1.8.0")
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint("1.8.0")
    }
    format("misc") {
        target(
            ".editorconfig",
            ".gitignore",
            "gradle.properties",
            "app/src/**/*.xml"
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register("format") {
    group = "formatting"
    description = "Formats native source and project configuration files."
    dependsOn("spotlessApply")
}

tasks.register("lint") {
    group = "verification"
    description = "Checks formatting, Kotlin style, and Android lint findings."
    dependsOn("spotlessCheck", ":app:lintDebug")
}
