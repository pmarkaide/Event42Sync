import com.google.auth.oauth2.ServiceAccountCredentials
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


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
        delay(2000)
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
        .createScoped(
            listOf(
                "https://www.googleapis.com/auth/calendar",
                "https://www.googleapis.com/auth/calendar.events"
            )
        )

    val accessToken = credentials.refreshAccessToken().tokenValue
    return accessToken
}

@Serializable
data class Event42(
    val id: Int,
    val name: String,
    val description: String,
    val location: String,
    val kind: String,
    @SerialName("begin_at") val beginAt: String,
    @SerialName("end_at") val endAt: String,
    @SerialName("campus_ids") val campusIds: List<Int>,
    @SerialName("cursus_ids") val cursusIds: List<Int>,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

val json = Json {
    ignoreUnknownKeys = true
}

@Serializable
data class DateTimeInfo(
    val dateTime: String,
    val timeZone: String
)

@Serializable
data class EventGCal(
    val id: String?,
    val created: String?,
    val updated: String?,
    val summary: String,
    val description: String,
    val location: String,
    val start: DateTimeInfo,
    val end: DateTimeInfo
)

@Serializable
data class GCalEventsResponse(
    val items: List<EventGCal> = emptyList() // List of events
)

suspend fun fetchUpdatedCampusEvents(access_token: String): List<Event42> {
    val client = HttpClient(CIO)
    val allEvent42s = mutableListOf<Event42>()
    var currentPage = 1
    val pageSize = 30 // Number of results per page
    val zone = ZoneId.systemDefault() // Get system default time zone
    // set current time as yesterday at midnight
    // value is relative to UTC. Modify by timezones as needed
    val currentTime = LocalDate.now(zone)
        .minusDays(1) // Move to the previous day (yesterday)
        .atStartOfDay(zone) // Set to midnight of the previous day
        .toInstant()
    var stopPagination = false

    try {
        while (true) {
            // Make the GET request to the 42 API with pagination
            val response: HttpResponse = client.get("https://api.intra.42.fr/v2/campus/13/events") {
                parameter("page[number]", currentPage) // Set page number
                parameter("page[size]", pageSize) // Set page size
                parameter("[sort]", "-updated_at")
                headers {
                    append(HttpHeaders.Authorization, "Bearer $access_token")
                }
            }

            // Read response and check for empty body
            val jsonString = response.bodyAsText()
            if (jsonString.isEmpty()) {
                throw Exception("Received empty response from the API")
            }

            // Parse JSON response as a list of events
            val Event42List = json.decodeFromString<List<Event42>>(jsonString)

            // Add events to the total list
            allEvent42s.addAll(Event42List)

            // Check each event's updated_at and compare with current time
            for (event in Event42List) {
                val eventBeginAt = Instant.parse(event.updatedAt)
                if (eventBeginAt.isAfter(currentTime)) {
                    continue
                } else {
                    stopPagination = true;
                    break
                }
            }

            // Check if this page had fewer items than `pageSize`, which means no more pages exist
            if (Event42List.size < pageSize || stopPagination) {
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
    val filteredEvents = allEvent42s.filter { event ->
        val eventBeginAt = Instant.parse(event.updatedAt) // Parse begin_at as Instant
        eventBeginAt.isAfter(currentTime) // Keep only events that are after the threshold
    }

    //Append ID to the description to use them as primary key when comparing events
    val modifiedEvents = filteredEvents.map { event ->
        event.copy(description = "${event.description}\n\nID: ${event.id}")
    }
    return modifiedEvents
}

fun Event42.toGCalEvent(): EventGCal {
    val timeZone = "Europe/Helsinki"

    val startDateTime = ZonedDateTime.parse(beginAt).withZoneSameInstant(ZoneId.of(timeZone))
    val endDateTime = ZonedDateTime.parse(endAt).withZoneSameInstant(ZoneId.of(timeZone))

    return EventGCal(
        id = this.id.toString(),
        created = this.createdAt,
        updated = this.updatedAt,
        location = this.location,
        summary = this.name,
        description = this.description,
        start = DateTimeInfo(
            dateTime = startDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            timeZone = timeZone
        ),
        end = DateTimeInfo(
            dateTime = endDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            timeZone = timeZone
        )
    )
}

fun EventGCal.toUploadEvent(): EventGCal {
    return this.copy(id = null, created = null, updated = null)
}

suspend fun createGCalEvent(
    accessToken: String,
    event: Event42
): String? {
    val dotenv = Dotenv.load()
    val calendarId = dotenv["calendar_id"]
    val client = HttpClient(CIO)
    try {
        // Transform Event42 to EventGCal and Nullify non-required fields
        val uploadEvent = event.toGCalEvent().toUploadEvent()
        val eventJson = json.encodeToString(uploadEvent)

        val response = client.post("https://www.googleapis.com/calendar/v3/calendars/$calendarId/events") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(eventJson) // Convert JsonObject to String
        }

        // Handle response
        if (response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            val createdEvent = json.decodeFromString<EventGCal>(responseBody)
            println("POST 42 ${event.name}")
            return createdEvent.id
        } else {
            println("Failed to POST event: ${response.status}")
            return null
        }
    } catch (e: Exception) {
        println("Error to POST event ${event.name}: ${e.message}")
        return null
    } finally {
        client.close()
    }
}

// Helper data class to store database event information
data class EventDBInfo(
    val gcalEventId: String,
    val title: String,
    val beginAt: String,
    val lastUpdated: String
)

fun syncEvents(access42token: String, accessGCtoken: String) = runBlocking {
    println("Starting sync process...")
    val dbManager = DatabaseManager.getInstance()

    try {
        // 1. Fetch updated events from 42 API
        println("Fetching events from 42 API...")
        val updatedEvents = fetchUpdatedCampusEvents(access42token)
        println("Found ${updatedEvents.size} events from 42")

        // 2. Get existing events from database
        val dbEvents = dbManager.fetchEvents().associate { dbEvent ->
            dbEvent.id.toInt() to EventDBInfo(
                gcalEventId = dbEvent.gcalEventId,
                title = dbEvent.title,
                beginAt = dbEvent.beginAt,
                lastUpdated = dbEvent.lastUpdated
            )
        }

        // 3. Process each event from 42
        for (event42 in updatedEvents) {
            val dbEvent = dbEvents[event42.id]

            if (dbEvent == null) {
                // New event - Create in Google Calendar
                println("Creating new event: ${event42.name}")
                val eventGCalID = createGCalEvent(accessGCtoken, event42)
                dbManager.upsertEvent(
                    id = event42.id,
                    gcalEventId = eventGCalID,
                    lastUpdated = event42.updatedAt,
                    title = event42.name,
                    beginAt = event42.beginAt
                )
            } else if (dbEvent.lastUpdated != event42.updatedAt) {
                // Event exists but was updated - Update in Google Calendar
                println("Updating event: ${event42.name}")
                updateGCalEvent(
                    accessGCtoken,
                    dbEvent.gcalEventId,
                    event42
                )
                dbManager.upsertEvent(
                    id = event42.id,
                    gcalEventId = dbEvent.gcalEventId,
                    lastUpdated = event42.updatedAt,
                    title = event42.name,
                    beginAt = event42.beginAt
                )
            } else {
                println("Event ${event42.name} is up to date")
            }
        }

        println("Sync completed successfully!")

    } catch (e: Exception) {
        println("Error during sync: ${e.message}")
    } finally {
        dbManager.closeConnection()
    }
}

suspend fun updateGCalEvent(accessGCtoken: String, gcalEventId: String, event42: Event42) {
    val dotenv = Dotenv.load()
    val calendarId = dotenv["calendar_id"]
    val client = HttpClient(CIO)
    try {
        // Transform Event42 to EventGCal and Nullify non-required fields
        val uploadEvent = event42.toGCalEvent().toUploadEvent()
        val eventJson = json.encodeToString(uploadEvent)

        val response = client.put("https://www.googleapis.com/calendar/v3/calendars/$calendarId/events/$gcalEventId") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessGCtoken")
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(eventJson) // Convert JsonObject to String
        }

        // Handle response
        if (response.status.isSuccess()) {
            println("PUT 42 ${event42.name}")
        } else {
            println("Failed to PUT 42 event: ${response.status}")
        }
    } catch (e: Exception) {
        println("Error to PUT 42 event ${event42.name}: ${e.message}")
    } finally {
        client.close()
    }
}


fun main() = runBlocking {
    try {
        // Fetch the access tokens
        val access42Token = fetch42AccessToken()
        println("42 Access Token: $access42Token")
        val accessGCtoken = fetchGCAccessToken()
        println("GC Access Token: $accessGCtoken")

        // init_calendar
        //initCalendar(accessGCtoken, access42Token)

        // daily sync
        syncEvents(access42Token, accessGCtoken)

    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
    }
}
