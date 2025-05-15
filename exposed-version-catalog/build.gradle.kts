import org.jetbrains.exposed.gradle.configurePublishing

catalog {
    versionCatalog {
        create("exposed") {
            // Core modules
            library("core", "org.jetbrains.exposed:exposed-core:${version}")
            library("jdbc", "org.jetbrains.exposed:exposed-jdbc:${version}")
            library("dao", "org.jetbrains.exposed:exposed-dao:${version}")

            // Date/time modules
            library("java-time", "org.jetbrains.exposed:exposed-java-time:${version}")
            library("joda-time", "org.jetbrains.exposed:exposed-jodatime:${version}")
            library("kotlin.datetime", "org.jetbrains.exposed:exposed-kotlin-datetime:${version}")

            // Additional modules
            library("json", "org.jetbrains.exposed:exposed-json:${version}")
            library("money", "org.jetbrains.exposed:exposed-money:${version}")
            library("crypt", "org.jetbrains.exposed:exposed-crypt:${version}")
            library("migration", "org.jetbrains.exposed:exposed-migration:${version}")

            // Spring integration
            library("spring", "org.jetbrains.exposed:spring-transaction:${version}")
            library("spring.boot.starter", "org.jetbrains.exposed:exposed-spring-boot-starter:${version}")
        }
    }
}

configurePublishing(isVersionCatalog = true)
