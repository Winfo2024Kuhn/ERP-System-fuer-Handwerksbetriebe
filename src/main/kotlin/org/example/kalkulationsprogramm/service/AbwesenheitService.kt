package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Abwesenheit
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp
import org.example.kalkulationsprogramm.domain.BuchungsTyp
import org.example.kalkulationsprogramm.domain.Feiertag
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Service
class AbwesenheitService(
    private val abwesenheitRepository: AbwesenheitRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val zeitkontoService: ZeitkontoService,
    private val feiertagService: FeiertagService,
    private val monatsSaldoService: MonatsSaldoService,
    private val zeitbuchungRepository: ZeitbuchungRepository,
) {
    @Transactional
    fun bucheAbwesenheit(
        mitarbeiterId: Long,
        datum: LocalDate,
        typ: AbwesenheitsTyp,
        halberTag: Boolean,
    ): Abwesenheit {
        val mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
            .orElseThrow { IllegalArgumentException("Mitarbeiter nicht gefunden: $mitarbeiterId") }

        if (abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(mitarbeiterId, datum, typ)) {
            throw IllegalStateException("Für diesen Tag existiert bereits eine $typ-Buchung")
        }

        if (feiertagService.istFeiertag(datum)) {
            val feiertag = feiertagService.getFeiertagInfo(datum)
                .map(Feiertag::bezeichnung)
                .orElse(datum.toString())
            throw IllegalArgumentException("An Feiertagen kann keine Abwesenheit gebucht werden: $feiertag")
        }

        val zeitkonto = zeitkontoService.getOrCreateZeitkonto(mitarbeiterId)
        val wochentag = datum.dayOfWeek.value
        val sollStunden = zeitkonto.getSollstundenFuerTag(wochentag)

        if (sollStunden.compareTo(BigDecimal.ZERO) <= 0) {
            val tag = datum.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
            throw IllegalArgumentException("Kein Arbeitstag: Am $tag hat dieser Mitarbeiter keine Sollstunden")
        }

        val basisStunden = if (halberTag) {
            sollStunden.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
        } else {
            sollStunden
        }

        val gearbeiteteStunden = if (typ == AbwesenheitsTyp.KRANKHEIT) {
            berechneGearbeiteteStunden(mitarbeiterId, datum)
        } else {
            BigDecimal.ZERO
        }
        val stundenZuBuchen = if (typ == AbwesenheitsTyp.KRANKHEIT) {
            basisStunden.subtract(gearbeiteteStunden).max(BigDecimal.ZERO)
        } else {
            basisStunden
        }

        if (typ == AbwesenheitsTyp.ZEITAUSGLEICH) {
            val aktuellerSaldo = berechneAktuellenSaldo(mitarbeiterId)
            if (aktuellerSaldo.compareTo(stundenZuBuchen) < 0) {
                throw IllegalStateException(
                    "Nicht genügend Überstunden für Zeitausgleich. " +
                        "Aktueller Saldo: ${aktuellerSaldo.setScale(1, RoundingMode.HALF_UP)}h, " +
                        "benötigt: ${stundenZuBuchen.setScale(1, RoundingMode.HALF_UP)}h",
                )
            }
        }

        val abwesenheit = Abwesenheit().apply {
            this.mitarbeiter = mitarbeiter
            this.datum = datum
            this.typ = typ
            stunden = stundenZuBuchen
            notiz = if (typ == AbwesenheitsTyp.KRANKHEIT && gearbeiteteStunden.compareTo(BigDecimal.ZERO) > 0) {
                "Krankheit (abzgl. ${gearbeiteteStunden.stripTrailingZeros().toPlainString()} h gearbeitet)"
            } else {
                if (halberTag) "Halber Tag (manuell gebucht)" else "Manuell gebucht"
            }
        }

        val gespeichert = abwesenheitRepository.save(abwesenheit)
        monatsSaldoService.invalidiereFuerDatum(mitarbeiterId, datum)
        return gespeichert
    }

    private fun berechneGearbeiteteStunden(mitarbeiterId: Long, datum: LocalDate): BigDecimal {
        val tagStart = datum.atStartOfDay()
        val tagEnde = datum.atTime(23, 59, 59)
        return zeitbuchungRepository
            .findByMitarbeiterIdAndStartZeitBetween(mitarbeiterId, tagStart, tagEnde)
            .asSequence()
            .filter { it.typ != BuchungsTyp.PAUSE }
            .mapNotNull { it.anzahlInStunden }
            .fold(BigDecimal.ZERO, BigDecimal::add)
    }

    private fun berechneAktuellenSaldo(mitarbeiterId: Long): BigDecimal {
        val heute = LocalDate.now()
        val currentYear = heute.year
        val currentMonth = heute.monthValue

        var saldo = BigDecimal.ZERO
        for (month in 1..currentMonth) {
            val sollMonat = zeitkontoService.berechneSollstundenFuerMonat(mitarbeiterId, currentYear, month)
            saldo = saldo.subtract(sollMonat)
        }

        val jahresanfang = LocalDate.of(currentYear, 1, 1)
        val abwesenheitsStunden = abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(
            mitarbeiterId,
            jahresanfang,
            heute,
        )
        return saldo.add(abwesenheitsStunden)
    }

    @Transactional
    fun loescheAbwesenheit(abwesenheitId: Long) {
        val abwesenheit = abwesenheitRepository.findById(abwesenheitId)
            .orElseThrow { IllegalArgumentException("Abwesenheit nicht gefunden: $abwesenheitId") }

        val mitarbeiterId = abwesenheit.mitarbeiter?.id
        val datum = abwesenheit.datum

        abwesenheitRepository.deleteById(abwesenheitId)

        if (mitarbeiterId != null && datum != null) {
            monatsSaldoService.invalidiereFuerDatum(mitarbeiterId, datum)
        }
    }

    fun getAbwesenheitenByMitarbeiter(mitarbeiterId: Long): List<Abwesenheit> =
        abwesenheitRepository.findByMitarbeiterIdOrderByDatumDesc(mitarbeiterId)

    fun getAbwesenheitenByMitarbeiterAndZeitraum(
        mitarbeiterId: Long,
        von: LocalDate,
        bis: LocalDate,
    ): List<Abwesenheit> =
        abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(mitarbeiterId, von, bis)

    fun getAllAbwesenheitenForZeitraum(von: LocalDate, bis: LocalDate): List<Abwesenheit> =
        abwesenheitRepository.findAllByDatumBetween(von, bis)
}
