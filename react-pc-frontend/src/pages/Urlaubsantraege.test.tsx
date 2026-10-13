import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, afterEach } from 'vitest';
import Urlaubsantraege from './Urlaubsantraege';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

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
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={['/urlaubsantraege']}>
                    <Urlaubsantraege />
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>,
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

/**
 * Issue #270: Entscheidungen des Büros müssen sichtbar bestätigt oder abgelehnt
 * werden (Toast), ein Antrag lässt sich stornieren, und nirgends steht ein
 * Rohwert wie „STORNIERT" auf dem Bildschirm.
 */
describe('Urlaubsantraege - Genehmigen, Stornieren und Klartext', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    const ANTRAG_GENEHMIGT = { ...ANTRAG_OHNE_HINWEIS, id: 3, status: 'GENEHMIGT' };

    function mockFetchMitAktionen(opts: {
        antraege?: unknown[];
        aktion?: (url: string) => { status: number; body: unknown } | undefined;
    }) {
        const aufrufe: string[] = [];
        const fetchMock = vi.fn((url: string, init?: RequestInit) => {
            if (init?.method === 'PUT') {
                aufrufe.push(url);
                const antwort = opts.aktion?.(url) ?? { status: 200, body: { id: 1, status: 'GENEHMIGT' } };
                return Promise.resolve({ ok: antwort.status < 400, status: antwort.status, json: () => Promise.resolve(antwort.body) });
            }
            if (url.startsWith('/api/urlaub/antraege')) {
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(opts.antraege ?? [ANTRAG_OHNE_HINWEIS]) });
            }
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ warnungen: [] }) });
        });
        vi.stubGlobal('fetch', fetchMock);
        return { fetchMock, aufrufe };
    }

    const toastText = async (muster: RegExp) => {
        const container = await screen.findByTestId('toast-container');
        return within(container).findByText(muster);
    };

    it('zeigt beim Genehmigen die Meldung des Servers statt stillem Nichts', async () => {
        const meldung = 'Für den 03.08.2026 ist noch keine Arbeitszeit hinterlegt. Bitte zuerst Arbeitszeit zuweisen.';
        mockFetchMitAktionen({ aktion: () => ({ status: 400, body: { status: 400, message: meldung } }) });
        renderSeite();
        await screen.findByText('Musterfrau, Erika');

        fireEvent.click(screen.getByRole('button', { name: /Genehmigen/ }));
        fireEvent.click(await within(screen.getByRole('dialog')).findByRole('button', { name: 'Genehmigen' }));

        expect(await toastText(new RegExp(meldung.slice(0, 30)))).toBeInTheDocument();
    });

    it('bestätigt eine erfolgreiche Genehmigung per Toast und lädt die Liste neu', async () => {
        const { fetchMock } = mockFetchMitAktionen({});
        renderSeite();
        await screen.findByText('Musterfrau, Erika');
        const listenAufrufeVorher = fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/urlaub/antraege?')).length;

        fireEvent.click(screen.getByRole('button', { name: /Genehmigen/ }));
        fireEvent.click(await within(screen.getByRole('dialog')).findByRole('button', { name: 'Genehmigen' }));

        expect(await toastText(/Antrag genehmigt\./)).toBeInTheDocument();
        await waitFor(() => {
            const listenAufrufe = fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/urlaub/antraege?')).length;
            expect(listenAufrufe).toBe(listenAufrufeVorher + 1);
        });
    });

    it('storniert einen genehmigten Antrag erst nach Rückfrage über PUT …/storno', async () => {
        const { aufrufe } = mockFetchMitAktionen({ antraege: [ANTRAG_GENEHMIGT], aktion: () => ({ status: 200, body: { ...ANTRAG_GENEHMIGT, status: 'STORNIERT' } }) });
        renderSeite();
        await screen.findByText('Musterfrau, Erika');

        // Genehmigte Anträge haben keinen Genehmigen-/Ablehnen-Knopf mehr, aber „Stornieren".
        expect(screen.queryByRole('button', { name: /Genehmigen/ })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Stornieren' }));

        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText('Urlaubsantrag stornieren?')).toBeInTheDocument();
        expect(within(dialog).getByText(/Resturlaub steigt wieder/)).toBeInTheDocument();
        // Abbrechen → nichts passiert.
        fireEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        expect(aufrufe).toHaveLength(0);

        fireEvent.click(screen.getByRole('button', { name: 'Stornieren' }));
        fireEvent.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: 'Stornieren' }));

        await waitFor(() => expect(aufrufe).toEqual(['/api/urlaub/antraege/3/storno']));
        expect(await toastText(/Antrag storniert\./)).toBeInTheDocument();
    });

    it('fragt beim Stornieren typabhängig nach – bei einer Krankmeldung ohne Resturlaub', async () => {
        mockFetchMitAktionen({ antraege: [{ ...ANTRAG_GENEHMIGT, typ: 'KRANKHEIT' }] });
        renderSeite();
        await screen.findByText('Musterfrau, Erika');

        const stornieren = screen.getByRole('button', { name: 'Stornieren' });
        // Sekundär-Schema (rose), nicht das rote Gefahr-Rot.
        expect(stornieren.className).toContain('text-rose-700');
        expect(stornieren.className).not.toContain('text-red-600');
        fireEvent.click(stornieren);

        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText('Krankmeldung stornieren?')).toBeInTheDocument();
        expect(within(dialog).getByText(/Bereits eingetragene Tage werden aus dem Kalender gelöscht\./)).toBeInTheDocument();
        expect(within(dialog).queryByText(/Resturlaub/)).not.toBeInTheDocument();
    });

    it('zeigt beim Storno den 409-Text, wenn ein Monat festgeschrieben ist', async () => {
        const meldung = 'Der Monat 08/2026 ist bereits festgeschrieben. Bitte zuerst den Monatsabschluss zurücksetzen.';
        mockFetchMitAktionen({ antraege: [ANTRAG_GENEHMIGT], aktion: () => ({ status: 409, body: { status: 409, message: meldung } }) });
        renderSeite();
        await screen.findByText('Musterfrau, Erika');

        fireEvent.click(screen.getByRole('button', { name: 'Stornieren' }));
        fireEvent.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: 'Stornieren' }));

        expect(await toastText(/bereits festgeschrieben/)).toBeInTheDocument();
    });

    it('erklärt das Stornieren über einen Info-Knopf direkt am Knopf', async () => {
        mockFetchMitAktionen({ antraege: [ANTRAG_GENEHMIGT] });
        renderSeite();
        await screen.findByText('Musterfrau, Erika');

        fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Stornieren' }));
        const popover = await screen.findByRole('dialog', { name: 'Stornieren' });
        expect(within(popover).getByText(/Was passiert im Hintergrund\?/)).toBeInTheDocument();
        expect(within(popover).getByText(/Bei Urlaub steigt der Resturlaub/)).toBeInTheDocument();
        expect(within(popover).getByText(/Nachricht aufs Handy/)).toBeInTheDocument();
    });

    it('zeigt stornierte Anträge mit Klartext-Badge und ohne Rohwert im Leerzustand', async () => {
        const storniert = { ...ANTRAG_OHNE_HINWEIS, id: 4, status: 'STORNIERT' };
        const fetchMock = vi.fn((url: string) => {
            if (url.startsWith('/api/urlaub/antraege?status=STORNIERT')) {
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([storniert]) });
            }
            if (url.startsWith('/api/urlaub/antraege')) {
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
            }
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ warnungen: [] }) });
        });
        vi.stubGlobal('fetch', fetchMock);
        renderSeite();

        expect(await screen.findByText('Keine offenen Anträge.')).toBeInTheDocument();
        expect(screen.queryByText(/"OFFEN"/)).not.toBeInTheDocument();

        fireEvent.click(screen.getByText('Offene Anträge'));
        fireEvent.click(await screen.findByRole('option', { name: 'Stornierte Anträge' }));

        expect(await screen.findByText('Storniert')).toBeInTheDocument();
        expect(screen.queryByText('STORNIERT')).not.toBeInTheDocument();
        // Stornierte Anträge haben keine Aktionen mehr.
        expect(screen.queryByRole('button', { name: 'Stornieren' })).not.toBeInTheDocument();
    });
});

describe('Urlaubsantraege - Resturlaub mit Vorjahresrest', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('zeigt bei offenen Urlaubsanträgen den Resturlaub mit dem Rest aus dem Vorjahr, je Mitarbeiter und Jahr einmal geladen', async () => {
        const fetchMock = vi.fn((url: string) => {
            if (url.startsWith('/api/urlaub/antraege')) {
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([ANTRAG_MIT_HINWEIS, ANTRAG_OHNE_HINWEIS]) });
            }
            if (url.startsWith('/api/urlaub/resturlaub')) {
                const params = new URL(url, 'http://localhost').searchParams;
                const gefunden = params.get('mitarbeiterId') === '10';
                const body = gefunden
                    ? { verbleibend: 33.5, jahr: 2026, ausVorjahrOffen: 3.5, verfallen: 0, verfallsdatum: '2026-03-31' }
                    : { error: 'Mitarbeiter nicht gefunden' };
                return Promise.resolve({ ok: gefunden, status: gefunden ? 200 : 400, json: () => Promise.resolve(body) });
            }
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ warnungen: [] }) });
        });
        vi.stubGlobal('fetch', fetchMock);
        renderSeite();

        expect(await screen.findByText('33,5 Tage')).toBeVisible();
        expect(screen.getByText('(davon 3,5 Tage aus dem Vorjahr, verfallen am 31.03.2026)')).toBeVisible();
        const aufrufe = fetchMock.mock.calls.map(([url]) => String(url)).filter(url => url.startsWith('/api/urlaub/resturlaub'));
        expect(aufrufe).toEqual([
            '/api/urlaub/resturlaub?mitarbeiterId=10&jahr=2026',
            '/api/urlaub/resturlaub?mitarbeiterId=11&jahr=2026',
        ]);
        // Ein Fehler beim zweiten Mitarbeiter blendet nur dessen Angabe aus.
        expect(screen.getAllByText(/^Resturlaub 2026:/)).toHaveLength(1);
    });
});
