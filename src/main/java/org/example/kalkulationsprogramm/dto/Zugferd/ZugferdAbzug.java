package org.example.kalkulationsprogramm.dto.Zugferd;

import java.math.BigDecimal;

/**
 * Abzug auf die ganze Rechnung (EN 16931: Nachlass auf Belegebene, BG-20) – etwa der
 * Pauschalrabatt oder eine bereits gestellte Abschlagsrechnung auf der Schlussrechnung.
 * Ein negativer Betrag wird zum Zuschlag (BG-21).
 *
 * @param grund       Text auf der E-Rechnung (Pflicht nach BR-33 bzw. BR-38); leer = „Nachlass“ bzw. „Zuschlag“
 * @param betragNetto Nettobetrag des Abzugs
 * @param mwstSatz    Steuersatz, auf den der Abzug wirkt; {@code null} = Satz der Rechnung
 */
public record ZugferdAbzug(String grund, BigDecimal betragNetto, BigDecimal mwstSatz) {
}
