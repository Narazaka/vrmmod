import gg.meza.stonecraft.mod
import java.util.Base64
import java.security.SecureRandom

plugins {
    id("gg.meza.stonecraft")
    id("com.gradleup.shadow") version "8.3.10"
    kotlin("jvm") version "2.1.0"
}

val loader = mod.loader // "fabric", "neoforge", or "forge"

// Stonecutter version constants for conditional compilation
stonecutter {
    val mcVersion = stonecutter.current.version
    constants["HAS_RENDER_STATE"] = eval(mcVersion, ">=1.21.2")
    constants["HAS_INTERACTION_RANGE"] = eval(mcVersion, ">=1.20.5")
    constants["HAS_CUSTOM_PAYLOAD"] = eval(mcVersion, ">=1.20.5")
    constants["HAS_APPROXIMATE_NEAREST"] = eval(mcVersion, ">=1.21.4")
    constants["HAS_ITEM_RENDER_STATE"] = eval(mcVersion, ">=1.21.2")
    constants["HAS_NEW_VERTEX_API"] = eval(mcVersion, ">=1.21")
    constants["HAS_RESOURCE_LOCATION_FACTORY"] = eval(mcVersion, ">=1.21")
    constants["HAS_TICK_COUNTER"] = eval(mcVersion, ">=1.21")
    constants["HAS_BLOCKPOS_CONTAINING"] = eval(mcVersion, ">=1.20.2")
}

// Stonecraft handles: architectury loom, java version, minecraft deps, mappings, etc.
modSettings {
    // Separate run directories per version+loader to avoid config/world conflicts
    runDirectory = rootProject.layout.projectDirectory.dir("run/${stonecutter.current.version}-$loader")

    // Custom variable replacements for mod metadata templates
    variableReplacements.set(mapOf(
        "architecturyVersion" to mod.prop("architectury_api_version").substringBefore(".") + ".0.0",
    ))

    clientOptions {
        fov = 90
        guiScale = 3
        narrator = false
    }
}

// Override Stonecraft's hardcoded --username=developer.
// Also add a "client2" run config for multiplayer testing with a different username.
afterEvaluate {
    val loom = extensions.getByType<net.fabricmc.loom.api.LoomGradleExtensionAPI>()
    loom.runConfigs.matching { it.environment == "client" }.configureEach {
        programArgs.removeIf { it.startsWith("--username") }
    }
    loom.runConfigs.create("client2") {
        client()
        programArgs("--username=Player2")
    }
}

// NeoForge's module classloader needs loom.mods to know which source sets belong to the mod.
// Without this, Mixin fails with ClassNotFoundException for the mod's own Kotlin classes in dev.
if (mod.isNeoforge || mod.isForge) {
    loom {
        mods {
            register(mod.id) {
                sourceSet(sourceSets.getByName("main"))
            }
        }
    }
}

// Forge needs explicit mixin config registration for dev environment
if (mod.isForge) {
    loom {
        forge {
            mixinConfigs("vrmmod.mixins.json")
        }
    }
}

// ---- Dependencies ----

repositories {
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    mavenCentral()
}

val shadowBundle: Configuration by configurations.creating

dependencies {
    modApi("dev.architectury:architectury-${loader}:${mod.prop("architectury_api_version")}")

    // JglTF for glTF/VRM parsing - bundled via shadow JAR in production.
    // NeoForge dev needs Jackson excluded (NeoForge provides it, including it causes module conflicts).
    val jgltfDep = if (mod.isNeoforge) {
        implementation("de.javagl:jgltf-model:2.0.4") {
            exclude(group = "com.fasterxml.jackson.core")
            exclude(group = "com.fasterxml.jackson")
        }
    } else {
        implementation("de.javagl:jgltf-model:2.0.4")
    }
    shadowBundle("de.javagl:jgltf-model:2.0.4")
    // NeoForge dev: JglTF must be on forgeRuntimeLibrary so the module classloader can find it.
    // forgeRuntimeLibrary is a NeoForge/Loom configuration that adds libraries to the mod's module layer.
    // In production, shadow JAR handles bundling. Fabric dev uses flat classpath so implementation() suffices.
    // Note: Jackson is NOT excluded here — NeoForge 1.21.1 doesn't provide all Jackson classes JglTF needs.
    if (mod.isNeoforge || mod.isForge) {
        "forgeRuntimeLibrary"("de.javagl:jgltf-model:2.0.4")
    }

    if (mod.isFabric) {
        modImplementation("net.fabricmc:fabric-language-kotlin:${mod.prop("fabric_language_kotlin_version")}")
        modImplementation("com.terraformersmc:modmenu:${mod.prop("mod_menu_version")}")
    }
    if (mod.isNeoforge) {
        implementation("thedarkcolour:kotlinforforge-neoforge:${mod.prop("kotlin_for_forge_version")}") {
            exclude(group = "net.neoforged.fancymodloader", module = "loader")
        }
    }
    if (mod.isForge) {
        implementation("thedarkcolour:kotlinforforge:${mod.prop("kotlin_for_forge_version")}")
    }

    // JOML for tests
    testImplementation("org.joml:joml:1.10.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---- Shadow JAR (bundle JglTF) ----

tasks.shadowJar {
    // Forge generates pack.mcmeta into resources — shadow must wait for it
    dependsOn(tasks.named("processResources"))
    tasks.findByName("generatePackMCMetaJson")?.let { dependsOn(it) }
    configurations = listOf(shadowBundle)
    archiveClassifier.set("dev-shadow")
    // Gson is provided by MC in all versions
    exclude("com/google/gson/**")
    // Jackson is only provided by NeoForge 1.21.4 — all other versions need it bundled.
    // Exclude only for NeoForge on >=1.21.4 to avoid module conflicts.
    if (mod.isNeoforge && stonecutter.eval(stonecutter.current.version, ">=1.21.4")) {
        exclude("com/fasterxml/**")
        exclude("META-INF/maven/com.fasterxml*/**")
        exclude("META-INF/services/com.fasterxml*")
    }
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
}

tasks.remapJar {
    injectAccessWidener.set(true)
    inputFile.set(tasks.shadowJar.get().archiveFile)
    dependsOn(tasks.shadowJar)
    archiveClassifier.set(null as String?)
}

// Don't generate sources JAR — this mod doesn't publish a Maven artifact
afterEvaluate {
    tasks.findByName("sourcesJar")?.enabled = false
    tasks.findByName("remapSourcesJar")?.enabled = false
}

tasks.processResources {
    filesMatching("vrmmod.mixins.json") {
        val javaVersion = if (stonecutter.eval(stonecutter.current.version, ">=1.20.6")) "JAVA_21" else "JAVA_17"
        filter { it.replace("JAVA_21", javaVersion) }
    }
}

// ---- VRoid Hub Secrets Generation ----

val generateVRoidHubSecrets by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sources/vroidhub/net/narazaka/vrmmod/vroidhub")
    val jsonFile = rootProject.file("vrmmod-vroidhub-secrets.json")
    if (jsonFile.exists()) inputs.file(jsonFile)
    inputs.property("envClientId", System.getenv("VROIDHUB_CLIENT_ID") ?: "")
    inputs.property("envClientSecret", System.getenv("VROIDHUB_CLIENT_SECRET") ?: "")
    outputs.dir(outputDir)

    doLast {
        var clientId = System.getenv("VROIDHUB_CLIENT_ID") ?: ""
        var clientSecret = System.getenv("VROIDHUB_CLIENT_SECRET") ?: ""

        if (clientId.isEmpty() && clientSecret.isEmpty() && jsonFile.exists()) {
            try {
                val json = groovy.json.JsonSlurper().parseText(jsonFile.readText()) as Map<*, *>
                clientId = json["clientId"]?.toString() ?: ""
                clientSecret = json["clientSecret"]?.toString() ?: ""
            } catch (e: Exception) {
                logger.warn("Failed to read vrmmod-vroidhub-secrets.json: ${e.message}")
            }
        }

        val random = SecureRandom()
        val xorKeyBytes = ByteArray(32).also { random.nextBytes(it) }
        val xorKey = Base64.getEncoder().encodeToString(xorKeyBytes)

        fun xorEncode(input: String, key: String): String {
            if (input.isEmpty()) return ""
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val encoded = input.toByteArray(Charsets.UTF_8).mapIndexed { i, b ->
                (b.toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }.toByteArray()
            return Base64.getEncoder().encodeToString(encoded)
        }

        val encodedId = xorEncode(clientId, xorKey)
        val encodedSecret = xorEncode(clientSecret, xorKey)

        val splitA = random.nextInt(xorKey.length / 3) + 1
        val splitB = splitA + random.nextInt(xorKey.length / 3) + 1
        val keyPart1 = xorKey.substring(0, splitA)
        val keyPart2 = xorKey.substring(splitA, splitB)
        val keyPart3 = xorKey.substring(splitB)

        fun randomName(): String {
            val chars = "abcdefghijklmnopqrstuvwxyz"
            val prefix = chars[random.nextInt(chars.length)]
            val hex = ByteArray(4).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            return "$prefix$hex"
        }
        val nameId = randomName()
        val nameSecret = randomName()
        val nameK1 = randomName()
        val nameK2 = randomName()
        val nameK3 = randomName()
        val nameDecode = randomName()

        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("VRoidHubSecrets.kt").writeText(
            """
            |package net.narazaka.vrmmod.vroidhub
            |
            |import java.util.Base64
            |
            |object VRoidHubSecrets {
            |    private const val $nameId = "$encodedId"
            |    private const val $nameSecret = "$encodedSecret"
            |    private val $nameK1 = "$keyPart1"
            |    private val $nameK2 = "$keyPart2"
            |    private val $nameK3 = "$keyPart3"
            |
            |    private fun $nameDecode(encoded: String): String {
            |        if (encoded.isEmpty()) return ""
            |        val key = ($nameK1 + $nameK2 + $nameK3).toByteArray(Charsets.UTF_8)
            |        val decoded = Base64.getDecoder().decode(encoded).mapIndexed { i, b ->
            |            (b.toInt() xor key[i % key.size].toInt()).toByte()
            |        }.toByteArray()
            |        return String(decoded, Charsets.UTF_8)
            |    }
            |
            |    val defaultClientId: String get() = $nameDecode($nameId)
            |    val defaultClientSecret: String get() = $nameDecode($nameSecret)
            |
            |    fun defaultConfig(): VRoidHubConfig = VRoidHubConfig(
            |        clientId = defaultClientId,
            |        clientSecret = defaultClientSecret,
            |    )
            |}
            """.trimMargin()
        )
    }
}

sourceSets.main {
    java.srcDir(generateVRoidHubSecrets.map { layout.buildDirectory.dir("generated/sources/vroidhub") })
}

tasks.named("compileKotlin") {
    dependsOn(generateVRoidHubSecrets)
}

tasks.test {
    useJUnitPlatform()
    // Pass project root as system property so tests can resolve testdata paths
    systemProperty("project.root", rootProject.projectDir.absolutePath)
}
