package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class KundeProjektKurzDto {
    private Long id;
    private String bauvorhaben;
    private String auftragsnummer;
    private LocalDate anlegedatum;
    private LocalDate abschlussdatum;
    private boolean bezahlt;
    private BigDecimal bruttoPreis;
}

