package org.example.kalkulationsprogramm.mapper;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfragePosition;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageLieferantDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfragePositionDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageResponseDto;
import org.springframework.stereotype.Component;

/**
 * Expliziter Mapper (kein MapStruct, siehe {@link ProjektMapper}) fuer
 * Preisanfrage-Entities auf Response-DTOs. Hier NUR Entity &rarr; DTO; der
 * Input-Weg (DTO &rarr; Entity) passiert im {@code PreisanfrageService}, weil
 * dort zusaetzliche Geschaeftslogik (Token, Nummer, Status) greift.
 */
@Component
public class PreisanfrageMapper {

    public PreisanfrageResponseDto toResponseDto(Preisanfrage pa) {
        if (pa == null) {
            return null;
        }
        PreisanfrageResponseDto dto = new PreisanfrageResponseDto();
        dto.setId(pa.getId());
        dto.setNummer(pa.getNummer());
        dto.setBauvorhaben(pa.getBauvorhaben());
        dto.setProjektId(pa.getProjekt() != null ? pa.getProjekt().getId() : null);
        dto.setErstelltAm(pa.getErstelltAm());
        dto.setAntwortFrist(pa.getAntwortFrist());
        dto.setStatus(pa.getStatus() != null ? pa.getStatus().name() : null);
        dto.setNotiz(pa.getNotiz());
        dto.setVergebenAnPreisanfrageLieferantId(
                pa.getVergebenAn() != null ? pa.getVergebenAn().getId() : null);

        if (pa.getLieferanten() != null) {
            List<PreisanfrageLieferantDto> liefDtos = pa.getLieferanten().stream()
                    .map(this::toLieferantDto)
                    .toList();
            dto.setLieferanten(new java.util.ArrayList<>(liefDtos));
        }
        if (pa.getPositionen() != null) {
            List<PreisanfragePositionDto> posDtos = pa.getPositionen().stream()
                    .map(this::toPositionDto)
                    .toList();
            dto.setPositionen(new java.util.ArrayList<>(posDtos));
        }
        return dto;
    }

    public PreisanfrageLieferantDto toLieferantDto(PreisanfrageLieferant pal) {
        if (pal == null) {
            return null;
        }
        PreisanfrageLieferantDto dto = new PreisanfrageLieferantDto();
        dto.setId(pal.getId());
        if (pal.getLieferant() != null) {
            dto.setLieferantId(pal.getLieferant().getId());
            dto.setLieferantenname(pal.getLieferant().getLieferantenname());
        }
        dto.setToken(pal.getToken());
        dto.setVersendetAn(pal.getVersendetAn());
        dto.setVersendetAm(pal.getVersendetAm());
        dto.setAntwortErhaltenAm(pal.getAntwortErhaltenAm());
        dto.setAntwortEmailId(pal.getAntwortEmail() != null ? pal.getAntwortEmail().getId() : null);
        dto.setStatus(pal.getStatus() != null ? pal.getStatus().name() : null);
        return dto;
    }

    public PreisanfragePositionDto toPositionDto(PreisanfragePosition pos) {
        if (pos == null) {
            return null;
        }
        PreisanfragePositionDto dto = new PreisanfragePositionDto();
        dto.setId(pos.getId());
        dto.setReihenfolge(pos.getReihenfolge());
        dto.setArtikelInProjektId(
                pos.getArtikelInProjekt() != null ? pos.getArtikelInProjekt().getId() : null);
        dto.setArtikelId(pos.getArtikel() != null ? pos.getArtikel().getId() : null);
        dto.setExterneArtikelnummer(pos.getExterneArtikelnummer());
        dto.setProduktname(pos.getProduktname());
        dto.setProdukttext(pos.getProdukttext());
        dto.setWerkstoffName(pos.getWerkstoffName());
        dto.setMenge(pos.getMenge());
        dto.setEinheit(pos.getEinheit());
        dto.setKommentar(pos.getKommentar());
        if (pos.getSchnittbild() != null) {
            dto.setSchnittbildId(pos.getSchnittbild().getId());
            dto.setSchnittbildForm(pos.getSchnittbild().getForm());
            dto.setSchnittbildBildUrl(pos.getSchnittbild().getBildUrlSchnittbild());
        }
        dto.setAnschnittWinkelLinks(pos.getAnschnittWinkelLinks());
        dto.setAnschnittWinkelRechts(pos.getAnschnittWinkelRechts());
        return dto;
    }
}
