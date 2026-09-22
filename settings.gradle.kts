// NOTE ON REPOSITORY ORDER
// "maven-central.storage-download.googleapis.com" is Google's read-only mirror
// of Maven Central. It is listed first because repo.maven.apache.org
// aggressively rate-limits shared/CI egress IPs with HTTP 429, which breaks
// otherwise healthy builds. mavenCentral() stays in the list as a fallback, so
// nothing here depends on the mirror being reachable.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
        }
        mavenCentral()
    }
}

rootProject.name = "PacketBastion"
include(":app")
