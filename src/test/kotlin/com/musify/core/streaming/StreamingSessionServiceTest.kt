package com.musify.core.streaming

import com.musify.core.exceptions.PaymentException
import com.musify.core.monitoring.AnalyticsService
import com.musify.core.utils.Result
import com.musify.domain.entities.SubscriptionStatus
import com.musify.domain.entity.*
import com.musify.domain.repository.StreamingSessionRepository
import com.musify.domain.repository.SubscriptionRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingSessionServiceTest {
    private lateinit var service: StreamingSessionService
    private lateinit var sessionRepository: StreamingSessionRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var analyticsService: AnalyticsService
    
    @BeforeEach
    fun setUp() {
        sessionRepository = mockk()
        subscriptionRepository = mockk()
        analyticsService = mockk()
        service = StreamingSessionService(sessionRepository, subscriptionRepository, analyticsService)
    }
    
    @Test
    fun `should start session successfully for free user`() = runTest {
        // Given
        val request = StartSessionRequest(
            userId = 1,
            songId = 100,
            deviceId = "device123",
            deviceName = "Test Device",
            ipAddress = "192.168.1.1",
            userAgent = "TestAgent/1.0",
            quality = 192,
            streamType = StreamType.DIRECT
        )
        
        coEvery { sessionRepository.countActiveSessionsByUserId(1) } returns Result.Success(0)
        coEvery { subscriptionRepository.findSubscriptionByUserId(1) } returns Result.Success(null)
        coEvery { sessionRepository.createSession(any()) } returns Result.Success(
            StreamingSession(
                id = 1,
                sessionId = "test-session-id",
                userId = 1,
                songId = 100,
                deviceId = "device123",
                deviceName = "Test Device",
                ipAddress = "192.168.1.1",
                userAgent = "TestAgent/1.0",
                quality = 192,
                streamType = StreamType.DIRECT,
                status = SessionStatus.ACTIVE,
                startedAt = Instant.now(),
                lastHeartbeat = Instant.now()
            )
        )
        coEvery { analyticsService.track(any(), any()) } just Runs
        coEvery { sessionRepository.addSessionEvent(any()) } returns Result.Success(
            StreamingSessionEvent(
                id = 1,
                sessionId = 1,
                eventType = SessionEventType.PLAY,
                timestamp = Instant.now()
            )
        )
        
        // When
        val result = service.startSession(request)
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals("test-session-id", result.data.sessionId)
        
        coVerify { sessionRepository.createSession(any()) }
        coVerify { analyticsService.track("streaming_session_started", any()) }
    }
    
    @Test
    fun `should enforce concurrent stream limit for free users`() = runTest {
        // Given
        val request = StartSessionRequest(
            userId = 1,
            songId = 100,
            deviceId = "device123",
            ipAddress = "192.168.1.1",
            quality = 192
        )
        
        coEvery { sessionRepository.countActiveSessionsByUserId(1) } returns Result.Success(1) // Already at limit
        coEvery { subscriptionRepository.findSubscriptionByUserId(1) } returns Result.Success(null) // Free user
        coEvery { sessionRepository.findActiveSessionsByUserId(1) } returns Result.Success(
            listOf(
                StreamingSession(
                    id = 1,
                    sessionId = "existing-session",
                    userId = 1,
                    songId = 99,
                    deviceId = "device999",
                    ipAddress = "192.168.1.2",
                    quality = 192,
                    streamType = StreamType.DIRECT,
                    status = SessionStatus.ACTIVE,
                    startedAt = Instant.now(),
                    lastHeartbeat = Instant.now()
                )
            )
        )
        
        // When
        val result = service.startSession(request)
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is PaymentException)
        assertTrue(result.message.contains("Maximum concurrent streams"))
    }
    
    @Test
    fun `should allow multiple concurrent streams for premium users`() = runTest {
        // Given
        val request = StartSessionRequest(
            userId = 1,
            songId = 100,
            deviceId = "device123",
            ipAddress = "192.168.1.1",
            quality = 320
        )
        
        coEvery { sessionRepository.countActiveSessionsByUserId(1) } returns Result.Success(2)
        coEvery { subscriptionRepository.findSubscriptionByUserId(1) } returns Result.Success(
            com.musify.domain.entities.Subscription(
                id = 1,
                userId = 1,
                planId = 2,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodStart = Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                currentPeriodEnd = Instant.now().plusSeconds(2592000).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            )
        )
        coEvery { sessionRepository.createSession(any()) } returns Result.Success(
            StreamingSession(
                id = 3,
                sessionId = "new-session-id",
                userId = 1,
                songId = 100,
                deviceId = "device123",
                ipAddress = "192.168.1.1",
                quality = 320,
                streamType = StreamType.DIRECT,
                status = SessionStatus.ACTIVE,
                startedAt = Instant.now(),
                lastHeartbeat = Instant.now()
            )
        )
        coEvery { analyticsService.track(any(), any()) } just Runs
        coEvery { sessionRepository.addSessionEvent(any()) } returns Result.Success(
            StreamingSessionEvent(
                id = 1,
                sessionId = 1,
                eventType = SessionEventType.PLAY,
                timestamp = Instant.now()
            )
        )
        
        // When
        val result = service.startSession(request)
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals("new-session-id", result.data.sessionId)
    }
    
    @Test
    fun `should validate session ownership`() = runTest {
        // Given
        val sessionId = "test-session-id"
        val userId = 1
        val wrongUserId = 2
        
        coEvery { sessionRepository.findBySessionId(sessionId) } returns Result.Success(
            StreamingSession(
                id = 1,
                sessionId = sessionId,
                userId = userId,
                songId = 100,
                deviceId = "device123",
                ipAddress = "192.168.1.1",
                quality = 192,
                streamType = StreamType.DIRECT,
                status = SessionStatus.ACTIVE,
                startedAt = Instant.now(),
                lastHeartbeat = Instant.now()
            )
        )
        
        // When
        val validResult = service.validateSession(sessionId, userId)
        val invalidResult = service.validateSession(sessionId, wrongUserId)
        
        // Then
        assertTrue(validResult is Result.Success)
        assertTrue(validResult.data)
        
        assertTrue(invalidResult is Result.Error)
        assertTrue(invalidResult.message.contains("does not belong to user"))
    }
    
    @Test
    fun `should update heartbeat and metrics`() = runTest {
        // Given
        val sessionId = "test-session-id"
        val userId = 1
        val metrics = HeartbeatMetrics(
            streamedSeconds = 30,
            streamedBytes = 1000000,
            bufferingEvents = 2,
            bufferingDuration = 500
        )
        
        coEvery { sessionRepository.findBySessionId(sessionId) } returns Result.Success(
            StreamingSession(
                id = 1,
                sessionId = sessionId,
                userId = userId,
                songId = 100,
                deviceId = "device123",
                ipAddress = "192.168.1.1",
                quality = 192,
                streamType = StreamType.DIRECT,
                status = SessionStatus.ACTIVE,
                startedAt = Instant.now(),
                lastHeartbeat = Instant.now()
            )
        )
        coEvery { sessionRepository.updateHeartbeat(sessionId) } returns Result.Success(true)
        coEvery { sessionRepository.incrementStreamedTime(sessionId, 30) } returns Result.Success(true)
        coEvery { sessionRepository.incrementStreamedBytes(sessionId, 1000000) } returns Result.Success(true)
        coEvery { sessionRepository.addSessionEvent(any()) } returns Result.Success(
            StreamingSessionEvent(
                id = 1,
                sessionId = 1,
                eventType = SessionEventType.PLAY,
                timestamp = Instant.now()
            )
        )
        
        // When
        val result = service.heartbeat(sessionId, userId, metrics)
        
        // Then
        assertTrue(result is Result.Success)
        assertTrue(result.data)
        
        coVerify { sessionRepository.updateHeartbeat(sessionId) }
        coVerify { sessionRepository.incrementStreamedTime(sessionId, 30) }
        coVerify { sessionRepository.incrementStreamedBytes(sessionId, 1000000) }
    }
    
    @Test
    fun `should end session successfully`() = runTest {
        // Given
        val sessionId = "test-session-id"
        val userId = 1
        
        coEvery { sessionRepository.findBySessionId(sessionId) } returns Result.Success(
            StreamingSession(
                id = 1,
                sessionId = sessionId,
                userId = userId,
                songId = 100,
                deviceId = "device123",
                ipAddress = "192.168.1.1",
                quality = 192,
                streamType = StreamType.DIRECT,
                status = SessionStatus.ACTIVE,
                startedAt = Instant.now().minusSeconds(300), // 5 minutes ago
                lastHeartbeat = Instant.now(),
                totalStreamedSeconds = 300,
                totalBytes = 5000000
            )
        )
        coEvery { sessionRepository.endSession(sessionId) } returns Result.Success(true)
        coEvery { analyticsService.track(any(), any()) } just Runs
        
        // When
        val result = service.endSession(sessionId, userId)
        
        // Then
        assertTrue(result is Result.Success)
        assertTrue(result.data)
        
        coVerify { sessionRepository.endSession(sessionId) }
        coVerify { analyticsService.track("streaming_session_ended", any()) }
    }
}