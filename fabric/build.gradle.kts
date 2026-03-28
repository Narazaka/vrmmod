plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating
val developmentFabric: Configuration by configurations.getting

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    developmentFabric.extendsFrom(common)
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    modApi("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_api_version")}")
    modApi("dev.architectury:architectury-fabric:${rootProject.property("architectury_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${rootProject.property("fabric_language_kotlin_version")}")


    modImplementation("com.terraformersmc:modmenu:${rootProject.property("mod_menu_version")}")

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionFabric")) { isTransitive = false }

    // JglTF must be bundled into the mod jar (not provided by MC or Fabric)
    implementation("de.javagl:jgltf-model:2.0.4")
    shadowCommon("de.javagl:jgltf-model:2.0.4")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
    from(project(":common").file("src/main/resources")) {
        include("vrmmod.accesswidener")
    }
}

tasks.shadowJar {
    exclude("architectury.common.json")
    // Exclude Jackson/Gson (transitive deps of JglTF) - MC/loader already provides them
    exclude("com/fasterxml/**")
    exclude("com/google/gson/**")
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
