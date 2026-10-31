package org.example.kalkulationsprogramm.service.ki;

/**
 * Antwort einer KI.
 *
 * @param text          die Antwort, nie {@code null}
 * @param abgeschnitten true, wenn die Antwort am Ausgabelimit endete
 * @param anbieter      wer geantwortet hat
 * @param modell        mit welchem Modell
 */
public record KiErgebnis(String text, boolean abgeschnitten, KiAnbieterTyp anbieter, String modell) {

    public KiErgebnis {
        text = text == null ? "" : text;
    }
}
