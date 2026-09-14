package org.example.kalkulationsprogramm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

public class KassenbuchungDto {
    public enum Art {
        GELD_EINGENOMMEN, GELD_AUSGEGEBEN, VON_BANK_GEHOLT,
        ZUR_BANK_GEBRACHT, EIGENES_GELD_EINGELEGT, GELD_PRIVAT_ENTNOMMEN
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        private String art;
        private LocalDate belegDatum;
        private BigDecimal betragBrutto;
        private BigDecimal mwstSatz;
        private String gegenpartei;
        private String beschreibung;
        private Long sachkontoId;
        private Long kostenstelleId;
        private Long ausgangsrechnungId;
        private Boolean keinBelegVorhanden;
        private String grundOhneBeleg;
        private String notiz;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OffeneRechnung {
        private Long id;
        private String dokumentNummer;
        private LocalDate datum;
        private BigDecimal bruttoBetrag;
        private String kundeName;
    }
}
