package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.BelegKategorie
import org.example.kalkulationsprogramm.domain.BelegStatus
import org.example.kalkulationsprogramm.domain.KasseEinstellung
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class KasseSaldoService(
    private val belegRepository: BelegRepository,
    private val kasseEinstellungRepository: KasseEinstellungRepository,
) {
    @Transactional(readOnly = true)
    fun berechneAktuellenSaldo(): BigDecimal {
        val belege = belegRepository.findValidierteByKategorien(BelegStatus.VALIDIERT, BAR_KATEGORIEN)
        var saldo = BigDecimal.ZERO
        for (beleg in belege) {
            saldo = saldo.add(signedBetrag(belegKategorie(beleg), betragBrutto(beleg)))
        }
        return saldo.setScale(2, RoundingMode.HALF_UP)
    }

    @Transactional(readOnly = true)
    fun projiziereSaldo(
        kategorieAlt: BelegKategorie?,
        bruttoAlt: BigDecimal?,
        kategorieNeu: BelegKategorie?,
        bruttoNeu: BigDecimal?,
    ): BigDecimal {
        var saldo = berechneAktuellenSaldo()
        if (kategorieAlt != null && bruttoAlt != null && kategorieAlt.istKassenBewegung()) {
            saldo = saldo.subtract(signedBetrag(kategorieAlt, bruttoAlt))
        }
        if (kategorieNeu != null && bruttoNeu != null && kategorieNeu.istKassenBewegung()) {
            saldo = saldo.add(signedBetrag(kategorieNeu, bruttoNeu))
        }
        return saldo.setScale(2, RoundingMode.HALF_UP)
    }

    @Transactional(readOnly = true)
    fun getMindestbestand(): BigDecimal =
        kasseEinstellungRepository.findSingleton()
            .map(KasseEinstellung::mindestbestand)
            .orElse(BigDecimal.ZERO) ?: BigDecimal.ZERO

    fun assertSaldoMindestensMindestbestand(projizierterSaldo: BigDecimal) {
        val min = getMindestbestand()
        if (projizierterSaldo < min) {
            throw KasseUnterdeckungException(projizierterSaldo, min)
        }
    }

    private fun belegKategorie(beleg: Beleg): BelegKategorie? =
        invokeGetter(beleg, "getBelegKategorie") as? BelegKategorie

    private fun betragBrutto(beleg: Beleg): BigDecimal? =
        invokeGetter(beleg, "getBetragBrutto") as? BigDecimal

    private fun invokeGetter(target: Any, method: String): Any? =
        target.javaClass.getMethod(method).invoke(target)

    companion object {
        private val BAR_KATEGORIEN: List<BelegKategorie> = listOf(
            BelegKategorie.KASSE_EINNAHME,
            BelegKategorie.KASSE_AUSGABE,
            BelegKategorie.PRIVATENTNAHME,
            BelegKategorie.PRIVATEINLAGE,
        )

        private fun signedBetrag(kategorie: BelegKategorie?, brutto: BigDecimal?): BigDecimal {
            if (brutto == null) {
                return BigDecimal.ZERO
            }
            return if (kategorie?.istAusgang() == true) brutto.negate() else brutto
        }
    }
}
