package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeListItemDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeResponseDto;
import org.springframework.stereotype.Component;

@Component
public class KundeMapper {
    public KundeListItemDto toListItem(Kunde kunde) {
        if (kunde == null) {
            return null;
        }
        KundeListItemDto dto = new KundeListItemDto();
        dto.setId(kunde.getId());
        dto.setKundennummer(kunde.getKundennummer());
        dto.setName(kunde.getName());
        dto.setAnrede(kunde.getAnrede() != null ? kunde.getAnrede().name() : null);
        dto.setAnsprechspartner(kunde.getAnsprechspartner());
        dto.setStrasse(kunde.getStrasse());
        dto.setPlz(kunde.getPlz());
        dto.setOrt(kunde.getOrt());
        dto.setTelefon(kunde.getTelefon());
        dto.setMobiltelefon(kunde.getMobiltelefon());
        dto.setKundenEmails(kunde.getKundenEmails());
        dto.setHatProjekte(kunde.getProjekts() != null && !kunde.getProjekts().isEmpty());
        return dto;
    }

    public KundeResponseDto toResponseDto(Kunde kunde) {
        if (kunde == null) {
            return null;
        }
        KundeResponseDto dto = new KundeResponseDto();
        dto.setId(kunde.getId());
        dto.setKundennummer(kunde.getKundennummer());
        dto.setName(kunde.getName());
        dto.setAnrede(kunde.getAnrede() != null ? kunde.getAnrede().name() : null);
        dto.setAnsprechspartner(kunde.getAnsprechspartner());
        dto.setStrasse(kunde.getStrasse());
        dto.setPlz(kunde.getPlz());
        dto.setOrt(kunde.getOrt());
        dto.setTelefon(kunde.getTelefon());
        dto.setMobiltelefon(kunde.getMobiltelefon());
        dto.setKundenEmails(kunde.getKundenEmails());
        return dto;
    }
}
