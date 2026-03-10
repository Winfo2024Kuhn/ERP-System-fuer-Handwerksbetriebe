package org.example.kalkulationsprogramm.mapper;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangResponseDto;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ArbeitsgangMapper {

    private final ArbeitsgangStundensatzRepository stundensatzRepository;

    public ArbeitsgangResponseDto toArbeitsgangResponseDto(Arbeitsgang arbeitsgang) {
        if (arbeitsgang == null) {
            return null;
        }
        ArbeitsgangResponseDto dto = new ArbeitsgangResponseDto();
        dto.setId(arbeitsgang.getId());
        dto.setBeschreibung(arbeitsgang.getBeschreibung());

        // Abteilung-Mapping
        if (arbeitsgang.getAbteilung() != null) {
            dto.setAbteilungId(arbeitsgang.getAbteilung().getId());
            dto.setAbteilungName(arbeitsgang.getAbteilung().getName());
        }

        // Stundensatz und Jahr mappen
        stundensatzRepository.findTopByArbeitsgangIdOrderByJahrDesc(arbeitsgang.getId())
                .ifPresent((ArbeitsgangStundensatz s) -> {
                    dto.setStundensatz(s.getSatz());
                    dto.setStundensatzJahr(s.getJahr());
                });

        return dto;
    }
}
