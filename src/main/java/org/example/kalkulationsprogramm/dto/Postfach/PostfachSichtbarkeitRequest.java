package org.example.kalkulationsprogramm.dto.Postfach;

import java.util.List;

/**
 * „Wer darf das Postfach sehen?“ (Einstellungen → Abteilungen & Rechte). Alle Felder sind Pflicht;
 * fehlende Listen gelten als leer. Die Listen werden auch bei {@code sichtbarFuerAlle = true}
 * gespeichert (wirken dann nicht), damit ein späteres Umschalten die Auswahl behält.
 */
public record PostfachSichtbarkeitRequest(
        Boolean sichtbarFuerAlle,
        List<Long> abteilungIds,
        List<Long> benutzerIds) {
}
