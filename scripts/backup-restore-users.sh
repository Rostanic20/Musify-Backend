#!/bin/bash

# Script to backup and restore users from PostgreSQL

# Database connection details
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="musify"
DB_USER="musify_user"
DB_PASSWORD="musify123"
BACKUP_FILE="users_backup.sql"

case "$1" in
    backup)
        echo "📦 Backing up users..."
        PGPASSWORD=$DB_PASSWORD pg_dump -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME \
            --table=users \
            --data-only \
            --column-inserts \
            > $BACKUP_FILE
        
        if [ $? -eq 0 ]; then
            echo "✅ Backup saved to $BACKUP_FILE"
            echo "Users in backup:"
            grep "INSERT INTO" $BACKUP_FILE | wc -l
        else
            echo "❌ Backup failed"
        fi
        ;;
        
    restore)
        if [ ! -f "$BACKUP_FILE" ]; then
            echo "❌ No backup file found!"
            exit 1
        fi
        
        echo "📥 Restoring users from backup..."
        PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -d $DB_NAME -U $DB_USER < $BACKUP_FILE
        
        if [ $? -eq 0 ]; then
            echo "✅ Users restored successfully!"
        else
            echo "⚠️  Some users may have been skipped (duplicates)"
        fi
        ;;
        
    list)
        echo "📋 Current users in database:"
        PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -d $DB_NAME -U $DB_USER \
            -c "SELECT id, username, email, display_name, is_artist, created_at FROM users ORDER BY id;"
        ;;
        
    *)
        echo "Usage: $0 {backup|restore|list}"
        echo "  backup  - Save current users to backup file"
        echo "  restore - Restore users from backup file"
        echo "  list    - Show all users in database"
        exit 1
        ;;
esac