import java.util.*

plugins {
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.gradle.versions)
    alias(kotlinx.plugins.android) apply false
}

buildscript {
    dependencies {
        classpath(libs.gradle)
        classpath(libs.google.services)
        classpath(kotlinx.gradle)
        classpath(libs.oss.licenses.plugin)
        classpath(kotlinx.serialization.gradle)
        classpath(libs.firebase.crashlytics.gradle)
        classpath(libs.sqldelight.gradle)
    }
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java).configure {
    rejectVersionIf {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { candidate.version.uppercase(Locale.ROOT).contains(it) }
        val regex = "^[0-9,.v-]+(-r)?$".toRegex()
        val isStable = stableKeyword || regex.matches(candidate.version)
        isStable.not()
    }
    // optional parameters
    checkForGradleUpdate = true
    outputFormatter = "json"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
