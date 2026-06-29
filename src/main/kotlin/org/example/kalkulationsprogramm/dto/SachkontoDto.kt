package org.example.kalkulationsprogramm.dto

import java.math.BigDecimal

class SachkontoDto private constructor() {
    class Response() {
        var id: Long? = null
        var nummer: String? = null
        var bezeichnung: String? = null
        var kontoTyp: String? = null
        var beschreibung: String? = null
        var aktiv: Boolean = false
        var sortierung: Int = 0

        constructor(
            id: Long?,
            nummer: String?,
            bezeichnung: String?,
            kontoTyp: String?,
            beschreibung: String?,
            aktiv: Boolean,
            sortierung: Int,
        ) : this() {
            this.id = id
            this.nummer = nummer
            this.bezeichnung = bezeichnung
            this.kontoTyp = kontoTyp
            this.beschreibung = beschreibung
            this.aktiv = aktiv
            this.sortierung = sortierung
        }

        fun isAktiv(): Boolean = aktiv

        class ResponseBuilder {
            private var id: Long? = null
            private var nummer: String? = null
            private var bezeichnung: String? = null
            private var kontoTyp: String? = null
            private var beschreibung: String? = null
            private var aktiv: Boolean = false
            private var sortierung: Int = 0

            fun id(id: Long?) = apply { this.id = id }
            fun nummer(nummer: String?) = apply { this.nummer = nummer }
            fun bezeichnung(bezeichnung: String?) = apply { this.bezeichnung = bezeichnung }
            fun kontoTyp(kontoTyp: String?) = apply { this.kontoTyp = kontoTyp }
            fun beschreibung(beschreibung: String?) = apply { this.beschreibung = beschreibung }
            fun aktiv(aktiv: Boolean) = apply { this.aktiv = aktiv }
            fun sortierung(sortierung: Int) = apply { this.sortierung = sortierung }
            fun build(): Response = Response(id, nummer, bezeichnung, kontoTyp, beschreibung, aktiv, sortierung)
        }

        companion object {
            @JvmStatic
            fun builder(): ResponseBuilder = ResponseBuilder()
        }
    }

    class UpsertRequest() {
        var nummer: String? = null
        var bezeichnung: String? = null
        var kontoTyp: String? = null
        var beschreibung: String? = null
        var aktiv: Boolean? = null
        var sortierung: Int? = null

        constructor(
            nummer: String?,
            bezeichnung: String?,
            kontoTyp: String?,
            beschreibung: String?,
            aktiv: Boolean?,
            sortierung: Int?,
        ) : this() {
            this.nummer = nummer
            this.bezeichnung = bezeichnung
            this.kontoTyp = kontoTyp
            this.beschreibung = beschreibung
            this.aktiv = aktiv
            this.sortierung = sortierung
        }
    }

    class AuswertungZeile() {
        var sachkontoId: Long? = null
        var nummer: String? = null
        var bezeichnung: String? = null
        var kontoTyp: String? = null
        var summe: BigDecimal? = null
        var anzahlBelege: Int = 0

        constructor(
            sachkontoId: Long?,
            nummer: String?,
            bezeichnung: String?,
            kontoTyp: String?,
            summe: BigDecimal?,
            anzahlBelege: Int,
        ) : this() {
            this.sachkontoId = sachkontoId
            this.nummer = nummer
            this.bezeichnung = bezeichnung
            this.kontoTyp = kontoTyp
            this.summe = summe
            this.anzahlBelege = anzahlBelege
        }

        class AuswertungZeileBuilder {
            private var sachkontoId: Long? = null
            private var nummer: String? = null
            private var bezeichnung: String? = null
            private var kontoTyp: String? = null
            private var summe: BigDecimal? = null
            private var anzahlBelege: Int = 0

            fun sachkontoId(sachkontoId: Long?) = apply { this.sachkontoId = sachkontoId }
            fun nummer(nummer: String?) = apply { this.nummer = nummer }
            fun bezeichnung(bezeichnung: String?) = apply { this.bezeichnung = bezeichnung }
            fun kontoTyp(kontoTyp: String?) = apply { this.kontoTyp = kontoTyp }
            fun summe(summe: BigDecimal?) = apply { this.summe = summe }
            fun anzahlBelege(anzahlBelege: Int) = apply { this.anzahlBelege = anzahlBelege }
            fun build(): AuswertungZeile = AuswertungZeile(sachkontoId, nummer, bezeichnung, kontoTyp, summe, anzahlBelege)
        }

        companion object {
            @JvmStatic
            fun builder(): AuswertungZeileBuilder = AuswertungZeileBuilder()
        }
    }

    class AuswertungResponse() {
        var von: String? = null
        var bis: String? = null
        var summeAufwand: BigDecimal? = null
        var summeErtrag: BigDecimal? = null
        var summePrivat: BigDecimal? = null
        var summeOhneKonto: BigDecimal? = null
        var zeilen: List<AuswertungZeile>? = null

        constructor(
            von: String?,
            bis: String?,
            summeAufwand: BigDecimal?,
            summeErtrag: BigDecimal?,
            summePrivat: BigDecimal?,
            summeOhneKonto: BigDecimal?,
            zeilen: List<AuswertungZeile>?,
        ) : this() {
            this.von = von
            this.bis = bis
            this.summeAufwand = summeAufwand
            this.summeErtrag = summeErtrag
            this.summePrivat = summePrivat
            this.summeOhneKonto = summeOhneKonto
            this.zeilen = zeilen
        }

        class AuswertungResponseBuilder {
            private var von: String? = null
            private var bis: String? = null
            private var summeAufwand: BigDecimal? = null
            private var summeErtrag: BigDecimal? = null
            private var summePrivat: BigDecimal? = null
            private var summeOhneKonto: BigDecimal? = null
            private var zeilen: List<AuswertungZeile>? = null

            fun von(von: String?) = apply { this.von = von }
            fun bis(bis: String?) = apply { this.bis = bis }
            fun summeAufwand(summeAufwand: BigDecimal?) = apply { this.summeAufwand = summeAufwand }
            fun summeErtrag(summeErtrag: BigDecimal?) = apply { this.summeErtrag = summeErtrag }
            fun summePrivat(summePrivat: BigDecimal?) = apply { this.summePrivat = summePrivat }
            fun summeOhneKonto(summeOhneKonto: BigDecimal?) = apply { this.summeOhneKonto = summeOhneKonto }
            fun zeilen(zeilen: List<AuswertungZeile>?) = apply { this.zeilen = zeilen }
            fun build(): AuswertungResponse =
                AuswertungResponse(von, bis, summeAufwand, summeErtrag, summePrivat, summeOhneKonto, zeilen)
        }

        companion object {
            @JvmStatic
            fun builder(): AuswertungResponseBuilder = AuswertungResponseBuilder()
        }
    }
}
