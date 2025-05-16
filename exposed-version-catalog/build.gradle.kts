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
        rootProject.subprojects.filter { 
            it.plugins.hasPlugin("maven-publish") && 
            it.name != name && 
            it.name != "exposed-bom" && 
            it.name != "exposed-tests" 
        }.forEach { project ->
            val moduleName = when {
                project.name == "spring-transaction" -> "exposed.spring.transaction"
                else -> "exposed." + project.name.removePrefix("exposed-").replace("-", ".")
            }
            library(moduleName, "org.jetbrains.exposed:${project.name}:${project.version}")
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
