plugins {
    java
}

group = "dev.yuan"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
}

// CraftEngine 26.8.2 and Paper are resolved from the jars in libs/ so the project also
// builds completely offline. Swap these for the coordinates below if you prefer Maven:
//   compileOnly("net.momirealms:craft-engine-core:26.8.2")
//   compileOnly("net.momirealms:craft-engine-bukkit:26.8.2")
//   compileOnly("net.momirealms:craft-engine-bukkit-proxy:26.8.2")
//   compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
//
// Paper's published API does not shade Adventure or JetBrains annotations, so the jars the
// sources import from (Adventure, Guava, PapersDelight) are listed explicitly here.
// build.ps1 uses the exact same list.

dependencies {
    compileOnly(files("libs/paper-api-1.21.11.jar"))
    compileOnly(files("libs/craft-engine-core-26.8.2.jar"))
    compileOnly(files("libs/craft-engine-bukkit-26.8.2.jar"))
    compileOnly(files("libs/craft-engine-bukkit-proxy-26.8.2.jar"))
    compileOnly(files("libs/RealisticSeasons-11.12.0.jar"))
    compileOnly(files("libs/PapersDelight-1.2.0.jar"))
    compileOnly(files("libs/guava-33.4.0-jre.jar"))
    compileOnly(files("libs/adventure-api-4.26.1.jar"))
    compileOnly(files("libs/adventure-key-4.26.1.jar"))
    compileOnly(files("libs/adventure-text-serializer-commons-4.26.1.jar"))
    compileOnly(files("libs/adventure-text-serializer-legacy-4.26.1.jar"))
    compileOnly(files("libs/examination-api-1.3.0.jar"))
    compileOnly(files("libs/annotations-26.0.2.jar"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    archiveFileName.set("YuanGuoDelight-${project.version}.jar")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}
