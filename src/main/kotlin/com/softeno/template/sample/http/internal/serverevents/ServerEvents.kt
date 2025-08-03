package com.softeno.template.sample.http.internal.serverevents

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/coroutine/currency-rate")
class CurrencyRateController(val currencyRateService: CurrencyRateService) {

    @GetMapping("/current", produces = ["text/event-stream"])
    suspend fun currentRates() = currencyRateService.currentRates()
}

enum class CURRENCY {
    USD, EUR, GBP, PLN
}

data class CurrencyRate(val currency: CURRENCY, val value: Double)

@Service
class CurrencyRateService {

    suspend fun currentRates(): Flow<List<CurrencyRate>> = flow {
        while (true) {
            val rates: List<CurrencyRate> = CURRENCY.entries.map {
                CurrencyRate(it, Math.random())
            }
            emit(rates)

            val delay = (100..2000).random()
            delay(delay.toLong())
        }
    }
}