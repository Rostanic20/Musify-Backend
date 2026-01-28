package com.musify.infrastructure.middleware

import com.musify.core.utils.InputSanitizer
import com.musify.core.config.EnvironmentConfig
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Comprehensive input validation middleware for security hardening
 * Applies to all incoming requests to prevent various attack vectors
 */
object InputValidationMiddleware {
    private val logger = LoggerFactory.getLogger(InputValidationMiddleware::class.java)
    
    // Security limits to prevent DoS attacks
    private const val MAX_REQUEST_SIZE = 10 * 1024 * 1024 // 10MB
    private const val MAX_HEADER_SIZE = 8192 // 8KB
    private const val MAX_URL_LENGTH = 2048
    private const val MAX_JSON_DEPTH = 10
    private const val MAX_ARRAY_SIZE = 1000
    private const val MAX_FIELD_COUNT = 100
    
    // Dangerous patterns to block
    private val DANGEROUS_PATTERNS = listOf(
        // SQL Injection patterns
        Regex("(union|select|insert|update|delete|drop|create|alter)\\s", RegexOption.IGNORE_CASE),
        Regex("(script|exec|execute)\\s", RegexOption.IGNORE_CASE),
        Regex("(--|#|/\\*)", RegexOption.IGNORE_CASE),
        
        // XSS patterns
        Regex("<script[^>]*>", RegexOption.IGNORE_CASE),
        Regex("javascript:", RegexOption.IGNORE_CASE),
        Regex("vbscript:", RegexOption.IGNORE_CASE),
        Regex("on(load|error|click|focus|blur)\\s*=", RegexOption.IGNORE_CASE),
        
        // Path traversal patterns
        Regex("\\.{2,}[/\\\\]"),
        Regex("(\\.\\./)|(\\.\\.\\\\)"),
        
        // Command injection patterns
        Regex("[;&|`$(){}\\[\\]]"),
        
        // LDAP injection patterns
        Regex("[()&|!<>=*]"),
        
        // NoSQL injection patterns
        Regex("\\\$where|\\\$ne|\\\$gt|\\\$lt|\\\$or|\\\$and", RegexOption.IGNORE_CASE)
    )
    
    // File upload patterns
    private val DANGEROUS_FILE_EXTENSIONS = setOf(
        "exe", "bat", "cmd", "scr", "pif", "vbs", "vbe", "js", "jse", 
        "wsf", "wsh", "com", "dll", "pif", "scr", "hta", "cpl", "msc", "jar"
    )
    
    fun install(): PluginBuilder<Unit>.() -> Unit = {
        onCall { call ->
            try {
                validateRequest(call)
            } catch (e: SecurityException) {
                logger.warn("Security validation failed for ${call.request.uri}: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Request validation failed",
                    "message" to "Invalid or potentially dangerous input detected"
                ))
            } catch (e: Exception) {
                logger.error("Input validation middleware error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to "Request processing failed"
                ))
            }
        }
    }
    
    private suspend fun validateRequest(call: ApplicationCall) {
        // 1. Basic request validation
        validateBasicRequest(call)
        
        // 2. URL and query parameter validation
        validateUrlAndParameters(call)
        
        // 3. Header validation
        validateHeaders(call)
        
        // 4. Content validation (if applicable)
        if (call.request.httpMethod in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)) {
            validateRequestBody(call)
        }
        
        // 5. File upload validation (if applicable)
        if (call.request.contentType()?.match(ContentType.MultiPart.FormData) == true) {
            validateFileUpload(call)
        }
    }
    
    private fun validateBasicRequest(call: ApplicationCall) {
        val request = call.request
        
        // Validate URL length
        if (request.uri.length > MAX_URL_LENGTH) {
            throw SecurityException("Request URL too long: ${request.uri.length} > $MAX_URL_LENGTH")
        }
        
        // Validate content length
        val contentLength = request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_REQUEST_SIZE) {
            throw SecurityException("Request body too large: $contentLength > $MAX_REQUEST_SIZE")
        }
        
        // Validate HTTP method
        if (request.httpMethod !in listOf(
                HttpMethod.Get, HttpMethod.Post, HttpMethod.Put, 
                HttpMethod.Delete, HttpMethod.Patch, HttpMethod.Options, HttpMethod.Head
            )) {
            throw SecurityException("Unsupported HTTP method: ${request.httpMethod.value}")
        }
    }
    
    private fun validateUrlAndParameters(call: ApplicationCall) {
        val request = call.request
        
        // Validate URL path for dangerous patterns
        val decodedPath = try {
            URLDecoder.decode(request.path(), StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            throw SecurityException("Invalid URL encoding in path")
        }
        
        validateInputAgainstPatterns(decodedPath, "URL path")
        
        // Validate query parameters
        request.queryParameters.entries().forEach { (key, values) ->
            validateParameterName(key)
            values.forEach { value ->
                val decodedValue = try {
                    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
                } catch (e: Exception) {
                    throw SecurityException("Invalid URL encoding in parameter: $key")
                }
                validateInputAgainstPatterns(decodedValue, "Query parameter: $key")
            }
        }
    }
    
    private fun validateHeaders(call: ApplicationCall) {
        val request = call.request
        
        request.headers.entries().forEach { (name, values) ->
            values.forEach { value ->
                // Validate header size
                if (value.length > MAX_HEADER_SIZE) {
                    throw SecurityException("Header too large: $name")
                }
                
                // Validate against dangerous patterns (except for specific headers)
                if (name.lowercase() !in listOf("authorization", "cookie", "user-agent")) {
                    validateInputAgainstPatterns(value, "Header: $name")
                }
            }
        }
        
        // Validate specific headers
        validateUserAgent(request.header(HttpHeaders.UserAgent))
        validateContentType(request.header(HttpHeaders.ContentType))
    }
    
    private suspend fun validateRequestBody(call: ApplicationCall) {
        val contentType = call.request.contentType()
        
        when {
            contentType?.match(ContentType.Application.Json) == true -> {
                validateJsonBody(call)
            }
            contentType?.match(ContentType.Application.FormUrlEncoded) == true -> {
                validateFormBody(call)
            }
            contentType?.match(ContentType.Text.Plain) == true -> {
                validateTextBody(call)
            }
        }
    }
    
    private suspend fun validateJsonBody(call: ApplicationCall) {
        try {
            val bodyText = call.receiveText()
            
            // Check JSON size
            if (bodyText.length > MAX_REQUEST_SIZE) {
                throw SecurityException("JSON body too large")
            }
            
            // Parse and validate JSON structure
            val json = Json.parseToJsonElement(bodyText)
            validateJsonStructure(json, 0)
            
            // Validate JSON content for dangerous patterns
            validateJsonContent(json)
            
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            logger.warn("JSON parsing error: ${e.message}")
            throw SecurityException("Invalid JSON format")
        }
    }
    
    private fun validateJsonStructure(element: JsonElement, depth: Int) {
        if (depth > MAX_JSON_DEPTH) {
            throw SecurityException("JSON nesting too deep: $depth > $MAX_JSON_DEPTH")
        }
        
        when (element) {
            is JsonObject -> {
                if (element.size > MAX_FIELD_COUNT) {
                    throw SecurityException("Too many JSON fields: ${element.size} > $MAX_FIELD_COUNT")
                }
                element.values.forEach { validateJsonStructure(it, depth + 1) }
            }
            is JsonArray -> {
                if (element.size > MAX_ARRAY_SIZE) {
                    throw SecurityException("JSON array too large: ${element.size} > $MAX_ARRAY_SIZE")
                }
                element.forEach { validateJsonStructure(it, depth + 1) }
            }
            else -> {
                // Handle JsonPrimitive and JsonNull cases
            }
        }
    }
    
    private fun validateJsonContent(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                element.forEach { (key, value) ->
                    validateInputAgainstPatterns(key, "JSON key")
                    validateJsonContent(value)
                }
            }
            is JsonArray -> {
                element.forEach { validateJsonContent(it) }
            }
            is JsonPrimitive -> {
                if (element.isString) {
                    validateInputAgainstPatterns(element.content, "JSON value")
                }
            }
        }
    }
    
    private suspend fun validateFormBody(call: ApplicationCall) {
        try {
            val parameters = call.receiveParameters()
            
            if (parameters.entries().size > MAX_FIELD_COUNT) {
                throw SecurityException("Too many form fields: ${parameters.entries().size} > $MAX_FIELD_COUNT")
            }
            
            parameters.entries().forEach { (name, values) ->
                validateParameterName(name)
                values.forEach { value ->
                    validateInputAgainstPatterns(value, "Form field: $name")
                }
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Form parsing error: ${e.message}")
            throw SecurityException("Invalid form data")
        }
    }
    
    private suspend fun validateTextBody(call: ApplicationCall) {
        try {
            val bodyText = call.receiveText()
            
            if (bodyText.length > MAX_REQUEST_SIZE) {
                throw SecurityException("Text body too large")
            }
            
            validateInputAgainstPatterns(bodyText, "Request body")
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Text body parsing error: ${e.message}")
            throw SecurityException("Invalid text data")
        }
    }
    
    private fun validateFileUpload(call: ApplicationCall) {
        // Note: File upload validation would need to be integrated with 
        // the specific file upload handling in the application
        // This is a placeholder for file upload validation logic
        logger.info("File upload validation - implement specific validation based on upload handling")
    }
    
    private fun validateInputAgainstPatterns(input: String, context: String) {
        // Skip validation for very short inputs that are unlikely to be malicious
        if (input.length < 2) return
        
        DANGEROUS_PATTERNS.forEach { pattern ->
            if (pattern.containsMatchIn(input)) {
                throw SecurityException("Potentially dangerous pattern detected in $context")
            }
        }
        
        // Additional validation for specific contexts
        validateSpecialCharacters(input, context)
        validateUnicodeExploits(input, context)
    }
    
    private fun validateSpecialCharacters(input: String, context: String) {
        // Check for null bytes and other control characters
        if (input.contains('\u0000')) {
            throw SecurityException("Null byte detected in $context")
        }
        
        // Check for excessive control characters
        val controlCharCount = input.count { it.isISOControl() && it != '\t' && it != '\n' && it != '\r' }
        if (controlCharCount > input.length * 0.1) {
            throw SecurityException("Excessive control characters in $context")
        }
    }
    
    private fun validateUnicodeExploits(input: String, context: String) {
        // Check for Unicode normalization attacks
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFC)
        if (normalized != input && normalized.length != input.length) {
            logger.warn("Unicode normalization changed input in $context")
        }
        
        // Check for homograph attacks (basic detection)
        val suspiciousUnicodeRanges = listOf(
            '\u0400'..'\u04FF', // Cyrillic
            '\u0370'..'\u03FF', // Greek
            '\u0590'..'\u05FF'  // Hebrew
        )
        
        val hasLatinLike = input.any { it in 'A'..'Z' || it in 'a'..'z' }
        val hasSuspiciousUnicode = suspiciousUnicodeRanges.any { range -> 
            input.any { it in range } 
        }
        
        if (hasLatinLike && hasSuspiciousUnicode) {
            logger.warn("Potential homograph attack detected in $context")
        }
    }
    
    private fun validateParameterName(name: String) {
        if (name.length > 100) {
            throw SecurityException("Parameter name too long: ${name.length}")
        }
        
        // Parameter names should be alphanumeric with underscores and dashes
        if (!name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            throw SecurityException("Invalid parameter name: $name")
        }
    }
    
    private fun validateUserAgent(userAgent: String?) {
        if (userAgent != null) {
            if (userAgent.length > 500) {
                throw SecurityException("User-Agent header too long")
            }
            
            // Check for suspicious user agents
            val suspiciousPatterns = listOf(
                "sqlmap", "nikto", "nmap", "masscan", "burp", "owasp",
                "python-requests", "curl", "wget"
            )
            
            if (suspiciousPatterns.any { userAgent.contains(it, ignoreCase = true) }) {
                logger.warn("Suspicious User-Agent detected: $userAgent")
            }
        }
    }
    
    private fun validateContentType(contentType: String?) {
        if (contentType != null && contentType.length > 200) {
            throw SecurityException("Content-Type header too long")
        }
    }
}

/**
 * Plugin for installing input validation middleware
 */
val InputValidation = createApplicationPlugin("InputValidation") {
    InputValidationMiddleware.install()
}

/**
 * Extension function to configure input validation
 */
fun Application.configureInputValidation() {
    if (EnvironmentConfig.IS_PRODUCTION || EnvironmentConfig.ENVIRONMENT != "test") {
        install(InputValidation)
        
        // Log middleware installation
        val logger = LoggerFactory.getLogger("InputValidation")
        logger.info("🛡️  Input validation middleware enabled")
    }
}