import org.jetbrains.exposed.gradle.configureMavenCentralMetadata
import org.jetbrains.exposed.gradle.signPublicationIfKeyPresent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `version-catalog`
    `maven-publish`
    signing
    kotlin("jvm")
}

kotlin {
    jvmToolchain(8)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

group = "org.jetbrains.exposed"

catalog {
    versionCatalog {
        from(files("../gradle/libs.versions.toml"))

        // Define the Exposed version
        version("exposed", rootProject.version.toString())

        // Core modules
        library("core", "org.jetbrains.exposed", "exposed-core").versionRef("exposed")
        library("dao", "org.jetbrains.exposed", "exposed-dao").versionRef("exposed")
        library("jdbc", "org.jetbrains.exposed", "exposed-jdbc").versionRef("exposed")

        // Time modules
        library("java-time", "org.jetbrains.exposed", "exposed-java-time").versionRef("exposed")
        library("joda-time", "org.jetbrains.exposed", "exposed-jodatime").versionRef("exposed")
        library("kotlin-datetime", "org.jetbrains.exposed", "exposed-kotlin-datetime").versionRef("exposed")

        // Additional modules
        library("spring-transaction", "org.jetbrains.exposed", "spring-transaction").versionRef("exposed")
        library("spring-boot-starter", "org.jetbrains.exposed", "exposed-spring-boot-starter").versionRef("exposed")
        library("money", "org.jetbrains.exposed", "exposed-money").versionRef("exposed")
        library("crypt", "org.jetbrains.exposed", "exposed-crypt").versionRef("exposed")
        library("json", "org.jetbrains.exposed", "exposed-json").versionRef("exposed")
        library("migration", "org.jetbrains.exposed", "exposed-migration").versionRef("exposed")

        // Define bundles
        bundle("core", listOf("core", "dao", "jdbc"))
        bundle("with-java-time", listOf("core", "dao", "jdbc", "java-time"))
        bundle("with-joda-time", listOf("core", "dao", "jdbc", "joda-time"))
        bundle("with-kotlin-datetime", listOf("core", "dao", "jdbc", "kotlin-datetime"))
        bundle("spring", listOf("core", "dao", "jdbc", "spring-transaction"))
        bundle("spring-boot", listOf("core", "dao", "jdbc", "spring-transaction", "spring-boot-starter"))
    }
}

publishing {
    val version: String by rootProject

    publications {
        create<MavenPublication>("versionCatalog") {
            groupId = "org.jetbrains.exposed"
            artifactId = "exposed-version-catalog"
            this.version = version

            from(components["versionCatalog"])

            pom {
                configureMavenCentralMetadata(project)
                description.set("Version Catalog for Exposed, an ORM framework for Kotlin")
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
