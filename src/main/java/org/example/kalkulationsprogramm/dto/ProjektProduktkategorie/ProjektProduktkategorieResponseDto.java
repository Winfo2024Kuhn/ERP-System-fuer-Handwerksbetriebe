package org.example.kalkulationsprogramm.dto.ProjektProduktkategorie;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.dto.Produktkategroie.ProduktkategorieResponseDto;
import java.math.BigDecimal;

@Getter
@Setter
public class ProjektProduktkategorieResponseDto {
    private Long id;
    private ProduktkategorieResponseDto produktkategorie;
    private BigDecimal menge;
}
