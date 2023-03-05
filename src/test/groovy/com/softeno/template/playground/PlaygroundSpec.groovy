package com.softeno.template.playground

import com.softeno.template.coroutinerunner.PlaygroundCoroutineRunner
import spock.lang.Specification

class PlaygroundSpec extends Specification {

    def "sample coroutine test"() {
        given:
        def result = PlaygroundCoroutineRunner.execHelloCoroutine()

        expect:
        result == "hello"
    }

    def "sample coroutine test with context"() {
        given:
        def result = PlaygroundCoroutineRunner.execHelloCoroutineWithContext()

        expect:
        result == "coroutine!"
    }

    def "sample coroutine with exception"() {
        given:

        when:
        PlaygroundCoroutineRunner.execSomeError()

        then:
        def exception = thrown(RuntimeException)
        exception.message == "Some Error Occurred"
    }
}
