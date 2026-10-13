import type { ZeitkontoStatus } from '../../types/zeitkonto';

/**
 * Höchste Tagesstundenzahl aus der aktuell gültigen Arbeitszeit eines Zeitkontos.
 * Die Stunden liegen unter `aktuell.arbeitszeit` (ZeitkontoStatusDto), nicht flach –
 * ohne eingerichtete Arbeitszeit gibt es keinen Wert, dann greift der Standard.
 */
export function maxTagesstundenAusZeitkonto(konto: Pick<ZeitkontoStatus, 'aktuell'>): number | null {
    const arbeitszeit = konto.aktuell?.arbeitszeit;
    if (!arbeitszeit) return null;
    const stunden = [
        arbeitszeit.montagStunden, arbeitszeit.dienstagStunden, arbeitszeit.mittwochStunden,
        arbeitszeit.donnerstagStunden, arbeitszeit.freitagStunden, arbeitszeit.samstagStunden, arbeitszeit.sonntagStunden,
    ].map(wert => Number(wert ?? 0)).filter(Number.isFinite);
    const max = Math.max(0, ...stunden);
    return max > 0 ? max : null;
}
