package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.BelegKategorie
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus
import org.example.kalkulationsprogramm.domain.BelegStatus
import org.example.kalkulationsprogramm.domain.KasseEinstellung
import org.example.kalkulationsprogramm.domain.Kostenstelle
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.Sachkonto
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@Service
class KasseShortcutService(
    private val belegRepository: BelegRepository,
    private val kasseEinstellungRepository: KasseEinstellungRepository,
    private val kasseSaldoService: KasseSaldoService,
) {
    @Transactional
    fun bankAbhebung(
        betrag: BigDecimal?,
        datum: LocalDate?,
        belegNr: String?,
        beschreibung: String?,
        ersteller: Mitarbeiter?,
    ): Beleg {
        assertPositiv(betrag, "Betrag")
        assertNichtNull(datum, "Datum")

        val beleg = baseBeleg(BelegKategorie.KASSE_EINNAHME, datum!!, betrag!!, ersteller)
        beleg.belegNummer = belegNr
        beleg.beschreibung = if (!beschreibung.isNullOrBlank()) beschreibung else "Bank-Abhebung"
        return belegRepository.save(beleg)
    }

    @Transactional
    fun privatEinlage(
        betrag: BigDecimal?,
        datum: LocalDate?,
        beschreibung: String?,
        ersteller: Mitarbeiter?,
    ): Beleg {
        assertPositiv(betrag, "Betrag")
        assertNichtNull(datum, "Datum")

        val beleg = baseBeleg(BelegKategorie.PRIVATEINLAGE, datum!!, betrag!!, ersteller)
        beleg.beschreibung = if (!beschreibung.isNullOrBlank()) beschreibung else "Privateinlage"
        ladeEinstellung().map(KasseEinstellung::privateinlageSachkonto).ifPresent { beleg.sachkonto = it }
        return belegRepository.save(beleg)
    }

    @Transactional
    fun privatEntnahme(
        betrag: BigDecimal?,
        datum: LocalDate?,
        beschreibung: String?,
        ersteller: Mitarbeiter?,
    ): Beleg {
        assertPositiv(betrag, "Betrag")
        assertNichtNull(datum, "Datum")

        val projiziert = kasseSaldoService.projiziereSaldo(null, null, BelegKategorie.PRIVATENTNAHME, betrag)
        kasseSaldoService.assertSaldoMindestensMindestbestand(projiziert)

        val beleg = baseBeleg(BelegKategorie.PRIVATENTNAHME, datum!!, betrag!!, ersteller)
        beleg.beschreibung = if (!beschreibung.isNullOrBlank()) beschreibung else "Privatentnahme"
        return belegRepository.save(beleg)
    }

    @Transactional
    fun lohnZahlung(
        betrag: BigDecimal?,
        datum: LocalDate?,
        empfaengerName: String?,
        sachkonto: Sachkonto?,
        kostenstelle: Kostenstelle?,
        ersteller: Mitarbeiter?,
    ): LohnZahlungResult {
        assertPositiv(betrag, "Betrag")
        assertNichtNull(datum, "Datum")
        if (sachkonto == null) {
            throw IllegalArgumentException("Sachkonto fuer Lohn-Buchung fehlt")
        }

        val betragValue = betrag!!
        val mindestbestand = kasseSaldoService.getMindestbestand()
        val aktuellerSaldo = kasseSaldoService.berechneAktuellenSaldo()
        val saldoNachLohn = aktuellerSaldo.subtract(betragValue)

        var einlage: Beleg? = null
        if (saldoNachLohn.compareTo(mindestbestand) < 0) {
            val benoetigteEinlage = mindestbestand.subtract(saldoNachLohn)
                .setScale(2, RoundingMode.HALF_UP)
            einlage = privatEinlage(benoetigteEinlage, datum, "Auto-Privateinlage fuer Lohnzahlung", ersteller)
        }

        val lohn = baseBeleg(BelegKategorie.KASSE_AUSGABE, datum!!, betragValue, ersteller)
        lohn.beschreibung = "Lohn" + if (!empfaengerName.isNullOrBlank()) " $empfaengerName" else ""
        lohn.sachkonto = sachkonto
        if (kostenstelle != null) {
            lohn.kostenstelle = kostenstelle
        }

        val lohnGespeichert = belegRepository.save(lohn)
        val neuerSaldo = kasseSaldoService.berechneAktuellenSaldo()
        return LohnZahlungResult(einlage, lohnGespeichert, neuerSaldo)
    }

    private fun baseBeleg(
        kategorie: BelegKategorie,
        datum: LocalDate,
        betrag: BigDecimal,
        ersteller: Mitarbeiter?,
    ): Beleg =
        Beleg().apply {
            belegKategorie = kategorie
            status = BelegStatus.VALIDIERT
            kiAnalyseStatus = BelegKiAnalyseStatus.DONE
            istUmbuchung = true
            belegDatum = datum
            betragBrutto = betrag
            uploadDatum = LocalDateTime.now()
            validiertAm = LocalDateTime.now()
            uploadedBy = ersteller
            validiertVon = ersteller
        }

    private fun ladeEinstellung(): Optional<KasseEinstellung> = kasseEinstellungRepository.findSingleton()

    data class LohnZahlungResult(
        val privateinlage: Beleg?,
        val lohnBeleg: Beleg?,
        val neuerSaldo: BigDecimal?,
    ) {
        fun privateinlage(): Beleg? = privateinlage
        fun lohnBeleg(): Beleg? = lohnBeleg
        fun neuerSaldo(): BigDecimal? = neuerSaldo
    }

    companion object {
        private fun assertPositiv(value: BigDecimal?, feld: String) {
            if (value == null || value.signum() <= 0) {
                throw IllegalArgumentException("$feld fehlt oder ist nicht positiv")
            }
        }

        private fun assertNichtNull(value: Any?, feld: String) {
            if (value == null) {
                throw IllegalArgumentException("$feld fehlt")
            }
        }
    }
}
