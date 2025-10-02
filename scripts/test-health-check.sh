#!/bin/bash

# Test Health Check Script
# Run this daily to ensure tests remain stable

set -e

echo "======================================"
echo "Running Musify Backend Test Health Check"
echo "Date: $(date)"
echo "======================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test results file
RESULTS_FILE="test-health-results-$(date +%Y%m%d-%H%M%S).txt"

# Function to run tests and check results
run_test_suite() {
    local test_name=$1
    local test_command=$2
    
    echo -e "\n${YELLOW}Running $test_name...${NC}"
    
    if $test_command > "$RESULTS_FILE" 2>&1; then
        echo -e "${GREEN}✓ $test_name PASSED${NC}"
        return 0
    else
        echo -e "${RED}✗ $test_name FAILED${NC}"
        echo "Check $RESULTS_FILE for details"
        return 1
    fi
}

# Clean build
echo "Cleaning build..."
./gradlew clean

# Run different test configurations
FAILED_TESTS=0

# 1. Run all tests sequentially
if ! run_test_suite "All Tests (Sequential)" "./gradlew test"; then
    ((FAILED_TESTS++))
fi

# 2. Run tests by category
if ! run_test_suite "Unit Tests Only" "./gradlew test --tests '*Test' -x integrationTest"; then
    ((FAILED_TESTS++))
fi

# 3. Run specific problematic test classes individually
echo -e "\n${YELLOW}Running potentially flaky tests individually...${NC}"

FLAKY_TESTS=(
    "com.musify.presentation.controller.SearchControllerTest"
    "com.musify.presentation.controller.SearchPerformanceControllerV2"
)

for test_class in "${FLAKY_TESTS[@]}"; do
    if ! run_test_suite "$test_class" "./gradlew test --tests '$test_class'"; then
        ((FAILED_TESTS++))
    fi
done

# 4. Run tests multiple times to check for flakiness
echo -e "\n${YELLOW}Running flakiness check (3 iterations)...${NC}"
FLAKY_COUNT=0

for i in {1..3}; do
    echo "Iteration $i/3..."
    if ! ./gradlew test --quiet > /dev/null 2>&1; then
        ((FLAKY_COUNT++))
    fi
done

if [ $FLAKY_COUNT -gt 0 ]; then
    echo -e "${RED}✗ Tests failed $FLAKY_COUNT/3 times - FLAKY TESTS DETECTED${NC}"
    ((FAILED_TESTS++))
else
    echo -e "${GREEN}✓ All iterations passed - Tests appear stable${NC}"
fi

# Summary
echo -e "\n======================================"
echo "Test Health Check Summary"
echo "======================================"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✓ All test suites passed successfully!${NC}"
    echo "Tests are healthy and stable."
else
    echo -e "${RED}✗ $FAILED_TESTS test suite(s) failed${NC}"
    echo "Action required: Review failing tests and fix issues."
    
    # Generate report
    echo -e "\nGenerating detailed report..."
    cat > "test-health-report-$(date +%Y%m%d).md" << EOF
# Test Health Report - $(date)

## Summary
- Total test suites failed: $FAILED_TESTS
- Flakiness detected: $([ $FLAKY_COUNT -gt 0 ] && echo "Yes" || echo "No")

## Recommendations
1. Review test isolation in failing tests
2. Check for shared state between tests
3. Verify database cleanup is working properly
4. Consider adding retry logic for external dependencies

## Failed Tests
$(grep -A 5 "FAILED" "$RESULTS_FILE" || echo "See $RESULTS_FILE for details")
EOF
    
    echo "Report saved to test-health-report-$(date +%Y%m%d).md"
fi

# Cleanup old results files (keep last 7 days)
find . -name "test-health-results-*.txt" -mtime +7 -delete
find . -name "test-health-report-*.md" -mtime +30 -delete

exit $FAILED_TESTS