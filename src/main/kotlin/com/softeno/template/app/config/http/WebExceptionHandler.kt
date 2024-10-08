package com.softeno.template.app.config.http

import com.softeno.template.sample.http.external.coroutine.ExternalServiceException
import org.apache.commons.logging.LogFactory
import org.springframework.boot.autoconfigure.web.WebProperties
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.web.reactive.error.ErrorAttributes
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.*
import reactor.core.publisher.Mono


@Configuration
class ResourceWebPropertiesConfig {
    @Bean
    fun resources(): WebProperties.Resources {
        return WebProperties.Resources()
    }
}

@Component
@Order(-2)
class GlobalErrorWebExceptionHandler(
    val errorAttributes: ErrorAttributes,
    resources: WebProperties.Resources,
    applicationContext: ApplicationContext,
    configurer: ServerCodecConfigurer
) : AbstractErrorWebExceptionHandler(
    errorAttributes, resources, applicationContext
) {
    init {
        this.setMessageWriters(configurer.writers)
        this.setMessageReaders(configurer.readers)
    }

    private val log = LogFactory.getLog(javaClass)

    override fun getRoutingFunction(errorAttributes: ErrorAttributes?): RouterFunction<ServerResponse?> {
        return RouterFunctions.route(
            RequestPredicates.all()
        ) { request: ServerRequest -> renderErrorResponse(request) }
    }

    private fun getCustomErrorAttributes(request: ServerRequest, includeStackTrace: Boolean): Map<String, Any> {
        val options: ErrorAttributeOptions = if (includeStackTrace) {
            ErrorAttributeOptions.of(ErrorAttributeOptions.Include.STACK_TRACE)
        } else {
            ErrorAttributeOptions.defaults()
        }
        val errorAttributes: MutableMap<String, Any> = this.getErrorAttributes(request, options)
        val error = getError(request)
        log.error("[exception handler]: Handling exception: $error")

        var httpStatus: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR // note: can be taken from custom exception
        errorAttributes["errorCode"] = "E000" // note: custom error code from exception

        if (error is ExternalServiceException) {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE
            errorAttributes["errorCode"] = "E100"
        } else { // note: generic RuntimeException handler
            // ...
        }

        errorAttributes["message"] = error.message ?: "" // note: custom message
        errorAttributes["status"] = httpStatus.value()

        errorAttributes.remove("trace") // note: trace (stacktrace) omitted, can be also configured by: `includeStackTrace = false`

        val correspondentStatus = HttpStatus.valueOf(httpStatus.value())
        errorAttributes["error"] = correspondentStatus.reasonPhrase

        return errorAttributes
    }

    private fun renderErrorResponse(request: ServerRequest): Mono<ServerResponse?> {
        val errorPropertiesMap = getCustomErrorAttributes(request, includeStackTrace = true)

        log.warn(
            "Rendering response for exception of error code: ${errorPropertiesMap["errorCode"]} " +
                    "and type: ${errorPropertiesMap["error"]} with properties: $errorPropertiesMap"
        )

        return ServerResponse.status(HttpStatus.valueOf(errorPropertiesMap["status"] as Int))
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(errorPropertiesMap))
    }
}

