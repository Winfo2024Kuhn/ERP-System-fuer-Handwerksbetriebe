import type { Baum } from '../../lib/ordnerBaum';
import { wurzelId } from './firmendokumenteLogik';
import type { FirmenOrdner } from './typen';

/**
 * Rückfrage vor dem Verschieben eines Ordners, wenn sich dadurch ändert, wer
 * ihn sehen darf – sonst `null`. Regel wie auf dem Server: Kommt ein Ordner
 * unter einen anderen Hauptordner, gelten dessen Rechte. Wird ein Unterordner
 * selbst zum Hauptordner (`zielId = null`), behält er die Sichtbarkeit seines
 * bisherigen Hauptordners – dann keine Rückfrage. Gefragt wird nur, wenn der
 * alte oder der neue Zweig eingeschränkt ist.
 */
export function sichtbarkeitsRueckfrage(baum: Baum<FirmenOrdner>, ordnerId: number, zielId: number | null): string | null {
    const ordner = baum.byId.get(ordnerId);
    const ziel = zielId === null ? undefined : baum.byId.get(zielId);
    if (!ordner || !ziel || zielId === null) return null;
    const vorher = wurzelId(baum, ordnerId);
    const nachher = wurzelId(baum, zielId);
    const neuerHauptordner = nachher === null ? undefined : baum.byId.get(nachher);
    if (vorher === nachher || !neuerHauptordner) return null;
    if (!ordner.eingeschraenkt && !ziel.eingeschraenkt) return null;
    const frage = `„${ordner.name}“ nach „${ziel.name}“ verschieben?`;
    const folge = ziel.eingeschraenkt
        ? `${frage} Danach sehen ihn nur noch die Abteilungen, die „${neuerHauptordner.name}“ sehen dürfen.`
        : `${frage} Danach ist er für alle Mitarbeiter sichtbar.`;
    if (eigeneRechteGehenVerloren(baum, ordnerId, zielId)) return `${folge} Die Einstellung „Wer darf das sehen?“ dieses Ordners geht dabei verloren.`;
    // Offener Hauptordner in einen eingeschränkten Zweig: heraus darf nur ein Admin.
    if (ordner.parentId === null && ziel.eingeschraenkt) return `${folge} Zurückholen geht danach nur über einen Admin.`;
    return folge;
}

/**
 * Darf das Verschieben per „Rückgängig“ zurückgenommen werden? Ja für Unterordner (sie
 * tragen keine eigenen Rechte) und für offene Hauptordner, die in einen offenen Zweig
 * kommen. Nein, wenn ein Hauptordner einen eingeschränkten Zweig berührt: Ein
 * eingeschränkter verliert seine Rechte, ein offener käme zurückgeholt eingeschränkt
 * zurück (der Server übernimmt beim Zurückholen die Rechte des bisherigen Hauptordners).
 */
export function rueckgaengigMoeglich(baum: Baum<FirmenOrdner>, ordnerId: number, zielId: number | null): boolean {
    const ordner = baum.byId.get(ordnerId);
    if (!ordner) return false;
    if (ordner.parentId !== null) return true;
    const ziel = zielId === null ? undefined : baum.byId.get(zielId);
    return !ordner.eingeschraenkt && !ziel?.eingeschraenkt;
}

/**
 * Verliert der Ordner beim Verschieben seine eigene Einstellung „Wer darf das sehen?“?
 * Ja bei einem eingeschränkten Hauptordner, der in einen anderen Ordner kommt: Der Server
 * löscht dann seine Rechte-Einträge. Ein „Rückgängig“ würde ihn danach für alle sichtbar
 * machen – deshalb gibt es in diesem Fall keins.
 */
export function eigeneRechteGehenVerloren(baum: Baum<FirmenOrdner>, ordnerId: number, zielId: number | null): boolean {
    const ordner = baum.byId.get(ordnerId);
    return !!ordner && ordner.parentId === null && ordner.eingeschraenkt && zielId !== null;
}
