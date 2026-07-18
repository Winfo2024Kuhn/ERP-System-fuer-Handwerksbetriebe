package org.example.kalkulationsprogramm.mapper

import org.example.kalkulationsprogramm.domain.miete.Kostenposition
import org.example.kalkulationsprogramm.domain.miete.KostenpositionBerechnung
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt
import org.example.kalkulationsprogramm.domain.miete.Mietpartei
import org.example.kalkulationsprogramm.domain.miete.Raum
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselEintrag
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingConsumptionDto
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingCostCenterDto
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingPartyDto
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingResponseDto
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingShareDto
import org.example.kalkulationsprogramm.dto.miete.KostenpositionDto
import org.example.kalkulationsprogramm.dto.miete.KostenstelleDto
import org.example.kalkulationsprogramm.dto.miete.MietobjektDto
import org.example.kalkulationsprogramm.dto.miete.MietparteiDto
import org.example.kalkulationsprogramm.dto.miete.RaumDto
import org.example.kalkulationsprogramm.dto.miete.VerteilungsschluesselDto
import org.example.kalkulationsprogramm.dto.miete.VerteilungsschluesselEintragDto
import org.example.kalkulationsprogramm.dto.miete.VerbrauchsgegenstandDto
import org.example.kalkulationsprogramm.dto.miete.ZaehlerstandDto
import org.example.kalkulationsprogramm.service.miete.KostenpositionBerechner
import org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.MathContext
import java.util.LinkedHashMap

@Component
class MieteMapper(
    private val kostenpositionBerechner: KostenpositionBerechner,
) {
    fun toDto(mietobjekt: Mietobjekt): MietobjektDto {
        return MietobjektDto().apply {
            id = mietobjekt.id
            name = mietobjekt.name
            strasse = mietobjekt.strasse
            plz = mietobjekt.plz
            ort = mietobjekt.ort
        }
    }

    fun toEntity(dto: MietobjektDto): Mietobjekt =
        Mietobjekt().apply {
            id = dto.id
            name = dto.name
            strasse = dto.strasse
            plz = dto.plz
            ort = dto.ort
        }

    fun toDto(partei: Mietpartei): MietparteiDto =
        MietparteiDto().apply {
            id = partei.id
            name = partei.name
            rolle = partei.rolle
            email = partei.email
            telefon = partei.telefon
            monatlicherVorschuss = partei.monatlicherVorschuss
        }

    fun toEntity(dto: MietparteiDto): Mietpartei =
        Mietpartei().apply {
            id = dto.id
            name = dto.name
            rolle = dto.rolle
            email = dto.email
            telefon = dto.telefon
            monatlicherVorschuss = dto.monatlicherVorschuss
        }

    fun toDto(raum: Raum): RaumDto =
        RaumDto().apply {
            id = raum.id
            mietobjektId = raum.mietobjekt?.id
            name = raum.name
            beschreibung = raum.beschreibung
            flaecheQuadratmeter = raum.flaecheQuadratmeter
        }

    fun toEntity(dto: RaumDto): Raum =
        Raum().apply {
            id = dto.id
            name = dto.name
            beschreibung = dto.beschreibung
            flaecheQuadratmeter = dto.flaecheQuadratmeter
        }

    fun toDto(gegenstand: Verbrauchsgegenstand): VerbrauchsgegenstandDto =
        VerbrauchsgegenstandDto().apply {
            id = gegenstand.id
            raumId = gegenstand.raum?.id
            name = gegenstand.name
            seriennummer = gegenstand.seriennummer
            verbrauchsart = gegenstand.verbrauchsart
            einheit = gegenstand.einheit
            isAktiv = gegenstand.isAktiv()
        }

    fun toEntity(dto: VerbrauchsgegenstandDto): Verbrauchsgegenstand =
        Verbrauchsgegenstand().apply {
            id = dto.id
            name = dto.name
            seriennummer = dto.seriennummer
            verbrauchsart = dto.verbrauchsart
            einheit = dto.einheit
            aktiv = dto.isAktiv
        }

    fun toDto(zaehlerstand: Zaehlerstand): ZaehlerstandDto =
        ZaehlerstandDto().apply {
            id = zaehlerstand.id
            verbrauchsgegenstandId = zaehlerstand.verbrauchsgegenstand?.id
            abrechnungsJahr = zaehlerstand.abrechnungsJahr
            stichtag = zaehlerstand.stichtag
            stand = zaehlerstand.stand
            verbrauch = zaehlerstand.verbrauch
            kommentar = zaehlerstand.kommentar
        }

    fun toEntity(dto: ZaehlerstandDto): Zaehlerstand =
        Zaehlerstand().apply {
            id = dto.id
            abrechnungsJahr = dto.abrechnungsJahr
            stichtag = dto.stichtag
            stand = dto.stand
            verbrauch = dto.verbrauch
            kommentar = dto.kommentar
            dto.verbrauchsgegenstandId?.let {
                verbrauchsgegenstand = Verbrauchsgegenstand().apply { id = it }
            }
        }

    fun toDto(kostenstelle: Kostenstelle): KostenstelleDto =
        KostenstelleDto().apply {
            id = kostenstelle.id
            mietobjektId = kostenstelle.mietobjekt?.id
            name = kostenstelle.name
            beschreibung = kostenstelle.beschreibung
            isUmlagefaehig = kostenstelle.isUmlagefaehig()
            standardSchluesselId = kostenstelle.standardSchluessel?.id
        }

    fun toEntity(dto: KostenstelleDto): Kostenstelle =
        Kostenstelle().apply {
            id = dto.id
            name = dto.name
            beschreibung = dto.beschreibung
            umlagefaehig = dto.isUmlagefaehig
            dto.standardSchluesselId?.let {
                standardSchluessel = Verteilungsschluessel().apply { id = it }
            }
        }

    fun toDto(schluessel: Verteilungsschluessel): VerteilungsschluesselDto =
        VerteilungsschluesselDto().apply {
            id = schluessel.id
            mietobjektId = schluessel.mietobjekt?.id
            name = schluessel.name
            beschreibung = schluessel.beschreibung
            typ = schluessel.typ
            val sourceEintraege = schluessel.eintraege
            if (sourceEintraege != null) {
                eintraege = sourceEintraege.map { eintrag ->
                    VerteilungsschluesselEintragDto().apply {
                        id = eintrag.id
                        anteil = eintrag.anteil
                        kommentar = eintrag.kommentar
                        mietparteiId = eintrag.mietpartei?.id
                        verbrauchsgegenstandId = eintrag.verbrauchsgegenstand?.id
                    }
                }.toMutableList()
            }
        }

    fun toEntity(dto: VerteilungsschluesselDto): Verteilungsschluessel {
        val entity = Verteilungsschluessel().apply {
            id = dto.id
            name = dto.name
            beschreibung = dto.beschreibung
            typ = dto.typ
        }
        entity.eintraege = dto.eintraege.map { eintragDto ->
            VerteilungsschluesselEintrag().apply {
                id = eintragDto.id
                anteil = eintragDto.anteil
                kommentar = eintragDto.kommentar
                eintragDto.mietparteiId?.let {
                    mietpartei = Mietpartei().apply { id = it }
                }
                eintragDto.verbrauchsgegenstandId?.let {
                    verbrauchsgegenstand = Verbrauchsgegenstand().apply { id = it }
                }
                verteilungsschluessel = entity
            }
        }.toMutableList()
        return entity
    }

    fun toDto(kostenposition: Kostenposition): KostenpositionDto {
        var berechneterBetrag = kostenposition.betrag
        var verbrauchsmenge: BigDecimal? = null
        val jahr = kostenposition.abrechnungsJahr
        if (jahr != null) {
            try {
                val ergebnis = kostenpositionBerechner.berechne(kostenposition, jahr, LinkedHashMap())
                berechneterBetrag = ergebnis.betrag()
                verbrauchsmenge = ergebnis.verbrauchsSumme()
            } catch (ignored: RuntimeException) {
                // Bei fehlerhafter Konfiguration kann die Kostenposition dennoch angezeigt werden.
            }
        }
        return KostenpositionDto().apply {
            id = kostenposition.id
            kostenstelleId = kostenposition.kostenstelle?.id
            abrechnungsJahr = kostenposition.abrechnungsJahr
            betrag = kostenposition.betrag
            berechnung = kostenposition.berechnung ?: KostenpositionBerechnung.BETRAG
            verbrauchsfaktor = kostenposition.verbrauchsfaktor
            this.berechneterBetrag = berechneterBetrag
            this.verbrauchsmenge = verbrauchsmenge
            beschreibung = kostenposition.beschreibung
            belegNummer = kostenposition.belegNummer
            buchungsdatum = kostenposition.buchungsdatum
            verteilungsschluesselId = kostenposition.verteilungsschluesselOverride?.id
        }
    }

    fun toEntity(dto: KostenpositionDto): Kostenposition =
        Kostenposition().apply {
            id = dto.id
            abrechnungsJahr = dto.abrechnungsJahr
            betrag = dto.betrag
            berechnung = dto.berechnung ?: KostenpositionBerechnung.BETRAG
            verbrauchsfaktor = dto.verbrauchsfaktor
            beschreibung = dto.beschreibung
            belegNummer = dto.belegNummer
            buchungsdatum = dto.buchungsdatum
            dto.verteilungsschluesselId?.let {
                verteilungsschluesselOverride = Verteilungsschluessel().apply { id = it }
            }
        }

    fun toDto(result: AnnualAccountingResult): AnnualAccountingResponseDto =
        AnnualAccountingResponseDto().apply {
            mietobjektId = result.mietobjektId
            mietobjektName = result.mietobjektName
            mietobjektStrasse = result.mietobjektStrasse
            mietobjektPlz = result.mietobjektPlz
            mietobjektOrt = result.mietobjektOrt
            jahr = result.abrechnungsJahr
            gesamtkosten = result.gesamtkosten
            gesamtkostenVorjahr = result.gesamtkostenVorjahr
            gesamtkostenDifferenz = result.gesamtkostenDifferenz

            result.kostenstellen?.forEach { ks ->
                val kostenstelle = requireNotNull(ks.kostenstelle)
                val cDto = AnnualAccountingCostCenterDto().apply {
                    kostenstelleId = kostenstelle.id
                    kostenstelleName = kostenstelle.name
                    summe = ks.gesamtkosten
                    vorjahr = ks.gesamtkostenVorjahr
                    differenz = safe(ks.gesamtkosten).subtract(safe(ks.gesamtkostenVorjahr), MC)
                    if (ks.parteianteile != null) {
                        parteianteile = ks.parteianteile.map { e ->
                            val mietpartei = requireNotNull(e.mietpartei)
                            AnnualAccountingShareDto().apply {
                                mietparteiId = mietpartei.id
                                mietparteiName = mietpartei.name
                                rolle = mietpartei.rolle
                                betrag = e.betrag
                            }
                        }.toMutableList()
                    }
                }
                kostenstellen.add(cDto)
            }

            result.parteien?.forEach { p ->
                val mietpartei = requireNotNull(p.mietpartei)
                parteien.add(
                    AnnualAccountingPartyDto().apply {
                        mietparteiId = mietpartei.id
                        mietparteiName = mietpartei.name
                        rolle = mietpartei.rolle
                        summe = p.betrag
                        vorjahr = p.betragVorjahr
                        differenz = p.differenz
                        monatlicherVorschuss = p.vorauszahlungMonatlich
                        jahresVorauszahlung = p.vorauszahlungJahr
                        saldo = p.saldo
                    },
                )
            }

            result.verbrauchsvergleiche?.forEach { v ->
                val g = v.verbrauchsgegenstand!!
                verbrauchsvergleiche.add(
                    AnnualAccountingConsumptionDto().apply {
                        verbrauchsgegenstandId = g.id
                        name = g.name
                        raumName = v.raumName
                        verbrauchsart = g.verbrauchsart
                        einheit = g.einheit
                        verbrauchJahr = v.verbrauchJahr
                        verbrauchVorjahr = v.verbrauchVorjahr
                        differenz = v.differenz
                    },
                )
            }
        }

    private companion object {
        val MC: MathContext = MathContext.DECIMAL64

        fun safe(value: BigDecimal?): BigDecimal = value ?: BigDecimal.ZERO
    }
}
