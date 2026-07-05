package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailAttachment
import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.regex.Pattern

@Service
class EmailAttachmentProcessingService(
    private val emailRepository: EmailRepository,
    private val emailAttachmentRepository: EmailAttachmentRepository,
    private val lieferantDokumentRepository: LieferantDokumentRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val lieferantGeschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository,
    private val geminiAnalyseService: GeminiDokumentAnalyseService,
    private val standardKostenstelleAutoAssigner: LieferantStandardKostenstelleAutoAssigner,
) {
    @Autowired
    @Lazy
    private var self: EmailAttachmentProcessingService? = null

    @Value("\${file.mail-attachment-dir:uploads/email}")
    private lateinit var attachmentDir: String

    @Transactional
    fun processLieferantAttachments(email: Email): Int {
        val freshEmail = emailRepository.findById(email.id).orElse(null)
        if (freshEmail == null || freshEmail.lieferant == null) {
            log.warn("Email {} hat keine Lieferant-Zuordnung", email.id)
            return 0
        }

        val lieferant = requireNotNull(freshEmail.lieferant)
        val attachments = freshEmail.attachments
        if (attachments == null || attachments.isEmpty()) return 0

        var erstellt = 0
        val toProcess = attachments
            .filter { isProcessableAttachment(it) && it.aiProcessed != true }

        val pdfCount = toProcess.count { isPdf(it) }.toLong()
        val xmlCount = toProcess.count { isXml(it) }.toLong()
        val consumed = HashSet<Long?>()

        for (xml in toProcess) {
            if (!isXml(xml) || consumed.contains(xml.id)) continue
            val pdf = findMatchingPdf(xml, toProcess, consumed, pdfCount, xmlCount) ?: continue
            try {
                log.info(
                    "Paare PDF '{}' (Anzeige) mit XML '{}' (Metadaten), Email-ID: {}",
                    pdf.originalFilename,
                    xml.originalFilename,
                    email.id,
                )
                if (processAttachment(pdf, xml, requireNotNull(lieferant.id))) {
                    erstellt++
                }
            } catch (ex: Exception) {
                log.error("Fehler beim Paaren von PDF {} mit XML {}: {}", pdf.id, xml.id, ex.message)
            }
            consumed.add(xml.id)
            consumed.add(pdf.id)
        }

        for (attachment in toProcess) {
            if (consumed.contains(attachment.id)) continue
            try {
                log.info("Starte Dokumentanalyse fuer Attachment: {} (Email-ID: {})", attachment.originalFilename, email.id)
                val success = processAttachment(attachment, attachment, requireNotNull(lieferant.id))
                log.info("Dokumentanalyse abgeschlossen fuer {}: Erfolg={}", attachment.originalFilename, success)
                if (success) erstellt++
            } catch (ex: Exception) {
                log.error("Fehler bei Verarbeitung von Attachment {}: {}", attachment.id, ex.message)
            }
            consumed.add(attachment.id)
        }

        return erstellt
    }

    @Transactional
    fun backfillXmlDokumenteAufPdf(): Int {
        val xmlDocs = lieferantDokumentRepository.findMitXmlAnzeigedatei()
        var umgestellt = 0

        for (doc in xmlDocs) {
            try {
                val verknuepft = emailAttachmentRepository.findByLieferantDokumentId(doc.id)
                val xmlAtt = verknuepft.firstOrNull { isXml(it) }
                if (xmlAtt == null || xmlAtt.email == null) {
                    log.info("Backfill XML->PDF: kein XML-Attachment/Email fuer Dokument {}", doc.id)
                    continue
                }

                val pdf = findePdfImSelbenEmail(xmlAtt, doc)
                if (pdf == null) {
                    log.info("Backfill XML->PDF: kein passendes PDF fuer Dokument {} (XML '{}')", doc.id, xmlAtt.originalFilename)
                    continue
                }

                doc.gespeicherterDateiname = pdf.storedFilename
                doc.originalDateiname = pdf.originalFilename
                lieferantDokumentRepository.save(doc)

                if (pdf.lieferantDokument == null) {
                    markProcessed(pdf, doc)
                }

                umgestellt++
                log.info("Backfill XML->PDF: Dokument {} von '{}' auf '{}' umgestellt", doc.id, xmlAtt.originalFilename, pdf.originalFilename)
            } catch (ex: Exception) {
                log.error("Backfill XML->PDF fehlgeschlagen fuer Dokument {}: {}", doc.id, ex.message)
            }
        }

        log.info("Backfill XML->PDF abgeschlossen: {} von {} Dokumenten umgestellt", umgestellt, xmlDocs.size)
        return umgestellt
    }

    private fun findePdfImSelbenEmail(xmlAtt: EmailAttachment, doc: LieferantDokument): EmailAttachment? {
        val atts = xmlAtt.email?.attachments
        if (atts.isNullOrEmpty()) return null
        val pdfs = ArrayList<EmailAttachment>()
        var xmlCount = 0L
        for (attachment in atts) {
            if (attachment.inlineAttachment == true) continue
            if (isPdf(attachment)) {
                val vorhanden = attachment.lieferantDokument
                val freiOderEigen = vorhanden == null || vorhanden.id == doc.id
                val pdfPath = resolveAttachmentPath(attachment)
                val existiert = pdfPath != null && Files.exists(pdfPath)
                if (freiOderEigen && existiert) pdfs.add(attachment)
            } else if (isXml(attachment)) {
                xmlCount++
            }
        }
        if (pdfs.isEmpty()) return null

        var nummer = doc.geschaeftsdaten?.dokumentNummer
        if (nummer.isNullOrBlank()) {
            nummer = extractInvoiceNumberFromXml(resolveAttachmentPath(xmlAtt))
        }
        val needle = normalizeForMatch(nummer)
        if (needle.isNotEmpty()) {
            pdfs.firstOrNull { normalizeForMatch(it.originalFilename).contains(needle) }?.let { return it }
        }

        return if (pdfs.size == 1 && xmlCount == 1L) pdfs[0] else null
    }

    private fun findMatchingPdf(
        xml: EmailAttachment,
        candidates: List<EmailAttachment>,
        consumed: Set<Long?>,
        pdfCount: Long,
        xmlCount: Long,
    ): EmailAttachment? {
        val freePdfs = candidates.filter { isPdf(it) && !consumed.contains(it.id) }
        if (freePdfs.isEmpty()) return null

        val invoiceNr = extractInvoiceNumberFromXml(resolveAttachmentPath(xml))
        val needle = normalizeForMatch(invoiceNr)
        if (needle.isNotEmpty()) {
            freePdfs.firstOrNull { normalizeForMatch(it.originalFilename).contains(needle) }?.let { return it }
        }
        return if (pdfCount == 1L && xmlCount == 1L) freePdfs[0] else null
    }

    private fun extractInvoiceNumberFromXml(xmlPath: Path?): String? {
        if (xmlPath == null || !Files.exists(xmlPath)) return null
        return try {
            val xml = Files.readString(xmlPath, StandardCharsets.UTF_8)
            val matcher = Pattern.compile(
                "<(?:[^:>]+:)?(?:ID|InvoiceNumber)(?:\\s[^>]*)?>([^<]+)</",
                Pattern.CASE_INSENSITIVE,
            ).matcher(xml)
            if (matcher.find()) matcher.group(1).trim() else null
        } catch (ex: Exception) {
            log.debug("Konnte Rechnungsnummer nicht aus XML lesen ({}): {}", xmlPath, ex.message)
            null
        }
    }

    private fun normalizeForMatch(value: String?): String =
        value?.lowercase()?.replace("[^a-z0-9]".toRegex(), "") ?: ""

    private fun isPdf(attachment: EmailAttachment): Boolean =
        attachment.originalFilename?.lowercase()?.endsWith(".pdf") == true

    private fun isXml(attachment: EmailAttachment): Boolean =
        attachment.originalFilename?.lowercase()?.endsWith(".xml") == true

    private fun processAttachment(
        displayAttachment: EmailAttachment,
        metaAttachment: EmailAttachment,
        lieferantId: Long,
    ): Boolean {
        val displayFilename = displayAttachment.originalFilename ?: return false
        val metaFilename = metaAttachment.originalFilename ?: return false

        val metaPath = resolveAttachmentPath(metaAttachment)
        if (metaPath == null || !Files.exists(metaPath)) {
            log.warn("Attachment-Datei nicht gefunden: {}", metaAttachment.storedFilename)
            return false
        }

        var effektiveAnzeige = displayAttachment
        if (displayAttachment.id != metaAttachment.id) {
            val displayPath = resolveAttachmentPath(displayAttachment)
            if (displayPath == null || !Files.exists(displayPath)) {
                log.warn("Anzeige-Datei fehlt auf der Platte ({}), nutze Metadaten-Datei als Anzeige", displayAttachment.storedFilename)
                effektiveAnzeige = metaAttachment
            }
        }
        val anzeigeFilename = effektiveAnzeige.originalFilename

        val geschaeftsdaten = geminiAnalyseService.analyzeAndReturnData(metaPath, metaFilename)

        val lieferant = lieferantenRepository.findById(lieferantId).orElse(null)
        if (lieferant == null) {
            log.error("Lieferant ID {} nicht gefunden", lieferantId)
            return false
        }

        if (geschaeftsdaten != null && !geschaeftsdaten.dokumentNummer.isNullOrBlank()) {
            val duplikat = lieferantGeschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(
                lieferantId,
                requireNotNull(geschaeftsdaten.dokumentNummer),
            )
            if (duplikat) {
                log.info(
                    "Duplikat erkannt: Dokumentnummer {} existiert bereits bei Lieferant {}. Ueberspringe.",
                    geschaeftsdaten.dokumentNummer,
                    lieferantId,
                )
                markProcessed(effektiveAnzeige, null)
                markProcessed(metaAttachment, null)
                return false
            }
        }

        var dokument = LieferantDokument().apply {
            this.lieferant = lieferant
            originalDateiname = anzeigeFilename
            gespeicherterDateiname = effektiveAnzeige.storedFilename
            uploadDatum = LocalDateTime.now()
        }

        var typ = LieferantDokumentTyp.SONSTIG
        if (geschaeftsdaten?.detectedTyp != null) {
            typ = requireNotNull(geschaeftsdaten.detectedTyp)
        } else if (geschaeftsdaten?.dokumentNummer != null) {
            typ = inferDokumentTyp(geschaeftsdaten.dokumentNummer)
        }
        dokument.typ = typ

        if (geschaeftsdaten != null) {
            dokument.geschaeftsdaten = geschaeftsdaten
            geschaeftsdaten.dokument = dokument
        } else {
            val empty = LieferantGeschaeftsdokument().apply {
                this.dokument = dokument
                manuellePruefungErforderlich = true
                datenquelle = "ERROR_NO_RESULT"
                analysiertAm = LocalDateTime.now()
            }
            dokument.geschaeftsdaten = empty
        }

        dokument = lieferantDokumentRepository.save(dokument)

        if (geschaeftsdaten != null) {
            geminiAnalyseService.performRelink(dokument)
        }

        try {
            standardKostenstelleAutoAssigner.applyIfApplicable(dokument)
        } catch (ex: Exception) {
            log.warn("Auto-Zuweisung Standard-Kostenstelle fehlgeschlagen fuer Dokument {}: {}", dokument.id, ex.message)
        }

        markProcessed(effektiveAnzeige, dokument)
        if (metaAttachment.id != effektiveAnzeige.id) {
            markProcessed(metaAttachment, dokument)
        }

        log.info(
            "Geschaeftsdokument atomar erstellt fuer: {} (Lieferant-ID: {}, Typ: {}, Data: {})",
            anzeigeFilename,
            lieferantId,
            dokument.typ,
            if (geschaeftsdaten != null) "Ja" else "Nein",
        )
        return true
    }

    private fun markProcessed(attachment: EmailAttachment, dokument: LieferantDokument?) {
        attachment.aiProcessed = true
        attachment.aiProcessedAt = LocalDateTime.now()
        if (dokument != null) {
            attachment.lieferantDokument = dokument
        }
        emailAttachmentRepository.save(attachment)
    }

    private fun inferDokumentTyp(nummer: String?): LieferantDokumentTyp {
        if (nummer == null) return LieferantDokumentTyp.SONSTIG
        val upper = nummer.uppercase()
        if (upper.startsWith("RE") || upper.contains("RECHNUNG")) return LieferantDokumentTyp.RECHNUNG
        if (upper.startsWith("AB") || upper.contains("AUFTRAGS")) return LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG
        if (upper.startsWith("LS") || upper.contains("LIEFER")) return LieferantDokumentTyp.LIEFERSCHEIN
        if (upper.startsWith("AN") || upper.contains("ANGEBOT")) return LieferantDokumentTyp.ANGEBOT
        if (upper.startsWith("GS") || upper.contains("GUTSCHRIFT")) return LieferantDokumentTyp.GUTSCHRIFT
        return LieferantDokumentTyp.SONSTIG
    }

    private fun isProcessableAttachment(attachment: EmailAttachment): Boolean {
        if (attachment.inlineAttachment == true) return false
        val lower = attachment.originalFilename?.lowercase() ?: return false
        return lower.endsWith(".pdf") || lower.endsWith(".xml")
    }

    private fun resolveAttachmentPath(attachment: EmailAttachment): Path? {
        val storedFilename = attachment.storedFilename ?: return null
        val basePath = Path.of(attachmentDir).toAbsolutePath().normalize()
        val directPath = basePath.resolve(storedFilename)
        if (Files.exists(directPath)) return directPath

        val email = attachment.email
        if (email != null) {
            val emailSubDirPath = basePath.resolve(email.id.toString()).resolve(storedFilename)
            if (Files.exists(emailSubDirPath)) return emailSubDirPath
        }

        val lieferant = email?.lieferant
        if (lieferant != null) {
            val lieferantPath = basePath.resolve(lieferant.id.toString()).resolve(storedFilename)
            if (Files.exists(lieferantPath)) return lieferantPath
        }

        return if (email != null) basePath.resolve(email.id.toString()).resolve(storedFilename) else directPath
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailAttachmentProcessingService::class.java)
    }
}
