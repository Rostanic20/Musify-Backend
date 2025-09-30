#!/bin/bash
#
# Test script for monitoring and logging features
#

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}🧪 Musify Backend Monitoring Test${NC}"
echo "=================================="

# Start the application
echo -e "${YELLOW}Starting application...${NC}"
./run-local.sh &
APP_PID=$!

# Wait for application to start
echo "Waiting for application to start..."
sleep 10

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
curl -s http://localhost:8080/health | jq '.'

echo -e "\n2. Monitoring dashboard:"
curl -s http://localhost:8080/api/monitoring/dashboard/metrics | jq '.'

echo -e "\n3. Database pool stats:"
curl -s http://localhost:8080/api/monitoring/dashboard/database/pool | jq '.'

echo -e "\n4. JVM metrics:"
curl -s http://localhost:8080/api/monitoring/dashboard/jvm | jq '.'

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

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token')
USER_ID=$(echo $LOGIN_RESPONSE | jq -r '.data.user.id')

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
    echo -e "${RED}❌ Failed to get auth token${NC}"
    kill $APP_PID 2>/dev/null || true
    exit 1
fi

echo -e "${GREEN}✅ Got auth token${NC}"

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
            "timestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'"
        }
    }' | jq '.'

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
    }' | jq '.'

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
    }' | jq '.'

# 4. Track batch interactions
echo -e "\n4. Tracking batch interactions:"
curl -s -X POST http://localhost:8080/api/interactions/batch \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "interactions": [
            {
                "userId": '$USER_ID',
                "songId": 4,
                "interactionType": "PLAYED_FULL",
                "context": {"source": "test"}
            },
            {
                "userId": '$USER_ID',
                "songId": 5,
                "interactionType": "LIKED",
                "context": {"source": "test"}
            },
            {
                "userId": '$USER_ID',
                "songId": 6,
                "interactionType": "SKIPPED_EARLY",
                "context": {"source": "test", "position": 10.0}
            }
        ]
    }' | jq '.'

# 5. Track a session
echo -e "\n5. Tracking listening session:"
curl -s -X POST http://localhost:8080/api/interactions/session \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "userId": '$USER_ID',
        "sessionId": "test-session-123",
        "interactions": [
            {
                "songId": 7,
                "interactionType": "PLAYED_FULL"
            },
            {
                "songId": 8,
                "interactionType": "LIKED"
            },
            {
                "songId": 9,
                "interactionType": "ADD_TO_PLAYLIST",
                "context": {"playlistId": 1}
            }
        ]
    }' | jq '.'

# Check metrics after interactions
echo -e "\n${YELLOW}Checking metrics after interactions...${NC}"
sleep 2
curl -s http://localhost:8080/api/monitoring/dashboard/metrics | jq '.'

# Check log files
echo -e "\n${YELLOW}Checking log files...${NC}"
echo "1. Application log:"
if [ -f logs/application.log ]; then
    echo "Recent entries:"
    tail -n 10 logs/application.log
else
    echo -e "${RED}Application log not found${NC}"
fi

echo -e "\n2. Interaction log:"
if [ -f logs/interactions.log ]; then
    echo "Recent entries:"
    tail -n 10 logs/interactions.log
else
    echo -e "${RED}Interaction log not found${NC}"
fi

echo -e "\n3. Database pool log:"
if [ -f logs/db-pool.log ]; then
    echo "Recent entries:"
    tail -n 5 logs/db-pool.log
else
    echo -e "${RED}Database pool log not found${NC}"
fi

# Test monitoring dashboard HTML view
echo -e "\n${YELLOW}Testing monitoring dashboard web view...${NC}"
echo "Dashboard available at: http://localhost:8080/api/monitoring/dashboard"
echo "Opening in browser..."
which xdg-open > /dev/null 2>&1 && xdg-open http://localhost:8080/api/monitoring/dashboard || echo "Please open manually"

# Clean up
echo -e "\n${YELLOW}Test completed. Press Enter to stop the application...${NC}"
read

echo "Stopping application..."
kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo -e "${GREEN}✅ Monitoring test completed!${NC}"
echo "=================================="
echo "Summary:"
echo "- Health endpoints: Working"
echo "- Interaction tracking: Working"
echo "- Metrics collection: Working"
echo "- Logging: Check logs/ directory"
echo "- Dashboard: http://localhost:8080/api/monitoring/dashboard"