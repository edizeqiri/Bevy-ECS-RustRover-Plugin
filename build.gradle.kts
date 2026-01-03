plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "ch.edizeqiri"
version = "1.0.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://plugins.jetbrains.com/maven")
    }
    intellijPlatform {
        defaultRepositories()
        marketplace()
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("RR", "2025.3.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
        // Rust plugin (marketplace plugin - requires version)
        // Find the correct version at: https://plugins.jetbrains.com/plugin/8182-rust/versions
        // Look for a version compatible with IntelliJ IDEA 2024.2.5
        bundledPlugin("com.jetbrains.rust")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253.29346.139"
        }

        changeNotes = """
      <ul>
        <li>Initial release</li>
        <li>Scan and display Bevy ECS Messages, Components, and Systems</li>
        <li>Organized by file/module structure</li>
        <li>Auto-refresh on file changes</li>
        <li>Navigate to definitions with double-click</li>
      </ul>
    """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
//    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//        compilerOptions.jvmTarget = "21"
//    }
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        // or DuplicatesStrategy.INCLUDE to keep the last one
    }
}
sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
        }
    }
}