package com.Event42Sync
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import kotlinx.coroutines.runBlocking

class InitializationHandler : RequestHandler<Map<String, String>, String> {
    override fun handleRequest(input: Map<String, String>, context: Context): String {
        return try {
            runBlocking {
                context.logger.log("Starting Event42Sync reinitialization")

                // Fetch the access tokens
                context.logger.log("Fetching 42 access token...")
                val access42Token = fetch42AccessToken()
                context.logger.log("42 token obtained successfully")

                context.logger.log("Fetching Google Calendar access token...")
                val accessGCtoken = fetchGCAccessToken()
                context.logger.log("Google Calendar token obtained successfully")

                // Run reinitialization using the new function
                context.logger.log("Starting calendar reinitialization...")
                reinitializeCalendar(accessGCtoken, access42Token, clearDatabase = true)
                context.logger.log("Calendar reinitialization completed")

                "Reinitialization completed successfully"
            }
        } catch (e: Exception) {
            val errorMsg = "Error during reinitialization: ${e.message}"
            context.logger.log(errorMsg)
            throw RuntimeException(errorMsg, e)
        }
    }
}

class DailySyncHandler : RequestHandler<Map<String, String>, String> {
    override fun handleRequest(input: Map<String, String>, context: Context): String {
        return try {
            runBlocking {
                context.logger.log("Starting Event42Sync daily sync")

                // Fetch the access tokens
                context.logger.log("Fetching 42 access token...")
                val access42Token = fetch42AccessToken()
                context.logger.log("42 token obtained successfully")

                context.logger.log("Fetching Google Calendar access token...")
                val accessGCtoken = fetchGCAccessToken()
                context.logger.log("Google Calendar token obtained successfully")

                // Run daily sync
                context.logger.log("Starting sync process...")
                syncEvents(access42Token, accessGCtoken)
                context.logger.log("Sync process completed")

                "Daily sync completed successfully"
            }
        } catch (e: Exception) {
            val errorMsg = "Error during sync: ${e.message}"
            context.logger.log(errorMsg)
            throw RuntimeException(errorMsg, e)
        }
    }
}