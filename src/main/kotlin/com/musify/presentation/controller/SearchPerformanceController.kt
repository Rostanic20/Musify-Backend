package com.musify.presentation.controller

import com.musify.domain.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlinx.serialization.Serializable

/**
 * Controller for monitoring and managing search performance
 */
fun Route.searchPerformanceController() {
    val performanceOptimizer by inject<SearchPerformanceOptimizer>()
    val cacheService by inject<SearchCacheService>()
    
    authenticate("auth-jwt") {
        route("/api/search/performance") {
            
            // Get performance metrics
            get("/metrics") {
                val report = performanceOptimizer.getPerformanceReport()
                val cacheStats = cacheService.getStats()
                
                val metrics = PerformanceMetricsResponse(
                    avgResponseTimeMs = report.avgResponseTimeMs,
                    medianResponseTimeMs = report.medianResponseTimeMs,
                    p95ResponseTimeMs = report.p95ResponseTimeMs,
                    p99ResponseTimeMs = report.p99ResponseTimeMs,
                    sub50msPercentage = report.sub50msPercentage,
                    cacheHitRate = cacheStats.hitRate(),
                    cacheSize = cacheStats.hits + cacheStats.misses,
                    errorRate = report.errorRate
                )
                
                call.respond(HttpStatusCode.OK, metrics)
            }
            
            // Get detailed performance report
            get("/report") {
                val report = performanceOptimizer.getPerformanceReport()
                val cacheStats = cacheService.getStats()
                
                val detailedReport = DetailedPerformanceReport(
                    performance = report,
                    cache = CacheReport(
                        hits = cacheStats.hits,
                        misses = cacheStats.misses,
                        hitRate = cacheStats.hitRate(),
                        evictions = cacheStats.evictions,
                        invalidations = cacheStats.invalidations
                    ),
                    recommendations = generateRecommendations(report, cacheStats)
                )
                
                call.respond(HttpStatusCode.OK, detailedReport)
            }
            
            // Clear cache
            post("/cache/clear") {
                // TODO: Add admin permission check
                
                cacheService.clear()
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Cache cleared successfully"
                ))
            }
            
            // Warm up cache
            post("/cache/warmup") {
                // TODO: Add admin permission check
                
                performanceOptimizer.warmup()
                call.respond(HttpStatusCode.Accepted, mapOf(
                    "message" to "Cache warmup initiated"
                ))
            }
            
            // Performance dashboard HTML  
            get("/dashboard") {
                call.respondText("<h1>Performance Dashboard</h1><p>Under development</p>", ContentType.Text.Html)
            }
        }
    }
}

private fun generateRecommendations(
    report: PerformanceReport,
    cacheStats: SearchCacheService.CacheStats
): List<String> {
    val recommendations = mutableListOf<String>()
    
    // Response time recommendations
    if (report.avgResponseTimeMs > 50) {
        recommendations.add("Average response time is ${report.avgResponseTimeMs.toInt()}ms. Consider:")
        
        if (report.cacheHitRate < 0.5) {
            recommendations.add("- Increase cache TTL or size (current hit rate: ${(report.cacheHitRate * 100).toInt()}%)")
        }
        
        if (report.p99ResponseTimeMs > 200) {
            recommendations.add("- Optimize slow queries (P99: ${report.p99ResponseTimeMs.toInt()}ms)")
        }
        
        recommendations.add("- Enable query result pre-fetching for common searches")
    }
    
    // Cache recommendations
    if (cacheStats.hitRate() < 0.3) {
        recommendations.add("Cache hit rate is low (${(cacheStats.hitRate() * 100).toInt()}%). Consider increasing cache size or TTL")
    }
    
    if (cacheStats.evictions > cacheStats.hits) {
        recommendations.add("High cache eviction rate. Consider increasing cache size")
    }
    
    // Success recommendations
    if (report.sub50msPercentage > 0.9) {
        recommendations.add("✓ Excellent performance! ${(report.sub50msPercentage * 100).toInt()}% of queries complete in <50ms")
    }
    
    return recommendations
}

// Response DTOs

@Serializable
data class PerformanceMetricsResponse(
    val avgResponseTimeMs: Double,
    val medianResponseTimeMs: Double,
    val p95ResponseTimeMs: Double,
    val p99ResponseTimeMs: Double,
    val sub50msPercentage: Double,
    val cacheHitRate: Double,
    val cacheSize: Long,
    val errorRate: Double
)

@Serializable
data class DetailedPerformanceReport(
    val performance: PerformanceReport,
    val cache: CacheReport,
    val recommendations: List<String>
)

@Serializable
data class CacheReport(
    val hits: Long,
    val misses: Long,
    val hitRate: Double,
    val evictions: Long,
    val invalidations: Long
)

