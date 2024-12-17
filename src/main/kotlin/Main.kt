import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.util.*

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


@Serializable
data class TokenResponse(
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String
)

@OptIn(InternalAPI::class)
suspend fun fetchGCAccessToken(): String {
    // Load your environment variables securely
    val dotenv = Dotenv.load()
    val clientId = dotenv["client_id"]
    val clientSecret = dotenv["client_secret"]
    val refreshToken = dotenv["refresh_token"]

    val client = HttpClient()

    try {
        val response: HttpResponse = client.post("https://oauth2.googleapis.com/token") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
            body = FormDataContent(Parameters.build {
                append("client_id", clientId ?: "")
                append("client_secret", clientSecret ?: "")
                append("refresh_token", refreshToken ?: "")
                append("grant_type", "refresh_token")
            })
        }
        val responseBody = response.bodyAsText()
        println("GC Token Response: $responseBody")

        // Extract access token from response
        val accessToken = Json.decodeFromString<TokenResponse>(responseBody)
        return accessToken.accessToken
    } catch (e: Exception) {
        println("GC Error fetching token: ${e.message}")
        throw e
    } finally {
        client.close()
    }
}


fun main() = runBlocking {
    try {
        // Fetch the access token
        val accessToken = fetch42AccessToken()
        println("42 Access Token: $accessToken")

        val accessGCToken = fetchGCAccessToken()
        println("GC Access Token: $accessGCToken")

    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
    }
}
