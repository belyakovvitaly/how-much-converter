pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "how-much-android"

// core is plain Kotlin/JVM on purpose: the price rules are the part shared with
// iOS, and keeping them off the Android SDK means they can be tested with a JDK
// alone — which is also how they are held to the OCR benchmark's numbers.
include(":core")
include(":app")
