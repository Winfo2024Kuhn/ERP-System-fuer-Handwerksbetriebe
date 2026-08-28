import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WebsiteEditor } from './WebsiteEditor';

// Dieser Test deckt nur den Wechsel zwischen den zwei Reitern ab, nicht das
// Verhalten der Tab-Inhalte selbst -- das testet InsightsTab.test.tsx bereits
// vollstaendig (inklusive des echten Leerzustands bei 204 ohne Inhalt). Ein
// generischer Fetch-Stub muesste sonst fuer jeden Endpunkt jedes Tabs exakt
// das reale Antwortformat nachbilden; genau daran ist dieser Test zuvor
// gescheitert (leeres Array statt 204 fuer /website-analytics/latest hat
// einen Fehler im Insights-Tab verdeckt statt ihn zu zeigen). Deshalb wird
// InsightsTab hier durch einen Platzhalter ersetzt.
vi.mock('../components/website/InsightsTab', () => ({
    InsightsTab: () => <div data-testid="insights-tab-platzhalter" />,
}));

beforeEach(() => {
    // Fuer den Beitraege-Tab genuegt eine leere Liste als Antwort auf jede
    // Anfrage; der Insights-Tab ist oben gemockt und fragt hier nichts ab.
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
        ok: true, status: 200, json: () => Promise.resolve([]),
    })));
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('WebsiteEditor', () => {
    it('zeigt die Kopfzeile mit Kategorie und Titel', () => {
        render(<WebsiteEditor />);

        expect(screen.getByText('Website')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'NEUIGKEITEN' })).toBeInTheDocument();
    });

    it('startet auf dem Tab Beitrag erstellen', () => {
        render(<WebsiteEditor />);

        expect(screen.getByTestId('tab-beitraege')).toBeInTheDocument();
        expect(screen.queryByTestId('tab-insights')).not.toBeInTheDocument();
    });

    it('wechselt auf Insights und wieder zurueck', async () => {
        const user = userEvent.setup();
        render(<WebsiteEditor />);

        await user.click(screen.getByRole('button', { name: /Zahlen der Website/ }));
        expect(screen.getByTestId('tab-insights')).toBeInTheDocument();
        expect(screen.queryByTestId('tab-beitraege')).not.toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: /Beitrag erstellen/ }));
        expect(screen.getByTestId('tab-beitraege')).toBeInTheDocument();
    });
});
