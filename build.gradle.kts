// AGP 9 compiles Kotlin itself (no org.jetbrains.kotlin.android plugin). Declaring the
// Kotlin compose/serialization plugins here also pulls the Kotlin Gradle plugin up to the
// catalog version; AGP's own POM would otherwise drag in an older one.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
