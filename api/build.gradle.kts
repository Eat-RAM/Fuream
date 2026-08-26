plugins {
    `java`
}

group = "io.github.eat-ram.fuream-api"
version = "0.0.1"

base {
    archivesName = "fuream-api"
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2")
}

val targetJavaVersion = 8
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
}
