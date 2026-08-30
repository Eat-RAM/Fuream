pluginManagement {
    repositories {
        maven("https://repository.hanbings.io/proxy") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}

buildscript {
    dependencies {
        classpath("rege.rege.minecraftmod:mcmodmetadata:0.0.1@jar")
    }

    repositories {
        ivy("https://gitlab.com") {
            isAllowInsecureProtocol = true

            patternLayout {
                artifact(
                "/IAmREGE/[module]/-/raw/releases/[artifact]-[revision].[ext]"
                )
            }

            // This is required in Gradle 6.0+ as metadata file (ivy.xml)
            // is mandatory. Docs linked below this code section
            metadataSources {
                artifact()
            }
        }
    }
}

rootProject.name = "Fuream"
include("api")
include("1_19_4f")
include("af2023f")
include("1_20f")
include("1_20_1f")
include("spigot")
