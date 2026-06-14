import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

val catalog = the<VersionCatalogsExtension>().named("libs")

android {
    compileSdk = catalog.findVersion("android-compileSdk").get().requiredVersion.toInt()
    defaultConfig {
        minSdk = catalog.findVersion("android-minSdk").get().requiredVersion.toInt()
        targetSdk = catalog.findVersion("android-targetSdk").get().requiredVersion.toInt()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
