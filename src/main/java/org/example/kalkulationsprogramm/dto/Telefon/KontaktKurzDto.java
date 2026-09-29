package org.example.kalkulationsprogramm.dto.Telefon;

/** Kurzform eines Kunden oder Lieferanten für Anrufliste und Anruf-Fenster. */
public record KontaktKurzDto(String typ, Long id, String name, String nummer, String ort) {

    public static final String KUNDE = "KUNDE";
    public static final String LIEFERANT = "LIEFERANT";
}
