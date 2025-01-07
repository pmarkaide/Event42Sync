plugins {
    kotlin("jvm") version "1.9.24"
    application
    kotlin("plugin.serialization") version "1.9.24"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation("io.ktor:ktor-client-content-negotiation:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")

    // Dotenv for environment variable management
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.2")

    // Kotlin Coroutines (Needed for suspending functions)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Kotlinx Serialization for JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // google
    implementation("com.google.auth:google-auth-library-oauth2-http:1.30.1")

    //SLF4 error fix
    implementation("org.slf4j:slf4j-nop:2.0.6")
    implementation("io.ktor:ktor-client-cio-jvm:2.3.0")

    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")

    // PostgresSQL
    implementation("org.postgresql:postgresql:42.7.1")

    // AWS
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.3")
    implementation("com.amazonaws:aws-java-sdk-ssm:1.12.+")

}

application {
    mainClass.set("com.Event42Sync.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("event42sync")
    archiveClassifier.set("")
    archiveVersion.set("")
}