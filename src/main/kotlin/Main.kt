import com.google.auth.oauth2.ServiceAccountCredentials
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.io.FileInputStream

@Serializable
data class AccessTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val scope: String,
    val created_at: Long,
    val secret_valid_until: Long
)

fun extractAccessToken(response: String): String {
    val tokenResponse = Json.decodeFromString<AccessTokenResponse>(response)
    return tokenResponse.access_token
}

@OptIn(InternalAPI::class)
suspend fun fetch42AccessToken(): String {
    // Load your environment variables securely
    val dotenv = Dotenv.load()
    val clientId = dotenv["UID"]
    val clientSecret = dotenv["SECRET"]

    val client = HttpClient()

        try {
            val response: HttpResponse = client.post("https://api.intra.42.fr/oauth/token") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
                body = FormDataContent(Parameters.build {
                    append("grant_type", "client_credentials")  // Or "authorization_code" depending on your flow
                    append("client_id", clientId ?: "")
                    append("client_secret", clientSecret ?: "")
                })
            }
            val responseBody = response.bodyAsText()
            println("42 Token Response: $responseBody")

            // Extract access token from response
            val accessToken = extractAccessToken(responseBody)
            return accessToken
        } catch (e: Exception) {
            println(" 42Error fetching token: ${e.message}")
            throw e
        } finally {
            client.close()
        }
}

fun fetchGCAccessToken(): String {
    val pathToKeyFile = "event42sync.json"

    val credentials = ServiceAccountCredentials
        .fromStream(FileInputStream(pathToKeyFile))
        .createScoped(listOf(
            "https://www.googleapis.com/auth/calendar",
            "https://www.googleapis.com/auth/calendar.events"))

    val accessToken = credentials.refreshAccessToken().tokenValue
    return accessToken
}

@Serializable
data class Event(
    val id: Int,
    val name: String,
    val description: String,
    val location: String,
    val kind: String,
    @SerialName("max_people") val maxPeople: Int? = null,
    @SerialName("nbr_subscribers") val nbrSubscribers: Int,
    @SerialName("begin_at") val beginAt: String,
    @SerialName("end_at") val endAt: String,
    @SerialName("campus_ids") val campusIds: List<Int>,
    @SerialName("cursus_ids") val cursusIds: List<Int>,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("prohibition_of_cancellation") val prohibitionOfCancellation: Int? = null,
    val waitlist: Waitlist? = null,
    val themes: List<String> = emptyList() // Default to an empty list if not provided
)

@Serializable
data class Waitlist(
    val id: Int,
    @SerialName("waitlistable_id") val waitlistableId: Int,
    @SerialName("waitlistable_type") val waitlistableType: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

suspend fun fetchAllCampusEvents(access_token:String): List<Event> {
    val client = HttpClient(CIO)
    val allEvents = mutableListOf<Event>()
    var currentPage = 1
    val pageSize = 30 // Number of results per page

    try {
        while (true) {
            // Make the GET request to the 42 API with pagination
            val response: HttpResponse = client.get("https://api.intra.42.fr/v2/campus/13/events") {
                parameter("page[number]", currentPage) // Set page number
                parameter("page[size]", pageSize) // Set page size
                headers {
                    append(HttpHeaders.Authorization, "Bearer $access_token")
                }
            }

            // Read response and check for empty body
            val jsonString = response.bodyAsText()
            if (jsonString.isEmpty()) {
                throw Exception("Received empty response from the API")
            }

            // Parse JSON response as a list of campuses
            val eventList = json.decodeFromString<List<Event>>(jsonString)

            // Add campuses to the list
            allEvents.addAll(eventList)

            // Check if this page had fewer items than `pageSize`, which means no more pages exist
            if (eventList.size < pageSize) {
                println("Last page reached. Stopping pagination.")
                break
            }

            // Move to the next page
            currentPage++
        }
    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
        throw e
    } finally {
        client.close()
    }

    return allEvents
}

fun main() = runBlocking {
    try {
        // Fetch the access token
        val access42Token = fetch42AccessToken()
        println("42 Access Token: $access42Token")

        val accessGCToken = fetchGCAccessToken()
        println("GC Access Token: $accessGCToken")

        val allEvents = fetchAllCampusEvents(access42Token)
        allEvents.forEach { event ->
            println(event.name)
        }

    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
    }
}
