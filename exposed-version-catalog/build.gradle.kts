plugins {
    `version-catalog`
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

// Import the configurePublishingVersionCatalog function
import org.jetbrains.exposed.gradle.configurePublishingVersionCatalog

// Configure publishing for the version catalog
configurePublishingVersionCatalog()

// Get the project version
val version: String by rootProject

// Define the version catalog
catalog {
    versionCatalog {
        // Create the "exposed" namespace
        create("exposed") {
            // Add all modules from the project
            library("core", "org.jetbrains.exposed:exposed-core:${version}")
            library("dao", "org.jetbrains.exposed:exposed-dao:${version}")
            library("jdbc", "org.jetbrains.exposed:exposed-jdbc:${version}")
            library("java-time", "org.jetbrains.exposed:exposed-java-time:${version}")
            library("joda-time", "org.jetbrains.exposed:exposed-jodatime:${version}")
            library("kotlin-datetime", "org.jetbrains.exposed:exposed-kotlin-datetime:${version}")
            library("json", "org.jetbrains.exposed:exposed-json:${version}")
            library("money", "org.jetbrains.exposed:exposed-money:${version}")
            library("crypt", "org.jetbrains.exposed:exposed-crypt:${version}")
            library("migration", "org.jetbrains.exposed:exposed-migration:${version}")
            library("spring-boot-starter", "org.jetbrains.exposed:exposed-spring-boot-starter:${version}")
            library("spring-transaction", "org.jetbrains.exposed:spring-transaction:${version}")
        }
    }
}
