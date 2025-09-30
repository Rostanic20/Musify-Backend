#!/bin/bash

# Script to create a test user in the database
# This can be run after backend restart to quickly recreate your test user

# Database connection details
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="musify"
DB_USER="musify_user"
DB_PASSWORD="musify123"

# Test user details
TEST_USERNAME="test"
TEST_EMAIL="test@example.com"
TEST_PASSWORD="test1234"
TEST_DISPLAY_NAME="Test User"

# Generate bcrypt hash for the password (using htpasswd from apache2-utils)
# Note: You might need to install apache2-utils: sudo apt-get install apache2-utils
# For now, using a pre-generated hash for "test1234"
PASSWORD_HASH='$2a$12$Lk7W9CJn0C2dKyD0FjzYOeUZmQH7gKyPU6H0SSz1QKQdpGSFyK2Oy'

# SQL to insert user
SQL="INSERT INTO users (username, email, password_hash, display_name, is_verified, is_active, created_at, updated_at) 
     VALUES ('$TEST_USERNAME', '$TEST_EMAIL', '$PASSWORD_HASH', '$TEST_DISPLAY_NAME', true, true, NOW(), NOW())
     ON CONFLICT (username) DO NOTHING;"

# Execute SQL
echo "Creating test user..."
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -d $DB_NAME -U $DB_USER -c "$SQL"

if [ $? -eq 0 ]; then
    echo "✅ Test user created successfully!"
    echo "Username: $TEST_USERNAME"
    echo "Password: $TEST_PASSWORD"
else
    echo "❌ Failed to create test user"
fi