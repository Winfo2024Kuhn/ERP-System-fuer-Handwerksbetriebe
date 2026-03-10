package org.example.kalkulationsprogramm.dto.ProjektProduktkategorie;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProjektProduktkategorieErfassenDto {
    private Long id;
    private Long produktkategorieID;
    private BigDecimal menge;
}
