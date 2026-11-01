package com.softeno.template.sample.http.external

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.softeno.template.testsupport.R

/**
 * WireMock stubs for the external service demo.
 * Lives with its context (`sample/http/external`).
 */
interface ExternalApiAbility {

    fun mockGetId(wiremock: WireMockServer, data: String = R.data()) {
        wiremock.stubFor(
            get(urlMatching("/sample/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"data": "$data"}""")
                )
        )
    }
}
