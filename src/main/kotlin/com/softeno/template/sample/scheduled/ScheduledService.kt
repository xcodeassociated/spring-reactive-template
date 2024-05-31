package com.softeno.template.sample.scheduled

import com.softeno.template.sample.http.internal.async.AsyncService
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.Executor

@Profile(value = ["!integration"])
@Service
class ScheduledService(
    @Qualifier(value = "scheduledExecutor") private val executor: Executor,
    private val syncService: AsyncService
) {
    private val log = LogFactory.getLog(javaClass)

    @Scheduled(fixedDelay = 60_000)
    fun periodicTaskDelay() {
        // fixedDelay: specifically controls the next execution time when the last execution finishes.
        log.info("[scheduled]: periodic task delay start")
        // note: inplace Runnable
        executor.execute { syncService.asyncMethodVoid("fixedDelay", 90_000) }
    }

    @Scheduled(fixedRate = 60_000)
    fun periodicTaskRate() {
        // fixedRate: makes Spring run the task on periodic intervals even if the last invocation may still be running.
        log.info("[scheduled]: periodic task rate start")
        executor.execute { syncService.asyncMethodVoid("fixedRate", 90_000) }

    }

    // cron: every 10m
    @Scheduled(cron = "0 */10 * * * *")
    fun periodicTaskCron() {
        log.info("[scheduled]: periodic task cron start")
        executor.execute { syncService.asyncMethodVoid("cron", 30_000) }
    }

    @Scheduled(cron = "0 */10 * * * *")
    fun periodicTaskWithFailCron() {
        log.info("[scheduled]: periodic task cron with fail start")
        executor.execute { syncService.asyncMethodVoidFail("cron-fail", 30_000) }
    }
}