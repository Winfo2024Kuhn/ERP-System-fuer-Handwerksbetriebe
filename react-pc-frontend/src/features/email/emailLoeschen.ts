import type { ConfirmOptions } from '../../components/ui/confirm-dialog';
import type { EmailItem } from './emailCenterModel';

/** Mehr IDs nimmt der Server je Aufruf nicht an. */
export const PAPIERKORB_BLOCK = 500;

const anzahl = (n: number, einzahl: string, mehrzahl: string) => (n === 1 ? einzahl : `${n} ${mehrzahl}`);

export const papierkorbText = (n: number) => `${anzahl(n, 'E-Mail', 'E-Mails')} in den Papierkorb gelegt.`;
export const zurueckText = (n: number) => (n === 1 ? 'E-Mail ist wieder da.' : `${n} E-Mails sind wieder da.`);
export const schonImPapierkorbText = (n: number) =>
    (n === 1 ? 'Diese E-Mail liegt schon im Papierkorb.' : 'Diese E-Mails liegen schon im Papierkorb.');
export const nichtMehrImPapierkorbText = (n: number) =>
    (n === 1 ? 'Diese E-Mail liegt nicht mehr im Papierkorb.' : 'Diese E-Mails liegen nicht mehr im Papierkorb.');
export const endgueltigText = (n: number) => `${anzahl(n, 'E-Mail', 'E-Mails')} endgültig gelöscht.`;

const FOLGE = 'endgültig gelöscht und auch vom Mailserver entfernt. Das lässt sich nicht rückgängig machen.';

/** Rückfrage vor dem endgültigen Löschen im Papierkorb: nennt Betreff bzw. Anzahl. */
export function endgueltigFrage(n: number, betreff?: string | null): ConfirmOptions {
    const titel = betreff?.trim();
    let message: string;
    if (n === 1) message = titel ? `„${titel}“ wird ${FOLGE}` : `Die E-Mail wird ${FOLGE}`;
    else if (titel) message = `Der Verlauf „${titel}“ mit ${n} E-Mails wird ${FOLGE}`;
    else message = `${n} E-Mails werden ${FOLGE}`;
    return { title: 'Endgültig löschen?', message, confirmLabel: 'Endgültig löschen', variant: 'danger' };
}

/** Teilt IDs in Blöcke, damit kein Aufruf die Obergrenze des Servers reißt. */
export function inBloecke(ids: number[], groesse = PAPIERKORB_BLOCK): number[][] {
    const bloecke: number[][] = [];
    for (let i = 0; i < ids.length; i += groesse) bloecke.push(ids.slice(i, i + groesse));
    return bloecke;
}

/**
 * Welche Mail nach dem Löschen geöffnet wird (wie Outlook/Gmail): die erste unter der letzten
 * gelöschten Zeile, am Listenende die darüber. `null`, wenn nichts übrig bleibt.
 */
export function naechsteNachLoeschen(liste: EmailItem[], entfernt: ReadonlySet<number>): EmailItem | null {
    let letzte = -1;
    liste.forEach((email, index) => { if (entfernt.has(email.id)) letzte = index; });
    if (letzte === -1) return null;
    for (let i = letzte + 1; i < liste.length; i++) if (!entfernt.has(liste[i].id)) return liste[i];
    for (let i = letzte - 1; i >= 0; i--) if (!entfernt.has(liste[i].id)) return liste[i];
    return null;
}
