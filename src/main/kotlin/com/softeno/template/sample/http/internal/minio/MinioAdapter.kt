package com.softeno.template.sample.http.internal.minio

import io.minio.*
import org.springframework.context.annotation.Profile
import org.springframework.core.io.InputStreamResource
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.io.InputStream

data class UploadResponse(val versionId: String, val objectId: String, val bucket: String)

@Profile(value = ["!integration"])
@Component
class MinioAdapter(private val minioClient: MinioClient, private val config: ExternalMinioConfig) {
    fun uploadFile(file: Mono<FilePart>): Mono<UploadResponse> {
        return file.publishOn(Schedulers.boundedElastic()).map { multipartFile ->
            val temp = File.createTempFile(multipartFile.filename(), null)
                .also { it.canRead() }
                .also { it.canWrite() }
            Pair(multipartFile, temp)
        }.flatMap {
            Mono.just(it.first).zipWith(Mono.just(it.second)).flatMap { tuple ->
                tuple.t1.transferTo(tuple.t2).thenReturn {
                    val uploadObjectArgs: UploadObjectArgs = UploadObjectArgs.builder()
                        .bucket(config.bucket)
                        .`object`(tuple.t1.filename())
                        .filename(tuple.t2.absolutePath)
                        .build()
                    val response: ObjectWriteResponse = minioClient.uploadObject(uploadObjectArgs)
                    tuple.t2.delete()
                    UploadResponse(
                        versionId = response.versionId() ?: "",
                        objectId = response.`object`(),
                        bucket = response.bucket()
                    )
                }.map { it() }
            }
        }
    }

    fun download(name: String): Mono<InputStreamResource> {
        return Mono.fromCallable {
            val response: InputStream =
                minioClient.getObject(GetObjectArgs.builder().bucket(config.bucket).`object`(name).build())
            InputStreamResource(response)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    fun remove(name: String) {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(config.bucket).`object`(name).build())
    }

}