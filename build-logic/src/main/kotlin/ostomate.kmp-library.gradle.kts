import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

val catalog = the<VersionCatalogsExtension>().named("libs")

kotlin {
    androidLibrary {
        compileSdk = catalog.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = catalog.findVersion("android-minSdk").get().requiredVersion.toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // Without this, detekt.yml is the whole config and only rules it marks `active: true` run, which
    // left UnusedImports as the one rule checked. Layer it on detekt's defaults instead.
    buildUponDefaultConfig = true
    // detekt's default source is src/{main,test}/{java,kotlin}, which a KMP module does not have,
    // so `detekt` ran NO-SOURCE here. Point it at src/<set>/kotlin for every KMP source set, main and
    // test; directories, not a file list, so a new file is not missed by an up-to-date check.
    source.setFrom(provider { kotlin.sourceSets.map { set -> file("src/${set.name}/kotlin") } })
}

ktlint {
    filter {
        exclude { element -> element.file.path.contains("/build/generated/") }
    }
}

// Robolectric runs offline from android-all jars that Gradle resolves. Left to itself it downloads
// them from a Maven mirror at test time, so a mirror rate limit (HTTP 429, main run 43996321273)
// failed 58 tests that never ran. Resolved here, a download failure fails the build before any
// test starts, and the jars ride along in the Gradle cache CI already keeps. One entry per SDK a
// test pins with @Config; versions are the ones Robolectric (libs.versions.toml) itself asks for,
// so a Robolectric bump or a new SDK needs a new entry here. A missing jar fails the tests that
// need it with "Path is not a file: …/android-all-instrumented-<version>.jar", never a download.
val robolectricAndroidAll =
    listOf(
        "10-robolectric-5803371-i7", // SDK 29
        "14-robolectric-10818077-i7", // SDK 34
        "15-robolectric-12650502-i7", // SDK 35, Robolectric's default SDK, loaded even by pinned tests
    )
// One configuration per jar: in a single one, Gradle's conflict resolution keeps only the newest.
val robolectricJars =
    robolectricAndroidAll.map { version ->
        configurations.create("robolectricAndroidAll$version") {
            isTransitive = false
            isCanBeConsumed = false
            dependencies.add(project.dependencies.create("org.robolectric:android-all-instrumented:$version"))
        }
    }
val robolectricJarDir = layout.buildDirectory.dir("robolectric-jars")
val syncRobolectricJars =
    tasks.register<Sync>("syncRobolectricJars") {
        robolectricJars.forEach { from(it) }
        into(robolectricJarDir)
    }
tasks.withType<Test>().configureEach {
    inputs.files(syncRobolectricJars).withPropertyName("robolectricJars")
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricJarDir.get().asFile.absolutePath)
}

// In CI, test and coverage tasks always execute: testpulse ingests each run's JUnit files as
// that run's results, and a result restored from the build cache is not a test run. It cannot
// flip, so it hides flakiness, and its timestamps belong to an older build (main run
// 36765788191 restored :shared:testAndroidHostTest FROM-CACHE and reported it as starting 19 h
// before the run did). AbstractTestTask covers the JVM host tests and iosSimulatorArm64Test.
// Only these tasks: lint and compile keep the cache, and local runs keep up-to-date checks.
if (providers.environmentVariable("CI").isPresent) {
    val reason = "CI reports test results to testpulse; they must come from this run"
    tasks.withType<AbstractTestTask>().configureEach {
        outputs.cacheIf(reason) { false }
        outputs.upToDateWhen { false }
    }
    tasks.withType<JacocoReport>().configureEach {
        outputs.cacheIf(reason) { false }
        outputs.upToDateWhen { false }
    }
    tasks.withType<JacocoCoverageVerification>().configureEach {
        outputs.cacheIf(reason) { false }
        outputs.upToDateWhen { false }
    }
}
