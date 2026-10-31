package org.example.kalkulationsprogramm.dto.Postfach;

import java.util.List;

/**
 * Ordner eines Postfachs auf dem Mail-Server – für die Auswahl des Spam-Ordners.
 *
 * @param erkannterSpamOrdner Ordner, den das ERP automatisch nehmen würde; {@code null} = keiner erkannt
 */
public record PostfachServerOrdnerDto(List<Ordner> ordner, String erkannterSpamOrdner) {

    /** @param spamOrdner {@code true} beim automatisch erkannten Spam-Ordner */
    public record Ordner(String name, boolean spamOrdner) {
    }
}
