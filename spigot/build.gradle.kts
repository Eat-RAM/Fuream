import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

plugins {
    `java`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.eat-ram.fuream-spigot"
version = "0.0.1"
evaluationDependsOn(":api")
project(":api") {
    repositories {
        mavenCentral()
    }
}
base {
    archivesName = "fuream"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        name = "spigotmc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.codemc.io/repository/maven-public/") {
        name = "CodeMC"
    }
}

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.17.7")
    implementation("net.bytebuddy:byte-buddy-agent:1.17.7")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("com.google.code.gson:gson:2.10.1")
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly(project(":api")) {
        isTransitive = false
    }

    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(project(":api"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

val targetJavaVersion = 8
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.jar {
    enabled = false
}
tasks.shadowJar {
    enabled = false
}

fun registerVariant(taskName: String, fileName: String, resourceDir: String) =
    tasks.register<ShadowJar>(taskName) {
        group = "build"
        description = "Builds $fileName"
        dependsOn(tasks.classes, project(":api").tasks.jar)
        archiveFileName.set(fileName)
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        configurations = listOf(project.configurations.runtimeClasspath.get())
        from(sourceSets.main.get().output) {
            exclude("plugin.yml")
        }
        from(resourceDir) {
            filesMatching("plugin.yml") {
                expand("version" to project.version)
            }
        }
        exclude(
            "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class",
            "META-INF/versions/**"
        )
        from(rootProject.file("LICENSE")) { into("META-INF") }
        from(zipTree(project(":api").tasks.jar.get().archiveFile)) { include("**/*.class") }
        mergeServiceFiles()
        relocate("net.bytebuddy", "io.github.eat_ram.fuream.libs.bytebuddy")
        relocate("com.sun.jna", "io.github.eat_ram.fuream.libs.jna")
        relocate("com.google.gson", "io.github.eat_ram.fuream.libs.gson")
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

val legacyJar = registerVariant(
    "legacyJar", "fuream-legacy-1.7-1.12.jar", "src/variants/legacy"
)
val flatteningJar = registerVariant(
    "flatteningJar", "fuream-flattening-1.13-1.20.4.jar", "src/variants/flattening"
)
val componentsJar = registerVariant(
    "componentsJar", "fuream-components-1.20.5-26.2.jar", "src/variants/components"
)

tasks.assemble {
    dependsOn(legacyJar, flatteningJar, componentsJar)
}

val verifyVariantJars = tasks.register("verifyVariantJars") {
    group = "verification"
    description = "Checks variant metadata, Java 8 bytecode, shading, and guarded API references"
    dependsOn(legacyJar, flatteningJar, componentsJar)
    doLast {
        val variants = listOf(
            legacyJar.get().archiveFile.get().asFile to null,
            flatteningJar.get().archiveFile.get().asFile to "1.13",
            componentsJar.get().archiveFile.get().asFile to "1.20.5"
        )
        val forbiddenEntries = listOf("net/bytebuddy/", "com/google/gson/", "com/sun/jna/")
        for ((jarFile, apiVersion) in variants) {
            ZipFile(jarFile).use { jar ->
                val pluginEntry = jar.getEntry("plugin.yml")
                    ?: error("${jarFile.name} has no plugin.yml")
                val pluginYml = jar.getInputStream(pluginEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                check(pluginYml.contains("version: $version")) {
                    "${jarFile.name} does not contain project version $version"
                }
                if (apiVersion == null) {
                    check(!pluginYml.contains("api-version:")) {
                        "Legacy plugin.yml must not declare api-version"
                    }
                } else {
                    check(pluginYml.contains("api-version: '$apiVersion'")) {
                        "${jarFile.name} has the wrong api-version"
                    }
                }

                val entries = jar.entries().asSequence().toList()
                check(entries.none { entry -> forbiddenEntries.any(entry.name::startsWith) }) {
                    "${jarFile.name} contains an unrelocated runtime dependency"
                }
                for (entry in entries) {
                    if (!entry.name.endsWith(".class")) continue
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }
                    val major = ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
                    check(major <= 52) { "${entry.name} in ${jarFile.name} is not Java 8 bytecode" }
                    if (jarFile == variants[0].first &&
                        entry.name != "io/github/eat_ram/fuream/hook/BlockExplodeListener.class") {
                        val constants = String(bytes, StandardCharsets.ISO_8859_1)
                        check(!constants.contains("org/bukkit/event/block/BlockExplodeEvent")) {
                            "${entry.name} directly links BlockExplodeEvent in the legacy variant"
                        }
                    }
                }
            }
        }
    }
}

tasks.check {
    dependsOn(verifyVariantJars)
}
