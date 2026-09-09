package org.example.kalkulationsprogramm.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public final class MonatsabschlussUebersichtDto {
    private MonatsabschlussUebersichtDto() {}
    public record Referenz(Long mitarbeiterId, int jahr, int monat) {}
    public record Stand(Long mitarbeiterId, int jahr, int monat, Long version) {}
    public record Kennzahlen(BigDecimal istStunden, BigDecimal sollStunden, BigDecimal abwesenheitsStunden, BigDecimal feiertagsStunden, BigDecimal korrekturStunden, BigDecimal gesamtIst, BigDecimal differenz) {}
    public record Zeile(Referenz referenz, String mitarbeiterName, List<Long> abteilungIds, boolean festgeschrieben, Long version, LocalDateTime festgeschriebenAm, Kennzahlen kennzahlen) {}
    public record Filter(int jahr, int monat, Long mitarbeiterId, Long abteilungId, String status, int page, int size) {}
    public record Uebersicht(List<Zeile> items, long totalElements, int page, int size, Kennzahlen summen, List<Stand> auswahl) {}
    public record Vergleichsmonat(int jahr, int monat, Kennzahlen summen, int offen, int abgeschlossen) {}
    public record SammelRequest(List<Referenz> auswahl) {}
    public record Einzelergebnis(Referenz referenz, String status, String meldung) {}
    public record SammelResponse(List<Einzelergebnis> ergebnisse) {}
}
