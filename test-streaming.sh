#!/bin/bash

echo "🧪 Testing Musify Streaming"
echo "========================="

# Test 1: Check if server is running
echo "1️⃣ Checking server..."
if curl -s http://localhost:8080 > /dev/null; then
    echo "✅ Server is running"
else
    echo "❌ Server is not running. Start it with: ./gradlew run"
    exit 1
fi

# Test 2: Test streaming endpoint
echo "2️⃣ Testing streaming endpoint..."
# You'll need to add a valid auth token here
AUTH_TOKEN="your-auth-token"
curl -H "Authorization: Bearer $AUTH_TOKEN" \
     http://localhost:8080/api/songs/stream/1/url

echo ""
echo "✅ Tests complete!"
