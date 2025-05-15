import org.jetbrains.exposed.gradle.configureMavenCentralMetadata
import org.jetbrains.exposed.gradle.signPublicationIfKeyPresent

plugins {
    `version-catalog`
    `maven-publish`
    signing
}

group = "org.jetbrains.exposed"

catalog {
    versionCatalog {
        val version: String by rootProject
        
        from(files("${rootProject.projectDir}/gradle/libs.versions.toml"))
        
        // Add all exposed modules to the catalog
        rootProject.subprojects.forEach { subproject ->
            if (subproject.plugins.hasPlugin("maven-publish") && subproject.name != name) {
                val moduleName = subproject.name
                if (moduleName.startsWith("exposed-")) {
                    val shortName = moduleName.removePrefix("exposed-")
                    library(shortName.replace("-", "."), "org.jetbrains.exposed:$moduleName:$version")
                } else if (moduleName == "spring-transaction") {
                    library("spring.transaction", "org.jetbrains.exposed:$moduleName:$version")
                }
            }
        }
    }
}

publishing {
    val version: String by rootProject

    publications {
        create<MavenPublication>("versionCatalog") {
            groupId = "org.jetbrains.exposed"
            artifactId = project.name
            this.version = version
            from(components["versionCatalog"])
            pom {
                configureMavenCentralMetadata(project)
            }
            signPublicationIfKeyPresent(project)
        }
    }

    val publishingUsername: String? = System.getenv("PUBLISHING_USERNAME")
    val publishingPassword: String? = System.getenv("PUBLISHING_PASSWORD")

    repositories {
        maven {
            name = "Exposed"
            url = uri("https://maven.pkg.jetbrains.space/public/p/exposed/release")
            credentials {
                username = publishingUsername
                password = publishingPassword
            }
        }
    }
}
