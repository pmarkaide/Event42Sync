import io.github.cdimascio.dotenv.dotenv

fun main() {
    // Load .env file
    val dotenv = dotenv()
    val clientId = dotenv["UID"] ?: error("UID is missing in .env file")
    val clientSecret = dotenv["SECRET"] ?: error("SECRET is missing in .env file")

    println("UID: $clientId")
    println("SECRET $clientSecret")
}