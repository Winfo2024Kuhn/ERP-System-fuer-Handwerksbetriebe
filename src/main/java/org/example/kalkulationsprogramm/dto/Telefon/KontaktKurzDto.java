package org.example.kalkulationsprogramm.dto.Telefon;

/**
 * Kurzform eines Kunden, Lieferanten oder Steuerberaters für Anrufliste und Anruf-Fenster.
 *
 * @param ansprechpartner nur bei Steuerberatern: der Ansprechpartner, bei dem genau diese
 *                        Rufnummer hinterlegt ist – sonst null
 */
public record KontaktKurzDto(String typ, Long id, String name, String nummer, String ort, String ansprechpartner) {

    public static final String KUNDE = "KUNDE";
    public static final String LIEFERANT = "LIEFERANT";
    public static final String STEUERBERATER = "STEUERBERATER";

    public KontaktKurzDto(String typ, Long id, String name, String nummer, String ort) {
        this(typ, id, name, nummer, ort, null);
    }

    /** Derselbe Kontakt, ergänzt um den Ansprechpartner. */
    public KontaktKurzDto mitAnsprechpartner(String person) {
        return new KontaktKurzDto(typ, id, name, nummer, ort, person);
    }

    /** Gleicher Kontakt (Art und Id), unabhängig vom Ansprechpartner. */
    public boolean gleicherKontakt(KontaktKurzDto anderer) {
        return anderer != null && typ.equals(anderer.typ) && id.equals(anderer.id);
    }
}
