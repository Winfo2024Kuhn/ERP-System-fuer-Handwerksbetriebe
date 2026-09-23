package org.example.kalkulationsprogramm.dto.Einkauf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.example.kalkulationsprogramm.domain.PreisScope;

public final class EinkaufPreisUebernahmeDto {
    private EinkaufPreisUebernahmeDto() {}
    public record PreisUebernahme(String scope, Long projektId, BigDecimal abMenge, BigDecimal bisMenge,
            String begruendung, UUID idempotenzKey) {}
    public record Preisvorschlag(Long artikelId, Long lieferantId, BigDecimal preis, String waehrung,
            String einheit, LocalDate datum, LocalDate gueltigBis, PreisScope scope, String hinweis) {}
}
