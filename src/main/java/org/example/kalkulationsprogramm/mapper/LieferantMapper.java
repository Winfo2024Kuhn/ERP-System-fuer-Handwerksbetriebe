package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantListItemDto;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LieferantMapper {

    private static final Logger log = LoggerFactory.getLogger(LieferantMapper.class);

    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;

    /**
     * Basis-Mapping ohne Berechnungen (für Listen)
     */
    public LieferantListItemDto toListItem(Lieferanten lieferant) {
        if (lieferant == null) {
            return null;
        }
        LieferantListItemDto dto = new LieferantListItemDto();
        dto.setId(lieferant.getId());
        dto.setLieferantenname(lieferant.getLieferantenname());
        dto.setLieferantenTyp(lieferant.getLieferantenTyp());
        dto.setVertreter(lieferant.getVertreter());
        dto.setStrasse(lieferant.getStrasse());
        dto.setPlz(lieferant.getPlz());
        dto.setOrt(lieferant.getOrt());
        dto.setTelefon(lieferant.getTelefon());
        dto.setMobiltelefon(lieferant.getMobiltelefon());
        dto.setIstAktiv(lieferant.getIstAktiv());
        dto.setKundenEmails(lieferant.getKundenEmails());
        return dto;
    }

    /**
     * Mapping MIT Berechnungen (für Detailansicht)
     */
    public LieferantListItemDto toDetailItem(Lieferanten lieferant) {
        LieferantListItemDto dto = toListItem(lieferant);
        if (dto == null) return null;
        
        // Berechne durchschnittliche Lieferzeit aus Auftragsbestätigungen
        try {
            Double avgLieferzeit = geschaeftsdokumentRepository.calculateAverageLieferzeitByLieferantId(lieferant.getId());
            if (avgLieferzeit != null && avgLieferzeit > 0) {
                dto.setLieferzeit(avgLieferzeit.intValue());
            }
        } catch (Exception e) {
            log.warn("Fehler bei Lieferzeit-Berechnung für Lieferant {}: {}", lieferant.getId(), e.getMessage());
        }
        
        // Berechne Anzahl der Bestellungen (Auftragsbestätigungen)
        try {
            Long bestellungen = geschaeftsdokumentRepository.countBestellungenByLieferantId(lieferant.getId());
            if (bestellungen != null) {
                dto.setBestellungen(bestellungen.intValue());
            }
        } catch (Exception e) {
            log.warn("Fehler bei Bestellungen-Zählung für Lieferant {}: {}", lieferant.getId(), e.getMessage());
        }
        
        return dto;
    }
}
