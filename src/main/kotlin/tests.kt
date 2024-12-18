import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable


@Serializable
data class Campus(
    val id: Int,
    val name: String,
    val country: String,
    val users_count: Int,
    val active: Boolean
)

val json = Json {
    ignoreUnknownKeys = true
}

suspend fun fetchCampusData(access_token: String): Campus {
    // Initialize Ktor client
    val client = HttpClient(CIO)
    try {
    // Make GET request to 42 API campus endpoint
    val response: HttpResponse = client.get("https://api.intra.42.fr/v2/campus") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $access_token")
        }
    }

    // Read response and check for empty body
    val jsonString = response.bodyAsText()
    if (jsonString.isEmpty()) {
        throw Exception("Received empty response from the API")
    }

    // Parse JSON response, which is an array of campuses
    val campusList = json.decodeFromString<List<Campus>>(jsonString)

    // We assume there's only one campus in the response, retrieve the first element
    return campusList.first()

    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
        throw e
    } finally {
        client.close()
    }
}

fun printCampusDetails(campus: Campus) {
    println("Campus ID: ${campus.id}")
    println("Name: ${campus.name}")
    println("Country: ${campus.country}")
    println("Users Count: ${campus.users_count}")
    println("Active: ${campus.active}")
}
