import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun propertiesOf(file: File): Properties = Properties().apply {
    file.takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

/** Per machine, and out of the repository. */
val localProperties = propertiesOf(rootProject.file("local.properties"))

fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

// Where the Report button sends its zip. The repository is public and an
// address in it is an address for every scraper, so it comes from outside:
// the HOW_MUCH_REPORT_EMAIL environment variable (a secret, in CI) or
// `reportEmail=` in local.properties. Without either, a report goes to the
// share sheet and the reader picks where.
val reportEmail: String = env("HOW_MUCH_REPORT_EMAIL")
    ?: localProperties.getProperty("reportEmail").orEmpty()

// The key a release is signed with — the upload key Google Play knows the app
// by, and the one the APK on GitHub carries. Also from outside: in CI, the
// HOW_MUCH_KEYSTORE* variables; on a machine, a file named by
// `signingProperties=` in local.properties, holding storeFile, storePassword,
// keyAlias and keyPassword. Without it a release builds unsigned.
val signing: Map<String, String>? = run {
    val fromEnv = mapOf(
        "storeFile" to env("HOW_MUCH_KEYSTORE"),
        "storePassword" to env("HOW_MUCH_KEYSTORE_PASSWORD"),
        "keyAlias" to env("HOW_MUCH_KEY_ALIAS"),
        "keyPassword" to env("HOW_MUCH_KEY_PASSWORD"),
    )
    if (fromEnv.values.all { it != null }) return@run fromEnv.mapValues { it.value!! }
    val file = localProperties.getProperty("signingProperties") ?: return@run null
    val props = propertiesOf(File(file))
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { props.getProperty(it) ?: return@run null }
}

android {
    namespace = "converter.android"
    compileSdk = 37

    defaultConfig {
        // What Google Play knows the app by, for good: it cannot change once
        // uploaded. The namespace above is only the code's package, and stays.
        applicationId = "io.github.belyakovvitaly.howmuch"
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

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = file(signing.getValue("storeFile"))
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // A development build installs beside the released app instead of
            // over it — the two are signed with different keys, and Android
            // would refuse one over the other. src/debug names it "How Much?
            // dev", so the two icons can be told apart.
            applicationIdSuffix = ".debug"
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
