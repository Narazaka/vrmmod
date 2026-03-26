plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating
val developmentNeoForge: Configuration by configurations.getting

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    developmentNeoForge.extendsFrom(common)
}

dependencies {
    "neoForge"("net.neoforged:neoforge:${rootProject.property("neoforge_version")}")
    modApi("dev.architectury:architectury-neoforge:${rootProject.property("architectury_api_version")}")
    implementation("thedarkcolour:kotlinforforge-neoforge:${rootProject.property("kotlin_for_forge_version")}") {
        exclude(group = "net.neoforged.fancymodloader", module = "loader")
    }

    modApi("me.shedaniel.cloth:cloth-config-neoforge:${rootProject.property("cloth_config_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionNeoForge")) { isTransitive = false }

    // JglTF must be bundled into the mod jar (not provided by MC or NeoForge)
    // Exclude Jackson - NeoForge already provides it, and including it causes module conflicts
    implementation("de.javagl:jgltf-model:2.0.4") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson")
    }
    // JglTF must also be on dev runtime classpath (shadow jar bundles it for production,
    // but dev environment needs it explicitly via developmentNeoForge)
    developmentNeoForge("de.javagl:jgltf-model:2.0.4") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson")
    }
    shadowCommon("de.javagl:jgltf-model:2.0.4") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    exclude("architectury.common.json")
    // Exclude Jackson (transitive dep of JglTF) - NeoForge already provides it
    exclude("com/fasterxml/**")
    exclude("META-INF/maven/com.fasterxml*/**")
    exclude("META-INF/services/com.fasterxml*")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
    configurations = listOf(shadowCommon)
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    injectAccessWidener.set(true)
    inputFile.set(tasks.shadowJar.get().archiveFile)
    dependsOn(tasks.shadowJar)
    archiveClassifier.set(null as String?)
}

tasks.sourcesJar {
    val commonSources = project(":common").tasks.getByName<Jar>("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.archiveFile.map { zipTree(it) })
}

components.getByName("java") {
    this as AdhocComponentWithVariants
    this.withVariantsFromConfiguration(project.configurations["shadowRuntimeElements"]) {
        skip()
    }
}
