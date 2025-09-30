#!/bin/bash

echo "=== FILES SAFE TO REMOVE ==="
echo
echo "These files can be safely removed based on the analysis:"
echo

echo "1. Backup files:"
find src/main/kotlin -name "*.bak" -o -name "*_backup.kt" -o -name "*.backup" | sort

echo -e "\n2. Test utilities in main source:"
echo "src/main/kotlin/com/musify/TestConnection.kt"

echo -e "\n3. Unused AuthExtensions:"
echo "src/main/kotlin/com/musify/utils/AuthExtensions.kt (0 imports, duplicate of presentation version)"

echo -e "\n4. Models package files (using domain entities instead):"
# Check each model file for actual usage
for file in src/main/kotlin/com/musify/models/*.kt; do
    class_name=$(basename "$file" .kt)
    import_count=$(rg "import.*models\.$class_name" src/main/kotlin --glob '*.kt' | wc -l)
    if [ "$import_count" -le 1 ]; then
        echo "$file (imports: $import_count)"
    fi
done

echo -e "\n5. Duplicate implementations to consider removing:"
echo "src/main/kotlin/com/musify/core/media/AudioStreamingService.kt (consider keeping only V2 or Resilient)"
echo "src/main/kotlin/com/musify/data/repository/UserRepositoryImpl.kt (if EnhancedCached version is used)"
echo "src/main/kotlin/com/musify/data/repository/SearchRepositoryImpl_backup.kt (backup file)"

echo -e "\n=== VERIFICATION COMMANDS ==="
echo
echo "Before removing any file, verify it's not used:"
echo "rg 'ClassName' src/main/kotlin --glob '*.kt'"
echo
echo "To safely remove files:"
echo "mkdir -p ~/musify-backend-removed-files"
echo "mv <file> ~/musify-backend-removed-files/"