package com.musify.core.utils

/**
 * Utility object for sanitizing user input to prevent XSS attacks
 */
object InputSanitizer {
    
    private val htmlCharacterMap = mapOf(
        '<' to "&lt;",
        '>' to "&gt;",
        '"' to "&quot;",
        '\'' to "&#x27;",
        '&' to "&amp;",
        '/' to "&#x2F;",
        '`' to "&#x60;",
        '=' to "&#x3D;"
    )
    
    /**
     * Sanitizes a string by escaping HTML special characters to prevent XSS attacks
     */
    fun sanitizeHtml(input: String?): String? {
        if (input == null) return null
        
        // First, remove dangerous protocols
        var sanitized = input
            .replace(Regex("javascript:", RegexOption.IGNORE_CASE), "")
            .replace(Regex("vbscript:", RegexOption.IGNORE_CASE), "")
            .replace(Regex("data:text/html", RegexOption.IGNORE_CASE), "")
            .replace(Regex("data:application/x-javascript", RegexOption.IGNORE_CASE), "")
        
        // Then escape HTML special characters
        return sanitized.fold(StringBuilder()) { acc, char ->
            htmlCharacterMap[char]?.let { acc.append(it) } ?: acc.append(char)
            acc
        }.toString()
    }
    
    /**
     * Sanitizes a string for safe SQL usage (basic protection, use parameterized queries for full protection)
     */
    fun sanitizeSql(input: String?): String? {
        if (input == null) return null
        
        return input
            .replace("'", "''")
            .replace("\\", "\\\\")
            .replace("\u0000", "") // Remove null bytes
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\u001a", "") // Remove SUB character
    }
    
    /**
     * Removes all HTML tags from input (more aggressive than escaping)
     */
    fun stripHtml(input: String?): String? {
        if (input == null) return null
        
        return input
            .replace(Regex("<[^>]*>"), "") // Remove all HTML tags
            .replace(Regex("&[a-zA-Z][a-zA-Z0-9]*;"), "") // Remove HTML entities
            .trim()
    }
    
    /**
     * Validates and sanitizes input based on the context
     */
    fun sanitizeForContext(input: String?, context: SanitizationContext): String? {
        if (input == null) return null
        
        return when (context) {
            SanitizationContext.HTML_CONTENT -> sanitizeHtml(input)
            SanitizationContext.PLAIN_TEXT -> stripHtml(input)
            SanitizationContext.SQL_PARAMETER -> sanitizeSql(input)
            SanitizationContext.FILE_NAME -> sanitizeFileName(input)
        }
    }
    
    /**
     * Sanitizes file names to prevent directory traversal attacks
     */
    private fun sanitizeFileName(input: String): String {
        return input
            .replace("..", "")
            .replace("/", "")
            .replace("\\", "")
            .replace("\u0000", "")
            .take(255) // Limit file name length
    }
    
    enum class SanitizationContext {
        HTML_CONTENT,
        PLAIN_TEXT,
        SQL_PARAMETER,
        FILE_NAME
    }
}