# Security Configuration

## Important: Before You Start

This repository contains example configuration files. You MUST create your own configuration files with real values before running the application.

### Required Setup Steps:

1. **Environment Variables**
   ```bash
   cp .env.example .env
   # Edit .env with your actual values
   ```

2. **Database Setup**
   ```bash
   cp setup-database.sql.example setup-database.sql
   # Edit setup-database.sql with a secure password
   ```

3. **Generate JWT Secret**
   ```bash
   # Generate a secure random secret
   openssl rand -base64 64
   ```

4. **SSL/TLS Certificates**
   - For production, obtain proper SSL certificates
   - Never commit private keys to version control
   - Store certificates securely

### Security Checklist:

- [ ] Changed all default passwords
- [ ] Generated new JWT secret
- [ ] Set up proper SSL certificates for production
- [ ] Configured secure database credentials
- [ ] Set up API keys for external services
- [ ] Reviewed and updated CORS settings
- [ ] Enabled HTTPS in production
- [ ] Configured rate limiting
- [ ] Set up monitoring and alerting

### Files That Should NEVER Be Committed:

- `.env` or any `.env.*` files (except `.env.example`)
- `*.pem` files (private keys)
- `*_token.txt` files
- `setup-database.sql` (with real passwords)
- Any file containing real credentials or secrets
- User data or authentication tokens

### Rotating Secrets

If any secrets are accidentally exposed:

1. Immediately rotate all affected credentials
2. Update all services using those credentials
3. Review access logs for any unauthorized access
4. Consider using `git filter-branch` or BFG Repo-Cleaner to remove from history

### Additional Security Resources

- [OWASP Security Guidelines](https://owasp.org/)
- [Spring Security Best Practices](https://spring.io/projects/spring-security)
- [AWS Security Best Practices](https://aws.amazon.com/architecture/security-identity-compliance/)