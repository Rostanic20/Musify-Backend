#!/bin/bash

# Local Streaming Setup Script for Musify
# This script sets up local development environment for streaming

set -e

echo "🎵 Musify Local Streaming Setup"
echo "=============================="

# Create necessary directories
echo "📁 Creating directories..."
mkdir -p uploads/songs
mkdir -p uploads/temp
mkdir -p uploads/hls
mkdir -p keys

# Check if FFmpeg is installed
echo "🎬 Checking FFmpeg..."
if command -v ffmpeg &> /dev/null; then
    echo "✅ FFmpeg is installed"
    ffmpeg -version | head -n 1
else
    echo "❌ FFmpeg is not installed"
    echo ""
    echo "Please install FFmpeg:"
    echo "  Ubuntu/Debian: sudo apt-get install ffmpeg"
    echo "  macOS: brew install ffmpeg"
    echo "  Windows: Download from https://ffmpeg.org/download.html"
    exit 1
fi

# Generate local signing key for development
echo "🔐 Generating signing keys..."
if [ ! -f "keys/streaming-private-key.pem" ]; then
    openssl genrsa -out keys/streaming-private-key.pem 2048
    openssl rsa -pubout -in keys/streaming-private-key.pem -out keys/streaming-public-key.pem
    echo "✅ Keys generated"
else
    echo "✅ Keys already exist"
fi

# Create sample .env.local file
echo "📝 Creating .env.local file..."
cat > .env.local <<EOF
# Local Development Settings
ENVIRONMENT=development

# Database (using H2 for local dev)
DATABASE_URL=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1
DATABASE_DRIVER=org.h2.Driver

# JWT Settings
JWT_SECRET=local-development-secret-change-in-production
JWT_ISSUER=musify-backend
JWT_AUDIENCE=musify-app

# Storage Settings (local)
STORAGE_TYPE=local
LOCAL_STORAGE_PATH=./uploads

# CDN Settings (disabled for local)
CDN_ENABLED=false
CDN_BASE_URL=http://localhost:8080

# Streaming Settings
STREAMING_SECRET_KEY=local-streaming-secret
ENABLE_HLS_STREAMING=true
DEFAULT_SEGMENT_DURATION=10
MAX_CONCURRENT_STREAMS=10

# Transcoding Settings
FFMPEG_PATH=$(which ffmpeg)
TRANSCODING_ENABLED=true
TRANSCODING_WORKERS=2

# Email (disabled for local)
EMAIL_ENABLED=false

# Redis (disabled for local)
REDIS_ENABLED=false

# Monitoring (disabled for local)
MONITORING_ENABLED=false
EOF

echo "✅ .env.local created"

# Create sample audio files for testing
echo "🎵 Creating test audio files..."
if [ ! -f "uploads/songs/test_song.mp3" ]; then
    # Generate a 30-second test tone
    ffmpeg -f lavfi -i "sine=frequency=440:duration=30" -ac 2 -ar 44100 -b:a 192k uploads/songs/test_song.mp3 2>/dev/null
    echo "✅ Test audio file created"
else
    echo "✅ Test audio already exists"
fi

# Create test script
echo "📜 Creating test script..."
cat > test-streaming.sh <<'EOF'
#!/bin/bash

echo "🧪 Testing Musify Streaming"
echo "========================="

# Test 1: Check if server is running
echo "1️⃣ Checking server..."
if curl -s http://localhost:8080 > /dev/null; then
    echo "✅ Server is running"
else
    echo "❌ Server is not running. Start it with: ./gradlew run"
    exit 1
fi

# Test 2: Test streaming endpoint
echo "2️⃣ Testing streaming endpoint..."
# You'll need to add a valid auth token here
AUTH_TOKEN="your-auth-token"
curl -H "Authorization: Bearer $AUTH_TOKEN" \
     http://localhost:8080/api/songs/stream/1/url

echo ""
echo "✅ Tests complete!"
EOF

chmod +x test-streaming.sh

# Create README
echo "📚 Creating README..."
cat > STREAMING_LOCAL_README.md <<EOF
# Local Streaming Setup

## Quick Start

1. Copy environment variables:
   \`\`\`bash
   cp .env.local .env
   \`\`\`

2. Start the server:
   \`\`\`bash
   ./gradlew run
   \`\`\`

3. Test streaming:
   \`\`\`bash
   ./test-streaming.sh
   \`\`\`

## Directory Structure

- \`uploads/songs/\` - Audio files
- \`uploads/temp/\` - Temporary transcoding files
- \`uploads/hls/\` - HLS segments
- \`keys/\` - Signing keys

## Testing Audio Upload

1. Upload a song through the API
2. Check transcoding logs
3. Verify files in uploads directory

## Troubleshooting

- **FFmpeg not found**: Install FFmpeg for your OS
- **Permission denied**: Check directory permissions
- **Out of space**: Clear temp directory

## Production Setup

For production, run:
\`\`\`bash
./scripts/setup-cloudfront.sh
\`\`\`
EOF

echo ""
echo "✅ Local streaming setup complete!"
echo ""
echo "Next steps:"
echo "1. Copy .env.local to .env: cp .env.local .env"
echo "2. Start the server: ./gradlew run"
echo "3. Test streaming: ./test-streaming.sh"
echo ""
echo "📖 See STREAMING_LOCAL_README.md for more details"