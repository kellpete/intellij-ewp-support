import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

// NO_COMPATIBILITY: no Kotlin stubs for interface defaults (avoids Plugin Verifier
// flagging ToolWindowFactory.isApplicable / manage / getIcon as overridden).
kotlin {
    compilerOptions {
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // 5.18+ ships Byte Buddy with Java 25 support (needed for Mockito inline mocks)
    testImplementation("org.mockito:mockito-core:5.24.0")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        clion("2026.2")
        bundledPlugin("com.intellij.clion")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.CLion)
    }
}
