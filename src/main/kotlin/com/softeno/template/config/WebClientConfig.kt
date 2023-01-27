package com.softeno.template.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@ConfigurationProperties(prefix = "com.softeno.external")
@ConstructorBinding
data class ExternalClientConfig(val url: String)

@Configuration
class WebClientConfig {
    @Bean(value = ["external"])
    fun buildWebClient(config: ExternalClientConfig): WebClient {
        return WebClient.builder()
            .baseUrl(config.url)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}