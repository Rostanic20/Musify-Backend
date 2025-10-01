# Contributing to Musify Backend

First off, thank you for considering contributing to Musify Backend! It's people like you that make this project better.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Process](#development-process)
- [Style Guidelines](#style-guidelines)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Community](#community)

## Code of Conduct

This project and everyone participating in it is governed by the [Musify Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## Getting Started

1. Fork the repository on GitHub
2. Clone your fork locally
   ```bash
   git clone https://github.com/your-username/Musify-Backend.git
   cd Musify-Backend
   ```
3. Add the upstream repository as a remote
   ```bash
   git remote add upstream https://github.com/Rostanic20/Musify-Backend.git
   ```
4. Create a branch for your changes
   ```bash
   git checkout -b feature/your-feature-name
   ```

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, please include:

- A clear and descriptive title
- Exact steps to reproduce the problem
- Expected behavior vs actual behavior
- Code samples or test cases that demonstrate the issue
- Your environment details (OS, Java version, etc.)

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md) when creating issues.

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

- A clear and descriptive title
- A detailed description of the proposed functionality
- Explain why this enhancement would be useful
- List any alternative solutions you've considered

Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.md) when creating feature requests.

### Your First Code Contribution

Unsure where to begin? Look for issues labeled:

- `good first issue` - Good for newcomers
- `help wanted` - Extra attention needed
- `documentation` - Documentation improvements

### Pull Requests

1. Ensure your code follows the project's code style
2. Update documentation as needed
3. Add tests for new functionality
4. Ensure all tests pass locally
5. Update the README.md if needed
6. Fill out the pull request template completely

## Development Process

### Setting Up Development Environment

1. Install required dependencies:
   - JDK 17+
   - PostgreSQL 14+
   - Redis 7+

2. Set up environment:
   ```bash
   cp .env.example .env
   cp setup-database.sql.example setup-database.sql
   # Edit files with your configuration
   ```

3. Run database setup:
   ```bash
   psql -U postgres < setup-database.sql
   ./gradlew flywayMigrate
   ```

4. Run tests:
   ```bash
   ./gradlew test
   ```

### Project Structure

```
src/main/kotlin/com/musify/
├── domain/         # Business logic
├── data/          # Data layer
├── presentation/  # API layer
├── infrastructure/# External services
└── di/           # Dependency injection
```

### Testing

- Write unit tests for all business logic
- Write integration tests for API endpoints
- Maintain test coverage above 70%
- Use descriptive test names

Example test:
```kotlin
@Test
fun `should return 401 when accessing protected endpoint without token`() {
    // Test implementation
}
```

## Style Guidelines

### Kotlin Style

We follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) with these additions:

- Use meaningful variable and function names
- Keep functions small and focused
- Use data classes for DTOs
- Prefer immutability
- Use coroutines for async operations

### Code Formatting

- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Add blank lines between class members
- Group related functionality together

Run Ktlint to check formatting:
```bash
./gradlew ktlintCheck
```

### Documentation

- Add KDoc comments to all public APIs
- Document complex algorithms
- Keep comments up-to-date with code changes
- Write clear commit messages

## Commit Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation changes
- `style:` Formatting changes
- `refactor:` Code refactoring
- `perf:` Performance improvements
- `test:` Test additions or modifications
- `chore:` Maintenance tasks

Examples:
```
feat: add playlist sharing functionality
fix: resolve race condition in streaming service
docs: update API documentation for search endpoints
```

## Pull Request Process

1. **Update your fork**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Make your changes**
   - Write clean, documented code
   - Add/update tests
   - Update documentation

3. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: your feature description"
   ```

4. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

5. **Create Pull Request**
   - Use the PR template
   - Link related issues
   - Request reviews from maintainers

6. **Code Review**
   - Address feedback promptly
   - Update PR based on suggestions
   - Keep discussions professional

7. **After Merge**
   - Delete your feature branch
   - Update your local main branch
   - Celebrate your contribution! 🎉

## API Design Guidelines

When adding new endpoints:

1. Follow RESTful conventions
2. Use appropriate HTTP methods
3. Return consistent response formats
4. Include proper error handling
5. Document the endpoint in code

Example:
```kotlin
/**
 * Get user playlists
 * @return List of user's playlists
 */
get("/api/playlists") {
    // Implementation
}
```

## Database Changes

When making database changes:

1. Create a new migration file:
   ```
   V{number}__{description}.sql
   ```

2. Test migration locally:
   ```bash
   ./gradlew flywayMigrate
   ```

3. Include rollback strategy in PR description

## Community

- **GitHub Issues**: Bug reports and feature requests
- **Discussions**: General questions and ideas
- **Pull Requests**: Code contributions

## Recognition

Contributors will be recognized in:
- The project README
- Release notes
- Special thanks section

Thank you for contributing to Musify Backend! 🎵