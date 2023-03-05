@file:JvmName("PlaygroundCoroutineRunner")
package com.softeno.template.coroutinerunner


import com.softeno.template.playground.helloCoroutine
import com.softeno.template.playground.helloCoroutineWithContext
import com.softeno.template.playground.someError
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

fun execHelloCoroutine(): String {
    return runBlocking {
        helloCoroutine()
    }
}

fun execHelloCoroutineWithContext(): String {
    return runBlocking {
        helloCoroutineWithContext()
    }
}

fun execSomeError() {
    runBlocking {
        someError()
    }
}

