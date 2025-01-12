package com.Event42Sync

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout

object HttpClientConfig {
    fun createClient(): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000  // 30 seconds
            connectTimeoutMillis = 15000  // 15 seconds
            socketTimeoutMillis = 15000   // 15 secondsº
        }
        engine {
            requestTimeout = 30000  // 30 seconds
            endpoint {
                connectTimeout = 15000  // 15 seconds
                keepAliveTime = 5000   // 5 seconds
                maxConnectionsCount = 1000
            }
        }
    }
}