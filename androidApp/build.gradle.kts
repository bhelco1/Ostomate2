plugins {
    id("ostimate.android-app")
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.ostimate.app"
    defaultConfig {
        applicationId = "com.ostimate.app"
        versionCode = 1
        versionName = "1.0"
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
}
