#!/bin/bash

echo "=== FINDING UNUSED CLASSES ==="
echo

# Create a list of all class names with their full paths
find src/main/kotlin -name "*.kt" -type f | while read file; do
    package=$(grep "^package " "$file" | sed 's/package //')
    grep -E "^(class|interface|object|data class|enum class|sealed class|abstract class) " "$file" | while read line; do
        class_name=$(echo "$line" | sed -E 's/^(class|interface|object|data class|enum class|sealed class|abstract class) ([A-Za-z0-9]+).*/\2/')
        if [ ! -z "$package" ] && [ ! -z "$class_name" ]; then
            echo "$package.$class_name|$class_name|$file"
        fi
    done
done > /tmp/all_classes_full.txt

# Find all imports
find src/main/kotlin -name "*.kt" -type f -exec grep -H "^import " {} \; | sed 's/:import / /' > /tmp/all_imports.txt

# Check each class for usage
echo "Classes that appear to be unused (not imported anywhere):"
echo

while IFS='|' read -r full_name class_name file_path; do
    # Skip certain files that are entry points or used via reflection
    if [[ "$file_path" =~ "Application.kt" ]] || \
       [[ "$file_path" =~ "routes/" ]] || \
       [[ "$file_path" =~ "AppModule.kt" ]] || \
       [[ "$class_name" == "Companion" ]]; then
        continue
    fi
    
    # Check if the class is imported anywhere
    if ! grep -q "$full_name" /tmp/all_imports.txt && \
       ! grep -q "\.$class_name" /tmp/all_imports.txt; then
        
        # Also check if it's used directly in the same package
        package_dir=$(dirname "$file_path")
        used_in_package=false
        
        find "$package_dir" -name "*.kt" -type f | while read pkg_file; do
            if [ "$pkg_file" != "$file_path" ]; then
                if grep -q "\b$class_name\b" "$pkg_file" 2>/dev/null; then
                    used_in_package=true
                    break
                fi
            fi
        done
        
        if [ "$used_in_package" = false ]; then
            # Additional check: is it referenced in any file?
            if ! rg -q "\b$class_name\b" src/main/kotlin --glob '*.kt' --glob '!'"$(basename "$file_path")" 2>/dev/null; then
                echo "- $class_name"
                echo "  File: $file_path"
                echo "  Full name: $full_name"
                echo
            fi
        fi
    fi
done < /tmp/all_classes_full.txt

echo -e "\n=== SPECIFIC SUSPICIOUS CLASSES ==="

echo -e "\nTest classes in main source:"
grep "Test" /tmp/all_classes_full.txt | grep -v "/test/"

echo -e "\nBackup classes:"
grep -E "_backup|\.bak" /tmp/all_classes_full.txt

echo -e "\nDuplicate services (V2, Resilient variants):"
grep -E "V2\||Resilient" /tmp/all_classes_full.txt | awk -F'|' '{print $2 " - " $3}'

echo -e "\nController variants:"
grep "InteractionController" /tmp/all_classes_full.txt | awk -F'|' '{print $2 " - " $3}'