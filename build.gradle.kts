plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "net.chikach"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.3.2")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

