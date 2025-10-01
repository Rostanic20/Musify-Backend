// Set mainClassName property even earlier
ext.set("mainClassName", "com.musify.ApplicationKt")

plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

group = "com.musify"
version = "0.0.1"

application {
    mainClass.set("com.musify.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    fatJar {
        archiveFileName.set("musify-backend-fat.jar")
    }
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

// Override the standard build task to avoid shadow plugin issues
tasks.named("build") {
    // Clear existing dependencies that might include shadow tasks
    setDependsOn(emptyList<String>())
    // Set our own safe dependencies
    dependsOn("compileKotlin", "compileTestKotlin", "test", "jar")
    doFirst {
        logger.info("Using custom build task to avoid shadow plugin issues")
    }
}

// Alternative task for explicit use
tasks.register("buildNoShadow") {
    group = "build"
    description = "Build without shadow plugin issues"
    dependsOn("compileKotlin", "compileTestKotlin", "test", "jar")
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-auth-jwt-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-partial-content-jvm")
    implementation("io.ktor:ktor-server-websockets-jvm")
    implementation("io.ktor:ktor-server-rate-limit-jvm")
    implementation("io.ktor:ktor-server-html-builder-jvm")
    
    // Ktor client (for OAuth providers)
    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-cio-jvm")
    implementation("io.ktor:ktor-client-content-negotiation-jvm")
    implementation("io.ktor:ktor-client-logging-jvm")
    
    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.45.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.45.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.45.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.45.0")
    implementation("com.h2database:h2:2.2.224")
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // Database migrations
    implementation("org.flywaydb:flyway-core:9.22.3")
    
    // Security
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("at.favre.lib:bcrypt:0.10.2")
    
    // 2FA / TOTP
    implementation("dev.turingcomplete:kotlin-onetimepassword:2.4.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
    
    // File handling and audio processing
    implementation("commons-io:commons-io:2.15.1")
    implementation("com.mpatric:mp3agic:0.9.1")
    
    // Caching
    implementation("io.github.reactivecircus.cache4k:cache4k:0.12.0")
    // Redis clients - trying Jedis instead of Lettuce due to compilation issues
    implementation("redis.clients:jedis:5.1.0")
    
    // Email
    implementation("com.sun.mail:javax.mail:1.6.2")
    
    // Payment Processing
    implementation("com.stripe:stripe-java:24.16.0")
    
    // AWS SDK for S3
    implementation("software.amazon.awssdk:s3:2.21.0")
    implementation("software.amazon.awssdk:sts:2.21.0")
    
    // Image processing
    implementation("com.sksamuel.scrimage:scrimage-core:4.0.32")
    implementation("com.sksamuel.scrimage:scrimage-webp:4.0.32")
    
    // Error tracking
    implementation("io.sentry:sentry:6.34.0")
    implementation("io.sentry:sentry-logback:6.34.0")
    
    // Monitoring and Metrics
    implementation("io.micrometer:micrometer-core:1.15.4")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.4")
    implementation("io.micrometer:micrometer-registry-cloudwatch2:1.15.4")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Dependency Injection
    implementation("io.insert-koin:koin-core:3.5.3")
    implementation("io.insert-koin:koin-ktor:3.5.3")
    
    // Validation
    implementation("io.konform:konform:0.4.0")
    
    // Environment variables
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    
    // Testing
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("io.ktor:ktor-client-mock-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.insert-koin:koin-test:3.5.3")
    testImplementation("io.insert-koin:koin-test-junit5:3.5.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}