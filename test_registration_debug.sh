#!/bin/bash

# Test registration endpoint with debugging

BASE_URL="http://localhost:8080/api/auth"

# Generate unique test data
TIMESTAMP=$(date +%s)
UNIQUE_EMAIL="testuser_${TIMESTAMP}@example.com"
UNIQUE_USERNAME="testuser_${TIMESTAMP}"

echo "Testing registration with:"
echo "Email: $UNIQUE_EMAIL"
echo "Username: $UNIQUE_USERNAME"
echo ""

# First, let's check if the server is running
echo "Checking server health..."
curl -s http://localhost:8080/health | jq . || echo "Server not responding"
echo ""

# Test registration
echo "Testing registration..."
RESPONSE=$(curl -s -X POST "$BASE_URL/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$UNIQUE_EMAIL\",
    \"username\": \"$UNIQUE_USERNAME\",
    \"password\": \"Test1234!\",
    \"displayName\": \"Test User $TIMESTAMP\"
  }" -w "\nHTTP_STATUS:%{http_code}")

# Extract HTTP status and response body
HTTP_STATUS=$(echo "$RESPONSE" | grep -o "HTTP_STATUS:[0-9]*" | cut -d: -f2)
BODY=$(echo "$RESPONSE" | sed 's/HTTP_STATUS:[0-9]*$//')

echo "HTTP Status: $HTTP_STATUS"
echo "Response Body:"
echo "$BODY" | jq . || echo "$BODY"
echo ""

# If registration fails with conflict, let's try to query the database directly
if [ "$HTTP_STATUS" = "409" ]; then
    echo "Registration failed with conflict. Checking database..."
    
    # Check if PostgreSQL is running
    if command -v psql &> /dev/null; then
        echo "Checking if email exists in database..."
        psql -U musify -d musify_db -c "SELECT COUNT(*) as count FROM users WHERE email = '$UNIQUE_EMAIL';" 2>/dev/null || echo "Could not query database"
        
        echo "Checking if username exists in database..."
        psql -U musify -d musify_db -c "SELECT COUNT(*) as count FROM users WHERE username = '$UNIQUE_USERNAME';" 2>/dev/null || echo "Could not query database"
        
        echo "Total users in database:"
        psql -U musify -d musify_db -c "SELECT COUNT(*) as count FROM users;" 2>/dev/null || echo "Could not query database"
    fi
fi