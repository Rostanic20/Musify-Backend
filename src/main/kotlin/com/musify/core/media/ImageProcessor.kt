package com.musify.core.media

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.webp.WebpWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Service for processing and optimizing images
 */
class ImageProcessor {
    
    companion object {
        // Standard sizes for different use cases
        val PROFILE_SIZES = listOf(
            ImageSize(50, 50, "thumbnail"),
            ImageSize(150, 150, "small"),
            ImageSize(300, 300, "medium"),
            ImageSize(600, 600, "large")
        )
        
        val COVER_SIZES = listOf(
            ImageSize(100, 100, "thumbnail"),
            ImageSize(300, 300, "small"),
            ImageSize(600, 600, "medium"),
            ImageSize(1200, 1200, "large")
        )
        
        val BANNER_SIZES = listOf(
            ImageSize(1920, 400, "desktop"),
            ImageSize(1200, 250, "tablet"),
            ImageSize(600, 150, "mobile")
        )
    }
    
    /**
     * Process an image and generate multiple sizes
     */
    suspend fun processImage(
        inputStream: InputStream,
        sizes: List<ImageSize>,
        format: ImageFormat = ImageFormat.WEBP,
        quality: Int = 85
    ): Result<Map<String, ProcessedImage>> = withContext(Dispatchers.IO) {
        try {
            val originalImage = ImmutableImage.loader().fromStream(inputStream)
            val results = mutableMapOf<String, ProcessedImage>()
            
            for (size in sizes) {
                val resized = if (size.crop) {
                    // Crop to exact dimensions
                    originalImage.cover(size.width, size.height)
                } else {
                    // Scale to fit within dimensions
                    originalImage.fit(size.width, size.height)
                }
                
                val writer = when (format) {
                    ImageFormat.JPEG -> JpegWriter().withCompression(quality)
                    ImageFormat.PNG -> PngWriter.MaxCompression
                    ImageFormat.WEBP -> WebpWriter.DEFAULT.withQ(quality)
                }
                
                val bytes = resized.bytes(writer)
                
                results[size.name] = ProcessedImage(
                    data = ByteArrayInputStream(bytes),
                    format = format,
                    width = resized.width,
                    height = resized.height,
                    size = bytes.size
                )
            }
            
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Optimize a single image without resizing
     */
    suspend fun optimizeImage(
        inputStream: InputStream,
        format: ImageFormat = ImageFormat.WEBP,
        quality: Int = 85
    ): Result<ProcessedImage> = withContext(Dispatchers.IO) {
        try {
            val image = ImmutableImage.loader().fromStream(inputStream)
            
            val writer = when (format) {
                ImageFormat.JPEG -> JpegWriter().withCompression(quality)
                ImageFormat.PNG -> PngWriter.MaxCompression
                ImageFormat.WEBP -> WebpWriter.DEFAULT.withQ(quality)
            }
            
            val bytes = image.bytes(writer)
            
            Result.success(ProcessedImage(
                data = ByteArrayInputStream(bytes),
                format = format,
                width = image.width,
                height = image.height,
                size = bytes.size
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Generate a blur hash for lazy loading
     */
    suspend fun generateBlurHash(inputStream: InputStream): Result<String> = withContext(Dispatchers.IO) {
        try {
            // For now, return a placeholder
            // TODO: Implement actual blur hash generation
            Result.success("LEHV6nWB2yk8pyo0adR*.7kCMdnj")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Extract dominant color from an image
     */
    suspend fun extractDominantColor(inputStream: InputStream): Result<String> = withContext(Dispatchers.IO) {
        try {
            val image = ImmutableImage.loader().fromStream(inputStream)
            
            // Simple algorithm: get center pixel color
            // TODO: Implement proper dominant color extraction
            val centerX = image.width / 2
            val centerY = image.height / 2
            val pixel = image.pixel(centerX, centerY)
            
            val hex = String.format("#%06X", pixel.toARGBInt() and 0xFFFFFF)
            Result.success(hex)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ImageSize(
    val width: Int,
    val height: Int,
    val name: String,
    val crop: Boolean = true
)

data class ProcessedImage(
    val data: InputStream,
    val format: ImageFormat,
    val width: Int,
    val height: Int,
    val size: Int
)

enum class ImageFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}