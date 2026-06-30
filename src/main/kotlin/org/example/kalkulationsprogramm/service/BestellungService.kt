package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.ArtikelInProjekt
import org.example.kalkulationsprogramm.domain.Kategorie
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class BestellungService(
    private val artikelInProjektRepository: ArtikelInProjektRepository,
) {
    fun findeOffeneBestellungen(): List<BestellungResponseDto> {
        val dtos = artikelInProjektRepository
            .findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc()
            .map(::toDto)

        val sum = dtos
            .mapNotNull { it.kilogramm }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        val total = if (sum.compareTo(BigDecimal.ZERO) > 0) sum else null
        dtos.forEach { it.gesamtKilogramm = total }
        return dtos
    }

    @Transactional
    fun setBestellt(id: Long, bestellt: Boolean) {
        val aip = artikelInProjektRepository.findById(id)
            .orElseThrow { RuntimeException("Artikel nicht gefunden") }

        aip.bestellt = bestellt
        aip.bestelltAm = if (bestellt) LocalDate.now() else null
        if (bestellt && aip.lieferantenArtikelPreis != null) {
            var preis = aip.lieferantenArtikelPreis?.preis
            if (preis != null) {
                val verrechnungseinheit = aip.artikel?.verrechnungseinheit
                if (verrechnungseinheit != null) {
                    preis = when (verrechnungseinheit) {
                        Verrechnungseinheit.KILOGRAMM -> aip.kilogramm?.let(preis::multiply) ?: preis
                        Verrechnungseinheit.LAUFENDE_METER,
                        Verrechnungseinheit.QUADRATMETER,
                            -> aip.meter?.let(preis::multiply) ?: preis

                        Verrechnungseinheit.STUECK ->
                            aip.stueckzahl?.toLong()?.let { preis.multiply(BigDecimal.valueOf(it)) } ?: preis
                    }
                }
                aip.preisProStueck = preis
            } else {
                aip.preisProStueck = null
            }
            aip.lieferantenArtikelPreis = null
        }
        artikelInProjektRepository.save(aip)
    }

    private fun toDto(aip: ArtikelInProjekt): BestellungResponseDto {
        val dto = BestellungResponseDto()
        dto.id = aip.id

        var kat: Kategorie? = null
        aip.artikel?.let { artikel ->
            dto.artikelId = artikel.id
            dto.externeArtikelnummer = artikel.getExterneArtikelnummer()
            dto.produktname = artikel.produktname
            dto.produkttext = artikel.produkttext
            dto.kommentar = aip.kommentar
            dto.werkstoffName = artikel.werkstoff?.name
            kat = artikel.kategorie
            if (kat != null) {
                dto.kategorieName = kat?.beschreibung
                var root = kat
                while (root?.parentKategorie != null) {
                    root = root?.parentKategorie
                }
                dto.rootKategorieId = root?.id
                dto.rootKategorieName = root?.beschreibung
            }
        }

        val isWerkstoff = dto.rootKategorieId != null && dto.rootKategorieId == 1
        if (isWerkstoff) {
            dto.lieferantName = "Werkstoffe"
            dto.lieferantId = null
        } else if (aip.lieferant != null) {
            dto.lieferantName = aip.lieferant?.lieferantenname
            dto.lieferantId = aip.lieferant?.id
        }

        aip.projekt?.let { projekt ->
            dto.projektId = projekt.id
            dto.projektName = projekt.bauvorhaben
            dto.projektNummer = projekt.auftragsnummer
            dto.kundenName = projekt.getKunde()
        }

        dto.stueckzahl = aip.stueckzahl ?: 0
        val hasMeter = isWerkstoff && aip.meter != null && aip.meter!!.compareTo(BigDecimal.ZERO) > 0
        dto.menge = if (hasMeter) aip.meter else BigDecimal.valueOf(dto.stueckzahl.toLong())
        dto.einheit = if (hasMeter) "m" else "Stück"
        dto.kilogramm = aip.kilogramm
        dto.isBestellt = aip.isBestellt()
        dto.bestelltAm = aip.bestelltAm
        dto.schnittForm = aip.schnittForm
        dto.anschnittWinkelLinks = aip.anschnittWinkelLinks
        dto.anschnittWinkelRechts = aip.anschnittWinkelRechts
        return dto
    }
}
