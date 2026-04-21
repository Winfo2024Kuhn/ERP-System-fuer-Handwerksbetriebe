import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ArtikelDetailModal } from './ArtikelDetailModal';
import type { Artikel, ArtikelPreisHistorieEintrag } from '../types';

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

const makeArtikel = (overrides: Partial<Artikel> = {}): Artikel => ({
    id: 10,
    produktname: 'Rohr 40x40x2',
    produktlinie: 'DIN EN 10210',
    werkstoffName: 'S235',
    verrechnungseinheit: { name: 'KILOGRAMM', anzeigename: 'Kilogramm' },
    durchschnittspreisNetto: 4.8250,
    durchschnittspreisMenge: 1245,
    durchschnittspreisAktualisiertAm: '2026-04-21T10:00:00',
    anzahlLieferanten: 2,
    lieferantenpreise: [
        { lieferantId: 1, lieferantName: 'Stahl GmbH', preis: 5.20, externeArtikelnummer: 'A-1', preisDatum: '2026-04-10' },
        { lieferantId: 2, lieferantName: 'Metall AG', preis: 4.90, externeArtikelnummer: 'B-1', preisDatum: '2026-04-15' },
    ],
    ...overrides,
});

const makeHistorieEintrag = (overrides: Partial<ArtikelPreisHistorieEintrag> = {}): ArtikelPreisHistorieEintrag => ({
    id: 100,
    preis: 4.90,
    menge: 500,
    einheit: { name: 'KILOGRAMM', anzeigename: 'Kilogramm' },
    quelle: 'RECHNUNG',
    lieferantId: 2,
    lieferantName: 'Metall AG',
    externeNummer: 'B-1',
    belegReferenz: 'RE-2026-0042',
    erfasstAm: '2026-04-20T12:00:00',
    ...overrides,
});

describe('ArtikelDetailModal', () => {
    beforeEach(() => {
        mockFetch.mockReset();
    });

    it('zeigt den Durchschnittspreis mit Einheit und Menge', async () => {
        mockFetch.mockResolvedValueOnce({ ok: true, json: async () => [] });
        render(
            <ArtikelDetailModal
                artikel={makeArtikel()}
                onClose={vi.fn()}
                onEditPrice={vi.fn()}
            />
        );

        expect(screen.getByText('Rohr 40x40x2')).toBeInTheDocument();
        expect(screen.getByText(/4,825\s*€/)).toBeInTheDocument();
        expect(screen.getAllByText(/\/\s*kg/).length).toBeGreaterThan(0);
        expect(screen.getByText(/aus 1\.245 kg Historie/)).toBeInTheDocument();
        await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('/api/artikel/10/preis-historie'));
    });

    it('listet alle Lieferantenpreise mit Edit-Button', async () => {
        mockFetch.mockResolvedValueOnce({ ok: true, json: async () => [] });
        const onEditPrice = vi.fn();
        render(
            <ArtikelDetailModal
                artikel={makeArtikel()}
                onClose={vi.fn()}
                onEditPrice={onEditPrice}
            />
        );

        expect(screen.getByText('Stahl GmbH')).toBeInTheDocument();
        expect(screen.getByText('Metall AG')).toBeInTheDocument();
        expect(screen.getByText('A-1')).toBeInTheDocument();
        expect(screen.getByText('B-1')).toBeInTheDocument();

        fireEvent.click(screen.getByLabelText('Preis von Metall AG bearbeiten'));
        expect(onEditPrice).toHaveBeenCalledWith(
            expect.objectContaining({ id: 10 }),
            { id: 2, name: 'Metall AG' }
        );
    });

    it('ruft onEditPrice mit null beim Button "Neuen Lieferantenpreis hinzufuegen"', async () => {
        mockFetch.mockResolvedValueOnce({ ok: true, json: async () => [] });
        const onEditPrice = vi.fn();
        render(
            <ArtikelDetailModal
                artikel={makeArtikel()}
                onClose={vi.fn()}
                onEditPrice={onEditPrice}
            />
        );

        fireEvent.click(screen.getByText(/Neuen Lieferantenpreis hinzuf/i));
        expect(onEditPrice).toHaveBeenCalledWith(expect.objectContaining({ id: 10 }), null);
    });

    it('zeigt Preis-Historie mit Quelle-Badge und Beleg-Referenz', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => [makeHistorieEintrag()],
        });
        render(
            <ArtikelDetailModal
                artikel={makeArtikel()}
                onClose={vi.fn()}
                onEditPrice={vi.fn()}
            />
        );

        await waitFor(() => expect(screen.getByText('Rechnung')).toBeInTheDocument());
        expect(screen.getByText(/RE-2026-0042/)).toBeInTheDocument();
        expect(screen.getAllByText(/Metall AG/).length).toBeGreaterThanOrEqual(2);
    });

    it('zeigt "Mehr anzeigen" wenn Historie laenger als 10 Eintraege ist', async () => {
        const eintraege = Array.from({ length: 12 }, (_, i) =>
            makeHistorieEintrag({ id: i + 1, preis: 4 + i * 0.1 })
        );
        mockFetch.mockResolvedValueOnce({ ok: true, json: async () => eintraege });

        render(
            <ArtikelDetailModal
                artikel={makeArtikel()}
                onClose={vi.fn()}
                onEditPrice={vi.fn()}
            />
        );

        await waitFor(() => expect(screen.getByText(/Mehr anzeigen \(2 weitere\)/)).toBeInTheDocument());
        fireEvent.click(screen.getByText(/Mehr anzeigen/));
        await waitFor(() =>
            expect(screen.queryByText(/Mehr anzeigen/)).not.toBeInTheDocument()
        );
    });

    it('zeigt Fallback wenn kein Durchschnittspreis vorhanden', async () => {
        mockFetch.mockResolvedValueOnce({ ok: true, json: async () => [] });
        render(
            <ArtikelDetailModal
                artikel={makeArtikel({
                    durchschnittspreisNetto: undefined,
                    durchschnittspreisMenge: undefined,
                    durchschnittspreisAktualisiertAm: undefined,
                })}
                onClose={vi.fn()}
                onEditPrice={vi.fn()}
            />
        );

        expect(screen.getByText(/Noch kein Durchschnittspreis erfasst/)).toBeInTheDocument();
    });

    it('zeigt Fehler-Meldung wenn Historie-Endpoint fehlschlaegt', async () => {
        mockFetch.mockResolvedValueOnce({ ok: false });
        const errSpy = vi.spyOn(console, 'error').mockImplementation(() => { });
        render(
            <ArtikelDetailModal
                artikel={makeArtikel()}
                onClose={vi.fn()}
                onEditPrice={vi.fn()}
            />
        );

        await waitFor(() =>
            expect(screen.getByText(/Preis-Historie konnte nicht geladen werden/)).toBeInTheDocument()
        );
        errSpy.mockRestore();
    });

    it('schliesst Modal per Escape-Taste', async () => {
        mockFetch.mockResolvedValueOnce({ ok: true, json: async () => [] });
        const onClose = vi.fn();
        render(
            <ArtikelDetailModal
                artikel={makeArtikel()}
                onClose={onClose}
                onEditPrice={vi.fn()}
            />
        );

        fireEvent.keyDown(window, { key: 'Escape' });
        expect(onClose).toHaveBeenCalled();
    });

    it('hebt den gefilterten Lieferanten optisch hervor', async () => {
        mockFetch.mockResolvedValueOnce({ ok: true, json: async () => [] });
        const { container } = render(
            <ArtikelDetailModal
                artikel={makeArtikel()}
                highlightLieferantName="Metall AG"
                onClose={vi.fn()}
                onEditPrice={vi.fn()}
            />
        );

        const highlightedRow = container.querySelector('tr.bg-rose-50\\/70');
        expect(highlightedRow).not.toBeNull();
        expect(highlightedRow?.textContent).toContain('Metall AG');
    });
});
