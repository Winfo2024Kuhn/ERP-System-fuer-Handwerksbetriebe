package org.example.kalkulationsprogramm.dto.Freigabe

import java.math.BigDecimal

class FreigabePositionDto {
    var blockId: String? = null
    var typ: String? = null
    var pos: String? = null
    var bezeichnung: String? = null
    var beschreibungHtml: String? = null
    var menge: BigDecimal? = null
    var einheit: String? = null
    var einzelpreisNetto: BigDecimal? = null
    var rabattProzent: BigDecimal? = null
    var gesamtpreisNetto: BigDecimal? = null
    var optional: Boolean = false
    var sectionLabel: String? = null
    var children: List<FreigabePositionDto>? = null

    constructor()

    constructor(
        blockId: String?,
        typ: String?,
        pos: String?,
        bezeichnung: String?,
        beschreibungHtml: String?,
        menge: BigDecimal?,
        einheit: String?,
        einzelpreisNetto: BigDecimal?,
        rabattProzent: BigDecimal?,
        gesamtpreisNetto: BigDecimal?,
        optional: Boolean,
        sectionLabel: String?,
        children: List<FreigabePositionDto>?,
    ) {
        this.blockId = blockId
        this.typ = typ
        this.pos = pos
        this.bezeichnung = bezeichnung
        this.beschreibungHtml = beschreibungHtml
        this.menge = menge
        this.einheit = einheit
        this.einzelpreisNetto = einzelpreisNetto
        this.rabattProzent = rabattProzent
        this.gesamtpreisNetto = gesamtpreisNetto
        this.optional = optional
        this.sectionLabel = sectionLabel
        this.children = children
    }

    fun isOptional(): Boolean = optional

    class FreigabePositionDtoBuilder {
        private var blockId: String? = null
        private var typ: String? = null
        private var pos: String? = null
        private var bezeichnung: String? = null
        private var beschreibungHtml: String? = null
        private var menge: BigDecimal? = null
        private var einheit: String? = null
        private var einzelpreisNetto: BigDecimal? = null
        private var rabattProzent: BigDecimal? = null
        private var gesamtpreisNetto: BigDecimal? = null
        private var optional: Boolean = false
        private var sectionLabel: String? = null
        private var children: List<FreigabePositionDto>? = null

        fun blockId(blockId: String?) = apply { this.blockId = blockId }
        fun typ(typ: String?) = apply { this.typ = typ }
        fun pos(pos: String?) = apply { this.pos = pos }
        fun bezeichnung(bezeichnung: String?) = apply { this.bezeichnung = bezeichnung }
        fun beschreibungHtml(beschreibungHtml: String?) = apply { this.beschreibungHtml = beschreibungHtml }
        fun menge(menge: BigDecimal?) = apply { this.menge = menge }
        fun einheit(einheit: String?) = apply { this.einheit = einheit }
        fun einzelpreisNetto(einzelpreisNetto: BigDecimal?) = apply { this.einzelpreisNetto = einzelpreisNetto }
        fun rabattProzent(rabattProzent: BigDecimal?) = apply { this.rabattProzent = rabattProzent }
        fun gesamtpreisNetto(gesamtpreisNetto: BigDecimal?) = apply { this.gesamtpreisNetto = gesamtpreisNetto }
        fun optional(optional: Boolean) = apply { this.optional = optional }
        fun sectionLabel(sectionLabel: String?) = apply { this.sectionLabel = sectionLabel }
        fun children(children: List<FreigabePositionDto>?) = apply { this.children = children }

        fun build(): FreigabePositionDto =
            FreigabePositionDto(
                blockId,
                typ,
                pos,
                bezeichnung,
                beschreibungHtml,
                menge,
                einheit,
                einzelpreisNetto,
                rabattProzent,
                gesamtpreisNetto,
                optional,
                sectionLabel,
                children,
            )
    }

    companion object {
        @JvmStatic
        fun builder(): FreigabePositionDtoBuilder = FreigabePositionDtoBuilder()
    }
}
