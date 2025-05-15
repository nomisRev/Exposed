plugins {
    `version-catalog`
    `maven-publish`
    signing
}

group = "org.jetbrains.exposed"
version = rootProject.version

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

publishing {
    publications {
        create<MavenPublication>("versionCatalog") {
            from(components["versionCatalog"])
            artifactId = "exposed-version-catalog"

            pom {
                name.set("Exposed Version Catalog")
                description.set("Version Catalog for Exposed, an ORM framework for Kotlin")
                url.set("https://github.com/JetBrains/Exposed")

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("JetBrains")
                        name.set("JetBrains Team")
                        organization.set("JetBrains")
                        organizationUrl.set("https://www.jetbrains.com")
                    }
                }

                scm {
                    url.set("https://github.com/JetBrains/Exposed")
                    connection.set("scm:git:git://github.com/JetBrains/Exposed.git")
                    developerConnection.set("scm:git:git@github.com:JetBrains/Exposed.git")
                }
            }

            // Sign the publication if the key is present
            val keyId = System.getenv("exposed.sign.key.id")
            val signingKey = System.getenv("exposed.sign.key.private")
            val signingKeyPassphrase = System.getenv("exposed.sign.passphrase")
            if (!signingKey.isNullOrBlank()) {
                extensions.configure<SigningExtension>("signing") {
                    useInMemoryPgpKeys(keyId, signingKey.replace(" ", "\r\n"), signingKeyPassphrase)
                    sign(this@create)
                }
            }
        }

        repositories {
            val publishingUsername: String? = System.getenv("PUBLISHING_USERNAME")
            val publishingPassword: String? = System.getenv("PUBLISHING_PASSWORD")

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
}
