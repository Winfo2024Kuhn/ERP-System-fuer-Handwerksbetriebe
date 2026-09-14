package org.example.kalkulationsprogramm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public final class SteuerberaterPaketDto {
    private SteuerberaterPaketDto() { }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Vorpruefung {
        private int anzahlBelege;
        private List<OffenerPunkt> offenePunkte;
        private boolean beraternummerFehlt;
        private boolean mandantennummerFehlt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OffenerPunkt {
        private Long belegId;
        private LocalDate belegDatum;
        private String bezeichnung;
        private String wasFehlt;
    }
}
