package com.softeno.template.sample.http.internal.minio

import org.springframework.context.annotation.Profile
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.util.MimeTypeUtils
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono


@Profile(value = ["!integration"])
@RestController
@RequestMapping("/minio")
@Validated
class FileController(private val adapter: MinioAdapter) {

    @RequestMapping(
        path = ["/upload"],
        method = [RequestMethod.POST],
        produces = [MimeTypeUtils.APPLICATION_JSON_VALUE],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun upload(
        @RequestPart(value = "file", required = true) files: Mono<FilePart>
    ): Mono<UploadResponse> = adapter.uploadFile(files)

    @GetMapping("/{file}")
    fun download(
        @PathVariable(value = "file") file: String
    ): ResponseEntity<Mono<InputStreamResource>> =
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$file")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE).body(adapter.download(file))


    @DeleteMapping("/{file}")
    fun remove(@PathVariable(value = "file") file: String) = adapter.remove(file)

}