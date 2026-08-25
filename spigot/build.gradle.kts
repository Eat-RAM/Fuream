plugins {
    id("xyz.wagyourtail.jvmdowngrader") version "1.2.2"
    `java`
}

group = "io.github.eat-ram.fuream-spigot"
version = "0.0.1"
evaluationDependsOn(":api")
jvmdg.downgradeTo = JavaVersion.VERSION_1_8

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
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("de.tr7zw:item-nbt-api:2.14.1")
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.14.1")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly(project(":api")) {
        isTransitive = false
    }

    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("de.tr7zw:item-nbt-api-plugin:2.14.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(project(":api"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val targetJavaVersion = 17
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
    from(zipTree(project(":api").tasks.jar.get().archiveFile)) {
        include("**/*.class")
    }
}
