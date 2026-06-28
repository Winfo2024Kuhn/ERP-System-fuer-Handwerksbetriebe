package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.EmailSignature
import org.example.kalkulationsprogramm.domain.EmailSignatureImage
import org.example.kalkulationsprogramm.service.EmailSignatureService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files

@RestController
@RequestMapping("/api/email/signatures")
class EmailSignatureController(
    private val service: EmailSignatureService,
) {
    @GetMapping
    fun list(): List<EmailSignature> = service.list()

    @GetMapping("/default")
    fun getDefault(
        @RequestParam(name = "frontendUserId", required = false) frontendUserId: Long?,
        @RequestParam(name = "frontendUserName", required = false) frontendUserName: String?,
    ): ResponseEntity<EmailSignature> =
        service.getDefaultForFrontendUser(frontendUserId, frontendUserName)
            .map { ResponseEntity.ok(it) }
            .orElse(ResponseEntity.noContent().build())

    @GetMapping("/system-default")
    fun getSystemDefaultRaw(): ResponseEntity<Map<String, Any>> {
        val signature = service.list().firstOrNull { readBoolean(it, "systemDefault") }
            ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(
            mapOf(
                "signature" to signature,
                "isPlatzhalter" to EmailSignatureService.isPlatzhalter(signature),
            ),
        )
    }

    @PutMapping("/{id}/system-default")
    fun setSystemDefault(@PathVariable id: Long): ResponseEntity<EmailSignature> =
        try {
            ResponseEntity.ok(service.setSystemDefault(id))
        } catch (_: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }

    @PostMapping
    fun save(@RequestBody req: SaveSignatureRequest): ResponseEntity<EmailSignature> {
        val signature = EmailSignature()
        if (req.id != null) writeField(signature, "id", req.id)
        writeField(signature, "name", req.name ?: "Signatur")
        writeField(signature, "html", req.html ?: "")
        val defaultRequested = req.defaultSignature == true
        writeField(signature, "defaultSignature", defaultRequested)
        val saved = service.saveOrUpdate(signature)
        writeField(saved, "defaultSignature", defaultRequested)
        return ResponseEntity.ok(saved)
    }

    @PostMapping(path = ["/{id}/images"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable id: Long,
        @RequestPart("files") files: List<MultipartFile>,
    ): ResponseEntity<List<EmailSignatureImage>> =
        try {
            ResponseEntity.ok(files.map { service.addImage(id, it) })
        } catch (_: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

    @GetMapping("/{id}/preview")
    fun preview(
        @PathVariable id: Long,
        @RequestParam(name = "user", required = false) user: String?,
    ): ResponseEntity<Map<String, String>> {
        val signature = service.list().firstOrNull { readLong(it, "id") == id }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("html" to service.renderSignatureHtmlForPreview(signature, user)))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<EmailSignature> =
        service.findById(id).map { ResponseEntity.ok(it) }.orElse(ResponseEntity.notFound().build())

    @GetMapping("/{id}/images/{imageId}")
    fun image(@PathVariable id: Long, @PathVariable imageId: Long): ResponseEntity<ByteArray> {
        val signature = service.list().firstOrNull { readLong(it, "id") == id }
            ?: return ResponseEntity.notFound().build()
        val images = readField(signature, "images") as? Collection<*> ?: emptyList<Any>()
        val image = images.filterIsInstance<EmailSignatureImage>().firstOrNull { readLong(it, "id") == imageId }
            ?: return ResponseEntity.notFound().build()
        val path = service.resolveImagePath(image).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return try {
            val bytes = Files.readAllBytes(path)
            val contentType = readString(image, "contentType") ?: "application/octet-stream"
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(bytes)
        } catch (_: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> =
        try {
            service.delete(id)
            ResponseEntity.noContent().build()
        } catch (_: Exception) {
            ResponseEntity.notFound().build()
        }

    @DeleteMapping("/{id}/images/{imageId}")
    fun deleteImage(@PathVariable id: Long, @PathVariable imageId: Long): ResponseEntity<Void> =
        try {
            service.deleteImage(id, imageId)
            ResponseEntity.noContent().build()
        } catch (_: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        } catch (_: Exception) {
            ResponseEntity.notFound().build()
        }

    data class SaveSignatureRequest(
        var id: Long? = null,
        var name: String? = null,
        var html: String? = null,
        var defaultSignature: Boolean? = null,
    )

    private fun readString(target: Any?, fieldName: String): String? =
        readField(target, fieldName) as? String

    private fun readLong(target: Any?, fieldName: String): Long? =
        (readField(target, fieldName) as? Number)?.toLong()

    private fun readBoolean(target: Any?, fieldName: String): Boolean =
        readField(target, fieldName) as? Boolean ?: false

    private fun readField(target: Any?, fieldName: String): Any? {
        if (target == null) return null
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }

    private fun writeField(target: Any, fieldName: String, value: Any?) {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
    }
}
