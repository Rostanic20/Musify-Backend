package com.musify.infrastructure.cache

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlin.random.Random

class CompressionServiceTest {
    
    private lateinit var compressionService: CompressionService
    
    @BeforeEach
    fun setup() {
        compressionService = CompressionService(compressionThreshold = 1024)
    }
    
    @Test
    fun `test small data is not compressed`() {
        // Given
        val smallData = "This is a small string that should not be compressed"
        
        // When
        val result = compressionService.compress(smallData)
        
        // Then
        assertEquals(smallData, result)
        assertFalse(result.startsWith("gzip:"))
    }
    
    @Test
    fun `test large data is compressed`() {
        // Given
        val largeData = "x".repeat(2000) // Highly compressible
        
        // When
        val compressed = compressionService.compress(largeData)
        
        // Then
        assertTrue(compressed.startsWith("gzip:"))
        assertTrue(compressed.length < largeData.length)
        
        // Verify decompression
        val decompressed = compressionService.decompress(compressed)
        assertEquals(largeData, decompressed)
    }
    
    @Test
    fun `test compression ratio calculation`() {
        // Given
        val original = "x".repeat(2000)
        val compressed = compressionService.compress(original)
        
        // When
        val ratio = compressionService.calculateCompressionRatio(original, compressed)
        
        // Then
        assertTrue(ratio < 0.1, "Repetitive data should compress well")
    }
    
    @Test
    fun `test incompressible data is not compressed`() {
        // Given - Random data doesn't compress well
        val randomData = Random.nextBytes(2000).toString()
        
        // When
        val result = compressionService.compress(randomData)
        
        // Then
        // Should not compress if ratio > 0.9
        if (!result.startsWith("gzip:")) {
            assertEquals(randomData, result)
        }
    }
    
    @Test
    fun `test decompression of uncompressed data`() {
        // Given
        val uncompressedData = "This is not compressed"
        
        // When
        val result = compressionService.decompress(uncompressedData)
        
        // Then
        assertEquals(uncompressedData, result)
    }
    
    @Test
    fun `test compression preserves special characters`() {
        // Given
        val specialData = "Special chars: 🎵🎸🎤 ñáéíóú 中文 © ® ™" + "x".repeat(1000)
        
        // When
        val compressed = compressionService.compress(specialData)
        val decompressed = compressionService.decompress(compressed)
        
        // Then
        assertEquals(specialData, decompressed)
    }
    
    @Test
    fun `test compression threshold`() {
        // Given
        val justBelowThreshold = "x".repeat(1023)
        val justAboveThreshold = "x".repeat(1025)
        
        // When
        val belowResult = compressionService.compress(justBelowThreshold)
        val aboveResult = compressionService.compress(justAboveThreshold)
        
        // Then
        assertFalse(belowResult.startsWith("gzip:"), "Below threshold should not compress")
        assertTrue(aboveResult.startsWith("gzip:"), "Above threshold should compress")
    }
    
    @Test
    fun `test malformed compressed data throws exception`() {
        // Given
        val malformedData = "gzip:invalid-base64-@#$%"
        
        // When/Then
        assertThrows(Exception::class.java) {
            compressionService.decompress(malformedData)
        }
    }
    
    @Test
    fun `test compression performance`() {
        // Given
        val testData = "x".repeat(10000)
        val iterations = 1000
        
        // When
        val startTime = System.currentTimeMillis()
        repeat(iterations) {
            val compressed = compressionService.compress(testData)
            compressionService.decompress(compressed)
        }
        val duration = System.currentTimeMillis() - startTime
        
        // Then
        val avgTime = duration.toDouble() / iterations
        assertTrue(avgTime < 10, "Compression should be fast (avg: ${avgTime}ms)")
    }
    
    @Test
    fun `test various data patterns`() {
        val testCases = mapOf(
            "JSON" to """{"id":1,"name":"Test","data":"${"x".repeat(1500)}"}""",
            "XML" to """<root><data>${"<item>test</item>".repeat(100)}</data></root>""",
            "CSV" to (1..200).joinToString("\n") { "col1,col2,col3,col4,col5" },
            "Log" to (1..100).joinToString("\n") { "[INFO] 2024-01-01 12:00:00 - Message $it" }
        )
        
        testCases.forEach { (type, data) ->
            val compressed = compressionService.compress(data)
            val decompressed = compressionService.decompress(compressed)
            
            assertEquals(data, decompressed, "$type data should compress/decompress correctly")
            
            if (compressed.startsWith("gzip:")) {
                assertTrue(compressed.length < data.length, "$type should compress effectively")
            }
        }
    }
}