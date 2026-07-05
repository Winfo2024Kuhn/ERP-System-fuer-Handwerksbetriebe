package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Abteilung
import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.util.UUID

@Service
class LieferantDokumentService(
    private val dokumentRepository: LieferantDokumentRepository,
    private val berechtigungRepository: AbteilungDokumentBerechtigungRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val projektRepository: ProjektRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val geschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository,
    @Lazy private val geminiService: GeminiDokumentAnalyseService,
    private val standardKostenstelleAutoAssigner: LieferantStandardKostenstelleAutoAssigner,
) {
    @Value("\${upload.path:uploads}")
    private lateinit var uploadPath: String

    @Transactional(readOnly = true)
    fun getBerechtigungen(mitarbeiterId: Long): LieferantDokumentDto.BerechtigungenResponse {
        val mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
            .orElseThrow { RuntimeException("Mitarbeiter nicht gefunden") }

        val abteilungIds = mitarbeiter.abteilungen.mapNotNull(Abteilung::id)
        if (abteilungIds.isEmpty()) {
            return LieferantDokumentDto.BerechtigungenResponse.builder()
                .sichtbareTypen(emptyList())
                .scanbarTypen(emptyList())
                .build()
        }

        val sichtbar = berechtigungRepository.findSichtbareTypenByAbteilungIds(abteilungIds)
            .mapNotNull { safeValueOf(it) }
            .distinct()

        val scanbar = berechtigungRepository.findScanbarTypenByAbteilungIds(abteilungIds)
            .mapNotNull { safeValueOf(it) }
            .distinct()

        return LieferantDokumentDto.BerechtigungenResponse.builder()
            .sichtbareTypen(sichtbar)
            .scanbarTypen(scanbar)
            .build()
    }

    private fun safeValueOf(typeName: String?): LieferantDokumentTyp? {
        if (typeName == null) return null
        if ("EINGANGSRECHNUNG".equals(typeName, ignoreCase = true)) {
            return LieferantDokumentTyp.RECHNUNG
        }
        return try {
            LieferantDokumentTyp.valueOf(typeName)
        } catch (ex: IllegalArgumentException) {
            null
        }
    }

    @Transactional(readOnly = true)
    fun getDokumenteFiltered(
        lieferantId: Long,
        mitarbeiterId: Long,
        typFilter: LieferantDokumentTyp?,
    ): List<LieferantDokumentDto.Response> {
        val berechtigungen = getBerechtigungen(mitarbeiterId)
        val sichtbareTypen = berechtigungen.sichtbareTypen.orEmpty()
        if (sichtbareTypen.isEmpty()) return emptyList()

        val dokumente = if (typFilter != null) {
            if (!sichtbareTypen.contains(typFilter)) return emptyList()
            dokumentRepository.findByLieferantIdAndTypOrderByUploadDatumDesc(lieferantId, typFilter)
        } else {
            dokumentRepository.findByLieferantIdAndTypIn(lieferantId, sichtbareTypen)
        }
        return dokumente.map { toDto(it) }
    }

    @Transactional(readOnly = true)
    fun findById(dokumentId: Long): LieferantDokument? =
        dokumentRepository.findById(dokumentId).orElse(null)

    @Transactional(readOnly = true)
    fun getDokumentById(dokumentId: Long): LieferantDokumentDto.Response? =
        dokumentRepository.findById(dokumentId).orElse(null)?.let { toDto(it) }

    @Transactional(readOnly = true)
    fun getDokumenteByLieferant(
        lieferantId: Long,
        typFilter: LieferantDokumentTyp?,
    ): List<LieferantDokumentDto.Response> {
        val dokumente = if (typFilter != null) {
            dokumentRepository.findByLieferantIdAndTypOrderByUploadDatumDesc(lieferantId, typFilter)
        } else {
            dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(lieferantId)
        }
        return dokumente.map { toDto(it) }
    }

    @Transactional
    @Throws(IOException::class)
    fun uploadDokument(
        lieferantId: Long,
        datei: MultipartFile,
        request: LieferantDokumentDto.UploadRequest,
        mitarbeiterId: Long,
        useProModel: Boolean,
    ): LieferantDokumentDto.Response {
        val berechtigungen = getBerechtigungen(mitarbeiterId)
        if (!berechtigungen.scanbarTypen.orEmpty().contains(request.typ)) {
            throw RuntimeException("Keine Berechtigung zum Hochladen von ${request.typ}")
        }

        val lieferant = lieferantenRepository.findById(lieferantId)
            .orElseThrow { RuntimeException("Lieferant nicht gefunden") }
        val uploadedBy = mitarbeiterRepository.findById(mitarbeiterId).orElse(null)

        val originalFilename = Path.of(StringUtils.cleanPath(requireNotNull(datei.originalFilename))).fileName.toString()
        val storedFilename = "${UUID.randomUUID()}_$originalFilename"

        val lieferantDir = Path.of(uploadPath, "lieferanten", lieferantId.toString()).toAbsolutePath().normalize()
        Files.createDirectories(lieferantDir)
        val targetPath = lieferantDir.resolve(storedFilename).normalize()
        if (!targetPath.startsWith(lieferantDir)) {
            throw SecurityException("Ungueltiger Dateipfad: Verzeichnistraversal erkannt")
        }
        Files.copy(datei.inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)

        var dokument = LieferantDokument().apply {
            this.lieferant = lieferant
            typ = request.typ
            this.originalDateiname = originalFilename
            gespeicherterDateiname = storedFilename
            uploadDatum = LocalDateTime.now()
            this.uploadedBy = uploadedBy
        }

        val verknuepfteIds = request.verknuepfteIds
        if (!verknuepfteIds.isNullOrEmpty()) {
            dokument.verknuepfteDokumente = HashSet(dokumentRepository.findAllById(verknuepfteIds))
        }

        dokument = dokumentRepository.saveAndFlush(dokument)

        try {
            log.info("Starte synchrone Analyse fuer Dokument {}", dokument.id)
            val analyzeResult = geminiService.analyzeFile(targetPath, originalFilename, useProModel)
            if (analyzeResult != null) {
                log.info(
                    "Analyse erfolgreich: Typ={}, Nummer={}, Betrag={}",
                    analyzeResult.dokumentTyp,
                    analyzeResult.dokumentNummer,
                    analyzeResult.betragBrutto,
                )

                var gd = LieferantGeschaeftsdokument().apply {
                    this.dokument = dokument
                    dokumentNummer = analyzeResult.dokumentNummer
                    dokumentDatum = analyzeResult.dokumentDatum
                    betragNetto = analyzeResult.betragNetto
                    betragBrutto = analyzeResult.betragBrutto
                    mwstSatz = analyzeResult.mwstSatz
                    zahlungsziel = analyzeResult.zahlungsziel
                    bestellnummer = analyzeResult.bestellnummer
                    referenzNummer = analyzeResult.referenzNummer
                    skontoTage = analyzeResult.skontoTage
                    skontoProzent = analyzeResult.skontoProzent
                    nettoTage = analyzeResult.nettoTage
                    bereitsGezahlt = analyzeResult.bereitsGezahlt
                    zahlungsart = analyzeResult.zahlungsart
                    aiConfidence = analyzeResult.aiConfidence
                    analysiertAm = LocalDateTime.now()
                }

                if (analyzeResult.dokumentTyp != null) {
                    dokument.typ = analyzeResult.dokumentTyp
                }

                gd = geschaeftsdokumentRepository.saveAndFlush(gd)
                dokument.geschaeftsdaten = gd
                dokument = dokumentRepository.saveAndFlush(dokument)
                standardKostenstelleAutoAssigner.applyIfApplicable(dokument)
            } else {
                log.warn("Analyse ergab keine Ergebnisse fuer Dokument {}", dokument.id)
            }
        } catch (ex: Exception) {
            log.error("Fehler bei synchroner Analyse fuer Dokument {}: {}", dokument.id, ex.message, ex)
        }

        return toDto(dokument)
    }

    @Transactional
    fun zuordnenProjekte(
        dokumentId: Long,
        request: LieferantDokumentDto.ProjektZuordnungRequest,
    ): LieferantDokumentDto.Response {
        var dokument = dokumentRepository.findById(dokumentId)
            .orElseThrow { RuntimeException("Dokument nicht gefunden") }

        val anteile = request.anteile.orEmpty()
        val summe = anteile.sumOf { it.prozent ?: 0 }
        if (summe != 100) {
            throw RuntimeException("Die Summe der Prozente muss 100 ergeben, ist aber $summe")
        }

        val betragBrutto: BigDecimal? = dokument.geschaeftsdaten?.betragBrutto
        dokument.projektAnteile.clear()

        for (anteil in anteile) {
            val projektId = requireNotNull(anteil.projektId) { "Projekt-ID fehlt" }
            val projekt = projektRepository.findById(projektId)
                .orElseThrow { RuntimeException("Projekt nicht gefunden: $projektId") }

            val pa = LieferantDokumentProjektAnteil().apply {
                this.dokument = dokument
                this.projekt = projekt
                prozent = anteil.prozent
                beschreibung = anteil.beschreibung
            }
            if (betragBrutto != null) {
                pa.berechneAnteil(betragBrutto)
            }
            dokument.projektAnteile.add(pa)
        }

        dokument = dokumentRepository.save(dokument)
        return toDto(dokument)
    }

    @Transactional
    fun addVerknuepfungen(dokumentId: Long, verknuepfteIds: Set<Long>): LieferantDokumentDto.Response {
        var dokument = dokumentRepository.findById(dokumentId)
            .orElseThrow { RuntimeException("Dokument nicht gefunden") }
        val neueVerknuepfungen = HashSet(dokumentRepository.findAllById(verknuepfteIds))
        dokument.verknuepfteDokumente.addAll(neueVerknuepfungen)
        dokument = dokumentRepository.save(dokument)
        return toDto(dokument)
    }

    private fun toDto(dok: LieferantDokument): LieferantDokumentDto.Response {
        val attachment = dok.attachment
        val attachmentEmail = attachment?.email
        val lieferant = requireNotNull(dok.lieferant) { "Lieferant fehlt am Dokument." }
        val url = if (attachmentEmail != null) {
            val emailId = attachmentEmail.id
            val attachmentId = attachment.id
            "/api/emails/$emailId/attachments/$attachmentId"
        } else if (dok.gespeicherterDateiname != null) {
            "/api/lieferanten/${lieferant.id}/dokumente/${dok.id}/download"
        } else {
            null
        }

        val builder = LieferantDokumentDto.Response.builder()
            .id(dok.id)
            .lieferantId(lieferant.id)
            .lieferantName(lieferant.lieferantenname)
            .typ(dok.typ)
            .originalDateiname(dok.getEffektiverDateiname())
            .gespeicherterDateiname(dok.getEffektiverGespeicherterDateiname())
            .uploadDatum(dok.uploadDatum)
            .url(url)
            .uploadedByName(dok.uploadedBy?.let { "${it.vorname} ${it.nachname}" })
            .projektAnteile(
                dok.projektAnteile
                    .filter { it.projekt != null || it.kostenstelle != null }
                    .map { pa ->
                        val b = LieferantDokumentDto.ProjektAnteilRef.builder()
                            .id(pa.id)
                            .prozent(pa.prozent)
                            .berechneterBetrag(pa.berechneterBetrag)
                            .beschreibung(pa.beschreibung)
                            .zugeordnetAm(pa.zugeordnetAm)
                        pa.zugeordnetVon?.let { b.zugeordnetVonName(it.displayName) }
                        pa.projekt?.let {
                            b.projektId(it.id)
                                .projektName(it.bauvorhaben)
                                .auftragsnummer(it.auftragsnummer)
                        }
                        pa.kostenstelle?.let {
                            b.kostenstelleId(it.id)
                                .kostenstelleName(it.bezeichnung)
                        }
                        b.build()
                    },
            )
            .verknuepfteDokumente(
                dok.verknuepfteDokumente.map { v ->
                    LieferantDokumentDto.VerknuepftesDoc.builder()
                        .id(v.id)
                        .typ(v.typ)
                        .originalDateiname(v.getEffektiverDateiname())
                        .uploadDatum(v.uploadDatum)
                        .build()
                },
            )

        dok.geschaeftsdaten?.let { gd ->
            builder.geschaeftsdaten(
                LieferantDokumentDto.GeschaeftsdatenRef.builder()
                    .dokumentNummer(gd.dokumentNummer)
                    .dokumentDatum(gd.dokumentDatum)
                    .betragNetto(gd.betragNetto)
                    .betragBrutto(gd.betragBrutto)
                    .liefertermin(gd.liefertermin)
                    .bestellnummer(gd.bestellnummer)
                    .referenzNummer(gd.referenzNummer)
                    .aiConfidence(gd.aiConfidence)
                    .zahlungsziel(gd.zahlungsziel)
                    .bezahlt(gd.bezahlt)
                    .bezahltAm(gd.bezahltAm)
                    .bereitsGezahlt(gd.bereitsGezahlt)
                    .zahlungsart(gd.zahlungsart)
                    .skontoTage(gd.skontoTage)
                    .skontoProzent(gd.skontoProzent)
                    .nettoTage(gd.nettoTage)
                    .tatsaechlichGezahlt(gd.tatsaechlichGezahlt)
                    .mitSkonto(gd.mitSkonto)
                    .manuellePruefungErforderlich(gd.manuellePruefungErforderlich)
                    .datenquelle(gd.datenquelle)
                    .build(),
            )
        }

        return builder.build()
    }

    companion object {
        private val log = LoggerFactory.getLogger(LieferantDokumentService::class.java)
    }
}
