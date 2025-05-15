# Exposed Version Catalog

This module provides a Gradle Version Catalog for the Exposed library. It allows users to easily manage Exposed dependencies in their Gradle projects.

## Usage

Add the following to your `settings.gradle.kts` file:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("exposed") {
            from("org.jetbrains.exposed:exposed-version-catalog:x.x.x")
        }
    }
}
```

Then, in your `build.gradle.kts` file, you can use the version catalog to add dependencies:

```kotlin
dependencies {
    implementation(exposed.jdbc)
    implementation(exposed.kotlin.datetime)
    // Add other Exposed modules as needed
}
```

## Available Dependencies

The version catalog includes all Exposed modules with the following naming convention:

- `exposed.core` - Core Exposed functionality
- `exposed.dao` - DAO API
- `exposed.jdbc` - JDBC support
- `exposed.java.time` - Java Time support
- `exposed.jodatime` - Joda Time support
- `exposed.kotlin.datetime` - Kotlin DateTime support
- `exposed.json` - JSON support
- `exposed.crypt` - Cryptography support
- `exposed.money` - Money support
- `exposed.migration` - Database migration support
- `exposed.spring.transaction` - Spring transaction support
- `exposed.spring.boot.starter` - Spring Boot starter

## Implementation Details

The version catalog is generated from the Exposed project and includes all modules with their correct versions. Dependencies are grouped under the name `exposed`, followed by their module name.

For example, for the Gradle module `kotlin-datetime` the resulting version catalog definition is `exposed.kotlin.datetime` which is defined as:

```kotlin
catalog {
    versionCatalog {
        create("exposed") {
            library("kotlin-datetime", "org.jetbrains.exposed:exposed-kotlin-datetime:${version}")
        }
    }
}
```
