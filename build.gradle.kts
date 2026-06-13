plugins {
    // These are applied via convention plugins in build-logic. The apply false declarations
    // register the JARs on the main project's buildscript classpath so the convention
    // plugins can find and apply them at runtime.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktlintGradle) apply false
    // Still applied directly in composeApp:
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}
