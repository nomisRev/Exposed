plugins {
    kotlin("jvm")
    `version-catalog`
    `maven-publish`
    alias(libs.plugins.dokka)
}

import org.jetbrains.exposed.gradle.configurePublishingVersionCatalog

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

val version: String by rootProject

catalog {
    versionCatalog {
        create("exposed") {
            library("core", "org.jetbrains.exposed:exposed-core:${version}")
            library("dao", "org.jetbrains.exposed:exposed-dao:${version}")
            library("jdbc", "org.jetbrains.exposed:exposed-jdbc:${version}")
            library("java-time", "org.jetbrains.exposed:exposed-java-time:${version}")
            library("jodatime", "org.jetbrains.exposed:exposed-jodatime:${version}")
            library("json", "org.jetbrains.exposed:exposed-json:${version}")
            library("kotlin-datetime", "org.jetbrains.exposed:exposed-kotlin-datetime:${version}")
            library("crypt", "org.jetbrains.exposed:exposed-crypt:${version}")
            library("migration", "org.jetbrains.exposed:exposed-migration:${version}")
            library("money", "org.jetbrains.exposed:exposed-money:${version}")
            library("spring-boot-starter", "org.jetbrains.exposed:exposed-spring-boot-starter:${version}")
            library("spring-transaction", "org.jetbrains.exposed:spring-transaction:${version}")
        }
    }
}

configurePublishingVersionCatalog()
