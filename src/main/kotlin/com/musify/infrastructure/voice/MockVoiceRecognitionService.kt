package com.musify.infrastructure.voice

import com.musify.domain.usecase.search.VoiceRecognitionService
import com.musify.domain.usecase.search.TranscriptionResult

/**
 * Mock implementation of voice recognition service for testing
 * In production, this would integrate with Google Cloud Speech-to-Text, AWS Transcribe, or similar
 */
class MockVoiceRecognitionService : VoiceRecognitionService {
    
    override suspend fun transcribe(
        audioData: ByteArray,
        format: String,
        language: String
    ): TranscriptionResult {
        // Simulate processing time
        kotlinx.coroutines.delay(500)
        
        // Mock responses based on audio data size
        val mockTranscriptions = listOf(
            "play taylor swift",
            "find songs like shape of you",
            "create workout playlist",
            "play my discover weekly",
            "what's trending today",
            "play some chill music",
            "find happy songs from the 80s"
        )
        
        val index = audioData.size % mockTranscriptions.size
        val transcription = mockTranscriptions[index]
        
        // Generate alternatives
        val alternatives = when {
            transcription.contains("taylor") -> listOf(
                "play taylor swift",
                "play tailor swift",
                "play taylor's gift"
            )
            transcription.contains("workout") -> listOf(
                "create workout playlist",
                "great workout playlist",
                "create work out playlist"
            )
            else -> emptyList()
        }
        
        return TranscriptionResult(
            text = transcription,
            confidence = 0.95,
            language = language,
            alternatives = alternatives
        )
    }
}