package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.dto.OpenExternalResponse
import org.example.kalkulationsprogramm.exception.NotFoundException
import org.example.kalkulationsprogramm.service.DateiSpeicherService
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.Principal
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

@RestController
class DateiController(
    private val dateiSpeicherService: DateiSpeicherService,
) {
    private val thumbnailCache: MutableMap<String, ByteArray> = ConcurrentHashMap()

    @GetMapping("/api/images/{dateiname:.+}")
    fun liefereBild(@PathVariable dateiname: String): ResponseEntity<Resource> {
        val resource = dateiSpeicherService.ladeBildAlsResource(dateiname)
        val contentType = bestimmeContentType(resource, dateiname)

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${resource.filename}\"")
            .body(resource)
    }

    @GetMapping("/api/dokumente/{dateiname:.+}")
    fun liefereDokument(
        @PathVariable dateiname: String,
        @RequestParam(required = false) token: String?,
        @RequestParam(required = false, defaultValue = "false") download: Boolean,
        principal: Principal?,
    ): ResponseEntity<*> {
        val dokument =
            try {
                dateiSpeicherService.ladeDokumentMetadaten(dateiname)
            } catch (ex: NotFoundException) {
                return liefereDokumentOhneMetadaten(dateiname)
            }

        log.info(
            "Dokument {} von Benutzer {} am {}",
            dokument.id,
            principal?.name ?: "unbekannt",
            LocalDateTime.now(),
        )

        val lower = dateiname.lowercase(Locale.ROOT)
        val isHiCAD = lower.endsWith(".sza") || lower.endsWith(".tcd")
        val isExcel = lower.endsWith(".xls") ||
            lower.endsWith(".xlsx") ||
            lower.endsWith(".xlsm") ||
            lower.endsWith(".csv") ||
            lower.endsWith(".ods") ||
            lower.endsWith(".xlsb")
        val canOpenExtern = !download &&
            (isHiCAD || (isExcel && dateiSpeicherService.liegtInHicadSpeicher(dokument.gespeicherterDateiname)))

        if (canOpenExtern) {
            val pfad = ensureUncPrefix(dateiSpeicherService.holeNetzwerkPfad(dokument.gespeicherterDateiname))
            val encPath = encodePathForProtocol(pfad)
            val cleanTok = token?.trim()
            val protocolUrl = "openfile://open?path=$encPath" +
                (cleanTok?.let { "&token=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" } ?: "")
            val resp = OpenExternalResponse("openExternal", protocolUrl, cleanTok)
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt")
                .body(resp)
        }

        val resource =
            try {
                dateiSpeicherService.ladeDokumentAlsResource(dokument.gespeicherterDateiname)
            } catch (ex: RuntimeException) {
                if (!dokument.gespeicherterDateiname.equals(dateiname, ignoreCase = true)) {
                    return liefereDokumentOhneMetadaten(dateiname)
                }
                throw ex
            }

        val originalerDateiname = dokument.originalDateiname
        var contentType = dokument.dateityp
        if (contentType.isNullOrBlank() || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType, ignoreCase = true)) {
            contentType = bestimmeContentType(resource, originalerDateiname ?: dateiname)
        }

        val filename = originalerDateiname?.takeIf { it.isNotBlank() } ?: resource.filename
        val inline = MediaType.APPLICATION_PDF_VALUE.equals(contentType, ignoreCase = true) ||
            contentType.lowercase(Locale.ROOT).startsWith("image/")
        val disposition = if (inline) "inline" else "attachment"

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "$disposition; filename=\"$filename\"")
            .body(resource)
    }

    @GetMapping("/api/dokumente/{dateiname:.+}/thumbnail")
    fun liefereThumbnail(@PathVariable dateiname: String): ResponseEntity<ByteArray> {
        thumbnailCache[dateiname]?.let { cached ->
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"thumb_$dateiname\"")
                .body(cached)
        }

        val resource =
            try {
                dateiSpeicherService.ladeDokumentAlsResource(dateiname)
            } catch (ex: RuntimeException) {
                try {
                    dateiSpeicherService.ladeBildAlsResource(dateiname)
                } catch (ex2: RuntimeException) {
                    throw NotFoundException("Dokument nicht gefunden: $dateiname")
                }
            }

        val contentType = bestimmeContentType(resource, dateiname)
        if (!contentType.lowercase(Locale.ROOT).startsWith("image/")) {
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$dateiname\"")
                .body(resourceToBytes(resource))
        }

        try {
            resource.inputStream.use { inputStream ->
                val originalImage = ImageIO.read(inputStream) ?: return fallbackOriginal(resource, dateiname, contentType)
                val origWidth = originalImage.width
                val origHeight = originalImage.height

                if (origWidth <= THUMBNAIL_MAX_SIZE && origHeight <= THUMBNAIL_MAX_SIZE) {
                    val jpegBytes = convertToJpeg(originalImage)
                    thumbnailCache[dateiname] = jpegBytes
                    return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"thumb_$dateiname\"")
                        .body(jpegBytes)
                }

                val scale = minOf(
                    THUMBNAIL_MAX_SIZE.toDouble() / origWidth,
                    THUMBNAIL_MAX_SIZE.toDouble() / origHeight,
                )
                val newWidth = Math.round(origWidth * scale).toInt()
                val newHeight = Math.round(origHeight * scale).toInt()

                val thumbnail = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
                val g2d = thumbnail.createGraphics()
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null)
                g2d.dispose()

                val jpegBytes = convertToJpeg(thumbnail)
                thumbnailCache[dateiname] = jpegBytes

                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"thumb_$dateiname\"")
                    .body(jpegBytes)
            }
        } catch (e: IOException) {
            log.warn("Thumbnail-Erzeugung fehlgeschlagen für {}: {}", dateiname, e.message)
            return fallbackOriginal(resource, dateiname, contentType)
        }
    }

    @Throws(IOException::class)
    private fun convertToJpeg(image: BufferedImage): ByteArray {
        var rgbImage = image
        if (image.type == BufferedImage.TYPE_INT_ARGB || image.colorModel.hasAlpha()) {
            rgbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val g = rgbImage.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, image.width, image.height)
            g.drawImage(image, 0, 0, null)
            g.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(rgbImage, "jpg", baos)
        return baos.toByteArray()
    }

    private fun fallbackOriginal(
        resource: Resource,
        dateiname: String,
        contentType: String,
    ): ResponseEntity<ByteArray> =
        try {
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$dateiname\"")
                .body(resourceToBytes(resource))
        } catch (e: Exception) {
            throw RuntimeException("Fehler beim Lesen der Datei: $dateiname", e)
        }

    private fun resourceToBytes(resource: Resource): ByteArray =
        try {
            resource.inputStream.use { it.readAllBytes() }
        } catch (e: IOException) {
            throw RuntimeException("Fehler beim Lesen der Resource: ${resource.filename}", e)
        }

    private fun liefereDokumentOhneMetadaten(dateiname: String): ResponseEntity<Resource> {
        val resource = dateiSpeicherService.ladeDokumentAlsResource(dateiname)
        val contentType = bestimmeContentType(resource, dateiname)
        val inline = MediaType.APPLICATION_PDF_VALUE.equals(contentType, ignoreCase = true) ||
            contentType.lowercase(Locale.ROOT).startsWith("image/")
        val disposition = if (inline) "inline" else "attachment"

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "$disposition; filename=\"$dateiname\"")
            .body(resource)
    }

    private fun bestimmeContentType(resource: Resource, filename: String): String {
        var contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE
        try {
            val probed = Files.probeContentType(resource.file.toPath())
            if (probed != null) {
                contentType = probed
            }
        } catch (ignored: IOException) {
        }

        if (MediaType.APPLICATION_OCTET_STREAM_VALUE == contentType) {
            val name = filename.lowercase(Locale.ROOT)
            contentType = when {
                name.endsWith(".png") -> MediaType.IMAGE_PNG_VALUE
                name.endsWith(".jpg") || name.endsWith(".jpeg") -> MediaType.IMAGE_JPEG_VALUE
                name.endsWith(".gif") -> MediaType.IMAGE_GIF_VALUE
                name.endsWith(".webp") -> "image/webp"
                name.endsWith(".pdf.html") -> MediaType.APPLICATION_PDF_VALUE
                name.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                name.endsWith(".xls") -> "application/vnd.ms-excel"
                name.endsWith(".xlsm") -> "application/vnd.ms-excel.sheet.macroEnabled.12"
                name.endsWith(".csv") -> "text/csv"
                name.endsWith(".ods") -> "application/vnd.oasis.opendocument.spreadsheet"
                else -> contentType
            }
        }
        return contentType
    }

    private fun ensureUncPrefix(pfad: String?): String? =
        when {
            pfad.isNullOrBlank() -> pfad
            pfad.startsWith("\\\\") -> pfad
            pfad.startsWith("\\") -> "\\$pfad"
            else -> pfad
        }

    private fun encodePathForProtocol(pfad: String?): String? =
        pfad?.let { URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20") }

    private companion object {
        private val log = LoggerFactory.getLogger(DateiController::class.java)
        private const val THUMBNAIL_MAX_SIZE = 300
    }
}
