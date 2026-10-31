package org.example.kalkulationsprogramm.service.ids;

/**
 * Was der Shop an die Rücksprung-Adresse geschickt hat.
 *
 * @param warenkorbBytes Feld {@code warenkorb} als Rohbytes (multipart) – das XML nennt seine Kodierung selbst
 * @param warenkorbText  Feld {@code warenkorb} als Text (url-kodiertes Formular), wenn keine Rohbytes vorliegen
 */
public record IdsRueckgabe(byte[] warenkorbBytes, String warenkorbText) {

    /** Für die Fehlersuche: Rohbytes als UTF-8 bzw. der Text (Kodierungsfehler werden ersetzt). */
    public String alsText() {
        String text = warenkorbBytes != null && warenkorbBytes.length > 0
                ? new String(warenkorbBytes, java.nio.charset.StandardCharsets.UTF_8)
                : warenkorbText;
        if (text == null) {
            return null;
        }
        // PostgreSQL nimmt kein NUL-Zeichen in TEXT; die Rohdaten sind nur zur Fehlersuche, also gekürzt.
        String ohneNul = text.replace("\u0000", "");
        return ohneNul.length() > MAX_ROHDATEN ? ohneNul.substring(0, MAX_ROHDATEN) : ohneNul;
    }

    /** Höchstens so viele Zeichen Rohdaten speichern (der Rücksprung ist ohnehin auf 1 MB begrenzt). */
    static final int MAX_ROHDATEN = 1_000_000;

    public boolean istLeer() {
        return (warenkorbBytes == null || warenkorbBytes.length == 0)
                && (warenkorbText == null || warenkorbText.isBlank());
    }
}
