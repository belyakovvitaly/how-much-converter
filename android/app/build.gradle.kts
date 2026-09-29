import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Where the Report button sends its zip. The repository is public and an
// address in it is an address for every scraper, so it comes from outside:
// the HOW_MUCH_REPORT_EMAIL environment variable (a secret, in CI) or
// `reportEmail=` in local.properties. Without either, a report goes to the
// share sheet and the reader picks where.
val reportEmail: String = System.getenv("HOW_MUCH_REPORT_EMAIL")?.takeIf { it.isNotBlank() }
    ?: Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }.getProperty("reportEmail").orEmpty()

android {
    namespace = "converter.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "converter.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "0.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REPORT_EMAIL", "\"${reportEmail.trim()}\"")

        ndk {
            // ONNX Runtime ships a large native library for every ABI, and four
            // copies of it is 135 MB of a 166 MB APK. Every phone this could run
            // on is 64-bit ARM, and so is the emulator on an Apple Silicon Mac.
            // Add x86_64 back if an Intel-host emulator is ever needed.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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


dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.photopicker.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.onnxruntime.android)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
