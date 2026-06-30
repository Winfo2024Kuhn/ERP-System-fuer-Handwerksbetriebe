package org.example.kalkulationsprogramm.mapper

import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelInProjektResponseDto
import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenResponseDto
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto
import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieResponseDto
import org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitResponseDto
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class ProjektMapper(
    private val produktkategorieMapper: ProduktkategorieMapper,
    private val anfrageMapper: AnfrageMapper,
    private val kundeMapper: KundeMapper,
) {
    fun toProjektResponseDto(projekt: Projekt?): ProjektResponseDto? {
        if (projekt == null) {
            return null
        }

        val dto = fillBaseDto(projekt)
        dto.kundenEmails = projekt.kundenEmails
        dto.projektArt = projekt.projektArt.name
        dto.isProduktiv = projekt.projektArt.isProduktiv()

        dto.produktkategorien = projekt.projektProduktkategorien.map { ppk ->
            ProjektProduktkategorieResponseDto().apply {
                id = ppk.id
                if (ppk.produktkategorie != null) {
                    produktkategorie =
                        produktkategorieMapper.toProduktkategorieResponseDto(ppk.produktkategorie)
                }
                menge = ppk.menge
            }
        }

        dto.materialkosten = projekt.materialkosten.map { mk ->
            MaterialkostenResponseDto().apply {
                id = mk.id
                beschreibung = mk.beschreibung
                externeArtikelnummer = mk.externeArtikelnummer
                monat = mk.monat
                betrag = mk.betrag
            }
        }

        dto.artikel = projekt.artikelInProjekt.map { aip ->
            ArtikelInProjektResponseDto().apply {
                id = aip.id
                aip.artikel?.let { artikel ->
                    artikelId = artikel.id
                    externeArtikelnummer = artikel.getExterneArtikelnummer()
                    produktname = artikel.produktname
                    produkttext = artikel.produkttext
                }
                stueckzahl = aip.stueckzahl
                meter = aip.meter
                kilogramm = aip.kilogramm
                werkstoffName = aip.artikel?.werkstoff?.name
                preisProStueck = berechnePreisProStueck(aip)
                hinzugefuegtAm = aip.hinzugefuegtAm
                isBestellt = aip.isBestellt()
                bestelltAm = aip.bestelltAm
                kommentar = aip.kommentar
                schnittForm = aip.schnittForm
                anschnittWinkelLinks = aip.anschnittWinkelLinks
                anschnittWinkelRechts = aip.anschnittWinkelRechts
                lieferantName = aip.lieferant?.lieferantenname
            }
        }

        dto.zeiten = projekt.zeitbuchungen.map { zeitEntity ->
            ZeitResponseDto().apply {
                id = zeitEntity.id
                anzahlInStunden = zeitEntity.anzahlInStunden
                stundensatz = zeitEntity.arbeitsgangStundensatz?.satz
                arbeitsgangBeschreibung = zeitEntity.arbeitsgang?.beschreibung
                val produktkategorie = zeitEntity.projektProduktkategorie?.produktkategorie
                if (produktkategorie != null) {
                    this.produktkategorie =
                        produktkategorieMapper.toProduktkategorieResponseDto(produktkategorie)
                }
                mitarbeiterVorname = zeitEntity.mitarbeiter?.vorname
                mitarbeiterNachname = zeitEntity.mitarbeiter?.nachname
            }
        }

        dto.anfragen = projekt.anfragen.mapNotNull(anfrageMapper::toAnfrageResponseDto)
        return dto
    }

    fun toProjektListeDto(projekt: Projekt?): ProjektResponseDto? {
        if (projekt == null) {
            return null
        }
        return fillBaseDto(projekt)
    }

    private fun fillBaseDto(projekt: Projekt): ProjektResponseDto =
        ProjektResponseDto().apply {
            id = projekt.id
            bauvorhaben = projekt.bauvorhaben
            strasse = projekt.strasse
            plz = projekt.plz
            ort = projekt.ort
            kunde = projekt.getKunde()
            projekt.kundenId?.let { kundeDto = kundeMapper.toResponseDto(it) }
            kundenId = projekt.kundenId?.id
            kurzbeschreibung = projekt.kurzbeschreibung
            anlegedatum = projekt.anlegedatum
            abschlussdatum = projekt.abschlussdatum
            bildUrl = normalizeBildUrl(projekt.bildUrl)
            kundennummer = projekt.getKundennummer()
            auftragsnummer = projekt.auftragsnummer
            bruttoPreis = projekt.bruttoPreis
            isBezahlt = projekt.isBezahlt()
            isAbgeschlossen = projekt.isAbgeschlossen()
        }

    private fun normalizeBildUrl(bildUrl: String?): String? =
        if (!bildUrl.isNullOrBlank() && !bildUrl.startsWith("/")) {
            "/api/images/$bildUrl"
        } else {
            bildUrl
        }

    private fun berechnePreisProStueck(aip: org.example.kalkulationsprogramm.domain.ArtikelInProjekt): BigDecimal? {
        var preisProStueck = aip.preisProStueck
        val lieferantenPreis = aip.lieferantenArtikelPreis?.preis
        if (!aip.isBestellt() && lieferantenPreis != null) {
            val menge = when (aip.artikel?.verrechnungseinheit) {
                org.example.kalkulationsprogramm.domain.Verrechnungseinheit.STUECK ->
                    aip.stueckzahl?.toLong()?.let(BigDecimal::valueOf) ?: BigDecimal.ZERO

                org.example.kalkulationsprogramm.domain.Verrechnungseinheit.LAUFENDE_METER,
                org.example.kalkulationsprogramm.domain.Verrechnungseinheit.QUADRATMETER,
                    -> aip.meter ?: BigDecimal.ZERO

                org.example.kalkulationsprogramm.domain.Verrechnungseinheit.KILOGRAMM ->
                    aip.kilogramm ?: BigDecimal.ZERO

                null -> BigDecimal.ZERO
            }
            preisProStueck = lieferantenPreis.multiply(menge)
        }
        return preisProStueck
    }
}
