package org.example.kalkulationsprogramm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class LieferantDokumentDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long lieferantId;
        private String lieferantName;
        private LieferantDokumentTyp typ;
        private String originalDateiname;
        private String gespeicherterDateiname;
        private LocalDateTime uploadDatum;
        private String uploadedByName;
        private String url; // PDF-Download URL für Preview
        private List<ProjektAnteilRef> projektAnteile;
        private List<VerknuepftesDoc> verknuepfteDokumente;
        private GeschaeftsdatenRef geschaeftsdaten;
        private Boolean wareGeprueft;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjektAnteilRef {
        private Long id;
        private Long projektId;
        private String projektName;
        private String auftragsnummer;
        private Integer prozent;
        private BigDecimal berechneterBetrag;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeschaeftsdatenRef {
        private String dokumentNummer;
        private LocalDate dokumentDatum;
        private BigDecimal betragNetto;
        private BigDecimal betragBrutto;
        private LocalDate liefertermin;
        private String bestellnummer;
        private String referenzNummer;
        private Double aiConfidence;
        // Zahlungsstatus
        private LocalDate zahlungsziel;
        private Boolean bezahlt;
        private LocalDate bezahltAm;
        private Boolean bereitsGezahlt;
        private String zahlungsart;
        // Skonto-Konditionen
        private Integer skontoTage;
        private BigDecimal skontoProzent;
        private Integer nettoTage;
        private BigDecimal tatsaechlichGezahlt;
        private Boolean mitSkonto;
        // Neu: Flag für manuelle Prüfung
        private Boolean manuellePruefungErforderlich;
        private String datenquelle; // ZUGFERD, XML, AI, AI_FAILED, AI_ERROR
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerknuepftesDoc {
        private Long id;
        private LieferantDokumentTyp typ;
        private String originalDateiname;
        private LocalDateTime uploadDatum;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadRequest {
        private LieferantDokumentTyp typ;
        private Set<Long> verknuepfteIds; // Optional: IDs vorhandener Dokumente zum Verknüpfen
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjektZuordnungRequest {
        private List<ProjektAnteil> anteile;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjektAnteil {
        private Long projektId;
        private Integer prozent;
        private String beschreibung;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BerechtigungenResponse {
        private List<LieferantDokumentTyp> sichtbareTypen;
        private List<LieferantDokumentTyp> scanbarTypen;
    }

    /**
     * Response für die KI-Analyse eines Dokuments (ohne Speicherung).
     * Wird für den manuellen Dokument-Importer verwendet.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzeResponse {
        private LieferantDokumentTyp dokumentTyp;
        private String dokumentNummer;
        private LocalDate dokumentDatum;
        private BigDecimal betragNetto;
        private BigDecimal betragBrutto;
        private BigDecimal mwstSatz;
        private LocalDate liefertermin;
        private LocalDate zahlungsziel;
        private String bestellnummer;
        private String referenzNummer;
        private Integer skontoTage;
        private BigDecimal skontoProzent;
        private Integer nettoTage;
        private Boolean bereitsGezahlt; // Wichtig für Amazon/Lastschrift
        private String zahlungsart;
        private Double aiConfidence;
        private String analyseQuelle; // "ZUGFeRD", "XML", "KI"

        // Neu: Lieferanten-Erkennung für manuellen Import
        private String lieferantName;
        private String lieferantStrasse;
        private String lieferantPlz;
        private String lieferantOrt;

        // Werkstoffzeugnis-spezifische Felder (EN 10204)
        private String schmelzNummer;
        private String materialGuete;
        private String normTyp;
        private String lieferscheinNummer;
    }

    /**
     * Request für den Import eines Dokuments mit bearbeiteten Metadaten.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRequest {
        private LieferantDokumentTyp dokumentTyp;
        private String dokumentNummer;
        private LocalDate dokumentDatum;
        private BigDecimal betragNetto;
        private BigDecimal betragBrutto;
        private BigDecimal mwstSatz;
        private LocalDate liefertermin;
        private LocalDate zahlungsziel;
        private String bestellnummer;
        private String referenzNummer;
        private Integer skontoTage;
        private BigDecimal skontoProzent;
        private Integer nettoTage;
        private Boolean bereitsGezahlt; // Wichtig für Amazon/Lastschrift
        private String zahlungsart;

        // Neu: Ausgewählter oder erkannter Lieferant
        private Long lieferantId; // Optional, wenn manuell zugewiesen
        private String lieferantName; // Fallback für "One-Time" oder Prüfung
    }

    /**
     * Response für Multi-Invoice PDFs (z.B. Amazon mit mehreren Rechnungen).
     * Enthält die extrahierten Daten plus das aufgeteilte PDF als Base64.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiInvoiceAnalyzeResponse {
        private String pageRange; // z.B. "1-2" oder "3"
        private AnalyzeResponse analyzeResponse;
        private String splitPdfBase64; // Aufgeteiltes PDF als Base64 für Frontend-Preview
    }
}
