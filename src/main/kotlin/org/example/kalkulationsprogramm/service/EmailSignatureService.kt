package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.EmailSignature
import org.example.kalkulationsprogramm.domain.EmailSignatureImage
import org.example.kalkulationsprogramm.repository.EmailSignatureImageRepository
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository
import org.example.kalkulationsprogramm.util.InlineAttachmentUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.function.Function
import java.util.function.Predicate
import java.util.regex.Pattern

@Service
class EmailSignatureService(
    private val signatureRepository: EmailSignatureRepository,
    private val imageRepository: EmailSignatureImageRepository,
    private val frontendUserProfileRepository: FrontendUserProfileRepository,
) {
    @Value("\${file.image-upload-dir}")
    private lateinit var imageUploadDir: String

    @Transactional(readOnly = true)
    fun list(): List<EmailSignature> {
        val signatures = signatureRepository.findAllByOrderByUpdatedAtDesc()
        signatures.forEach { it.images?.size }
        return signatures
    }

    @Transactional(readOnly = true)
    fun getDefaultForFrontendUser(profileId: Long): Optional<EmailSignature> =
        getDefaultForFrontendUser(profileId, null)

    @Transactional(readOnly = true)
    fun getSystemDefaultSignature(): Optional<EmailSignature> =
        signatureRepository.findFirstByIsSystemDefaultTrue()
            .filter { !isPlatzhalter(it) }
            .map {
                it.images?.size
                it
            }

    @Transactional(readOnly = true)
    fun appendSystemSignatureIfConfigured(htmlBody: String): String =
        getSystemDefaultSignature()
            .map { ensureSignaturePresentOnce(htmlBody, it, null) }
            .orElse(htmlBody)

    @Transactional
    fun setSystemDefault(signatureId: Long): EmailSignature {
        var sig = signatureRepository.findById(signatureId)
            .orElseThrow { IllegalArgumentException("Signatur nicht gefunden: $signatureId") }
        signatureRepository.clearSystemDefaultExcept(signatureId)
        if (!sig.isSystemDefault) {
            sig.isSystemDefault = true
            sig = signatureRepository.save(sig)
        }
        return sig
    }

    @Transactional(readOnly = true)
    fun getDefaultForFrontendUser(
        profileId: Long?,
        frontendUserDisplayName: String?,
    ): Optional<EmailSignature> {
        var profileOpt =
            if (profileId != null) {
                frontendUserProfileRepository.findById(profileId)
            } else {
                Optional.empty()
            }

        if (profileOpt.isEmpty && frontendUserDisplayName != null) {
            val normalized = frontendUserDisplayName.trim()
            if (normalized.isNotEmpty()) {
                profileOpt = frontendUserProfileRepository.findByDisplayNameIgnoreCase(normalized)
            }
        }

        return profileOpt
            .map { it.defaultSignature }
            .map {
                it?.images?.size
                it
            }
    }

    @Transactional
    fun saveOrUpdate(sig: EmailSignature): EmailSignature {
        val target =
            if (sig.id != null) {
                signatureRepository.findById(sig.id).orElseThrow().apply {
                    name = sig.name
                    html = sig.html
                }
            } else {
                sig
        }
        val saved = signatureRepository.save(target)
        saved.defaultSignature = sig.isDefaultSignature()
        return saved
    }

    @Transactional
    @Throws(IOException::class)
    fun addImage(signatureId: Long, file: MultipartFile): EmailSignatureImage {
        val sig = signatureRepository.findById(signatureId).orElseThrow()
        val baseDir = Path.of(imageUploadDir).toAbsolutePath().normalize()
            .resolve("signatures")
            .resolve(signatureId.toString())
        Files.createDirectories(baseDir)

        val original = Path.of(file.originalFilename ?: "image").fileName.toString()
        val stored = "${UUID.randomUUID()}_$original"
        val dst = baseDir.resolve(stored).normalize()
        if (!dst.startsWith(baseDir)) {
            throw SecurityException("Ungültiger Dateipfad: Verzeichnistraversal erkannt")
        }

        file.transferTo(dst)
        var img = EmailSignatureImage().apply {
            signature = sig
            cid = "sig-${UUID.randomUUID()}"
            originalFilename = original
            storedFilename = stored
            contentType = file.contentType ?: Files.probeContentType(dst)
            sizeBytes = Files.size(dst)
            sortOrder = sig.images?.size ?: 0
        }
        img = imageRepository.save(img)
        sig.images.add(img)
        signatureRepository.save(sig)
        return img
    }

    fun resolveImagePath(img: EmailSignatureImage?): Optional<Path> {
        if (img == null) {
            return Optional.empty()
        }
        val baseDir = Path.of(imageUploadDir).toAbsolutePath().normalize()
            .resolve("signatures")
            .resolve(img.signature?.id.toString())
        val path = baseDir.resolve(img.storedFilename)
        return if (Files.exists(path)) Optional.of(path) else Optional.empty()
    }

    fun findById(id: Long): Optional<EmailSignature> =
        signatureRepository.findById(id)

    @Transactional
    fun delete(id: Long) {
        val sig = signatureRepository.findById(id).orElseThrow()
        val imgs = imageRepository.findBySignatureIdOrderBySortOrderAsc(id)
        for (img in imgs) {
            runCatching {
                resolveImagePath(img).ifPresent { path ->
                    runCatching { Files.deleteIfExists(path) }
                }
            }
            imageRepository.delete(img)
        }
        signatureRepository.delete(sig)
    }

    @Transactional
    fun deleteImage(signatureId: Long, imageId: Long) {
        val img = imageRepository.findById(imageId).orElseThrow()
        val signature = img.signature
        if (signature == null || signature.id != signatureId) {
            throw IllegalArgumentException("Image does not belong to signature")
        }
        runCatching {
            resolveImagePath(img).ifPresent { path ->
                runCatching { Files.deleteIfExists(path) }
            }
        }
        imageRepository.delete(img)
    }

    fun renderSignatureHtmlForPreview(sig: EmailSignature, userName: String?): String {
        val html = applyVariables(sig.html, userName)
        val imgs = imageRepository.findBySignatureIdOrderBySortOrderAsc(sig.id)
        val rewritten = InlineAttachmentUtil.rewriteCidSources(
            html,
            imgs,
            Predicate { true },
            Function { it.cid.orEmpty() },
            Function { img -> "/api/email/signatures/${sig.id}/images/${img.id}" },
        )
        return ensureSignatureWrapper(rewritten, sig)
    }

    fun renderSignatureHtmlForEmail(sig: EmailSignature, userName: String?): String =
        ensureSignatureWrapper(applyVariables(sig.html, userName), sig)

    fun renderSignatureContent(sig: EmailSignature, userName: String?): String =
        applyVariables(sig.html, userName)

    fun ensureSignaturePresentOnce(html: String?, signature: EmailSignature, userName: String?): String {
        val base = html ?: ""
        val signatureHtml = renderSignatureHtmlForEmail(signature, userName)
        if (signatureHtml.isBlank()) {
            return base
        }

        val signatureContent = renderSignatureContent(signature, userName).trim()
        var cleaned = stripTrailingSignatureVariant(base, signatureHtml.trim())
        if (signatureContent.isNotEmpty()) {
            cleaned = stripTrailingSignatureVariant(cleaned, signatureContent)
        }
        if (!containsSignatureMarker(cleaned)) {
            cleaned += signatureHtml
        }
        return cleaned
    }

    fun containsSignatureMarker(html: String?): Boolean =
        html != null && SIGNATURE_CLASS_PATTERN.matcher(html).find()

    fun buildInlineCidFileMap(sig: EmailSignature): Map<String, File> {
        val map = LinkedHashMap<String, File>()
        val html = sig.html ?: ""
        val hLower = html.lowercase(Locale.ROOT)
        val imgs = imageRepository.findBySignatureIdOrderBySortOrderAsc(sig.id)
        for (img in imgs) {
            val cid = img.cid ?: ""
            if (cid.isNotBlank() && hLower.contains("cid:$cid".lowercase(Locale.ROOT))) {
                resolveImagePath(img).ifPresent { path -> map[cid] = path.toFile() }
            }
        }
        return map
    }

    private fun applyVariables(html: String?, userName: String?): String {
        var out = html ?: return ""
        if (userName != null) {
            out = out.replace("{user}", userName).replace("{USER}", userName)
        }
        return out
    }

    private fun ensureSignatureWrapper(html: String?, sig: EmailSignature?): String {
        val content = html?.trim().orEmpty()
        if (content.isEmpty()) {
            return ""
        }
        if (SIGNATURE_CLASS_PATTERN.matcher(content).find()) {
            return content
        }
        val idAttr = if (sig?.id != null) " data-signature-id=\"${sig.id}\"" else ""
        return "<div class=\"email-signature\"$idAttr>$content</div>"
    }

    private fun stripTrailingSignatureVariant(html: String?, variant: String?): String {
        if (html == null || variant.isNullOrEmpty()) {
            return html ?: ""
        }
        var working = html
        while (true) {
            val trimmedWorking = rtrim(working)
            if (trimmedWorking.endsWith(variant)) {
                working = trimmedWorking.substring(0, trimmedWorking.length - variant.length)
            } else {
                break
            }
        }
        return working
    }

    private fun rtrim(value: String?): String {
        if (value == null) {
            return ""
        }
        var end = value.length
        while (end > 0 && value[end - 1].isWhitespace()) {
            end--
        }
        return value.substring(0, end)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailSignatureService::class.java)
        private val SIGNATURE_CLASS_PATTERN: Pattern = Pattern.compile(
            "class\\s*=\\s*(\\\"[^\\\"]*email-signature[^\\\"]*\\\"|'[^']*email-signature[^']*')",
            Pattern.CASE_INSENSITIVE,
        )

        @JvmStatic
        fun isPlatzhalter(sig: EmailSignature?): Boolean {
            val html = sig?.html ?: return true
            return html.contains("data-system-placeholder=\"1\"") ||
                html.contains("data-system-placeholder='1'")
        }
    }
}
