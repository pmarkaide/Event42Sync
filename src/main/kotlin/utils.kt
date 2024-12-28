import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/////////////// GET campus summary ///////////////

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

/////////////// DELETE all events ///////////////

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
        val response: HttpResponse = client.get("https://www.googleapis.com/calendar/v3/calendars/$calendarID/" +
                "events?singleEvents=true") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        val jsonString = response.bodyAsText()
        if (jsonString.isEmpty()) {
            throw Exception("Received empty response from the API")
        }
        val eventList: EventsResponse = json.decodeFromString(jsonString)

        // Step 2: Delete each event
        eventList.items.forEach { event ->
            println("Deleting ${event.id}...")
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


suspend fun fetchAllCampusEvents(access_token:String): List<Event> {
    val client = HttpClient(CIO)
    val allEvents = mutableListOf<Event>()
    var currentPage = 1
    val pageSize = 30 // Number of results per page
    val zone = ZoneId.systemDefault() // Get system default time zone
    // set current time as yesterday at midnight
    // NB: value is relative to UTC. Modify by timezones as needed
    val currentTime = LocalDate.now(zone)
        .minusDays(30) // Move to the previous day (yesterday)
        .atStartOfDay(zone) // Set to midnight of the previous day
        .toInstant()
    var stopPagination = false

    try {
        while (true) {
            // Make the GET request to the 42 API with pagination
            val response: HttpResponse = client.get("https://api.intra.42.fr/v2/campus/13/events") {
                parameter("page[number]", currentPage) // Set page number
                parameter("page[size]", pageSize) // Set page size
                parameter("[sort]", "-begin_at")
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

            // Check each event's begin_at and compare with current time
            for (event in eventList) {
                val eventBeginAt = Instant.parse(event.beginAt) // Parse begin_at as Instant
                if (eventBeginAt.isAfter(currentTime)) {
                    continue
                } else {
                    stopPagination = true;
                    break
                }
            }

            // Check if this page had fewer items than `pageSize`, which means no more pages exist
            if (eventList.size < pageSize || stopPagination) {
                println("Last page reached or events are older than today. Stopping pagination.")
                break
            }
            delay(2000)
            // Move to the next page
            currentPage++
        }
    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
        throw e
    } finally {
        client.close()
    }

    // Filter out events older than the threshold time
    val filteredEvents = allEvents.filter { event ->
        val eventBeginAt = Instant.parse(event.beginAt) // Parse begin_at as Instant
        eventBeginAt.isAfter(currentTime) // Keep only events that are after the threshold
    }

    //Append ID to the description to use them as primary key when comparing events
    val modifiedEvents = filteredEvents.map { event ->
        event.copy(description = "${event.description}\n\nID: ${event.id}")
    }
    return modifiedEvents
}

fun initCalendar(accessGCtoken: String, access42token: String) = runBlocking {
    try {
        println("Deleting all calendar events...")
        deleteAllEvents(accessGCtoken)

//        println("Fetching 42 events...")
//        val allEvents = fetchAllCampusEvents(access42token)
//        println("Total events fetched: ${allEvents.size}")
//
//        println("Uploading events to Gcal...")
//        val uploadEvents = allEvents.map {
//            it.toGCalEvent()
//        }
//        val jsonEvent = Json.encodeToString(uploadEvents.first())
//        println(jsonEvent)
        //insertCalendarEvents(accessGCtoken, uploadEvents)
    }
    catch (e: Exception) {
        println("Error occurred: ${e.message}")
    }
}
