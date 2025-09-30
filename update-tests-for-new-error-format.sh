#!/bin/bash

# Script to update tests for the new error handling format

echo "Updating test files for new error handling format..."

# Find all test files that might need updating
TEST_FILES=(
    "src/test/kotlin/com/musify/presentation/controller/AuthControllerPasswordResetTest.kt"
    "src/test/kotlin/com/musify/presentation/controller/AuthControllerEmailVerificationTest.kt"
    "src/test/kotlin/com/musify/presentation/controller/AuthController2FATest.kt"
    "src/test/kotlin/com/musify/presentation/controller/AuthControllerOAuthTest.kt"
    "src/test/kotlin/com/musify/presentation/controller/PlaylistControllerTest.kt"
    "src/test/kotlin/com/musify/presentation/controller/SongControllerTest.kt"
    "src/test/kotlin/com/musify/presentation/controller/SearchControllerTest.kt"
    "src/test/kotlin/com/musify/security/AuthenticationBypassTest.kt"
    "src/test/kotlin/com/musify/security/AuthorizationTest.kt"
    "src/test/kotlin/com/musify/security/JWTSecurityTest.kt"
)

# Create a backup directory
mkdir -p test-backup

# Common patterns to replace
# Pattern 1: Simple error check
# OLD: assertNotNull(responseBody["error"])
# NEW: assertTrue(ErrorTestUtils.hasError(responseBody))

# Pattern 2: Checking errors field
# OLD: assertNotNull(responseBody["errors"])
# NEW: assertNotNull(ErrorTestUtils.getValidationErrors(responseBody))

# Pattern 3: Getting error message
# OLD: responseBody["error"]?.jsonPrimitive?.content
# NEW: ErrorTestUtils.getErrorMessage(responseBody)

for file in "${TEST_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "Processing $file..."
        
        # Backup the file
        cp "$file" "test-backup/$(basename $file).bak"
        
        # Add import if not present
        if ! grep -q "import com.musify.utils.ErrorTestUtils" "$file"; then
            sed -i '/import kotlin.test/ a import com.musify.utils.ErrorTestUtils' "$file"
        fi
        
        # Update simple error checks
        sed -i 's/assertNotNull(responseBody\["error"\])/assertTrue(ErrorTestUtils.hasError(responseBody))/' "$file"
        
        # Update validation error checks
        sed -i 's/assertNotNull(responseBody\["errors"\])/assertNotNull(ErrorTestUtils.getValidationErrors(responseBody))/' "$file"
        
        # Update error message access
        sed -i 's/responseBody\["error"\]?.jsonPrimitive?.content/ErrorTestUtils.getErrorMessage(responseBody)/' "$file"
        
        echo "Updated $file"
    fi
done

echo "Test update complete. Backups saved in test-backup/"