plugins {
    id("ostomate.kmp-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinxSerialization)
    jacoco
}

kotlin {
    // Logic-only module: no Compose, so iOS targets are unconstrained.
    // No iosX64: it existed "for local simulator testing on Bobby's Intel Mac", but this
    // machine is Apple Silicon and CI runs only :shared:iosSimulatorArm64Test on an
    // arm64 macos runner. Nothing built or tested it — it was pure build time.
    // composeApp cannot have it either (CMP 1.11+ drops Intel-simulator artifacts).
    iosArm64()
    iosSimulatorArm64()

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
            implementation(libs.zxing.core)
        }
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.property)
        }
        // DataStore tests live in commonTest (run on JVM host + iOS sim). The SQLite-backed
        // Room tests run per-platform: iOS uses the bundled native driver; the JVM host uses
        // Robolectric + AndroidSQLiteDriver (the bundled driver ships only Android-ABI natives).
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.sqlite.framework)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

// Coverage (2.5.2, 08-test-strategy §5): JaCoCo on the JVM host run. Kover stays out —
// 0.8.x does not support com.android.kotlin.multiplatform.library; revisit if that lands.
// Scope: hand-written domain + data code. Excluded: Room-generated impls, the generated
// database constructor, and platform driver glue (per-platform wiring, not logic).

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// The AGP KMP plugin registers the host-test task after this script evaluates, so
// configure it lazily by type + name rather than tasks.named().
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    configure<JacocoTaskExtension> {
        // Robolectric loads classes through its instrumenting classloader, which strips
        // source-location info; without this flag those classes report 0 coverage.
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val coverageClasses =
    layout.buildDirectory.dir("classes/kotlin/android/main").map { dir ->
        fileTree(dir) {
            include("com/ostomate/app/domain/**", "com/ostomate/app/data/**")
            exclude(
                "**/*_Impl*",
                "**/OstomateDatabaseConstructor*",
                "**/DatabaseBuilder_*",
            )
        }
    }

val jacocoHostTestReport by tasks.registering(JacocoReport::class) {
    group = "verification"
    description = "Coverage report for the JVM host test run (domain + data scope)."
    dependsOn("testAndroidHostTest")
    executionData(layout.buildDirectory.file("jacoco/testAndroidHostTest.exec"))
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/androidMain/kotlin"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val jacocoCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    group = "verification"
    description = "Fails if line coverage of the domain + data scope drops below the floor."
    dependsOn("testAndroidHostTest")
    executionData(layout.buildDirectory.file("jacoco/testAndroidHostTest.exec"))
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/androidMain/kotlin"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                // Floor ratcheted after 2.5.4 repository tests: 92.0% measured 2026-07-02
                // (was 52.6% at the 2.5.2 baseline). Never lower it.
                minimum = "0.91".toBigDecimal()
            }
        }
    }
}
