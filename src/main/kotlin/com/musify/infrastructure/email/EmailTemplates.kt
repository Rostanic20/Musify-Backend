package com.musify.infrastructure.email

import com.musify.core.config.EnvironmentConfig
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Email template system for consistent branding
 */
object EmailTemplates {
    
    private val APP_NAME = "Musify"
    private val APP_URL = EnvironmentConfig.API_BASE_URL
    private val SUPPORT_EMAIL = "support@musify.com"
    private val YEAR = LocalDateTime.now().year
    
    /**
     * Base HTML template for all emails
     */
    private fun baseTemplate(title: String, content: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$title</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                    line-height: 1.6;
                    color: #333;
                    background-color: #f8f9fa;
                    margin: 0;
                    padding: 0;
                }
                .container {
                    max-width: 600px;
                    margin: 0 auto;
                    background-color: #ffffff;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }
                .header {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    color: #ffffff;
                    padding: 40px 20px;
                    text-align: center;
                }
                .logo {
                    font-size: 32px;
                    font-weight: bold;
                    margin: 0;
                }
                .content {
                    padding: 40px 30px;
                }
                .button {
                    display: inline-block;
                    padding: 12px 32px;
                    background-color: #667eea;
                    color: #ffffff;
                    text-decoration: none;
                    border-radius: 5px;
                    font-weight: bold;
                    margin: 20px 0;
                }
                .button:hover {
                    background-color: #5a67d8;
                }
                .footer {
                    background-color: #f8f9fa;
                    padding: 20px 30px;
                    text-align: center;
                    font-size: 14px;
                    color: #6c757d;
                }
                .social-links {
                    margin: 20px 0;
                }
                .social-links a {
                    margin: 0 10px;
                    color: #6c757d;
                    text-decoration: none;
                }
                code {
                    background-color: #f3f4f6;
                    padding: 2px 6px;
                    border-radius: 3px;
                    font-family: 'Courier New', monospace;
                }
                .warning {
                    background-color: #fef3c7;
                    border-left: 4px solid #f59e0b;
                    padding: 12px;
                    margin: 20px 0;
                }
                .info {
                    background-color: #dbeafe;
                    border-left: 4px solid #3b82f6;
                    padding: 12px;
                    margin: 20px 0;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1 class="logo">🎵 $APP_NAME</h1>
                    <p style="margin: 0; opacity: 0.9;">Your Music, Your Way</p>
                </div>
                <div class="content">
                    $content
                </div>
                <div class="footer">
                    <div class="social-links">
                        <a href="#">Twitter</a>
                        <a href="#">Facebook</a>
                        <a href="#">Instagram</a>
                    </div>
                    <p>&copy; $YEAR $APP_NAME. All rights reserved.</p>
                    <p>
                        <a href="$APP_URL/privacy" style="color: #6c757d;">Privacy Policy</a> | 
                        <a href="$APP_URL/terms" style="color: #6c757d;">Terms of Service</a>
                    </p>
                    <p style="font-size: 12px; margin-top: 20px;">
                        You received this email because you have an account with $APP_NAME. 
                        If you have any questions, please contact us at <a href="mailto:$SUPPORT_EMAIL">$SUPPORT_EMAIL</a>.
                    </p>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
    
    /**
     * Welcome email for new users
     */
    fun welcomeEmail(username: String): EmailContent {
        val content = """
            <h2>Welcome to $APP_NAME, $username! 🎉</h2>
            <p>We're thrilled to have you join our community of music lovers. Get ready to discover, stream, and share your favorite tunes!</p>
            
            <h3>Here's what you can do with $APP_NAME:</h3>
            <ul>
                <li><strong>Stream millions of songs</strong> - From chart-toppers to hidden gems</li>
                <li><strong>Create custom playlists</strong> - Organize your music your way</li>
                <li><strong>Discover new artists</strong> - Our AI-powered recommendations learn what you love</li>
                <li><strong>Follow friends</strong> - See what your friends are listening to</li>
                <li><strong>Offline listening</strong> - Download songs for when you're on the go (Premium)</li>
            </ul>
            
            <div style="text-align: center;">
                <a href="$APP_URL/explore" class="button">Start Exploring</a>
            </div>
            
            <div class="info">
                <strong>Pro tip:</strong> Complete your profile to get better music recommendations!
            </div>
            
            <p>If you have any questions, our support team is here to help 24/7.</p>
            <p>Happy listening! 🎧</p>
        """.trimIndent()
        
        return EmailContent(
            subject = "Welcome to $APP_NAME! 🎵",
            htmlBody = baseTemplate("Welcome to $APP_NAME", content),
            plainTextBody = "Welcome to $APP_NAME, $username! Visit $APP_URL to start exploring."
        )
    }
    
    /**
     * Email verification template
     */
    fun verificationEmail(username: String, verificationUrl: String): EmailContent {
        val content = """
            <h2>Verify Your Email Address</h2>
            <p>Hi $username,</p>
            <p>Thanks for signing up for $APP_NAME! Please verify your email address to unlock all features.</p>
            
            <div style="text-align: center;">
                <a href="$verificationUrl" class="button">Verify Email Address</a>
            </div>
            
            <p>Or copy and paste this link into your browser:</p>
            <p><code>$verificationUrl</code></p>
            
            <div class="warning">
                <strong>Important:</strong> This verification link will expire in 24 hours.
            </div>
            
            <p>If you didn't create an account with $APP_NAME, please ignore this email.</p>
        """.trimIndent()
        
        return EmailContent(
            subject = "Verify your $APP_NAME account",
            htmlBody = baseTemplate("Email Verification", content),
            plainTextBody = "Hi $username, verify your email by visiting: $verificationUrl"
        )
    }
    
    /**
     * Password reset email template
     */
    fun passwordResetEmail(username: String, resetUrl: String): EmailContent {
        val content = """
            <h2>Reset Your Password</h2>
            <p>Hi $username,</p>
            <p>We received a request to reset your password. Click the button below to create a new password:</p>
            
            <div style="text-align: center;">
                <a href="$resetUrl" class="button">Reset Password</a>
            </div>
            
            <p>Or copy and paste this link into your browser:</p>
            <p><code>$resetUrl</code></p>
            
            <div class="warning">
                <strong>Security Notice:</strong> This link will expire in 1 hour for your protection.
            </div>
            
            <p>If you didn't request a password reset, please ignore this email. Your password won't be changed.</p>
            
            <div class="info">
                <strong>Security tip:</strong> Use a strong, unique password that you don't use on other websites.
            </div>
        """.trimIndent()
        
        return EmailContent(
            subject = "Reset your $APP_NAME password",
            htmlBody = baseTemplate("Password Reset", content),
            plainTextBody = "Hi $username, reset your password by visiting: $resetUrl"
        )
    }
    
    /**
     * Subscription confirmation email
     */
    fun subscriptionConfirmationEmail(
        username: String,
        planName: String,
        price: String,
        nextBillingDate: String
    ): EmailContent {
        val content = """
            <h2>Welcome to $APP_NAME Premium! 🎉</h2>
            <p>Hi $username,</p>
            <p>Your subscription to <strong>$planName</strong> is now active. Get ready for an enhanced music experience!</p>
            
            <div style="background-color: #f8f9fa; padding: 20px; border-radius: 5px; margin: 20px 0;">
                <h3 style="margin-top: 0;">Subscription Details:</h3>
                <table style="width: 100%;">
                    <tr>
                        <td><strong>Plan:</strong></td>
                        <td>$planName</td>
                    </tr>
                    <tr>
                        <td><strong>Price:</strong></td>
                        <td>$price</td>
                    </tr>
                    <tr>
                        <td><strong>Next billing date:</strong></td>
                        <td>$nextBillingDate</td>
                    </tr>
                </table>
            </div>
            
            <h3>Your Premium Benefits:</h3>
            <ul>
                <li>✓ Ad-free listening</li>
                <li>✓ Unlimited skips</li>
                <li>✓ High-quality audio (up to 320kbps)</li>
                <li>✓ Download songs for offline listening</li>
                <li>✓ Exclusive content and early access</li>
            </ul>
            
            <div style="text-align: center;">
                <a href="$APP_URL/account/subscription" class="button">Manage Subscription</a>
            </div>
            
            <p>Thank you for supporting $APP_NAME! If you have any questions about your subscription, please don't hesitate to contact us.</p>
        """.trimIndent()
        
        return EmailContent(
            subject = "Your $APP_NAME Premium subscription is active! 🎵",
            htmlBody = baseTemplate("Subscription Confirmation", content),
            plainTextBody = "Hi $username, your $planName subscription is now active. Next billing: $nextBillingDate"
        )
    }
    
    /**
     * Subscription cancellation email
     */
    fun subscriptionCancellationEmail(username: String, endDate: String): EmailContent {
        val content = """
            <h2>Your Subscription Has Been Cancelled</h2>
            <p>Hi $username,</p>
            <p>We're sorry to see you go! Your $APP_NAME Premium subscription has been cancelled.</p>
            
            <div class="info">
                <strong>Important:</strong> You'll continue to have Premium access until <strong>$endDate</strong>.
            </div>
            
            <p>After this date, your account will revert to the free tier with these limitations:</p>
            <ul>
                <li>Limited skips (6 per hour)</li>
                <li>Audio ads between songs</li>
                <li>Standard quality audio</li>
                <li>No offline downloads</li>
            </ul>
            
            <h3>We'd Love Your Feedback</h3>
            <p>Help us improve by telling us why you cancelled:</p>
            <div style="text-align: center;">
                <a href="$APP_URL/feedback/cancellation" class="button">Share Feedback</a>
            </div>
            
            <p>Changed your mind? You can reactivate your subscription anytime:</p>
            <div style="text-align: center;">
                <a href="$APP_URL/premium" class="button" style="background-color: #10b981;">Reactivate Premium</a>
            </div>
            
            <p>Thank you for being a part of the $APP_NAME community!</p>
        """.trimIndent()
        
        return EmailContent(
            subject = "Your $APP_NAME subscription has been cancelled",
            htmlBody = baseTemplate("Subscription Cancelled", content),
            plainTextBody = "Hi $username, your subscription has been cancelled. Premium access until: $endDate"
        )
    }
    
    /**
     * Two-factor authentication enabled email
     */
    fun twoFactorEnabledEmail(username: String): EmailContent {
        val content = """
            <h2>Two-Factor Authentication Enabled</h2>
            <p>Hi $username,</p>
            <p>Great news! You've successfully enabled two-factor authentication (2FA) on your $APP_NAME account.</p>
            
            <div class="info">
                <strong>What this means:</strong> You'll now need to enter a verification code from your authenticator app each time you sign in.
            </div>
            
            <h3>Important Security Tips:</h3>
            <ul>
                <li>Keep your authenticator app backed up</li>
                <li>Save your recovery codes in a secure location</li>
                <li>Never share your 2FA codes with anyone</li>
            </ul>
            
            <p>If you didn't enable 2FA, please contact us immediately at <a href="mailto:$SUPPORT_EMAIL">$SUPPORT_EMAIL</a>.</p>
            
            <div style="text-align: center;">
                <a href="$APP_URL/account/security" class="button">View Security Settings</a>
            </div>
        """.trimIndent()
        
        return EmailContent(
            subject = "Two-factor authentication enabled on your $APP_NAME account",
            htmlBody = baseTemplate("2FA Enabled", content),
            plainTextBody = "Hi $username, 2FA has been enabled on your account. Contact support if this wasn't you."
        )
    }
}

/**
 * Email content with both HTML and plain text versions
 */
data class EmailContent(
    val subject: String,
    val htmlBody: String,
    val plainTextBody: String
)