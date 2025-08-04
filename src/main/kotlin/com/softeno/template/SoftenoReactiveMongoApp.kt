package com.softeno.template

import com.softeno.template.playground.CoroutinePlayground
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import reactor.core.publisher.Hooks


@SpringBootApplication
@EnableConfigurationProperties
@ConfigurationPropertiesScan
class SoftenoReactiveMongoApp

fun main(args: Array<String>) {
    Hooks.enableAutomaticContextPropagation()
    runApplication<SoftenoReactiveMongoApp>(*args)
}

@Component
@Profile(value = ["!integration"])
class SpringApplicationReadyEventListener {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        logger.info("Application Ready")
    }
}

@Component
@Profile("playgroud")
class SpringApplicationReadyEventListenerPlayground {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener
    fun onApplicationReady(event: ApplicationReadyEvent) {
        logger.info(">> Application Ready")
        // play around with kotlin coroutines
        CoroutinePlayground().run()
    }
}
