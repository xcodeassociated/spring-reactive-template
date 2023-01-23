package com.softeno.template.event

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import reactor.core.publisher.FluxSink
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.Consumer

data class AppEvent(val source: String) : ApplicationEvent(source)

@Component
class SampleApplicationEventPublisher(@Qualifier("websocketExecutor") executor: Executor) : ApplicationListener<AppEvent>, Consumer<FluxSink<AppEvent>> {
    private val executor: Executor
    private val queue: BlockingQueue<AppEvent> = LinkedBlockingQueue()

    init {
        this.executor = executor
    }

    override fun onApplicationEvent(event: AppEvent) {
        queue.offer(event)
    }

    override fun accept(sink: FluxSink<AppEvent>) {
        executor.execute {
            while (true) {
                val event: AppEvent = queue.take()
                sink.next(event)
            }
        }
    }
}