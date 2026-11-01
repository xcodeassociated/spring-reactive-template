package com.softeno.template.sample.http.external

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
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

    fun mockPost(wiremock: WireMockServer, data: String = R.data()) {
        wiremock.stubFor(
            post(urlMatching("/sample.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"data": "$data"}""")
                )
        )
    }

    fun mockPut(wiremock: WireMockServer, data: String = R.data()) {
        wiremock.stubFor(
            put(urlMatching("/sample/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"data": "$data"}""")
                )
        )
    }

    fun mockDelete(wiremock: WireMockServer) {
        wiremock.stubFor(delete(urlMatching("/sample/.*")).willReturn(aResponse().withStatus(200)))
    }

    /** Any call to the external service fails -> circuit-breaker fallback paths. */
    fun mockFailure(wiremock: WireMockServer) {
        wiremock.stubFor(get(urlMatching("/sample/.*")).willReturn(aResponse().withStatus(500)))
        wiremock.stubFor(post(urlMatching("/sample.*")).willReturn(aResponse().withStatus(500)))
        wiremock.stubFor(put(urlMatching("/sample/.*")).willReturn(aResponse().withStatus(500)))
        wiremock.stubFor(delete(urlMatching("/sample/.*")).willReturn(aResponse().withStatus(500)))
    }
}
