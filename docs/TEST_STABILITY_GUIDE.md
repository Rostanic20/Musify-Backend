# Test Stability Guide

## Overview
This guide documents the measures taken to ensure test stability and prevent flaky tests in the Musify Backend project.

## Test Isolation Strategies

### 1. Database Isolation
Each test class gets its own unique H2 in-memory database instance:
- Database name includes test class name and UUID
- Databases are created fresh for each test class
- No shared state between test classes

### 2. Dependency Injection Isolation
- Koin context is stopped and restarted between tests
- Each test gets a fresh dependency injection container
- No shared singletons between tests

### 3. System Properties Isolation
Test-specific system properties are set and restored:
- `DATABASE_URL` - Unique per test class
- `JWT_SECRET` - Test-specific secret
- `JWT_ISSUER` / `JWT_AUDIENCE` - Consistent test values
- Feature flags disabled (`REDIS_ENABLED`, `SENTRY_ENABLED`, etc.)

## Configuration Files

### junit-platform.properties
Located in `src/test/resources/`:
- Disables parallel test execution
- Sets consistent test ordering
- Configures test timeouts (30s default)
- Uses per-method test instances

### gradle.properties
Test-specific Gradle settings:
- `systemProp.junit.jupiter.execution.parallel.enabled=false`
- Sequential test execution enforced

## Robust Test Base Classes

### RobustIntegrationTest
Use this base class for integration tests:
```kotlin
class MyControllerTest : RobustIntegrationTest() {
    @Test
    fun `test something`() = testApplication {
        // Your test code
    }
}
```

Features:
- Automatic database isolation
- Koin cleanup
- System property management
- JUnit 5 extensions for lifecycle management

## Daily Health Checks

### Automated Script
Run `./scripts/test-health-check.sh` to:
- Execute all tests sequentially
- Run potentially flaky tests individually
- Perform multiple iterations to detect flakiness
- Generate health reports

### GitHub Actions
The `.github/workflows/test-stability.yml` workflow:
- Runs tests daily at 2 AM UTC
- Tests on multiple Java versions (17, 21)
- Retries failed tests up to 3 times
- Uploads test reports as artifacts
- Comments on PRs if tests fail

## Best Practices

### 1. Always Use Test Utilities
```kotlin
// Good - uses BaseIntegrationTest
class MyTest : BaseIntegrationTest() {
    @Test
    fun test() = testApplication {
        application { testModule() }
        // test code
    }
}

// Better - uses RobustIntegrationTest
class MyTest : RobustIntegrationTest() {
    @Test
    fun test() = testApplication {
        application { testModule() }
        // test code
    }
}
```

### 2. Avoid Shared State
```kotlin
// Bad - shared state
companion object {
    val sharedData = mutableListOf<String>()
}

// Good - test-local state
@Test
fun test() {
    val testData = mutableListOf<String>()
    // use testData
}
```

### 3. Clean Up Resources
```kotlin
@Test
fun test() = testApplication {
    application { testModule() }
    
    try {
        // test code
    } finally {
        // cleanup if needed
    }
}
```

### 4. Use Unique Test Data
```kotlin
// Bad - hardcoded IDs
val userId = 1
val email = "test@example.com"

// Good - unique per test
val userId = Random.nextInt()
val email = "test${UUID.randomUUID()}@example.com"
```

## Troubleshooting Flaky Tests

### 1. Identify Flaky Tests
```bash
# Run tests multiple times
for i in {1..10}; do
    ./gradlew test --tests "YourTestClass"
done
```

### 2. Common Causes
- **Shared database state**: Ensure unique database per test
- **Port conflicts**: Use random ports for test servers
- **Timing issues**: Add proper waits/timeouts
- **External dependencies**: Mock external services
- **Race conditions**: Avoid parallel execution

### 3. Debugging Steps
1. Run test in isolation: `./gradlew test --tests "specific.test.name"`
2. Check test logs: `build/reports/tests/test/index.html`
3. Enable debug logging: Add `--debug` to Gradle command
4. Review test order dependencies

## Monitoring Test Health

### Metrics to Track
- Test success rate over time
- Flaky test frequency
- Test execution time trends
- Failed test patterns

### Regular Maintenance
1. **Weekly**: Review test health reports
2. **Monthly**: Update flaky test list
3. **Quarterly**: Refactor problematic tests

## Emergency Fixes

If tests suddenly start failing:

1. **Check recent changes**:
   ```bash
   git log --oneline -10
   git diff HEAD~1
   ```

2. **Revert to known good state**:
   ```bash
   git checkout <last-known-good-commit>
   ./gradlew test
   ```

3. **Clear all caches**:
   ```bash
   ./gradlew clean
   rm -rf ~/.gradle/caches/
   ./gradlew test --no-build-cache
   ```

4. **Run with fresh environment**:
   ```bash
   docker run -it --rm openjdk:17 bash
   # Inside container: clone and test
   ```

## Contact

For persistent test issues:
1. Check existing issues: https://github.com/musify/backend/issues
2. Create new issue with test logs
3. Tag with `test-flaky` label