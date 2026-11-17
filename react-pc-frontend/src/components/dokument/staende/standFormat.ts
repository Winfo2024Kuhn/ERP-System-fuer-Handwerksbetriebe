import type { StandEintrag } from './staendeTypen';

/** „2026-10-31T14:32:05“ → „31.10.2026, 14:32 Uhr“. Unlesbares bleibt, wie es ist. */
export function formatiereStandZeit(iso: string): string {
    const t = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso);
    return t ? `${t[3]}.${t[2]}.${t[1]}, ${t[4]}:${t[5]} Uhr` : iso;
}

const EURO = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });

export function formatiereNetto(betrag: number | null): string {
    return betrag == null ? '–' : EURO.format(betrag);
}

/** Schreibgeschützte Ansicht eines Stands im Dokumenteditor (eigener Tab). */
export function standEditorUrl(dokumentId: number, standId: number): string {
    return `/dokument-editor?dokumentId=${encodeURIComponent(dokumentId)}&stand=${encodeURIComponent(standId)}`;
}

export function aktuellerStandUrl(dokumentId: number): string {
    return `/dokument-editor?dokumentId=${encodeURIComponent(dokumentId)}`;
}

export function standPdfUrl(dokumentId: number, standId: number): string {
    return `/api/ausgangs-dokumente/${encodeURIComponent(dokumentId)}/staende/${encodeURIComponent(standId)}/pdf`;
}

/**
 * Warum dieser Stand nicht wiederhergestellt werden kann, oder `null`, wenn es geht.
 * Ob das Dokument noch geändert werden darf, entscheidet allein der Server
 * (`dokumentBearbeitbar` an Liste und Stand); hier wird nichts selbst abgeleitet.
 */
export function wiederherstellenSperrgrund(
    dokumentBearbeitbar: boolean | undefined,
    stand: Pick<StandEintrag, 'aktuell'>,
): string | null {
    if (dokumentBearbeitbar === false) {
        return 'Das Dokument lässt sich nicht mehr ändern (gebucht, angenommen oder storniert). Ältere Stände können Sie nur ansehen.';
    }
    if (stand.aktuell) return 'Dieser Stand entspricht schon dem aktuellen Inhalt.';
    return null;
}

/** Satz im Band der Stand-Ansicht. */
export function standAnsichtText(stand: Pick<StandEintrag, 'nummer' | 'festgehaltenAm' | 'festgehaltenVon' | 'art'>): string {
    const von = stand.festgehaltenVon
        ? `, ${stand.art === 'VOR_WIEDERHERSTELLEN' ? 'gesichert' : 'exportiert'} von ${stand.festgehaltenVon}`
        : '';
    return `Sie sehen Stand ${stand.nummer} vom ${formatiereStandZeit(stand.festgehaltenAm)}${von}. Dieser Stand ist eingefroren.`;
}
