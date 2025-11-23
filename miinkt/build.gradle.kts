import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    id("fabric-loom") version "1.13.4"
}

// Extension function to make property reading cleaner
fun prop(name: String): String = project.property(name) as String

group = prop("maven_group")
version = prop("mod_version")
base.archivesName.set(prop("archives_base_name"))

repositories {
    mavenCentral()
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "Mojang"
        url = uri("https://libraries.minecraft.net/")
    }
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("MIIN") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
    mappings("net.fabricmc:yarn:${prop("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${prop("fabric_language_kotlin_version")}")

    // JSON support
    implementation("com.google.code.gson:gson:2.11.0")
    
    // MIIN Integration Dependencies
    val coroutinesVersion = "1.7.3"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    include("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    val jacksonVersion = "2.15.2"
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    include("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    include("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    include("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    include("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    val socketVersion = "1.5.4"
    implementation("org.java-websocket:Java-WebSocket:$socketVersion")
    include("org.java-websocket:Java-WebSocket:$socketVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    withType<JavaCompile> {
        options.release.set(21)
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${base.archivesName.get()}" }
        }
    }
}