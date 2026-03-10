package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KategorieResponseDto {
    private Integer id;
    private String bezeichnung;
    private boolean leaf;
}

