#!/usr/bin/env kotlin

// Script to debug the registration issue

import java.net.HttpURLConnection
import java.net.URL

fun testRegistration(email: String, username: String): Pair<Int, String> {
    val url = URL("http://localhost:8080/api/auth/register")
    val connection = url.openConnection() as HttpURLConnection
    
    connection.requestMethod = "POST"
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true
    
    val requestBody = """
        {
            "email": "$email",
            "username": "$username",
            "password": "Test1234!",
            "displayName": "Test User"
        }
    """.trimIndent()
    
    connection.outputStream.use { os ->
        os.write(requestBody.toByteArray())
    }
    
    val responseCode = connection.responseCode
    val responseBody = if (responseCode < 400) {
        connection.inputStream.bufferedReader().use { it.readText() }
    } else {
        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
    }
    
    return Pair(responseCode, responseBody)
}

fun main() {
    // Test 1: Register a new user
    val timestamp = System.currentTimeMillis()
    val email = "test_${timestamp}@example.com"
    val username = "user_${timestamp}"
    
    println("Test 1: Registering new user")
    println("Email: $email")
    println("Username: $username")
    
    val (code1, body1) = testRegistration(email, username)
    println("Response Code: $code1")
    println("Response Body: $body1")
    println()
    
    // Test 2: Try to register with same email
    println("Test 2: Registering with duplicate email")
    val (code2, body2) = testRegistration(email, "different_username")
    println("Response Code: $code2")
    println("Response Body: $body2")
    println()
    
    // Test 3: Try to register with same username
    println("Test 3: Registering with duplicate username")
    val (code3, body3) = testRegistration("different_${timestamp}@example.com", username)
    println("Response Code: $code3")
    println("Response Body: $body3")
}

main()