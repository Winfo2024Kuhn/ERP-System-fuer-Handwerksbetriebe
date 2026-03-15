package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KategorieCreateDto {
    private String bezeichnung;
    private Integer parentId;
}
