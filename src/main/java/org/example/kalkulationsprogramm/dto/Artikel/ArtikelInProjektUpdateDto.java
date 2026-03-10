package org.example.kalkulationsprogramm.dto.Artikel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArtikelInProjektUpdateDto {
    private String schnittForm;
    private String anschnittWinkelLinks;
    private String anschnittWinkelRechts;
    private String kommentar;
}

