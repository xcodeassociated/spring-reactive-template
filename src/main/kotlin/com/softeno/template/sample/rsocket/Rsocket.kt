package com.softeno.template.sample.rsocket

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.reactive.asFlow
import org.apache.commons.logging.LogFactory
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.annotation.ConnectMapping
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration.ofSeconds
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom


@Controller
class StockPricesRSocketController(private val stockService: StockService) {
    private val log = LogFactory.getLog(javaClass)
    private var REQUESTER_MAP: HashMap<String, RSocketRequester> = HashMap()

    @MessageMapping("stock")
    fun prices(symbol: String): Flux<StockPrice> = stockService.streamOfPrices(symbol)

    @MessageMapping("number.channel")
    fun biDirectionalStream(numberFlux: Flux<Long>): Flux<Long>? {
        return numberFlux
            .map { n: Long -> n * n }
            .onErrorReturn(-1L)
    }
    @MessageMapping("stock.single")
    fun singlePrice(symbol: String): Mono<StockPrice> = stockService.getSingle(symbol)

    @MessageMapping("stock.coroutine")
    suspend fun singlePriceCoroutine(symbol: String): StockPrice? =
        stockService.getSingle(symbol).asFlow().firstOrNull()

    @MessageMapping("stock.error")
    fun stockError(symbol: String) {
        throw RuntimeException("exception for symbol: $symbol")
    }

    @MessageExceptionHandler
    fun handle(ex: Exception): Mono<String> {
        log.error("MessageExceptionHandler: $ex")
        return Mono.error(ex)
    }

    @ConnectMapping("client-id")
    fun register(rSocketRequester: RSocketRequester, @Payload clientId: String): Mono<Void> {
        log.info("hello: $clientId")
        rSocketRequester.rsocket()!!
            .onClose()
            .subscribe(
                null, null
            ) {
                log.info("good bye: $clientId")
                REQUESTER_MAP.remove(clientId, rSocketRequester)
            }

        REQUESTER_MAP.put(clientId, rSocketRequester)

        return rSocketRequester.metadata("hello", MimeType.valueOf("message/x.rsocket.routing.v0")).send()
    }

}

@Service
class StockService {
    private val pricesForStock = ConcurrentHashMap<String, Flux<StockPrice>>()
    private val log = LogFactory.getLog(javaClass)

    fun getSingle(symbol: String): Mono<StockPrice> = Mono.just(StockPrice(symbol, randomStockPrice(), LocalDateTime.now()))
        .doOnSubscribe { log.info("New subscription for SINGLE symbol $symbol.") }
        .share()

    fun streamOfPrices(symbol: String): Flux<StockPrice> {
        return pricesForStock.computeIfAbsent(symbol) {
            Flux
                .interval(ofSeconds(1))
                .map { StockPrice(symbol, randomStockPrice(), LocalDateTime.now()) }
                .doOnSubscribe { log.info("New subscription for symbol $symbol.") }
                .share()
        }
    }

    private fun randomStockPrice() = ThreadLocalRandom.current().nextDouble(100.0)
}

data class StockPrice(val symbol: String, val price: Double, val time: LocalDateTime)