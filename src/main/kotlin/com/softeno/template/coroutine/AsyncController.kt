package com.softeno.template.coroutine

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.apache.commons.logging.LogFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture


@RestController
@RequestMapping("/async")
@Validated
class AsyncController(private val asyncService: AsyncService) {
    private val log = LogFactory.getLog(javaClass)

    @GetMapping("/result")
    suspend fun asyncResultHandler(): String? {
        log.info("[async]: triggering async method with result ...")

        val resultFuture: CompletableFuture<String> = asyncService.asyncMethodWithReturnType("test", 5_000)
        val result: String? = Mono.fromFuture(resultFuture).awaitSingleOrNull()

        log.info("[async]: finished with result: $result")
        return result
    }

    @GetMapping("/void")
    fun asyncVoidHandler() {
        log.info("[async]: triggering async method without result ...")
        asyncService.asyncMethodVoid("test", 5_000)
    }

    @GetMapping("/fail")
    suspend fun asyncResultWithFailHandler(): String? {
        log.info("[async]: triggering async method with result and fail ...")

        val resultFuture: CompletableFuture<String> = asyncService.asyncMethodFail("fail", 2_000)
        return Mono.fromFuture(resultFuture)
            // note: in case of spring async the webflux exception handler will not be invoked since the thread pool does not belong to webflux context;
            //       exception has to be handled inside monad of Mono (proof: look at logger at thread name)
            .doOnError {
                log.error("[async]: error handled: ${it.message}")
                // clean up here ...
            }
            .onErrorReturn("ERROR")
            .awaitSingleOrNull()
    }

    @GetMapping("/void-fail")
    fun asyncVoidFailHandler() {
        log.info("[async]: triggering async method without result and with fail ...")
        asyncService.asyncMethodVoidFail("void-fail", 2_000)
    }

}

@Service
class AsyncService {
    private val log = LogFactory.getLog(javaClass)

    @Async(value = "asyncExecutor")
    fun asyncMethodWithReturnType(input: String, delay: Long): CompletableFuture<String> {
        log.info("[async]: async method invoked")
        try {
            Thread.sleep(delay)
            val result = "FUTURE_DONE: $input"
            log.info("[async]: async method finished with result: $result")
            return CompletableFuture.completedFuture(result)
        } catch (e: InterruptedException) {
            // ...
        }
        return CompletableFuture.completedFuture(null)
    }

    @Async(value = "asyncExecutor")
    fun asyncMethodVoid(input: String, delay: Long) {
        log.info("[async]: async void method invoked")
        try {
            Thread.sleep(delay)
            val result = "VOID_DONE: $input"
            log.info("[async]: async void method finished with result: $result")
        } catch (e: InterruptedException) {
            // ...
        }
    }

    @Async(value = "asyncExecutor")
    fun asyncMethodFail(input: String, delay: Long): CompletableFuture<String> {
        log.info("[async]: async void method invoked")
        try {
            Thread.sleep(delay)
            log.error("[async]: Error occurred during async process...")
            throw RuntimeException("ASYNC_METHOD_EXCEPTION")
        } catch (e: InterruptedException) {
            // ...
        }
        return CompletableFuture.completedFuture(null)
    }

    @Async(value = "asyncExecutor")
    fun asyncMethodVoidFail(input: String, delay: Long) {
        log.info("[async]: async void method invoked")
        try {
            Thread.sleep(delay)
            log.error("[async]: Error occurred during async void process...")
            throw RuntimeException("ASYNC_METHOD_EXCEPTION")
        } catch (e: InterruptedException) {
            // ...
        }
    }
}

