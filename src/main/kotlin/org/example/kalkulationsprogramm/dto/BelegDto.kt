package org.example.kalkulationsprogramm.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class BelegDto {
    class Response {
        var id: Long? = null
        var belegKategorie: String? = null
        var dokumentTyp: String? = null
        var istUmbuchung: Boolean? = null
        var status: String? = null
        var kiAnalyseStatus: String? = null
        var belegDatum: LocalDate? = null
        var belegNummer: String? = null
        var beschreibung: String? = null
        var betragNetto: BigDecimal? = null
        var betragBrutto: BigDecimal? = null
        var mwstSatz: BigDecimal? = null
        var zahlungsart: String? = null
        var lieferantId: Long? = null
        var lieferantName: String? = null
        var sachkontoId: Long? = null
        var sachkontoBezeichnung: String? = null
        var sachkontoNummer: String? = null
        var sachkontoTyp: String? = null
        var kostenstelleId: Long? = null
        var kostenstelleBezeichnung: String? = null
        var kostenstelleTyp: String? = null
        var kostenstelleIstFixkosten: Boolean? = null
        var kiVorgeschlagenerLieferant: String? = null
        var kiConfidence: BigDecimal? = null
        var kiVorgeschlagenerKostenstelleId: Long? = null
        var kiVorgeschlagenerKostenstelleBezeichnung: String? = null
        var kiVorgeschlagenerSachkontoId: Long? = null
        var kiVorgeschlagenerSachkontoBezeichnung: String? = null
        var kiKostenkontoConfidence: BigDecimal? = null
        var kiKostenkontoBegruendung: String? = null
        var kiFehlerText: String? = null
        var originalDateiname: String? = null
        var mimeType: String? = null
        var uploadDatum: LocalDateTime? = null
        var uploadedById: Long? = null
        var uploadedByName: String? = null
        var validiertAm: LocalDateTime? = null
        var validiertVonId: Long? = null
        var validiertVonName: String? = null
        var notiz: String? = null
        var eingangsrechnungId: Long? = null
        var aufteilungsModus: String? = null
        var betragFirmaNetto: BigDecimal? = null
        var betragFirmaBrutto: BigDecimal? = null
        var betragFirmaMwst: BigDecimal? = null
        var positionen: List<PositionResponse>? = null
        var kostenstellenSplits: List<KostenstellenSplitDto>? = null

        class ResponseBuilder {
            private val value = Response()
            fun id(id: Long?) = apply { value.id = id }
            fun belegKategorie(belegKategorie: String?) = apply { value.belegKategorie = belegKategorie }
            fun dokumentTyp(dokumentTyp: String?) = apply { value.dokumentTyp = dokumentTyp }
            fun istUmbuchung(istUmbuchung: Boolean?) = apply { value.istUmbuchung = istUmbuchung }
            fun status(status: String?) = apply { value.status = status }
            fun kiAnalyseStatus(kiAnalyseStatus: String?) = apply { value.kiAnalyseStatus = kiAnalyseStatus }
            fun belegDatum(belegDatum: LocalDate?) = apply { value.belegDatum = belegDatum }
            fun belegNummer(belegNummer: String?) = apply { value.belegNummer = belegNummer }
            fun beschreibung(beschreibung: String?) = apply { value.beschreibung = beschreibung }
            fun betragNetto(betragNetto: BigDecimal?) = apply { value.betragNetto = betragNetto }
            fun betragBrutto(betragBrutto: BigDecimal?) = apply { value.betragBrutto = betragBrutto }
            fun mwstSatz(mwstSatz: BigDecimal?) = apply { value.mwstSatz = mwstSatz }
            fun zahlungsart(zahlungsart: String?) = apply { value.zahlungsart = zahlungsart }
            fun lieferantId(lieferantId: Long?) = apply { value.lieferantId = lieferantId }
            fun lieferantName(lieferantName: String?) = apply { value.lieferantName = lieferantName }
            fun sachkontoId(sachkontoId: Long?) = apply { value.sachkontoId = sachkontoId }
            fun sachkontoBezeichnung(sachkontoBezeichnung: String?) = apply { value.sachkontoBezeichnung = sachkontoBezeichnung }
            fun sachkontoNummer(sachkontoNummer: String?) = apply { value.sachkontoNummer = sachkontoNummer }
            fun sachkontoTyp(sachkontoTyp: String?) = apply { value.sachkontoTyp = sachkontoTyp }
            fun kostenstelleId(kostenstelleId: Long?) = apply { value.kostenstelleId = kostenstelleId }
            fun kostenstelleBezeichnung(kostenstelleBezeichnung: String?) = apply { value.kostenstelleBezeichnung = kostenstelleBezeichnung }
            fun kostenstelleTyp(kostenstelleTyp: String?) = apply { value.kostenstelleTyp = kostenstelleTyp }
            fun kostenstelleIstFixkosten(kostenstelleIstFixkosten: Boolean?) = apply { value.kostenstelleIstFixkosten = kostenstelleIstFixkosten }
            fun kiVorgeschlagenerLieferant(kiVorgeschlagenerLieferant: String?) = apply { value.kiVorgeschlagenerLieferant = kiVorgeschlagenerLieferant }
            fun kiConfidence(kiConfidence: BigDecimal?) = apply { value.kiConfidence = kiConfidence }
            fun kiVorgeschlagenerKostenstelleId(kiVorgeschlagenerKostenstelleId: Long?) = apply { value.kiVorgeschlagenerKostenstelleId = kiVorgeschlagenerKostenstelleId }
            fun kiVorgeschlagenerKostenstelleBezeichnung(kiVorgeschlagenerKostenstelleBezeichnung: String?) = apply { value.kiVorgeschlagenerKostenstelleBezeichnung = kiVorgeschlagenerKostenstelleBezeichnung }
            fun kiVorgeschlagenerSachkontoId(kiVorgeschlagenerSachkontoId: Long?) = apply { value.kiVorgeschlagenerSachkontoId = kiVorgeschlagenerSachkontoId }
            fun kiVorgeschlagenerSachkontoBezeichnung(kiVorgeschlagenerSachkontoBezeichnung: String?) = apply { value.kiVorgeschlagenerSachkontoBezeichnung = kiVorgeschlagenerSachkontoBezeichnung }
            fun kiKostenkontoConfidence(kiKostenkontoConfidence: BigDecimal?) = apply { value.kiKostenkontoConfidence = kiKostenkontoConfidence }
            fun kiKostenkontoBegruendung(kiKostenkontoBegruendung: String?) = apply { value.kiKostenkontoBegruendung = kiKostenkontoBegruendung }
            fun kiFehlerText(kiFehlerText: String?) = apply { value.kiFehlerText = kiFehlerText }
            fun originalDateiname(originalDateiname: String?) = apply { value.originalDateiname = originalDateiname }
            fun mimeType(mimeType: String?) = apply { value.mimeType = mimeType }
            fun uploadDatum(uploadDatum: LocalDateTime?) = apply { value.uploadDatum = uploadDatum }
            fun uploadedById(uploadedById: Long?) = apply { value.uploadedById = uploadedById }
            fun uploadedByName(uploadedByName: String?) = apply { value.uploadedByName = uploadedByName }
            fun validiertAm(validiertAm: LocalDateTime?) = apply { value.validiertAm = validiertAm }
            fun validiertVonId(validiertVonId: Long?) = apply { value.validiertVonId = validiertVonId }
            fun validiertVonName(validiertVonName: String?) = apply { value.validiertVonName = validiertVonName }
            fun notiz(notiz: String?) = apply { value.notiz = notiz }
            fun eingangsrechnungId(eingangsrechnungId: Long?) = apply { value.eingangsrechnungId = eingangsrechnungId }
            fun aufteilungsModus(aufteilungsModus: String?) = apply { value.aufteilungsModus = aufteilungsModus }
            fun betragFirmaNetto(betragFirmaNetto: BigDecimal?) = apply { value.betragFirmaNetto = betragFirmaNetto }
            fun betragFirmaBrutto(betragFirmaBrutto: BigDecimal?) = apply { value.betragFirmaBrutto = betragFirmaBrutto }
            fun betragFirmaMwst(betragFirmaMwst: BigDecimal?) = apply { value.betragFirmaMwst = betragFirmaMwst }
            fun positionen(positionen: List<PositionResponse>?) = apply { value.positionen = positionen }
            fun kostenstellenSplits(kostenstellenSplits: List<KostenstellenSplitDto>?) = apply { value.kostenstellenSplits = kostenstellenSplits }
            fun build() = value
        }

        companion object {
            @JvmStatic fun builder() = ResponseBuilder()
        }
    }

    class KostenstellenSplitDto {
        var id: Long? = null
        var kostenstelleId: Long? = null
        var kostenstelleBezeichnung: String? = null
        var kostenstelleIstFixkosten: Boolean? = null
        var prozent: Int? = null
        var absoluterBetrag: BigDecimal? = null
        var berechneterBetrag: BigDecimal? = null
        var beschreibung: String? = null
        var streckungJahre: Int? = null
        var streckungStartJahr: Int? = null

        class KostenstellenSplitDtoBuilder {
            private val value = KostenstellenSplitDto()
            fun id(id: Long?) = apply { value.id = id }
            fun kostenstelleId(kostenstelleId: Long?) = apply { value.kostenstelleId = kostenstelleId }
            fun kostenstelleBezeichnung(kostenstelleBezeichnung: String?) = apply { value.kostenstelleBezeichnung = kostenstelleBezeichnung }
            fun kostenstelleIstFixkosten(kostenstelleIstFixkosten: Boolean?) = apply { value.kostenstelleIstFixkosten = kostenstelleIstFixkosten }
            fun prozent(prozent: Int?) = apply { value.prozent = prozent }
            fun absoluterBetrag(absoluterBetrag: BigDecimal?) = apply { value.absoluterBetrag = absoluterBetrag }
            fun berechneterBetrag(berechneterBetrag: BigDecimal?) = apply { value.berechneterBetrag = berechneterBetrag }
            fun beschreibung(beschreibung: String?) = apply { value.beschreibung = beschreibung }
            fun streckungJahre(streckungJahre: Int?) = apply { value.streckungJahre = streckungJahre }
            fun streckungStartJahr(streckungStartJahr: Int?) = apply { value.streckungStartJahr = streckungStartJahr }
            fun build() = value
        }
        companion object { @JvmStatic fun builder() = KostenstellenSplitDtoBuilder() }
    }

    class PositionResponse {
        var id: Long? = null
        var sortierung: Int = 0
        var beschreibung: String? = null
        var menge: BigDecimal? = null
        var einheit: String? = null
        var einzelpreis: BigDecimal? = null
        var betragNetto: BigDecimal? = null
        var betragBrutto: BigDecimal? = null
        var mwstSatz: BigDecimal? = null
        var isIstFuerFirma: Boolean = false

        class PositionResponseBuilder {
            private val value = PositionResponse()
            fun id(id: Long?) = apply { value.id = id }
            fun sortierung(sortierung: Int) = apply { value.sortierung = sortierung }
            fun beschreibung(beschreibung: String?) = apply { value.beschreibung = beschreibung }
            fun menge(menge: BigDecimal?) = apply { value.menge = menge }
            fun einheit(einheit: String?) = apply { value.einheit = einheit }
            fun einzelpreis(einzelpreis: BigDecimal?) = apply { value.einzelpreis = einzelpreis }
            fun betragNetto(betragNetto: BigDecimal?) = apply { value.betragNetto = betragNetto }
            fun betragBrutto(betragBrutto: BigDecimal?) = apply { value.betragBrutto = betragBrutto }
            fun mwstSatz(mwstSatz: BigDecimal?) = apply { value.mwstSatz = mwstSatz }
            fun istFuerFirma(istFuerFirma: Boolean) = apply { value.isIstFuerFirma = istFuerFirma }
            fun build() = value
        }
        companion object { @JvmStatic fun builder() = PositionResponseBuilder() }
    }

    class PositionAuswahlRequest {
        var firmaPositionIds: List<Long>? = null
        constructor()
        constructor(firmaPositionIds: List<Long>?) { this.firmaPositionIds = firmaPositionIds }
    }

    class MwstRechnerRequest {
        var netto: BigDecimal? = null
        var brutto: BigDecimal? = null
        var satzProzent: BigDecimal? = null
        constructor()
        constructor(netto: BigDecimal?, brutto: BigDecimal?, satzProzent: BigDecimal?) {
            this.netto = netto
            this.brutto = brutto
            this.satzProzent = satzProzent
        }
    }

    class MwstRechnerResponse {
        var netto: BigDecimal? = null
        var brutto: BigDecimal? = null
        var satzProzent: BigDecimal? = null
        var mwstBetrag: BigDecimal? = null

        class MwstRechnerResponseBuilder {
            private val value = MwstRechnerResponse()
            fun netto(netto: BigDecimal?) = apply { value.netto = netto }
            fun brutto(brutto: BigDecimal?) = apply { value.brutto = brutto }
            fun satzProzent(satzProzent: BigDecimal?) = apply { value.satzProzent = satzProzent }
            fun mwstBetrag(mwstBetrag: BigDecimal?) = apply { value.mwstBetrag = mwstBetrag }
            fun build() = value
        }
        companion object { @JvmStatic fun builder() = MwstRechnerResponseBuilder() }
    }

    class UpdateRequest {
        var belegKategorie: String? = null
        var status: String? = null
        var belegDatum: LocalDate? = null
        var belegNummer: String? = null
        var beschreibung: String? = null
        var betragNetto: BigDecimal? = null
        var betragBrutto: BigDecimal? = null
        var mwstSatz: BigDecimal? = null
        var zahlungsart: String? = null
        var lieferantId: Long? = null
        var sachkontoId: Long? = null
        var kostenstelleId: Long? = null
        var notiz: String? = null
        var aufteilungsModus: String? = null
        var kostenstellenSplits: List<KostenstellenSplitDto>? = null
    }

    class PermissionResponse {
        var isDarfScannen: Boolean = false
        var isDarfSehen: Boolean = false

        class PermissionResponseBuilder {
            private val value = PermissionResponse()
            fun darfScannen(darfScannen: Boolean) = apply { value.isDarfScannen = darfScannen }
            fun darfSehen(darfSehen: Boolean) = apply { value.isDarfSehen = darfSehen }
            fun build() = value
        }
        companion object { @JvmStatic fun builder() = PermissionResponseBuilder() }
    }

    class KassenBewegung {
        var belegId: Long? = null
        var datum: LocalDate? = null
        var kategorie: String? = null
        var beschreibung: String? = null
        var lieferantName: String? = null
        var betrag: BigDecimal? = null
        var saldoNachher: BigDecimal? = null

        class KassenBewegungBuilder {
            private val value = KassenBewegung()
            fun belegId(belegId: Long?) = apply { value.belegId = belegId }
            fun datum(datum: LocalDate?) = apply { value.datum = datum }
            fun kategorie(kategorie: String?) = apply { value.kategorie = kategorie }
            fun beschreibung(beschreibung: String?) = apply { value.beschreibung = beschreibung }
            fun lieferantName(lieferantName: String?) = apply { value.lieferantName = lieferantName }
            fun betrag(betrag: BigDecimal?) = apply { value.betrag = betrag }
            fun saldoNachher(saldoNachher: BigDecimal?) = apply { value.saldoNachher = saldoNachher }
            fun build() = value
        }
        companion object { @JvmStatic fun builder() = KassenBewegungBuilder() }
    }

    class SteuerberaterExportEntry {
        var belegId: Long? = null
        var belegDatum: LocalDate? = null
        var belegNummer: String? = null
        var lieferantName: String? = null
        var belegKategorie: String? = null
        var dokumentTyp: String? = null
        var sachkontoNummer: String? = null
        var sachkontoBezeichnung: String? = null
        var betragNetto: BigDecimal? = null
        var betragBrutto: BigDecimal? = null
        var betragMwst: BigDecimal? = null
        var mwstSatz: BigDecimal? = null
        var notiz: String? = null
        var beschreibung: String? = null
        var aufteilungsModus: String? = null
        var gesamtBruttoOriginal: BigDecimal? = null
        var anzahlPositionenGesamt: Int? = null
        var anzahlPositionenFirma: Int? = null
        var positionenHinweis: String? = null

        class SteuerberaterExportEntryBuilder {
            private val value = SteuerberaterExportEntry()
            fun belegId(belegId: Long?) = apply { value.belegId = belegId }
            fun belegDatum(belegDatum: LocalDate?) = apply { value.belegDatum = belegDatum }
            fun belegNummer(belegNummer: String?) = apply { value.belegNummer = belegNummer }
            fun lieferantName(lieferantName: String?) = apply { value.lieferantName = lieferantName }
            fun belegKategorie(belegKategorie: String?) = apply { value.belegKategorie = belegKategorie }
            fun dokumentTyp(dokumentTyp: String?) = apply { value.dokumentTyp = dokumentTyp }
            fun sachkontoNummer(sachkontoNummer: String?) = apply { value.sachkontoNummer = sachkontoNummer }
            fun sachkontoBezeichnung(sachkontoBezeichnung: String?) = apply { value.sachkontoBezeichnung = sachkontoBezeichnung }
            fun betragNetto(betragNetto: BigDecimal?) = apply { value.betragNetto = betragNetto }
            fun betragBrutto(betragBrutto: BigDecimal?) = apply { value.betragBrutto = betragBrutto }
            fun betragMwst(betragMwst: BigDecimal?) = apply { value.betragMwst = betragMwst }
            fun mwstSatz(mwstSatz: BigDecimal?) = apply { value.mwstSatz = mwstSatz }
            fun notiz(notiz: String?) = apply { value.notiz = notiz }
            fun beschreibung(beschreibung: String?) = apply { value.beschreibung = beschreibung }
            fun aufteilungsModus(aufteilungsModus: String?) = apply { value.aufteilungsModus = aufteilungsModus }
            fun gesamtBruttoOriginal(gesamtBruttoOriginal: BigDecimal?) = apply { value.gesamtBruttoOriginal = gesamtBruttoOriginal }
            fun anzahlPositionenGesamt(anzahlPositionenGesamt: Int?) = apply { value.anzahlPositionenGesamt = anzahlPositionenGesamt }
            fun anzahlPositionenFirma(anzahlPositionenFirma: Int?) = apply { value.anzahlPositionenFirma = anzahlPositionenFirma }
            fun positionenHinweis(positionenHinweis: String?) = apply { value.positionenHinweis = positionenHinweis }
            fun build() = value
        }
        companion object { @JvmStatic fun builder() = SteuerberaterExportEntryBuilder() }
    }

    class KassenbuchResponse {
        var saldoStart: BigDecimal? = null
        var saldoEnde: BigDecimal? = null
        var summeEinnahmen: BigDecimal? = null
        var summeAusgaben: BigDecimal? = null
        var summePrivatentnahmen: BigDecimal? = null
        var summePrivateinlagen: BigDecimal? = null
        var bewegungen: List<KassenBewegung>? = null

        class KassenbuchResponseBuilder {
            private val value = KassenbuchResponse()
            fun saldoStart(saldoStart: BigDecimal?) = apply { value.saldoStart = saldoStart }
            fun saldoEnde(saldoEnde: BigDecimal?) = apply { value.saldoEnde = saldoEnde }
            fun summeEinnahmen(summeEinnahmen: BigDecimal?) = apply { value.summeEinnahmen = summeEinnahmen }
            fun summeAusgaben(summeAusgaben: BigDecimal?) = apply { value.summeAusgaben = summeAusgaben }
            fun summePrivatentnahmen(summePrivatentnahmen: BigDecimal?) = apply { value.summePrivatentnahmen = summePrivatentnahmen }
            fun summePrivateinlagen(summePrivateinlagen: BigDecimal?) = apply { value.summePrivateinlagen = summePrivateinlagen }
            fun bewegungen(bewegungen: List<KassenBewegung>?) = apply { value.bewegungen = bewegungen }
            fun build() = value
        }
        companion object { @JvmStatic fun builder() = KassenbuchResponseBuilder() }
    }

    class UmbuchungCreateRequest {
        var belegKategorie: String? = null
        var belegDatum: LocalDate? = null
        var betragBrutto: BigDecimal? = null
        var beschreibung: String? = null
        var zahlungsart: String? = null
        var sachkontoId: Long? = null
        var notiz: String? = null
    }
}
