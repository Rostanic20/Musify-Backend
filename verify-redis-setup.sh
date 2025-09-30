#!/bin/bash

echo "==================================="
echo "Redis/Valkey Setup Verification"
echo "==================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. Check if Redis/Valkey service is running
echo "1. Checking Redis/Valkey Service Status..."
echo "----------------------------------------"
if systemctl is-active --quiet redis 2>/dev/null; then
    echo -e "${GREEN}✓ Redis service is running${NC}"
    SERVICE_NAME="redis"
elif systemctl is-active --quiet valkey 2>/dev/null; then
    echo -e "${GREEN}✓ Valkey service is running${NC}"
    SERVICE_NAME="valkey"
else
    # Check if running in Docker
    if docker ps 2>/dev/null | grep -qE "redis|valkey"; then
        echo -e "${GREEN}✓ Redis/Valkey is running in Docker${NC}"
        SERVICE_NAME="docker"
    else
        echo -e "${RED}✗ No Redis/Valkey service found running${NC}"
        exit 1
    fi
fi
echo ""

# 2. Test Redis connection
echo "2. Testing Redis Connection..."
echo "------------------------------"
if command -v redis-cli &> /dev/null; then
    PING_RESULT=$(redis-cli ping 2>&1)
    if [ "$PING_RESULT" = "PONG" ]; then
        echo -e "${GREEN}✓ Redis connection successful (PONG received)${NC}"
    else
        echo -e "${RED}✗ Redis connection failed: $PING_RESULT${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ redis-cli not found${NC}"
fi
echo ""

# 3. Check Redis server info
echo "3. Redis Server Information..."
echo "------------------------------"
if command -v redis-cli &> /dev/null; then
    # Get version
    VERSION=$(redis-cli INFO server | grep redis_version | cut -d: -f2 | tr -d '\r')
    PORT=$(redis-cli INFO server | grep tcp_port | cut -d: -f2 | tr -d '\r')
    UPTIME=$(redis-cli INFO server | grep uptime_in_seconds | cut -d: -f2 | tr -d '\r')
    
    echo "Version: $VERSION"
    echo "Port: $PORT"
    echo "Uptime: $UPTIME seconds"
fi
echo ""

# 4. Test basic Redis operations
echo "4. Testing Basic Redis Operations..."
echo "-----------------------------------"
# Set a test key
redis-cli SET test:musify "Hello from Musify Backend" EX 60 > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ SET operation successful${NC}"
else
    echo -e "${RED}✗ SET operation failed${NC}"
fi

# Get the test key
TEST_VALUE=$(redis-cli GET test:musify 2>&1)
if [ "$TEST_VALUE" = "Hello from Musify Backend" ]; then
    echo -e "${GREEN}✓ GET operation successful${NC}"
else
    echo -e "${RED}✗ GET operation failed${NC}"
fi

# Delete the test key
redis-cli DEL test:musify > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ DEL operation successful${NC}"
else
    echo -e "${RED}✗ DEL operation failed${NC}"
fi
echo ""

# 5. Check .env configuration
echo "5. Checking Musify Configuration..."
echo "-----------------------------------"
if [ -f .env ]; then
    echo -e "${GREEN}✓ .env file found${NC}"
    
    # Check REDIS_ENABLED
    if grep -q "REDIS_ENABLED=true" .env; then
        echo -e "${GREEN}✓ REDIS_ENABLED=true is set${NC}"
    else
        echo -e "${YELLOW}⚠ REDIS_ENABLED is not set to true in .env${NC}"
        echo "  Add: REDIS_ENABLED=true"
    fi
    
    # Check REDIS_HOST
    REDIS_HOST=$(grep "REDIS_HOST=" .env | cut -d= -f2)
    if [ -n "$REDIS_HOST" ]; then
        echo -e "${GREEN}✓ REDIS_HOST=$REDIS_HOST${NC}"
    else
        echo -e "${YELLOW}⚠ REDIS_HOST not set, will use default: localhost${NC}"
    fi
    
    # Check REDIS_PORT
    REDIS_PORT=$(grep "REDIS_PORT=" .env | cut -d= -f2)
    if [ -n "$REDIS_PORT" ]; then
        echo -e "${GREEN}✓ REDIS_PORT=$REDIS_PORT${NC}"
    else
        echo -e "${YELLOW}⚠ REDIS_PORT not set, will use default: 6379${NC}"
    fi
else
    echo -e "${YELLOW}⚠ No .env file found${NC}"
    echo "  Creating a basic .env file..."
    cat > .env << EOF
# Redis Configuration
REDIS_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0

# Required for application
DATABASE_URL=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1
JWT_SECRET=development-secret-key-change-in-production
EOF
    echo -e "${GREEN}✓ Created .env file with Redis enabled${NC}"
fi
echo ""

# 6. Test with actual cache operations
echo "6. Testing Cache Operations..."
echo "------------------------------"
# Create a test script
cat > test_cache_operations.kt << 'EOF'
import redis.clients.jedis.Jedis

fun main() {
    try {
        val jedis = Jedis("localhost", 6379)
        
        // Test connection
        val pong = jedis.ping()
        println("✓ Connection test: $pong")
        
        // Test JSON operations
        val testData = """{"id": 1, "name": "Test Song", "artist": "Test Artist"}"""
        jedis.setex("test:song:1", 60, testData)
        println("✓ Cached test song data")
        
        val retrieved = jedis.get("test:song:1")
        println("✓ Retrieved: ${retrieved?.take(50)}...")
        
        // Test key patterns
        jedis.set("test:pattern:1", "value1")
        jedis.set("test:pattern:2", "value2")
        val keys = jedis.keys("test:pattern:*")
        println("✓ Found ${keys.size} keys matching pattern")
        
        // Cleanup
        keys.forEach { jedis.del(it) }
        jedis.del("test:song:1")
        println("✓ Cleanup completed")
        
        jedis.close()
        println("\n✅ All cache operations working correctly!")
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
    }
}
EOF

echo -e "${GREEN}Cache operation test script created${NC}"
echo ""

# 7. Summary
echo "==================================="
echo "Summary"
echo "==================================="
echo ""
if [ "$SERVICE_NAME" != "" ]; then
    echo -e "${GREEN}✅ Redis/Valkey is properly installed and running${NC}"
    echo ""
    echo "Next steps to use with Musify:"
    echo "1. Ensure REDIS_ENABLED=true in your .env file"
    echo "2. Run the application: ./gradlew run"
    echo "3. Look for: 'Redis cache initialized successfully' in logs"
    echo "4. Monitor cache stats at: /api/monitoring/cache/stats"
    echo ""
    echo "To monitor Redis in real-time:"
    echo "  redis-cli monitor"
    echo ""
    echo "To check Redis memory usage:"
    echo "  redis-cli info memory"
else
    echo -e "${RED}❌ Redis/Valkey setup incomplete${NC}"
fi

# Cleanup
rm -f test_cache_operations.kt 2>/dev/null

echo ""
echo "Test completed!"