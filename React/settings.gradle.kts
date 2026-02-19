import java.io.File

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "React"

fun hasVolmLibSettings(directory: File): Boolean {
    return directory.resolve("settings.gradle.kts").exists() || directory.resolve("settings.gradle").exists()
}

fun resolveLocalVolmLibDirectory(): File? {
    val configuredPath: String? = providers.gradleProperty("localVolmLibDirectory")
        .orElse(providers.environmentVariable("VOLMLIB_DIR"))
        .orNull
    if (!configuredPath.isNullOrBlank()) {
        val configuredDirectory: File = file(configuredPath)
        if (hasVolmLibSettings(configuredDirectory)) {
            return configuredDirectory
        }
    }

    var currentDirectory: File? = settingsDir
    while (currentDirectory != null) {
        val candidate: File = currentDirectory.resolve("VolmLib")
        if (hasVolmLibSettings(candidate)) {
            return candidate
        }

        currentDirectory = currentDirectory.parentFile
    }

    return null
}

val useLocalVolmLib: Boolean = providers.gradleProperty("useLocalVolmLib")
    .orElse("true")
    .map { value: String -> value.equals("true", ignoreCase = true) }
    .get()
val localVolmLibDirectory: File? = resolveLocalVolmLibDirectory()

if (useLocalVolmLib && localVolmLibDirectory != null) {
    includeBuild(localVolmLibDirectory) {
        dependencySubstitution {
            substitute(module("com.github.VolmitSoftware:VolmLib")).using(project(":shared"))
            substitute(module("com.github.VolmitSoftware.VolmLib:shared")).using(project(":shared"))
            substitute(module("com.github.VolmitSoftware.VolmLib:volmlib-shared")).using(project(":shared"))
        }
    }
}
