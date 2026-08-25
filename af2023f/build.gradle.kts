import rege.rege.minecraftmod.ModVersion

plugins {
    id("fabric-loom") version "1.6-SNAPSHOT"
    `maven-publish`
}

/**
 * Properties migrated from `gradle.properties` of this project.
 * @author REGE
 * @since 0.0.1
 */
object ProjectProperties {
    // Fabric Properties
    // check these on https://fabricmc.net/develop
    /**
     * Original `minecraft_version` in `gradle.properties`.
     * @author REGE
     * @since 0.0.1
     */
    const val MC_VER = "23w13a_or_b"

    /**
     * Original `yarn_mappings` in `gradle.properties`.
     * @author REGE
     * @since 0.0.1
     */
    const val YARN_MAPPINGS = "23w13a_or_b+build.4"

    /**
     * Original `loader_version` in `gradle.properties`.
     * @author REGE
     * @since 0.0.1
     */
    const val LOADER_VER = "0.19.3"

    // Mod Properties
    /**
     * Original `mod_version` in `gradle.properties`.
     * @see rege.rege.minecraftmod.ModVersion
     * @author REGE
     * @since 0.0.1
     */
    val MOD_VER = ModVersion(1, 0, 0, 1, addition = "mc1.20.1")

    /**
     * Original `maven_group` in `gradle.properties`.
     * @author REGE
     * @since 0.0.1
     */
    const val MAVEN_GROUP = "io.github.eat-ram.fuream"

    /**
     * Original `archives_base_name` in `gradle.properties`.
     * @author REGE
     * @since 0.0.1
     */
    const val ARCHIVES_BASE_NAME = "fuream"

    // Dependencies
    // check this on https://modmuss50.me/fabric.html
    /**
     * Original `fabric_version` in `gradle.properties`.
     * @author REGE
     * @since 0.0.1
     */
    const val FABRIC_API_VER = "0.76.3+23w13a_or_b"
}

evaluationDependsOn(":api")

base {
    archivesName = ProjectProperties.ARCHIVES_BASE_NAME
    project.version = ProjectProperties.MOD_VER.toString()
    project.group = ProjectProperties.MAVEN_GROUP
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}


loom {
    splitEnvironmentSourceSets()

    mods {
        create("fuream") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.named("client").get())
        }
    }
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${ProjectProperties.MC_VER}")
    mappings("net.fabricmc:yarn:${ProjectProperties.YARN_MAPPINGS}:v2")
    modImplementation(
        "net.fabricmc:fabric-loader:${ProjectProperties.LOADER_VER}"
    )
    implementation(project(":api")) {
        isTransitive = false
    }

    // Fabric API. This is technically optional, but you probably want it anyway.
    //modImplementation(
    //    "net.fabricmc.fabric-api:fabric-api:${ProjectProperties.FABRIC_API_VER}"
    //)
}

var targetJavaVersion = 17
tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
    from(zipTree(project(":api").tasks.jar.get().archiveFile)) {
        include("**/*.class")
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = ProjectProperties.ARCHIVES_BASE_NAME
            from(components.findByName("java"))
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}