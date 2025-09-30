# Security Configuration Guide

## CORS Configuration

The backend supports flexible CORS configuration through environment variables. 

### Development Mode
In development mode or when `CORS_ALLOWED_HOSTS=*`, the server allows requests from any origin.

### Production Mode
For production, you should restrict CORS to specific trusted origins by setting the `CORS_ALLOWED_HOSTS` environment variable.

#### Configuration Examples:

**Single origin:**
```bash
CORS_ALLOWED_HOSTS=https://app.musify.com
```

**Multiple origins (comma-separated):**
```bash
CORS_ALLOWED_HOSTS=https://app.musify.com,https://admin.musify.com,https://m.musify.com
```

**With and without protocol:**
```bash
# This will allow both http and https
CORS_ALLOWED_HOSTS=app.musify.com,admin.musify.com

# Or be explicit about protocols
CORS_ALLOWED_HOSTS=https://app.musify.com,http://localhost:3000
```

**Development with specific hosts:**
```bash
ENVIRONMENT=development
CORS_ALLOWED_HOSTS=http://localhost:3000,http://localhost:5000
```

### Allowed Headers
The following headers are allowed for cross-origin requests:
- `Authorization` - For JWT authentication
- `Content-Type` - For JSON/form data
- `X-Requested-With` - For AJAX requests
- `X-Network-Type` - For smart download feature

### Allowed Methods
All standard HTTP methods are allowed:
- GET, POST, PUT, DELETE, PATCH, OPTIONS

### Credentials
Cross-origin requests with credentials (cookies, authorization headers) are allowed when `allowCredentials = true`.

### Preflight Cache
Preflight responses are cached for 1 hour (3600 seconds) to reduce OPTIONS requests.

## Security Headers

Additional security headers should be configured at the reverse proxy level (nginx/Apache) or CDN:

```nginx
# Example nginx configuration
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline';" always;
```

## Database Connection Pool

The database connection pool is configured with the following defaults:
- **Maximum Pool Size**: 30 connections (configurable via `DATABASE_MAX_POOL_SIZE`)
- **Minimum Idle**: 5 connections (configurable via `DATABASE_MIN_IDLE`)
- **Connection Timeout**: 30 seconds (configurable via `DATABASE_CONNECTION_TIMEOUT_MS`)

For production, adjust these values based on your server capacity and expected load.

## Rate Limiting

Rate limiting is enabled by default with the following configuration:
- **Free users**: 60 requests per minute (configurable via `RATE_LIMIT_REQUESTS_PER_MINUTE`)
- **Premium users**: 200 requests per minute (hardcoded, should be made configurable)

## Authentication & Authorization

All sensitive endpoints are protected with JWT authentication. The middleware ensures:
- Valid JWT tokens are required for protected routes
- User exists in database
- Token hasn't expired
- Proper user context is available for authorization checks

### Protected Route Patterns:
- User-specific data access
- Data modification operations  
- Premium features
- Administrative functions
- User interaction tracking

### Public Route Patterns:
- Authentication endpoints
- Public content browsing
- Health checks
- Webhook endpoints (protected by signatures)