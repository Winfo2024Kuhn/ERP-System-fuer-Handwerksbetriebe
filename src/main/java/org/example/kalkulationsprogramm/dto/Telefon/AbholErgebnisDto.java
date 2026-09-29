package org.example.kalkulationsprogramm.dto.Telefon;

/** Ergebnis einer Abholung bzw. eines Nachhol-Laufs. */
public record AbholErgebnisDto(boolean erfolgreich,
                               String meldung,
                               int neueAnrufe,
                               int neueSprachnachrichten,
                               int nachtraeglichZugeordnet) {
}
