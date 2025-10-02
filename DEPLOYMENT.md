# Musify Backend Deployment Guide

This guide provides instructions for deploying the Musify Backend to staging and production environments.

## Prerequisites

- Docker and Docker Compose installed
- PostgreSQL database (for production)
- Redis server (optional but recommended)
- SSL certificates (for production)
- Configured environment variables

## Environment Configuration

### 1. Environment Variables

Copy your existing `.env` file and update it for the target environment:

```bash
cp .env .env.production
```

**Critical variables to update for production:**

- `ENVIRONMENT=production`
- `JWT_SECRET` - Must be a secure, random string
- `DATABASE_URL` - PostgreSQL connection string
- `DATABASE_USER` and `DATABASE_PASSWORD`
- `REDIS_PASSWORD` - If using Redis
- `STRIPE_API_KEY` - For payment processing
- `CORS_ALLOWED_HOSTS` - Restrict to your domains

### 2. Database Setup

Ensure PostgreSQL is installed and create the database:

```bash
sudo -u postgres psql
CREATE DATABASE musify;
CREATE USER musify_user WITH ENCRYPTED PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE musify TO musify_user;
```

The application will run migrations automatically on startup using Flyway.

## Deployment Methods

### Method 1: Docker Compose (Recommended)

#### Local/Staging Deployment

```bash
# Start all services
docker-compose up -d

# Check logs
docker-compose logs -f app

# Stop services
docker-compose down
```

#### Production Deployment

```bash
# Use production compose file
docker-compose -f docker-compose.prod.yml up -d

# Or use the deployment script
./scripts/deploy.sh --env production
```

### Method 2: Deployment Script

The deployment script handles the entire deployment process:

```bash
# Deploy to staging
./scripts/deploy.sh --env staging

# Deploy to production
./scripts/deploy.sh --env production

# Deploy to remote server
./scripts/deploy.sh --env production \
  --host your-server.com \
  --user deploy \
  --path /opt/musify
```

Options:
- `--env` - Environment (staging/production)
- `--host` - Remote host for deployment
- `--user` - SSH user for remote deployment
- `--path` - Deployment path on remote host
- `--skip-tests` - Skip running tests
- `--skip-build` - Skip building the application

### Method 3: Manual Docker Deployment

```bash
# Build the Docker image
docker build -t musify-backend:latest .

# Run the container
docker run -d \
  --name musify-backend \
  -p 8080:8080 \
  --env-file .env \
  -v ./uploads:/app/uploads \
  -v ./logs:/app/logs \
  musify-backend:latest
```

## SSL/TLS Configuration

For production, you need SSL certificates. Place them in `nginx/ssl/`:

```bash
mkdir -p nginx/ssl
cp /path/to/cert.pem nginx/ssl/
cp /path/to/key.pem nginx/ssl/
```

Or use Let's Encrypt:

```bash
# Install certbot
sudo apt-get install certbot

# Generate certificates
sudo certbot certonly --standalone -d your-domain.com
```

## Health Checks

Verify the deployment is successful:

```bash
# Run health check script
./scripts/health-check.sh

# Or manually check endpoints
curl http://localhost:8080/health
curl http://localhost:8080/api/health/ready
curl http://localhost:8080/api/health/live
```

## Monitoring

### Application Logs

```bash
# Docker logs
docker-compose logs -f app

# Application logs
tail -f logs/application.log
```

### Metrics

If monitoring is enabled, metrics are available at:
- Prometheus: `/metrics`
- Health: `/health`

## Database Migrations

Migrations run automatically on startup. To run manually:

```bash
# Connect to container
docker-compose exec app bash

# Run migrations
java -cp app.jar org.flywaydb.core.Flyway migrate
```

## Backup and Restore

### Database Backup

```bash
# Backup
pg_dump -h localhost -U musify_user -d musify > backup.sql

# Restore
psql -h localhost -U musify_user -d musify < backup.sql
```

### File Storage Backup

```bash
# Backup uploads
tar -czf uploads-backup.tar.gz uploads/

# Restore uploads
tar -xzf uploads-backup.tar.gz
```

## Troubleshooting

### Common Issues

1. **Port already in use**
   ```bash
   # Find process using port
   sudo lsof -i :8080
   # Kill process
   sudo kill -9 <PID>
   ```

2. **Database connection failed**
   - Check PostgreSQL is running
   - Verify connection string in `.env`
   - Check firewall rules

3. **Redis connection failed**
   - Verify Redis is running
   - Check password in `.env`
   - Test connection: `redis-cli ping`

4. **Docker permission denied**
   ```bash
   # Add user to docker group
   sudo usermod -aG docker $USER
   # Log out and back in
   ```

### Debug Mode

Enable debug logging:
```bash
LOG_LEVEL=DEBUG docker-compose up
```

## Security Checklist

- [ ] Changed default JWT_SECRET
- [ ] Set strong database passwords
- [ ] Configured CORS for specific domains
- [ ] Enabled HTTPS/SSL
- [ ] Set up firewall rules
- [ ] Disabled debug mode in production
- [ ] Configured rate limiting
- [ ] Set up monitoring and alerts
- [ ] Regular security updates

## Scaling

### Horizontal Scaling

1. **Load Balancer**: Use nginx or HAProxy
2. **Multiple App Instances**: 
   ```yaml
   # docker-compose.yml
   app:
     scale: 3
   ```
3. **Database Replication**: Set up PostgreSQL replication
4. **Redis Cluster**: For distributed caching

### Vertical Scaling

Adjust resource limits in `docker-compose.prod.yml`:
```yaml
deploy:
  resources:
    limits:
      cpus: '4'
      memory: 8G
```

## Rollback Procedure

1. **Tag current version**:
   ```bash
   docker tag musify-backend:latest musify-backend:backup
   ```

2. **Deploy previous version**:
   ```bash
   docker-compose down
   docker tag musify-backend:previous musify-backend:latest
   docker-compose up -d
   ```

3. **Restore database** (if needed):
   ```bash
   psql -h localhost -U musify_user -d musify < backup.sql
   ```

## Maintenance Mode

To enable maintenance mode:

1. Update nginx configuration to serve maintenance page
2. Or use environment variable:
   ```bash
   MAINTENANCE_MODE=true docker-compose up -d
   ```

## Support

For issues or questions:
1. Check application logs
2. Run health checks
3. Review this documentation
4. Check GitHub issues