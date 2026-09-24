package org.example.kalkulationsprogramm.dto.Einkauf;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;

public final class EinkaufBelegDto {
    private EinkaufBelegDto() {}
    public record Beleg(Long lieferantDokumentId, LieferantDokumentTyp typ, String dateiname, boolean verfuegbar) {}
    public record Datei(Long dateiId, Long lieferantDokumentId, LieferantDokumentTyp typ, String dateiname, boolean verfuegbar) {}
}
