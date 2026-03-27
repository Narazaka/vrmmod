import java.util.Base64
import java.security.SecureRandom

architectury {
    common(rootProject.property("enabled_platforms").toString().split(","))
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")

    modApi("dev.architectury:architectury:${rootProject.property("architectury_api_version")}")

    // JglTF for glTF/VRM parsing (api so it's available on platform classpath for Architectury Transformer)
    api("de.javagl:jgltf-model:2.0.4")

    // JOML for tests (Minecraft includes it at runtime via LWJGL, but tests need it explicitly)
    testImplementation("org.joml:joml:1.10.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}

val generateVRoidHubSecrets by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sources/vroidhub/net/narazaka/vrmmod/vroidhub")
    val jsonFile = rootProject.file("vrmmod-vroidhub-secrets.json")
    inputs.file(jsonFile).optional()
    inputs.property("envClientId", System.getenv("VROIDHUB_CLIENT_ID") ?: "")
    inputs.property("envClientSecret", System.getenv("VROIDHUB_CLIENT_SECRET") ?: "")
    outputs.dir(outputDir)

    doLast {
        // 優先順: 環境変数 > JSONファイル > 空
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

        // ビルドごとにランダムなXORキーを生成
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

        // XORキーを3分割（分割位置もランダム）
        val splitA = random.nextInt(xorKey.length / 3) + 1
        val splitB = splitA + random.nextInt(xorKey.length / 3) + 1
        val keyPart1 = xorKey.substring(0, splitA)
        val keyPart2 = xorKey.substring(splitA, splitB)
        val keyPart3 = xorKey.substring(splitB)

        // private 変数名・メソッド名をランダム化
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
