package com.musify.infrastructure.auth

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.util.Base64

class TotpService {
    private val config = TimeBasedOneTimePasswordConfig(
        codeDigits = 6,
        hmacAlgorithm = HmacAlgorithm.SHA1,
        timeStep = 30,
        timeStepUnit = TimeUnit.SECONDS
    )
    
    private val generator = TimeBasedOneTimePasswordGenerator(secret = ByteArray(0), config = config)
    private val base64 = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()
    
    fun generateSecret(): String {
        val random = SecureRandom()
        val bytes = ByteArray(20) // 160 bits
        random.nextBytes(bytes)
        return base64.encodeToString(bytes)
    }
    
    fun validateCode(secret: String, code: String): Boolean {
        return try {
            val secretBytes = base64Decoder.decode(secret)
            val generator = TimeBasedOneTimePasswordGenerator(secret = secretBytes, config = config)
            
            val expectedCode = generator.generate()
            
            // Check current code
            if (expectedCode == code) return true
            
            // Check previous code (30 seconds ago) for clock skew tolerance
            val previousCode = generator.generate(System.currentTimeMillis() - 30000)
            if (previousCode == code) return true
            
            // Check next code (30 seconds ahead) for clock skew tolerance
            val nextCode = generator.generate(System.currentTimeMillis() + 30000)
            nextCode == code
        } catch (e: Exception) {
            false
        }
    }
    
    fun generateQrCodeUrl(issuer: String, accountName: String, secret: String): String {
        // Format: otpauth://totp/ISSUER:ACCOUNT?secret=SECRET&issuer=ISSUER
        val secretBase32 = base64ToBase32(secret)
        return "otpauth://totp/${issuer}:${accountName}?secret=${secretBase32}&issuer=${issuer}"
    }
    
    fun generateQrCodeImage(issuer: String, accountName: String, secret: String): ByteArray {
        val qrCodeUrl = generateQrCodeUrl(issuer, accountName, secret)
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(qrCodeUrl, BarcodeFormat.QR_CODE, 200, 200)
        
        val outputStream = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
        return outputStream.toByteArray()
    }
    
    private fun base64ToBase32(base64String: String): String {
        val bytes = base64Decoder.decode(base64String)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        
        var buffer = 0
        var bitsLeft = 0
        
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                result.append(alphabet[index])
                bitsLeft -= 5
            }
        }
        
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            result.append(alphabet[index])
        }
        
        return result.toString()
    }
}