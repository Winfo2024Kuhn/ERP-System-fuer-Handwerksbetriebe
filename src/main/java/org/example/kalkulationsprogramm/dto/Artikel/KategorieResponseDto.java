package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;
import org.example.kalkulationsprogramm.domain.LieferantRolle;

import java.util.Set;

@Getter
@Setter
public class KategorieResponseDto {
    private Integer id;
    private String bezeichnung;
    private boolean leaf;

    /** Eigene, an dieser Kategorie hinterlegte Rollen (leer = erbt von Oberkategorie). */
    private Set<LieferantRolle> typischeRollen;
}

