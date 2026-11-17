import type { AusgangsGeschaeftsDokument } from '../../types';
import type { StandDetail } from '../dokument/staende/staendeTypen';

/**
 * Entfernt Flags, die beim Laden etwas auslösen würden. Ein angesehener Stand ist
 * eingefroren: Er darf keine Standardtexte nachladen. Altes Format (reines Array)
 * und kaputtes JSON bleiben unverändert, der Editor behandelt sie wie sonst.
 */
function eingefrorenePositionen(positionenJson: string): string {
    try {
        const parsed: unknown = JSON.parse(positionenJson);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return positionenJson;
        if (!('standardTextbausteineErneuern' in parsed)) return positionenJson;
        const ohneFlag = { ...(parsed as Record<string, unknown>) };
        delete ohneFlag.standardTextbausteineErneuern;
        return JSON.stringify(ohneFlag);
    } catch {
        return positionenJson;
    }
}

/**
 * Legt den Inhalt eines Stands über das frisch geladene Dokument, damit der Editor
 * den Stand genau so zeigt, wie er exportiert wurde: Positionen (samt Rabatt),
 * Betreff, Datum, MwSt-Satz, Zahlungsziel und Rechnungsadresse. Stammdaten (Kunde,
 * Projekt, Nummer, Status) bleiben die des Dokuments. Ohne Stand kommt das Dokument
 * unverändert zurück.
 */
export function ueberlagereDokumentMitStand(
    dokument: AusgangsGeschaeftsDokument,
    stand: StandDetail | undefined,
): AusgangsGeschaeftsDokument {
    if (!stand) return dokument;
    return {
        ...dokument,
        betreff: stand.betreff ?? '',
        datum: stand.datum ?? dokument.datum,
        mwstSatz: stand.mwstSatz ?? dokument.mwstSatz,
        zahlungszielTage: stand.zahlungszielTage ?? dokument.zahlungszielTage,
        // Leer im Stand heißt: damals galt die Adresse aus dem Kundenstamm.
        rechnungsadresseOverride: stand.rechnungsadresseOverride ?? undefined,
        // Dokumente ohne Kunde und Projekt zeigen die gespeicherte Adresse direkt an.
        rechnungsadresse: stand.rechnungsadresseOverride ?? dokument.rechnungsadresse,
        positionenJson: stand.positionenJson != null ? eingefrorenePositionen(stand.positionenJson) : dokument.positionenJson,
    };
}
