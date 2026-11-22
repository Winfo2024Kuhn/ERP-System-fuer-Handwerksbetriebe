/** Texte rund ums Löschen einer Buchung (rein, ohne React). */
import { ABWESENHEIT_LABEL, istAbwesenheitTyp, type Buchung } from './kalenderTypen';

const uhrzeit = (zeit: string | null) => (zeit ? zeit.substring(0, 5) : 'offen');
const datumDeutsch = (iso: string) => iso.split('-').reverse().join('.');

/** „Buchung 07:00–11:27“, „Pause 12:00–12:30“ oder „Urlaub“. */
export function buchungBezeichnung(buchung: Buchung): string {
    if (istAbwesenheitTyp(buchung.typ)) return ABWESENHEIT_LABEL[buchung.typ];
    const art = buchung.typ === 'PAUSE' ? 'Pause' : 'Buchung';
    return `${art} ${uhrzeit(buchung.startZeit)}–${uhrzeit(buchung.endeZeit)}`;
}

/** Name des Löschen-Knopfs an der Zeile. */
export const loeschKnopfName = (buchung: Buchung) => `${buchungBezeichnung(buchung)} löschen`;

/**
 * Rückfrage vor dem Löschen. Arbeitszeit und Pausen storniert der Server mit Protokoll,
 * Abwesenheiten löscht er endgültig – das sagt der Text ehrlich.
 */
export function loeschRueckfrage(buchung: Buchung, datum: string) {
    if (istAbwesenheitTyp(buchung.typ)) {
        const art = ABWESENHEIT_LABEL[buchung.typ];
        return {
            title: `${art} löschen?`,
            message: `${art} am ${datumDeutsch(datum)} löschen? Das lässt sich nicht rückgängig machen.`,
            confirmLabel: `${art} löschen`,
            variant: 'danger' as const,
        };
    }
    const art = buchung.typ === 'PAUSE' ? 'Pause' : 'Buchung';
    const projekt = buchung.typ !== 'PAUSE' && buchung.projektName ? ` (${buchung.projektName})` : '';
    return {
        title: `${art} löschen?`,
        message: `${buchungBezeichnung(buchung)}${projekt} löschen? Die ${art} verschwindet aus dem Kalender, im Änderungsprotokoll bleibt sie sichtbar.`,
        confirmLabel: `${art} löschen`,
        variant: 'danger' as const,
    };
}
