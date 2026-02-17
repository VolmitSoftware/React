import java.io.File

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "React"

val useLocalVolmLib: Boolean = providers.gradleProperty("useLocalVolmLib")
    .orElse("true")
    .map { value: String -> value.equals("true", ignoreCase = true) }
    .get()
val localVolmLibDirectory: File = file("../../VolmLib")

if (useLocalVolmLib && localVolmLibDirectory.resolve("settings.gradle.kts").exists()) {
    includeBuild(localVolmLibDirectory) {
        dependencySubstitution {
            substitute(module("com.github.VolmitSoftware:VolmLib")).using(project(":shared"))
            substitute(module("com.github.VolmitSoftware.VolmLib:shared")).using(project(":shared"))
            substitute(module("com.github.VolmitSoftware.VolmLib:volmlib-shared")).using(project(":shared"))
        }
    }
}
