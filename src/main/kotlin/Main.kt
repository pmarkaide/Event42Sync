import com.google.auth.oauth2.ServiceAccountCredentials
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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

fun main() = runBlocking {
    try {
        // Fetch the access token
        val access42Token = fetch42AccessToken()
        println("42 Access Token: $access42Token")

        val accessGCToken = fetchGCAccessToken()
        println("GC Access Token: $accessGCToken")

        val campus = fetchCampusData(access42Token)
        printCampusDetails(campus)
    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
    }
}
