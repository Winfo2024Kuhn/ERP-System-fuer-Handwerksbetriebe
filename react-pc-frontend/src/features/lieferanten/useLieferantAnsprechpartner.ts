import { useCallback, useEffect, useRef, useState } from 'react';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { apiAnfrage, apiJson, fehlerText } from '../../lib/apiFehler';
import { anzeigeName, zuAnfrage, type AnsprechpartnerEntwurf, type LieferantAnsprechpartner } from './ansprechpartnerEntwurf';

const LADEN_FEHLER = 'Ansprechpartner konnten nicht geladen werden.';

/** Wie der Server: nach Nachname, dann Vorname. */
function sortiert(liste: LieferantAnsprechpartner[]): LieferantAnsprechpartner[] {
    const schluessel = (a: LieferantAnsprechpartner) => `${a.nachname}\u0000${a.vorname ?? ''}`;
    return [...liste].sort((a, b) => schluessel(a).localeCompare(schluessel(b), 'de', { sensitivity: 'base' }));
}

/**
 * Ansprechpartner eines Lieferanten: lädt beim Einbinden (also erst, wenn der
 * Reiter offen ist), legt an, ändert und löscht. `liste === undefined` heißt
 * „lädt noch“, `fehler` heißt „Laden ist gescheitert“.
 */
export function useLieferantAnsprechpartner(lieferantId: number) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [liste, setListe] = useState<LieferantAnsprechpartner[] | undefined>(undefined);
    const [fehler, setFehler] = useState(false);
    const [ladeRunde, setLadeRunde] = useState(0);
    const [speichert, setSpeichert] = useState(false);
    const [loeschtId, setLoeschtId] = useState<number | null>(null);
    // Sperrt einen zweiten Klick sofort, noch bevor React neu gezeichnet hat.
    const beschaeftigtRef = useRef(false);
    const basis = `/api/lieferanten/${lieferantId}/ansprechpartner`;

    useEffect(() => {
        const abbruch = new AbortController();
        apiJson<LieferantAnsprechpartner[]>(basis, { signal: abbruch.signal }, LADEN_FEHLER)
            .then((daten) => {
                setListe(Array.isArray(daten) ? daten : []);
                setFehler(false);
            })
            .catch((err: unknown) => {
                if (abbruch.signal.aborted) return;
                setFehler(true);
                toast.error(fehlerText(err, LADEN_FEHLER));
            });
        return () => abbruch.abort();
    }, [basis, ladeRunde, toast]);

    /** Nach einem Ladefehler: noch einmal versuchen. */
    const neuLaden = useCallback(() => {
        setFehler(false);
        setListe(undefined);
        setLadeRunde(r => r + 1);
    }, []);

    /** Legt an (`id === null`) oder ändert. Liefert true, wenn gespeichert wurde. */
    const speichern = async (entwurf: AnsprechpartnerEntwurf, id: number | null): Promise<boolean> => {
        if (beschaeftigtRef.current) return false;
        beschaeftigtRef.current = true;
        setSpeichert(true);
        try {
            const gespeichert = await apiJson<LieferantAnsprechpartner>(id === null ? basis : `${basis}/${id}`, {
                method: id === null ? 'POST' : 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(zuAnfrage(entwurf)),
            }, 'Ansprechpartner konnte nicht gespeichert werden.');
            setListe(vorher => sortiert([...(vorher ?? []).filter(a => a.id !== gespeichert.id), gespeichert]));
            return true;
        } catch (err) {
            toast.error(fehlerText(err, 'Ansprechpartner konnte nicht gespeichert werden.'));
            return false;
        } finally {
            beschaeftigtRef.current = false;
            setSpeichert(false);
        }
    };

    const loeschen = async (ap: LieferantAnsprechpartner): Promise<void> => {
        if (beschaeftigtRef.current) return;
        const name = anzeigeName({ ...ap, anrede: null });
        const bestaetigt = await confirmDialog({
            title: 'Ansprechpartner löschen',
            message: `${name} löschen? Die Person verschwindet auch aus der Anruferkennung und dem FRITZ!Box-Telefonbuch. Das lässt sich nicht rückgängig machen.`,
            variant: 'danger',
            confirmLabel: 'Ansprechpartner löschen',
        });
        if (!bestaetigt) return;
        beschaeftigtRef.current = true;
        setLoeschtId(ap.id);
        try {
            await apiAnfrage(`${basis}/${ap.id}`, { method: 'DELETE' }, `${name} konnte nicht gelöscht werden.`);
            setListe(vorher => (vorher ?? []).filter(a => a.id !== ap.id));
        } catch (err) {
            toast.error(fehlerText(err, `${name} konnte nicht gelöscht werden.`));
        } finally {
            beschaeftigtRef.current = false;
            setLoeschtId(null);
        }
    };

    return { liste, fehler, neuLaden, speichern, speichert, loeschen, loeschtId };
}
