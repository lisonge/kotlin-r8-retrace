plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "li.songe"
    // don't change, we don't need maven publish
    version = "0.0.0-SNAPSHOT"
}
