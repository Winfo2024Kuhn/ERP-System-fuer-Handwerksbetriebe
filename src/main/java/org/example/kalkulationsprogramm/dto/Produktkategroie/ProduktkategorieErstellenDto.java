package org.example.kalkulationsprogramm.dto.Produktkategroie;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;

@Getter
@Setter
public class ProduktkategorieErstellenDto {
    private String bezeichnung;
    private Verrechnungseinheit verrechnungseinheit;
    private Long parentId;
    private String beschreibung;
}
