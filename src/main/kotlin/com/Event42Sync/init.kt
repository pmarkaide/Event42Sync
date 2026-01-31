package com.Event42Sync

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.headers
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

suspend fun fetchAllCampusEvents(access_token:String): List<Event42> {
    val client = HttpClientConfig.createClient()
    val allEvent42s = mutableListOf<Event42>()
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
            val Event42List = json.decodeFromString<List<Event42>>(jsonString)

            // Add campuses to the list
            allEvent42s.addAll(Event42List)

            // Check each event's begin_at and compare with current time
            for (event in Event42List) {
                val eventBeginAt = Instant.parse(event.beginAt) // Parse begin_at as Instant
                if (eventBeginAt.isAfter(currentTime)) {
                    continue
                } else {
                    stopPagination = true
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
        val eventBeginAt = Instant.parse(event.beginAt) // Parse begin_at as Instant
        eventBeginAt.isAfter(currentTime) // Keep only events that are after the threshold
    }

    //Append ID to the description to use them as primary key when comparing events
    val modifiedEvents = filteredEvents.map { event ->
        event.copy(description = "${event.description}\n\nID: ${event.id}")
    }
    return modifiedEvents
}

// Helper data class for database events
data class DatabaseEvent(
    val id: String,
    val gcalEventId: String,
    val title: String,
    val beginAt: String,
    val lastUpdated: String
)

class DatabaseManager private constructor() {
    private var connection: Connection? = null
    private val dbFileName = "events.db"

    init {
        Class.forName("org.sqlite.JDBC")
    }

    companion object {
        @Volatile
        private var instance: DatabaseManager? = null

        fun getInstance(): DatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: DatabaseManager().also {
                    instance = it
                    it.initializeDatabase()
                }
            }
        }
    }

    private fun getConnection(): Connection {
        return connection ?: synchronized(this) {
            try {
                println("Attempting to connect to SQLite database...")
                connection = DriverManager.getConnection("jdbc:sqlite:$dbFileName")
                println("Connection successful!")
                connection!!
            } catch (e: Exception) {
                println("Database connection failed:")
                println("Error type: ${e.javaClass.name}")
                println("Error message: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
    }

    private fun initializeDatabase() {
        try {
            getConnection().createStatement().use { stmt ->
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS events (
                        id INTEGER PRIMARY KEY,
                        gcal_event_id TEXT,
                        title TEXT,
                        begin_at TIMESTAMP,
                        last_updated TIMESTAMP
                    )
                """)
                println("Events table created or verified successfully!")
            }
        } catch (e: Exception) {
            println("Failed to initialize database: ${e.message}")
            throw e
        }
    }

    fun clearDatabase() {
        getConnection().createStatement().use { stmt ->
            println("Clearing all events from database...")
            stmt.execute("DELETE FROM events")
            println("Database cleared successfully!")
        }
    }

    fun upsertEvent(id: Int, gcalEventId: String?, title: String, beginAt: String, lastUpdated: String) {
        getConnection().prepareStatement("""
            INSERT OR REPLACE INTO events (id, gcal_event_id, title, begin_at, last_updated)
            VALUES (?, ?, ?, ?, ?)
        """).use { stmt ->
            stmt.setInt(1, id)
            stmt.setString(2, gcalEventId)
            stmt.setString(3, title)
            stmt.setTimestamp(4, Timestamp.from(Instant.parse(beginAt)))
            stmt.setTimestamp(5, Timestamp.from(Instant.parse(lastUpdated)))
            stmt.executeUpdate()
        }
    }

    fun fetchEvents(): List<DatabaseEvent> {
        return getConnection().createStatement().use { stmt ->
            stmt.executeQuery("""
            SELECT id, gcal_event_id, title, begin_at, last_updated
            FROM events
        """).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(DatabaseEvent(
                            id = rs.getInt("id").toString(),
                            gcalEventId = rs.getString("gcal_event_id"),
                            title = rs.getString("title"),
                            beginAt = rs.getTimestamp("begin_at").toInstant().toString(),
                            lastUpdated = rs.getTimestamp("last_updated").toInstant().toString()
                        ))
                    }
                }
            }
        }
    }

    fun fixNullGcalIds() {
        try {
            connection?.createStatement()?.use { stmt ->
                println("Checking for null gcal_event_id values...")
                val updatedRows = stmt.executeUpdate(
                    "UPDATE events SET gcal_event_id = 'manual_fix_' || id WHERE gcal_event_id IS NULL"
                )
                println("Fixed $updatedRows events with null gcal_event_id")
            }
        } catch (e: Exception) {
            println("Error fixing null gcal_event_id values: ${e.message}")
            throw e
        }
    }

    fun closeConnection() {
        connection?.close()
        connection = null
    }
}

fun reinitializeCalendar(accessGCtoken: String, access42token: String, clearDatabase: Boolean = true) = runBlocking {
    try {
        println("Starting calendar reinitialization...")

        // Step 1: Delete all events from Google Calendar
        println("Deleting all calendar events from Google Calendar...")
        deleteAllEvents(accessGCtoken)
        println("Calendar cleared successfully!")

        // Step 2: Clear database if requested
        val dbManager = DatabaseManager.getInstance()
        if (clearDatabase) {
            dbManager.clearDatabase()
        }

        // Step 3: Fetch all events from 42 API
        println("Fetching events from 42 API...")
        val allEvents = fetchAllCampusEvents(access42token)
        println("Total events fetched: ${allEvents.size}")

        // Step 4: Upload events to new calendar and database
        println("Uploading events to Google Calendar and database...")
        var successCount = 0
        var failureCount = 0

        try {
            for (event in allEvents) {
                try {
                    val eventGCalID = createGCalEvent(accessGCtoken, event)
                    if (eventGCalID != null) {
                        dbManager.upsertEvent(
                            id = event.id,
                            gcalEventId = eventGCalID,
                            title = event.name,
                            beginAt = event.beginAt,
                            lastUpdated = event.updatedAt
                        )
                        successCount++
                    } else {
                        failureCount++
                        println("Failed to create GCal event for: ${event.name}")
                    }
                } catch (e: Exception) {
                    failureCount++
                    println("Error processing event ${event.name}: ${e.message}")
                }
            }
        } finally {
            dbManager.closeConnection()
        }

        println("\nReinitialization completed!")
        println("Summary:")
        println("- Total events processed: ${allEvents.size}")
        println("- Successful: $successCount")
        println("- Failed: $failureCount")

    } catch (e: Exception) {
        println("Error during reinitialization: ${e.message}")
        throw e
    }
}
