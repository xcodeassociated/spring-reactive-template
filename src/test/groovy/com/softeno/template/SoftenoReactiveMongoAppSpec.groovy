package com.softeno.template

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

class SoftenoReactiveMongoAppSpec extends BaseAppSpec {

    @Autowired
    ApplicationContext context

    def "contextLoads"() {
        expect:
        context.id == "SoftenoReactiveMongoApp"
    }

    def "container is running"() {
        expect:
        mongoDBContainer.isRunning()
    }

}
