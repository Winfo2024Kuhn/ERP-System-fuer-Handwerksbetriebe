package org.example.kalkulationsprogramm.service.miete.model

import org.example.kalkulationsprogramm.domain.miete.Kostenposition
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle
import org.example.kalkulationsprogramm.domain.miete.Mietpartei
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand
import java.math.BigDecimal

class AnnualAccountingResult(
    val mietobjektId: Long?,
    val mietobjektName: String?,
    val mietobjektStrasse: String?,
    val mietobjektPlz: String?,
    val mietobjektOrt: String?,
    val abrechnungsJahr: Int?,
    val gesamtkosten: BigDecimal?,
    val gesamtkostenVorjahr: BigDecimal?,
    val gesamtkostenDifferenz: BigDecimal?,
    val kostenstellen: List<KostenstellenResult>?,
    val parteien: List<ParteiErgebnis>?,
    val verbrauchsvergleiche: List<Verbrauchsvergleich>?,
) {
    class AnnualAccountingResultBuilder {
        private var mietobjektId: Long? = null
        private var mietobjektName: String? = null
        private var mietobjektStrasse: String? = null
        private var mietobjektPlz: String? = null
        private var mietobjektOrt: String? = null
        private var abrechnungsJahr: Int? = null
        private var gesamtkosten: BigDecimal? = null
        private var gesamtkostenVorjahr: BigDecimal? = null
        private var gesamtkostenDifferenz: BigDecimal? = null
        private var kostenstellen: List<KostenstellenResult>? = null
        private var parteien: List<ParteiErgebnis>? = null
        private var verbrauchsvergleiche: List<Verbrauchsvergleich>? = null

        fun mietobjektId(mietobjektId: Long?) = apply { this.mietobjektId = mietobjektId }
        fun mietobjektName(mietobjektName: String?) = apply { this.mietobjektName = mietobjektName }
        fun mietobjektStrasse(mietobjektStrasse: String?) = apply { this.mietobjektStrasse = mietobjektStrasse }
        fun mietobjektPlz(mietobjektPlz: String?) = apply { this.mietobjektPlz = mietobjektPlz }
        fun mietobjektOrt(mietobjektOrt: String?) = apply { this.mietobjektOrt = mietobjektOrt }
        fun abrechnungsJahr(abrechnungsJahr: Int?) = apply { this.abrechnungsJahr = abrechnungsJahr }
        fun gesamtkosten(gesamtkosten: BigDecimal?) = apply { this.gesamtkosten = gesamtkosten }
        fun gesamtkostenVorjahr(gesamtkostenVorjahr: BigDecimal?) = apply { this.gesamtkostenVorjahr = gesamtkostenVorjahr }
        fun gesamtkostenDifferenz(gesamtkostenDifferenz: BigDecimal?) = apply { this.gesamtkostenDifferenz = gesamtkostenDifferenz }
        fun kostenstellen(kostenstellen: List<KostenstellenResult>?) = apply { this.kostenstellen = kostenstellen }
        fun parteien(parteien: List<ParteiErgebnis>?) = apply { this.parteien = parteien }
        fun verbrauchsvergleiche(verbrauchsvergleiche: List<Verbrauchsvergleich>?) =
            apply { this.verbrauchsvergleiche = verbrauchsvergleiche }

        fun build(): AnnualAccountingResult =
            AnnualAccountingResult(
                mietobjektId,
                mietobjektName,
                mietobjektStrasse,
                mietobjektPlz,
                mietobjektOrt,
                abrechnungsJahr,
                gesamtkosten,
                gesamtkostenVorjahr,
                gesamtkostenDifferenz,
                kostenstellen,
                parteien,
                verbrauchsvergleiche,
            )
    }

    class KostenstellenResult(
        val kostenstelle: Kostenstelle?,
        val gesamtkosten: BigDecimal?,
        val gesamtkostenVorjahr: BigDecimal?,
        val positionen: List<Kostenposition>?,
        val parteianteile: List<Parteianteil>?,
    ) {
        class KostenstellenResultBuilder {
            private var kostenstelle: Kostenstelle? = null
            private var gesamtkosten: BigDecimal? = null
            private var gesamtkostenVorjahr: BigDecimal? = null
            private var positionen: List<Kostenposition>? = null
            private var parteianteile: List<Parteianteil>? = null

            fun kostenstelle(kostenstelle: Kostenstelle?) = apply { this.kostenstelle = kostenstelle }
            fun gesamtkosten(gesamtkosten: BigDecimal?) = apply { this.gesamtkosten = gesamtkosten }
            fun gesamtkostenVorjahr(gesamtkostenVorjahr: BigDecimal?) = apply { this.gesamtkostenVorjahr = gesamtkostenVorjahr }
            fun positionen(positionen: List<Kostenposition>?) = apply { this.positionen = positionen }
            fun parteianteile(parteianteile: List<Parteianteil>?) = apply { this.parteianteile = parteianteile }
            fun build(): KostenstellenResult =
                KostenstellenResult(kostenstelle, gesamtkosten, gesamtkostenVorjahr, positionen, parteianteile)
        }

        companion object {
            @JvmStatic
            fun builder(): KostenstellenResultBuilder = KostenstellenResultBuilder()
        }
    }

    class Parteianteil(
        val mietpartei: Mietpartei?,
        val betrag: BigDecimal?,
    ) {
        class ParteianteilBuilder {
            private var mietpartei: Mietpartei? = null
            private var betrag: BigDecimal? = null

            fun mietpartei(mietpartei: Mietpartei?) = apply { this.mietpartei = mietpartei }
            fun betrag(betrag: BigDecimal?) = apply { this.betrag = betrag }
            fun build(): Parteianteil = Parteianteil(mietpartei, betrag)
        }

        companion object {
            @JvmStatic
            fun builder(): ParteianteilBuilder = ParteianteilBuilder()
        }
    }

    class ParteiErgebnis(
        val mietpartei: Mietpartei?,
        val betrag: BigDecimal?,
        val betragVorjahr: BigDecimal?,
        val differenz: BigDecimal?,
        val vorauszahlungMonatlich: BigDecimal?,
        val vorauszahlungJahr: BigDecimal?,
        val saldo: BigDecimal?,
    ) {
        class ParteiErgebnisBuilder {
            private var mietpartei: Mietpartei? = null
            private var betrag: BigDecimal? = null
            private var betragVorjahr: BigDecimal? = null
            private var differenz: BigDecimal? = null
            private var vorauszahlungMonatlich: BigDecimal? = null
            private var vorauszahlungJahr: BigDecimal? = null
            private var saldo: BigDecimal? = null

            fun mietpartei(mietpartei: Mietpartei?) = apply { this.mietpartei = mietpartei }
            fun betrag(betrag: BigDecimal?) = apply { this.betrag = betrag }
            fun betragVorjahr(betragVorjahr: BigDecimal?) = apply { this.betragVorjahr = betragVorjahr }
            fun differenz(differenz: BigDecimal?) = apply { this.differenz = differenz }
            fun vorauszahlungMonatlich(vorauszahlungMonatlich: BigDecimal?) =
                apply { this.vorauszahlungMonatlich = vorauszahlungMonatlich }

            fun vorauszahlungJahr(vorauszahlungJahr: BigDecimal?) = apply { this.vorauszahlungJahr = vorauszahlungJahr }
            fun saldo(saldo: BigDecimal?) = apply { this.saldo = saldo }
            fun build(): ParteiErgebnis =
                ParteiErgebnis(mietpartei, betrag, betragVorjahr, differenz, vorauszahlungMonatlich, vorauszahlungJahr, saldo)
        }

        companion object {
            @JvmStatic
            fun builder(): ParteiErgebnisBuilder = ParteiErgebnisBuilder()
        }
    }

    class Verbrauchsvergleich(
        val verbrauchsgegenstand: Verbrauchsgegenstand?,
        val raumName: String?,
        val verbrauchJahr: BigDecimal?,
        val verbrauchVorjahr: BigDecimal?,
        val differenz: BigDecimal?,
    ) {
        class VerbrauchsvergleichBuilder {
            private var verbrauchsgegenstand: Verbrauchsgegenstand? = null
            private var raumName: String? = null
            private var verbrauchJahr: BigDecimal? = null
            private var verbrauchVorjahr: BigDecimal? = null
            private var differenz: BigDecimal? = null

            fun verbrauchsgegenstand(verbrauchsgegenstand: Verbrauchsgegenstand?) =
                apply { this.verbrauchsgegenstand = verbrauchsgegenstand }

            fun raumName(raumName: String?) = apply { this.raumName = raumName }
            fun verbrauchJahr(verbrauchJahr: BigDecimal?) = apply { this.verbrauchJahr = verbrauchJahr }
            fun verbrauchVorjahr(verbrauchVorjahr: BigDecimal?) = apply { this.verbrauchVorjahr = verbrauchVorjahr }
            fun differenz(differenz: BigDecimal?) = apply { this.differenz = differenz }
            fun build(): Verbrauchsvergleich =
                Verbrauchsvergleich(verbrauchsgegenstand, raumName, verbrauchJahr, verbrauchVorjahr, differenz)
        }

        companion object {
            @JvmStatic
            fun builder(): VerbrauchsvergleichBuilder = VerbrauchsvergleichBuilder()
        }
    }

    companion object {
        @JvmStatic
        fun builder(): AnnualAccountingResultBuilder = AnnualAccountingResultBuilder()
    }
}
