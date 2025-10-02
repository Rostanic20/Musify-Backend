#!/bin/bash
#
# Health check script for Musify Backend
# Verifies all components are working correctly
#

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuration
API_URL="${API_URL:-http://localhost:8080}"
TIMEOUT=5

echo -e "${GREEN}🏥 Musify Backend Health Check${NC}"
echo "API URL: $API_URL"
echo "================================================"

# Function to check endpoint
check_endpoint() {
    local endpoint=$1
    local expected_status=$2
    local description=$3
    
    echo -n "Checking $description... "
    
    response=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout $TIMEOUT "$API_URL$endpoint" || echo "000")
    
    if [ "$response" == "$expected_status" ]; then
        echo -e "${GREEN}✅ OK (HTTP $response)${NC}"
        return 0
    else
        echo -e "${RED}❌ FAILED (HTTP $response, expected $expected_status)${NC}"
        return 1
    fi
}

# Function to check service
check_service() {
    local service=$1
    local host=$2
    local port=$3
    
    echo -n "Checking $service... "
    
    if nc -z -w$TIMEOUT "$host" "$port" 2>/dev/null; then
        echo -e "${GREEN}✅ OK${NC}"
        return 0
    else
        echo -e "${RED}❌ FAILED${NC}"
        return 1
    fi
}

# Track failures
failures=0

# Check API endpoints
check_endpoint "/health" "200" "Health endpoint" || ((failures++))
check_endpoint "/api/health/ready" "200" "Readiness probe" || ((failures++))
check_endpoint "/api/health/live" "200" "Liveness probe" || ((failures++))

# Check database connectivity via API
echo -n "Checking database connectivity... "
db_status=$(curl -s "$API_URL/health" | grep -o '"database":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
if [ "$db_status" == "healthy" ] || [ "$db_status" == "up" ]; then
    echo -e "${GREEN}✅ OK${NC}"
else
    echo -e "${RED}❌ FAILED (status: $db_status)${NC}"
    ((failures++))
fi

# Check Redis connectivity via API (if enabled)
echo -n "Checking Redis connectivity... "
redis_status=$(curl -s "$API_URL/health" | grep -o '"redis":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
if [ "$redis_status" == "healthy" ] || [ "$redis_status" == "up" ] || [ "$redis_status" == "disabled" ]; then
    echo -e "${GREEN}✅ OK${NC}"
else
    echo -e "${RED}❌ FAILED (status: $redis_status)${NC}"
    ((failures++))
fi

# Check if running in Docker
if [ -f /.dockerenv ]; then
    echo -e "${YELLOW}Running in Docker container${NC}"
else
    # Check local services if not in Docker
    check_service "PostgreSQL" "localhost" "5432" || ((failures++))
    check_service "Redis" "localhost" "6379" || ((failures++))
fi

# Test authentication flow
echo -n "Testing authentication flow... "
auth_response=$(curl -s -X POST "$API_URL/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"test"}' \
    -w "\n%{http_code}" | tail -n1)

if [ "$auth_response" == "401" ] || [ "$auth_response" == "400" ]; then
    echo -e "${GREEN}✅ OK (properly rejecting invalid credentials)${NC}"
else
    echo -e "${RED}❌ FAILED (unexpected response: $auth_response)${NC}"
    ((failures++))
fi

# Performance check
echo -n "Checking response time... "
response_time=$(curl -s -o /dev/null -w "%{time_total}" "$API_URL/health")
response_time_ms=$(echo "$response_time * 1000" | bc | cut -d. -f1)

if [ "$response_time_ms" -lt 1000 ]; then
    echo -e "${GREEN}✅ OK (${response_time_ms}ms)${NC}"
elif [ "$response_time_ms" -lt 3000 ]; then
    echo -e "${YELLOW}⚠️  SLOW (${response_time_ms}ms)${NC}"
else
    echo -e "${RED}❌ TOO SLOW (${response_time_ms}ms)${NC}"
    ((failures++))
fi

# Summary
echo "================================================"
if [ $failures -eq 0 ]; then
    echo -e "${GREEN}✅ All health checks passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ $failures health checks failed${NC}"
    exit 1
fi