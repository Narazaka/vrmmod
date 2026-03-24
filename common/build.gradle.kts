architectury {
    common(rootProject.property("enabled_platforms").toString().split(","))
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")

    modApi("dev.architectury:architectury:${rootProject.property("architectury_api_version")}")
}
