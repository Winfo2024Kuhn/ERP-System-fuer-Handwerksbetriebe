package org.example.kalkulationsprogramm.mapper

import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantListItemDto
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.springframework.stereotype.Component

@Component
class LieferantMapper(
    private val geschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository
) {
    fun toListItem(lieferant: Lieferanten?): LieferantListItemDto? {
        if (lieferant == null) return null
        val dto = LieferantListItemDto()
        dto.id = lieferant.id
        dto.lieferantenname = lieferant.lieferantenname
        dto.lieferantenTyp = lieferant.lieferantenTyp
        dto.rollen = lieferant.rollen
        dto.vertreter = lieferant.vertreter
        dto.strasse = lieferant.strasse
        dto.plz = lieferant.plz
        dto.ort = lieferant.ort
        dto.telefon = lieferant.telefon
        dto.mobiltelefon = lieferant.mobiltelefon
        dto.istAktiv = lieferant.istAktiv
        dto.kundenEmails = lieferant.kundenEmails
        val standardKostenstelle = lieferant.standardKostenstelle
        if (standardKostenstelle != null) {
            dto.standardKostenstelleId = standardKostenstelle.id
            dto.standardKostenstelleName = standardKostenstelle.bezeichnung
        }
        return dto
    }

    fun toDetailItem(lieferant: Lieferanten?): LieferantListItemDto? {
        val dto = toListItem(lieferant) ?: return null
        if (lieferant == null) return null
        try {
            val avgLieferzeit = geschaeftsdokumentRepository.calculateAverageLieferzeitByLieferantId(lieferant.id)
            if (avgLieferzeit != null && avgLieferzeit > 0) {
                dto.lieferzeit = avgLieferzeit.toInt()
            }
        } catch (ex: Exception) {
            System.err.println("[LieferantMapper] Fehler bei Lieferzeit-Berechnung: ${ex.message}")
        }
        try {
            val bestellungen = geschaeftsdokumentRepository.countBestellungenByLieferantId(lieferant.id)
            if (bestellungen != null) {
                dto.bestellungen = bestellungen.toInt()
            }
        } catch (ex: Exception) {
            System.err.println("[LieferantMapper] Fehler bei Bestellungen-Zaehlung: ${ex.message}")
        }
        return dto
    }
}
