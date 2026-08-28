import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { InsightsTab } from './InsightsTab';

// jsdom hat kein Canvas, deshalb die Diagramm-Komponente ersetzen.
vi.mock('react-chartjs-2', () => ({
    Line: () => <div data-testid="verlauf-diagramm" />,
}));

let fetchMock: ReturnType<typeof vi.fn>;

const schnappschuss = {
    schemaVersion: 1,
    snapshotDate: '2026-08-27',
    generatedAt: '2026-08-27T23:55:00',
    receivedAt: '2026-08-28T00:05:00',
    totals: { visitors: 1200, pageviews: 3400, leadsPhone: 8, leadsMail: 5, submissions: 21 },
    visitorsToday: 42,
    visitorsYesterday: 30,
    conversion: 3,
    funnel: [{ name: 'start', label: 'Startseite', count: 100 }],
    topPages: [{ path: '/leistungen/gelaender/', count: 55 }],
    devices: [{ device: 'Handy', count: 70 }],
    browsers: [{ browser: 'Chrome', count: 60 }],
    cities: [{ city: 'Würzburg', country: 'DE', count: 30 }],
};

const verlauf = [
    { snapshotDate: '2026-08-26', besucherAmTag: 30, besucherGesamt: 1170, seitenaufrufeGesamt: 3300, anfragenGesamt: 20, conversion: 3 },
    { snapshotDate: '2026-08-27', besucherAmTag: 42, besucherGesamt: 1200, seitenaufrufeGesamt: 3400, anfragenGesamt: 21, conversion: 3 },
];

/** Antwortet je nach URL mit Schnappschuss oder Verlauf. */
function antworteMit(schnapp: unknown | null, punkte: unknown[]) {
    return vi.fn((url: string) => {
        if (url.includes('/verlauf')) {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(punkte) });
        }
        if (schnapp === null) {
            return Promise.resolve({ ok: true, status: 204, json: () => Promise.reject(new Error('leer')) });
        }
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(schnapp) });
    });
}

describe('InsightsTab', () => {
    beforeEach(() => {
        fetchMock = antworteMit(schnappschuss, verlauf);
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('zeigt die Besucher von heute und den Vergleich zu gestern', async () => {
        render(<InsightsTab />);

        expect(await screen.findByText('42')).toBeInTheDocument();
        expect(screen.getByText('Besucher heute')).toBeInTheDocument();
        // 42 gegen 30 sind 12 mehr.
        expect(screen.getByText(/12 mehr als gestern/)).toBeInTheDocument();
    });

    it('nennt die Conversion in Handwerker-Sprache', async () => {
        render(<InsightsTab />);

        expect(await screen.findByText('Anfragen je 100 Besucher')).toBeInTheDocument();
        expect(screen.queryByText(/Conversion/i)).not.toBeInTheDocument();
    });

    it('zeichnet den Verlauf', async () => {
        render(<InsightsTab />);

        expect(await screen.findByTestId('verlauf-diagramm')).toBeInTheDocument();
    });

    it('lädt den Verlauf neu, wenn der Zeitraum wechselt', async () => {
        const user = userEvent.setup();
        render(<InsightsTab />);
        await screen.findByTestId('verlauf-diagramm');

        await user.click(screen.getByRole('button', { name: '90 Tage' }));

        await waitFor(() => {
            const urls = fetchMock.mock.calls.map((c: unknown[]) => c[0] as string);
            expect(urls.some(u => u.includes('tage=90'))).toBe(true);
        });
    });

    it('zeigt die Listen mit Seiten, Geräten, Browsern und Städten', async () => {
        render(<InsightsTab />);

        expect(await screen.findByText('/leistungen/gelaender/')).toBeInTheDocument();
        expect(screen.getByText('Handy')).toBeInTheDocument();
        expect(screen.getByText('Chrome')).toBeInTheDocument();
        expect(screen.getByText('Würzburg')).toBeInTheDocument();
    });

    it('erklärt den Leerzustand, wenn noch nie Zahlen ankamen', async () => {
        fetchMock = antworteMit(null, []);
        vi.stubGlobal('fetch', fetchMock);

        render(<InsightsTab />);

        expect(await screen.findByText(/Noch keine Zahlen von der Website/)).toBeInTheDocument();
    });

    it('meldet einen Fehlschlag, statt leer zu bleiben', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
            ok: false, status: 500, json: () => Promise.resolve({ message: 'Kaputt.' }),
        })));

        render(<InsightsTab />);

        expect(await screen.findByText(/Die Zahlen konnten nicht geladen werden/)).toBeInTheDocument();
    });
});
