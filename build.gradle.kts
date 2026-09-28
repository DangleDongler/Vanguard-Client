plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
    group = providers.gradleProperty("maven_group").get()
    version = "${providers.gradleProperty("mod_version").get()}+${libs.versions.minecraft.get()}"
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)

    // Only the Fabric API modules we need, bundled so players don't have to install Fabric API.
    // The resource loader is what makes the game load our assets (shaders, fonts).
    val fabricApiVersion = libs.versions.fabric.api.get()
    for (module in listOf("fabric-api-base", "fabric-resource-loader-v1")) {
        val dependency = fabricApi.module(module, fabricApiVersion)
        modImplementation(dependency)
        include(dependency)
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version,
            "minecraft" to libs.versions.minecraft.get(),
            "loader" to libs.versions.fabric.loader.get(),
            "jdk" to libs.versions.jdk.get(),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = libs.versions.jdk.get().toInt()
    }
}

loom {
    accessWidenerPath = file("src/main/resources/vanguard.accesswidener")
}
