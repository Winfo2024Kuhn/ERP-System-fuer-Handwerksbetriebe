import { useCallback, useRef, useState } from 'react';
import { ladeKiModelle } from './kiEinstellungenApi';
import type { KiModellEintrag } from './kiEinstellungenApi';

export type KiModellListeStatus = 'leer' | 'laedt' | 'fertig' | 'fehler';

export interface KiModellListeZustand {
    status: KiModellListeStatus;
    modelle: KiModellEintrag[];
    hinweise: string[];
    fehler: string | null;
    /** Lädt die Liste, falls sie noch nicht da ist (oder beim letzten Mal scheiterte). */
    laden: () => void;
    /** Vergisst die Liste – z. B. nach einem neuen Schlüssel; beim nächsten Öffnen wird neu geladen. */
    verwerfen: () => void;
}

/**
 * Die Modelle aller Anbieter mit Schlüssel – einmal je Seite geladen und von
 * allen Modellsuchen geteilt. Geladen wird erst, wenn jemand eine Auswahl
 * öffnet: Der Server fragt dafür live bei den Anbietern nach.
 */
export function useKiModellListe(): KiModellListeZustand {
    const [status, setStatus] = useState<KiModellListeStatus>('leer');
    const [modelle, setModelle] = useState<KiModellEintrag[]>([]);
    const [hinweise, setHinweise] = useState<string[]>([]);
    const [fehler, setFehler] = useState<string | null>(null);
    // Zählt Ladevorgänge: Eine Antwort, die nach „verwerfen“ ankommt, ist veraltet.
    const runde = useRef(0);
    const statusRef = useRef<KiModellListeStatus>('leer');

    const setze = (neu: KiModellListeStatus) => {
        statusRef.current = neu;
        setStatus(neu);
    };

    const laden = useCallback(() => {
        if (statusRef.current === 'laedt' || statusRef.current === 'fertig') return;
        const meine = ++runde.current;
        setze('laedt');
        setFehler(null);
        ladeKiModelle()
            .then((liste) => {
                if (meine !== runde.current) return;
                setModelle(liste.modelle);
                setHinweise(liste.hinweise);
                setze('fertig');
            })
            .catch((e: unknown) => {
                if (meine !== runde.current) return;
                setFehler(e instanceof Error ? e.message : 'Die Modelle konnten nicht geladen werden.');
                setze('fehler');
            });
    }, []);

    const verwerfen = useCallback(() => {
        runde.current += 1;
        setze('leer');
        setModelle([]);
        setHinweise([]);
        setFehler(null);
    }, []);

    return { status, modelle, hinweise, fehler, laden, verwerfen };
}
