architectury {
    common(rootProject.property("enabled_platforms").toString().split(","))
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")

    modApi("dev.architectury:architectury:${rootProject.property("architectury_api_version")}")

    // JglTF for glTF/VRM parsing
    implementation("de.javagl:jgltf-model:2.0.4")

    // JOML for tests (Minecraft includes it at runtime via LWJGL, but tests need it explicitly)
    testImplementation("org.joml:joml:1.10.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}
