package org.example.kalkulationsprogramm.dto.Schnittbilder;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload fuer Anlegen/Aktualisieren eines Schnittbilds (Admin).
 */
@Getter
@Setter
public class SchnittbildUpsertDto {
    private String bildUrlSchnittbild;
    private Long schnittAchseId;
}
