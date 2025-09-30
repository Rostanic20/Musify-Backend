# Monitoring & Alerts Guide

## Overview

The Musify backend includes comprehensive monitoring and alerting capabilities using Micrometer, CloudWatch, and Prometheus.

## Architecture

### Components

1. **MetricsCollector** - Core metrics collection using Micrometer
2. **CloudWatchMetricsPublisher** - Publishes metrics to AWS CloudWatch
3. **AlertManager** - Manages alert rules and notifications
4. **NotificationService** - Sends alerts via Email, Slack, and PagerDuty
5. **MonitoringController** - REST endpoints and dashboard

### Metrics Flow

```
Application Code
    ↓
MetricsCollector (Micrometer)
    ↓
┌─────────────────┬──────────────────┐
│   CloudWatch    │    Prometheus    │
│   Registry      │    Registry      │
└─────────────────┴──────────────────┘
    ↓                     ↓
CloudWatch          Prometheus Scraper
Dashboard           (External)
```

## Available Metrics

### Streaming Metrics
- `musify.streaming.requests` - Total streaming requests (counter)
- `musify.streaming.errors` - Streaming errors by type (counter)
- `musify.streaming.latency` - Request latency (timer)
- `musify.streaming.active_sessions` - Current active sessions (gauge)
- `musify.streaming.requests.by_quality` - Requests grouped by quality

### CDN Metrics
- `musify.cdn.requests` - CDN requests by domain and hit/miss
- `musify.cdn.latency` - CDN response time by domain
- `musify.cdn.cache_hit_rate` - Cache hit percentage

### Payment Metrics
- `musify.payments.transactions` - Payment transactions by type
- `musify.payments.amount` - Transaction amounts by currency
- `musify.payments.success_rate` - Payment success rate

### API Metrics
- `musify.api.latency` - API endpoint latency
- `musify.api.requests` - Requests by endpoint and method

### System Metrics
- `musify.database.query_time` - Database query performance
- `musify.storage.operations` - Storage operation metrics
- `musify.cache.hit_rate` - Application cache performance

## Alert Rules

### Critical Alerts (PagerDuty)
1. **Payment Failure Spike**
   - Threshold: >10% failure rate
   - Duration: 5 minutes
   - Actions: Email, Slack, PagerDuty

2. **Database Connection Pool Exhausted**
   - Threshold: <10% available connections
   - Duration: 2 minutes
   - Actions: Email, Slack, PagerDuty

### High Priority Alerts
1. **High Streaming Error Rate**
   - Threshold: >5% error rate
   - Duration: 5 minutes
   - Actions: Email, Slack

2. **High API Latency**
   - Threshold: p95 >1000ms
   - Duration: 5 minutes
   - Actions: Email, Slack

### Medium Priority Alerts
1. **Low CDN Cache Hit Rate**
   - Threshold: <80%
   - Duration: 10 minutes
   - Actions: Email

2. **Storage Quota Warning**
   - Threshold: >85% usage
   - Duration: 30 minutes
   - Actions: Email

## API Endpoints

### Metrics Endpoints
- `GET /api/monitoring/metrics` - Current metrics snapshot (authenticated)
- `GET /api/monitoring/metrics/prometheus` - Prometheus format (no auth)
- `GET /api/monitoring/alerts` - Active alerts
- `GET /api/monitoring/alerts/history` - Alert history
- `GET /api/monitoring/dashboard` - HTML dashboard

### Example Response
```json
{
  "timestamp": "2025-07-22T15:30:00Z",
  "streaming": {
    "totalRequests": 150000,
    "totalErrors": 500,
    "errorRate": 0.0033,
    "activeSessions": 1200,
    "cacheHitRate": 0.92
  },
  "payments": {
    "totalTransactions": 5000
  },
  "authentication": {
    "totalAttempts": 25000
  }
}
```

## Configuration

### Environment Variables

```bash
# CloudWatch Configuration
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=your-key
AWS_SECRET_ACCESS_KEY=your-secret

# Alert Notifications
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
PAGERDUTY_API_KEY=your-pagerduty-key
ALERT_EMAIL_RECIPIENTS=ops@musify.com,alerts@musify.com

# Monitoring Features
PROMETHEUS_ENABLED=true
MONITORING_ENABLED=true
```

### Alert Customization

Add custom alerts in code:
```kotlin
alertManager.addAlertRule(AlertRule(
    id = "custom_metric_alert",
    name = "Custom Metric Alert",
    description = "Description of condition",
    metric = "custom.metric.name",
    threshold = 100.0,
    operator = ComparisonOperator.GREATER_THAN,
    duration = 5.minutes,
    severity = AlertSeverity.HIGH,
    actions = listOf(AlertAction.EMAIL, AlertAction.SLACK)
))
```

## CloudWatch Dashboard

### Automatic Metrics
The system automatically publishes these metrics to CloudWatch:
- Request counts and error rates
- Latency percentiles (p50, p95, p99)
- Business metrics (revenue, user engagement)
- System performance metrics

### Creating Custom Dashboards
1. Go to CloudWatch Console
2. Create new dashboard
3. Add widgets for `Musify/Backend` namespace
4. Common widgets:
   - Line graph for request rate
   - Number widget for active sessions
   - Gauge for error rate
   - Table for top endpoints by latency

## Prometheus Integration

### Scraping Configuration
Add to your Prometheus config:
```yaml
scrape_configs:
  - job_name: 'musify-backend'
    scrape_interval: 30s
    static_configs:
      - targets: ['musify-backend:8080']
    metrics_path: '/api/monitoring/metrics/prometheus'
```

### Grafana Dashboard
Import the provided Grafana dashboard JSON from `docs/grafana-dashboard.json`

## Operational Procedures

### Responding to Alerts

1. **High Error Rate**
   - Check recent deployments
   - Review error logs in CloudWatch
   - Check circuit breaker status: `/api/health`
   - Scale up if load-related

2. **Payment Failures**
   - Check Stripe dashboard
   - Review webhook logs
   - Verify API keys are valid
   - Check for rate limiting

3. **High Latency**
   - Check database slow query log
   - Review CDN performance
   - Check for blocking operations
   - Consider caching improvements

### Monitoring Checklist

Daily:
- [ ] Review alert history
- [ ] Check error rate trends
- [ ] Monitor active session count
- [ ] Verify payment success rate

Weekly:
- [ ] Review CloudWatch costs
- [ ] Analyze latency trends
- [ ] Check storage usage growth
- [ ] Review top error types

Monthly:
- [ ] Audit alert rules
- [ ] Review notification recipients
- [ ] Test alert channels
- [ ] Optimize metric retention

## Troubleshooting

### Metrics Not Appearing
1. Check CloudWatch credentials
2. Verify IAM permissions include `cloudwatch:PutMetricData`
3. Check application logs for publish errors
4. Ensure `MONITORING_ENABLED=true`

### Alerts Not Firing
1. Verify alert rules match metric names
2. Check notification service configuration
3. Review alert manager logs
4. Test notification channels manually

### High CloudWatch Costs
1. Reduce metric publish frequency
2. Use metric filters
3. Adjust retention policies
4. Consider aggregation before publishing

## Integration Examples

### Recording Custom Metrics
```kotlin
@Inject lateinit var metricsCollector: MetricsCollector

// In your service
metricsCollector.recordUserAction("playlist_created", userId)
metricsCollector.recordApiLatency("/api/playlists", "POST", Duration.ofMillis(45))
```

### Checking Metrics in Code
```kotlin
val snapshot = metricsCollector.getMetricsSnapshot()
if (snapshot.cacheHitRate < 0.5) {
    logger.warn("Cache hit rate is low: ${snapshot.cacheHitRate}")
}
```

## Best Practices

1. **Metric Naming**
   - Use dot notation: `service.component.metric`
   - Be consistent with units
   - Include dimensions for filtering

2. **Alert Design**
   - Set realistic thresholds
   - Include sufficient duration to avoid flapping
   - Start with logging, add notifications gradually

3. **Performance**
   - Batch metric publishes
   - Use sampling for high-volume metrics
   - Monitor the monitoring system itself

4. **Cost Management**
   - Use CloudWatch metric filters
   - Aggregate before publishing
   - Set appropriate retention periods