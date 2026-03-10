package org.example.kalkulationsprogramm.dto.Produktkategroie;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

@Getter
@Setter
public class ProduktkategorieResponseDto {
    private Long id;
    private String bezeichnung;
    private String bildUrl;
    private String beschreibung;
    private Verrechnungseinheit verrechnungseinheit;
    private String pfad;
    private boolean isLeaf;
    private Long projektAnzahl;

}
