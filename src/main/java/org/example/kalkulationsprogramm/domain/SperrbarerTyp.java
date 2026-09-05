package org.example.kalkulationsprogramm.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Datensatz-Typen, die ueber ein Soft-Lock ({@code DatensatzLock}) vor
 * gleichzeitiger Bearbeitung durch mehrere Benutzer geschuetzt werden koennen.
 *
 * <p>Ersetzt die frueher als {@code Set<String>} gefuehrte Liste erlaubter
 * Typen (vgl. {@code DokumentLockService.ERLAUBTE_TYPEN}) durch einen
 * typsicheren Wert. Ein neuer sperrbarer Editor-Typ kostet genau einen
 * Enum-Wert plus einen Frontend-Aufruf.
 */
public enum SperrbarerTyp {

    AUSGANG,
    EINGANG;

    /**
     * Wandelt rohen Text (z.B. einen URL-Pfadparameter) in einen bekannten
     * {@link SperrbarerTyp} um. Trimmt Whitespace und ist unabhaengig von
     * Gross-/Kleinschreibung.
     *
     * @param roh der rohe Text, darf {@code null}, leer oder beliebig lang sein
     * @return der passende Typ, oder {@link Optional#empty()} bei {@code null},
     *         leerem/nur-Whitespace-Text oder unbekanntem Wert. Wirft niemals
     *         eine Ausnahme, auch nicht bei extrem langen oder mit Sonderzeichen
     *         durchsetzten Eingaben.
     */
    public static Optional<SperrbarerTyp> ausText(String roh) {
        if (roh == null) {
            return Optional.empty();
        }
        String getrimmt = roh.trim();
        if (getrimmt.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(getrimmt.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unbekannterTyp) {
            return Optional.empty();
        }
    }
}
