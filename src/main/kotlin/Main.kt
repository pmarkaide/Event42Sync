import com.google.auth.oauth2.ServiceAccountCredentials
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
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
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.ZoneId


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

suspend fun fetchUpdatedCampusEvents(access_token:String): List<Event> {
    val client = HttpClient(CIO)
    val allEvents = mutableListOf<Event>()
    var currentPage = 1
    val pageSize = 1 // Number of results per page
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
            val eventList = json.decodeFromString<List<Event>>(jsonString)

            // Add events to the total list
            allEvents.addAll(eventList)

            // Check each event's updated_at and compare with current time
            for (event in eventList) {
                val eventBeginAt = Instant.parse(event.updatedAt)
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

    //Append ID to the description to use them as primary key when comparing events
    val modifiedEvents = allEvents.map { event ->
        event.copy(description = "${event.description}\n\nID: ${event.id}")
    }
    return modifiedEvents
}

fun Event.toGoogleCalendarEvent(): JsonObject {
    val timeZone = "Europe/Helsinki"

    val startDateTime = ZonedDateTime.parse(beginAt).withZoneSameInstant(java.time.ZoneId.of(timeZone))
    val endDateTime = ZonedDateTime.parse(endAt).withZoneSameInstant(java.time.ZoneId.of(timeZone))

    return buildJsonObject {
        put("summary", name)
        put("location", location)
        put("description", description)
        put("start", buildJsonObject {
            put("dateTime", startDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("timeZone", timeZone)
        })
        put("end", buildJsonObject {
            put("dateTime", endDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("timeZone", timeZone)
        })
    }
}

suspend fun insertCalendarEvents(
    accessToken: String,
    events: List<JsonObject>
) {
    val dotenv = Dotenv.load()
    val calendarId = dotenv["calendar_id"]
    val client = HttpClient(CIO)

    events.forEach { event ->
        try {
            val response = client.post("https://www.googleapis.com/calendar/v3/calendars/$calendarId/events") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(event.toString()) // Convert JsonObject to String
            }

            // Handle response
            if (response.status.isSuccess()) {
                println("Successfully created event: ${event["summary"]}")
            } else {
                println("Failed to create event: ${response.status}")
            }
        } catch (e: Exception) {
            println("Error creating event ${event["summary"]}: ${e.message}")
        }
    }
    client.close()
}

fun main() = runBlocking {
    try {
        // Fetch the access tokens
        val access42Token = fetch42AccessToken()
        println("42 Access Token: $access42Token")
        val accessGCToken = fetchGCAccessToken()
        println("GC Access Token: $accessGCToken")

        // init_calendar
        //initCalendar(accessGCToken, access42Token)
        println("Fetching 42 events...")
        val allEvents = fetchUpdatedCampusEvents(access42Token)
        println("Total events fetched: ${allEvents.size}")

        println("Uploading events to Gcal...")
        // Convert the list of Event objects to Google Calendar event format
        val googleCalendarEvents = allEvents.map { it.toGoogleCalendarEvent() }
        insertCalendarEvents(accessGCToken, googleCalendarEvents)

    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
    }
}
