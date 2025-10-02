#!/bin/bash

# Setup local cron job for daily test health checks

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "Setting up daily test health check cron job..."

# Create cron entry
CRON_CMD="0 2 * * * cd $PROJECT_ROOT && ./scripts/test-health-check.sh >> $PROJECT_ROOT/logs/test-health.log 2>&1"

# Check if cron job already exists
if crontab -l 2>/dev/null | grep -q "test-health-check.sh"; then
    echo "Cron job already exists. Updating..."
    # Remove existing entry
    crontab -l | grep -v "test-health-check.sh" | crontab -
fi

# Add new cron job
(crontab -l 2>/dev/null; echo "$CRON_CMD") | crontab -

# Create logs directory if it doesn't exist
mkdir -p "$PROJECT_ROOT/logs"

echo "Cron job setup complete!"
echo "Tests will run daily at 2 AM local time."
echo "Logs will be saved to: $PROJECT_ROOT/logs/test-health.log"
echo ""
echo "To view current cron jobs: crontab -l"
echo "To remove this cron job: crontab -l | grep -v 'test-health-check.sh' | crontab -"