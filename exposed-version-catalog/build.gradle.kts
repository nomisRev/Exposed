plugins {
    id("exposed.version-catalog")
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
