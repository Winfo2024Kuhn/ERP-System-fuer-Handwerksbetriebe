package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus
import org.example.kalkulationsprogramm.domain.BelegKategorie
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus
import org.example.kalkulationsprogramm.domain.BelegPosition
import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class BelegKiAnalyseService(
    private val belegRepository: BelegRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val lieferantDokumentRepository: LieferantDokumentRepository,
    private val lieferantGeschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository,
    private val geminiService: GeminiDokumentAnalyseService,
    private val kostenkontoService: BelegKiKostenkontoService,
    private val belegSplitService: BelegSplitService,
    private val objectMapper: ObjectMapper,
) {
    @Value("\${upload.path:uploads}")
    private lateinit var uploadPath: String

    @Async
    @Transactional
    fun analysiereBelegAsync(belegId: Long) {
        val beleg = belegRepository.findById(belegId).orElse(null)
        if (beleg == null) {
            log.warn("KI-Analyse: Beleg {} nicht gefunden", belegId)
            return
        }
        beleg.kiAnalyseStatus = BelegKiAnalyseStatus.LAEUFT
        belegRepository.save(beleg)

        try {
            val datei = Paths.get(uploadPath, "belege", beleg.gespeicherterDateiname)
            val ergebnis = geminiService.analyzeFile(datei, beleg.originalDateiname)

            if (ergebnis == null) {
                beleg.kiAnalyseStatus = BelegKiAnalyseStatus.FAILED
                beleg.kiFehlerText = "KI-Analyse lieferte kein Ergebnis"
                belegRepository.save(beleg)
                return
            }

            val aiConfidence = ergebnis.value<Double>("aiConfidence")
            beleg.belegNummer = ergebnis.value("dokumentNummer")
            beleg.belegDatum = ergebnis.value("dokumentDatum")
            beleg.betragNetto = ergebnis.value("betragNetto")
            beleg.betragBrutto = ergebnis.value("betragBrutto")
            beleg.mwstSatz = ergebnis.value("mwstSatz")
            beleg.zahlungsart = ergebnis.value("zahlungsart")
            beleg.dokumentTyp = ergebnis.value("dokumentTyp")
            if (aiConfidence != null) {
                beleg.kiConfidence = BigDecimal.valueOf(aiConfidence).setScale(2, RoundingMode.HALF_UP)
            }
            val lieferantName = ergebnis.value<String>("lieferantName")
            if (!lieferantName.isNullOrBlank() && beleg.lieferant == null) {
                lieferantenRepository.findByLieferantennameIgnoreCase(lieferantName.trim())
                    .filter { l -> java.lang.Boolean.FALSE != l.istAktiv }
                    .ifPresent { beleg.lieferant = it }
            }
            beleg.kiVorgeschlagenerLieferant = beleg.lieferant?.lieferantenname

            if (beleg.belegKategorie == null || beleg.belegKategorie == BelegKategorie.UNZUGEORDNET) {
                val abgeleitet = ableitenBelegKategorie(ergebnis.value("zahlungsart"))
                if (abgeleitet != null) {
                    beleg.belegKategorie = abgeleitet
                }
            }

            try {
                beleg.kiExtraktionJson = objectMapper.writeValueAsString(ergebnis)
            } catch (ignored: Exception) {
            }

            beleg.kiAnalyseStatus = BelegKiAnalyseStatus.DONE
            beleg.kiFehlerText = null

            if (beleg.aufteilungsModus == BelegAufteilungsModus.TEILWEISE) {
                try {
                    extrahiereUndSpeicherePositionen(beleg, datei)
                } catch (e: Exception) {
                    log.warn("Positions-Extraktion fuer Beleg {} fehlgeschlagen: {}", belegId, e.message)
                }
            }

            try {
                kostenkontoService.klassifiziereBeleg(beleg)
            } catch (e: Exception) {
                log.warn("Kostenkonto-Klassifizierung fuer Beleg {} fehlgeschlagen: {}", belegId, e.message)
            }
            belegRepository.save(beleg)

            try {
                erstelleEingangsrechnungFallsRechnung(beleg, ergebnis)
            } catch (e: Exception) {
                log.warn("Auto-Erzeugung Eingangsrechnung fuer Beleg {} fehlgeschlagen: {}", belegId, e.message)
            }
        } catch (e: Exception) {
            log.error("KI-Analyse fuer Beleg {} fehlgeschlagen: {}", belegId, e.message, e)
            beleg.kiAnalyseStatus = BelegKiAnalyseStatus.FAILED
            var msg = e.message ?: e.javaClass.simpleName
            if (msg.length > 1000) {
                msg = msg.substring(0, 1000)
            }
            beleg.kiFehlerText = msg
            belegRepository.save(beleg)
        }
    }

    private fun extrahiereUndSpeicherePositionen(beleg: Beleg, datei: java.nio.file.Path) {
        val bytes = Files.readAllBytes(datei)
        val mimeType = beleg.mimeType ?: "application/pdf"
        val json = geminiService.rufGeminiApiMitPrompt(bytes, mimeType, POSITIONS_PROMPT)
        if (json.isNullOrBlank()) {
            log.info("Positions-Extraktion fuer Beleg {} lieferte kein JSON", beleg.id)
            return
        }
        val root = objectMapper.readTree(json)
        val positionen = root.path("positionen")
        if (!positionen.isArray || positionen.isEmpty) {
            log.info("Beleg {} hat keine extrahierten Positionen (Bon ohne Einzelposten)", beleg.id)
            return
        }

        val ergebnis = ArrayList<BelegPosition>()
        var sortIdx = 0
        for (n in positionen) {
            val p = BelegPosition()
            p.sortierung = sortIdx++
            p.beschreibung = textOrFallback(n.path("beschreibung"), "Position $sortIdx")
            p.menge = numericOrNull(n.path("menge"))
            p.einheit = textOrNull(n.path("einheit"))
            p.einzelpreis = numericOrNull(n.path("einzelpreis"))
            p.betragBrutto = numericOrNull(n.path("betragBrutto"))
            p.mwstSatz = numericOrNull(n.path("mwstSatz"))
            p.istFuerFirma = false
            ergebnis.add(p)
        }
        belegSplitService.speicherePositionen(beleg, ergebnis)
        log.info("Beleg {}: {} Positionen aus KI-Extraktion gespeichert", beleg.id, ergebnis.size)
    }

    private fun erstelleEingangsrechnungFallsRechnung(
        beleg: Beleg,
        ergebnis: LieferantDokumentDto.AnalyzeResponse,
    ) {
        val typ = beleg.dokumentTyp
        val lieferant = beleg.lieferant
        if (typ == null || lieferant == null) {
            return
        }
        if (typ != LieferantDokumentTyp.RECHNUNG && typ != LieferantDokumentTyp.GUTSCHRIFT) {
            return
        }

        if (lieferantDokumentRepository.findByBelegId(beleg.id!!).isPresent) {
            log.debug("LieferantDokument fuer Beleg {} existiert bereits, kein erneutes Anlegen", beleg.id)
            return
        }
        val dokNr = beleg.belegNummer
        if (!dokNr.isNullOrBlank() &&
            lieferantGeschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(lieferant.id!!, dokNr)
        ) {
            log.info(
                "Rechnungs-Nr {} bei Lieferant {} schon vorhanden — Beleg {} bleibt eigenstaendig",
                dokNr,
                lieferant.id,
                beleg.id,
            )
            return
        }

        val gespeicherterFuerLD = beleg.gespeicherterDateiname?.let { "belege/$it" }

        var ld = LieferantDokument()
        ld.lieferant = lieferant
        ld.typ = typ
        ld.originalDateiname = beleg.originalDateiname
        ld.gespeicherterDateiname = gespeicherterFuerLD
        ld.uploadDatum = LocalDateTime.now()
        ld.uploadedBy = beleg.uploadedBy
        ld.beleg = beleg
        ld = lieferantDokumentRepository.save(ld)

        val lgd = LieferantGeschaeftsdokument()
        lgd.dokument = ld
        lgd.dokumentNummer = beleg.belegNummer
        lgd.dokumentDatum = beleg.belegDatum
        lgd.betragNetto = beleg.betragNetto
        lgd.betragBrutto = beleg.betragBrutto
        lgd.mwstSatz = beleg.mwstSatz
        lgd.zahlungsart = beleg.zahlungsart
        val aiConfidence = ergebnis.value<Double>("aiConfidence")
        lgd.bereitsGezahlt = java.lang.Boolean.TRUE == ergebnis.value<Boolean>("bereitsGezahlt")
        lgd.skontoTage = ergebnis.value("skontoTage")
        lgd.skontoProzent = ergebnis.value("skontoProzent")
        lgd.nettoTage = ergebnis.value("nettoTage")
        lgd.zahlungsziel = ergebnis.value<LocalDate>("zahlungsziel")
        lgd.liefertermin = ergebnis.value<LocalDate>("liefertermin")
        lgd.referenzNummer = ergebnis.value("referenzNummer")
        lgd.bestellnummer = ergebnis.value("bestellnummer")
        if (aiConfidence != null) {
            lgd.aiConfidence = aiConfidence
        }
        lgd.analysiertAm = LocalDateTime.now()
        try {
            lgd.aiRawJson = objectMapper.writeValueAsString(ergebnis)
        } catch (ignored: Exception) {
        }
        lieferantGeschaeftsdokumentRepository.save(lgd)

        log.info("Auto-Eingangsrechnung erzeugt: Beleg {} -> LieferantDokument {}", beleg.id, ld.id)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(BelegKiAnalyseService::class.java)

        private val POSITIONS_PROMPT = """
            Extrahiere ALLE einzelnen Posten/Zeilen dieses Belegs als JSON.
            Antworte AUSSCHLIESSLICH mit gueltigem JSON (keine Erklaerungen, kein Markdown).

            Format:
            {
              "positionen": [
                {
                  "beschreibung": "Artikelname wie auf dem Bon",
                  "menge": 1.0,
                  "einheit": "St" | "kg" | "l" | null,
                  "einzelpreis": 4.99,
                  "betragBrutto": 4.99,
                  "mwstSatz": 19.00
                }
              ]
            }

            Regeln:
            - JEDE Zeile, die einen Preis hat, ist eine Position (auch Pfand, Rabatt).
            - Rabatte als negativen Betrag.
            - mwstSatz aus dem Bon ablesen (oft als A=19%, B=7% gekennzeichnet).
              Wenn nicht erkennbar: Lebensmittel/Grundnahrung -> 7, sonst -> 19.
            - betragBrutto = was tatsaechlich fuer diese Zeile gezahlt wird.
            - Wenn der Bon nur eine Gesamtsumme ohne Einzelposten hat: leeres Array.
            - Beschreibung kurz halten (max 80 Zeichen), Original-Schreibweise behalten.
        """.trimIndent()

        private fun textOrNull(n: JsonNode?): String? {
            if (n == null || n.isNull || n.isMissingNode) return null
            val v = n.asText(null)
            return if (v.isNullOrBlank()) null else v.trim()
        }

        private fun textOrFallback(n: JsonNode, fallback: String): String {
            val v = textOrNull(n)
            return if (v != null) {
                if (v.length > 500) v.substring(0, 500) else v
            } else {
                fallback
            }
        }

        @JvmStatic
        fun ableitenBelegKategorie(zahlungsart: String?): BelegKategorie? {
            if (zahlungsart.isNullOrBlank()) {
                return null
            }
            return when (zahlungsart.trim().uppercase()) {
                "BAR" -> BelegKategorie.KASSE_AUSGABE
                "KREDITKARTE" -> BelegKategorie.KREDITKARTE
                "SEPA_LASTSCHRIFT", "UEBERWEISUNG", "VORAUSKASSE", "PAYPAL", "AMAZON_PAY" -> BelegKategorie.BANK
                else -> null
            }
        }

        private fun numericOrNull(n: JsonNode?): BigDecimal? {
            if (n == null || n.isNull || n.isMissingNode) return null
            return try {
                val raw = n.asText("").trim().replace(',', '.')
                if (raw.isEmpty()) null else BigDecimal(raw)
            } catch (e: NumberFormatException) {
                null
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> LieferantDokumentDto.AnalyzeResponse.value(name: String): T? {
            val field = javaClass.getDeclaredField(name)
            field.isAccessible = true
            return field.get(this) as T?
        }
    }
}
