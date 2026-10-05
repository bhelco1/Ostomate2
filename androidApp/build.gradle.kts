import java.util.Properties

plugins {
    id("ostomate.android-app")
    alias(libs.plugins.composeCompiler)
}

// local.properties is gitignored; read it manually so secrets stay out of gradle.properties
val localProps =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.reader())
    }

android {
    namespace = "com.ostomate.app"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.ostomate.app"
        versionCode = 1
        versionName = "1.0"
        // local.properties: SENTRY_DSN=https://...  CI: -PSENTRY_DSN=https://...
        val dsn = localProps["SENTRY_DSN"] as String? ?: project.findProperty("SENTRY_DSN") as String? ?: ""
        buildConfigField("String", "SENTRY_DSN", "\"$dsn\"")
    }

    // Upload key: CI sets these as env vars from GitHub secrets; locally they can go in
    // local.properties. With none set, release builds stay unsigned so a fresh clone still
    // builds. A partial set is a broken config, not "unsigned", so it fails loudly.
    val signingKeys = listOf("KEYSTORE_PATH", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    val signing = signingKeys.associateWith { System.getenv(it) ?: localProps[it] as String? }
    val presentKeys = signing.filterValues { !it.isNullOrEmpty() }.keys
    if (presentKeys.isNotEmpty()) {
        check(presentKeys.size == signingKeys.size) {
            "Release signing needs all of $signingKeys; missing ${signingKeys - presentKeys}"
        }
        signingConfigs {
            create("release") {
                storeFile = file(signing.getValue("KEYSTORE_PATH").orEmpty())
                storePassword = signing.getValue("KEYSTORE_PASSWORD")
                keyAlias = signing.getValue("KEY_ALIAS")
                keyPassword = signing.getValue("KEY_PASSWORD")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.foundation)
    implementation(libs.koin.android)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.glance.appwidget)
}
