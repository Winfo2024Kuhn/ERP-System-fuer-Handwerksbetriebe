import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WebsiteEditor } from './WebsiteEditor';

// Dieser Test deckt nur den Wechsel zwischen den zwei Reitern ab, nicht das
// Verhalten der Tab-Inhalte selbst -- dafuer gibt es InsightsTab.test.tsx und
// BeitraegeTab.test.tsx, die beide vollstaendig sind.
//
// Beide Tabs werden deshalb durch Platzhalter ersetzt, aus zwei Gruenden:
//
// Erstens muesste ein generischer Fetch-Stub sonst fuer jeden Endpunkt jedes
// Tabs exakt das reale Antwortformat nachbilden. Genau daran ist dieser Test
// schon einmal gescheitert: ein leeres Array statt 204 fuer
// /website-analytics/latest hat einen Fehler im Insights-Tab verdeckt statt
// ihn zu zeigen.
//
// Zweitens braeuchten die echten Tabs hier ihre Rahmen-Komponenten
// (ConfirmProvider, ToastProvider), die im Programm nur App.tsx an der Wurzel
// liefert. Die hier nachzubauen hiesse, die halbe Anwendung zu montieren, um
// zwei Knoepfe zu pruefen.
vi.mock('../components/website/InsightsTab', () => ({
    InsightsTab: () => <div data-testid="insights-tab-platzhalter" />,
}));

vi.mock('../components/website/BeitraegeTab', () => ({
    BeitraegeTab: () => <div data-testid="beitraege-tab-platzhalter" />,
}));

beforeEach(() => {
    // Beide Tabs sind gemockt und fragen hier nichts ab. Der Stub steht nur,
    // damit ein unerwarteter Aufruf nicht als echter Netzwerkzugriff endet.
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
