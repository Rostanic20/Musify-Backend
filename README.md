# Musify Backend

<div align="center">
  
  ![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
  ![Ktor](https://img.shields.io/badge/ktor-%23000000.svg?style=for-the-badge&logo=ktor&logoColor=white)
  ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)
  ![Redis](https://img.shields.io/badge/redis-%23DD0031.svg?style=for-the-badge&logo=redis&logoColor=white)
  ![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)
  
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  ![GitHub last commit](https://img.shields.io/github/last-commit/Rostanic20/Musify-Backend)
  ![GitHub issues](https://img.shields.io/github/issues/Rostanic20/Musify-Backend)
  
  <p align="center">
    <b>A high-performance, scalable music streaming backend built with Kotlin and Ktor</b>
  </p>
  
  <p align="center">
    <a href="#features">Features</a> •
    <a href="#tech-stack">Tech Stack</a> •
    <a href="#getting-started">Getting Started</a> •
    <a href="#api-documentation">API</a> •
    <a href="#contributing">Contributing</a>
  </p>
  
</div>

---

## 🎵 Overview

Musify Backend is a sophisticated music streaming service backend that provides comprehensive features for building a modern music streaming platform. Built with clean architecture principles, it offers high-performance audio streaming, intelligent recommendations, social features, and robust offline capabilities.

## ✨ Features

### Core Streaming
- 🎧 **Progressive Audio Streaming** - Range request support with adaptive bitrate
- 📡 **CDN Integration** - CloudFront support for global content delivery
- 🎬 **HLS Streaming** - HTTP Live Streaming for optimal performance
- 🔊 **Multiple Quality Levels** - From 96 kbps to lossless audio (1411 kbps)
- 📊 **Real-time Analytics** - Track listening patterns and user behavior

### User Features
- 🔐 **Advanced Authentication** - JWT with refresh tokens, OAuth2 (Google/Facebook), 2FA support
- 👥 **Social Features** - Follow users/artists, share playlists, activity feed
- 📱 **Offline Mode** - Smart downloads with ML-based predictions
- 🎯 **Smart Recommendations** - Hybrid recommendation engine with 5 strategies
- 🔍 **Advanced Search** - Semantic search, voice search, typo tolerance

### Platform Features
- 💳 **Subscription Management** - Tiered plans with Stripe integration
- 📊 **Admin Dashboard** - User management and content moderation
- 🚀 **High Performance** - Redis caching with stampede protection
- 🛡️ **Security First** - Rate limiting, CORS, input validation
- 📈 **Monitoring** - Sentry integration, CloudWatch metrics

## 🛠️ Tech Stack

### Core
- **Language**: Kotlin 1.9.22
- **Framework**: Ktor 2.3.7
- **Database**: PostgreSQL with HikariCP pooling
- **ORM**: Exposed 0.45.0
- **Caching**: Redis/Valkey with Jedis
- **Dependency Injection**: Koin 3.5.3

### Infrastructure
- **Migrations**: Flyway 9.22.3
- **Storage**: AWS S3 / Local storage
- **CDN**: CloudFront
- **Monitoring**: Sentry, Micrometer, CloudWatch
- **Testing**: JUnit 5, MockK, Testcontainers

### Security
- **Authentication**: JWT, OAuth2, TOTP (2FA)
- **Password Hashing**: BCrypt
- **API Security**: Rate limiting, CORS headers

## 🚀 Getting Started

### Prerequisites

- JDK 17 or higher
- PostgreSQL 14+
- Redis 7+
- Docker (optional)

### Quick Start

1. **Clone the repository**
   ```bash
   git clone https://github.com/Rostanic20/Musify-Backend.git
   cd Musify-Backend
   ```

2. **Set up environment variables**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Set up the database**
   ```bash
   cp setup-database.sql.example setup-database.sql
   # Edit with your password
   psql -U postgres < setup-database.sql
   ```

4. **Install Redis**
   ```bash
   # On Manjaro/Arch
   ./install-redis-manjaro.sh
   # Or use Docker
   docker run -d -p 6379:6379 redis:alpine
   ```

5. **Run the application**
   ```bash
   ./gradlew run
   ```

### Using Docker

```bash
# Development
docker-compose up

# Production
docker-compose -f docker-compose.prod.yml up
```

## 📚 API Documentation

### Authentication Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login with credentials |
| POST | `/api/auth/refresh` | Refresh access token |
| POST | `/api/auth/logout` | Logout user |
| GET  | `/api/auth/verify-email` | Verify email address |
| POST | `/api/auth/2fa/enable` | Enable 2FA |
| POST | `/api/auth/oauth/google` | Google OAuth login |

### Music Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/songs` | List all songs |
| GET | `/api/songs/{id}` | Get song details |
| GET | `/api/songs/stream/{id}` | Stream audio file |
| POST | `/api/songs/{id}/favorite` | Toggle favorite |
| GET | `/api/search` | Search songs/artists/albums |

### Playlist Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/playlists` | Get user playlists |
| POST | `/api/playlists` | Create playlist |
| PUT | `/api/playlists/{id}` | Update playlist |
| POST | `/api/playlists/{id}/songs` | Add song to playlist |

### Social Features

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/social/follow/{userId}` | Follow user |
| GET | `/api/social/followers` | Get followers |
| GET | `/api/social/feed` | Get activity feed |
| POST | `/api/social/share` | Share content |

For complete API documentation, see [docs/SEARCH_API.md](docs/SEARCH_API.md) and other docs.

## 🏗️ Architecture

The project follows Clean Architecture principles:

```
src/main/kotlin/com/musify/
├── domain/           # Business logic & entities
│   ├── entities/     # Core business models
│   ├── repository/   # Repository interfaces
│   ├── services/     # Domain services
│   └── usecase/      # Business use cases
├── data/            # Data layer implementations
│   └── repository/   # Repository implementations
├── presentation/    # API layer
│   ├── controller/   # HTTP endpoints
│   ├── dto/         # Data transfer objects
│   └── middleware/   # Auth, validation
├── infrastructure/  # External services
│   ├── auth/        # JWT, OAuth providers
│   ├── cache/       # Redis implementation
│   ├── email/       # Email service
│   └── storage/     # S3, local storage
└── di/             # Dependency injection
```

## 🧪 Testing

Run the test suite:

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# Run specific test
./gradlew test --tests "*AuthControllerTest"
```

## 🔧 Configuration

### Environment Variables

Key environment variables (see `.env.example` for full list):

```env
# Database
DATABASE_HOST=localhost
DATABASE_PORT=5432
DATABASE_NAME=musify
DATABASE_USER=musify_user
DATABASE_PASSWORD=your_password

# JWT
JWT_SECRET=your-secret-key
JWT_EXPIRY=86400000

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# AWS (Optional)
AWS_ACCESS_KEY_ID=your-key
AWS_SECRET_ACCESS_KEY=your-secret
S3_BUCKET_NAME=musify-storage

# Monitoring (Optional)
SENTRY_DSN=your-sentry-dsn
```

### Database Migrations

Migrations run automatically on startup. To run manually:

```bash
./gradlew flywayMigrate
```

## 📊 Monitoring & Logging

- **Logs**: Written to `logs/application.log` with daily rotation
- **Metrics**: Available at `/metrics` endpoint (Prometheus format)
- **Health Check**: `GET /health` returns system status
- **Monitoring Dashboard**: Access at `/monitoring/dashboard`

## 🚢 Deployment

### Production Checklist

See [PRODUCTION_CHECKLIST.md](PRODUCTION_CHECKLIST.md) for detailed deployment steps.

### Quick Deploy

1. Build Docker image:
   ```bash
   docker build -t musify-backend .
   ```

2. Run with production config:
   ```bash
   docker-compose -f docker-compose.prod.yml up -d
   ```

3. Set up reverse proxy (Nginx example in `nginx/nginx.conf`)

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Development Setup

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with [Ktor](https://ktor.io/) - Kotlin async web framework
- Database ORM by [Exposed](https://github.com/JetBrains/Exposed)
- Caching powered by [Redis](https://redis.io/)

---

<div align="center">
  Made with ❤️ by <a href="https://github.com/Rostanic20">Rostanic20</a>
</div>