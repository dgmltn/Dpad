pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "Dpad"
include(":protocol")
include(":domain")
include(":data")
include(":design")
include(":ui")
include(":app-android")
include(":app-ios-shared")
