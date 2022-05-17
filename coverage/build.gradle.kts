import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.asm)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("coverage.jar")
    manifest {
        attributes["Premain-Class"] = "de.skyrising.javafuzzer.coverage.PreMain"
    }
    relocate("org.objectweb.asm", "de.skyrising.javafuzzer.coverage.shadow.asm")
}