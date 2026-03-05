package com.softeno.template.sample.proto.config

import com.softeno.template.grpc.SampleGrpcServiceGrpcKt
import io.grpc.Channel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory


@Configuration
class GrpcClientConfig(
    private val channelFactory: GrpcChannelFactory,
) {

    @Bean
    fun externalStub(): SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineStub {
        val channel: Channel = channelFactory.createChannel("external")
        return SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineStub(channel)
    }
}
