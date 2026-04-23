package org.example.kalkulationsprogramm.dto.Schnittbilder;

import lombok.Getter;
import lombok.Setter;

/**
 * Achse eines Profils, an der ein Sonderzuschnitt erfolgt.
 * Gehoert zu genau einer Kategorie und bundelt die moeglichen
 * Schnittbilder dieser Achse.
 */
@Getter
@Setter
public class SchnittAchseDto {
    private Long id;
    private String bildUrl;
    private Integer kategorieId;
}
