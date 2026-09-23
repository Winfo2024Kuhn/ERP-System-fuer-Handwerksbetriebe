package org.example.kalkulationsprogramm.dto.Einkauf;

import java.util.List;
import java.util.Map;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;

public final class EinkaufVorlagenDto {
    private EinkaufVorlagenDto() { }

    public record Gerendert(Long templateId, long version, String subject, String htmlBody, String hash) { }

    public record VorlagenKontext(String typ, Map<String, String> skalare,
                                  List<PositionSnapshot> positionen, String rueckmeldecode) { }

    public record Platzhalter(String token, String label, boolean pflicht, boolean imBetreffErlaubt) { }
}
