plugins {
    id("ostomate.kmp-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    // Logic-only module: no Compose, so all three iOS targets are supported.
    // iosX64 exists for local simulator testing on Bobby's Intel Mac.
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    androidLibrary {
        namespace = "com.ostomate.app.shared"
        withHostTestBuilder {
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime)
            api(libs.androidx.biometric)
            implementation(libs.sentry.android)
        }
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.property)
        }
        iosTest.dependencies {
            implementation(libs.room.testing)
        }
    }
}

// The migration test reads exported schema JSONs from the source tree. SIMCTL_CHILD_
// makes simctl forward the variable into the spawned simulator test process.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    environment("SIMCTL_CHILD_OSTOMATE_SCHEMAS_PATH", layout.projectDirectory.dir("schemas").asFile.absolutePath)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
}

// TODO(coverage): Kover 0.8.x does not support com.android.kotlin.multiplatform.library
// (it expects the old `android` extension which this plugin does not expose). Coverage
// gate will be added once Kover releases a fix, or we switch to JaCoCo. Tracked in 05-dev-plan.
