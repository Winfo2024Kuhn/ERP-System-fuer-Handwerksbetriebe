package org.example.kalkulationsprogramm.dto

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class LieferantDokumentDto {
    class Response {
        var id: Long? = null
        var lieferantId: Long? = null
        var lieferantName: String? = null
        var typ: LieferantDokumentTyp? = null
        var originalDateiname: String? = null
        var gespeicherterDateiname: String? = null
        var uploadDatum: LocalDateTime? = null
        var uploadedByName: String? = null
        var url: String? = null
        var projektAnteile: List<ProjektAnteilRef>? = null
        var verknuepfteDokumente: List<VerknuepftesDoc>? = null
        var geschaeftsdaten: GeschaeftsdatenRef? = null

        constructor()
        constructor(
            id: Long?,
            lieferantId: Long?,
            lieferantName: String?,
            typ: LieferantDokumentTyp?,
            originalDateiname: String?,
            gespeicherterDateiname: String?,
            uploadDatum: LocalDateTime?,
            uploadedByName: String?,
            url: String?,
            projektAnteile: List<ProjektAnteilRef>?,
            verknuepfteDokumente: List<VerknuepftesDoc>?,
            geschaeftsdaten: GeschaeftsdatenRef?,
        ) {
            this.id = id
            this.lieferantId = lieferantId
            this.lieferantName = lieferantName
            this.typ = typ
            this.originalDateiname = originalDateiname
            this.gespeicherterDateiname = gespeicherterDateiname
            this.uploadDatum = uploadDatum
            this.uploadedByName = uploadedByName
            this.url = url
            this.projektAnteile = projektAnteile
            this.verknuepfteDokumente = verknuepfteDokumente
            this.geschaeftsdaten = geschaeftsdaten
        }

        class ResponseBuilder {
            private val value = Response()
            fun id(id: Long?) = apply { value.id = id }
            fun lieferantId(lieferantId: Long?) = apply { value.lieferantId = lieferantId }
            fun lieferantName(lieferantName: String?) = apply { value.lieferantName = lieferantName }
            fun typ(typ: LieferantDokumentTyp?) = apply { value.typ = typ }
            fun originalDateiname(originalDateiname: String?) = apply { value.originalDateiname = originalDateiname }
            fun gespeicherterDateiname(gespeicherterDateiname: String?) = apply { value.gespeicherterDateiname = gespeicherterDateiname }
            fun uploadDatum(uploadDatum: LocalDateTime?) = apply { value.uploadDatum = uploadDatum }
            fun uploadedByName(uploadedByName: String?) = apply { value.uploadedByName = uploadedByName }
            fun url(url: String?) = apply { value.url = url }
            fun projektAnteile(projektAnteile: List<ProjektAnteilRef>?) = apply { value.projektAnteile = projektAnteile }
            fun verknuepfteDokumente(verknuepfteDokumente: List<VerknuepftesDoc>?) = apply { value.verknuepfteDokumente = verknuepfteDokumente }
            fun geschaeftsdaten(geschaeftsdaten: GeschaeftsdatenRef?) = apply { value.geschaeftsdaten = geschaeftsdaten }
            fun build() = value
        }

        companion object {
            @JvmStatic
            fun builder() = ResponseBuilder()
        }
    }

    class ProjektAnteilRef {
        var id: Long? = null
        var projektId: Long? = null
        var projektName: String? = null
        var auftragsnummer: String? = null
        var kostenstelleId: Long? = null
        var kostenstelleName: String? = null
        var prozent: Int? = null
        var berechneterBetrag: BigDecimal? = null
        var beschreibung: String? = null
        var zugeordnetVonName: String? = null
        var zugeordnetAm: LocalDateTime? = null

        constructor()
        constructor(
            id: Long?,
            projektId: Long?,
            projektName: String?,
            auftragsnummer: String?,
            kostenstelleId: Long?,
            kostenstelleName: String?,
            prozent: Int?,
            berechneterBetrag: BigDecimal?,
            beschreibung: String?,
            zugeordnetVonName: String?,
            zugeordnetAm: LocalDateTime?,
        ) {
            this.id = id
            this.projektId = projektId
            this.projektName = projektName
            this.auftragsnummer = auftragsnummer
            this.kostenstelleId = kostenstelleId
            this.kostenstelleName = kostenstelleName
            this.prozent = prozent
            this.berechneterBetrag = berechneterBetrag
            this.beschreibung = beschreibung
            this.zugeordnetVonName = zugeordnetVonName
            this.zugeordnetAm = zugeordnetAm
        }

        class ProjektAnteilRefBuilder {
            private val value = ProjektAnteilRef()
            fun id(id: Long?) = apply { value.id = id }
            fun projektId(projektId: Long?) = apply { value.projektId = projektId }
            fun projektName(projektName: String?) = apply { value.projektName = projektName }
            fun auftragsnummer(auftragsnummer: String?) = apply { value.auftragsnummer = auftragsnummer }
            fun kostenstelleId(kostenstelleId: Long?) = apply { value.kostenstelleId = kostenstelleId }
            fun kostenstelleName(kostenstelleName: String?) = apply { value.kostenstelleName = kostenstelleName }
            fun prozent(prozent: Int?) = apply { value.prozent = prozent }
            fun berechneterBetrag(berechneterBetrag: BigDecimal?) = apply { value.berechneterBetrag = berechneterBetrag }
            fun beschreibung(beschreibung: String?) = apply { value.beschreibung = beschreibung }
            fun zugeordnetVonName(zugeordnetVonName: String?) = apply { value.zugeordnetVonName = zugeordnetVonName }
            fun zugeordnetAm(zugeordnetAm: LocalDateTime?) = apply { value.zugeordnetAm = zugeordnetAm }
            fun build() = value
        }

        companion object {
            @JvmStatic
            fun builder() = ProjektAnteilRefBuilder()
        }
    }

    class GeschaeftsdatenRef {
        var dokumentNummer: String? = null
        var dokumentDatum: LocalDate? = null
        var betragNetto: BigDecimal? = null
        var betragBrutto: BigDecimal? = null
        var liefertermin: LocalDate? = null
        var bestellnummer: String? = null
        var referenzNummer: String? = null
        var aiConfidence: Double? = null
        var zahlungsziel: LocalDate? = null
        var bezahlt: Boolean? = null
        var bezahltAm: LocalDate? = null
        var bereitsGezahlt: Boolean? = null
        var zahlungsart: String? = null
        var skontoTage: Int? = null
        var skontoProzent: BigDecimal? = null
        var nettoTage: Int? = null
        var tatsaechlichGezahlt: BigDecimal? = null
        var mitSkonto: Boolean? = null
        var manuellePruefungErforderlich: Boolean? = null
        var datenquelle: String? = null

        constructor()
        constructor(
            dokumentNummer: String?,
            dokumentDatum: LocalDate?,
            betragNetto: BigDecimal?,
            betragBrutto: BigDecimal?,
            liefertermin: LocalDate?,
            bestellnummer: String?,
            referenzNummer: String?,
            aiConfidence: Double?,
            zahlungsziel: LocalDate?,
            bezahlt: Boolean?,
            bezahltAm: LocalDate?,
            bereitsGezahlt: Boolean?,
            zahlungsart: String?,
            skontoTage: Int?,
            skontoProzent: BigDecimal?,
            nettoTage: Int?,
            tatsaechlichGezahlt: BigDecimal?,
            mitSkonto: Boolean?,
            manuellePruefungErforderlich: Boolean?,
            datenquelle: String?,
        ) {
            this.dokumentNummer = dokumentNummer
            this.dokumentDatum = dokumentDatum
            this.betragNetto = betragNetto
            this.betragBrutto = betragBrutto
            this.liefertermin = liefertermin
            this.bestellnummer = bestellnummer
            this.referenzNummer = referenzNummer
            this.aiConfidence = aiConfidence
            this.zahlungsziel = zahlungsziel
            this.bezahlt = bezahlt
            this.bezahltAm = bezahltAm
            this.bereitsGezahlt = bereitsGezahlt
            this.zahlungsart = zahlungsart
            this.skontoTage = skontoTage
            this.skontoProzent = skontoProzent
            this.nettoTage = nettoTage
            this.tatsaechlichGezahlt = tatsaechlichGezahlt
            this.mitSkonto = mitSkonto
            this.manuellePruefungErforderlich = manuellePruefungErforderlich
            this.datenquelle = datenquelle
        }

        class GeschaeftsdatenRefBuilder {
            private val value = GeschaeftsdatenRef()
            fun dokumentNummer(dokumentNummer: String?) = apply { value.dokumentNummer = dokumentNummer }
            fun dokumentDatum(dokumentDatum: LocalDate?) = apply { value.dokumentDatum = dokumentDatum }
            fun betragNetto(betragNetto: BigDecimal?) = apply { value.betragNetto = betragNetto }
            fun betragBrutto(betragBrutto: BigDecimal?) = apply { value.betragBrutto = betragBrutto }
            fun liefertermin(liefertermin: LocalDate?) = apply { value.liefertermin = liefertermin }
            fun bestellnummer(bestellnummer: String?) = apply { value.bestellnummer = bestellnummer }
            fun referenzNummer(referenzNummer: String?) = apply { value.referenzNummer = referenzNummer }
            fun aiConfidence(aiConfidence: Double?) = apply { value.aiConfidence = aiConfidence }
            fun zahlungsziel(zahlungsziel: LocalDate?) = apply { value.zahlungsziel = zahlungsziel }
            fun bezahlt(bezahlt: Boolean?) = apply { value.bezahlt = bezahlt }
            fun bezahltAm(bezahltAm: LocalDate?) = apply { value.bezahltAm = bezahltAm }
            fun bereitsGezahlt(bereitsGezahlt: Boolean?) = apply { value.bereitsGezahlt = bereitsGezahlt }
            fun zahlungsart(zahlungsart: String?) = apply { value.zahlungsart = zahlungsart }
            fun skontoTage(skontoTage: Int?) = apply { value.skontoTage = skontoTage }
            fun skontoProzent(skontoProzent: BigDecimal?) = apply { value.skontoProzent = skontoProzent }
            fun nettoTage(nettoTage: Int?) = apply { value.nettoTage = nettoTage }
            fun tatsaechlichGezahlt(tatsaechlichGezahlt: BigDecimal?) = apply { value.tatsaechlichGezahlt = tatsaechlichGezahlt }
            fun mitSkonto(mitSkonto: Boolean?) = apply { value.mitSkonto = mitSkonto }
            fun manuellePruefungErforderlich(manuellePruefungErforderlich: Boolean?) = apply { value.manuellePruefungErforderlich = manuellePruefungErforderlich }
            fun datenquelle(datenquelle: String?) = apply { value.datenquelle = datenquelle }
            fun build() = value
        }

        companion object {
            @JvmStatic
            fun builder() = GeschaeftsdatenRefBuilder()
        }
    }

    class VerknuepftesDoc {
        var id: Long? = null
        var typ: LieferantDokumentTyp? = null
        var originalDateiname: String? = null
        var uploadDatum: LocalDateTime? = null

        constructor()
        constructor(id: Long?, typ: LieferantDokumentTyp?, originalDateiname: String?, uploadDatum: LocalDateTime?) {
            this.id = id
            this.typ = typ
            this.originalDateiname = originalDateiname
            this.uploadDatum = uploadDatum
        }

        class VerknuepftesDocBuilder {
            private val value = VerknuepftesDoc()
            fun id(id: Long?) = apply { value.id = id }
            fun typ(typ: LieferantDokumentTyp?) = apply { value.typ = typ }
            fun originalDateiname(originalDateiname: String?) = apply { value.originalDateiname = originalDateiname }
            fun uploadDatum(uploadDatum: LocalDateTime?) = apply { value.uploadDatum = uploadDatum }
            fun build() = value
        }

        companion object {
            @JvmStatic
            fun builder() = VerknuepftesDocBuilder()
        }
    }

    class UploadRequest {
        var typ: LieferantDokumentTyp? = null
        var verknuepfteIds: Set<Long>? = null

        constructor()
        constructor(typ: LieferantDokumentTyp?, verknuepfteIds: Set<Long>?) {
            this.typ = typ
            this.verknuepfteIds = verknuepfteIds
        }
    }

    class ProjektZuordnungRequest {
        var anteile: List<ProjektAnteil>? = null

        constructor()
        constructor(anteile: List<ProjektAnteil>?) {
            this.anteile = anteile
        }
    }

    class ProjektAnteil {
        var projektId: Long? = null
        var prozent: Int? = null
        var beschreibung: String? = null

        constructor()
        constructor(projektId: Long?, prozent: Int?, beschreibung: String?) {
            this.projektId = projektId
            this.prozent = prozent
            this.beschreibung = beschreibung
        }
    }

    class BerechtigungenResponse {
        var sichtbareTypen: List<LieferantDokumentTyp>? = null
        var scanbarTypen: List<LieferantDokumentTyp>? = null

        constructor()
        constructor(sichtbareTypen: List<LieferantDokumentTyp>?, scanbarTypen: List<LieferantDokumentTyp>?) {
            this.sichtbareTypen = sichtbareTypen
            this.scanbarTypen = scanbarTypen
        }

        class BerechtigungenResponseBuilder {
            private val value = BerechtigungenResponse()
            fun sichtbareTypen(sichtbareTypen: List<LieferantDokumentTyp>?) = apply { value.sichtbareTypen = sichtbareTypen }
            fun scanbarTypen(scanbarTypen: List<LieferantDokumentTyp>?) = apply { value.scanbarTypen = scanbarTypen }
            fun build() = value
        }

        companion object {
            @JvmStatic
            fun builder() = BerechtigungenResponseBuilder()
        }
    }

    class AnalyzeResponse {
        var dokumentTyp: LieferantDokumentTyp? = null
        var dokumentNummer: String? = null
        var dokumentDatum: LocalDate? = null
        var betragNetto: BigDecimal? = null
        var betragBrutto: BigDecimal? = null
        var mwstSatz: BigDecimal? = null
        var liefertermin: LocalDate? = null
        var zahlungsziel: LocalDate? = null
        var bestellnummer: String? = null
        var referenzNummer: String? = null
        var skontoTage: Int? = null
        var skontoProzent: BigDecimal? = null
        var nettoTage: Int? = null
        var bereitsGezahlt: Boolean? = null
        var zahlungsart: String? = null
        var aiConfidence: Double? = null
        var analyseQuelle: String? = null
        var lieferantName: String? = null
        var lieferantStrasse: String? = null
        var lieferantPlz: String? = null
        var lieferantOrt: String? = null

        constructor()
        constructor(
            dokumentTyp: LieferantDokumentTyp?,
            dokumentNummer: String?,
            dokumentDatum: LocalDate?,
            betragNetto: BigDecimal?,
            betragBrutto: BigDecimal?,
            mwstSatz: BigDecimal?,
            liefertermin: LocalDate?,
            zahlungsziel: LocalDate?,
            bestellnummer: String?,
            referenzNummer: String?,
            skontoTage: Int?,
            skontoProzent: BigDecimal?,
            nettoTage: Int?,
            bereitsGezahlt: Boolean?,
            zahlungsart: String?,
            aiConfidence: Double?,
            analyseQuelle: String?,
            lieferantName: String?,
            lieferantStrasse: String?,
            lieferantPlz: String?,
            lieferantOrt: String?,
        ) {
            this.dokumentTyp = dokumentTyp
            this.dokumentNummer = dokumentNummer
            this.dokumentDatum = dokumentDatum
            this.betragNetto = betragNetto
            this.betragBrutto = betragBrutto
            this.mwstSatz = mwstSatz
            this.liefertermin = liefertermin
            this.zahlungsziel = zahlungsziel
            this.bestellnummer = bestellnummer
            this.referenzNummer = referenzNummer
            this.skontoTage = skontoTage
            this.skontoProzent = skontoProzent
            this.nettoTage = nettoTage
            this.bereitsGezahlt = bereitsGezahlt
            this.zahlungsart = zahlungsart
            this.aiConfidence = aiConfidence
            this.analyseQuelle = analyseQuelle
            this.lieferantName = lieferantName
            this.lieferantStrasse = lieferantStrasse
            this.lieferantPlz = lieferantPlz
            this.lieferantOrt = lieferantOrt
        }

        class AnalyzeResponseBuilder {
            private val value = AnalyzeResponse()
            fun dokumentTyp(dokumentTyp: LieferantDokumentTyp?) = apply { value.dokumentTyp = dokumentTyp }
            fun dokumentNummer(dokumentNummer: String?) = apply { value.dokumentNummer = dokumentNummer }
            fun dokumentDatum(dokumentDatum: LocalDate?) = apply { value.dokumentDatum = dokumentDatum }
            fun betragNetto(betragNetto: BigDecimal?) = apply { value.betragNetto = betragNetto }
            fun betragBrutto(betragBrutto: BigDecimal?) = apply { value.betragBrutto = betragBrutto }
            fun mwstSatz(mwstSatz: BigDecimal?) = apply { value.mwstSatz = mwstSatz }
            fun liefertermin(liefertermin: LocalDate?) = apply { value.liefertermin = liefertermin }
            fun zahlungsziel(zahlungsziel: LocalDate?) = apply { value.zahlungsziel = zahlungsziel }
            fun bestellnummer(bestellnummer: String?) = apply { value.bestellnummer = bestellnummer }
            fun referenzNummer(referenzNummer: String?) = apply { value.referenzNummer = referenzNummer }
            fun skontoTage(skontoTage: Int?) = apply { value.skontoTage = skontoTage }
            fun skontoProzent(skontoProzent: BigDecimal?) = apply { value.skontoProzent = skontoProzent }
            fun nettoTage(nettoTage: Int?) = apply { value.nettoTage = nettoTage }
            fun bereitsGezahlt(bereitsGezahlt: Boolean?) = apply { value.bereitsGezahlt = bereitsGezahlt }
            fun zahlungsart(zahlungsart: String?) = apply { value.zahlungsart = zahlungsart }
            fun aiConfidence(aiConfidence: Double?) = apply { value.aiConfidence = aiConfidence }
            fun analyseQuelle(analyseQuelle: String?) = apply { value.analyseQuelle = analyseQuelle }
            fun lieferantName(lieferantName: String?) = apply { value.lieferantName = lieferantName }
            fun lieferantStrasse(lieferantStrasse: String?) = apply { value.lieferantStrasse = lieferantStrasse }
            fun lieferantPlz(lieferantPlz: String?) = apply { value.lieferantPlz = lieferantPlz }
            fun lieferantOrt(lieferantOrt: String?) = apply { value.lieferantOrt = lieferantOrt }
            fun build() = value
        }

        companion object {
            @JvmStatic
            fun builder() = AnalyzeResponseBuilder()
        }
    }

    class ImportRequest {
        var dokumentTyp: LieferantDokumentTyp? = null
        var dokumentNummer: String? = null
        var dokumentDatum: LocalDate? = null
        var betragNetto: BigDecimal? = null
        var betragBrutto: BigDecimal? = null
        var mwstSatz: BigDecimal? = null
        var liefertermin: LocalDate? = null
        var zahlungsziel: LocalDate? = null
        var bestellnummer: String? = null
        var referenzNummer: String? = null
        var skontoTage: Int? = null
        var skontoProzent: BigDecimal? = null
        var nettoTage: Int? = null
        var bereitsGezahlt: Boolean? = null
        var zahlungsart: String? = null
        var lieferantId: Long? = null
        var lieferantName: String? = null

        constructor()
        constructor(
            dokumentTyp: LieferantDokumentTyp?,
            dokumentNummer: String?,
            dokumentDatum: LocalDate?,
            betragNetto: BigDecimal?,
            betragBrutto: BigDecimal?,
            mwstSatz: BigDecimal?,
            liefertermin: LocalDate?,
            zahlungsziel: LocalDate?,
            bestellnummer: String?,
            referenzNummer: String?,
            skontoTage: Int?,
            skontoProzent: BigDecimal?,
            nettoTage: Int?,
            bereitsGezahlt: Boolean?,
            zahlungsart: String?,
            lieferantId: Long?,
            lieferantName: String?,
        ) {
            this.dokumentTyp = dokumentTyp
            this.dokumentNummer = dokumentNummer
            this.dokumentDatum = dokumentDatum
            this.betragNetto = betragNetto
            this.betragBrutto = betragBrutto
            this.mwstSatz = mwstSatz
            this.liefertermin = liefertermin
            this.zahlungsziel = zahlungsziel
            this.bestellnummer = bestellnummer
            this.referenzNummer = referenzNummer
            this.skontoTage = skontoTage
            this.skontoProzent = skontoProzent
            this.nettoTage = nettoTage
            this.bereitsGezahlt = bereitsGezahlt
            this.zahlungsart = zahlungsart
            this.lieferantId = lieferantId
            this.lieferantName = lieferantName
        }
    }

    class MultiInvoiceAnalyzeResponse {
        var pageRange: String? = null
        var analyzeResponse: AnalyzeResponse? = null
        var splitPdfBase64: String? = null

        constructor()
        constructor(pageRange: String?, analyzeResponse: AnalyzeResponse?, splitPdfBase64: String?) {
            this.pageRange = pageRange
            this.analyzeResponse = analyzeResponse
            this.splitPdfBase64 = splitPdfBase64
        }

        class MultiInvoiceAnalyzeResponseBuilder {
            private val value = MultiInvoiceAnalyzeResponse()
            fun pageRange(pageRange: String?) = apply { value.pageRange = pageRange }
            fun analyzeResponse(analyzeResponse: AnalyzeResponse?) = apply { value.analyzeResponse = analyzeResponse }
            fun splitPdfBase64(splitPdfBase64: String?) = apply { value.splitPdfBase64 = splitPdfBase64 }
            fun build() = value
        }

        companion object {
            @JvmStatic
            fun builder() = MultiInvoiceAnalyzeResponseBuilder()
        }
    }
}
