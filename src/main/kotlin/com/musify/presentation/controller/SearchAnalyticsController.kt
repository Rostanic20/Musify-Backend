package com.musify.presentation.controller

import com.musify.domain.services.SearchAnalyticsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * REST endpoints for search analytics dashboard
 */
fun Route.searchAnalyticsController() {
    val searchAnalyticsService by inject<SearchAnalyticsService>()
    
    authenticate("auth-jwt") {
        route("/api/search/analytics/dashboard") {
            
            // Get complete dashboard data
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()
                
                // TODO: Check if user has admin/analytics permissions
                
                try {
                    val dashboardData = searchAnalyticsService.getDashboardData()
                    call.respond(HttpStatusCode.OK, dashboardData)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to get dashboard data: ${e.message}")
                    )
                }
            }
            
            // Get performance timeline
            get("/performance") {
                val duration = call.parameters["duration"]?.toLongOrNull() ?: 60
                val interval = call.parameters["interval"]?.toLongOrNull() ?: 5
                
                try {
                    val timeline = searchAnalyticsService.getPerformanceTimeline(duration, interval)
                    call.respond(HttpStatusCode.OK, mapOf(
                        "timeline" to timeline,
                        "duration" to duration,
                        "interval" to interval
                    ))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to get performance timeline: ${e.message}")
                    )
                }
            }
            
            // Get search funnel analytics
            get("/funnel") {
                val timeRange = call.parameters["timeRange"] ?: "hour"
                
                try {
                    val funnel = searchAnalyticsService.getSearchFunnel(timeRange)
                    call.respond(HttpStatusCode.OK, funnel)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to get search funnel: ${e.message}")
                    )
                }
            }
            
            // Get query performance analytics
            get("/queries") {
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
                
                try {
                    val queryPerformance = searchAnalyticsService.getQueryPerformance(limit)
                    call.respond(HttpStatusCode.OK, mapOf(
                        "queries" to queryPerformance,
                        "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    ))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to get query performance: ${e.message}")
                    )
                }
            }
            
            // Export analytics data
            get("/export") {
                val format = call.parameters["format"] ?: "json"
                val timeRange = call.parameters["timeRange"] ?: "day"
                
                try {
                    when (format) {
                        "csv" -> {
                            val dashboardData = searchAnalyticsService.getDashboardData()
                            val csv = buildString {
                                appendLine("Metric,Value")
                                appendLine("Active Users,${dashboardData.activeUsers}")
                                appendLine("Total Searches Today,${dashboardData.totalSearchesToday}")
                                appendLine("Average Response Time,${dashboardData.averageResponseTime}")
                                appendLine("P95 Response Time,${dashboardData.p95ResponseTime}")
                                appendLine("Click Through Rate,${dashboardData.clickThroughRate}")
                                appendLine("Error Rate,${dashboardData.errorRate}")
                                appendLine("Cache Hit Rate,${dashboardData.cacheHitRate}")
                                appendLine()
                                appendLine("Popular Queries")
                                appendLine("Query,Count,Trend")
                                dashboardData.popularQueries.forEach { query ->
                                    appendLine("${query.query},${query.count},${query.trend}")
                                }
                            }
                            
                            call.respondText(
                                contentType = ContentType.Text.CSV,
                                text = csv
                            )
                        }
                        else -> {
                            val dashboardData = searchAnalyticsService.getDashboardData()
                            call.respond(HttpStatusCode.OK, mapOf(
                                "exportDate" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                "timeRange" to timeRange,
                                "data" to dashboardData
                            ))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to export analytics: ${e.message}")
                    )
                }
            }
        }
    }
}