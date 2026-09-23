package org.example.kalkulationsprogramm.dto.Einkauf;

import java.time.Instant;

public final class EinkaufDateiDto {
    private EinkaufDateiDto() {}
    public record PdfSnapshotDto(Long dateiId, String sha256, long byteAnzahl) {}
    public record AnlageDto(Long id, Long dateiId, Long bedarfId, String revision, String dateiname, String mimeTyp,
            long byteAnzahl, String sha256, boolean freigegeben, boolean versendet, Instant hochgeladenAm) {}
}
