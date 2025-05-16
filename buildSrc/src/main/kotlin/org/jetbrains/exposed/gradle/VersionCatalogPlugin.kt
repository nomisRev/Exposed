package org.jetbrains.exposed.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class VersionCatalogPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.configureVersionCatalogPublishing()
    }
}
