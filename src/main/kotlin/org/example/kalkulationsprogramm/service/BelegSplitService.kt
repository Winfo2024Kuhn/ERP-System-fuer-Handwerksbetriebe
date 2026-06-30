package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus
import org.example.kalkulationsprogramm.domain.BelegPosition
import org.example.kalkulationsprogramm.repository.BelegPositionRepository
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class BelegSplitService(
    private val belegRepository: BelegRepository,
    private val belegPositionRepository: BelegPositionRepository,
    private val mwstRechnerService: MwstRechnerService,
) {
    @Transactional(readOnly = true)
    fun ladePositionen(belegId: Long?): List<BelegPosition> =
        belegPositionRepository.findByBelegIdOrderBySortierungAsc(belegId)

    @Transactional
    fun speicherePositionen(beleg: Beleg?, neue: List<BelegPosition>?) {
        if (beleg?.id == null) {
            return
        }
        belegPositionRepository.deleteByBelegId(beleg.id)
        if (neue.isNullOrEmpty()) {
            recomputeFirmaSummen(beleg, emptyList())
            return
        }

        var idx = 0
        for (position in neue) {
            position.beleg = beleg
            if (position.sortierung <= 0) {
                position.sortierung = idx
            }
            idx++
        }
        val persistiert = belegPositionRepository.saveAll(neue)
        recomputeFirmaSummen(beleg, persistiert)
    }

    @Transactional
    fun aktualisiereAuswahl(belegId: Long, firmaPositionIds: Set<Long>?): Beleg {
        val beleg = belegRepository.findById(belegId)
            .orElseThrow { IllegalArgumentException("Beleg nicht gefunden: $belegId") }
        if (beleg.aufteilungsModus != BelegAufteilungsModus.TEILWEISE) {
            throw IllegalArgumentException("Beleg ist nicht auf TEILWEISE gestellt — Positions-Auswahl nicht erlaubt")
        }

        val auswahl = firmaPositionIds ?: emptySet()
        val positionen = belegPositionRepository.findByBelegIdOrderBySortierungAsc(belegId)
        for (position in positionen) {
            position.istFuerFirma = auswahl.contains(position.id)
        }
        recomputeFirmaSummen(beleg, positionen)
        return belegRepository.save(beleg)
    }

    fun recomputeFirmaSummen(beleg: Beleg?) {
        if (beleg == null) {
            return
        }
        val positionen = if (beleg.id != null) {
            belegPositionRepository.findByBelegIdOrderBySortierungAsc(beleg.id)
        } else {
            emptyList()
        }
        recomputeFirmaSummen(beleg, positionen)
    }

    fun recomputeFirmaSummen(beleg: Beleg?, positionen: List<BelegPosition>) {
        if (beleg == null) {
            return
        }
        if (beleg.aufteilungsModus != BelegAufteilungsModus.TEILWEISE) {
            beleg.betragFirmaNetto = null
            beleg.betragFirmaBrutto = null
            beleg.betragFirmaMwst = null
            return
        }

        val firmaPositionen = positionen.filter { it.isIstFuerFirma() }
        var netto = BigDecimal.ZERO
        var brutto = BigDecimal.ZERO
        val mwstJeSatz = HashMap<BigDecimal, BigDecimal>()

        for (position in firmaPositionen) {
            var pBrutto = position.betragBrutto
            var pNetto = position.betragNetto
            val satz = position.mwstSatz

            if (pBrutto == null && pNetto != null && satz != null) {
                pBrutto = mwstRechnerService.bruttoAusNetto(pNetto, satz)
            }
            if (pNetto == null && pBrutto != null && satz != null) {
                pNetto = mwstRechnerService.nettoAusBrutto(pBrutto, satz)
            }
            if (pNetto == null && pBrutto != null) {
                pNetto = pBrutto
            }
            if (pBrutto == null && pNetto != null) {
                pBrutto = pNetto
            }
            if (pNetto == null || pBrutto == null) {
                continue
            }

            netto = netto.add(pNetto)
            brutto = brutto.add(pBrutto)

            val mwstPos = pBrutto.subtract(pNetto)
            val key = satz ?: BigDecimal.ZERO
            mwstJeSatz.merge(key, mwstPos, BigDecimal::add)
        }

        val mwstSumme = mwstJeSatz.values.fold(BigDecimal.ZERO, BigDecimal::add)
        beleg.betragFirmaNetto = netto.setScale(2, RoundingMode.HALF_UP)
        beleg.betragFirmaBrutto = brutto.setScale(2, RoundingMode.HALF_UP)
        beleg.betragFirmaMwst = mwstSumme.setScale(2, RoundingMode.HALF_UP)
    }
}
