package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Kostenstelle
import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Erstellt automatisch eine 100%-Zuordnung auf die Standard-Kostenstelle eines Lieferanten,
 * sobald ein neues RECHNUNGS-Dokument hereinkommt.
 */
@Service
class LieferantStandardKostenstelleAutoAssigner(
    private val anteilRepository: LieferantDokumentProjektAnteilRepository,
) {
    @Transactional
    fun applyIfApplicable(dokument: LieferantDokument?) {
        val dokumentId = dokument?.longValue("getId") ?: return
        if (dokument.enumValue<LieferantDokumentTyp>("getTyp") != LieferantDokumentTyp.RECHNUNG) {
            return
        }

        val lieferant = dokument.value<Lieferanten>("getLieferant") ?: return
        val standard = lieferant.value<Kostenstelle>("getStandardKostenstelle") ?: return
        if (anteilRepository.findByDokumentId(dokumentId).isNotEmpty()) {
            return
        }

        val gd = dokument.value<LieferantGeschaeftsdokument>("getGeschaeftsdaten")
        val betragNetto = gd?.value<BigDecimal>("getBetragNetto")
        val betragBrutto = gd?.value<BigDecimal>("getBetragBrutto")

        val anteil = LieferantDokumentProjektAnteil()
        anteil.setValue("setDokument", LieferantDokument::class.java, dokument)
        anteil.setValue("setKostenstelle", Kostenstelle::class.java, standard)
        anteil.setValue("setProzent", Integer::class.java, Integer.valueOf(100))
        if (betragNetto != null || betragBrutto != null) {
            anteil.berechneAnteil(betragNetto, betragBrutto)
        }
        anteilRepository.save(anteil)

        log.info(
            "Auto-Zuweisung: Dokument {} -> Standard-Kostenstelle '{}' (Lieferant {})",
            dokumentId,
            standard.stringValue("getBezeichnung"),
            lieferant.longValue("getId"),
        )
    }

    private inline fun <reified T> Any.value(getter: String): T? =
        javaClass.getMethod(getter).invoke(this) as? T

    private inline fun <reified T : Enum<T>> Any.enumValue(getter: String): T? =
        value<T>(getter)

    private fun Any.longValue(getter: String): Long? =
        javaClass.getMethod(getter).invoke(this) as? Long

    private fun Any.stringValue(getter: String): String? =
        javaClass.getMethod(getter).invoke(this) as? String

    private fun Any.setValue(setter: String, parameterType: Class<*>, value: Any?) {
        javaClass.getMethod(setter, parameterType).invoke(this, value)
    }

    companion object {
        private val log = LoggerFactory.getLogger(LieferantStandardKostenstelleAutoAssigner::class.java)
    }
}
