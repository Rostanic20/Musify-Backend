#!/bin/bash

echo "=== MUSIFY BACKEND CODEBASE ANALYSIS ==="
echo "Analyzing unused classes and duplicates..."
echo

# Extract all class names with their files
echo "Extracting all classes..."
find src/main/kotlin -name "*.kt" -type f | while read file; do
    grep -E "^(class|interface|object|data class|enum class|sealed class|abstract class) " "$file" | while read line; do
        class_name=$(echo "$line" | sed -E 's/^(class|interface|object|data class|enum class|sealed class|abstract class) ([A-Za-z0-9]+).*/\2/')
        echo "$class_name|$file"
    done
done > /tmp/all_classes.txt

# Find duplicate entities in models vs domain/entities
echo -e "\n=== DUPLICATE ENTITIES (models vs domain/entities) ==="
grep -E "(models|domain/entities)" /tmp/all_classes.txt | awk -F'|' '{print $1}' | sort | uniq -d | while read class; do
    echo -e "\nDuplicate: $class"
    grep "$class|" /tmp/all_classes.txt | grep -E "(models|domain/entities)" | awk -F'|' '{print "  - " $2}'
done

# Find backup files
echo -e "\n=== BACKUP FILES ==="
find src/main/kotlin -name "*_backup*" -o -name "*.bak" | sort

# Find similar repository implementations
echo -e "\n=== REPOSITORY IMPLEMENTATIONS ==="
grep "Repository" /tmp/all_classes.txt | awk -F'|' '{print $1}' | sed 's/Impl$//' | sed 's/Enhanced//' | sed 's/Cached//' | sort | uniq -c | sort -nr | head -20

# Find similar service implementations
echo -e "\n=== SERVICE IMPLEMENTATIONS ==="
grep "Service" /tmp/all_classes.txt | awk -F'|' '{print $1}' | sed 's/Impl$//' | sed 's/V2$//' | sed 's/Resilient//' | sort | uniq -c | sort -nr | head -20

# Find unused classes by checking imports
echo -e "\n=== CHECKING FOR POTENTIALLY UNUSED CLASSES ==="
echo "This requires checking if classes are imported or referenced..."

# Check some specific patterns
echo -e "\n=== CLASSES WITH V2 SUFFIX (potential duplicates) ==="
grep "V2" /tmp/all_classes.txt

echo -e "\n=== INTERACTION CONTROLLER VARIANTS ==="
grep "InteractionController" /tmp/all_classes.txt

echo -e "\n=== AUTH EXTENSIONS DUPLICATES ==="
find src/main/kotlin -name "*AuthExtensions*" -type f

echo -e "\n=== SERIALIZER DUPLICATES ==="
grep "Serializer" /tmp/all_classes.txt | grep -v "Companion"

# Check for unused test utilities
echo -e "\n=== TEST UTILITIES IN MAIN SOURCE ==="
find src/main/kotlin -name "*Test*.kt" -type f

# Find similar class names
echo -e "\n=== SIMILAR CLASS NAMES (potential duplicates) ==="
awk -F'|' '{print $1}' /tmp/all_classes.txt | sort | while read class1; do
    awk -F'|' '{print $1}' /tmp/all_classes.txt | sort | while read class2; do
        if [[ "$class1" != "$class2" ]] && [[ "${class1,,}" == "${class2,,}" ]]; then
            echo "Similar: $class1 vs $class2"
            grep -E "($class1|$class2)" /tmp/all_classes.txt | awk -F'|' '{print "  " $1 " in " $2}'
        fi
    done
done | sort | uniq

# Count total classes
echo -e "\n=== SUMMARY ==="
total_classes=$(wc -l < /tmp/all_classes.txt)
echo "Total classes/interfaces/objects: $total_classes"