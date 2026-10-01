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
}

ktlint {
    filter {
        exclude { element -> element.file.path.contains("/build/generated/") }
    }
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
