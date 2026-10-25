plugins {
    java
}

group = "dev.yuan"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}


// All compile-time dependencies come from public Maven repositories, so a fresh clone
// needs no local jars. build.ps1 uses the same coordinates and downloads into libs/.
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.momirealms:craft-engine-core:26.8.2")
    compileOnly("net.momirealms:craft-engine-bukkit:26.8.2")
    compileOnly("net.momirealms:craft-engine-bukkit-proxy:26.8.2")
    compileOnly("dev.tako:papersdelight-api:4.0.0")
    compileOnly("com.google.guava:guava:33.4.0-jre")
    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly("net.kyori:adventure-key:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-commons:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.26.1")
    compileOnly("net.kyori:examination-api:1.3.0")
    compileOnly("org.jetbrains:annotations:26.0.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// The runtime PackInstaller reads pack.zip from the jar and deploys it to CraftEngine,
// so the whole content pack is bundled as one archive at the jar root.
val packArchive by tasks.registering(Zip::class) {
    archiveFileName.set("pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    from(layout.projectDirectory.dir("pack"))
}

tasks.jar {
    archiveFileName.set("YuanGuoDelight-${project.version}.jar")
    from(packArchive)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}