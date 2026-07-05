package org.example.kalkulationsprogramm.service.miete

import org.example.kalkulationsprogramm.domain.miete.Kostenposition
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle
import org.example.kalkulationsprogramm.domain.miete.Mietpartei
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand
import org.example.kalkulationsprogramm.exception.NotFoundException
import org.example.kalkulationsprogramm.repository.miete.KostenpositionRepository
import org.example.kalkulationsprogramm.repository.miete.MieteKostenstelleRepository
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository
import org.example.kalkulationsprogramm.repository.miete.ZaehlerstandRepository
import org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.LinkedList

@Service
@Transactional(readOnly = true)
class MietabrechnungService(
    private val mietobjektRepository: MietobjektRepository,
    private val kostenstelleRepository: MieteKostenstelleRepository,
    private val kostenpositionRepository: KostenpositionRepository,
    private val verbrauchsgegenstandRepository: VerbrauchsgegenstandRepository,
    private val zaehlerstandRepository: ZaehlerstandRepository,
    private val kostenpositionBerechner: KostenpositionBerechner,
) {
    fun berechneJahresabrechnung(mietobjektId: Long, jahr: Int): AnnualAccountingResult {
        val mietobjekt = mietobjektRepository.findById(mietobjektId)
            .orElseThrow { NotFoundException("Mietobjekt $mietobjektId nicht gefunden") }

        val aktuellePositionen = kostenpositionRepository
            .findByKostenstelleMietobjektIdAndAbrechnungsJahr(mietobjektId, jahr)
        val vorjahrPositionen = kostenpositionRepository
            .findByKostenstelleMietobjektIdAndAbrechnungsJahr(mietobjektId, jahr - 1)

        val aktuelles = aggregiereKosten(aktuellePositionen, jahr)
        val vorjahr = aggregiereKosten(vorjahrPositionen, jahr - 1)
        val kostenstellen = kostenstelleRepository.findByMietobjektIdOrderByNameAsc(mietobjektId)

        val kostenstellenErgebnisse = kostenstellen.map { kostenstelle ->
            val sumAktuell = aktuelles.summeKostenstelle(kostenstelle)
            val sumVorjahr = vorjahr.summeKostenstelle(kostenstelle)
            val positionen = aktuelles.positionen(kostenstelle)
            val anteile = aktuelles.parteianteile(kostenstelle).entries
                .sortedBy { it.key.name }
                .map {
                    AnnualAccountingResult.Parteianteil.builder()
                        .mietpartei(it.key)
                        .betrag(it.value)
                        .build()
                }
            AnnualAccountingResult.KostenstellenResult.builder()
                .kostenstelle(kostenstelle)
                .gesamtkosten(sumAktuell)
                .gesamtkostenVorjahr(sumVorjahr)
                .positionen(positionen)
                .parteianteile(anteile)
                .build()
        }

        val parteien = LinkedHashSet<Mietpartei>().apply {
            addAll(aktuelles.summePartei.keys)
            addAll(vorjahr.summePartei.keys)
        }

        val parteiErgebnisse = parteien
            .sortedBy { it.name }
            .map { partei ->
                val aktuell = aktuelles.summePartei.getOrDefault(partei, BigDecimal.ZERO)
                val prev = vorjahr.summePartei.getOrDefault(partei, BigDecimal.ZERO)
                var monat: BigDecimal =
                    if (partei.rolle == MietparteiRolle.MIETER && partei.monatlicherVorschuss != null) {
                        partei.monatlicherVorschuss ?: BigDecimal.ZERO
                    } else {
                        BigDecimal.ZERO
                    }
                if (monat < BigDecimal.ZERO) {
                    monat = BigDecimal.ZERO
                }
                monat = monat.setScale(2, RoundingMode.HALF_UP)
                val jahresVorauszahlung = monat.multiply(BigDecimal.valueOf(12), MC).setScale(2, RoundingMode.HALF_UP)
                val saldo = aktuell.subtract(jahresVorauszahlung, MC).setScale(2, RoundingMode.HALF_UP)

                AnnualAccountingResult.ParteiErgebnis.builder()
                    .mietpartei(partei)
                    .betrag(aktuell)
                    .betragVorjahr(prev)
                    .differenz(aktuell.subtract(prev, MC))
                    .vorauszahlungMonatlich(monat)
                    .vorauszahlungJahr(jahresVorauszahlung)
                    .saldo(saldo)
                    .build()
            }

        val verbrauchsvergleiche = berechneVerbrauchsvergleiche(mietobjektId, jahr)
        val sumAktuell = aktuelles.gesamtsumme
        val sumVorjahr = vorjahr.gesamtsumme

        return AnnualAccountingResult.builder()
            .mietobjektId(mietobjekt.id)
            .mietobjektName(mietobjekt.name)
            .mietobjektStrasse(normalize(mietobjekt.strasse))
            .mietobjektPlz(normalize(mietobjekt.plz))
            .mietobjektOrt(normalize(mietobjekt.ort))
            .abrechnungsJahr(jahr)
            .gesamtkosten(sumAktuell)
            .gesamtkostenVorjahr(sumVorjahr)
            .gesamtkostenDifferenz(sumAktuell.subtract(sumVorjahr, MC))
            .kostenstellen(kostenstellenErgebnisse)
            .parteien(parteiErgebnisse)
            .verbrauchsvergleiche(verbrauchsvergleiche)
            .build()
    }

    private fun aggregiereKosten(positionen: List<Kostenposition>, jahr: Int): JahrAggregation {
        val aggregation = JahrAggregation()
        val cache = LinkedHashMap<Long, Verteilungsschluessel>()

        for (position in positionen) {
            val kostenstelle = position.kostenstelle ?: continue
            val ergebnis = kostenpositionBerechner.berechne(position, jahr, cache)
            val verteilung = ergebnis.anteile()
            val betrag = ergebnis.betrag() ?: BigDecimal.ZERO

            aggregation.addPosition(kostenstelle, position)
            aggregation.addKostenstelleSumme(kostenstelle, betrag)
            aggregation.addGesamtsumme(betrag)
            aggregation.addParteianteile(kostenstelle, verteilung)
        }

        return aggregation
    }

    private fun berechneVerbrauchsvergleiche(
        mietobjektId: Long,
        jahr: Int,
    ): List<AnnualAccountingResult.Verbrauchsvergleich> {
        val gegenstaende = verbrauchsgegenstandRepository.findByRaumMietobjektId(mietobjektId)
        if (gegenstaende.isEmpty()) {
            return emptyList()
        }

        val aktuelle = zaehlerstandRepository
            .findByVerbrauchsgegenstandInAndAbrechnungsJahr(gegenstaende, jahr)
            .filter { it.verbrauchsgegenstand?.id != null }
            .associateBy { it.verbrauchsgegenstand!!.id }
        val vorjahr = zaehlerstandRepository
            .findByVerbrauchsgegenstandInAndAbrechnungsJahr(gegenstaende, jahr - 1)
            .filter { it.verbrauchsgegenstand?.id != null }
            .associateBy { it.verbrauchsgegenstand!!.id }

        val result = LinkedList<AnnualAccountingResult.Verbrauchsvergleich>()
        for (gegenstand in gegenstaende) {
            val cur = aktuelle[gegenstand.id]
            val prev = vorjahr[gegenstand.id]
            val verbrauchJahr = berechneVerbrauch(cur, prev)
            val prevJahr = prev?.abrechnungsJahr
            val prevPrev =
                if (prevJahr != null) {
                    zaehlerstandRepository
                        .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, prevJahr - 1)
                        .orElse(null)
                } else {
                    null
                }
            val verbrauchVorjahr = berechneVerbrauch(prev, prevPrev)
            val differenz = verbrauchJahr.subtract(verbrauchVorjahr, MC)
            val raumName = gegenstand.raum?.name?.trim()?.takeIf { it.isNotEmpty() }

            result.add(
                AnnualAccountingResult.Verbrauchsvergleich.builder()
                    .verbrauchsgegenstand(gegenstand)
                    .raumName(raumName)
                    .verbrauchJahr(verbrauchJahr)
                    .verbrauchVorjahr(verbrauchVorjahr)
                    .differenz(differenz)
                    .build(),
            )
        }
        result.sortBy { it.verbrauchsgegenstand?.name }
        return result
    }

    private fun normalize(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun berechneVerbrauch(current: Zaehlerstand?, previous: Zaehlerstand?): BigDecimal {
        if (current == null) {
            return BigDecimal.ZERO
        }
        current.verbrauch?.let { return it }

        val currentStand = current.stand
        val previousStand = previous?.stand
        if (currentStand != null && previousStand != null) {
            return currentStand.subtract(previousStand, MC)
        }
        return BigDecimal.ZERO
    }

    private class JahrAggregation {
        private val summeJeKostenstelle = LinkedHashMap<Kostenstelle, BigDecimal>()
        private val anteileJeKostenstelle = LinkedHashMap<Kostenstelle, MutableMap<Mietpartei, BigDecimal>>()
        private val positionenJeKostenstelle = LinkedHashMap<Kostenstelle, MutableList<Kostenposition>>()
        val summePartei: MutableMap<Mietpartei, BigDecimal> = LinkedHashMap()
        var gesamtsumme: BigDecimal = BigDecimal.ZERO
            private set

        fun addKostenstelleSumme(kostenstelle: Kostenstelle, betrag: BigDecimal) {
            summeJeKostenstelle.merge(kostenstelle, betrag, BigDecimal::add)
        }

        fun addParteianteile(kostenstelle: Kostenstelle, anteile: Map<Mietpartei, BigDecimal>) {
            val map = anteileJeKostenstelle.computeIfAbsent(kostenstelle) { LinkedHashMap() }
            anteile.forEach { (partei, betrag) ->
                map.merge(partei, betrag, BigDecimal::add)
                summePartei.merge(partei, betrag, BigDecimal::add)
            }
        }

        fun addPosition(kostenstelle: Kostenstelle, position: Kostenposition) {
            positionenJeKostenstelle.computeIfAbsent(kostenstelle) { ArrayList() }.add(position)
        }

        fun addGesamtsumme(betrag: BigDecimal) {
            gesamtsumme = gesamtsumme.add(betrag)
        }

        fun summeKostenstelle(kostenstelle: Kostenstelle): BigDecimal =
            summeJeKostenstelle.getOrDefault(kostenstelle, BigDecimal.ZERO)

        fun parteianteile(kostenstelle: Kostenstelle): Map<Mietpartei, BigDecimal> =
            anteileJeKostenstelle.getOrDefault(kostenstelle, emptyMap())

        fun positionen(kostenstelle: Kostenstelle): List<Kostenposition> =
            positionenJeKostenstelle.getOrDefault(kostenstelle, emptyList())
    }

    private companion object {
        val MC: MathContext = MathContext.DECIMAL64
    }
}
