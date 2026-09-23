package org.example.kalkulationsprogramm.dto.Einkauf;

import java.time.Instant;

public final class EinkaufDateiDto {
    private EinkaufDateiDto() {}
    public record AnlageDto(Long id, Long dateiId, Long bedarfId, String revision, String dateiname, String mimeTyp,
            long byteAnzahl, String sha256, boolean freigegeben, boolean versendet, Instant hochgeladenAm) {}
}
