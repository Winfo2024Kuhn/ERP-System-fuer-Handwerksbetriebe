import { useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { leseFehlermeldung } from '../../lib/apiFehler';
import type { Buchung } from './kalenderTypen';
import { hatUeberschneidungen, zeitEntwurfAus } from './buchungZeiten';
import { mussGespeichertWerden, pruefeVorDemSpeichern, speicherInhalt } from './tagSpeichernRegeln';
import type { TagesBuchungen } from './useTagesBuchungen';

type Ergebnis = { ok: boolean; meldung?: string };

/** „Alle Speichern“ der Tagesansicht: erst alles prüfen, dann neue und geänderte Buchungen schicken. */
export function useTagSpeichern(tagesBuchungen: TagesBuchungen, mitarbeiterId: number, datum: string) {
    const toast = useToast();
    const bestaetige = useConfirm();
    const [speichert, setSpeichert] = useState(false);
    const [gespeichert, setGespeichert] = useState(false);
    const { buchungen, geaenderteIds, zeitEntwuerfe, setBuchungen, idErsetzen, alsGespeichertMerken } = tagesBuchungen;

    /** Speichert eine Buchung; bei Fehlern mit der Meldung des Servers (falls vorhanden). */
    const speichereEine = async (buchung: Buchung): Promise<Ergebnis> => {
        const istNeu = buchung.id < 0;
        try {
            const res = await fetch(istNeu ? '/api/zeitverwaltung/buchungen' : `/api/zeitverwaltung/buchungen/${buchung.id}`, {
                method: istNeu ? 'POST' : 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(speicherInhalt(buchung, mitarbeiterId, datum)),
            });
            if (!res.ok) return { ok: false, meldung: await leseFehlermeldung(res, 'Buchung konnte nicht gespeichert werden.') };
            // Neue Buchung bekommt ihre echte ID, damit sie beim nächsten Speichern nicht doppelt angelegt wird.
            const daten = await res.json();
            if (istNeu && daten?.id) idErsetzen(buchung.id, daten.id);
            return { ok: true };
        } catch (e) {
            console.error(e);
            return { ok: false };
        }
    };

    const speichereAlle = async () => {
        const pruefung = pruefeVorDemSpeichern(buchungen, geaenderteIds, zeitEntwuerfe);
        if (pruefung.meldungen.length > 0) {
            pruefung.meldungen.forEach(meldung => toast.error(meldung));
            return;
        }
        setBuchungen(pruefung.buchungen);
        if (hatUeberschneidungen(pruefung.buchungen, zeitEntwurfAus(zeitEntwuerfe))) {
            const trotzdem = await bestaetige({
                title: 'Überschneidungen',
                message: 'Es liegen zeitliche Überschneidungen bei den Buchungen vor.\nMöchten Sie trotzdem speichern?',
                variant: 'warning',
                confirmLabel: 'Trotzdem speichern',
            });
            if (!trotzdem) return;
        }

        setSpeichert(true);
        setGespeichert(false);
        // Gleiche Server-Meldungen (z. B. 403 bei jeder Buchung) nur einmal zeigen.
        const meldungen = new Set<string>();
        let fehlerOhneMeldung = false;
        const gespeicherteIds: number[] = [];
        for (const buchung of pruefung.buchungen) {
            if (!mussGespeichertWerden(buchung, geaenderteIds)) continue;
            const ergebnis = await speichereEine(buchung);
            if (ergebnis.ok) gespeicherteIds.push(buchung.id);
            if (ergebnis.meldung) meldungen.add(ergebnis.meldung);
            else if (!ergebnis.ok) fehlerOhneMeldung = true;
        }
        alsGespeichertMerken(gespeicherteIds);
        setSpeichert(false);

        if (meldungen.size === 0 && !fehlerOhneMeldung) {
            setGespeichert(true);
            setTimeout(() => setGespeichert(false), 2000);
            return;
        }
        meldungen.forEach(meldung => toast.error(meldung));
        if (fehlerOhneMeldung) toast.warning('Einige Buchungen konnten nicht gespeichert werden.');
    };

    return { speichert, gespeichert, speichereAlle };
}
