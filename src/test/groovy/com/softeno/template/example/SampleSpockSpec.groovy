package com.softeno.template.example

import spock.lang.Specification

class SampleSpockSpec extends Specification {

    def setup() {

    }

    def "sample test equals"() {
        given:
        def a = 1
        def b = 1
        when:
        def sum = a + b
        then:
        2 == sum
    }

    def "sample test expect"() {
        def a = 1
        def b = 1

        expect:
        2 == a + b
    }
}
