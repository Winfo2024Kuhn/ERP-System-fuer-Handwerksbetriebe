package org.example.kalkulationsprogramm.dto.Schnittbilder;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchnittbildResponseDto {
    private Long id;
    private String bildUrlSchnittbild;
    private Long schnittAchseId;
    /** Bild der zugehoerigen Achse — praktisch fuer den zweistufigen Picker im Frontend. */
    private String schnittAchseBildUrl;
    /** Kategorie ueber die Achse aufgeloest, fuer Filter/Anzeigen im Frontend. */
    private Integer kategorieId;
}
