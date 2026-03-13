package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class KundeAnfrageKurzDto {
    private Long id;
    private String bauvorhaben;
    private String anfragesnummer;
    private LocalDate anlegedatum;
    private BigDecimal betrag;
}

