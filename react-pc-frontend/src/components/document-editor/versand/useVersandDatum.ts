import { useRef, useState, type MutableRefObject } from 'react';
import { heuteIso } from '../../../lib/datum';
import type { DokumentDatumWahlProps } from '../DokumentDatumWahl';
import {
    DATUM_WAHL_START,
    datumWahlFehler,
    gewaehltesDatum,
    mussDatumFragen,
    type DatumWahl,
} from '../datumWahlRegeln';

interface Optionen {
    datumRef: MutableRefObject<string>;
    setDatum: (iso: string) => void;
    isLocked: boolean;
    istRechnung: boolean;
    zahlungszielTage: number;
}

export interface VersandDatum {
    /** Props für `DokumentDatumWahl`, null wenn es nichts zu fragen gibt. */
    wahlProps: DokumentDatumWahlProps | null;
    /** Datum, das nach der aktuellen Wahl oben stehen wird (leer bei „Anderes Datum“ ohne Wahl). */
    datum: string;
    /** Grund, warum mit der aktuellen Wahl nicht weitergemacht werden kann. */
    fehler: string | null;
    /** Beim Öffnen des ersten Versand-Dialogs: Frage vorbereiten, Vorauswahl heute. */
    beginne: (entwurf: boolean) => void;
    /** Unmittelbar vor Speichern und PDF: gewähltes Datum ins Dokument schreiben. */
    uebernehmen: () => void;
}

/**
 * Datum oben auf dem Dokument beim endgültigen Drucken, Herunterladen und Senden.
 * Das gewählte Datum liegt zusätzlich in einem Ref, weil der Versand es erst
 * Dialoge später (Buchungswarnung, Online-Annahme) übernimmt; `datumRef` wird
 * synchron gesetzt, damit Speichern und PDF-Anfrage im selben Tick den neuen
 * Wert lesen.
 */
export function useVersandDatum({ datumRef, setDatum, isLocked, istRechnung, zahlungszielTage }: Optionen): VersandDatum {
    const [frage, setFrage] = useState<{ gespeichertesDatum: string; heute: string } | null>(null);
    const [wahl, setWahl] = useState<DatumWahl>(DATUM_WAHL_START);
    /** Gespeichertes Datum beim Öffnen; gilt, wenn nicht gefragt wird. */
    const [datumBeimStart, setDatumBeimStart] = useState('');
    const zielRef = useRef<string | null>(null);

    const beginne = (entwurf: boolean) => {
        const heute = heuteIso();
        const gespeichertesDatum = datumRef.current;
        const fragen = mussDatumFragen({ gespeichertesDatum, heute, gesperrt: isLocked, entwurf });
        setFrage(fragen ? { gespeichertesDatum, heute } : null);
        setDatumBeimStart(gespeichertesDatum);
        setWahl(DATUM_WAHL_START);
        zielRef.current = fragen ? heute : null;
    };

    const aendere = (neu: DatumWahl) => {
        setWahl(neu);
        zielRef.current = frage ? gewaehltesDatum(neu, frage.gespeichertesDatum, frage.heute) || null : null;
    };

    const uebernehmen = () => {
        const ziel = zielRef.current;
        zielRef.current = null;
        if (!ziel || isLocked || datumRef.current === ziel) return;
        datumRef.current = ziel;
        setDatum(ziel);
    };

    return {
        wahlProps: frage && {
            gespeichertesDatum: frage.gespeichertesDatum,
            heute: frage.heute,
            wert: wahl,
            onChange: aendere,
            istRechnung,
            zahlungszielTage,
        },
        datum: frage ? gewaehltesDatum(wahl, frage.gespeichertesDatum, frage.heute) : datumBeimStart,
        fehler: frage ? datumWahlFehler(wahl, frage.gespeichertesDatum, frage.heute, istRechnung) : null,
        beginne,
        uebernehmen,
    };
}
