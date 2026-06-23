import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val milfDefaultBackendUrl = providers.gradleProperty("MILF_DEFAULT_BACKEND_URL")
    .orElse(providers.environmentVariable("MILF_DEFAULT_BACKEND_URL"))
    .orElse("ws://10.0.2.2:8765")
val milfDeviceToken = providers.gradleProperty("MILF_DEVICE_TOKEN")
    .orElse(providers.environmentVariable("MILF_DEVICE_TOKEN"))
    .orElse("")

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "ai.milf.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.milf.client"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MILF_DEFAULT_BACKEND_URL", buildConfigString(milfDefaultBackendUrl.get()))
        buildConfigField("String", "MILF_DEVICE_TOKEN", buildConfigString(milfDeviceToken.get()))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui-android:1.7.6")
    implementation("androidx.compose.material3:material3-android:1.3.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
}
