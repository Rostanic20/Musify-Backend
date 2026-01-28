package com.musify.domain.usecase.search

import com.musify.domain.entities.SearchContext
import com.musify.domain.entities.SearchFilters
import com.musify.domain.entities.SearchResult
import com.musify.domain.repository.SearchRepository
import java.util.Base64

interface VoiceRecognitionService {
    suspend fun transcribe(audioData: ByteArray, format: String, language: String): TranscriptionResult
}

data class TranscriptionResult(
    val text: String,
    val confidence: Double,
    val language: String,
    val alternatives: List<String> = emptyList()
)

class VoiceSearchUseCase(
    private val searchRepository: SearchRepository,
    private val voiceRecognitionService: VoiceRecognitionService,
    private val searchUseCase: SearchUseCase
) {
    
    suspend fun execute(
        audioData: String, // Base64 encoded
        format: String = "webm",
        language: String = "en-US",
        userId: Int? = null
    ): Result<VoiceSearchResult> {
        return try {
            // Decode audio data
            val audioBytes = Base64.getDecoder().decode(audioData)
            
            // Transcribe audio to text
            val transcription = voiceRecognitionService.transcribe(audioBytes, format, language)
            
            if (transcription.confidence < 0.5) {
                return Result.failure(
                    IllegalStateException("Low confidence transcription: ${transcription.confidence}")
                )
            }
            
            // Perform search with transcribed text
            val searchResult = searchUseCase.execute(
                query = transcription.text,
                filters = SearchFilters(),
                userId = userId,
                context = SearchContext.VOICE,
                limit = 20,
                offset = 0
            ).getOrThrow()
            
            // Save voice search history
            userId?.let {
                searchRepository.saveVoiceSearch(
                    userId = it,
                    audioUrl = null, // Could upload to S3 and store URL
                    transcription = transcription.text,
                    confidence = transcription.confidence,
                    language = language,
                    searchHistoryId = null // Would need to get from search history
                )
            }
            
            Result.success(
                VoiceSearchResult(
                    transcription = transcription.text,
                    confidence = transcription.confidence,
                    alternatives = transcription.alternatives,
                    searchResult = searchResult
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class VoiceSearchResult(
    val transcription: String,
    val confidence: Double,
    val alternatives: List<String>,
    val searchResult: SearchResult
)