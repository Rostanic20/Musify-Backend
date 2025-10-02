#!/bin/bash

echo "🧪 Running Musify Backend Tests..."
echo ""

# Set test environment
export DATABASE_URL="jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL"
export DATABASE_DRIVER="org.h2.Driver"
export JWT_SECRET="test-secret-key"

# Run tests with different options
case "$1" in
    "unit")
        echo "Running unit tests only..."
        ./gradlew test --tests "*UseCase*" --info
        ;;
    "integration")
        echo "Running integration tests only..."
        ./gradlew test --tests "*Controller*" --tests "*Repository*" --info
        ;;
    "coverage")
        echo "Running all tests with coverage..."
        ./gradlew test jacocoTestReport
        echo "Coverage report available at: build/reports/jacoco/test/html/index.html"
        ;;
    "watch")
        echo "Running tests in watch mode..."
        ./gradlew test --continuous
        ;;
    *)
        echo "Running all tests..."
        ./gradlew test
        ;;
esac

# Show test results
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ All tests passed!"
    echo ""
    echo "Test reports available at:"
    echo "- HTML: build/reports/tests/test/index.html"
    echo "- XML: build/test-results/test/"
else
    echo ""
    echo "❌ Some tests failed!"
    echo "Check the test report for details: build/reports/tests/test/index.html"
fi