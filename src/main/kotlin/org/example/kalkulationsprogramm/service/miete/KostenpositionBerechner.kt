package org.example.kalkulationsprogramm.service.miete

import org.example.kalkulationsprogramm.domain.miete.Kostenposition
import org.example.kalkulationsprogramm.domain.miete.KostenpositionBerechnung
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle
import org.example.kalkulationsprogramm.domain.miete.Mietpartei
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselEintrag
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselTyp
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand
import org.example.kalkulationsprogramm.exception.MietabrechnungValidationException
import org.example.kalkulationsprogramm.exception.NotFoundException
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository
import org.example.kalkulationsprogramm.repository.miete.VerteilungsschluesselRepository
import org.example.kalkulationsprogramm.repository.miete.ZaehlerstandRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.LinkedHashMap

@Component
class KostenpositionBerechner(
    private val verteilungsschluesselRepository: VerteilungsschluesselRepository,
    private val zaehlerstandRepository: ZaehlerstandRepository,
    @Suppress("unused") private val verbrauchsgegenstandRepository: VerbrauchsgegenstandRepository,
) {
    fun berechne(
        position: Kostenposition,
        jahr: Int,
        cache: MutableMap<Long, Verteilungsschluessel>?,
    ): KostenpositionVerteilErgebnis {
        val schluesselCache = cache ?: LinkedHashMap()
        val schluessel = ermittleSchluessel(position, schluesselCache)
        if (schluessel == null) {
            val kostenstelle = position.kostenstelle
            val kostenpositionName = kostenpositionLabel(position)
            val kostenstellenName = kostenstelleLabel(kostenstelle)
            val userMessage = "Fuer die Kostenposition \"$kostenpositionName\"" +
                " in der Kostenstelle \"$kostenstellenName\" ist kein Verteilungsschluessel hinterlegt." +
                " Bitte waehlen Sie in der Kostenposition einen Verteilungsschluessel oder hinterlegen Sie einen Standardschluessel in der Kostenstelle."
            val detail = "Kostenposition ID ${position.id} ($kostenpositionName), Kostenstelle " +
                "${kostenstelleDetailName(kostenstelle)}, Jahr ${position.abrechnungsJahr ?: "unbekannt"}" +
                " verfuegt ueber keinen Override, und die Kostenstelle hat keinen Standardschluessel."
            throw MietabrechnungValidationException(userMessage, detail)
        }

        val eintraege = schluessel.eintraege
        if (eintraege.isNullOrEmpty()) {
            val schluesselName = verteilungsschluesselLabel(schluessel)
            val userMessage = "Der Verteilungsschluessel \"$schluesselName\" enthaelt keine Eintraege." +
                " Oeffnen Sie den Verteilungsschluessel und fuegen Sie mindestens eine Mietpartei mit einem Anteil hinzu."
            val detail = "Verteilungsschluessel ${verteilungsschluesselDetailName(schluessel)} hat keine Eintraege."
            throw MietabrechnungValidationException(userMessage, detail)
        }

        val aktiveEintraege = eintraege.filter { it.mietpartei != null }
        if (aktiveEintraege.isEmpty()) {
            val schluesselName = verteilungsschluesselLabel(schluessel)
            val userMessage = "Dem Verteilungsschluessel \"$schluesselName\" sind keine Mietparteien zugeordnet." +
                " Bitte weisen Sie Mietparteien zu oder waehlen Sie einen anderen Verteilungsschluessel."
            val detail = "Verteilungsschluessel ${verteilungsschluesselDetailName(schluessel)}" +
                " enthaelt ausschliesslich Eintraege ohne Mietpartei."
            throw MietabrechnungValidationException(userMessage, detail)
        }

        var verteilVerbrauchsSumme: BigDecimal? = null
        val initialGewichte = when (schluessel.typ) {
            VerteilungsschluesselTyp.PROZENTUAL,
            VerteilungsschluesselTyp.FLAECHE,
                -> aktiveEintraege.map { it.anteil ?: BigDecimal.ZERO }

            VerteilungsschluesselTyp.VERBRAUCH -> {
                val werte = ermittleVerbrauchsgewichte(aktiveEintraege, jahr)
                verteilVerbrauchsSumme = werte.fold(BigDecimal.ZERO, BigDecimal::add)
                werte
            }

            null -> aktiveEintraege.map { it.anteil ?: BigDecimal.ZERO }
        }

        var sumGewichte = initialGewichte.fold(BigDecimal.ZERO, BigDecimal::add)
        var verteilGewichte = initialGewichte
        if (sumGewichte.compareTo(BigDecimal.ZERO) <= 0) {
            verteilGewichte = aktiveEintraege.map { BigDecimal.ONE }
            sumGewichte = BigDecimal.valueOf(aktiveEintraege.size.toLong())
        }

        var verbrauchsSumme = verteilVerbrauchsSumme
        val berechnung = position.berechnung ?: KostenpositionBerechnung.BETRAG
        if (berechnung == KostenpositionBerechnung.VERBRAUCHSFAKTOR &&
            schluessel.typ != VerteilungsschluesselTyp.VERBRAUCH
        ) {
            verbrauchsSumme = berechneVerbrauchAusSchluessel(schluessel, jahr)
        }

        val betrag = berechneBetrag(position, verbrauchsSumme)
        val result = LinkedHashMap<Mietpartei, BigDecimal>()
        var rest = betrag
        for (i in aktiveEintraege.indices) {
            val eintrag = aktiveEintraege[i]
            val partei = eintrag.mietpartei ?: continue
            val gewicht = verteilGewichte[i]
            val anteil = gewicht.divide(sumGewichte, MC)
            val anteilsbetrag = if (i == aktiveEintraege.size - 1) {
                rest.setScale(2, RoundingMode.HALF_UP)
            } else {
                val wert = betrag.multiply(anteil, MC).setScale(2, RoundingMode.HALF_UP)
                rest = rest.subtract(wert, MC)
                wert
            }
            result.merge(partei, anteilsbetrag, BigDecimal::add)
        }

        return KostenpositionVerteilErgebnis(betrag, result, verbrauchsSumme)
    }

    private fun berechneBetrag(position: Kostenposition, verbrauchsSumme: BigDecimal?): BigDecimal {
        val berechnung = position.berechnung ?: KostenpositionBerechnung.BETRAG
        if (berechnung == KostenpositionBerechnung.VERBRAUCHSFAKTOR) {
            val faktor = position.verbrauchsfaktor
            if (faktor == null) {
                val userMessage = "Bitte hinterlegen Sie einen Faktor fuer die Kostenposition \"" +
                    kostenpositionLabel(position) + "\"."
                val detail = "Kostenposition ID ${position.id}" +
                    " ist als VERBRAUCHSFAKTOR markiert, besitzt jedoch keinen Faktor."
                throw MietabrechnungValidationException(userMessage, detail)
            }
            val basis = verbrauchsSumme ?: BigDecimal.ZERO
            return basis.multiply(faktor, MC).setScale(2, RoundingMode.HALF_UP)
        }
        return position.betrag ?: BigDecimal.ZERO
    }

    private fun berechneVerbrauchAusSchluessel(schluessel: Verteilungsschluessel?, jahr: Int): BigDecimal {
        val eintraege = schluessel?.eintraege ?: return BigDecimal.ZERO
        val gegenstaende = eintraege
            .mapNotNull(VerteilungsschluesselEintrag::verbrauchsgegenstand)
            .distinct()
        var summe = BigDecimal.ZERO
        for (gegenstand in gegenstaende) {
            val current = zaehlerstandRepository
                .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr)
                .orElse(null)
            val previous = zaehlerstandRepository
                .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr - 1)
                .orElse(null)
            val verbrauch = berechneVerbrauch(current, previous)
            summe = summe.add(verbrauch.max(BigDecimal.ZERO))
        }
        return summe
    }

    private fun ermittleSchluessel(
        position: Kostenposition,
        cache: MutableMap<Long, Verteilungsschluessel>,
    ): Verteilungsschluessel? {
        position.verteilungsschluesselOverride?.let { override ->
            val id = override.id
            if (id != null) {
                return cache.computeIfAbsent(id, ::ladeSchluessel)
            }
            return override
        }
        val kostenstelle = position.kostenstelle ?: return null
        val standardSchluessel = kostenstelle.standardSchluessel
        val id = standardSchluessel?.id
        return if (id != null) {
            cache.computeIfAbsent(id, ::ladeSchluessel)
        } else {
            standardSchluessel
        }
    }

    private fun ladeSchluessel(id: Long): Verteilungsschluessel =
        verteilungsschluesselRepository.findById(id)
            .orElseThrow { NotFoundException("Verteilungsschluessel $id nicht gefunden") }

    private fun ermittleVerbrauchsgewichte(
        eintraege: List<VerteilungsschluesselEintrag>,
        jahr: Int,
    ): List<BigDecimal> {
        val werte = ArrayList<BigDecimal>(eintraege.size)
        for (eintrag in eintraege) {
            val gegenstand = eintrag.verbrauchsgegenstand
            var verbrauch = BigDecimal.ZERO
            if (gegenstand != null) {
                val current = zaehlerstandRepository
                    .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr)
                    .orElse(null)
                val previous = zaehlerstandRepository
                    .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr - 1)
                    .orElse(null)
                verbrauch = berechneVerbrauch(current, previous)
            }
            werte.add(verbrauch.max(BigDecimal.ZERO))
        }
        return werte
    }

    private fun berechneVerbrauch(current: Zaehlerstand?, previous: Zaehlerstand?): BigDecimal {
        if (current == null) {
            return BigDecimal.ZERO
        }
        current.verbrauch?.let { return it }
        val currentStand = current.stand
        val previousStand = previous?.stand
        return if (currentStand != null && previousStand != null) {
            currentStand.subtract(previousStand, MC)
        } else {
            BigDecimal.ZERO
        }
    }

    private fun kostenpositionLabel(position: Kostenposition?): String {
        if (position == null) {
            return "unbekannte Kostenposition"
        }
        if (!position.beschreibung.isNullOrBlank()) {
            return position.beschreibung!!
        }
        if (!position.belegNummer.isNullOrBlank()) {
            return "Beleg ${position.belegNummer}"
        }
        return "Kostenposition ohne Beschreibung"
    }

    private fun kostenstelleLabel(kostenstelle: Kostenstelle?): String {
        if (kostenstelle == null) {
            return "unbekannte Kostenstelle"
        }
        if (!kostenstelle.name.isNullOrBlank()) {
            return kostenstelle.name!!
        }
        return "Kostenstelle ohne Namen"
    }

    private fun kostenstelleDetailName(kostenstelle: Kostenstelle?): String {
        if (kostenstelle == null) {
            return "unbekannt"
        }
        val name = kostenstelle.name
        val id = kostenstelle.id
        if (!name.isNullOrBlank()) {
            return if (id != null) "$name (ID $id)" else name
        }
        if (id != null) {
            return "ID $id"
        }
        return "ohne Namen"
    }

    private fun verteilungsschluesselLabel(schluessel: Verteilungsschluessel?): String {
        if (schluessel == null) {
            return "unbekannter Verteilungsschluessel"
        }
        if (!schluessel.name.isNullOrBlank()) {
            return schluessel.name!!
        }
        return "Verteilungsschluessel ohne Namen"
    }

    private fun verteilungsschluesselDetailName(schluessel: Verteilungsschluessel?): String {
        if (schluessel == null) {
            return "unbekannt"
        }
        val name = schluessel.name
        val id = schluessel.id
        if (!name.isNullOrBlank()) {
            return if (id != null) "$name (ID $id)" else name
        }
        if (id != null) {
            return "ID $id"
        }
        return "ohne Namen"
    }

    data class KostenpositionVerteilErgebnis(
        val betrag: BigDecimal,
        val anteile: Map<Mietpartei, BigDecimal>,
        val verbrauchsSumme: BigDecimal?,
    ) {
        fun betrag(): BigDecimal = betrag
        fun anteile(): Map<Mietpartei, BigDecimal> = anteile
        fun verbrauchsSumme(): BigDecimal? = verbrauchsSumme
    }

    companion object {
        private val MC: MathContext = MathContext.DECIMAL64
    }
}
