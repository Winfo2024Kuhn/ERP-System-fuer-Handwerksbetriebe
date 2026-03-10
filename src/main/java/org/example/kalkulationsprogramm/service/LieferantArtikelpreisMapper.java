package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisDto;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class LieferantArtikelpreisMapper {

    public LieferantArtikelpreisDto toDto(LieferantenArtikelPreise entity) {
        if (entity == null) {
            return null;
        }
        LieferantArtikelpreisDto dto = new LieferantArtikelpreisDto();
        if (entity.getArtikel() != null) {
            dto.setArtikelId(entity.getArtikel().getId());
            dto.setProduktname(entity.getArtikel().getProduktname());
            dto.setProdukttext(entity.getArtikel().getProdukttext());
            if (entity.getArtikel().getWerkstoff() != null) {
                dto.setWerkstoff(entity.getArtikel().getWerkstoff().getName());
            }
        }
        dto.setExterneArtikelnummer(entity.getExterneArtikelnummer());
        dto.setPreis(entity.getPreis());
        dto.setPreisAenderungsdatum(toLocalDate(entity.getPreisAenderungsdatum()));
        return dto;
    }

    public List<LieferantArtikelpreisDto> toDtoList(List<LieferantenArtikelPreise> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .filter(Objects::nonNull)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private java.time.LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
