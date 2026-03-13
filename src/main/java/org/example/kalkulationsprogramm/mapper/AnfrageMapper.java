package org.example.kalkulationsprogramm.mapper;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class AnfrageMapper {

    public AnfrageResponseDto toAnfrageResponseDto(Anfrage a) {
        if (a == null) {
            return null;
        }
        AnfrageResponseDto dto = new AnfrageResponseDto();
        dto.setId(a.getId());
        if (a.getKunde() != null) {
            dto.setKundenId(a.getKunde().getId());
            dto.setKundenName(sanitize(a.getKunde().getName()));
            dto.setKundennummer(a.getKunde().getKundennummer());

            List<String> allEmails = new ArrayList<>();
            if (a.getKunde().getKundenEmails() != null) {
                allEmails.addAll(a.getKunde().getKundenEmails());
            }
            if (a.getKundenEmails() != null) {
                allEmails.addAll(a.getKundenEmails());
            }
            dto.setKundenEmails(allEmails.stream().distinct().toList());

            dto.setKundenStrasse(a.getKunde().getStrasse());
            dto.setKundenPlz(a.getKunde().getPlz());
            dto.setKundenOrt(a.getKunde().getOrt());
            dto.setKundenTelefon(a.getKunde().getTelefon());
            dto.setKundenMobiltelefon(a.getKunde().getMobiltelefon());
            dto.setKundenAnsprechpartner(a.getKunde().getAnsprechspartner());
            // Anrede als String-Name des Enum übertragen
            dto.setKundenAnrede(a.getKunde().getAnrede() != null ? a.getKunde().getAnrede().name() : null);
        }

        return dto;
    }

    private String sanitize(String s) {
        if (s == null)
            return null;
        return s.replace("�", "ss").replace("?", "ss").replace("?", "");
    }

}
