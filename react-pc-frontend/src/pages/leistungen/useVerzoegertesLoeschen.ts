import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { leseFehlermeldung } from '../../lib/apiFehler';
import { loescheSofort } from './api';

/** So lange lässt sich ein Löschen ohne Rückfrage rückgängig machen. */
export const RUECKGAENGIG_MS = 6000;

export interface LoeschAuftrag {
    /** Eindeutig je Objekt, z. B. `ordner:12`. */
    schluessel: string;
    url: string;
    /** Name für die Meldung, z. B. „Geländer“. */
    name: string;
    /** Ersatztext, falls das Backend keinen eigenen Fehlersatz liefert. */
    fehlerText: string;
    /** Nach erfolgreichem DELETE – z. B. Daten neu laden. */
    onGeloescht: () => void;
}

interface Eintrag {
    auftrag: LoeschAuftrag;
    timer: ReturnType<typeof setTimeout>;
}

/**
 * Löschen mit „Rückgängig“ statt Rückfrage (für Dinge ohne Bezüge).
 *
 * Das Objekt verschwindet sofort aus der Ansicht, eine Meldung bietet
 * „Rückgängig“ an. Erst nach {@link RUECKGAENGIG_MS} geht das DELETE raus.
 * Verlässt man vorher die Seite (Unmount oder `pagehide`), gehen alle
 * ausstehenden Löschungen sofort per `keepalive` raus – sonst wäre ein
 * „gelöschter“ Ordner beim nächsten Besuch wieder da. Sobald das DELETE
 * rausgeht, schließt die Meldung, damit kein „Rückgängig“ ins Leere zeigt.
 * Scheitert das DELETE, taucht das Objekt wieder auf, und die Meldung des
 * Servers erscheint (nach einem Seitenwechsel lässt sich nichts mehr zeigen).
 */
export function useVerzoegertesLoeschen() {
    const toast = useToast();
    const [ausgeblendet, setAusgeblendet] = useState<ReadonlySet<string>>(() => new Set());
    const ausstehend = useRef(new Map<string, Eintrag>());
    const gemountet = useRef(false);

    const einblenden = useCallback((schluessel: string) => {
        setAusgeblendet((vorher) => {
            const neu = new Set(vorher);
            neu.delete(schluessel);
            return neu;
        });
    }, []);

    /** Ergebnis eines DELETE verarbeiten – nur, solange die Seite noch steht. */
    const auswerten = useCallback(async (anfrage: Promise<void>, auftrag: LoeschAuftrag) => {
        try {
            await anfrage;
            if (gemountet.current) auftrag.onGeloescht();
        } catch (fehler) {
            // TypeError = keine Verbindung; sonst steht der Satz des Servers im Fehler.
            const meldung = fehler instanceof Error && !(fehler instanceof TypeError) ? fehler.message : auftrag.fehlerText;
            if (gemountet.current) toast.error(meldung);
        } finally {
            if (gemountet.current) einblenden(auftrag.schluessel);
        }
    }, [einblenden, toast]);

    const senden = useCallback(async (schluessel: string) => {
        const eintrag = ausstehend.current.get(schluessel);
        if (!eintrag) return;
        ausstehend.current.delete(schluessel);
        toast.schliessen(eintrag.auftrag.schluessel);
        await auswerten(loescheSofort(eintrag.auftrag.url, eintrag.auftrag.fehlerText), eintrag.auftrag);
    }, [auswerten, toast]);

    const rueckgaengig = useCallback((schluessel: string, name: string) => {
        const eintrag = ausstehend.current.get(schluessel);
        if (!eintrag) {
            toast.warning(`„${name}“ war schon gelöscht.`);
            return;
        }
        clearTimeout(eintrag.timer);
        ausstehend.current.delete(schluessel);
        einblenden(schluessel);
        toast.info(`„${name}“ ist wieder da.`, 2500);
    }, [einblenden, toast]);

    const planen = useCallback((auftrag: LoeschAuftrag) => {
        if (ausstehend.current.has(auftrag.schluessel)) return;
        const timer = setTimeout(() => { void senden(auftrag.schluessel); }, RUECKGAENGIG_MS);
        // Die Meldung trägt den Schlüssel des Objekts – so lässt sie sich beim Senden schließen.
        toast.success(`„${auftrag.name}“ gelöscht.`, {
            aktion: { label: 'Rückgängig', onClick: () => rueckgaengig(auftrag.schluessel, auftrag.name) },
            dauerMs: RUECKGAENGIG_MS,
            schluessel: auftrag.schluessel,
        });
        ausstehend.current.set(auftrag.schluessel, { auftrag, timer });
        setAusgeblendet((vorher) => new Set(vorher).add(auftrag.schluessel));
    }, [rueckgaengig, senden, toast]);

    useEffect(() => {
        gemountet.current = true;
        const offen = ausstehend.current;
        const sofortSenden = () => {
            for (const { auftrag, timer } of offen.values()) {
                clearTimeout(timer);
                toast.schliessen(auftrag.schluessel);
                // keepalive: Die Anfrage überlebt das Schließen des Tabs.
                const anfrage = fetch(auftrag.url, { method: 'DELETE', keepalive: true }).then(async (res) => {
                    if (!res.ok) throw new Error(await leseFehlermeldung(res, auftrag.fehlerText));
                });
                void auswerten(anfrage, auftrag);
            }
            offen.clear();
        };
        window.addEventListener('pagehide', sofortSenden);
        return () => {
            window.removeEventListener('pagehide', sofortSenden);
            gemountet.current = false;
            sofortSenden();
        };
    }, [auswerten, toast]);

    return { ausgeblendet, planen };
}
