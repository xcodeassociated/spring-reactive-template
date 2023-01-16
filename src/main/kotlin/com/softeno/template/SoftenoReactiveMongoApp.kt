package com.softeno.template

import com.softeno.template.playground.CoroutinePlayground
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.stereotype.Component


@SpringBootApplication
class SoftenoReactiveMongoApp

fun main(args: Array<String>) {
	runApplication<SoftenoReactiveMongoApp>(*args)
}

@EnableAsync
@Component
class SpringApplicationReadyEventListener {
	private val logger = LoggerFactory.getLogger(this::class.java)

	@EventListener
	fun onApplicationReady(event: ApplicationReadyEvent) {
		logger.info(">> Application Ready")
		// play around with kotlin coroutines
		CoroutinePlayground().run()
	}
}
