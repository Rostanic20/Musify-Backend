# Build stage
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code
COPY src ./src

# Build application
RUN gradle buildFatJar --no-daemon

# Runtime stage
FROM amazoncorretto:17-alpine

# Install required packages
RUN apk add --no-cache \
    curl \
    bash \
    ffmpeg

# Create non-root user
RUN addgroup -g 1001 -S musify && \
    adduser -u 1001 -S musify -G musify

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/build/libs/musify-backend-fat.jar ./app.jar

# Copy necessary files
COPY keys ./keys

# Create necessary directories
RUN mkdir -p uploads/songs uploads/temp uploads/hls logs && \
    chown -R musify:musify /app

# Switch to non-root user
USER musify

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]