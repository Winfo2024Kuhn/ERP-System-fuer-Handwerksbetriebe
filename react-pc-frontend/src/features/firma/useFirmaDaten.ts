import { useCallback, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { firmenEntwurf, pruefeFirmenEntwurf } from './firmaEntwurf';
import type { FirmaAendern, FirmenEntwurf, GewerkOption } from './firmaTypen';

/** Firmendaten und Gewerke laden, im Formular ändern, prüfen und speichern. */
export function useFirmaDaten() {
    const toast = useToast();
    const showError = toast.error;
    const [firma, setFirma] = useState<FirmenEntwurf | null>(null);
    const [gewerke, setGewerke] = useState<GewerkOption[]>([]);
    const [speichert, setSpeichert] = useState(false);

    // Die Ladefunktionen sind Promise-Ketten: Zustand wird erst gesetzt, wenn die Antwort da ist.
    const ladeFirma = useCallback(() => fetch('/api/firma')
        .then(async (res) => {
            if (!res.ok) throw new Error('Firmendaten konnten nicht geladen werden.');
            setFirma(firmenEntwurf(await res.json()));
        })
        .catch((e) => {
            console.error('Fehler beim Laden der Firmendaten', e);
            showError('Firmendaten konnten nicht geladen werden.');
        }), [showError]);

    const ladeGewerke = useCallback(() => fetch('/api/lohn-stammdaten/gewerke?nurAktive=true')
        .then(async (res) => {
            if (!res.ok) throw new Error('Gewerke konnten nicht geladen werden.');
            setGewerke(await res.json());
        })
        .catch((e) => {
            console.error('Fehler beim Laden der Gewerke', e);
            showError('Gewerke konnten nicht geladen werden.');
        }), [showError]);

    const aendern: FirmaAendern = useCallback(
        (teil) => setFirma(prev => (prev ? { ...prev, ...teil } : prev)),
        [],
    );

    const speichern = async () => {
        if (!firma) return;
        const pruefung = pruefeFirmenEntwurf(firma);
        if (!pruefung.ok) { toast.error(pruefung.meldung); return; }
        setSpeichert(true);
        try {
            const res = await fetch('/api/firma', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(pruefung.payload)
            });
            if (res.ok) {
                setFirma(firmenEntwurf(await res.json()));
                toast.success('Firmendaten gespeichert.');
            } else {
                toast.error('Firmendaten konnten nicht gespeichert werden.');
            }
        } catch (e) {
            console.error('Fehler beim Speichern', e);
            toast.error('Firmendaten konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return { firma, gewerke, speichert, ladeFirma, ladeGewerke, aendern, speichern };
}
