plugins {
    java
    kotlin("jvm") version "2.1.0" apply false
    id("architectury-plugin") version "3.4.162"
    id("dev.architectury.loom") version "1.10.430" apply false
}

architectury {
    minecraft = rootProject.property("minecraft_version").toString()
}

subprojects {
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "architectury-plugin")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    val loom = project.extensions.getByName<net.fabricmc.loom.api.LoomGradleExtensionAPI>("loom")

    repositories {
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.fabricmc.net/")
        maven("https://thedarkcolour.github.io/KotlinForForge/")
        mavenCentral()
    }

    dependencies {
        "minecraft"("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
        @Suppress("UnstableApiUsage")
        "mappings"(loom.officialMojangMappings())
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

allprojects {
    group = rootProject.property("maven_group").toString()
    version = rootProject.property("mod_version").toString()
}
