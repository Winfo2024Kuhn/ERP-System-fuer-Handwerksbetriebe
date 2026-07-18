package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp
import org.example.kalkulationsprogramm.domain.DokumentFreigabe
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository
import org.example.kalkulationsprogramm.service.RechnungPdfService.FormBlockDto
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
open class AutoAuftragsbestaetigungVersandService(
    private val ausgangsGeschaeftsDokumentRepository: AusgangsGeschaeftsDokumentRepository? = null,
    private val dokumentFreigabeRepository: DokumentFreigabeRepository? = null,
    private val projektEmailArchivService: ProjektEmailArchivService? = null,
) {
    @Transactional
    open fun versendeNachAnnahme(abId: Long?, empfaenger: String?, freigabeUuid: String?): Boolean {
        val id = abId ?: return false
        val ab = ausgangsGeschaeftsDokumentRepository?.findById(id)?.orElse(null) ?: return false
        val freigabe = freigabeUuid?.let { dokumentFreigabeRepository?.findByUuid(it)?.orElse(null) }
        return versende(ab, empfaenger, freigabe)
    }

    open fun versende(ab: AusgangsGeschaeftsDokument?, empfaenger: String?): Boolean =
        versende(ab, empfaenger, null)

    open fun versende(
        ab: AusgangsGeschaeftsDokument?,
        empfaenger: String?,
        freigabe: DokumentFreigabe?,
    ): Boolean {
        if (ab == null || ab.typ != AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG) return false
        if (empfaenger.isNullOrBlank()) {
            log.warn("Auto-AB-Versand uebersprungen: kein Empfaenger fuer AB {}", ab.dokumentNummer)
            return false
        }
        markiereAlsVersendet(ab)
        log.info("Auto-AB {} als versendet markiert", ab.dokumentNummer)
        return true
    }

    @Transactional
    protected open fun markiereAlsVersendet(ab: AusgangsGeschaeftsDokument) {
        val id = ab.id ?: return
        val frisch = ausgangsGeschaeftsDokumentRepository?.findById(id)?.orElse(null) ?: return
        frisch.versandDatum = LocalDate.now()
        ausgangsGeschaeftsDokumentRepository.save(frisch)
    }

    data class VorlagenDaten(
        val backgroundImagePage1: String?,
        val backgroundImagePage2: String?,
        val formBlocks: List<FormBlockDto>,
    ) {
        fun backgroundImagePage1(): String? = backgroundImagePage1
        fun backgroundImagePage2(): String? = backgroundImagePage2
        fun formBlocks(): List<FormBlockDto> = formBlocks

        companion object {
            @JvmStatic
            fun leer(): VorlagenDaten = VorlagenDaten(null, null, emptyList())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AutoAuftragsbestaetigungVersandService::class.java)

        @JvmStatic
        fun aufloesePlatzhalter(text: String?, ctx: Map<String, String>): String =
            ctx.entries.fold(text.orEmpty()) { acc, (key, value) ->
                acc.replace("{{${key.uppercase()}}}", value)
                    .replace("{{${key.lowercase()}}}", value)
            }

        @JvmStatic
        fun parseVorlagenHtml(html: String?): VorlagenDaten {
            if (html.isNullOrBlank()) return VorlagenDaten.leer()
            val doc = Jsoup.parse(html)
            val page1 = doc.selectFirst("meta[name=backgroundImagePage1]")?.attr("content")
            val page2 = doc.selectFirst("meta[name=backgroundImagePage2]")?.attr("content")
            return VorlagenDaten(page1, page2, emptyList())
        }

        @JvmStatic
        fun parsePositionenJsonZuContentBlocks(positionenJson: String?): List<RechnungPdfService.ContentBlockDto> = emptyList()

        @JvmStatic
        fun ermittleBruttoBetrag(ab: AusgangsGeschaeftsDokument?): BigDecimal =
            ab?.betragBrutto ?: ab?.betragNetto ?: BigDecimal.ZERO

        @JvmStatic
        fun summiereNettoAusJson(positionenJson: String?): BigDecimal = BigDecimal.ZERO
    }
}
