# Exposed Version Catalog Implementation

This module provides a [Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog) for Exposed, making it easier for users to manage Exposed dependencies in their projects.

## Changes Made

1. Created a new module `exposed-version-catalog` with the following structure:
   - `build.gradle.kts`: Configuration for generating and publishing the Version Catalog
   - `README.md`: Documentation on how to use the Version Catalog

2. Updated project configuration:
   - Added the new module to `settings.gradle.kts`
   - Updated `build.gradle.kts` to include the new module in dokka configuration
   - Updated `build.gradle.kts` to exclude the new module from API validation
   - Updated `build.gradle.kts` to exclude the new module from publishing configuration
   - Updated `build.gradle.kts` to exclude the new module from test database configuration

## Version Catalog Structure

The Version Catalog includes:

1. All Exposed modules with idiomatic naming:
   - Core modules: `core`, `dao`, `jdbc`
   - Time modules: `java-time`, `joda-time`, `kotlin-datetime`
   - Additional modules: `spring-transaction`, `spring-boot-starter`, `money`, `crypt`, `json`, `migration`

2. Predefined bundles for common use cases:
   - `core`: Core modules (core, dao, jdbc)
   - `with-java-time`: Core modules with Java Time
   - `with-joda-time`: Core modules with Joda Time
   - `with-kotlin-datetime`: Core modules with Kotlin DateTime
   - `spring`: Core modules with Spring Transaction
   - `spring-boot`: Core modules with Spring Boot Starter

## Usage

### In settings.gradle.kts

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("exposed") {
            from("org.jetbrains.exposed:exposed-version-catalog:0.60.0") // Use the latest version
        }
    }
}
```

### In build.gradle.kts

```kotlin
dependencies {
    // Individual modules
    implementation(exposed.core)
    implementation(exposed.dao)
    implementation(exposed.jdbc)
    
    // Or use predefined bundles
    implementation(exposed.bundles.core)
    implementation(exposed.bundles.with.java.time)
}
```

## Benefits

- **Simplified dependency management**: No need to specify versions for each Exposed module
- **Consistent versions**: All Exposed modules will use the same version
- **Convenient bundles**: Predefined bundles for common use cases
- **Type-safe accessors**: IDE auto-completion for dependencies