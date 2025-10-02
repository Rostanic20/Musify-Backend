package com.musify.infrastructure.email

import com.musify.core.config.EnvironmentConfig
import com.musify.core.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.*
import javax.mail.*
import javax.mail.internet.*

interface EmailService {
    suspend fun sendVerificationEmail(email: String, token: String): Result<Unit>
    suspend fun sendPasswordResetEmail(email: String, token: String): Result<Unit>
    suspend fun sendWelcomeEmail(email: String, displayName: String): Result<Unit>
    suspend fun sendSubscriptionConfirmation(email: String, username: String, planName: String, price: String, nextBillingDate: String): Result<Unit>
    suspend fun sendSubscriptionCancellation(email: String, username: String, endDate: String): Result<Unit>
    suspend fun sendTwoFactorEnabled(email: String, username: String): Result<Unit>
}

class EmailServiceImpl : EmailService {
    private val logger = LoggerFactory.getLogger(EmailServiceImpl::class.java)
    
    private val isEnabled = EnvironmentConfig.EMAIL_ENABLED
    private val fromAddress = EnvironmentConfig.EMAIL_FROM_ADDRESS
    private val fromName = EnvironmentConfig.EMAIL_FROM_NAME
    
    private val props = Properties().apply {
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", EnvironmentConfig.SMTP_USE_TLS.toString())
        put("mail.smtp.host", EnvironmentConfig.SMTP_HOST)
        put("mail.smtp.port", EnvironmentConfig.SMTP_PORT.toString())
    }
    
    private val session = Session.getInstance(props, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(
                EnvironmentConfig.SMTP_USERNAME ?: "",
                EnvironmentConfig.SMTP_PASSWORD ?: ""
            )
        }
    })
    
    override suspend fun sendVerificationEmail(email: String, token: String): Result<Unit> = 
        sendEmail(email) {
            val verificationUrl = "${EnvironmentConfig.API_BASE_URL}/api/auth/verify-email?token=$token"
            val username = email.substringBefore("@")
            EmailTemplates.verificationEmail(username, verificationUrl)
        }
    
    override suspend fun sendPasswordResetEmail(email: String, token: String): Result<Unit> = 
        sendEmail(email) {
            val resetUrl = "${EnvironmentConfig.API_BASE_URL}/api/auth/reset-password?token=$token"
            val username = email.substringBefore("@")
            EmailTemplates.passwordResetEmail(username, resetUrl)
        }
    
    override suspend fun sendWelcomeEmail(email: String, displayName: String): Result<Unit> = 
        sendEmail(email) {
            EmailTemplates.welcomeEmail(displayName)
        }
    
    override suspend fun sendSubscriptionConfirmation(
        email: String, 
        username: String, 
        planName: String, 
        price: String, 
        nextBillingDate: String
    ): Result<Unit> = 
        sendEmail(email) {
            EmailTemplates.subscriptionConfirmationEmail(username, planName, price, nextBillingDate)
        }
    
    override suspend fun sendSubscriptionCancellation(
        email: String, 
        username: String, 
        endDate: String
    ): Result<Unit> = 
        sendEmail(email) {
            EmailTemplates.subscriptionCancellationEmail(username, endDate)
        }
    
    override suspend fun sendTwoFactorEnabled(email: String, username: String): Result<Unit> = 
        sendEmail(email) {
            EmailTemplates.twoFactorEnabledEmail(username)
        }
    
    /**
     * Generic email sending function
     */
    private suspend fun sendEmail(
        to: String,
        contentProvider: () -> EmailContent
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isEnabled) {
            logger.info("Email service is disabled. Would have sent email to: $to")
            return@withContext Result.Success(Unit)
        }
        
        try {
            val content = contentProvider()
            
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(fromAddress, fromName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                subject = content.subject
                
                // Create multipart message for both HTML and plain text
                val multipart = MimeMultipart("alternative")
                
                // Plain text part
                val textPart = MimeBodyPart().apply {
                    setText(content.plainTextBody, "UTF-8")
                }
                
                // HTML part
                val htmlPart = MimeBodyPart().apply {
                    setContent(content.htmlBody, "text/html; charset=UTF-8")
                }
                
                multipart.addBodyPart(textPart)
                multipart.addBodyPart(htmlPart)
                
                setContent(multipart)
            }
            
            Transport.send(message)
            logger.info("Email sent successfully to: $to (subject: ${content.subject})")
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to send email to $to", e)
            Result.Error(Exception("Failed to send email: ${e.message}"))
        }
    }
}