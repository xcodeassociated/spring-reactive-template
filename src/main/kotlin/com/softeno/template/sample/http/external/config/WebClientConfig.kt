package com.softeno.template.sample.http.external.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

@ConfigurationProperties(prefix = "com.softeno.external")
data class ExternalClientConfig(val url: String, val name: String, val graphqlUrl: String)

@Configuration
class WebClientConfig {

    @Bean
    fun authorizedClientManager(
        clientRegistrationRepository: ReactiveClientRegistrationRepository,
        authorizedClientService: ReactiveOAuth2AuthorizedClientService
    ): ReactiveOAuth2AuthorizedClientManager {
        val authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .build()
        val authorizedClientManager = AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
            clientRegistrationRepository,
            authorizedClientService
        )
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
        return authorizedClientManager
    }

    @Bean(value = ["external"])
    fun buildWebClient(
        config: ExternalClientConfig,
        authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
        builder: WebClient.Builder
    ): WebClient {
        val oauth = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
        oauth.setDefaultClientRegistrationId("keycloak")

        return builder
            .filter(oauth)
            .baseUrl(config.url)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}