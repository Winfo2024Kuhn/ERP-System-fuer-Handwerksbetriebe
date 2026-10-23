import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { KategorieAnalyseInhalt } from './KategorieAnalyseInhalt';

// Chart.js braucht ein echtes Canvas (in jsdom nicht verfügbar) – wegmocken.
vi.mock('chart.js', () => {
    class Chart {
        destroy() {}
    }
    (Chart as unknown as { register: () => void }).register = () => {};
    return { Chart, registerables: [] };
});

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;


/**
 * Backend-Antwort mit den vom Nutzer gemeldeten Werten (anonymisiert).
 * Entscheidend: Das Backend liefert die echte OLS-Regression
 *   fixzeit = 10.91 h, steigung = 10.41 h/Einheit, R² = 0.85.
 * Zwei Aufträge (16.46 h, 34.23 h) liegen UNTER der früher angezeigten Fixzeit (56.13 h).
 */
const analyseResponse = {
    projektAnzahl: 5,
    durchschnittlicheZeit: 12.3,
    fixzeit: 10.91,
    steigung: 10.41,
    verrechnungseinheit: 'Laufende Meter',
    rQuadrat: 0.85,
    residualStdAbweichung: 24.8,
    datenpunkte: 5,
    arbeitsgangAnalysen: [
        { arbeitsgangId: 1, arbeitsgangBeschreibung: 'Montage', durchschnittStundenProEinheit: 0.47 },
        { arbeitsgangId: 2, arbeitsgangBeschreibung: 'Planung', durchschnittStundenProEinheit: 0.29 },
        { arbeitsgangId: 3, arbeitsgangBeschreibung: 'Anfertigung', durchschnittStundenProEinheit: 0.37 },
        { arbeitsgangId: 4, arbeitsgangBeschreibung: 'Technische Zeichnung', durchschnittStundenProEinheit: 0.56 },
        { arbeitsgangId: 5, arbeitsgangBeschreibung: 'Fahrzeit', durchschnittStundenProEinheit: 0.16 },
        { arbeitsgangId: 6, arbeitsgangBeschreibung: 'Aufmass', durchschnittStundenProEinheit: 0.20 },
    ],
    projekte: [
        { id: 1, auftragsnummer: '2025/11/06801', kunde: 'Max Mustermann', masseinheit: 11.5, zeitGesamt: 157.65, arbeitsgaenge: [] },
        { id: 2, auftragsnummer: '2025/04/02400', kunde: 'Erika Mustermann', masseinheit: 1.0, zeitGesamt: 34.23, arbeitsgaenge: [] },
        { id: 3, auftragsnummer: '2025/12/05304', kunde: 'Hans Beispiel', masseinheit: 0.5, zeitGesamt: 16.46, arbeitsgaenge: [] },
        { id: 4, auftragsnummer: '2026/01/01100', kunde: 'Lieschen Müller', masseinheit: 9.3, zeitGesamt: 78.38, arbeitsgaenge: [] },
        { id: 5, auftragsnummer: '2026/03/02000', kunde: 'Otto Normal', masseinheit: 4.7, zeitGesamt: 48.78, arbeitsgaenge: [] },
    ],
};

describe('KategorieAnalyseInhalt – Fixzeit/Regression', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(analyseResponse) });
    });

    /**
     * Bug: Das Frontend hat fixzeit/steigung aus dem Backend ignoriert und sich ein eigenes
     * Modell gebaut (Steigung = Summe der Arbeitsgang-Durchschnitte = 2.04, Fixzeit = Ø Restzeit).
     * Dadurch war die angezeigte Fixzeit 56.13 h – höher als die Gesamtdauer mehrerer Aufträge,
     * was logisch unmöglich ist (Fixzeit fällt immer an, kann nie > Gesamtzeit sein).
     */
    it('zeigt die Fixzeit aus der Backend-Regression statt der aufgeblaehten Restzeit-Summe', async () => {
        render(<KategorieAnalyseInhalt kategorieId={7} jahr="" />);

        // KPI + Panel-Fuß zeigen den Backend-Wert 10.91 (nicht die alten 56.13)
        await waitFor(() => expect(screen.getAllByText('10,91').length).toBeGreaterThan(0));
        expect(screen.queryByText(/56[.,]13/)).not.toBeInTheDocument();
    });

    it('haelt die Fixzeit unterhalb der kleinsten Auftragsdauer', async () => {
        render(<KategorieAnalyseInhalt kategorieId={7} jahr="" />);

        await waitFor(() => expect(screen.getAllByText('10,91').length).toBeGreaterThan(0));

        const kleinsteAuftragsdauer = Math.min(...analyseResponse.projekte.map((p) => p.zeitGesamt));
        const angezeigteFixzeit = parseFloat((screen.getAllByText('10,91')[0].textContent || '0').replace(',', '.'));
        expect(angezeigteFixzeit).toBeLessThanOrEqual(kleinsteAuftragsdauer);
    });

    it('nutzt die Regressionssteigung als variable Zeit, nicht die Arbeitsgang-Summe', async () => {
        render(<KategorieAnalyseInhalt kategorieId={7} jahr="" />);

        // Variable Zeit (Modell) = steigung 10.41 (KPI + Panel)
        await waitFor(() => expect(screen.getAllByText('10,41').length).toBeGreaterThan(0));
        // Arbeitsgang-Summe (0.47+0.29+0.37+0.56+0.16+0.20 = 2.05) bleibt als reine
        // Aufschlüsselung sichtbar, aber NICHT als Modellwert
        expect(screen.getByText('2,05')).toBeInTheDocument();
    });
});

describe('KategorieAnalyseInhalt – Zustände', () => {
    beforeEach(() => vi.clearAllMocks());

    it('fragt mit Jahr und ohne Ausreißer nach', async () => {
        mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(analyseResponse) });
        render(<KategorieAnalyseInhalt kategorieId={7} jahr="2025" />);
        await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('/api/produktkategorien/7/analyse?jahr=2025'));
        (await screen.findByRole('checkbox', { name: 'Ausreißer aus der Rechnung nehmen' })).click();
        await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('/api/produktkategorien/7/analyse?jahr=2025&ohneAusreisser=true'));
    });

    it('erklärt einen leeren Zustand statt leerer Kästen', async () => {
        mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({ ...analyseResponse, projekte: [] }) });
        render(<KategorieAnalyseInhalt kategorieId={7} jahr="" />);
        expect(await screen.findByText('Noch keine Projekte für die Zeitanalyse')).toBeInTheDocument();
    });

    it('zeigt einen fachlichen Hinweis (400) ohne Fehler-Optik', async () => {
        mockFetch.mockResolvedValueOnce({ ok: false, status: 400, json: () => Promise.resolve({ message: '„Edelstahl“ hat eine andere Einheit als der gewählte Ordner.' }) });
        render(<KategorieAnalyseInhalt kategorieId={7} jahr="" />);
        expect(await screen.findByText('„Edelstahl“ hat eine andere Einheit als der gewählte Ordner.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Erneut versuchen/ })).not.toBeInTheDocument();
    });

    it('zeigt die Fehlermeldung des Servers und lädt per „Erneut versuchen“ neu', async () => {
        mockFetch.mockResolvedValueOnce({ ok: false, status: 500, json: () => Promise.resolve({ message: 'Die Datenbank ist gerade nicht erreichbar.' }) });
        render(<KategorieAnalyseInhalt kategorieId={7} jahr="" />);
        expect(await screen.findByText('Die Datenbank ist gerade nicht erreichbar.')).toBeInTheDocument();
        mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(analyseResponse) });
        screen.getByRole('button', { name: /Erneut versuchen/ }).click();
        await waitFor(() => expect(screen.getAllByText('10,91').length).toBeGreaterThan(0));
    });

    it('meldet einen Verbindungsfehler verständlich', async () => {
        mockFetch.mockRejectedValueOnce(new TypeError('Failed to fetch'));
        render(<KategorieAnalyseInhalt kategorieId={7} jahr="" />);
        expect(await screen.findByText('Die Analyse konnte nicht geladen werden.')).toBeInTheDocument();
    });
});
