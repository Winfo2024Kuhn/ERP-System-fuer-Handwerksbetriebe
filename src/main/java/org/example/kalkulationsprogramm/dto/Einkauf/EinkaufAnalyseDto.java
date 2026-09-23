package org.example.kalkulationsprogramm.dto.Einkauf;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class EinkaufAnalyseDto {
    private EinkaufAnalyseDto() {}
    public record JobDto(Long id, Long emailId, Long angebotId, String status, Instant erstelltAm,
            Instant beendetAm, String hinweis, List<Long> weitereJobIds) {
        public JobDto { weitereJobIds = weitereJobIds == null ? List.of() : List.copyOf(weitereJobIds); }
    }
    public record FeldVorschlag(String feldpfad, JsonNode wert, Quelle quelle, BigDecimal confidence, String hinweis) {
        public FeldVorschlag { wert = wert == null ? null : wert.deepCopy(); }
        @Override public JsonNode wert() { return wert == null ? null : wert.deepCopy(); }
    }
    public record Quelle(Long emailId, Long dateiId, Integer seite, String zitat, Integer textStart, Integer textEnd) {}
    public record Uebernahme(long erwarteteAngebotVersion, List<String> akzeptierteFeldpfade,
            Map<String, JsonNode> korrekturen) {
        public Uebernahme {
            akzeptierteFeldpfade = akzeptierteFeldpfade == null ? List.of() : List.copyOf(akzeptierteFeldpfade);
            korrekturen = korrekturen == null ? Map.of() : Map.copyOf(korrekturen);
        }
    }
    public record EmpfehlungDto(Long angebotVersionId, BigDecimal nettoGesamt, List<String> quellen, String text) {
        public EmpfehlungDto { quellen = quellen == null ? List.of() : List.copyOf(quellen); }
    }
}
