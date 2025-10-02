#!/bin/bash

echo "Setting up Musify database..."
echo "This will DROP the existing musify database if it exists!"
echo ""
read -p "Do you want to continue? (y/n): " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]
then
    echo "Please enter your MySQL/MariaDB root password:"
    mysql -u root -p < setup-database.sql
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ Database setup completed successfully!"
        echo ""
        echo "Database Details:"
        echo "- Database Name: musify"
        echo "- Username: musify_user"
        echo "- Password: musify2024"
        echo "- Host: localhost"
        echo "- Port: 3306"
        echo ""
        echo "You can now run the application with: ./gradlew run"
    else
        echo "❌ Database setup failed!"
    fi
else
    echo "Database setup cancelled."
fi