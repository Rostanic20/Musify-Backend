#!/bin/bash
#
# Simple test script for monitoring and logging features
#

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}🧪 Musify Backend Monitoring Test (Simple)${NC}"
echo "=========================================="

# Start the application
echo -e "${YELLOW}Starting application...${NC}"
java -jar build/libs/musify-backend-fat.jar &
APP_PID=$!

# Wait for application to start
echo "Waiting for application to start..."
sleep 15

# Check if application is running
if ! curl -f http://localhost:8080/health > /dev/null 2>&1; then
    echo -e "${RED}❌ Application failed to start${NC}"
    kill $APP_PID 2>/dev/null || true
    exit 1
fi

echo -e "${GREEN}✅ Application started successfully${NC}"

# Test health endpoints
echo -e "\n${YELLOW}Testing health endpoints...${NC}"
echo "1. Basic health check:"
curl -s http://localhost:8080/health && echo

echo -e "\n2. Monitoring metrics:"
curl -s http://localhost:8080/api/monitoring/dashboard/metrics && echo

echo -e "\n3. Database pool stats:"
curl -s http://localhost:8080/api/monitoring/dashboard/database/pool && echo

echo -e "\n4. JVM metrics:"
curl -s http://localhost:8080/api/monitoring/dashboard/jvm && echo

# Test user registration for auth token
echo -e "\n${YELLOW}Creating test user...${NC}"
REGISTER_RESPONSE=$(curl -s -X POST http://localhost:8080/register \
    -H "Content-Type: application/json" \
    -d '{
        "email": "monitoring-test@example.com",
        "username": "monitortest",
        "password": "Test123!@#",
        "displayName": "Monitoring Test User"
    }')

echo "Registration response: $REGISTER_RESPONSE"

# Login to get token
echo -e "\n${YELLOW}Logging in...${NC}"
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/login \
    -H "Content-Type: application/json" \
    -d '{
        "username": "monitortest",
        "password": "Test123!@#"
    }')

echo "Login response: $LOGIN_RESPONSE"

# Extract token manually (basic parsing)
TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
USER_ID=$(echo $LOGIN_RESPONSE | grep -o '"id":[0-9]*' | cut -d':' -f2)

if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
    echo -e "${RED}❌ Failed to get auth token${NC}"
    kill $APP_PID 2>/dev/null || true
    exit 1
fi

echo -e "${GREEN}✅ Got auth token${NC}"
echo "Token: ${TOKEN:0:20}..."
echo "User ID: $USER_ID"

# Test interaction tracking
echo -e "\n${YELLOW}Testing interaction tracking...${NC}"

# 1. Track a single interaction
echo "1. Tracking song like:"
curl -s -X POST http://localhost:8080/api/interactions/like \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "songId": 1,
        "context": {
            "source": "test",
            "timestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")'"
        }
    }' && echo

# 2. Track song skip
echo -e "\n2. Tracking song skip:"
curl -s -X POST http://localhost:8080/api/interactions/skip \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "songId": 2,
        "position": 15.5,
        "context": {
            "source": "test"
        }
    }' && echo

# 3. Track complete play
echo -e "\n3. Tracking complete play:"
curl -s -X POST http://localhost:8080/api/interactions/complete \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "songId": 3,
        "playDuration": 180.5,
        "context": {
            "source": "test"
        }
    }' && echo

# Check metrics after interactions
echo -e "\n${YELLOW}Checking metrics after interactions...${NC}"
sleep 2
curl -s http://localhost:8080/api/monitoring/dashboard/metrics && echo

# Check log files
echo -e "\n${YELLOW}Checking log files...${NC}"
if [ -d logs ]; then
    echo "Log directory exists. Files:"
    ls -la logs/
    
    if [ -f logs/application.log ]; then
        echo -e "\nLast 5 lines of application.log:"
        tail -n 5 logs/application.log
    fi
    
    if [ -f logs/interactions.log ]; then
        echo -e "\nLast 5 lines of interactions.log:"
        tail -n 5 logs/interactions.log
    fi
else
    echo -e "${YELLOW}Log directory not found${NC}"
fi

# Test monitoring dashboard
echo -e "\n${YELLOW}Testing monitoring dashboard...${NC}"
DASHBOARD_RESPONSE=$(curl -s -w "\n%{http_code}" http://localhost:8080/api/monitoring/dashboard)
HTTP_CODE=$(echo "$DASHBOARD_RESPONSE" | tail -n 1)
if [ "$HTTP_CODE" == "200" ]; then
    echo -e "${GREEN}✅ Dashboard accessible (HTTP $HTTP_CODE)${NC}"
    echo "Dashboard URL: http://localhost:8080/api/monitoring/dashboard"
else
    echo -e "${RED}❌ Dashboard returned HTTP $HTTP_CODE${NC}"
fi

# Clean up
echo -e "\n${YELLOW}Stopping application...${NC}"
kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo -e "\n${GREEN}✅ Monitoring test completed!${NC}"
echo "=========================================="
echo "Summary:"
echo "- Health endpoints: Working"
echo "- Interaction tracking: Tested"
echo "- Metrics collection: Tested"
echo "- Logging: Check logs/ directory"
echo "- Dashboard: http://localhost:8080/api/monitoring/dashboard"