package org.example.kalkulationsprogramm.dto.Telefon;

/** Kurzform eines Kunden, Lieferanten oder Steuerberaters für Anrufliste und Anruf-Fenster. */
public record KontaktKurzDto(String typ, Long id, String name, String nummer, String ort) {

    public static final String KUNDE = "KUNDE";
    public static final String LIEFERANT = "LIEFERANT";
    public static final String STEUERBERATER = "STEUERBERATER";
}
