plugins {
    kotlin("jvm") version "1.9.24"
    application
    kotlin("plugin.serialization") version "1.9.24"
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Client and Server
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-netty:2.3.0")
    implementation("io.ktor:ktor-client-core:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")
    implementation("io.ktor:ktor-client-serialization:2.3.0")

    // Dotenv for environment variable management
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.2")

    // Kotlin Coroutines (Needed for suspending functions)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Kotlinx Serialization for JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}

application {
    mainClass.set("com.Event42Sync.MainKt")
}