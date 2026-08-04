package org.example.kalkulationsprogramm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs rund um den Monatsabschluss des Kassenbuchs.
 */
public class KassenbuchAbschlussDto {

    private KassenbuchAbschlussDto() {
    }

    /**
     * Was ein Abschluss bewirken wuerde -- und was ihn blockiert. Das
     * Frontend zeigt das vor dem Klick, damit niemand erst in eine
     * Fehlermeldung laeuft.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vorschau {
        private int jahr;
        private int monat;
        /** null beim allerersten Abschluss -- der zieht alles Bisherige mit. */
        private LocalDate zeitraumVon;
        private LocalDate zeitraumBis;
        private boolean bereitsAbgeschlossen;
        private int anzahlFestzuschreiben;
        private int anzahlUngeprueft;
        private int anzahlOhneDatum;
        private BigDecimal anfangsbestand;
        private BigDecimal endbestand;
        /** Klartext-Gruende, warum es (noch) nicht geht. Leer = alles bereit. */
        private List<String> hindernisse;
        private boolean abschlussMoeglich;
    }

    /** Ein abgeschlossener Monat, so wie er in der Liste erscheint. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ergebnis {
        private Long id;
        private int jahr;
        private int monat;
        private LocalDateTime abgeschlossenAm;
        private String abgeschlossenVon;
        private BigDecimal anfangsbestand;
        private BigDecimal endbestand;
        private BigDecimal summeEinnahmen;
        private BigDecimal summeAusgaben;
        private int anzahlBelege;
        private Long ersteLaufendeNummer;
        private Long letzteLaufendeNummer;
        /** Position und Pruefsumme in der Protokollkette zum Zeitpunkt des Abschlusses. */
        private Long chainIndex;
        private String entryHash;
        private String bemerkung;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AbschlussRequest {
        private Integer jahr;
        private Integer monat;
        private String bemerkung;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StornoRequest {
        private String grund;
    }

    /**
     * Ergebnis der Protokollpruefung. {@code intakt=false} heisst: am
     * Kassenbuch wurde nachweislich nachtraeglich etwas veraendert.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PruefErgebnis {
        private boolean intakt;
        private int geprueftEintraege;
        private Long letzteKettenposition;
        private String letztePruefsumme;
        private List<String> fehler;
        /** Ein Satz, den man einem Steuerprüfer zeigen kann. */
        private String zusammenfassung;
    }
}
