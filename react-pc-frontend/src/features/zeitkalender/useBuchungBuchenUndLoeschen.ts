import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { leseFehlermeldung } from '../../lib/apiFehler';
import { loeschRueckfrage } from './buchungLoeschen';
import { ABWESENHEIT_LABEL, getNextTempBookingId, istAbwesenheitTyp, type AbwesenheitTyp, type Buchung, type KalenderTag } from './kalenderTypen';

interface Optionen {
    mitarbeiterId: number;
    tag: KalenderTag;
    hinzufuegen: (buchung: Buchung) => void;
    entfernen: (id: number) => void;
}

/** Abwesenheit sofort buchen und Buchungen löschen – beides geht direkt an den Server. */
export function useBuchungBuchenUndLoeschen({ mitarbeiterId, tag, hinzufuegen, entfernen }: Optionen) {
    const toast = useToast();
    const bestaetige = useConfirm();

    const abwesenheitBuchen = async (typ: AbwesenheitTyp, halberTag = false, stundenWunsch?: number) => {
        try {
            const res = await fetch('/api/abwesenheit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ mitarbeiterId, datum: tag.datum, typ, halberTag, stunden: stundenWunsch }),
            });
            if (!res.ok) {
                const fehler = await res.json().catch(() => ({}));
                toast.error(fehler.error || fehler.message || `${ABWESENHEIT_LABEL[typ]} konnte nicht gebucht werden.`);
                return;
            }
            const daten = await res.json();
            const stunden = daten.stunden != null
                ? daten.stunden
                : (stundenWunsch ?? (halberTag && tag.sollStunden ? tag.sollStunden / 2 : (tag.sollStunden || null)));
            hinzufuegen({
                id: getNextTempBookingId(),
                abwesenheitId: daten.id,
                projektId: 0, projektName: '', arbeitsgangName: '',
                startZeit: '00:00', endeZeit: null,
                dauerMinuten: stunden != null ? Math.round(stunden * 60) : null,
                dauerFormatiert: stunden != null ? `${stunden}h` : null,
                notiz: daten.notiz || null,
                typ,
            });
            toast.success(`${ABWESENHEIT_LABEL[typ]} hinzugefügt.`);
        } catch (e) {
            console.error(e);
            toast.error('Netzwerkfehler beim Buchen der Abwesenheit.');
        }
    };

    const loeschen = async (buchung: Buchung) => {
        // Abwesenheiten kommen mit negativer Anzeige-ID, sind aber gespeichert und werden
        // über ihre echte abwesenheitId gelöscht – nicht nur lokal entfernt.
        const istAbwesenheit = istAbwesenheitTyp(buchung.typ);
        if (buchung.id < 0 && !istAbwesenheit) {
            entfernen(buchung.id);
            return;
        }
        if (!await bestaetige(loeschRueckfrage(buchung, tag.datum))) return;
        try {
            const url = istAbwesenheit
                ? `/api/abwesenheit/${buchung.abwesenheitId ?? Math.abs(buchung.id)}`
                : `/api/zeitverwaltung/buchungen/${buchung.id}`;
            const res = await fetch(url, { method: 'DELETE' });
            if (res.ok || res.status === 204) entfernen(buchung.id);
            else toast.error(await leseFehlermeldung(res, 'Buchung konnte nicht gelöscht werden.'));
        } catch (e) {
            console.error(e);
            toast.error('Netzwerkfehler beim Löschen');
        }
    };

    return { abwesenheitBuchen, loeschen };
}
