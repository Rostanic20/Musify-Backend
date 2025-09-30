#!/bin/bash
# Simple script to run Musify Backend locally without Docker

echo "🎵 Starting Musify Backend locally..."

# Check if database is configured
if grep -q "h2:mem" .env; then
    echo "✅ Using in-memory H2 database (development mode)"
else
    echo "⚠️  Make sure PostgreSQL is running if configured"
fi

# Run the application
java -jar build/libs/musify-backend-fat.jar