package com.musify.utils

import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom

object PasswordUtils {
    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }
    
    fun verifyPassword(password: String, hash: String): Boolean {
        return BCrypt.checkpw(password, hash)
    }
    
    fun generateRandomPassword(): String {
        // Generate a random password for OAuth users (they won't use it)
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        val random = SecureRandom()
        val password = StringBuilder()
        for (i in 0 until 32) {
            password.append(chars[random.nextInt(chars.length)])
        }
        return hashPassword(password.toString())
    }
}