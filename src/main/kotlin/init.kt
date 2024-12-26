import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*

// Data class for the event
@Serializable
data class EventID(val id: String)

// Data class for the API response
@Serializable
data class EventsResponse(val items: List<EventID>)


suspend fun deleteAllEvents(accessToken: String) {
    val client = HttpClient(CIO)

    val dotenv = Dotenv.load()
    val calendarID = dotenv["calendar_id"]
    try {
        // Step 1: Fetch events
        val response: HttpResponse = client.get("https://www.googleapis.com/calendar/v3/calendars/$calendarID/events") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        val jsonString = response.bodyAsText()
        if (jsonString.isEmpty()) {
            throw Exception("Received empty response from the API")
        }
        val eventList: EventsResponse = json.decodeFromString(jsonString)

        // Step 2: Delete each event
        eventList.items.forEach { event ->
            client.delete("https://www.googleapis.com/calendar/v3/calendars/$calendarID/events/${event.id}") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        println("Successfully deleted all events!")
        
    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
    } finally {
        client.close()
    }
}

fun main() = runBlocking {
    try {
        // GET GC token
        val accessGCToken = fetchGCAccessToken()
        println("GC Access Token: $accessGCToken")

        // DELETE all events in Gcal calendar
        println("Deleting all Gcal events...")
        deleteAllEvents(accessGCToken)
    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
    }
}
