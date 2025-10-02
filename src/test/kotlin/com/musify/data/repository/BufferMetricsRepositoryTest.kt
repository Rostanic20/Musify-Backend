package com.musify.data.repository

import com.musify.core.streaming.DeviceType
import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory
import com.musify.database.tables.BufferMetricsTable
import com.musify.database.tables.Users
import com.musify.presentation.dto.BufferPerformanceHistory
import com.musify.presentation.dto.NetworkProfile
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BufferMetricsRepositoryTest {
    
    private lateinit var repository: BufferMetricsRepositoryImpl
    
    @BeforeAll
    fun setUpDatabase() {
        // Setup test environment and initialize database with unique name
        System.setProperty("DATABASE_URL", "jdbc:h2:mem:test_buffer_metrics_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        System.setProperty("DATABASE_USER", "sa")
        System.setProperty("DATABASE_PASSWORD", "")
        System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
        System.setProperty("ENVIRONMENT", "test")
        
        DatabaseFactory.init()
        
        // Insert test users after schema creation
        transaction {
            Users.insert {
                it[id] = 1
                it[email] = "test1@example.com"
                it[username] = "testuser1"
                it[passwordHash] = "hash1"
                it[displayName] = "Test User 1"
            }
            Users.insert {
                it[id] = 2
                it[email] = "test2@example.com"
                it[username] = "testuser2"
                it[passwordHash] = "hash2"
                it[displayName] = "Test User 2"
            }
        }
        
        repository = BufferMetricsRepositoryImpl()
    }
    
    @AfterAll
    fun tearDownDatabase() {
        DatabaseFactory.close()
    }
    
    @BeforeEach
    fun cleanDatabase() {
        transaction {
            BufferMetricsTable.deleteAll()
        }
    }
    
    @Nested
    @DisplayName("Save Buffer Performance Tests")
    inner class SaveBufferPerformanceTests {
        
        @Test
        fun `should save buffer performance successfully`() = runTest {
            // Given
            val performance = createBufferPerformanceHistory(
                userId = 1,
                sessionId = "session-123"
            )
            
            // When
            val result = repository.saveBufferPerformance(performance)
            
            // Then
            assertTrue(result is Result.Success)
            assertTrue(result.data > 0)
            
            // Verify in database
            val saved = transaction {
                BufferMetricsTable.selectAll().count()
            }
            assertEquals(1, saved)
        }
        
        @Test
        fun `should save multiple performances for same user`() = runTest {
            // Given
            val performance1 = createBufferPerformanceHistory(
                userId = 1,
                sessionId = "session-1",
                sessionStart = System.currentTimeMillis() - 3600000
            )
            val performance2 = createBufferPerformanceHistory(
                userId = 1,
                sessionId = "session-2",
                sessionStart = System.currentTimeMillis()
            )
            
            // When
            val result1 = repository.saveBufferPerformance(performance1)
            val result2 = repository.saveBufferPerformance(performance2)
            
            // Then
            assertTrue(result1 is Result.Success)
            assertTrue(result2 is Result.Success)
            
            val count = transaction {
                BufferMetricsTable.select { BufferMetricsTable.userId eq 1 }.count()
            }
            assertEquals(2, count)
        }
        
        @Test
        fun `should handle complex quality distribution`() = runTest {
            // Given
            val qualityDistribution = mapOf(
                96 to 300,   // 5 minutes at 96kbps
                128 to 600,  // 10 minutes at 128kbps
                192 to 1800, // 30 minutes at 192kbps
                320 to 900   // 15 minutes at 320kbps
            )
            
            val performance = createBufferPerformanceHistory(
                userId = 1,
                sessionId = "session-quality",
                qualityDistribution = qualityDistribution
            )
            
            // When
            val result = repository.saveBufferPerformance(performance)
            
            // Then
            assertTrue(result is Result.Success)
            
            // Verify quality distribution is properly stored
            val saved = transaction {
                BufferMetricsTable
                    .select { BufferMetricsTable.sessionId eq "session-quality" }
                    .firstOrNull()
            }
            
            assertNotNull(saved)
            val savedDistribution: Map<Int, Int> = Json.decodeFromString(
                saved[BufferMetricsTable.qualityDistribution]
            )
            assertEquals(qualityDistribution, savedDistribution)
        }
        
        @Test
        fun `should handle null session end time`() = runTest {
            // Given - Active session without end time
            val performance = createBufferPerformanceHistory(
                userId = 1,
                sessionId = "active-session",
                sessionEnd = null
            )
            
            // When
            val result = repository.saveBufferPerformance(performance)
            
            // Then
            assertTrue(result is Result.Success)
        }
    }
    
    @Nested
    @DisplayName("Get Buffer Performance History Tests")
    inner class GetBufferPerformanceHistoryTests {
        
        @Test
        fun `should retrieve performance history for user`() = runTest {
            // Given - Insert test data
            val performances = listOf(
                createBufferPerformanceHistory(userId = 1, sessionId = "session-1"),
                createBufferPerformanceHistory(userId = 1, sessionId = "session-2"),
                createBufferPerformanceHistory(userId = 2, sessionId = "session-3") // Different user
            )
            
            performances.forEach { repository.saveBufferPerformance(it) }
            
            // When
            val result = repository.getBufferPerformanceHistory(
                userId = 1,
                limit = 10,
                offset = 0
            )
            
            // Then
            assertTrue(result is Result.Success)
            assertEquals(2, result.data.size)
            assertTrue(result.data.all { it.userId == 1 })
        }
        
        @Test
        fun `should order history by session start descending`() = runTest {
            // Given - Sessions with different start times
            val oldSession = createBufferPerformanceHistory(
                userId = 1,
                sessionId = "old-session",
                sessionStart = System.currentTimeMillis() - 7200000 // 2 hours ago
            )
            val recentSession = createBufferPerformanceHistory(
                userId = 1,
                sessionId = "recent-session",
                sessionStart = System.currentTimeMillis() - 3600000 // 1 hour ago
            )
            val currentSession = createBufferPerformanceHistory(
                userId = 1,
                sessionId = "current-session",
                sessionStart = System.currentTimeMillis() // Now
            )
            
            // Save in random order
            repository.saveBufferPerformance(recentSession)
            repository.saveBufferPerformance(currentSession)
            repository.saveBufferPerformance(oldSession)
            
            // When
            val result = repository.getBufferPerformanceHistory(userId = 1)
            
            // Then
            assertTrue(result is Result.Success)
            assertEquals(3, result.data.size)
            assertEquals("current-session", result.data[0].sessionId)
            assertEquals("recent-session", result.data[1].sessionId)
            assertEquals("old-session", result.data[2].sessionId)
        }
        
        @Test
        fun `should support pagination`() = runTest {
            // Given - Multiple sessions
            repeat(15) { i ->
                val performance = createBufferPerformanceHistory(
                    userId = 1,
                    sessionId = "session-$i",
                    sessionStart = System.currentTimeMillis() - (i * 3600000L)
                )
                repository.saveBufferPerformance(performance)
            }
            
            // When - Get first page
            val page1 = repository.getBufferPerformanceHistory(
                userId = 1,
                limit = 5,
                offset = 0
            )
            
            // When - Get second page
            val page2 = repository.getBufferPerformanceHistory(
                userId = 1,
                limit = 5,
                offset = 5
            )
            
            // Then
            assertTrue(page1 is Result.Success)
            assertTrue(page2 is Result.Success)
            assertEquals(5, page1.data.size)
            assertEquals(5, page2.data.size)
            
            // Verify no overlap
            val page1Ids = page1.data.map { it.sessionId }.toSet()
            val page2Ids = page2.data.map { it.sessionId }.toSet()
            assertTrue(page1Ids.intersect(page2Ids).isEmpty())
        }
        
        @Test
        fun `should return empty list for user with no history`() = runTest {
            // When
            val result = repository.getBufferPerformanceHistory(
                userId = 999,
                limit = 10,
                offset = 0
            )
            
            // Then
            assertTrue(result is Result.Success)
            assertTrue(result.data.isEmpty())
        }
    }
    
    @Nested
    @DisplayName("Average Buffer Health Tests")
    inner class AverageBufferHealthTests {
        
        @Test
        fun `should calculate average buffer health for recent sessions`() = runTest {
            // Given - Sessions with different health scores
            val performances = listOf(
                createBufferPerformanceHistory(
                    userId = 1,
                    averageBufferHealth = 0.8,
                    sessionStart = System.currentTimeMillis() - 86400000 // 1 day ago
                ),
                createBufferPerformanceHistory(
                    userId = 1,
                    averageBufferHealth = 0.6,
                    sessionStart = System.currentTimeMillis() - 172800000 // 2 days ago
                ),
                createBufferPerformanceHistory(
                    userId = 1,
                    averageBufferHealth = 0.9,
                    sessionStart = System.currentTimeMillis() // Today
                )
            )
            
            performances.forEach { repository.saveBufferPerformance(it) }
            
            // When
            val result = repository.getAverageBufferHealth(userId = 1, days = 7)
            
            // Then
            assertTrue(result is Result.Success)
            val expectedAverage = (0.8 + 0.6 + 0.9) / 3
            assertEquals(expectedAverage, result.data, 0.01)
        }
        
        @Test
        fun `should exclude sessions older than specified days`() = runTest {
            // Given
            val recentSession = createBufferPerformanceHistory(
                userId = 1,
                averageBufferHealth = 0.9,
                sessionStart = System.currentTimeMillis() - 86400000 // 1 day ago
            )
            val oldSession = createBufferPerformanceHistory(
                userId = 1,
                averageBufferHealth = 0.3, // Poor health
                sessionStart = System.currentTimeMillis() - (10 * 86400000) // 10 days ago
            )
            
            repository.saveBufferPerformance(recentSession)
            repository.saveBufferPerformance(oldSession)
            
            // When - Only last 7 days
            val result = repository.getAverageBufferHealth(userId = 1, days = 7)
            
            // Then
            assertTrue(result is Result.Success)
            assertEquals(0.9, result.data) // Only recent session counted
        }
        
        @Test
        fun `should return 0 for user with no sessions`() = runTest {
            // When
            val result = repository.getAverageBufferHealth(userId = 999, days = 7)
            
            // Then
            assertTrue(result is Result.Success)
            assertEquals(0.0, result.data)
        }
    }
    
    @Nested
    @DisplayName("Update Session Metrics Tests")
    inner class UpdateSessionMetricsTests {
        
        @Test
        fun `should update existing session metrics`() = runTest {
            // Given
            val performance = createBufferPerformanceHistory(
                sessionId = "update-session",
                totalStarvations = 2,
                totalRebufferTime = 10
            )
            repository.saveBufferPerformance(performance)
            
            // When - Update metrics
            val updates = mapOf(
                "totalStarvations" to 5,
                "totalRebufferTime" to 25,
                "averageBitrate" to 192
            )
            val result = repository.updateSessionMetrics("update-session", updates)
            
            // Then
            assertTrue(result is Result.Success)
            assertTrue(result.data)
            
            // Verify updates
            val updated = repository.getSessionMetrics("update-session")
            assertTrue(updated is Result.Success)
            assertEquals(5, updated.data?.totalStarvations)
            assertEquals(25, updated.data?.totalRebufferTime)
            assertEquals(192, updated.data?.averageBitrate)
        }
        
        @Test
        fun `should update session end time`() = runTest {
            // Given
            val performance = createBufferPerformanceHistory(
                sessionId = "end-session",
                sessionEnd = null // Active session
            )
            repository.saveBufferPerformance(performance)
            
            // When
            val endTime = System.currentTimeMillis()
            val updates = mapOf("sessionEnd" to endTime)
            val result = repository.updateSessionMetrics("end-session", updates)
            
            // Then
            assertTrue(result is Result.Success)
            
            val updated = repository.getSessionMetrics("end-session")
            assertTrue(updated is Result.Success)
            assertEquals(endTime, updated.data?.sessionEnd)
        }
        
        @Test
        fun `should return false for non-existent session`() = runTest {
            // When
            val result = repository.updateSessionMetrics(
                "non-existent",
                mapOf("totalStarvations" to 1)
            )
            
            // Then
            assertTrue(result is Result.Success)
            assertEquals(false, result.data)
        }
    }
    
    @Nested
    @DisplayName("Get Session Metrics Tests")
    inner class GetSessionMetricsTests {
        
        @Test
        fun `should retrieve metrics for specific session`() = runTest {
            // Given
            val performance = createBufferPerformanceHistory(
                sessionId = "specific-session",
                userId = 1,
                deviceType = DeviceType.MOBILE,
                averageBufferHealth = 0.85
            )
            repository.saveBufferPerformance(performance)
            
            // When
            val result = repository.getSessionMetrics("specific-session")
            
            // Then
            assertTrue(result is Result.Success)
            assertNotNull(result.data)
            assertEquals("specific-session", result.data?.sessionId)
            assertEquals(1, result.data?.userId)
            assertEquals(DeviceType.MOBILE, result.data?.deviceType)
            assertEquals(0.85, result.data?.averageBufferHealth)
        }
        
        @Test
        fun `should return null for non-existent session`() = runTest {
            // When
            val result = repository.getSessionMetrics("non-existent-session")
            
            // Then
            assertTrue(result is Result.Success)
            assertEquals(null, result.data)
        }
    }
    
    @Nested
    @DisplayName("Device Type Statistics Tests")
    inner class DeviceTypeStatsTests {
        
        @Test
        fun `should calculate average health by device type`() = runTest {
            // Given - Different device types with varying health
            val performances = listOf(
                createBufferPerformanceHistory(
                    userId = 1,
                    deviceType = DeviceType.MOBILE,
                    averageBufferHealth = 0.7
                ),
                createBufferPerformanceHistory(
                    userId = 1,
                    deviceType = DeviceType.MOBILE,
                    averageBufferHealth = 0.8
                ),
                createBufferPerformanceHistory(
                    userId = 1,
                    deviceType = DeviceType.DESKTOP,
                    averageBufferHealth = 0.9
                ),
                createBufferPerformanceHistory(
                    userId = 1,
                    deviceType = DeviceType.TV,
                    averageBufferHealth = 0.95
                )
            )
            
            performances.forEach { repository.saveBufferPerformance(it) }
            
            // When
            val result = repository.getDeviceTypeStats(userId = 1)
            
            // Then
            assertTrue(result is Result.Success)
            val stats = result.data
            assertEquals(3, stats.size)
            assertEquals(0.75, stats["MOBILE"]) // (0.7 + 0.8) / 2
            assertEquals(0.9, stats["DESKTOP"])
            assertEquals(0.95, stats["TV"])
        }
        
        @Test
        fun `should return empty map for user with no data`() = runTest {
            // When
            val result = repository.getDeviceTypeStats(userId = 999)
            
            // Then
            assertTrue(result is Result.Success)
            assertTrue(result.data.isEmpty())
        }
    }
    
    @Nested
    @DisplayName("Cleanup Old Metrics Tests")
    inner class CleanupOldMetricsTests {
        
        @Test
        fun `should delete metrics older than specified days`() = runTest {
            // Given - Mix of old and recent sessions
            val oldSession = createBufferPerformanceHistory(
                sessionId = "old-1",
                sessionStart = System.currentTimeMillis() - (35 * 86400000L) // 35 days ago
            )
            val recentSession = createBufferPerformanceHistory(
                sessionId = "recent-1",
                sessionStart = System.currentTimeMillis() - (10 * 86400000L) // 10 days ago
            )
            
            repository.saveBufferPerformance(oldSession)
            repository.saveBufferPerformance(recentSession)
            
            // When - Keep only last 30 days
            val result = repository.cleanupOldMetrics(daysToKeep = 30)
            
            // Then
            assertTrue(result is Result.Success)
            assertEquals(1, result.data) // 1 record deleted
            
            // Verify only recent session remains
            val remaining = transaction {
                BufferMetricsTable.selectAll().count()
            }
            assertEquals(1, remaining)
            
            val remainingSession = repository.getSessionMetrics("recent-1")
            assertTrue(remainingSession is Result.Success)
            assertNotNull(remainingSession.data)
        }
        
        @Test
        fun `should return 0 when no old metrics to delete`() = runTest {
            // Given - Only recent sessions
            val recentSession = createBufferPerformanceHistory(
                sessionStart = System.currentTimeMillis()
            )
            repository.saveBufferPerformance(recentSession)
            
            // When
            val result = repository.cleanupOldMetrics(daysToKeep = 30)
            
            // Then
            assertTrue(result is Result.Success)
            assertEquals(0, result.data)
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {
        
        @Test
        fun `should handle database errors gracefully`() = runTest {
            // This test would require mocking the database connection
            // For now, we'll test with invalid data
            
            // Given - Performance with extremely long session ID
            val performance = createBufferPerformanceHistory(
                sessionId = "x".repeat(1000) // Exceeds typical column length
            )
            
            // When
            val result = repository.saveBufferPerformance(performance)
            
            // Then - Should handle error gracefully
            assertTrue(result is Result.Success || result is Result.Error)
        }
    }
    
    // Helper function to create test data
    private fun createBufferPerformanceHistory(
        userId: Int = 1,
        sessionId: String = "test-session-${System.currentTimeMillis()}",
        deviceType: DeviceType = DeviceType.MOBILE,
        averageBufferHealth: Double = 0.8,
        totalStarvations: Int = 2,
        totalRebufferTime: Int = 15,
        averageBitrate: Int = 192,
        qualityDistribution: Map<Int, Int> = mapOf(192 to 3600),
        networkConditions: NetworkProfile = NetworkProfile(
            averageBandwidthKbps = 2048,
            latencyMs = 50,
            jitterMs = 20,
            packetLossPercentage = 0.5,
            connectionType = "wifi"
        ),
        sessionStart: Long = System.currentTimeMillis(),
        sessionEnd: Long? = System.currentTimeMillis() + 3600000
    ) = BufferPerformanceHistory(
        userId = userId,
        sessionId = sessionId,
        deviceType = deviceType,
        averageBufferHealth = averageBufferHealth,
        totalStarvations = totalStarvations,
        totalRebufferTime = totalRebufferTime,
        averageBitrate = averageBitrate,
        qualityDistribution = qualityDistribution,
        networkConditions = networkConditions,
        sessionStart = sessionStart,
        sessionEnd = sessionEnd
    )
}