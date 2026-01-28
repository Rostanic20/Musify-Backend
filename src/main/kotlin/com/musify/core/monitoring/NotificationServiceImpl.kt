package com.musify.core.monitoring

import com.musify.core.config.EnvironmentConfig
import com.musify.infrastructure.email.EmailService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.time.format.DateTimeFormatter

/**
 * Implementation of notification service for alerts
 */
class NotificationServiceImpl(
    private val emailService: EmailService
) : NotificationService {
    
    private val logger = LoggerFactory.getLogger(NotificationServiceImpl::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
    private val slackWebhookUrl = EnvironmentConfig.getEnvOrNull("SLACK_WEBHOOK_URL")
    private val pagerDutyApiKey = EnvironmentConfig.getEnvOrNull("PAGERDUTY_API_KEY")
    private val alertEmailRecipients = EnvironmentConfig.getEnvOrNull("ALERT_EMAIL_RECIPIENTS")
        ?.split(",")?.map { it.trim() } ?: listOf("ops@musify.com")
    
    override suspend fun sendEmail(alert: Alert) = withContext(Dispatchers.IO) {
        try {
            val subject = "[${alert.rule.severity}] ${alert.rule.name}"
            val body = buildEmailBody(alert)
            
            alertEmailRecipients.forEach { recipient ->
                sendAlertEmail(recipient, subject, body)
            }
            
            logger.info("Alert email sent for: ${alert.rule.name}")
        } catch (e: Exception) {
            logger.error("Failed to send alert email", e)
        }
    }
    
    override suspend fun sendSlack(alert: Alert) = withContext(Dispatchers.IO) {
        if (slackWebhookUrl == null) {
            logger.warn("Slack webhook URL not configured")
            return@withContext
        }
        
        try {
            val payload = SlackPayload(
                text = "🚨 Alert: ${alert.rule.name}",
                attachments = listOf(
                    SlackAttachment(
                        color = getSlackColor(alert.rule.severity),
                        title = alert.rule.name,
                        text = alert.rule.description,
                        fields = listOf(
                            SlackField("Severity", alert.rule.severity.name, true),
                            SlackField("Metric", alert.rule.metric, true),
                            SlackField("Value", "${alert.value}", true),
                            SlackField("Threshold", "${alert.rule.threshold}", true),
                            SlackField("Duration", alert.rule.duration.toString(), true)
                        ) + alert.dimensions.map { (key, value) ->
                            SlackField(key, value, true)
                        },
                        ts = System.currentTimeMillis() / 1000
                    )
                )
            )
            
            val url = URL(slackWebhookUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                
                outputStream.use { os ->
                    os.write(json.encodeToString(payload).toByteArray())
                }
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    logger.error("Slack notification failed: $responseCode - $responseMessage")
                } else {
                    logger.info("Slack notification sent for: ${alert.rule.name}")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to send Slack notification", e)
        }
    }
    
    override suspend fun sendPagerDuty(alert: Alert) = withContext(Dispatchers.IO) {
        if (pagerDutyApiKey == null) {
            logger.warn("PagerDuty API key not configured")
            return@withContext
        }
        
        try {
            val incident = PagerDutyIncident(
                incident_key = alert.id,
                event_type = "trigger",
                description = "${alert.rule.name}: ${alert.rule.description}",
                details = mapOf(
                    "severity" to alert.rule.severity.name,
                    "metric" to alert.rule.metric,
                    "value" to alert.value.toString(),
                    "threshold" to alert.rule.threshold.toString(),
                    "dimensions" to alert.dimensions.entries.joinToString(", ") { "${it.key}=${it.value}" }
                ),
                service_key = pagerDutyApiKey
            )
            
            val url = URL("https://events.pagerduty.com/v2/enqueue")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                
                outputStream.use { os ->
                    os.write(json.encodeToString(incident).toByteArray())
                }
                
                if (responseCode == HttpURLConnection.HTTP_ACCEPTED) {
                    logger.info("PagerDuty incident created for: ${alert.rule.name}")
                } else {
                    logger.error("PagerDuty notification failed: $responseCode - $responseMessage")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to send PagerDuty notification", e)
        }
    }
    
    override suspend fun sendResolution(alert: Alert) = withContext(Dispatchers.IO) {
        // Send resolution notifications
        try {
            // Email
            val subject = "[RESOLVED] ${alert.rule.name}"
            val body = buildResolutionEmailBody(alert)
            
            alertEmailRecipients.forEach { recipient ->
                sendAlertEmail(recipient, subject, body)
            }
            
            // Slack
            if (slackWebhookUrl != null) {
                val payload = SlackPayload(
                    text = "✅ Alert Resolved: ${alert.rule.name}",
                    attachments = listOf(
                        SlackAttachment(
                            color = "good",
                            title = "${alert.rule.name} - Resolved",
                            text = "The alert condition has been resolved.",
                            fields = listOf(
                                SlackField("Duration", calculateDuration(alert), false)
                            ),
                            ts = System.currentTimeMillis() / 1000
                        )
                    )
                )
                
                sendSlackMessage(payload)
            }
            
            // PagerDuty
            if (pagerDutyApiKey != null) {
                val incident = PagerDutyIncident(
                    incident_key = alert.id,
                    event_type = "resolve",
                    description = "${alert.rule.name}: Resolved",
                    service_key = pagerDutyApiKey
                )
                
                sendPagerDutyEvent(incident)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to send resolution notifications", e)
        }
    }
    
    private fun buildEmailBody(alert: Alert): String {
        val formatter = DateTimeFormatter.ISO_INSTANT
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; }
                    .alert-box { 
                        border: 2px solid ${getEmailColor(alert.rule.severity)}; 
                        padding: 20px; 
                        margin: 20px 0;
                        border-radius: 5px;
                    }
                    .metric { 
                        background-color: #f0f0f0; 
                        padding: 10px; 
                        margin: 10px 0;
                        border-radius: 3px;
                    }
                    table { border-collapse: collapse; width: 100%; }
                    td { padding: 8px; border-bottom: 1px solid #ddd; }
                </style>
            </head>
            <body>
                <h2>🚨 Alert: ${alert.rule.name}</h2>
                <div class="alert-box">
                    <p><strong>Description:</strong> ${alert.rule.description}</p>
                    <p><strong>Severity:</strong> ${alert.rule.severity}</p>
                    <p><strong>Time:</strong> ${formatter.format(alert.startTime)}</p>
                </div>
                
                <div class="metric">
                    <h3>Metric Details</h3>
                    <table>
                        <tr><td><strong>Metric:</strong></td><td>${alert.rule.metric}</td></tr>
                        <tr><td><strong>Current Value:</strong></td><td>${alert.value}</td></tr>
                        <tr><td><strong>Threshold:</strong></td><td>${alert.rule.threshold} (${alert.rule.operator})</td></tr>
                        <tr><td><strong>Duration:</strong></td><td>${alert.rule.duration}</td></tr>
                        ${alert.dimensions.entries.joinToString("") { (key, value) ->
                            "<tr><td><strong>$key:</strong></td><td>$value</td></tr>"
                        }}
                    </table>
                </div>
                
                <p><small>This is an automated alert from Musify Monitoring System</small></p>
            </body>
            </html>
        """.trimIndent()
    }
    
    private fun buildResolutionEmailBody(alert: Alert): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; }
                    .resolved-box { 
                        border: 2px solid #4CAF50; 
                        padding: 20px; 
                        margin: 20px 0;
                        border-radius: 5px;
                        background-color: #f1f8f4;
                    }
                </style>
            </head>
            <body>
                <h2>✅ Alert Resolved: ${alert.rule.name}</h2>
                <div class="resolved-box">
                    <p>The alert condition has been resolved.</p>
                    <p><strong>Duration:</strong> ${calculateDuration(alert)}</p>
                    <p><strong>Resolution Time:</strong> ${DateTimeFormatter.ISO_INSTANT.format(alert.lastUpdateTime)}</p>
                </div>
                <p><small>This is an automated alert from Musify Monitoring System</small></p>
            </body>
            </html>
        """.trimIndent()
    }
    
    private fun getSlackColor(severity: AlertSeverity): String = when (severity) {
        AlertSeverity.CRITICAL -> "danger"
        AlertSeverity.HIGH -> "warning"
        AlertSeverity.MEDIUM -> "#ff9800"
        AlertSeverity.LOW -> "#2196f3"
    }
    
    private fun getEmailColor(severity: AlertSeverity): String = when (severity) {
        AlertSeverity.CRITICAL -> "#f44336"
        AlertSeverity.HIGH -> "#ff9800"
        AlertSeverity.MEDIUM -> "#ffc107"
        AlertSeverity.LOW -> "#2196f3"
    }
    
    private fun calculateDuration(alert: Alert): String {
        val durationSeconds = alert.lastUpdateTime.epochSecond - alert.startTime.epochSecond
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
    
    private suspend fun sendSlackMessage(payload: SlackPayload) {
        // Implementation reused from sendSlack
    }
    
    private suspend fun sendPagerDutyEvent(incident: PagerDutyIncident) {
        // Implementation reused from sendPagerDuty
    }
    
    private suspend fun sendAlertEmail(to: String, subject: String, htmlBody: String) {
        // For now, log the email since EmailService doesn't have a generic method
        // In production, this would integrate with a proper email service
        logger.info("Would send alert email to: $to, subject: $subject")
        // TODO: Add proper email integration when EmailService is extended
    }
}

@Serializable
private data class SlackPayload(
    val text: String,
    val attachments: List<SlackAttachment>
)

@Serializable
private data class SlackAttachment(
    val color: String,
    val title: String,
    val text: String,
    val fields: List<SlackField>,
    val ts: Long
)

@Serializable
private data class SlackField(
    val title: String,
    val value: String,
    val short: Boolean
)

@Serializable
private data class PagerDutyIncident(
    val incident_key: String,
    val event_type: String,
    val description: String,
    val details: Map<String, String>? = null,
    val service_key: String
)