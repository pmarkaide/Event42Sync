package com.Event42Sync
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter


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

/////////////// GET all GCal events ///////////////

suspend fun getGcalEvents(accessToken: String): List<EventGCal> {
    val client = HttpClient(CIO)

    val calendarID = Config.get("CALENDAR_ID")

    val zone = ZoneId.systemDefault() // Get system default time zone
    val currentTime = LocalDate.now(zone)
        .minusDays(1) // Move to the previous day (yesterday)
        .atStartOfDay(zone) // Set to midnight of the previous day
        .toInstant()
    // Format the current time in ISO 8601 format (UTC timezone) for the API
    val formatter = DateTimeFormatter.ISO_INSTANT
    val formattedTime = formatter.format(currentTime)
    println(formattedTime)
    try {
        val response: HttpResponse = client.get(
            "https://www.googleapis.com/calendar/v3/calendars/$calendarID/" +
                    "events?singleEvents=true&timeMin=2024-12-26T22:00:00Z"
        ) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        // Check if the response was successful
        if (!response.status.isSuccess()) {
            throw Exception("Failed to fetch events: ${response.status}")
        }
        println(response.bodyAsText())
        val jsonString = response.bodyAsText()
        if (jsonString.isEmpty()) {
            println("No events found for the given time range.")
            return emptyList() // Return empty list if no events found
        }

        // Deserialize the response JSON to a list of GCalEvent objects
        val responseWrapper = json.decodeFromString<GCalEventsResponse>(jsonString)
        return responseWrapper.items
    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
        return emptyList() // Return empty list in case of error
    } finally {
        client.close()
    }
}

/////////////// DELETE all GCal events ///////////////

// Data class for the event
@Serializable
data class EventID(val id: String)

// Data class for the API response
@Serializable
data class EventsResponse(val items: List<EventID>)


suspend fun deleteAllEvents(accessToken: String) {
    val client = HttpClient(CIO)

    val calendarID = Config.get("CALENDAR_ID")
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
        println("Starting the deleting of events...\n")
        eventList.items.forEach { event ->
            try {
                val response = client.delete("https://www.googleapis.com/calendar/v3/calendars/$calendarID/events/${event.id}") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }

                // Check if response is successful (status code 200)
                if (response.status.value == 204) {
                    println("[200] DELETE GCal ${event.id}")
                } else {
                    // Log the error response code and message
                    println("[${response.status.value}] ERROR: ${response.bodyAsText()}")
                }
            } catch (e: Exception) {
                // Handle and log the exception
                println("[ERROR] Failed to delete event ${event.id}: ${e.message}")
                e.printStackTrace() // Print full stack trace for debugging
            }
        }

    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
    } finally {
        client.close()
    }
}



