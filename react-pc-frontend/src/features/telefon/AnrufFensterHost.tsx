import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../../components/ui/toast';
import { aktenPfad } from './api';
import { AnrufFenster } from './AnrufFenster';
import type { KontaktKurz } from './types';
import { useKontaktUeberblick } from './useKontaktUeberblick';
import { useTelefonBerechtigung } from './useTelefonBerechtigung';
import { useTelefonLive } from './useTelefonLive';

/**
 * Bindet das Live-Anruf-Fenster ins Layout ein – nur für Benutzer mit dem
 * Recht „Anrufe & Anrufbeantworter". Ohne Recht wird keine Verbindung
 * geöffnet.
 *
 * @param akteInNeuemTab Im Dokumenteditor würde „Akte öffnen" die offene
 *   Bearbeitung verlassen. Dort öffnen Akte, Projekt und Anfrage deshalb in
 *   einem neuen Tab.
 */
export function AnrufFensterHost({ akteInNeuemTab = false }: { akteInNeuemTab?: boolean }) {
    const darf = useTelefonBerechtigung();
    const { anrufe, schliessen } = useTelefonLive(darf === true);
    const navigate = useNavigate();
    const aktuell = anrufe[0];
    const aktuelleId = aktuell?.verbindungsId;
    const toast = useToast();
    const ueberblick = useKontaktUeberblick(aktuell?.kontakt ?? null, toast.error);

    const schliesseAktuellen = useCallback(() => {
        if (aktuelleId) schliessen(aktuelleId);
    }, [aktuelleId, schliessen]);

    const oeffne = useCallback((pfad: string) => {
        if (aktuelleId) schliessen(aktuelleId);
        if (akteInNeuemTab) window.open(pfad, '_blank', 'noopener');
        else navigate(pfad);
    }, [akteInNeuemTab, aktuelleId, navigate, schliessen]);

    const oeffneKontakt = useCallback((kontakt: KontaktKurz) => {
        const pfad = aktenPfad(kontakt.typ, kontakt.id);
        if (pfad) oeffne(pfad);
    }, [oeffne]);

    if (!aktuell) return null;
    return (
        <AnrufFenster
            anruf={aktuell}
            weitere={anrufe.length - 1}
            onSchliessen={schliesseAktuellen}
            onKontaktOeffnen={oeffneKontakt}
            ueberblick={ueberblick}
            onOeffnen={oeffne}
        />
    );
}
