plugins {
    `kotlin-dsl`
}

dependencies {
    // compileOnly: provides type safety for extension DSLs (kotlin {}, android {}, etc.)
    // but keeps plugin JARs off the runtime classpath so they don't auto-apply on other modules.
    // Plugin IDs are resolved at build time via pluginManagement in settings.gradle.kts.
    compileOnly(libs.build.agp)
    compileOnly(libs.build.kotlin.gradle)
    compileOnly(libs.build.detekt.gradle)
    compileOnly(libs.build.ktlint.gradle)
}
