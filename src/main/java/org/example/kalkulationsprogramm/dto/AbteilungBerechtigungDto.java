package org.example.kalkulationsprogramm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;

import java.util.List;

public class AbteilungBerechtigungDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long abteilungId;
        private String abteilungName;
        private List<TypBerechtigung> berechtigungen;
        private Boolean darfRechnungenGenehmigen;
        private Boolean darfRechnungenSehen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypBerechtigung {
        private LieferantDokumentTyp typ;
        private Boolean darfSehen;
        private Boolean darfScannen;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private List<TypBerechtigung> berechtigungen;
        private Boolean darfRechnungenGenehmigen;
        private Boolean darfRechnungenSehen;
    }
}
