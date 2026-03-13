package org.example.kalkulationsprogramm.dto.Kunde;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class KundeStatistikDto {
    private long projektAnzahl;
    private long anfrageAnzahl;
    private long emailAdresseAnzahl;
    private LocalDate letzteAktivitaet;
    private BigDecimal gesamtUmsatz;
    private BigDecimal gesamtGewinn;
}
