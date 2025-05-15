import org.jetbrains.exposed.gradle.configurePublishingVersionCatalog

plugins {
    `version-catalog`
}

group = "org.jetbrains.exposed"

catalog {
    versionCatalog {
        val version: String by rootProject

        rootProject.subprojects.forEach { subproject ->
            if (subproject.plugins.hasPlugin("maven-publish") && subproject.name != name) {
                val name = subproject.name
                if (name.startsWith("exposed-")) {
                    val shortName = name.removePrefix("exposed-")
                    library(shortName.replace("-", "."), "org.jetbrains.exposed:$name:$version")
                } else if (name == "spring-transaction") {
                    library("spring.transaction", "org.jetbrains.exposed:$name:$version")
                }
            }
        }
    }
}

configurePublishingVersionCatalog()
