package org.example.kalkulationsprogramm.dto.Schnittbilder;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload fuer Anlegen/Aktualisieren einer Schnitt-Achse.
 */
@Getter
@Setter
public class SchnittAchseUpsertDto {
    private String bildUrl;
    private Integer kategorieId;
}
