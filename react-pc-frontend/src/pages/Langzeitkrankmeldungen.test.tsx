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
