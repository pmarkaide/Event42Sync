
plugins {
    kotlin("jvm") version "1.9.24"
    application
}


repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-netty:2.3.0")
    implementation("io.ktor:ktor-client-core:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")
    implementation("io.ktor:ktor-client-serialization:2.3.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")
}

application {
    mainClass.set("MainKt")
}

