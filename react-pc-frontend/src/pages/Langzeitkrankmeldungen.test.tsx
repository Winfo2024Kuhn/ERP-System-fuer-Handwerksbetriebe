/**
 * Vitest-Suite fuer die Desktop-Seite "Lange Krankheit".
 *
 * Prueft die Kernanforderungen aus dem Plan (Task 15, Abschnitt 5):
 * Karten rendern, Filterwechsel loest den richtigen Fetch aus, ein
 * API-Fehler zeigt einen Toast statt eines stillen console.error, der
 * 42-Tage-Hinweis erscheint in beiden Varianten (Rest-Tage bzw. ueberfaellig
 * mit "Auf Krankengeld umstellen"), und das Notiz-Label im Anlegen-Dialog
 * traegt wortgleich den DSGVO-Hinweis.
 *
 * DSGVO: ausschliesslich Dummy-Daten (Max Mustermann & Co.), keine echten
 * Namen, keine Diagnosen.
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import Langzeitkrankmeldungen from './Langzeitkrankmeldungen';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

function jsonResponse(body: unknown, status = 200): Response {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: async () => body,
    } as Response;
}

const MELDUNG_LAUFEND = {
    id: 1,
    mitarbeiterId: 10,
    mitarbeiterName: 'Mustermann, Max',
    beginn: '2026-03-01',
    ende: null,
    status: 'LAUFEND',
    statusLabel: 'Läuft noch',
    lohnfortzahlungBis: '2026-04-11',
    notiz: null,
    version: 3,
    aktuellePhaseTyp: 'LOHNFORTZAHLUNG',
    aktuellePhaseLabel: 'Lohnfortzahlung durch den Betrieb',
    restTageLohnfortzahlung: 12,
    heuteGeplanteStunden: null,
    geplanteRueckkehr: '2026-05-01',
    phasen: [],
};

const MELDUNG_UEBERFAELLIG = {
    ...MELDUNG_LAUFEND,
    id: 2,
    mitarbeiterId: 11,
    mitarbeiterName: 'Beispiel, Erika',
    lohnfortzahlungBis: '2026-03-14',
    restTageLohnfortzahlung: -3,
    geplanteRueckkehr: null,
};

function renderSeite() {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <Langzeitkrankmeldungen />
            </ConfirmProvider>
        </ToastProvider>,
    );
}

describe('Langzeitkrankmeldungen', () => {
    let fetchMock: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        fetchMock = vi.fn((input: RequestInfo | URL) => {
            const url = String(input);
            if (url.startsWith('/api/langzeitkrankmeldungen?status=')) {
                return Promise.resolve(jsonResponse([MELDUNG_LAUFEND, MELDUNG_UEBERFAELLIG]));
            }
            if (url === '/api/mitarbeiter') {
                return Promise.resolve(jsonResponse([{ id: 10, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]));
            }
            if (url === '/api/zeitverwaltung/zeitkonten') {
                return Promise.resolve(jsonResponse([]));
            }
            return Promise.resolve(jsonResponse({ error: 'Unbekannte Route im Test' }, 404));
        });
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('rendert die Karten der geladenen Krankmeldungen', async () => {
        renderSeite();

        expect(await screen.findByText('Mustermann, Max')).toBeInTheDocument();
        expect(screen.getByText('Beispiel, Erika')).toBeInTheDocument();
    });

    it('laedt beim ersten Rendern die laufenden Krankmeldungen (Default-Filter)', async () => {
        renderSeite();
        await screen.findByText('Mustermann, Max');

        expect(fetchMock).toHaveBeenCalledWith('/api/langzeitkrankmeldungen?status=LAUFEND');
    });

    it('loest bei Filterwechsel einen neuen Fetch mit dem richtigen Status aus', async () => {
        const user = userEvent.setup();
        renderSeite();
        await screen.findByText('Mustermann, Max');

        await user.click(screen.getByText('Läuft noch'));
        const dropdown = await screen.findByRole('listbox');
        await user.click(within(dropdown).getByRole('option', { name: 'Abgeschlossen' }));

        await waitFor(() =>
            expect(fetchMock).toHaveBeenCalledWith('/api/langzeitkrankmeldungen?status=BEENDET'),
        );
    });

    it('zeigt einen Toast statt eines stillen Fehlers, wenn das Laden fehlschlaegt', async () => {
        fetchMock.mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url.startsWith('/api/langzeitkrankmeldungen?status=')) {
                return Promise.resolve(jsonResponse({ error: 'Serverfehler' }, 500));
            }
            return Promise.resolve(jsonResponse([]));
        });

        renderSeite();

        // Toast UND eigener Fehlerzustand zeigen bewusst denselben Sachverhalt --
        // gezielt auf den Toast pruefen (die eine Stelle, die garantiert
        // gerendert wird, sobald der Fehler passiert ist), nicht auf beide Treffer stossen.
        const toastContainer = await screen.findByTestId('toast-container');
        expect(within(toastContainer).getByText(/nicht geladen werden/i)).toBeInTheDocument();
    });

    it('zeigt "Noch N Tage Lohnfortzahlung" bei positiven Resttagen', async () => {
        renderSeite();
        await screen.findByText('Mustermann, Max');

        expect(screen.getByText('Noch 12 Tage Lohnfortzahlung')).toBeInTheDocument();
    });

    // Nachbesserung Abschnitt 5, Befund 1 (BLOCKER): gestempelte/geplante
    // Stunden im Stufenplan-Vergleich muessen mit deutschem Komma erscheinen
    // ("4,5 h"), nicht mit englischem Punkt.
    it('zeigt gestempelte und geplante Stunden mit deutschem Komma statt englischem Punkt', async () => {
        const user = userEvent.setup();
        fetchMock.mockImplementation((input: RequestInfo | URL) => {
            const url = String(input);
            if (url.startsWith('/api/langzeitkrankmeldungen?status=')) {
                return Promise.resolve(jsonResponse([MELDUNG_LAUFEND]));
            }
            if (url === '/api/langzeitkrankmeldungen/1') {
                return Promise.resolve(jsonResponse({
                    ...MELDUNG_LAUFEND,
                    stufenplanTage: [
                        { datum: '2026-04-01', geplanteStunden: 4.5, gestempelteStunden: 5.5, ueberPlan: true },
                    ],
                }));
            }
            if (url === '/api/mitarbeiter') return Promise.resolve(jsonResponse([]));
            if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(jsonResponse([]));
            return Promise.resolve(jsonResponse({ error: 'Unbekannte Route im Test' }, 404));
        });

        renderSeite();
        await screen.findByText('Mustermann, Max');
        await user.click(screen.getByRole('button', { name: 'Details anzeigen' }));

        expect(await screen.findByText('5,5 h gestempelt, 4,5 h geplant')).toBeInTheDocument();
        expect(screen.queryByText(/5\.5 h gestempelt/)).not.toBeInTheDocument();
    });

    it('zeigt bei ueberschrittener Lohnfortzahlung das Enddatum und den Umstellen-Knopf', async () => {
        renderSeite();
        await screen.findByText('Beispiel, Erika');

        expect(screen.getByText(/Lohnfortzahlung endete am 14\.03\.2026/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Auf Krankengeld umstellen/ })).toBeInTheDocument();
    });

    // Nachbesserung Abschnitt 5, Befund 3 (BLOCKER-nah): sobald status !==
    // 'LAUFEND' rendert die Karte gar keine Knoepfe mehr -- ein Fehlklick auf
    // "Wieder voll im Einsatz" liess sich dann nicht mehr rueckgaengig machen.
    // "Doch noch krank" ruft PUT .../{id}/oeffnen und erscheint NUR bei
    // status === 'BEENDET' (nicht bei LAUFEND und -- wie im Befund gefordert
    // -- nicht bei ABGEBROCHEN, das lehnt der Endpunkt laut Backend ohnehin ab).
    const MELDUNG_BEENDET = {
        ...MELDUNG_LAUFEND,
        id: 3,
        mitarbeiterId: 12,
        mitarbeiterName: 'Beispiel, Klaus',
        status: 'BEENDET' as const,
        statusLabel: 'Abgeschlossen',
        version: 5,
        aktuellePhaseTyp: null,
        aktuellePhaseLabel: 'Keine aktuelle Phase',
    };

    function stubMitDetailUndOeffnen(zusatz: Record<string, unknown> = {}) {
        fetchMock.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input);
            if (url.startsWith('/api/langzeitkrankmeldungen?status=')) {
                return Promise.resolve(jsonResponse([MELDUNG_LAUFEND, MELDUNG_BEENDET]));
            }
            if (url === '/api/langzeitkrankmeldungen/1') {
                return Promise.resolve(jsonResponse({ ...MELDUNG_LAUFEND, stufenplanTage: [] }));
            }
            if (url === '/api/langzeitkrankmeldungen/3') {
                return Promise.resolve(jsonResponse({ ...MELDUNG_BEENDET, stufenplanTage: [] }));
            }
            if (url === '/api/langzeitkrankmeldungen/3/oeffnen?version=5' && init?.method === 'PUT') {
                return Promise.resolve(jsonResponse(zusatz.oeffnenAntwort ?? { ...MELDUNG_BEENDET, status: 'LAUFEND' }, (zusatz.oeffnenStatus as number) ?? 200));
            }
            if (url === '/api/mitarbeiter') return Promise.resolve(jsonResponse([]));
            if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(jsonResponse([]));
            return Promise.resolve(jsonResponse({ error: 'Unbekannte Route im Test' }, 404));
        });
    }

    it('zeigt "Doch noch krank" nur bei einer abgeschlossenen Meldung, nicht bei einer laufenden', async () => {
        const user = userEvent.setup();
        stubMitDetailUndOeffnen();
        renderSeite();
        await screen.findByText('Mustermann, Max');

        const karteLaufend = screen.getByText('Mustermann, Max').closest('[data-meldung-id]') as HTMLElement;
        await user.click(within(karteLaufend).getByRole('button', { name: 'Details anzeigen' }));
        expect(within(karteLaufend).queryByRole('button', { name: 'Doch noch krank' })).not.toBeInTheDocument();

        const karteBeendet = screen.getByText('Beispiel, Klaus').closest('[data-meldung-id]') as HTMLElement;
        await user.click(within(karteBeendet).getByRole('button', { name: 'Details anzeigen' }));
        expect(await within(karteBeendet).findByRole('button', { name: 'Doch noch krank' })).toBeInTheDocument();
        // Bei BEENDET gibt es keine aktuelle Phase (Befund 4) -- das Badge zeigt
        // trotzdem einen sauberen Fallback statt einer "undefined"-Klasse.
        expect(within(karteBeendet).getByText('Keine aktuelle Phase').className).not.toContain('undefined');
    });

    it('"Doch noch krank" ruft PUT .../oeffnen mit der Version und laedt die Liste neu', async () => {
        const user = userEvent.setup();
        stubMitDetailUndOeffnen();
        renderSeite();
        await screen.findByText('Beispiel, Klaus');

        const karteBeendet = screen.getByText('Beispiel, Klaus').closest('[data-meldung-id]') as HTMLElement;
        await user.click(within(karteBeendet).getByRole('button', { name: 'Details anzeigen' }));
        await user.click(await within(karteBeendet).findByRole('button', { name: 'Doch noch krank' }));

        await waitFor(() =>
            expect(fetchMock).toHaveBeenCalledWith('/api/langzeitkrankmeldungen/3/oeffnen?version=5', { method: 'PUT' }),
        );
        const toastContainer = await screen.findByTestId('toast-container');
        expect(within(toastContainer).getByText(/wieder geöffnet/i)).toBeInTheDocument();
    });

    it('zeigt bei "Doch noch krank" die Server-Meldung im Toast, statt still zu scheitern', async () => {
        const user = userEvent.setup();
        stubMitDetailUndOeffnen({
            oeffnenAntwort: { error: 'Eine zurückgenommene Krankmeldung lässt sich nicht wieder öffnen. Bitte neu anlegen.' },
            oeffnenStatus: 400,
        });
        renderSeite();
        await screen.findByText('Beispiel, Klaus');

        const karteBeendet = screen.getByText('Beispiel, Klaus').closest('[data-meldung-id]') as HTMLElement;
        await user.click(within(karteBeendet).getByRole('button', { name: 'Details anzeigen' }));
        await user.click(await within(karteBeendet).findByRole('button', { name: 'Doch noch krank' }));

        const toastContainer = await screen.findByTestId('toast-container');
        expect(within(toastContainer).getByText(/Bitte neu anlegen/)).toBeInTheDocument();
    });

    it('traegt im Notiz-Label des Anlegen-Dialogs wortgleich den Diagnose-Hinweis', async () => {
        const user = userEvent.setup();
        renderSeite();
        await screen.findByText('Mustermann, Max');

        await user.click(screen.getByRole('button', { name: 'Krankmeldung anlegen' }));

        expect(
            await screen.findByText('Interne Notiz — bitte keine Diagnosen eintragen'),
        ).toBeInTheDocument();
    });
});

/**
 * Issue #270: Das Tageslimit im Stufenplan kommt aus `aktuell.arbeitszeit` des
 * Zeitkontos (nicht flach), und „Auf Krankengeld umstellen" fragt den Beginn ab.
 */
describe('Langzeitkrankmeldungen - Stufenplan-Limit und Krankengeld-Beginn (#270)', () => {
    let fetchMock: ReturnType<typeof vi.fn>;
    beforeEach(() => {
        fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
    });
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    const arbeitszeit = (stunden: number) => ({
        montagStunden: stunden, dienstagStunden: stunden, mittwochStunden: stunden, donnerstagStunden: stunden,
        freitagStunden: stunden - 0.5, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: '07:00', buchungEndeZeit: '16:00',
    });
    const zeitkonto = (mitarbeiterId: number, aktuell: unknown) => ({
        mitarbeiterId, mitarbeiterVersion: 1, mitarbeiterName: 'Mustermann, Max', fuehrtZeitkonto: true, eingerichtet: aktuell !== null,
        hinweis: null, aktuell, letzteVersion: aktuell, historie: aktuell ? [aktuell] : [],
    });

    function stubMitZeitkonten(zeitkonten: unknown[], phasenAntwort?: { status: number; body: unknown }) {
        const postBodies: unknown[] = [];
        fetchMock.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input);
            if (url.startsWith('/api/langzeitkrankmeldungen?status=')) {
                return Promise.resolve(jsonResponse([MELDUNG_LAUFEND, MELDUNG_UEBERFAELLIG]));
            }
            if (url === '/api/langzeitkrankmeldungen/1' || url === '/api/langzeitkrankmeldungen/2') {
                const meldung = url.endsWith('/1') ? MELDUNG_LAUFEND : MELDUNG_UEBERFAELLIG;
                return Promise.resolve(jsonResponse({ ...meldung, stufenplanTage: [] }));
            }
            if (url.startsWith('/api/langzeitkrankmeldungen/2/phasen?version=3') && init?.method === 'POST') {
                postBodies.push(JSON.parse(String(init.body)));
                const antwort = phasenAntwort ?? { status: 201, body: { id: 9 } };
                return Promise.resolve(jsonResponse(antwort.body, antwort.status));
            }
            if (url === '/api/mitarbeiter') return Promise.resolve(jsonResponse([]));
            if (url === '/api/zeitverwaltung/zeitkonten') return Promise.resolve(jsonResponse(zeitkonten));
            return Promise.resolve(jsonResponse({ error: 'Unbekannte Route im Test' }, 404));
        });
        return postBodies;
    }

    it('liest das Tageslimit aus aktuell.arbeitszeit statt „max. 0" zu zeigen', async () => {
        const user = userEvent.setup();
        stubMitZeitkonten([zeitkonto(10, { id: 1, version: 1, mitarbeiterId: 10, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: null, arbeitszeit: arbeitszeit(7.5) })]);
        renderSeite();
        await screen.findByText('Mustermann, Max');

        const karte = screen.getByText('Mustermann, Max').closest('[data-meldung-id]') as HTMLElement;
        await user.click(within(karte).getByRole('button', { name: 'Details anzeigen' }));

        expect(await within(karte).findByPlaceholderText('max. 7,5')).toBeInTheDocument();
        expect(within(karte).queryByPlaceholderText('max. 0')).not.toBeInTheDocument();
    });

    it('fällt ohne eingerichtete Arbeitszeit (aktuell = null) auf 8 Stunden zurück', async () => {
        const user = userEvent.setup();
        stubMitZeitkonten([zeitkonto(10, null)]);
        renderSeite();
        await screen.findByText('Mustermann, Max');

        const karte = screen.getByText('Mustermann, Max').closest('[data-meldung-id]') as HTMLElement;
        await user.click(within(karte).getByRole('button', { name: 'Details anzeigen' }));

        expect(await within(karte).findByPlaceholderText('max. 8')).toBeInTheDocument();
    });

    it('öffnet beim Umstellen einen Dialog mit dem Tag nach Ende der Lohnfortzahlung als Vorgabe', async () => {
        const user = userEvent.setup();
        const postBodies = stubMitZeitkonten([]);
        renderSeite();
        await screen.findByText('Beispiel, Erika');

        await user.click(screen.getByRole('button', { name: /Auf Krankengeld umstellen/ }));

        const dialog = (await screen.findByRole('heading', { name: 'Auf Krankengeld umstellen' })).closest('[role="dialog"]') as HTMLElement;
        // Lohnfortzahlung bis 14.03.2026 → Vorgabe 15.03.2026
        expect(within(dialog).getByText(/Lohnfortzahlung endete am 14\.03\.2026/)).toBeInTheDocument();
        expect(within(dialog).getByText('15.03.2026')).toBeInTheDocument();
        // Noch nichts gesendet, bis „Umstellen" bestätigt wird.
        expect(postBodies).toHaveLength(0);

        // Info-Knopf erklärt, dass die Lohnfortzahlung automatisch endet.
        await user.click(within(dialog).getByRole('button', { name: 'Mehr zu: Krankengeld ab' }));
        const info = await screen.findByRole('dialog', { name: 'Krankengeld ab' });
        expect(within(info).getByText(/endet automatisch am Tag vor dem gewählten Beginn/)).toBeInTheDocument();
        await user.keyboard('{Escape}');

        await user.click(within(dialog).getByRole('button', { name: 'Umstellen' }));

        await waitFor(() => expect(postBodies).toHaveLength(1));
        expect(postBodies[0]).toEqual({ typ: 'KRANKENGELD', vonDatum: '2026-03-15', bisDatum: null, stundenProTag: null });
        const toastContainer = await screen.findByTestId('toast-container');
        expect(within(toastContainer).getByText('Auf Krankengeld umgestellt.')).toBeInTheDocument();
    });

    it('zeigt beim Umstellen die Server-Meldung im Toast', async () => {
        const user = userEvent.setup();
        stubMitZeitkonten([], { status: 400, body: { error: 'Der Beginn liegt vor dem Beginn der Krankmeldung.' } });
        renderSeite();
        await screen.findByText('Beispiel, Erika');

        await user.click(screen.getByRole('button', { name: /Auf Krankengeld umstellen/ }));
        const dialog = (await screen.findByRole('heading', { name: 'Auf Krankengeld umstellen' })).closest('[role="dialog"]') as HTMLElement;
        await user.click(within(dialog).getByRole('button', { name: 'Umstellen' }));

        const toastContainer = await screen.findByTestId('toast-container');
        expect(await within(toastContainer).findByText('Der Beginn liegt vor dem Beginn der Krankmeldung.')).toBeInTheDocument();
        // Der Dialog bleibt offen, damit das Datum korrigiert werden kann.
        expect(screen.getByRole('heading', { name: 'Auf Krankengeld umstellen' })).toBeInTheDocument();
    });
});
