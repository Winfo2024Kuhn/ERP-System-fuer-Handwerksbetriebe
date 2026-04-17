package org.example.kalkulationsprogramm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * DTOs für die Review-Queue "Vorgeschlagene neue Materialien".
 * Enthält Liste, Detail, Update und Approve-Payload in einer Datei
 * (zusammenhängender View-Model-Satz).
 */
public class ArtikelVorschlagDto {

    private ArtikelVorschlagDto() {}

    @Getter
    @Setter
    public static class Response {
        private Long id;
        private String status;
        private String typ;
        private LocalDateTime erstelltAm;
        private LocalDateTime bearbeitetAm;

        private Long lieferantId;
        private String lieferantName;
        private Long quelleDokumentId;
        private String quelleDokumentBezeichnung;

        private String externeArtikelnummer;
        private String produktname;
        private String produktlinie;
        private String produkttext;

        private Integer vorgeschlageneKategorieId;
        private String vorgeschlageneKategoriePfad;
        private Long vorgeschlagenerWerkstoffId;
        private String vorgeschlagenerWerkstoffName;

        private BigDecimal masse;
        private Integer hoehe;
        private Integer breite;

        private BigDecimal einzelpreis;
        private String preiseinheit;

        private BigDecimal kiKonfidenz;
        private String kiBegruendung;

        private Long konfliktArtikelId;
        private String konfliktArtikelName;
        private Long trefferArtikelId;
        private String trefferArtikelName;
    }

    @Getter
    @Setter
    public static class UpdateRequest {
        private String produktname;
        private String produktlinie;
        private String produkttext;
        private String externeArtikelnummer;
        private Integer kategorieId;
        private Long werkstoffId;
        private BigDecimal masse;
        private Integer hoehe;
        private Integer breite;
        private BigDecimal einzelpreis;
        private String preiseinheit;
    }

    @Getter
    @Setter
    public static class CountResponse {
        private long pending;

        public CountResponse(long pending) { this.pending = pending; }
    }
}
