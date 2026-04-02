package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LeistungMapper {

    private final ProduktkategorieRepository produktkategorieRepository;
    private final ProduktkategorieMapper produktkategorieMapper;

    public LeistungDto toDto(Leistung leistung) {
        if (leistung == null)
            return null;
        LeistungDto dto = new LeistungDto();
        dto.setId(leistung.getId());
        dto.setName(leistung.getBezeichnung());
        dto.setDescription(leistung.getBeschreibung());
        dto.setPrice(leistung.getPreis());
        dto.setUnit(leistung.getEinheit());
        if (leistung.getKategorie() != null) {
            dto.setFolderId(leistung.getKategorie().getId());
            dto.setKategoriePfad(produktkategorieMapper.toProduktkategorieResponseDto(leistung.getKategorie()).getPfad());
        }
        return dto;
    }

    public Leistung toEntity(LeistungCreateDto dto) {
        if (dto == null)
            return null;
        Leistung leistung = new Leistung();
        updateEntity(leistung, dto);
        return leistung;
    }

    public void updateEntity(Leistung leistung, LeistungCreateDto dto) {
        leistung.setBezeichnung(dto.getName());
        leistung.setBeschreibung(dto.getDescription());
        leistung.setPreis(dto.getPrice());

        if (dto.getFolderId() != null) {
            Produktkategorie kat = produktkategorieRepository.findById(dto.getFolderId())
                    .orElse(null);
            leistung.setKategorie(kat);
            // Verrechnungseinheit automatisch von Produktkategorie uebernehmen, wenn nicht explizit gesetzt
            if (dto.getUnit() != null) {
                leistung.setEinheit(dto.getUnit());
            } else if (kat != null) {
                leistung.setEinheit(kat.getVerrechnungseinheit());
            }
        } else {
            leistung.setKategorie(null);
            leistung.setEinheit(dto.getUnit());
        }
    }
}
