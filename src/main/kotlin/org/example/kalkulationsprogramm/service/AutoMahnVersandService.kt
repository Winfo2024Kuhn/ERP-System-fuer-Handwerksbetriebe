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

@Service
open class AutoMahnVersandService(
    private val firmaRepository: FirmeninformationRepository? = null,
    private val projektDokumentRepository: ProjektDokumentRepository? = null,
    private val ausgangsGeschaeftsDokumentRepository: AusgangsGeschaeftsDokumentRepository? = null,
) {
    @Scheduled(cron = "0 0 9 * * *")
    open fun verarbeiteFaelligeMahnungen() {
        log.debug("Auto-Mahn-Lauf gestartet")
        val firma = firmaRepository?.findById(1L)?.orElse(null) ?: return
        if (!firma.isMahnverfahrenAktiv()) return
        projektDokumentRepository?.findOffeneGeschaeftsdokumente().orEmpty()
            .forEach { verarbeiteRechnung(it, firma, LocalDate.now()) }
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
        projektDokumentRepository?.findById(dokumentId)?.orElse(null)?.let {
            it.emailVersandDatum = datum
            projektDokumentRepository.save(it)
        }
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
