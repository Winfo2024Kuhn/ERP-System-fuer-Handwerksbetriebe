package org.example.kalkulationsprogramm.dto.Zugferd;

import java.time.LocalDate;

/**
 * Verweis auf eine vorausgegangene Rechnung (EN 16931: BG-3) – etwa die Abschlagsrechnungen,
 * die eine Schlussrechnung abzieht.
 *
 * @param nummer Rechnungsnummer (BT-25)
 * @param datum  Rechnungsdatum (BT-26), darf {@code null} sein
 */
public record ZugferdBelegverweis(String nummer, LocalDate datum) {
}
