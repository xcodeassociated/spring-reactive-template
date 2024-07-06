package com.softeno.template

import com.softeno.template.playground.CoroutinePlayground
import com.softeno.template.sample.http.internal.serverevents.UserNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
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
@ConfigurationPropertiesScan("com.softeno")
class SoftenoReactiveMongoApp

fun main(args: Array<String>) {
    Hooks.enableAutomaticContextPropagation()
    runApplication<SoftenoReactiveMongoApp>(*args)
}

@Component
@Profile(value = ["!integration"])
class SpringApplicationReadyEventListener(private val userNotificationService: UserNotificationService) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        logger.info(">> Application Ready")

        runBlocking {
            val subscribingScope = CoroutineScope(SupervisorJob())
            userNotificationService.stream()
                .onEach { logger.info(">> UserNotificationService: $it") }
                .launchIn(subscribingScope)

            logger.info(">> UserNotificationService: Subscribed to stream")
            while (true) { delay(Long.MAX_VALUE) }
        }
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
