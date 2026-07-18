package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Firmeninformation
import org.example.kalkulationsprogramm.domain.Mahnstufe
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

@Service
open class AutoMahnVersandService(
    private val firmaRepository: FirmeninformationRepository? = null,
    private val projektDokumentRepository: ProjektDokumentRepository? = null,
    private val ausgangsGeschaeftsDokumentRepository: AusgangsGeschaeftsDokumentRepository? = null,
    private val projektEmailArchivService: ProjektEmailArchivService? = null,
) {
    enum class MahnlaufStatus {
        AUSGEFUEHRT,
        VERFAHREN_INAKTIV,
        LAEUFT_BEREITS,
    }

    data class MahnlaufErgebnis(
        val status: MahnlaufStatus,
        val versendet: Int,
        val fehlgeschlagen: Int,
    )

    private val laufAktiv = AtomicBoolean(false)

    @Scheduled(cron = "0 0 9 * * *")
    open fun verarbeiteFaelligeMahnungen() {
        fuehreMahnlaufAus()
    }

    open fun fuehreMahnlaufAus(): MahnlaufErgebnis {
        if (!laufAktiv.compareAndSet(false, true)) {
            log.info("Mahn-Lauf uebersprungen: es laeuft bereits einer")
            return MahnlaufErgebnis(MahnlaufStatus.LAEUFT_BEREITS, 0, 0)
        }
        return try {
            fuehreMahnlaufAusIntern()
        } finally {
            laufAktiv.set(false)
        }
    }

    private fun fuehreMahnlaufAusIntern(): MahnlaufErgebnis {
        log.debug("Auto-Mahn-Lauf gestartet")
        val firma = firmaRepository?.findById(1L)?.orElse(null)
            ?: return MahnlaufErgebnis(MahnlaufStatus.VERFAHREN_INAKTIV, 0, 0)
        if (!firma.isMahnverfahrenAktiv()) return MahnlaufErgebnis(MahnlaufStatus.VERFAHREN_INAKTIV, 0, 0)

        var versendet = 0
        var fehlgeschlagen = 0
        val heute = LocalDate.now()
        val offene = projektDokumentRepository?.findOffeneGeschaeftsdokumenteFuerMahnlauf()
            ?: projektDokumentRepository?.findOffeneGeschaeftsdokumente()
            ?: emptyList()
        offene.forEach { dok ->
            try {
                if (verarbeiteRechnung(dok, firma, heute)) versendet++
            } catch (e: Exception) {
                fehlgeschlagen++
                log.error("Auto-Mahn-Lauf fuer Dokument {} fehlgeschlagen: {}", dok.id, e.message, e)
            }
        }
        if (versendet > 0 || fehlgeschlagen > 0) {
            log.info("Auto-Mahn-Lauf abgeschlossen: {} Mahnung(en) versendet, {} fehlgeschlagen", versendet, fehlgeschlagen)
        }
        return MahnlaufErgebnis(MahnlaufStatus.AUSGEFUEHRT, versendet, fehlgeschlagen)
    }

    fun verarbeiteRechnung(dok: ProjektGeschaeftsdokument, firma: Firmeninformation, heute: LocalDate): Boolean = false

    @Transactional
    protected open fun persistiereMahnung(
        rechnung: ProjektGeschaeftsdokument,
        stufe: Mahnstufe,
        pdfBytes: ByteArray,
        heute: LocalDate,
    ): ProjektGeschaeftsdokument = rechnung

    @Transactional
    protected open fun markiereVersendet(dokumentId: Long, datum: LocalDate) {
        val dokument = projektDokumentRepository?.findById(dokumentId)?.orElse(null)
            ?: throw IllegalStateException("Versandtes Mahndokument nicht mehr vorhanden: $dokumentId")
        dokument.emailVersandDatum = datum
        projektDokumentRepository.save(dokument)
    }

    open fun generiereVorschauPdf(rechnungId: Long, stufe: Mahnstufe): ByteArray = ByteArray(0)

    open fun generiereVorschauPdfFuerAusgangsRechnung(ausgangsDokumentId: Long, stufe: Mahnstufe): ByteArray {
        ausgangsGeschaeftsDokumentRepository?.findById(ausgangsDokumentId)?.orElse(null)
            ?: throw IllegalArgumentException("Rechnung nicht gefunden")
        return ByteArray(0)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AutoMahnVersandService::class.java)
    }
}
