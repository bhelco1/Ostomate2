import java.util.Properties

plugins {
    id("ostimate.android-app")
    alias(libs.plugins.composeCompiler)
}

// local.properties is gitignored; read it manually so secrets stay out of gradle.properties
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.reader())
}

android {
    namespace = "com.ostimate.app"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.ostimate.app"
        versionCode = 1
        versionName = "1.0"
        // local.properties: SENTRY_DSN=https://...  CI: -PSENTRY_DSN=https://...
        val dsn = localProps["SENTRY_DSN"] as String? ?: project.findProperty("SENTRY_DSN") as String? ?: ""
        buildConfigField("String", "SENTRY_DSN", "\"$dsn\"")
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
