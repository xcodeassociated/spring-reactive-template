package com.softeno.template

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles


@SpringBootTest
@ActiveProfiles("integration")
@EnableConfigurationProperties
@ConfigurationPropertiesScan("com.softeno")
class SoftenoReactiveMongoAppTests(
	val context: ApplicationContext
) {

	@Test
	fun contextLoads() {
		Assertions.assertEquals(context.id, "SoftenoReactiveMongoApp")
	}

}
