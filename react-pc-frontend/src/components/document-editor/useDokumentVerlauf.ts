import { useCallback, useEffect, useRef, useState } from 'react';
import {
    feldAenderungen,
    istLeer,
    leererVerlauf,
    nachherWerte,
    rueckgaengig as verlaufRueckgaengig,
    schrittAufnehmen,
    verlaufGeleert,
    wiederholen as verlaufWiederholen,
    type Schritt,
    type Verlauf,
} from '../../lib/aenderungsVerlauf';
import type { DocBlock } from './types';

/**
 * Bindet den generischen Aenderungsverlauf (`src/lib/aenderungsVerlauf.ts`) an
 * den Editor-Zustand des Dokumenteditors. Kennt -- anders als der Kern --
 * `DocBlock` und die konkreten Top-Level-Felder, die ein Schritt aendern kann.
 *
 * Ein Schritt merkt sich NUR die Felder, die er tatsaechlich veraendert hat.
 */
export interface DokumentStand {
    blocks: DocBlock[];
    globalRabatt: number;
    datum: string;
    /** Effektive Tage (kontextDaten.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE) -- nie undefined. */
    zahlungsziel: number;
    /** Nie undefined; leer = ''. */
    rechnungsadresse: string;
    balkenAnzeigen: boolean;
}

/** Wohin die UI nach einem Rueckgaengig/Wiederholen scrollen bzw. welches Feld sie fokussieren soll. */
export interface VerlaufsZiel {
    blockId: string;
    /** Gesetzt, wenn der Block ein Kind eines Bauabschnitts ist. */
    sectionId?: string;
    feld?: 'title' | 'quantity' | 'unit' | 'price' | 'description' | 'content' | 'sectionLabel';
}

export type DokumentSchritt = Schritt<DokumentStand, VerlaufsZiel>;

export interface VerlaufsMeldung {
    bezeichnung: string;
    anzahl: number;
    ziel: VerlaufsZiel | null;
}

export interface AenderungsAuftrag {
    bezeichnung: string;
    berechne: (stand: DokumentStand) => Partial<DokumentStand>;
    buendelSchluessel?: string | null;
    ziel?: VerlaufsZiel | null;
}

export interface DokumentVerlaufOptionen {
    leseStand: () => DokumentStand;
    schreibeStand: (werte: Partial<DokumentStand>) => void;
    gesperrt: boolean;
}

export interface DokumentVerlauf {
    /** true = der Stand hat sich geaendert und ein Schritt wurde geschrieben. */
    aendern: (auftrag: AenderungsAuftrag) => boolean;
    rueckgaengig: (anzahl?: number) => VerlaufsMeldung | null;
    wiederholen: () => VerlaufsMeldung | null;
    leeren: () => void;
    kannRueckgaengig: boolean;
    kannWiederholen: boolean;
    /** Neuester Schritt zuerst -- genau die Reihenfolge der Dropdown-Liste. */
    schritte: { id: number; bezeichnung: string }[];
    naechstesRueckgaengig: string | null;
    naechstesWiederholen: string | null;
}

function meldungAus(schritte: readonly DokumentSchritt[], zielSchritt: DokumentSchritt | undefined): VerlaufsMeldung {
    return {
        bezeichnung: schritte.length === 1 ? schritte[0].bezeichnung : `${schritte.length} Schritte`,
        anzahl: schritte.length,
        ziel: zielSchritt?.ziel ?? null,
    };
}

export function useDokumentVerlauf({
    leseStand,
    schreibeStand,
    gesperrt,
}: DokumentVerlaufOptionen): DokumentVerlauf {
    const [verlaufState, setVerlaufState] = useState<Verlauf<DokumentStand, VerlaufsZiel>>(
        () => leererVerlauf<DokumentStand, VerlaufsZiel>(),
    );
    // Synchroner Spiegel: `aendern`/`rueckgaengig`/`wiederholen` rechnen immer
    // auf dem aktuellen Verlauf, nicht auf einem evtl. veralteten State-Snapshot
    // (relevant bei zwei Aufrufen im selben Tick, z.B. schneller Tastatur-Doppelklick).
    const verlaufRef = useRef(verlaufState);

    // leseStand/schreibeStand/gesperrt in Refs spiegeln (Vorbild useIdleTimer.ts):
    // eine neue Funktionsreferenz des Aufrufers bei jedem Render soll aendern/
    // rueckgaengig/wiederholen nicht neu erzeugen. Das Schreiben passiert bewusst
    // in einem Effekt OHNE Dep-Array (laeuft nach jedem Commit), nicht direkt im
    // Funktionskoerper -- ein Ref waehrend des Renders zu beschreiben ist
    // unzulaessig (react-hooks/refs).
    const leseStandRef = useRef(leseStand);
    const schreibeStandRef = useRef(schreibeStand);
    const gesperrtRef = useRef(gesperrt);
    useEffect(() => {
        leseStandRef.current = leseStand;
        schreibeStandRef.current = schreibeStand;
        gesperrtRef.current = gesperrt;
    });

    const setzeVerlauf = useCallback((neu: Verlauf<DokumentStand, VerlaufsZiel>) => {
        verlaufRef.current = neu;
        setVerlaufState(neu);
    }, []);

    const aendern = useCallback((auftrag: AenderungsAuftrag): boolean => {
        if (gesperrtRef.current) return false;

        const stand = leseStandRef.current();
        const berechnet = auftrag.berechne(stand);
        const aenderungen = feldAenderungen(stand, berechnet);
        if (istLeer(aenderungen)) return false;

        schreibeStandRef.current(nachherWerte(aenderungen));

        setzeVerlauf(schrittAufnehmen(verlaufRef.current, {
            bezeichnung: auftrag.bezeichnung,
            aenderungen,
            buendelSchluessel: auftrag.buendelSchluessel,
            ziel: auftrag.ziel,
        }, Date.now()));
        return true;
    }, [setzeVerlauf]);

    const rueckgaengig = useCallback((anzahl?: number): VerlaufsMeldung | null => {
        if (gesperrtRef.current) return null;
        const sprung = verlaufRueckgaengig(verlaufRef.current, anzahl);
        if (!sprung) return null;

        schreibeStandRef.current(sprung.werte);
        setzeVerlauf(sprung.verlauf);
        // Rueckgaengig liefert die Schritte neuester-zuerst: der Kopf ist genau
        // die Stelle, die gerade eben zurueckgenommen wurde -- dahin soll "Stelle
        // zeigen" springen.
        return meldungAus(sprung.schritte, sprung.schritte[0]);
    }, [setzeVerlauf]);

    const wiederholen = useCallback((): VerlaufsMeldung | null => {
        if (gesperrtRef.current) return null;
        const sprung = verlaufWiederholen(verlaufRef.current);
        if (!sprung) return null;

        schreibeStandRef.current(sprung.werte);
        setzeVerlauf(sprung.verlauf);
        // Wiederholen liefert die Schritte in Ausfuehrungsreihenfolge: das Ende
        // ist die zuletzt angewendete (=juengste) Aenderung.
        return meldungAus(sprung.schritte, sprung.schritte[sprung.schritte.length - 1]);
    }, [setzeVerlauf]);

    const leeren = useCallback(() => {
        setzeVerlauf(verlaufGeleert(verlaufRef.current));
    }, [setzeVerlauf]);

    const { schritte, wiederholbar } = verlaufState;

    return {
        aendern,
        rueckgaengig,
        wiederholen,
        leeren,
        kannRueckgaengig: schritte.length > 0,
        kannWiederholen: wiederholbar.length > 0,
        schritte: [...schritte].reverse().map(s => ({ id: s.id, bezeichnung: s.bezeichnung })),
        naechstesRueckgaengig: schritte.length > 0 ? schritte[schritte.length - 1].bezeichnung : null,
        naechstesWiederholen: wiederholbar.length > 0 ? wiederholbar[wiederholbar.length - 1].bezeichnung : null,
    };
}
