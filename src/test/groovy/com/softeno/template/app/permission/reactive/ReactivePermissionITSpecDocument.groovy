package com.softeno.template.app.permission.reactive

import com.softeno.template.BaseAppSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient

class ReactivePermissionITSpecDocument extends BaseAppSpec {

    @Autowired
    WebTestClient webClient

    def "HTTP 200 GET /permissions and no data"() {
        expect:
        webClient.get().uri("/reactive/permissions")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]")
    }


}