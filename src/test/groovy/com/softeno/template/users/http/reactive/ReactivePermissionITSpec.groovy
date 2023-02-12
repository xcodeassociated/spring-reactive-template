package com.softeno.template.users.http.reactive

import com.softeno.template.BaseAppSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient

class ReactivePermissionITSpec extends BaseAppSpec {

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