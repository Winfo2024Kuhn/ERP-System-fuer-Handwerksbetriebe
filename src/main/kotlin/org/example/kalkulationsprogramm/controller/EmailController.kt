package org.example.kalkulationsprogramm.controller

import org.example.email.EmailService
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.DokumentFreigabe
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailAttachment
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.example.kalkulationsprogramm.domain.EmailSignature
import org.example.kalkulationsprogramm.domain.FrontendUserProfile
import org.example.kalkulationsprogramm.domain.ProjektDokument
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument
import org.example.kalkulationsprogramm.dto.Email.EmailBeautifyRequest
import org.example.kalkulationsprogramm.dto.Email.EmailBeautifyResponse
import org.example.kalkulationsprogramm.dto.Email.EmailPreviewRequest
import org.example.kalkulationsprogramm.dto.Email.EmailSendRequest
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository
import org.example.kalkulationsprogramm.service.DateiSpeicherService
import org.example.kalkulationsprogramm.service.DokumentFreigabeService
import org.example.kalkulationsprogramm.service.EmailAbsenderService
import org.example.kalkulationsprogramm.service.EmailAiService
import org.example.kalkulationsprogramm.service.EmailSignatureService
import org.example.kalkulationsprogramm.service.FrontendUserProfileService
import org.example.kalkulationsprogramm.service.SystemSettingsService
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.Optional
import java.util.UUID

@RestController
@RequestMapping("/api/email")
class EmailController(
    private val dokumentRepository: ProjektDokumentRepository,
    private val anfrageDokumentRepository: AnfrageDokumentRepository,
    private val anfrageRepository: AnfrageRepository,
    private val emailRepository: EmailRepository,
    private val emailAiService: EmailAiService,
    private val emailSignatureService: EmailSignatureService,
    private val frontendUserProfileService: FrontendUserProfileService,
    private val dateiSpeicherService: DateiSpeicherService,
    private val systemSettingsService: SystemSettingsService,
    private val dokumentFreigabeService: DokumentFreigabeService,
    private val emailAbsenderService: EmailAbsenderService,
) {
    private val log = LoggerFactory.getLogger(EmailController::class.java)

    @Value("\${file.mail-attachment-dir}")
    private lateinit var mailAttachmentDir: String

    @GetMapping("/from-addresses")
    fun getFromAddresses(
        @RequestParam(value = "frontendUserId", required = false) frontendUserId: Long?,
    ): ResponseEntity<List<String>> {
        val aktive = ArrayList(emailAbsenderService.findActiveEmailAddresses())
        if (frontendUserId != null) {
            val userAdresse = frontendUserProfileService.findById(frontendUserId)
                .map { it.emailAbsender }
                .map { it?.emailAdresse }
                .filter { !it.isNullOrBlank() }
                .orElse(null)
            if (userAdresse != null) {
                aktive.removeIf { it.equals(userAdresse, ignoreCase = true) }
                aktive.add(0, userAdresse)
            }
        }
        return ResponseEntity.ok(aktive)
    }

    @PostMapping("/beautify")
    fun beautifyEmail(@RequestBody request: EmailBeautifyRequest?): ResponseEntity<EmailBeautifyResponse> {
        val body = request?.body
        if (body == null || body.trim().isEmpty()) {
            return ResponseEntity.ok(EmailBeautifyResponse(""))
        }
        return try {
            ResponseEntity.ok(EmailBeautifyResponse(emailAiService.beautify(body, request.context)))
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("KI-Formulierung unterbrochen", ex)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
        } catch (ex: Exception) {
            log.warn("KI-Formulierung fehlgeschlagen", ex)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
        }
    }

    @PostMapping("/preview")
    fun previewInvoiceEmail(@RequestBody request: EmailPreviewRequest): ResponseEntity<EmailService.EmailContent> {
        val dokumentId = request.dokumentId ?: return ResponseEntity.notFound().build()
        val doc = dokumentRepository.findById(dokumentId).orElse(null)
        val userName = resolveUserName(request.benutzer, request.frontendUserId)

        if (doc == null) {
            val anfrageDocOpt = anfrageDokumentRepository.findById(dokumentId)
            if (anfrageDocOpt.isEmpty) return ResponseEntity.notFound().build()
            val anfrageDoc = anfrageDocOpt.get()
            val name = anfrageDoc.originalDateiname
            if (name != null) {
                val lowerCase = name.lowercase()
                if (lowerCase.contains("zeichnung") || lowerCase.contains("entwurf")) {
                    var bv = ""
                    if (anfrageDoc is AnfrageGeschaeftsdokument && anfrageDoc.anfrage != null) {
                        bv = anfrageDoc.anfrage!!.bauvorhaben ?: ""
                    }
                    var content = EmailService.buildDrawingEmail(request.anrede, userName, bv)
                    content = appendSignatureForPreview(content, request.frontendUserId, request.benutzer, userName)
                    return ResponseEntity.ok(content)
                }
            }
            return ResponseEntity.notFound().build()
        }

        doc.originalDateiname?.let {
            val lowerCase = it.lowercase()
            if (lowerCase.contains("zeichnung") || lowerCase.contains("entwurf")) {
                val bv = doc.projekt?.bauvorhaben ?: ""
                var content = EmailService.buildDrawingEmail(request.anrede, userName, bv)
                content = appendSignatureForPreview(content, request.frontendUserId, request.benutzer, userName)
                return ResponseEntity.ok(content)
            }
        }

        if (doc !is ProjektGeschaeftsdokument) return ResponseEntity.notFound().build()

        val storedPath = resolveStoredPath(doc.gespeicherterDateiname)
            ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        val path = storedPath.toString()
        val projekt = doc.projekt
        val bauvorhaben = projekt?.bauvorhaben ?: ""
        val projektnummer = projekt?.auftragsnummer ?: ""
        var kundenName = ""
        val kunde = projekt?.kundenId
        if (kunde != null) {
            kundenName = if (!kunde.ansprechspartner.isNullOrBlank()) kunde.ansprechspartner!! else kunde.name ?: ""
        }
        val betrag = doc.bruttoBetrag?.let { NumberFormat.getCurrencyInstance(Locale.GERMANY).format(it) } ?: ""

        if (doc.geschaeftsdokumentart?.lowercase()?.contains("zeichnung") == true) {
            var content = EmailService.buildDrawingEmail(request.anrede, userName, bauvorhaben)
            content = appendSignatureForPreview(content, request.frontendUserId, request.benutzer, userName)
            return ResponseEntity.ok(content)
        }

        var content = if (doc.geschaeftsdokumentart?.lowercase()?.contains("angebot") == true) {
            EmailService.buildOfferEmail(request.anrede, "", bauvorhaben, doc.dokumentid, userName, request.position)
        } else if (doc.geschaeftsdokumentart?.lowercase()?.contains("auftragsbest") == true) {
            EmailService.buildOrderConfirmationEmail(path, request.anrede, kundenName, bauvorhaben, projektnummer, doc.dokumentid, betrag, userName)
        } else {
            val rechnungsdatum = doc.rechnungsdatum ?: LocalDate.now()
            val faelligkeitsdatum = doc.faelligkeitsdatum ?: rechnungsdatum
            EmailService.buildInvoiceEmailWithTypeHints(
                path,
                request.anrede,
                kundenName,
                bauvorhaben,
                projektnummer,
                doc.dokumentid,
                rechnungsdatum,
                faelligkeitsdatum,
                betrag,
                userName,
                doc.geschaeftsdokumentart,
                doc.mahnstufe?.name,
            )
        }

        content = appendSignatureForPreview(content, request.frontendUserId, request.benutzer, userName)
        return ResponseEntity.ok(content)
    }

    @PostMapping("/preview/anfrage")
    fun previewOfferEmail(@RequestBody request: EmailPreviewRequest): ResponseEntity<EmailService.EmailContent> {
        val dokumentId = request.dokumentId ?: return ResponseEntity.notFound().build()
        val doc = anfrageDokumentRepository.findById(dokumentId).orElse(null)
        if (doc !is AnfrageGeschaeftsdokument) return ResponseEntity.notFound().build()
        val anfrage = doc.anfrage
        val userName = resolveUserName(request.benutzer, request.frontendUserId)

        doc.originalDateiname?.let {
            val lowerCase = it.lowercase()
            if (lowerCase.contains("zeichnung") || lowerCase.contains("entwurf")) {
                return ResponseEntity.ok(EmailService.buildDrawingEmail(request.anrede, userName, anfrage?.bauvorhaben ?: ""))
            }
        }

        if (doc.geschaeftsdokumentart?.lowercase()?.contains("zeichnung") == true) {
            return ResponseEntity.ok(EmailService.buildDrawingEmail(request.anrede, userName, request.bauvorhaben))
        }

        val bauvorhaben = request.bauvorhaben ?: anfrage?.bauvorhaben ?: ""
        var content = EmailService.buildOfferEmail(
            request.anrede,
            anfrage?.kunde?.name ?: "",
            bauvorhaben,
            doc.dokumentid,
            userName,
            request.position ?: "",
        )
        content = appendSignatureForPreview(content, request.frontendUserId, request.benutzer, userName)
        return ResponseEntity.ok(content)
    }

    @PostMapping("/send")
    fun sendInvoiceEmail(@RequestBody request: EmailSendRequest): ResponseEntity<Void> {
        val dokumentId = request.dokumentId ?: return ResponseEntity.notFound().build()
        val doc = dokumentRepository.findById(dokumentId).orElse(null) ?: return ResponseEntity.notFound().build()
        val storedPath = resolveStoredPath(doc.gespeicherterDateiname)
            ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        val path = storedPath.toString()
        val userName = resolveUserName(request.benutzer, request.frontendUserId)
        val service = EmailService(
            systemSettingsService.smtpHost,
            systemSettingsService.smtpPort,
            systemSettingsService.smtpUsername,
            systemSettingsService.smtpPassword,
        )

        val messageId: String
        try {
            var finalHtml = request.htmlBody ?: ""
            if (doc is ProjektGeschaeftsdokument) {
                finalHtml = appendFreigabeLinkProjekt(finalHtml, doc, request.recipient)
            }
            var inline: Map<String, File> = HashMap()
            val sigOpt = getSignatureForFrontendUser(request.frontendUserId, request.benutzer)
            if (sigOpt.isPresent) {
                finalHtml = emailSignatureService.ensureSignaturePresentOnce(finalHtml, sigOpt.get(), userName)
                inline = emailSignatureService.buildInlineCidFileMap(sigOpt.get())
            }
            messageId = service.sendEmailAndReturnMessageIdWithInline(
                request.recipient,
                request.cc,
                request.fromAddress,
                request.subject,
                finalHtml,
                inline,
                path,
                doc.originalDateiname,
            )
        } catch (_: Exception) {
            return ResponseEntity.internalServerError().build()
        }

        doc.emailVersandDatum = LocalDate.now()
        dokumentRepository.save(doc)
        persistSentProjektEmail(doc, request, storedPath, messageId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/send/anfrage")
    fun sendOfferEmail(@RequestBody request: EmailSendRequest): ResponseEntity<Void> {
        val dokumentId = request.dokumentId ?: return ResponseEntity.notFound().build()
        val doc = anfrageDokumentRepository.findById(dokumentId).orElse(null)
        if (doc !is AnfrageGeschaeftsdokument) return ResponseEntity.notFound().build()
        val anfrage = doc.anfrage ?: return ResponseEntity.notFound().build()
        val storedPath = resolveStoredPath(doc.gespeicherterDateiname)
            ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        val path = storedPath.toString()
        val userName = resolveUserName(request.benutzer, request.frontendUserId)
        val service = EmailService(
            systemSettingsService.smtpHost,
            systemSettingsService.smtpPort,
            systemSettingsService.smtpUsername,
            systemSettingsService.smtpPassword,
        )

        val messageId: String
        try {
            var finalHtml = appendFreigabeLinkAnfrage(request.htmlBody ?: "", doc, request.recipient)
            var inline: Map<String, File> = HashMap()
            val sigOpt = getSignatureForFrontendUser(request.frontendUserId, request.benutzer)
            if (sigOpt.isPresent) {
                finalHtml = emailSignatureService.ensureSignaturePresentOnce(finalHtml, sigOpt.get(), userName)
                inline = emailSignatureService.buildInlineCidFileMap(sigOpt.get())
            }
            messageId = service.sendEmailAndReturnMessageIdWithInline(
                request.recipient,
                request.cc,
                request.fromAddress,
                request.subject,
                finalHtml,
                inline,
                path,
                doc.originalDateiname,
            )
        } catch (_: Exception) {
            return ResponseEntity.internalServerError().build()
        }

        val recipient = request.recipient
        if (!recipient.isNullOrBlank()) {
            val inKunde = anfrage.kunde?.kundenEmails?.contains(recipient) == true
            val inAnfrage = anfrage.kundenEmails.contains(recipient)
            if (!inKunde && !inAnfrage) {
                anfrage.kundenEmails.add(recipient)
            }
        }
        if (request.bauvorhaben != null) anfrage.bauvorhaben = request.bauvorhaben
        anfrage.emailVersandDatum = LocalDate.now()
        anfrageRepository.save(anfrage)
        doc.emailVersandDatum = LocalDate.now()
        anfrageDokumentRepository.save(doc)
        persistSentAnfrageEmail(doc, request, storedPath, messageId)
        return ResponseEntity.ok().build()
    }

    private fun persistSentProjektEmail(doc: ProjektDokument, request: EmailSendRequest, storedPath: Path, messageId: String) {
        try {
            if (doc is ProjektGeschaeftsdokument) {
                val projekt = doc.projekt
                if (projekt != null) {
                    var email = Email()
                    email.assignToProjekt(projekt)
                    fillSentEmail(email, request, messageId)
                    email = emailRepository.save(email)
                    attachSentDocument(email, doc.originalDateiname, storedPath)
                    emailRepository.save(email)
                }
            }
        } catch (ex: Exception) {
            log.error("Fehler beim Speichern der gesendeten Projekt-Email", ex)
        }
    }

    private fun persistSentAnfrageEmail(doc: AnfrageGeschaeftsdokument, request: EmailSendRequest, storedPath: Path, messageId: String) {
        try {
            val anfrage = doc.anfrage ?: return
            var email = Email()
            email.assignToAnfrage(anfrage)
            fillSentEmail(email, request, messageId)
            email = emailRepository.save(email)
            attachSentDocument(email, doc.originalDateiname, storedPath)
            emailRepository.save(email)
        } catch (ex: Exception) {
            log.error("Fehler beim Speichern der gesendeten Anfrage-Email", ex)
        }
    }

    private fun fillSentEmail(email: Email, request: EmailSendRequest, messageId: String) {
        email.fromAddress = request.fromAddress
        email.extractSenderDomain()
        email.recipient = request.recipient
        email.cc = request.cc
        email.subject = request.subject
        email.htmlBody = request.htmlBody
        email.rawBody = request.htmlBody
        email.body = EmailHtmlSanitizer.htmlToPlainText(request.htmlBody)
        email.sentAt = LocalDateTime.now()
        email.direction = EmailDirection.OUT
        email.isRead = true
        email.messageId = messageId
    }

    private fun attachSentDocument(email: Email, originalDateiname: String?, storedPath: Path) {
        val src = storedPath.toAbsolutePath().normalize()
        val dstDir = Path.of(mailAttachmentDir).toAbsolutePath().normalize()
            .resolve("attachments").resolve(email.id.toString())
        try {
            Files.createDirectories(dstDir)
        } catch (_: Exception) {
        }
        val storedName = "${UUID.randomUUID()}_$originalDateiname"
        val dst = dstDir.resolve(storedName)
        try {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
        }

        val att = EmailAttachment()
        att.email = email
        att.originalFilename = originalDateiname
        att.storedFilename = storedName
        att.sizeBytes = try {
            Files.size(dst)
        } catch (_: Exception) {
            0L
        }
        att.mimeType = Files.probeContentType(dst)
        email.addAttachment(att)
    }

    private fun appendFreigabeLinkAnfrage(html: String, gesDoc: AnfrageGeschaeftsdokument, recipient: String?): String {
        if (!istAngebotOderAB(gesDoc.geschaeftsdokumentart)) return html
        return try {
            val kundeName = gesDoc.anfrage?.kunde?.name
            val freigabe: DokumentFreigabe = dokumentFreigabeService.erstelleFuerAnfrage(gesDoc, kundeName, recipient)
            html + buildFreigabeBlock(dokumentFreigabeService.buildPublicUrl(freigabe), gesDoc.geschaeftsdokumentart)
        } catch (e: Exception) {
            log.warn("Freigabe-Link für Anfrage-Dokument {} konnte nicht erzeugt werden: {}", gesDoc.id, e.message)
            html
        }
    }

    private fun appendFreigabeLinkProjekt(html: String, gesDoc: ProjektGeschaeftsdokument, recipient: String?): String {
        if (!istAngebotOderAB(gesDoc.geschaeftsdokumentart)) return html
        return try {
            val kundeName = gesDoc.projekt?.kundenId?.name
            val freigabe: DokumentFreigabe = dokumentFreigabeService.erstelleFuerProjekt(gesDoc, kundeName, recipient)
            html + buildFreigabeBlock(dokumentFreigabeService.buildPublicUrl(freigabe), gesDoc.geschaeftsdokumentart)
        } catch (e: Exception) {
            log.warn("Freigabe-Link für Projekt-Dokument {} konnte nicht erzeugt werden: {}", gesDoc.id, e.message)
            html
        }
    }

    private fun appendSignatureForPreview(
        content: EmailService.EmailContent,
        frontendUserId: Long?,
        benutzer: String?,
        userName: String?,
    ): EmailService.EmailContent {
        return try {
            val sigOpt = getSignatureForFrontendUser(frontendUserId, benutzer)
            if (sigOpt.isPresent) {
                EmailService.EmailContent(
                    content.subject(),
                    content.htmlBody() + emailSignatureService.renderSignatureHtmlForPreview(sigOpt.get(), userName),
                )
            } else {
                content
            }
        } catch (_: Exception) {
            content
        }
    }

    private fun getSignatureForFrontendUser(frontendUserId: Long?, displayName: String?): Optional<EmailSignature> {
        return try {
            emailSignatureService.getDefaultForFrontendUser(frontendUserId, displayName)
        } catch (_: Exception) {
            Optional.empty()
        }
    }

    private fun resolveUserName(providedName: String?, frontendUserId: Long?): String? {
        if (!providedName.isNullOrBlank()) return providedName
        val userById = frontendUserId?.let { frontendUserProfileService.findById(it) } ?: Optional.empty()
        return userById
            .or { frontendUserProfileService.findByDisplayName(providedName) }
            .map { it.displayName }
            .filter { !it.isNullOrBlank() }
            .orElse(null)
    }

    private fun resolveStoredPath(storedFileName: String?): Path? {
        if (storedFileName.isNullOrBlank()) return null
        return try {
            val resource: Resource = dateiSpeicherService.ladeDokumentAlsResource(storedFileName)
            if (resource.exists()) resource.file.toPath() else null
        } catch (ex: Exception) {
            log.warn("Konnte gespeicherten Pfad für {} nicht ermitteln", storedFileName, ex)
            null
        }
    }

    companion object {
        private fun istAngebotOderAB(art: String?): Boolean {
            if (art == null) return false
            val lower = art.lowercase(Locale.GERMAN)
            return lower.contains("angebot") || lower.contains("auftragsbest")
        }

        private fun buildFreigabeBlock(url: String, dokumentArt: String?): String {
            val art = if (dokumentArt.isNullOrBlank()) "Dokument" else dokumentArt
            return "<div style=\"margin:24px 0;padding:16px 18px;border-left:3px solid #dc2626;background:#fafafa;font-family:Arial,Helvetica,sans-serif;\">" +
                "<p style=\"margin:0 0 6px 0;font-weight:600;color:#1e293b;\">$art digital prüfen und annehmen</p>" +
                "<p style=\"margin:0 0 10px 0;color:#475569;line-height:1.45;\">" +
                "Sie können dieses $art bequem online ansehen und mit einem Klick verbindlich annehmen:" +
                "</p>" +
                "<p style=\"margin:0;\"><a href=\"$url\" style=\"color:#dc2626;font-weight:600;text-decoration:underline;\">" +
                "$url</a></p>" +
                "<p style=\"margin:8px 0 0 0;color:#94a3b8;font-size:13px;\">Der Link ist 14 Tage gültig.</p>" +
                "</div>"
        }
    }
}
