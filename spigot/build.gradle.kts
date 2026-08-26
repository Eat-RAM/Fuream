plugins {
    `java`
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

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.jar {
    enabled = false
}

fun registerVariant(taskName: String, fileName: String, resourceDir: String) =
    tasks.register<Jar>(taskName) {
        group = "build"
        description = "Builds $fileName"
        dependsOn(tasks.classes, project(":api").tasks.jar)
        archiveFileName.set(fileName)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(sourceSets.main.get().output) {
            exclude("plugin.yml")
        }
        from(resourceDir)
        from(configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }) {
            exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
        }
        from(rootProject.file("LICENSE")) { into("META-INF") }
        from(zipTree(project(":api").tasks.jar.get().archiveFile)) { include("**/*.class") }
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
