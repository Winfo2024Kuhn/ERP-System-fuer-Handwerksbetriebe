package org.example.kalkulationsprogramm.dto.miete;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle;

import java.math.BigDecimal;

@Data
public class AnnualAccountingPartyDto {
    private Long mietparteiId;
    private String mietparteiName;
    private MietparteiRolle rolle;
    private BigDecimal summe;
    private BigDecimal vorjahr;
    private BigDecimal differenz;
    private BigDecimal monatlicherVorschuss;
    private BigDecimal jahresVorauszahlung;
    private BigDecimal saldo;
}
