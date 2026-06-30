package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.KalenderEintrag
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

@Service
open class KalenderService(
    private val kalenderEintragRepository: KalenderEintragRepository,
    private val projektRepository: ProjektRepository,
    private val kundeRepository: KundeRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val anfrageRepository: AnfrageRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
) {

    open fun getEintraegeForMonat(jahr: Int, monat: Int): List<KalenderEintrag> {
        val yearMonth = YearMonth.of(jahr, monat)
        return kalenderEintragRepository.findByDatumBetween(yearMonth.atDay(1), yearMonth.atEndOfMonth())
    }

    open fun getEintraegeForMitarbeiter(mitarbeiterId: Long?, jahr: Int, monat: Int): List<KalenderEintrag> {
        val yearMonth = YearMonth.of(jahr, monat)
        return kalenderEintragRepository.findByMitarbeiterAndDatumBetween(
            mitarbeiterId,
            yearMonth.atDay(1),
            yearMonth.atEndOfMonth(),
        )
    }

    open fun getEintraegeForMitarbeiterTag(mitarbeiterId: Long?, datum: LocalDate): List<KalenderEintrag> =
        kalenderEintragRepository.findByMitarbeiterAndDatum(mitarbeiterId, datum)

    open fun getEintraegeForRange(von: LocalDate, bis: LocalDate): List<KalenderEintrag> =
        kalenderEintragRepository.findByDatumBetween(von, bis)

    open fun getEintrag(id: Long?): KalenderEintrag? =
        kalenderEintragRepository.findById(id!!).orElse(null)

    open fun getEintragWithTeilnehmer(id: Long?): KalenderEintrag =
        kalenderEintragRepository.findByIdWithTeilnehmer(id)

    @Transactional
    open fun saveEintrag(
        eintrag: KalenderEintrag,
        projektId: Long?,
        kundeId: Long?,
        lieferantId: Long?,
        anfrageId: Long?,
        erstellerId: Long?,
        teilnehmerIds: List<Long>?,
    ): KalenderEintrag {
        eintrag.projekt = projektId?.let { projektRepository.findById(it).orElse(null) }
        eintrag.kunde = kundeId?.let { kundeRepository.findById(it).orElse(null) }
        eintrag.lieferant = lieferantId?.let { lieferantenRepository.findById(it).orElse(null) }
        eintrag.anfrage = anfrageId?.let { anfrageRepository.findById(it).orElse(null) }

        if (erstellerId != null && eintrag.ersteller == null) {
            eintrag.ersteller = mitarbeiterRepository.findById(erstellerId).orElse(null)
        }

        if (teilnehmerIds != null) {
            val teilnehmer = mutableSetOf<Mitarbeiter>()
            for (teilnehmerId in teilnehmerIds) {
                mitarbeiterRepository.findById(teilnehmerId).ifPresent { teilnehmer.add(it) }
            }
            eintrag.teilnehmer = teilnehmer
        }

        return kalenderEintragRepository.save(eintrag)
    }

    @Transactional
    open fun saveEintrag(
        eintrag: KalenderEintrag,
        projektId: Long?,
        kundeId: Long?,
        lieferantId: Long?,
        anfrageId: Long?,
    ): KalenderEintrag =
        saveEintrag(eintrag, projektId, kundeId, lieferantId, anfrageId, null, null)

    @Transactional
    open fun deleteEintrag(id: Long?) {
        kalenderEintragRepository.deleteById(id!!)
    }

    open fun getEintraegeForProjekt(projektId: Long?): List<KalenderEintrag> =
        kalenderEintragRepository.findByProjektIdOrderByDatumDesc(projektId)
}
