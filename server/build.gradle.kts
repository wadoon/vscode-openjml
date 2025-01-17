//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    //kotlin("jvm") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("java")
    id("application")
}

//name = "openjml-lsp"

application {
    mainClassName = "com.github.wadoon.openjmllsp.App"
}

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/wadoon/jmlparser")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    //testImplementation(kotlin("test"))
    implementation("org.tinylog:tinylog-api-kotlin:2.5.0-M2.1")
    implementation("org.tinylog:tinylog-api:2.5.0-M2.1")
    implementation("org.tinylog:tinylog-impl:2.5.0-M2.1")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.14.0")
    implementation("org.key:jmlparser-symbol-solver-core:3.24.1-SNAPSHOT")
    testImplementation("com.google.truth:truth:1.1.3")

    implementation(files("$rootDir/libs/openjml.jar", "$rootDir/libs/jmlruntime.jar", "$rootDir/libs/jmlspecs.jar"))
}

/*
tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}*/