package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ProduktkategorieMapper {
    public ProduktkategorieResponseDto toProduktkategorieResponseDto(Produktkategorie produktkategorie) {
        if (produktkategorie == null) {
            return null;
        }
        boolean isLeaf = produktkategorie.getUnterkategorien() == null || produktkategorie.getUnterkategorien().isEmpty();
        return toProduktkategorieResponseDtoWithLeaf(produktkategorie, isLeaf);
    }

    public ProduktkategorieResponseDto toProduktkategorieResponseDtoWithLeaf(Produktkategorie produktkategorie, boolean isLeaf) {
        if (produktkategorie == null) {
            return null;
        }
        ProduktkategorieResponseDto dto = new ProduktkategorieResponseDto();
        dto.setId(produktkategorie.getId());
        dto.setBezeichnung(produktkategorie.getBezeichnung());
        dto.setVerrechnungseinheit(produktkategorie.getVerrechnungseinheit());
        dto.setBeschreibung(produktkategorie.getBeschreibung());

        String bildUrl = produktkategorie.getBildUrl();
        if (bildUrl != null && !bildUrl.isBlank() && !bildUrl.startsWith("/")) {
            bildUrl = "/api/images/" + bildUrl;
        }
        dto.setBildUrl(bildUrl);

        dto.setLeaf(isLeaf);
        dto.setPfad(bauePfad(produktkategorie));
        dto.setParentId(produktkategorie.getUebergeordneteKategorie() != null ? produktkategorie.getUebergeordneteKategorie().getId() : null);
        return dto;
    }

    private String bauePfad(Produktkategorie kategorie) {
        List<String> namen = new ArrayList<>();
        Produktkategorie current = kategorie;
        while (current != null) {
            namen.add(current.getBezeichnung());
            current = current.getUebergeordneteKategorie();
        }
        Collections.reverse(namen);
        return String.join(" > ", namen);
    }
}
