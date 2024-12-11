plugins {
    kotlin("jvm") version "1.9.24"
    application
}

repositories {
    mavenCentral()
    maven("https://jitpack.io") // Added Jitpack.io repository
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.2")
}

sourceSets {
    main {
        kotlin.srcDirs("src/main/kotlin")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // Ensure Java 17 or 21 is properly set
    }
}

application {
    mainClass.set("com.Event42Sync.MainKt")
}