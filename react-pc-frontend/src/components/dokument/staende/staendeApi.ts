import { apiJson } from '../../../lib/apiFehler';
import type { AusgangsGeschaeftsDokument } from '../../../types';
import { STAENDE_SEITENGROESSE, type StaendeSeite, type StandDetail, type StandEintrag } from './staendeTypen';

const basis = (dokumentId: number) => `/api/ausgangs-dokumente/${encodeURIComponent(dokumentId)}/staende`;

export function ladeStaendeSeite(
    dokumentId: number, seite: number, signal?: AbortSignal, groesse = STAENDE_SEITENGROESSE,
): Promise<StaendeSeite> {
    return apiJson<StaendeSeite>(`${basis(dokumentId)}?page=${seite}&size=${groesse}`, { signal }, 'Stände konnten nicht geladen werden.');
}

export function ladeStandDetail(dokumentId: number, standId: number, signal?: AbortSignal): Promise<StandDetail> {
    return apiJson<StandDetail>(`${basis(dokumentId)}/${encodeURIComponent(standId)}`, { signal }, 'Dieser Stand konnte nicht geladen werden.');
}

/** Holt den Inhalt eines Stands zurück; die Antwort ist das Dokument mit dem neuen Inhalt. */
export function sendeWiederherstellen(dokumentId: number, standId: number): Promise<AusgangsGeschaeftsDokument> {
    return apiJson<AusgangsGeschaeftsDokument>(
        `${basis(dokumentId)}/${encodeURIComponent(standId)}/wiederherstellen`,
        { method: 'POST' },
        'Der Stand konnte nicht wiederhergestellt werden.',
    );
}

/**
 * Meldet dem Server einen Export, der kein PDF ablegt (Drucken oder PDF-Download von
 * Angebot und Auftragsbestätigung), damit er einen Stand festhält. Fehler stoppen
 * Druck und Download nicht, sie gehen als Warnung an `warnen`.
 */
export async function halteStandFest(dokumentId: number, warnen: (text: string) => void): Promise<void> {
    try {
        await apiJson<{ festgehalten: boolean; stand: StandEintrag | null }>(
            `${basis(dokumentId)}/festhalten`, { method: 'POST' }, 'Der Stand konnte nicht festgehalten werden.',
        );
    } catch {
        warnen('Der Stand für den Verlauf konnte nicht festgehalten werden.');
    }
}
