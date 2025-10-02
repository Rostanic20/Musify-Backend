package com.musify.core.monitoring

import com.musify.core.config.EnvironmentConfig
import io.micrometer.cloudwatch2.CloudWatchConfig
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import java.time.Duration

/**
 * Configuration for Micrometer metrics
 */
object MicrometerConfig {
    
    fun createMeterRegistry(): MeterRegistry {
        val composite = CompositeMeterRegistry()
        
        // Always add simple registry for local metrics
        composite.add(SimpleMeterRegistry())
        
        // Add Prometheus registry if enabled
        if (EnvironmentConfig.getEnvOrNull("PROMETHEUS_ENABLED")?.toBoolean() == true) {
            composite.add(createPrometheusRegistry())
        }
        
        // Add CloudWatch registry if AWS credentials are available
        if (EnvironmentConfig.getEnvOrNull("AWS_ACCESS_KEY_ID") != null) {
            composite.add(createCloudWatchRegistry())
        }
        
        // Configure common tags
        composite.config().commonTags(
            "application", "musify-backend",
            "environment", EnvironmentConfig.getEnv("ENVIRONMENT", "development"),
            "region", EnvironmentConfig.getEnv("AWS_REGION", "us-east-1")
        )
        
        return composite
    }
    
    private fun createPrometheusRegistry(): PrometheusMeterRegistry {
        val config = object : PrometheusConfig {
            override fun get(key: String): String? = null
            override fun prefix(): String = "musify"
        }
        
        return PrometheusMeterRegistry(config)
    }
    
    private fun createCloudWatchRegistry(): CloudWatchMeterRegistry {
        val config = object : CloudWatchConfig {
            override fun get(key: String): String? = null
            override fun namespace(): String = "Musify/Backend"
            override fun step(): Duration = Duration.ofMinutes(1)
            override fun batchSize(): Int = 20
        }
        
        val cloudWatchClient = CloudWatchAsyncClient.builder()
            .region(Region.of(EnvironmentConfig.getEnv("AWS_REGION", "us-east-1")))
            .build()
        
        return CloudWatchMeterRegistry(config, Clock.SYSTEM, cloudWatchClient)
    }
    
    fun getPrometheusRegistry(meterRegistry: MeterRegistry): PrometheusMeterRegistry? {
        return when (meterRegistry) {
            is CompositeMeterRegistry -> {
                meterRegistry.registries
                    .filterIsInstance<PrometheusMeterRegistry>()
                    .firstOrNull()
            }
            is PrometheusMeterRegistry -> meterRegistry
            else -> null
        }
    }
}