package com.softeno.template.app.user.api

import com.softeno.template.sample.http.external.config.ExternalClientConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.client.HttpGraphQlClient
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class GraphqlClientConfig {

    @Bean(value = ["externalGraphQl"])
    fun httpGraphQlClient(
        builder: WebClient.Builder,
        config: ExternalClientConfig
    ): HttpGraphQlClient {
        val webClientBuilder = builder.build()
        return HttpGraphQlClient.builder(webClientBuilder).url(config.graphqlUrl).build()
    }
}