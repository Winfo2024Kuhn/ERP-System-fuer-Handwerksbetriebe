package org.example.kalkulationsprogramm.mapper;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantListItemDto;
import org.example.kalkulationsprogramm.service.lieferantansprechpartner.LieferantAnsprechpartnerUebersicht;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LieferantMapper {

    private final LieferantAnsprechpartnerUebersicht ansprechpartnerUebersicht;

    /**
     * Basis-Mapping ohne Berechnungen und ohne Ansprechpartner-Hinweis (z. B. Antwort beim Anlegen).
     */
    public LieferantListItemDto toListItem(Lieferanten lieferant) {
        if (lieferant == null) {
            return null;
        }
        LieferantListItemDto dto = new LieferantListItemDto();
        dto.setId(lieferant.getId());
        dto.setLieferantenname(lieferant.getLieferantenname());
        dto.setAliasName(lieferant.getAliasName());
        dto.setLieferantenTyp(lieferant.getLieferantenTyp());
        dto.setRollen(lieferant.getRollen());
        dto.setStrasse(lieferant.getStrasse());
        dto.setPlz(lieferant.getPlz());
        dto.setOrt(lieferant.getOrt());
        dto.setTelefon(lieferant.getTelefon());
        dto.setMobiltelefon(lieferant.getMobiltelefon());
        dto.setIstAktiv(lieferant.getIstAktiv());
        dto.setKundenEmails(lieferant.getKundenEmails());
        dto.setVorauskasse(Boolean.TRUE.equals(lieferant.getVorauskasse()));
        if (lieferant.getStandardKostenstelle() != null) {
            dto.setStandardKostenstelleId(lieferant.getStandardKostenstelle().getId());
            dto.setStandardKostenstelleName(lieferant.getStandardKostenstelle().getBezeichnung());
        }
        return dto;
    }

    /**
     * Eine Seite der Lieferantenliste samt Ansprechpartner-Hinweis („Max Mustermann +2“),
     * die Hinweise für alle Lieferanten der Seite in einer Abfrage geladen.
     */
    public List<LieferantListItemDto> toListItems(List<Lieferanten> seite) {
        if (seite.isEmpty()) {
            return List.of();
        }
        Map<Long, String> hinweise = ansprechpartnerUebersicht.hinweise(seite.stream().map(Lieferanten::getId).toList());
        return seite.stream().map(l -> {
            LieferantListItemDto dto = toListItem(l);
            dto.setAnsprechpartnerHinweis(hinweise.get(l.getId()));
            return dto;
        }).toList();
    }
}
