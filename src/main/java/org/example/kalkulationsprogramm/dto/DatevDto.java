package org.example.kalkulationsprogramm.dto;

import java.math.BigDecimal;
import java.util.List;

public final class DatevDto {
 private DatevDto() {}
 public record Referenz(Long mitarbeiterId, int jahr, int monat) {}
 public record Stand(Long mitarbeiterId, int jahr, int monat, Long version) {}
 public record Zuordnung(String kategorie, boolean ausgeschlossen, String lohnart) {}
 public record Personalnummer(Long mitarbeiterId, String personalnummer) {}
 public record Konfiguration(Long version, String ziel, String beraterNr, String mandantenNr,
     List<Zuordnung> zuordnungen, List<Personalnummer> personalnummern) {}
 public record ExportRequest(List<Stand> auswahl, Long konfigurationVersion) {}
 public record Hinweis(Referenz referenz, String kategorie, BigDecimal stunden, String meldung) {}
 public record Vorpruefung(boolean gueltig, List<Hinweis> fehler, List<Hinweis> ausschluesse,
     List<Stand> auswahl, Long konfigurationVersion) {}
 public record Datei(String dateiname, String contentType, byte[] inhalt) {}
}
