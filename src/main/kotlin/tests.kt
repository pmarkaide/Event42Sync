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

suspend fun fetchAllCampusData(access_token:String): List<Campus> {
    val client = HttpClient(CIO)
    val allCampuses = mutableListOf<Campus>()
    var currentPage = 1
    val pageSize = 30 // Number of results per page

    try {
        while (true) {
            // Make the GET request to the 42 API with pagination
            val response: HttpResponse = client.get("https://api.intra.42.fr/v2/campus") {
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
            val campusList = json.decodeFromString<List<Campus>>(jsonString)

            // Add campuses to the list
            allCampuses.addAll(campusList)

            // Check if this page had fewer items than `pageSize`, which means no more pages exist
            if (campusList.size < pageSize) {
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

    return allCampuses
}

fun printAllCampuses(campuses: List<Campus>) {
    println("Total Campuses: ${campuses.size}")
    campuses.forEach { campus ->
        println("Campus ID: ${campus.id}, " +
                "Name: ${campus.name}, " +
                "Country: ${campus.country}, " +
                "Users Count: ${campus.users_count}, " +
                "Active: ${campus.active}")
    }
}