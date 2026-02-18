/*
 * React is Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.github.slimjar.func.slimjarHelper
import io.github.slimjar.resolver.data.Mirror
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.net.URI
import kotlin.system.exitProcess

plugins {
    java
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-rc3"
    id("de.crazydev22.slimjar") version "2.1.8"
}

version = "2.0.0-Dev1" // Needs to be version specific
val apiVersion = "1.19"
val pluginName = rootProject.name // Defined in settings.gradle.kts
val mainClass = "art.arcane.react.React"
val lib = "art.arcane.react.util.arcane"
val volmLibCoordinate: String = providers.gradleProperty("volmLibCoordinate")
    .orElse("com.github.VolmitSoftware:VolmLib:master-SNAPSHOT")
    .get()

fun registerCustomOutputTask(name: String, path: String) {
    if (!System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }
    createOutputTask(name, path)
}

fun registerCustomOutputTaskUnix(name: String, path: String) {
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }
    createOutputTask(name, path)
}

fun createOutputTask(name: String, path: String) {
    tasks.register<Copy>("build$name") {
        group = "development"
        outputs.upToDateWhen { false }
        dependsOn(tasks.named("shadowJar"))
        from(layout.buildDirectory.file("libs/React-${project.version}.jar"))
        into(file(path))
        rename { fileName ->
            fileName.replace("React-${project.version}.jar", "React.jar")
        }
    }
}

// ADD YOURSELF AS A NEW LINE IF YOU WANT YOUR OWN BUILD TASK GENERATED
// ======================== WINDOWS =============================
registerCustomOutputTask("Cyberpwn", "C://Users/cyberpwn/Documents/development/server/plugins")
registerCustomOutputTask("Psycho", "C://Dan/MinecraftDevelopment/server/plugins")
registerCustomOutputTask("ArcaneArts", "C://Users/arcane/Documents/development/server/plugins")
registerCustomOutputTask("Vatuu", "D://Minecraft/Servers/1.19/plugins")
registerCustomOutputTask("Nowhere", "E://Desktop/server/plugins")
registerCustomOutputTask("Pixel", "C://Users//repix//Iris Dimension Engine//1.20.4 - Development/plugins")
registerCustomOutputTask("CrazyDev22", "C:\\Users\\Julian\\Desktop\\server\\plugins")
// ========================== UNIX ==============================
registerCustomOutputTaskUnix("CyberpwnLT", "/Users/danielmills/development/server/plugins")
registerCustomOutputTaskUnix("PsychoLT", "/Users/brianfopiano/Developer/RemoteGit/[Minecraft Server]/consumers/plugin-consumers/dropins/plugins")
// ==============================================================

/**
 * Expand properties into plugin yml
 */
val pluginYmlProperties = mapOf(
    "name" to pluginName,
    "version" to version.toString(),
    "main" to mainClass,
    "apiversion" to apiVersion,
)

tasks.processResources {
    inputs.properties(pluginYmlProperties)
    filesMatching("**/plugin.yml") {
        expand(pluginYmlProperties)
    }
}

repositories {
    mavenCentral()
    maven("https://www.jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}

dependencies {
    // Provided or Classpath
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("io.papermc:paperlib:1.0.7")

    // Shaded
    implementation(slimjarHelper("spigot"))
    implementation(volmLibCoordinate) {
        isChanging = true
        isTransitive = false
    }
    slim("com.github.VolmitDev:Curse:23.4.3")
    slim("com.github.VolmitDev:MultiBurst:22.9.2")
    slim("com.github.VolmitDev:Chrono:22.9.10")
    slim("com.github.VolmitDev:Spatial:22.11.1")
    slim("com.moandjiezana.toml:toml4j:0.7.2")
    slim("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Random API's
    compileOnly("me.clip:placeholderapi:2.11.6")

    // Dynamically Loaded (via plugin.yml libraries)
    compileOnly("net.kyori:adventure-text-minimessage:4.24.0")
    compileOnly("net.kyori:adventure-platform-bukkit:4.4.1")
    compileOnly("com.github.oshi:oshi-core:5.8.5")
    compileOnly("com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2")
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.0.6")

    // Provided by Paper
    compileOnly("net.kyori:adventure-api:4.9.3")
    compileOnly("it.unimi.dsi:fastutil:8.5.8")
    compileOnly("org.apache.commons:commons-lang3:3.12.0")
    compileOnly("com.google.code.gson:gson:2.10")
    compileOnly("io.netty:netty-transport:4.1.92.Final")
}

slimJar {
    mirrors = listOf(Mirror(
        URI.create("https://maven-central.storage-download.googleapis.com/maven2").toURL(),
        URI.create("https://repo.maven.apache.org/maven2/").toURL()
    ))
    relocate("art.arcane.chrono", "$lib.chrono")
    relocate("art.arcane.curse", "$lib.curse")
    relocate("art.arcane.edict", "$lib.edict")
    relocate("art.arcane.multiburst", "$lib.multiburst")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.compileJava {
    // We need parameter meta for the decree command system
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<ShadowJar>("shadowJar") {
    // Configure React for shading
    // React/Curse rely on reflection/decompiler internals; minimization strips required classes.
    // minimize()
    dependsOn(tasks.processResources)
    from(sourceSets.main.get().output)
    archiveClassifier.set("")
    relocate("io.github.slimjar", "$lib.slimjar")
    relocate("art.arcane.chrono", "$lib.chrono")
    relocate("art.arcane.curse", "$lib.curse")
    relocate("art.arcane.edict", "$lib.edict")
    relocate("art.arcane.multiburst", "$lib.multiburst")
    relocate("art.arcane.volmlib", "$lib.volmlib")
}

tasks.named("build") {
    dependsOn("shadowJar")
}

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    System.err.println()
    System.err.println("=========================================================================================================")
    System.err.println("You must run gradle on Java 21 or newer. You are using ${JavaVersion.current()}")
    System.err.println()
    System.err.println("=== For IDEs ===")
    System.err.println("1. Configure the project for Java 21")
    System.err.println("2. Configure the bundled gradle to use Java 21 in settings")
    System.err.println()
    System.err.println("=== For Command Line (gradlew) ===")
    System.err.println("1. Install JDK 21 from https://www.oracle.com/java/technologies/downloads/#java21")
    System.err.println("2. Set JAVA_HOME environment variable to the new jdk installation folder such as C:/Program Files/Java/jdk-21")
    System.err.println("3. Open a new command prompt window to get the new environment variables if need be.")
    System.err.println("=========================================================================================================")
    System.err.println()
    exitProcess(69)
}
