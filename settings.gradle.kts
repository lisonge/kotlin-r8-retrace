pluginManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}

rootProject.name = "kotlin-r8-retrace"

include(":retrace")
include(":sample-compose")
