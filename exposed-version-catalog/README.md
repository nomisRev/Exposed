# Exposed Version Catalog

This module provides a [Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog) for Exposed, making it easier to manage Exposed dependencies in your project.

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

You can reference individual libraries:

```kotlin
dependencies {
    implementation(exposed.core)
    implementation(exposed.dao)
    implementation(exposed.jdbc)
    
    // Time modules
    implementation(exposed.java.time)
    // OR
    implementation(exposed.joda.time)
    // OR
    implementation(exposed.kotlin.datetime)
    
    // Additional modules
    implementation(exposed.spring.transaction)
    implementation(exposed.spring.boot.starter)
    implementation(exposed.money)
    implementation(exposed.crypt)
    implementation(exposed.json)
    implementation(exposed.migration)
}
```

Or use predefined bundles:

```kotlin
dependencies {
    // Core bundle (core, dao, jdbc)
    implementation(exposed.bundles.core)
    
    // With Java Time bundle (core, dao, jdbc, java-time)
    implementation(exposed.bundles.with.java.time)
    
    // With Joda Time bundle (core, dao, jdbc, joda-time)
    implementation(exposed.bundles.with.joda.time)
    
    // With Kotlin DateTime bundle (core, dao, jdbc, kotlin-datetime)
    implementation(exposed.bundles.with.kotlin.datetime)
    
    // Spring bundle (core, dao, jdbc, spring-transaction)
    implementation(exposed.bundles.spring)
    
    // Spring Boot bundle (core, dao, jdbc, spring-transaction, spring-boot-starter)
    implementation(exposed.bundles.spring.boot)
}
```

## Benefits

- **Simplified dependency management**: No need to specify versions for each Exposed module
- **Consistent versions**: All Exposed modules will use the same version
- **Convenient bundles**: Predefined bundles for common use cases
- **Type-safe accessors**: IDE auto-completion for dependencies