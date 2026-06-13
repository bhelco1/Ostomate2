plugins {
    id("ostimate.kmp-library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    // No iosX64: Compose Multiplatform 1.11+ does not publish Intel-simulator
    // artifacts. iOS UI runs on devices and Apple-Silicon simulators (CI).
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(projects.shared)
        }
    }

    androidLibrary {
        namespace = "com.ostimate.app.compose"
        androidResources {
            enable = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            api(projects.shared)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.navigation.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.material.icons.core)

            implementation(libs.kotlinx.datetime)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.qrose)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
