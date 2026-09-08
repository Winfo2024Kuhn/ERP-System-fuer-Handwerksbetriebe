import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, afterEach } from 'vitest';
import Urlaubsantraege from './Urlaubsantraege';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

/**
 * Task 17 (Abschnitt 5, Langzeitkrankmeldung): Hinweis auf der
 * Urlaubsanträge-Seite, wenn sich ein offener Antrag mit einer laufenden
 * Krankmeldung desselben Mitarbeiters überschneidet.
 *
 * Endpunkt: GET /api/langzeitkrankmeldungen/urlaubs-hinweise?mitarbeiterId=&von=&bis=
 * (nicht /api/urlaub/antraege/hinweise — der alte Pfad lag auf der
 * permitAll-Kette der Zeiterfassungs-App und hätte Gesundheitsdaten ohne
 * Login preisgegeben, siehe UrlaubsantragController.java).
 *
 * DSGVO: Testdaten sind ausschließlich Dummy-Namen (Max Mustermann, Erika
 * Musterfrau). Der Hinweistext selbst kommt 1:1 vom Server — die Seite baut
 * nichts dazu und holt keine weiteren Details (Diagnose, Phase, Notiz) nach.
 */

const ANTRAG_MIT_HINWEIS = {
    id: 1,
    mitarbeiterName: 'Max Mustermann',
    mitarbeiter: { id: 10, vorname: 'Max', nachname: 'Mustermann', jahresUrlaub: 30 },
    vonDatum: '2026-03-10',
    bisDatum: '2026-03-20',
    bemerkung: null,
    status: 'OFFEN',
    erstellDatum: '2026-02-01',
    typ: 'URLAUB',
};

const ANTRAG_OHNE_HINWEIS = {
    id: 2,
    mitarbeiterName: 'Erika Musterfrau',
    mitarbeiter: { id: 11, vorname: 'Erika', nachname: 'Musterfrau', jahresUrlaub: 28 },
    vonDatum: '2026-04-01',
    bisDatum: '2026-04-05',
    bemerkung: null,
    status: 'OFFEN',
    erstellDatum: '2026-02-01',
    typ: 'URLAUB',
};

const WARNTEXT = 'In diesem Zeitraum läuft eine Krankmeldung (seit 01.03.2026). Bitte prüfen, ob der Urlaub wirklich passt.';

function renderSeite() {
    return render(
        <ConfirmProvider>
            <MemoryRouter initialEntries={['/urlaubsantraege']}>
                <Urlaubsantraege />
            </MemoryRouter>
        </ConfirmProvider>,
    );
}

/** Baut einen fetch-Mock, der die Antragsliste und die Hinweis-Abfrage bedient. */
function mockFetch(opts: {
    antraege?: unknown[];
    hinweise?: (mitarbeiterId: string) => { ok: boolean; warnungen?: string[] };
}) {
    const antraege = opts.antraege ?? [ANTRAG_MIT_HINWEIS, ANTRAG_OHNE_HINWEIS];
    return vi.fn((url: string) => {
        if (url.startsWith('/api/urlaub/antraege')) {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(antraege) });
        }
        if (url.startsWith('/api/langzeitkrankmeldungen/urlaubs-hinweise')) {
            const mitarbeiterId = new URL(url, 'http://localhost').searchParams.get('mitarbeiterId') ?? '';
            const ergebnis = opts.hinweise?.(mitarbeiterId) ?? { ok: true, warnungen: [] };
            if (!ergebnis.ok) {
                return Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) });
            }
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ warnungen: ergebnis.warnungen ?? [] }) });
        }
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
    });
}

describe('Urlaubsantraege - Hinweis bei laufender Krankmeldung', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('zeigt den Warnhinweis-Kasten nur beim Antrag, dessen Zeitraum in eine laufende Krankmeldung fällt', async () => {
        const fetchMock = mockFetch({
            hinweise: (mitarbeiterId) => mitarbeiterId === '10' ? { ok: true, warnungen: [WARNTEXT] } : { ok: true, warnungen: [] },
        });
        vi.stubGlobal('fetch', fetchMock);

        const { container } = renderSeite();

        expect(await screen.findByText('Mustermann, Max')).toBeInTheDocument();
        expect(screen.getByText('Musterfrau, Erika')).toBeInTheDocument();

        await waitFor(() => {
            expect(screen.getByText(WARNTEXT)).toBeInTheDocument();
        });

        const karteMitHinweis = container.querySelector('[data-antrag-id="1"]') as HTMLElement;
        const karteOhneHinweis = container.querySelector('[data-antrag-id="2"]') as HTMLElement;
        expect(within(karteMitHinweis).getByText(WARNTEXT)).toBeInTheDocument();
        expect(within(karteOhneHinweis).queryByText(WARNTEXT)).not.toBeInTheDocument();

        // Bündelt die Abfragen statt einer nach der anderen (kriterien.md,
        // Performance: kein Fetch-Wasserfall) -- für 2 offene Anträge genau
        // 1 Aufruf der Antragsliste + 2 Aufrufe des Hinweis-Endpunkts.
        const hinweisAufrufe = fetchMock.mock.calls.filter(([url]) =>
            String(url).startsWith('/api/langzeitkrankmeldungen/urlaubs-hinweise'));
        expect(hinweisAufrufe.length).toBe(2);
    });

    it('zeigt gar keinen Kasten, wenn die Warnungsliste für alle Anträge leer ist', async () => {
        const fetchMock = mockFetch({ hinweise: () => ({ ok: true, warnungen: [] }) });
        vi.stubGlobal('fetch', fetchMock);

        renderSeite();

        expect(await screen.findByText('Mustermann, Max')).toBeInTheDocument();
        await waitFor(() => expect(fetchMock).toHaveBeenCalled());
        expect(screen.queryByText(/Krankmeldung/)).not.toBeInTheDocument();
    });

    it('"Genehmigen" bleibt trotz Warnhinweis anklickbar -- eine Warnung ist keine Sperre', async () => {
        const fetchMock = mockFetch({
            hinweise: (mitarbeiterId) => mitarbeiterId === '10' ? { ok: true, warnungen: [WARNTEXT] } : { ok: true, warnungen: [] },
        });
        vi.stubGlobal('fetch', fetchMock);

        const { container } = renderSeite();
        await waitFor(() => expect(screen.getByText(WARNTEXT)).toBeInTheDocument());

        const karte = container.querySelector('[data-antrag-id="1"]') as HTMLElement;
        const genehmigenKnopf = within(karte).getByRole('button', { name: /Genehmigen/ });
        expect(genehmigenKnopf).toBeEnabled();
    });

    it('ein Fehler bei der Hinweis-Abfrage lässt die Antragsliste stehen und zeigt keinen Kasten', async () => {
        const fetchMock = mockFetch({ hinweise: () => ({ ok: false }) });
        vi.stubGlobal('fetch', fetchMock);

        renderSeite();

        expect(await screen.findByText('Mustermann, Max')).toBeInTheDocument();
        expect(screen.getByText('Musterfrau, Erika')).toBeInTheDocument();

        await waitFor(() => {
            const hinweisAufrufe = fetchMock.mock.calls.filter(([url]) =>
                String(url).startsWith('/api/langzeitkrankmeldungen/urlaubs-hinweise'));
            expect(hinweisAufrufe.length).toBe(2);
        });

        expect(screen.queryByText(/Krankmeldung/)).not.toBeInTheDocument();
        // Kein Kasten heißt nicht "kaputte Seite" -- die Anträge bleiben da,
        // beide "Genehmigen"-Knöpfe bleiben klickbar.
        for (const knopf of screen.getAllByRole('button', { name: /Genehmigen/ })) {
            expect(knopf).toBeEnabled();
        }
    });
});
