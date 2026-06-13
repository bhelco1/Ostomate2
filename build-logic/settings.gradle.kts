// Keep plugin versions here in sync with gradle/libs.versions.toml —
// pluginManagement is evaluated before versionCatalogs, so we can't reference libs here.
pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.multiplatform")          version "2.3.21"
        id("com.android.kotlin.multiplatform.library")    version "9.0.1"
        id("com.android.application")                     version "9.0.1"
        id("io.gitlab.arturbosch.detekt")                 version "1.23.8"
        id("org.jlleitschuh.gradle.ktlint")               version "12.1.2"
    }
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
