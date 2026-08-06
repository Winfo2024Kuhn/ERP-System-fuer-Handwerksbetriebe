import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import ArtikelEditor from './ArtikelEditor';

// PAGE_SIZE im ArtikelEditor ist 12 – bei 40 Treffern also 4 Seiten.
const PAGE_SIZE = 12;
const GESAMT = 40;

// DSGVO: ausschließlich Dummy-Daten in Tests.
function dummyArtikel(anzahl: number) {
    return Array.from({ length: anzahl }, (_, i) => ({
        id: i + 1,
        artikelnummer: `A-${1000 + i}`,
        produktname: `Musterprofil ${i + 1}`,
        werkstoffName: 'S235JR',
        kgProMeter: 1.5,
        preis: 9.99,
    }));
}

let fetchMock: ReturnType<typeof vi.fn>;
// Über diese Variable lässt sich im Test simulieren, dass die Trefferzahl schrumpft.
let gesamtTreffer = GESAMT;

function mockArtikelApi() {
    return vi.fn((url: string) => {
        const antwort = (daten: unknown) => Promise.resolve({ ok: true, json: () => Promise.resolve(daten) });

        if (typeof url !== 'string') return antwort({});
        if (url.startsWith('/api/artikel/werkstoffe')) return antwort(['S235JR', 'S355J2']);
        if (url.startsWith('/api/artikel/filteroptionen')) {
            return antwort({ herstellverfahren: [], fertigungszustand: [] });
        }
        if (url.startsWith('/api/artikel?')) {
            const params = new URLSearchParams(url.substring(url.indexOf('?') + 1));
            const seite = Number(params.get('page') ?? 0);
            const verbleibend = Math.max(0, gesamtTreffer - seite * PAGE_SIZE);
            return antwort({
                artikel: dummyArtikel(Math.min(PAGE_SIZE, verbleibend)),
                gesamt: gesamtTreffer,
            });
        }
        return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
    });
}

function listenRequests(): string[] {
    return fetchMock.mock.calls
        .map((call: unknown[]) => call[0])
        .filter((url: unknown): url is string => typeof url === 'string' && url.startsWith('/api/artikel?'));
}

/** Query-Parameter des zuletzt abgesetzten Listen-Requests. */
function letzterListenRequest(): URLSearchParams {
    const alle = listenRequests();
    const letzte = alle[alle.length - 1];
    return new URLSearchParams(letzte.substring(letzte.indexOf('?') + 1));
}

/** Aktuelle Adresse und Navigations-State, damit Tests beides pruefen koennen. */
let aktuelleAdresse = '';
let aktuellerNavigationsState: unknown = null;

function AdressSpion() {
    const location = useLocation();
    aktuelleAdresse = location.pathname + location.search;
    aktuellerNavigationsState = location.state;
    return null;
}

function renderArtikelEditor(startAdresse = '/artikel') {
    return render(
        <MemoryRouter initialEntries={[startAdresse]}>
            <ConfirmProvider>
                <ToastProvider>
                    <AdressSpion />
                    <Routes>
                        <Route path="/artikel" element={<ArtikelEditor />} />
                        <Route path="/artikel/:id" element={<div>Detailseite</div>} />
                    </Routes>
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

describe('ArtikelEditor – Pagination und Filter', () => {
    beforeEach(() => {
        gesamtTreffer = GESAMT;
        aktuelleAdresse = '';
        aktuellerNavigationsState = null;
        fetchMock = mockArtikelApi();
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('springt bei neuem Suchbegriff zurück auf die erste Seite', async () => {
        const user = userEvent.setup();
        renderArtikelEditor();

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('0'));

        await user.click(screen.getByRole('button', { name: /weiter/i }));
        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('1'));

        await user.type(screen.getByPlaceholderText('Name, Nummer...'), 'm');

        await waitFor(() => {
            const request = letzterListenRequest();
            expect(request.get('q')).toBe('m');
            expect(request.get('page')).toBe('0');
        });
    });

    it('springt bei Sortierwechsel zurück auf die erste Seite', async () => {
        const user = userEvent.setup();
        renderArtikelEditor();

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('0'));

        await user.click(screen.getByRole('button', { name: /weiter/i }));
        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('1'));

        await user.click(screen.getByRole('columnheader', { name: /Bester Preis/i }));

        await waitFor(() => {
            const request = letzterListenRequest();
            expect(request.get('sort')).toBe('preis');
            expect(request.get('page')).toBe('0');
        });
    });

    it('springt auf die letzte gültige Seite, wenn die Trefferzahl schrumpft', async () => {
        const user = userEvent.setup();
        renderArtikelEditor();

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('0'));

        const weiter = () => screen.getByRole('button', { name: /weiter/i });
        await user.click(weiter());
        await user.click(weiter());
        await user.click(weiter());
        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('3'));

        // Ab jetzt liefert das Backend nur noch 20 Treffer – also zwei Seiten (0 und 1).
        gesamtTreffer = 20;
        await user.click(screen.getByRole('button', { name: /Aktualisieren/i }));

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('1'));
        expect(screen.getByText('Musterprofil 1')).toBeInTheDocument();

        // Der Guard darf sich nicht selbst nachtriggern. Erwartet sind 6 Requests
        // (Start, 3x Weiter, Aktualisieren, Korrektur-Sprung); eine Endlosschleife
        // würde hier sofort auffallen.
        await act(async () => {
            await Promise.resolve();
        });
        expect(listenRequests().length).toBeLessThanOrEqual(8);
    });

    it('zeigt keinen Filtern-Button, weil live gefiltert wird', async () => {
        renderArtikelEditor();

        await waitFor(() => expect(listenRequests()).toHaveLength(1));

        expect(screen.queryByRole('button', { name: 'Filtern' })).not.toBeInTheDocument();
    });

    it('lädt bei Enter im Suchfeld nicht die Seite neu', async () => {
        const user = userEvent.setup();
        renderArtikelEditor();

        await waitFor(() => expect(listenRequests()).toHaveLength(1));

        // Ohne abgefangenes submit würde der Browser das Formular abschicken und
        // die Seite neu laden – in jsdom ein "Not implemented: navigation"-Fehler.
        await user.type(screen.getByPlaceholderText('Name, Nummer...'), '{Enter}');
        await act(async () => {
            await Promise.resolve();
        });

        expect(listenRequests()).toHaveLength(1);
    });

    it('verwirft die verspätete Antwort auf einen veralteten Suchbegriff', async () => {
        const user = userEvent.setup();
        // Die Antwort auf "a" wird künstlich zurückgehalten, damit sie erst nach
        // der Antwort auf "ab" eintrifft – der Fall, der beim schnellen Tippen auftritt.
        const verspaeteteAntworten: Array<() => void> = [];
        const basisMock = mockArtikelApi();

        fetchMock = vi.fn((url: string) => {
            if (typeof url !== 'string' || !url.startsWith('/api/artikel?')) return basisMock(url);

            const suchbegriff = new URLSearchParams(url.substring(url.indexOf('?') + 1)).get('q');
            const antwortFuer = (artikel: ReturnType<typeof dummyArtikel>) => ({
                ok: true,
                json: () => Promise.resolve({ artikel, gesamt: artikel.length }),
            });

            if (suchbegriff === 'a') {
                const veraltet = [{ ...dummyArtikel(1)[0], id: 101, produktname: 'Alter Treffer' }];
                return new Promise(resolve => {
                    verspaeteteAntworten.push(() => resolve(antwortFuer(veraltet)));
                });
            }
            if (suchbegriff === 'ab') {
                return Promise.resolve(
                    antwortFuer([{ ...dummyArtikel(1)[0], id: 102, produktname: 'Neuer Treffer' }])
                );
            }
            return basisMock(url);
        });
        global.fetch = fetchMock as unknown as typeof fetch;

        renderArtikelEditor();
        await waitFor(() => expect(screen.getByText('Musterprofil 1')).toBeInTheDocument());

        await user.type(screen.getByPlaceholderText('Name, Nummer...'), 'ab');
        await waitFor(() => expect(screen.getByText('Neuer Treffer')).toBeInTheDocument());

        await act(async () => {
            verspaeteteAntworten.forEach(aufloesen => aufloesen());
            await Promise.resolve();
        });

        expect(screen.queryByText('Alter Treffer')).not.toBeInTheDocument();
        expect(screen.getByText('Neuer Treffer')).toBeInTheDocument();
    });
});

describe('ArtikelEditor – Suche ueberlebt die Navigation', () => {
    beforeEach(() => {
        gesamtTreffer = GESAMT;
        aktuelleAdresse = '';
        aktuellerNavigationsState = null;
        fetchMock = mockArtikelApi();
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('uebernimmt Filter, Sortierung und Seite aus der Adresszeile', async () => {
        // Genau dieser Zustand entsteht, wenn man von der Detailseite zurueckkommt.
        renderArtikelEditor('/artikel?q=rohr&werkstoff=S235JR&page=2&sort=preis&dir=desc');

        await waitFor(() => {
            const request = letzterListenRequest();
            expect(request.get('q')).toBe('rohr');
            expect(request.get('werkstoff')).toBe('S235JR');
            expect(request.get('page')).toBe('2');
            expect(request.get('sort')).toBe('preis');
            expect(request.get('dir')).toBe('desc');
        });

        // Die Filterfelder muessen den Zustand auch anzeigen, nicht nur mitsenden.
        expect(screen.getByPlaceholderText('Name, Nummer...')).toHaveValue('rohr');
    });

    it('schreibt die eingegebene Suche in die Adresszeile', async () => {
        const user = userEvent.setup();
        renderArtikelEditor();

        await waitFor(() => expect(listenRequests()).toHaveLength(1));
        expect(aktuelleAdresse).toBe('/artikel');

        await user.type(screen.getByPlaceholderText('Name, Nummer...'), 'rohr');

        await waitFor(() => expect(aktuelleAdresse).toBe('/artikel?q=rohr'));
    });

    it('haelt die Adresszeile sauber, wenn die Filter zurueckgesetzt werden', async () => {
        const user = userEvent.setup();
        renderArtikelEditor('/artikel?q=rohr&page=1');

        await waitFor(() => expect(listenRequests()).toHaveLength(1));

        await user.click(screen.getByRole('button', { name: /Filter zurücksetzen/i }));

        await waitFor(() => expect(aktuelleAdresse).toBe('/artikel'));
    });

    it('vermerkt beim Oeffnen eines Artikels, dass man aus der Liste kommt', async () => {
        const user = userEvent.setup();
        renderArtikelEditor('/artikel?q=rohr');

        await user.click(await screen.findByRole('link', { name: /Details zu Musterprofil 1 öffnen/i }));

        await waitFor(() => expect(aktuelleAdresse).toBe('/artikel/1'));
        // Ohne diesen Vermerk wuesste die Detailseite nicht, dass ein Schritt
        // zurueck auf die gefilterte Liste fuehrt.
        expect(aktuellerNavigationsState).toEqual({ vonListe: true });
    });
});
