package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArtikelInProjektUpdateDto {
    private Long schnittbildId;
    private Double anschnittWinkelLinks;
    private Double anschnittWinkelRechts;
    private String kommentar;
}
