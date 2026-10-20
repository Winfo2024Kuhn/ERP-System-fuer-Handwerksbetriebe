import { render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BuchungVerlaufDialog } from './BuchungVerlaufDialog';

const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() } }));
vi.mock('../../components/ui/toast', () => ({ useToast: () => toast }));

const eintrag = (version: number, aktion: string, geaendertVia: string | null, extra: Record<string, unknown> = {}) => ({
    id: version * 10, version, aktion,
    startZeit: '2026-08-03T08:00:00', endeZeit: '2026-08-03T15:30:00', anzahlInStunden: 7.5,
    notiz: null, geaendertVon: 'Max Mustermann', geaendertAm: `2026-08-0${version + 2}T10:15:00`,
    geaendertVia, aenderungsgrund: null, ...extra,
});

const fetchMock = vi.fn();
beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => { vi.unstubAllGlobals(); });

describe('BuchungVerlaufDialog', () => {
    it('zeigt alle Aktionen und Wege in Klartext, neueste oben', async () => {
        // Absichtlich älteste zuerst geliefert – die Anzeige sortiert selbst.
        fetchMock.mockResolvedValue({ ok: true, json: async () => [
            eintrag(1, 'ERSTELLT', 'MOBILE_APP'),
            eintrag(2, 'GEAENDERT', 'DESKTOP', { aenderungsgrund: 'Korrektur im Zeiterfassungskalender', geaendertVon: null }),
            eintrag(3, 'STORNIERT', 'ADMIN_KORREKTUR'),
            eintrag(4, 'GEAENDERT', 'IMPORT'),
            eintrag(5, 'GEAENDERT', 'SYSTEM', { endeZeit: null, anzahlInStunden: null }),
        ] });
        render(<BuchungVerlaufDialog open onOpenChange={vi.fn()} buchungId={42} beschreibung="Montag · 08:00 – 15:30" />);

        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/zeitverwaltung/buchungen/42/historie', expect.anything()));
        const eintraege = within(await screen.findByRole('list', { name: 'Änderungen, neueste zuerst' })).getAllByRole('listitem');
        expect(eintraege).toHaveLength(5);
        expect(eintraege[0]).toHaveTextContent('über Automatisch vom Programm');
        expect(eintraege[0]).toHaveTextContent('08:00 – –');
        expect(eintraege[1]).toHaveTextContent('über Import');
        expect(eintraege[2]).toHaveTextContent('Gelöscht');
        expect(eintraege[2]).toHaveTextContent('über Korrektur durch die Verwaltung');
        expect(eintraege[3]).toHaveTextContent('Geändert');
        expect(eintraege[3]).toHaveTextContent('Unbekannt');
        expect(eintraege[3]).toHaveTextContent('Grund: Korrektur im Zeiterfassungskalender');
        expect(eintraege[3]).toHaveTextContent('über Büro (PC)');
        expect(eintraege[4]).toHaveTextContent('Angelegt');
        expect(eintraege[4]).toHaveTextContent('Max Mustermann');
        expect(eintraege[4]).toHaveTextContent('über Handy-App');
        expect(eintraege[4]).toHaveTextContent('08:00 – 15:30');
        expect(eintraege[4]).toHaveTextContent('7,50 Std.');
        expect(screen.getByText('Montag · 08:00 – 15:30')).toBeVisible();
        expect(screen.getByRole('button', { name: 'Mehr zu: Verlauf' })).toBeVisible();
    });

    it('sagt Bescheid, wenn es noch keinen Verlauf gibt', async () => {
        fetchMock.mockResolvedValue({ ok: true, json: async () => [] });
        render(<BuchungVerlaufDialog open onOpenChange={vi.fn()} buchungId={42} />);
        expect(await screen.findByText('Für diese Buchung ist noch kein Verlauf vorhanden.')).toBeVisible();
    });

    it('zeigt die Server-Meldung, wenn der Verlauf nicht geladen werden kann', async () => {
        fetchMock.mockResolvedValue({ ok: false, status: 403, json: async () => ({ error: 'Keine Berechtigung für diesen Verlauf.' }) });
        render(<BuchungVerlaufDialog open onOpenChange={vi.fn()} buchungId={42} />);
        expect(await screen.findByRole('alert')).toHaveTextContent('Keine Berechtigung für diesen Verlauf.');
        expect(toast.error).toHaveBeenCalledWith('Keine Berechtigung für diesen Verlauf.');
    });

    it('lädt nichts, solange der Dialog geschlossen ist', () => {
        render(<BuchungVerlaufDialog open={false} onOpenChange={vi.fn()} buchungId={42} />);
        expect(fetchMock).not.toHaveBeenCalled();
    });
});
