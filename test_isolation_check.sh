#!/bin/bash

echo "Testing for test isolation issues..."

# Run tests individually
echo "1. Running SearchControllerTest individually..."
./gradlew test --tests "SearchControllerTest" > individual_test1.log 2>&1
RESULT1=$?
echo "Result: $RESULT1"

echo "2. Running SearchPerformanceControllerTest individually..."
./gradlew test --tests "SearchPerformanceControllerTest" > individual_test2.log 2>&1
RESULT2=$?
echo "Result: $RESULT2"

echo "3. Running both tests together..."
./gradlew test --tests "SearchControllerTest" --tests "SearchPerformanceControllerTest" > combined_test.log 2>&1
RESULT3=$?
echo "Result: $RESULT3"

echo "4. Running all tests..."
./gradlew test > all_tests.log 2>&1
RESULT4=$?
echo "Result: $RESULT4"

echo ""
echo "Summary:"
echo "SearchControllerTest alone: $RESULT1"
echo "SearchPerformanceControllerTest alone: $RESULT2"
echo "Both tests together: $RESULT3"
echo "All tests: $RESULT4"

# Check for failures
if [ $RESULT1 -ne 0 ]; then
    echo "SearchControllerTest failures:"
    grep -A5 "FAILED" individual_test1.log
fi

if [ $RESULT2 -ne 0 ]; then
    echo "SearchPerformanceControllerTest failures:"
    grep -A5 "FAILED" individual_test2.log
fi

if [ $RESULT3 -ne 0 ]; then
    echo "Combined test failures:"
    grep -A5 "FAILED" combined_test.log
fi

if [ $RESULT4 -ne 0 ]; then
    echo "All test failures:"
    grep -A5 "FAILED" all_tests.log | head -20
fi