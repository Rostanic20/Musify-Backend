#!/bin/bash
#
# Musify Backend Deployment Script
# This script handles deployment to staging/production environments
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
ENVIRONMENT="staging"
SKIP_TESTS=false
SKIP_BUILD=false
DEPLOY_HOST=""
DEPLOY_USER=""
DEPLOY_PATH="/opt/musify"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --env)
            ENVIRONMENT="$2"
            shift 2
            ;;
        --host)
            DEPLOY_HOST="$2"
            shift 2
            ;;
        --user)
            DEPLOY_USER="$2"
            shift 2
            ;;
        --path)
            DEPLOY_PATH="$2"
            shift 2
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --help)
            echo "Usage: ./deploy.sh [options]"
            echo "Options:"
            echo "  --env <environment>    Environment to deploy to (staging/production)"
            echo "  --host <hostname>      Target host for deployment"
            echo "  --user <username>      SSH user for deployment"
            echo "  --path <path>          Deployment path on target host"
            echo "  --skip-tests          Skip running tests"
            echo "  --skip-build          Skip building the application"
            echo "  --help               Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo -e "${GREEN}🚀 Musify Backend Deployment Script${NC}"
echo "Environment: $ENVIRONMENT"
echo "================================================"

# Validate environment
if [[ "$ENVIRONMENT" != "staging" && "$ENVIRONMENT" != "production" ]]; then
    echo -e "${RED}❌ Invalid environment: $ENVIRONMENT${NC}"
    echo "Valid environments: staging, production"
    exit 1
fi

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo -e "${RED}❌ .env file not found${NC}"
    echo "Please create a .env file based on .env.template"
    exit 1
fi

# Load environment variables
export $(cat .env | grep -v '^#' | xargs)

# Validate required environment variables
validate_env() {
    local var_name=$1
    local var_value=${!var_name}
    
    if [ -z "$var_value" ]; then
        echo -e "${RED}❌ Missing required environment variable: $var_name${NC}"
        exit 1
    fi
}

# Validate critical variables for production
if [ "$ENVIRONMENT" == "production" ]; then
    echo -e "${YELLOW}Validating production configuration...${NC}"
    validate_env "JWT_SECRET"
    validate_env "DATABASE_URL"
    
    # Check if JWT_SECRET is not the default
    if [[ "$JWT_SECRET" == *"change-this-in-production"* ]]; then
        echo -e "${RED}❌ JWT_SECRET contains default value. Please set a secure secret!${NC}"
        exit 1
    fi
    
    # Check if database is not H2
    if [[ "$DATABASE_URL" == *"h2:mem"* || "$DATABASE_URL" == *"h2:file"* ]]; then
        echo -e "${RED}❌ H2 database cannot be used in production!${NC}"
        exit 1
    fi
fi

# Run tests
if [ "$SKIP_TESTS" = false ]; then
    echo -e "${YELLOW}Running tests...${NC}"
    ./gradlew test || {
        echo -e "${RED}❌ Tests failed${NC}"
        exit 1
    }
    echo -e "${GREEN}✅ All tests passed${NC}"
else
    echo -e "${YELLOW}⚠️  Skipping tests${NC}"
fi

# Build application
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${YELLOW}Building application...${NC}"
    ./gradlew clean buildFatJar || {
        echo -e "${RED}❌ Build failed${NC}"
        exit 1
    }
    echo -e "${GREEN}✅ Build successful${NC}"
else
    echo -e "${YELLOW}⚠️  Skipping build${NC}"
fi

# Build Docker image
echo -e "${YELLOW}Building Docker image...${NC}"
docker build -t musify-backend:latest . || {
    echo -e "${RED}❌ Docker build failed${NC}"
    exit 1
}
echo -e "${GREEN}✅ Docker image built${NC}"

# Tag image for environment
docker tag musify-backend:latest musify-backend:$ENVIRONMENT

# If deploying to remote host
if [ -n "$DEPLOY_HOST" ] && [ -n "$DEPLOY_USER" ]; then
    echo -e "${YELLOW}Deploying to remote host: $DEPLOY_HOST${NC}"
    
    # Save Docker image
    echo "Saving Docker image..."
    docker save musify-backend:$ENVIRONMENT | gzip > musify-backend-$ENVIRONMENT.tar.gz
    
    # Transfer files to remote host
    echo "Transferring files..."
    scp musify-backend-$ENVIRONMENT.tar.gz $DEPLOY_USER@$DEPLOY_HOST:/tmp/
    scp docker-compose.prod.yml $DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_PATH/docker-compose.yml
    scp .env $DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_PATH/.env
    scp -r nginx $DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_PATH/
    
    # Execute deployment on remote host
    echo "Executing remote deployment..."
    ssh $DEPLOY_USER@$DEPLOY_HOST << EOF
        cd $DEPLOY_PATH
        
        # Load Docker image
        echo "Loading Docker image..."
        docker load < /tmp/musify-backend-$ENVIRONMENT.tar.gz
        rm /tmp/musify-backend-$ENVIRONMENT.tar.gz
        
        # Stop existing containers
        echo "Stopping existing containers..."
        docker-compose down || true
        
        # Start new containers
        echo "Starting new containers..."
        docker-compose up -d
        
        # Wait for health check
        echo "Waiting for application to be healthy..."
        for i in {1..30}; do
            if docker-compose exec -T app curl -f http://localhost:8080/health > /dev/null 2>&1; then
                echo "✅ Application is healthy"
                break
            fi
            echo "Waiting... \$i/30"
            sleep 2
        done
        
        # Show container status
        docker-compose ps
EOF
    
    # Clean up local tar file
    rm musify-backend-$ENVIRONMENT.tar.gz
    
else
    echo -e "${YELLOW}Starting local deployment...${NC}"
    
    # Use appropriate docker-compose file
    if [ "$ENVIRONMENT" == "production" ]; then
        docker-compose -f docker-compose.prod.yml up -d
    else
        docker-compose up -d
    fi
    
    # Wait for services to be healthy
    echo "Waiting for services to be healthy..."
    sleep 10
    
    # Check health
    curl -f http://localhost:8080/health || {
        echo -e "${RED}❌ Health check failed${NC}"
        docker-compose logs app
        exit 1
    }
fi

echo -e "${GREEN}✅ Deployment completed successfully!${NC}"
echo "================================================"
echo "Environment: $ENVIRONMENT"
if [ -n "$DEPLOY_HOST" ]; then
    echo "Host: $DEPLOY_HOST"
fi
echo "API URL: ${API_BASE_URL:-http://localhost:8080}"
echo "================================================"