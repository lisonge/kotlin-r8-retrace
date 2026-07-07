plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "li.songe"
    version = "0.1.0-SNAPSHOT"
}
