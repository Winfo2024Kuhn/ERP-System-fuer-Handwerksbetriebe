package org.example.kalkulationsprogramm.dto.Bestellung;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Request fuer den Lager-Abgleich: Chef harkt eine Bedarfszeile als
 * „aus Lager entnommen" ab.
 *
 * <p>{@code preisProStueck} ist optional — wenn {@code null}, wird der
 * gewichtete Durchschnittspreis des Artikels aus dem Stamm verwendet.</p>
 */
@Getter
@Setter
public class LagerAbgleichRequestDto {
    private BigDecimal preisProStueck;
    private String chefName;
}
