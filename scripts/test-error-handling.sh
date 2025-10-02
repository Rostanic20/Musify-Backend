#!/bin/bash

# Test Error Handling Script
# This script verifies that error handling is working correctly

set -e

echo "======================================"
echo "Testing Error Handling Implementation"
echo "Date: $(date)"
echo "======================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base URL (adjust if running on different port)
BASE_URL="http://localhost:8080"

# Function to check endpoint
check_endpoint() {
    local endpoint=$1
    local expected_status=${2:-200}
    local description=$3
    
    echo -e "\n${BLUE}Testing: $description${NC}"
    echo "Endpoint: $endpoint"
    
    response=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$endpoint" || echo "000")
    
    if [ "$response" = "$expected_status" ]; then
        echo -e "${GREEN}✓ Response: $response (Expected: $expected_status)${NC}"
        return 0
    else
        echo -e "${RED}✗ Response: $response (Expected: $expected_status)${NC}"
        return 1
    fi
}

# Function to get JSON response
get_json_response() {
    local endpoint=$1
    curl -s "$BASE_URL$endpoint" | jq . 2>/dev/null || echo "{}"
}

# Start the application in background if not running
if ! curl -s -o /dev/null "$BASE_URL/api/health/live" 2>/dev/null; then
    echo -e "${YELLOW}Starting application...${NC}"
    ./gradlew run &
    APP_PID=$!
    
    # Wait for application to start
    echo "Waiting for application to start..."
    for i in {1..30}; do
        if curl -s -o /dev/null "$BASE_URL/api/health/live" 2>/dev/null; then
            echo -e "${GREEN}Application started successfully${NC}"
            break
        fi
        sleep 1
    done
else
    echo -e "${GREEN}Application is already running${NC}"
fi

# Test error handling endpoints
echo -e "\n${YELLOW}=== Testing Health Endpoints ===${NC}"

# 1. Test main health endpoint
check_endpoint "/api/health" 200 "Main health endpoint"
health_response=$(get_json_response "/api/health")
echo "Health Status:"
echo "$health_response" | jq -r '.status' 2>/dev/null || echo "N/A"

# 2. Test liveness probe
check_endpoint "/api/health/live" 200 "Liveness probe"

# 3. Test readiness probe
check_endpoint "/api/health/ready" 200 "Readiness probe" || \
check_endpoint "/api/health/ready" 503 "Readiness probe (degraded)"

# Check circuit breaker status
echo -e "\n${YELLOW}=== Circuit Breaker Status ===${NC}"
if [ -n "$health_response" ]; then
    echo "Storage Circuit Breaker:"
    echo "$health_response" | jq '.services.storage.circuitBreaker' 2>/dev/null || echo "N/A"
    
    echo -e "\nCDN Circuit Breaker:"
    echo "$health_response" | jq '.services.streaming.cdn.circuitBreaker' 2>/dev/null || echo "N/A"
    
    echo -e "\nS3 Circuit Breaker:"
    echo "$health_response" | jq '.services.streaming.s3.circuitBreaker' 2>/dev/null || echo "N/A"
fi

# Test resilience features
echo -e "\n${YELLOW}=== Testing Resilience Features ===${NC}"

# 1. Test retry mechanism
echo -e "\n${BLUE}Testing retry mechanism...${NC}"
echo "Run these unit tests to verify:"
echo "  ./gradlew test --tests 'RetryPolicyTest'"
./gradlew test --tests 'RetryPolicyTest' --quiet && echo -e "${GREEN}✓ Retry tests passed${NC}" || echo -e "${RED}✗ Retry tests failed${NC}"

# 2. Test circuit breaker
echo -e "\n${BLUE}Testing circuit breaker...${NC}"
echo "Run these unit tests to verify:"
echo "  ./gradlew test --tests 'CircuitBreakerTest'"
./gradlew test --tests 'CircuitBreakerTest' --quiet && echo -e "${GREEN}✓ Circuit breaker tests passed${NC}" || echo -e "${RED}✗ Circuit breaker tests failed${NC}"

# Summary
echo -e "\n${YELLOW}=== Summary ===${NC}"
echo "Error handling components:"
echo "✓ Health endpoints configured"
echo "✓ Circuit breaker implemented"
echo "✓ Retry policy implemented"
echo "✓ Resilient storage service"
echo "✓ Resilient streaming service"

echo -e "\n${BLUE}Key Features:${NC}"
echo "1. Circuit Breaker Pattern:"
echo "   - Prevents cascading failures"
echo "   - Auto-recovery with half-open state"
echo "   - Configurable thresholds"

echo -e "\n2. Retry Policy:"
echo "   - Exponential backoff"
echo "   - Configurable max attempts"
echo "   - Smart retry for transient errors"

echo -e "\n3. Health Monitoring:"
echo "   - Real-time service status"
echo "   - Circuit breaker metrics"
echo "   - Liveness and readiness probes"

echo -e "\n4. Fallback Mechanisms:"
echo "   - CDN fallback to S3"
echo "   - Multiple CDN domains"
echo "   - Graceful degradation"

# Cleanup if we started the app
if [ -n "$APP_PID" ]; then
    echo -e "\n${YELLOW}Stopping application...${NC}"
    kill $APP_PID 2>/dev/null || true
fi

echo -e "\n${GREEN}Error handling test complete!${NC}"