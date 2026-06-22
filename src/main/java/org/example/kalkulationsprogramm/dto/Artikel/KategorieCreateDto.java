package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.LieferantRolle;

import java.util.Set;

@Getter
@Setter
public class KategorieCreateDto {
    private String bezeichnung;
    private Integer parentId;
    private Set<LieferantRolle> typischeRollen;
}
