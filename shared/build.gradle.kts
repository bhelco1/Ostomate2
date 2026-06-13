import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
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
        namespace = "com.ostimate.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTestBuilder {
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime)
            api(libs.androidx.biometric)
        }
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        iosTest.dependencies {
            implementation(libs.room.testing)
            implementation(libs.okio) // MigrationTestHelper's native API takes okio Paths
        }
    }
}

// The migration test reads exported schema JSONs from the source tree. SIMCTL_CHILD_
// makes simctl forward the variable into the spawned simulator test process.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    environment("SIMCTL_CHILD_OSTIMATE_SCHEMAS_PATH", layout.projectDirectory.dir("schemas").asFile.absolutePath)
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
