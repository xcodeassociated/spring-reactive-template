package com.softeno.template


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import spock.lang.Specification

@SpringBootTest(classes = SoftenoReactiveMongoApp,
        properties = "application.environment=integration",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties
@ConfigurationPropertiesScan("com.softeno")
class SoftenoReactiveMongoAppSpec extends Specification {

    @Autowired
    ApplicationContext context

    def "contextLoads"() {
        expect:
        context.id == "SoftenoReactiveMongoApp"
    }

}
