package org.example.kalkulationsprogramm.dto.Einkauf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class EinkaufVergleichDto {
    private EinkaufVergleichDto() {}
    public record Vergleich(Long anfrageId, LocalDate stichtag, List<AngebotSumme> angebote, Long bestesAngebotVersionId) {
        public Vergleich { angebote = List.copyOf(angebote); }
    }
    public record AngebotSumme(Long angebotVersionId, BigDecimal nettoGesamt, boolean vollstaendig,
            boolean technischGeeignet, boolean gueltig, List<String> hindernisse, List<Rechenschritt> rechnung) {
        public AngebotSumme { hindernisse = List.copyOf(hindernisse); rechnung = List.copyOf(rechnung); }
    }
    public record Rechenschritt(String key, String formel, BigDecimal basis, BigDecimal ergebnis, String quellenbezug) {}
}
