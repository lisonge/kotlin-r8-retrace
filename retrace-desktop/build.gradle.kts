plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }

    jvm("desktop")

    sourceSets {
        named("desktopMain") {
            dependencies {
                implementation(project(":retrace-core"))
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "retrace.desktop.MainKt"
    }
}
