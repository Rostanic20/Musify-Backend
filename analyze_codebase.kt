#!/usr/bin/env kotlin

import java.io.File

data class KotlinClass(
    val filePath: String,
    val className: String,
    val packageName: String,
    val fullName: String
)

data class ImportInfo(
    val filePath: String,
    val importedClass: String
)

fun main() {
    val srcDir = File("src/main/kotlin")
    val kotlinFiles = srcDir.walkTopDown().filter { it.extension == "kt" && !it.path.contains("/test/") }
    
    // Extract all classes/objects/interfaces
    val allClasses = mutableListOf<KotlinClass>()
    val imports = mutableListOf<ImportInfo>()
    val fileContents = mutableMapOf<String, String>()
    
    kotlinFiles.forEach { file ->
        val content = file.readText()
        fileContents[file.path] = content
        
        // Extract package
        val packageMatch = Regex("package\\s+([\\w.]+)").find(content)
        val packageName = packageMatch?.groupValues?.get(1) ?: ""
        
        // Extract imports
        Regex("import\\s+([\\w.]+)").findAll(content).forEach { match ->
            imports.add(ImportInfo(file.path, match.groupValues[1]))
        }
        
        // Extract class/interface/object declarations
        val classPattern = Regex("(class|interface|object|enum\\s+class|data\\s+class|sealed\\s+class)\\s+(\\w+)")
        classPattern.findAll(content).forEach { match ->
            val className = match.groupValues[2]
            allClasses.add(KotlinClass(
                filePath = file.path,
                className = className,
                packageName = packageName,
                fullName = "$packageName.$className"
            ))
        }
    }
    
    // Find usage patterns
    val usedClasses = mutableSetOf<String>()
    
    // Classes used in imports
    imports.forEach { import ->
        usedClasses.add(import.importedClass)
        // Also consider partial imports
        val className = import.importedClass.split(".").last()
        allClasses.find { it.className == className }?.let {
            usedClasses.add(it.fullName)
        }
    }
    
    // Classes used in file contents (direct references)
    fileContents.forEach { (filePath, content) ->
        allClasses.forEach { kotlinClass ->
            // Check for various usage patterns
            val patterns = listOf(
                "\\b${kotlinClass.className}\\b(?!\\s*[:{])".toRegex(), // Direct usage
                ": ${kotlinClass.className}".toRegex(), // Type annotation
                "is ${kotlinClass.className}".toRegex(), // Type check
                "as ${kotlinClass.className}".toRegex(), // Type cast
                "<${kotlinClass.className}>".toRegex(), // Generic type
                "${kotlinClass.className}\\(".toRegex(), // Constructor call
                "${kotlinClass.className}::".toRegex(), // Reference
                "@${kotlinClass.className}".toRegex() // Annotation
            )
            
            if (patterns.any { it.containsMatchIn(content) } && filePath != kotlinClass.filePath) {
                usedClasses.add(kotlinClass.fullName)
            }
        }
    }
    
    // Find unused classes
    val unusedClasses = allClasses.filter { kotlinClass ->
        !usedClasses.contains(kotlinClass.fullName) && 
        !kotlinClass.className.equals("Application", ignoreCase = true) && // Entry point
        !kotlinClass.filePath.contains("routes") // Routes are used via reflection
    }
    
    // Find duplicate functionality
    val classGroups = allClasses.groupBy { it.className.toLowerCase() }
    val duplicates = classGroups.filter { it.value.size > 1 }
    
    // Find similar class names
    val similarGroups = mutableMapOf<String, MutableList<KotlinClass>>()
    allClasses.forEach { class1 ->
        allClasses.forEach { class2 ->
            if (class1 != class2) {
                val similarity = calculateSimilarity(class1.className, class2.className)
                if (similarity > 0.7) {
                    val key = listOf(class1.className, class2.className).sorted().joinToString("-")
                    similarGroups.getOrPut(key) { mutableListOf() }.apply {
                        if (!any { it.fullName == class1.fullName }) add(class1)
                        if (!any { it.fullName == class2.fullName }) add(class2)
                    }
                }
            }
        }
    }
    
    // Output results
    println("=== UNUSED CLASSES ===")
    println("Total: ${unusedClasses.size}")
    unusedClasses.sortedBy { it.filePath }.forEach { kotlinClass ->
        println("\n${kotlinClass.className}")
        println("  File: ${kotlinClass.filePath}")
        println("  Full name: ${kotlinClass.fullName}")
    }
    
    println("\n\n=== DUPLICATE CLASS NAMES ===")
    duplicates.forEach { (name, classes) ->
        println("\nDuplicate: $name (${classes.size} occurrences)")
        classes.forEach { kotlinClass ->
            println("  - ${kotlinClass.filePath}")
        }
    }
    
    println("\n\n=== SIMILAR CLASS NAMES ===")
    similarGroups.values.distinct().forEach { group ->
        println("\nSimilar classes:")
        group.forEach { kotlinClass ->
            println("  - ${kotlinClass.className} (${kotlinClass.filePath})")
        }
    }
    
    // Analyze specific patterns
    println("\n\n=== ANALYSIS BY CATEGORY ===")
    
    // Find backup files
    val backupFiles = allClasses.filter { 
        it.filePath.contains("_backup") || 
        it.filePath.contains(".bak") ||
        it.className.contains("Backup")
    }
    if (backupFiles.isNotEmpty()) {
        println("\nBackup files found:")
        backupFiles.forEach { println("  - ${it.filePath}") }
    }
    
    // Find multiple repository implementations
    val repositories = allClasses.filter { it.className.contains("Repository") }
    val repoGroups = repositories.groupBy { 
        it.className.replace("Impl", "").replace("Enhanced", "").replace("Cached", "")
    }
    println("\nRepository implementations:")
    repoGroups.filter { it.value.size > 1 }.forEach { (base, impls) ->
        println("  $base has ${impls.size} implementations:")
        impls.forEach { println("    - ${it.className} (${it.filePath})") }
    }
    
    // Find multiple service implementations
    val services = allClasses.filter { it.className.contains("Service") }
    val serviceGroups = services.groupBy {
        it.className.replace("Impl", "").replace("V2", "").replace("Resilient", "")
    }
    println("\nService implementations:")
    serviceGroups.filter { it.value.size > 1 }.forEach { (base, impls) ->
        println("  $base has ${impls.size} implementations:")
        impls.forEach { println("    - ${it.className} (${it.filePath})") }
    }
}

fun calculateSimilarity(s1: String, s2: String): Double {
    val longer = if (s1.length > s2.length) s1 else s2
    val shorter = if (s1.length > s2.length) s2 else s1
    
    if (longer.isEmpty()) return 1.0
    
    val editDistance = editDistance(longer, shorter)
    return (longer.length - editDistance).toDouble() / longer.length
}

fun editDistance(s1: String, s2: String): Int {
    val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
    
    for (i in 0..s1.length) dp[i][0] = i
    for (j in 0..s2.length) dp[0][j] = j
    
    for (i in 1..s1.length) {
        for (j in 1..s2.length) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    
    return dp[s1.length][s2.length]
}