package com.musify.presentation.websocket

import com.musify.domain.services.SearchAnalyticsService
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

/**
 * Simplified WebSocket endpoint for real-time search analytics dashboard
 */
fun Route.searchAnalyticsWebSocket() {
    val searchAnalyticsService by inject<SearchAnalyticsService>()
    val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    webSocket("/ws/search/analytics") {
        try {
            coroutineScope {
                // Send periodic dashboard updates (every 5 seconds)
                val dashboardJob = launch {
                    while (isActive) {
                        try {
                            val dashboardData = searchAnalyticsService.getDashboardData()
                            val message = buildString {
                                append("{")
                                append("\"type\":\"dashboard_update\",")
                                append("\"activeUsers\":${dashboardData.activeUsers},")
                                append("\"totalSearchesToday\":${dashboardData.totalSearchesToday},")
                                append("\"averageResponseTime\":${dashboardData.averageResponseTime},")
                                append("\"p95ResponseTime\":${dashboardData.p95ResponseTime},")
                                append("\"clickThroughRate\":${dashboardData.clickThroughRate},")
                                append("\"errorRate\":${dashboardData.errorRate},")
                                append("\"cacheHitRate\":${dashboardData.cacheHitRate},")
                                append("\"performanceStatus\":\"${dashboardData.performanceStatus}\"")
                                append("}")
                            }
                            send(Frame.Text(message))
                        } catch (e: Exception) {
                            println("Error sending dashboard update: ${e.message}")
                        }
                        delay(5000)
                    }
                }
                
                // Stream real-time search events
                val searchEventJob = launch {
                    searchAnalyticsService.searchEvents
                        .collect { event ->
                            try {
                                val eventJson = when (event) {
                                    is com.musify.domain.services.SearchEvent.NewSearch -> {
                                        """
                                        {
                                            "type": "search_event",
                                            "eventType": "new_search",
                                            "searchId": "${event.searchId}",
                                            "query": "${event.query}",
                                            "userId": ${event.userId ?: "null"},
                                            "resultCount": ${event.resultCount},
                                            "timestamp": "${event.timestamp}"
                                        }
                                        """
                                    }
                                    is com.musify.domain.services.SearchEvent.Click -> {
                                        """
                                        {
                                            "type": "search_event",
                                            "eventType": "click",
                                            "searchId": "${event.searchId}",
                                            "itemType": "${event.itemType}",
                                            "itemId": ${event.itemId},
                                            "position": ${event.position},
                                            "timestamp": "${event.timestamp}"
                                        }
                                        """
                                    }
                                }
                                send(Frame.Text(eventJson.trim()))
                            } catch (e: Exception) {
                                println("Error sending search event: ${e.message}")
                            }
                        }
                }
                
                // Stream metrics updates
                val metricsJob = launch {
                    searchAnalyticsService.metricsUpdate
                        .collect { metrics ->
                            try {
                                val metricsJson = """
                                {
                                    "type": "metrics_update",
                                    "timestamp": "${metrics.timestamp}",
                                    "activeSearches": ${metrics.activeSearches},
                                    "totalQueries": ${metrics.totalQueries},
                                    "avgResponseTime": ${metrics.avgResponseTime},
                                    "currentCTR": ${metrics.currentCTR},
                                    "qps": ${metrics.qps}
                                }
                                """
                                send(Frame.Text(metricsJson.trim()))
                            } catch (e: Exception) {
                                println("Error sending metrics update: ${e.message}")
                            }
                        }
                }
                
                // Handle incoming messages from client
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            handleClientMessage(text, searchAnalyticsService)
                        }
                        is Frame.Close -> {
                            break
                        }
                        else -> {}
                    }
                }
                
                // Cancel all jobs when connection closes
                dashboardJob.cancel()
                searchEventJob.cancel()
                metricsJob.cancel()
            }
        } catch (e: Exception) {
            println("WebSocket error: ${e.message}")
        }
    }
}

private suspend fun DefaultWebSocketServerSession.handleClientMessage(
    message: String,
    analyticsService: SearchAnalyticsService
) {
    try {
        // Simple command parsing
        when {
            message.startsWith("get_funnel") -> {
                val funnel = analyticsService.getSearchFunnel("hour")
                val funnelJson = """
                {
                    "type": "search_funnel",
                    "totalSearches": ${funnel.totalSearches},
                    "searchesWithResults": ${funnel.searchesWithResults},
                    "searchesWithClicks": ${funnel.searchesWithClicks},
                    "searchesWithMultipleClicks": ${funnel.searchesWithMultipleClicks},
                    "conversionRate": ${funnel.conversionRate},
                    "avgClicksPerSearch": ${funnel.avgClicksPerSearch}
                }
                """
                send(Frame.Text(funnelJson.trim()))
            }
            
            message.startsWith("get_query_performance") -> {
                val performance = analyticsService.getQueryPerformance(10)
                val queriesJson = performance.map { query ->
                    """
                    {
                        "query": "${query.query}",
                        "searchCount": ${query.searchCount},
                        "avgResponseTime": ${query.avgResponseTime},
                        "avgResultCount": ${query.avgResultCount},
                        "clickThroughRate": ${query.clickThroughRate}
                    }
                    """
                }.joinToString(",", "[", "]")
                
                send(Frame.Text("""{"type": "query_performance", "queries": $queriesJson}"""))
            }
            
            else -> {
                send(Frame.Text("""{"type": "error", "message": "Unknown command"}"""))
            }
        }
    } catch (e: Exception) {
        send(Frame.Text("""{"type": "error", "message": "Error: ${e.message}"}"""))
    }
}