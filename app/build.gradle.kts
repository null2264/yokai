import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("kotlin-parcelize")
    id("com.google.android.gms.oss-licenses-plugin")
    id("app.cash.sqldelight")
    id("com.google.gms.google-services") apply false
    id("com.google.firebase.crashlytics") apply false
}

if (gradle.startParameter.taskRequests.toString().contains("Standard")) {
    apply(mapOf("plugin" to "com.google.gms.google-services"))
    apply(mapOf("plugin" to "com.google.firebase.crashlytics"))
}

fun runCommand(command: String): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        commandLine = command.split(" ")
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

val supportedAbis = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

android {
    compileSdk = AndroidConfig.compileSdk
    ndkVersion = AndroidConfig.ndk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
        targetSdk = AndroidConfig.targetSdk
        applicationId = "eu.kanade.tachiyomi"
        versionCode = 128
        versionName = "1.8.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime()}\"")
        buildConfigField("Boolean", "INCLUDE_UPDATER", "false")
        buildConfigField("Boolean", "BETA", "false")
        buildConfigField("Boolean", "NIGHTLY", "false")

        ndk {
            // False positive, we have x86 abi support
            //noinspection ChromeOsAbiSupport
            abiFilters += supportedAbis
        }
        externalNativeBuild {
            cmake {
                this.arguments("-DHAVE_LIBJXL=FALSE")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            // False positive, we have x86 abi support
            //noinspection ChromeOsAbiSupport
            include(*supportedAbis.toTypedArray())
            isUniversalApk = true
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debugYokai"
            versionNameSuffix = "-d${getCommitCount()}"
        }
        getByName("release") {
            applicationIdSuffix = ".yokai"
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
        }
        create("beta") {
            initWith(getByName("release"))
            buildConfigField("boolean", "BETA", "true")

            matchingFallbacks.add("release")
            versionNameSuffix = "-b${getCommitCount()}"
        }
        create("nightly") {
            initWith(getByName("release"))
            buildConfigField("boolean", "BETA", "true")
            buildConfigField("boolean", "NIGHTLY", "true")

            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks.add("release")
            versionNameSuffix = "-b${getCommitCount()}"
            applicationIdSuffix = ".nightlyYokai"
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        // If you're here because there's not BuildConfig, build the app first, it'll generate it for you
        buildConfig = true

        // Disable some unused things
        aidl = false
        renderScript = false
        shaders = false
    }

    flavorDimensions.add("default")

    productFlavors {
        create("standard") {
            buildConfigField("Boolean", "INCLUDE_UPDATER", "true")
        }
        create("dev") {
            resourceConfigurations.clear()
            resourceConfigurations.add("en")
        }
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
        abortOnError = false
        checkReleaseBuilds = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = compose.versions.compose.compiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "eu.kanade.tachiyomi"
}

dependencies {
    // Compose
    implementation(compose.bundles.compose)
    debugImplementation(compose.ui.tooling)
    implementation(libs.compose.theme.adapter3)
    implementation(libs.accompanist.webview)

    // Modified dependencies
    implementation(libs.subsamplingscaleimageview) {
        exclude(module = "image-decoder")
    }
    implementation(libs.image.decoder)

    implementation(libs.flexbox)

    implementation(libs.material)

    // Android X libraries
    implementation(androidx.bundles.androidx)

    implementation(platform(libs.firebase))

    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // ReactiveX
    implementation(libs.rxandroid)
    implementation(libs.rxjava)
    implementation(libs.rxrelay)

    // Network client
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okhttp.brotli)
    implementation(libs.okio)

    // Chucker
    debugImplementation(libs.chucker.library)
    releaseImplementation(libs.chucker.library.no.op)
    "nightlyImplementation"(libs.chucker.library.no.op)

    implementation(kotlin("reflect", version = kotlinx.versions.kotlin.get()))

    // JSON
    implementation(kotlinx.bundles.serialization)

    // JavaScript engine
    implementation(libs.quickjs.android)

    // Disk
    implementation(libs.disklrucache)
    implementation(libs.unifile)
    implementation(libs.bundles.archive)

    // HTML parser
    implementation(libs.jsoup)

    // Job scheduling
    implementation(libs.guava)

    implementation(libs.play.services.gcm)

    // Database
    implementation(libs.sqlite.android)
    //noinspection UseTomlInstead
    implementation("com.github.inorichi.storio:storio-common:8be19de@aar")
    //noinspection UseTomlInstead
    implementation("com.github.inorichi.storio:storio-sqlite:8be19de@aar")

    // Model View Presenter
    implementation(libs.nucleus)
    implementation(libs.nucleus.support.v7)

    // Dependency injection
    implementation(libs.injekt.core)

    // Image library
    implementation(libs.bundles.coil)

    // Logging
    implementation(libs.timber)

    // Sort
    implementation(libs.java.nat.sort)

    // UI
    implementation(libs.material.design.dimens)
    implementation(libs.loading.button)
    implementation(libs.fastadapter)
    implementation(libs.fastadapter.extensions.binding)
    implementation(libs.flexible.adapter)
    implementation(libs.flexible.adapter.ui)
    implementation(libs.viewstatepageradapter)
    implementation(libs.slice)
    implementation(libs.markwon)

    implementation(libs.photoview)
    implementation(libs.directionalviewpager)
    implementation(libs.viewtooltip)
    implementation(libs.taptargetview)

    // Conductor
    implementation(libs.conductor)
    implementation(libs.conductor.support.preference)

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(kotlin("stdlib", org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION))

    implementation(kotlinx.coroutines.core)
    implementation(kotlinx.coroutines.android)

    // Text distance
    implementation(libs.java.string.similarity)

    implementation(libs.play.services.oss.licenses)

    // TLS 1.3 support for Android < 10
    implementation(libs.conscrypt)

    // Android Chart
    implementation(libs.mpandroidchart)

    // Tests
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime)
    androidTestImplementation(libs.bundles.test.android)
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // See https://kotlinlang.org/docs/reference/experimental.html#experimental-status-of-experimental-api(-markers)
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf(
            "-Xcontext-receivers",
            // "-opt-in=kotlin.Experimental",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            // "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            // "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )

        if (project.findProperty("tachiyomi.enableComposeCompilerMetrics") == "true") {
            kotlinOptions.freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                    (project.layout.buildDirectory.asFile.orNull?.absolutePath ?: "/tmp/yokai") + "/compose_metrics",
            )
            kotlinOptions.freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
                        (project.layout.buildDirectory.asFile.orNull?.absolutePath ?: "/tmp/yokai") + "/compose_metrics",
            )
        }
    }

    // Duplicating Hebrew string assets due to some locale code issues on different devices
    val copyHebrewStrings = task("copyHebrewStrings", type = Copy::class) {
        from("./src/main/res/values-he")
        into("./src/main/res/values-iw")
        include("**/*")
    }

    preBuild {
        dependsOn(
            // formatKotlin,
            copyHebrewStrings
        )
    }
}
