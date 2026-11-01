package com.softeno.template.sample.http.external.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.graphql.client.HttpGraphQlClient
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

/** External GraphQL client (demo of outbound calls, pairs with the circuit breaker sample). */
@Profile("!integration") // needs the oauth2 client registrations; no consumer in the test profile
@Configuration
class ExternalGraphqlClientConfig {
    @Bean(value = ["externalGraphQl"])
    fun httpGraphQlClient(
        builder: WebClient.Builder,
        config: ExternalClientConfig,
        authorizedClientManager: ReactiveOAuth2AuthorizedClientManager
    ): HttpGraphQlClient {
        val oauth = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
        oauth.setDefaultClientRegistrationId("keycloak")

        val webClientBuilder = builder.filter(oauth).build()

        return HttpGraphQlClient.builder(webClientBuilder).url(config.graphqlUrl).build()
    }
}
