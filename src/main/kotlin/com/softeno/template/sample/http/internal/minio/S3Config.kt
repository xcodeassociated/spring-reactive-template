package com.softeno.template.sample.http.internal.minio

import io.minio.MinioClient
import okhttp3.OkHttpClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.concurrent.TimeUnit


@ConfigurationProperties(prefix = "io.min")
@Profile(value = ["!integration"])
data class ExternalMinioConfig
    (val name: String, val secret: String, val url: String, val bucket: String, val folder: String)

@Profile(value = ["!integration"])
@Configuration
class MinioConfig {
    @Bean
    fun generateMinioClient(config: ExternalMinioConfig): MinioClient {
        return try {
            val httpClient: OkHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .readTimeout(30, TimeUnit.MINUTES)
                .build()

            MinioClient.builder()
                .endpoint(config.url)
                .httpClient(httpClient)
                .credentials(config.name, config.secret)
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e.message)
        }
    }
}