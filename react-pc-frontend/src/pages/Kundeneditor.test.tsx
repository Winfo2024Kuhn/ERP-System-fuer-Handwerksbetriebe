import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { Kundeneditor } from './Kundeneditor';
import { ToastProvider } from '../components/ui/toast';

// PAGE_SIZE im Kundeneditor ist 12 – bei 40 Treffern also 4 Seiten.
const PAGE_SIZE = 12;
const GESAMT = 40;

// DSGVO: ausschließlich Dummy-Daten in Tests.
function dummyKunden(anzahl: number) {
    return Array.from({ length: anzahl }, (_, i) => ({
        id: i + 1,
        name: `Max Mustermann ${i + 1}`,
        kundennummer: `K-${1000 + i}`,
        plz: '12345',
        ort: 'Musterstadt',
        hatProjekte: true,
    }));
}

let fetchMock: ReturnType<typeof vi.fn>;
// Über diese Variable lässt sich im Test simulieren, dass die Trefferzahl schrumpft.
let gesamtTreffer = GESAMT;

function mockKundenApi() {
    return vi.fn((url: string) => {
        if (typeof url === 'string' && url.startsWith('/api/kunden')) {
            const params = new URLSearchParams(url.substring(url.indexOf('?') + 1));
            const seite = Number(params.get('page') ?? 0);
            const verbleibend = Math.max(0, gesamtTreffer - seite * PAGE_SIZE);
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({
                    kunden: dummyKunden(Math.min(PAGE_SIZE, verbleibend)),
                    gesamt: gesamtTreffer,
                }),
            });
        }
        return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
    });
}

function listenRequests(): string[] {
    return fetchMock.mock.calls
        .map((call: unknown[]) => call[0])
        .filter((url: unknown): url is string => typeof url === 'string' && url.startsWith('/api/kunden?'));
}

/** Query-Parameter des zuletzt abgesetzten Listen-Requests. */
function letzterListenRequest(): URLSearchParams {
    const alle = listenRequests();
    const letzte = alle[alle.length - 1];
    return new URLSearchParams(letzte.substring(letzte.indexOf('?') + 1));
}

function renderKundeneditor() {
    return render(
        <ToastProvider>
            <MemoryRouter>
                <Kundeneditor />
            </MemoryRouter>
        </ToastProvider>
    );
}

describe('Kundeneditor – Pagination und Filter', () => {
    beforeEach(() => {
        gesamtTreffer = GESAMT;
        fetchMock = mockKundenApi();
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('springt bei neuem Suchbegriff zurück auf die erste Seite', async () => {
        const user = userEvent.setup();
        renderKundeneditor();

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('0'));

        await user.click(screen.getByRole('button', { name: /weiter/i }));
        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('1'));

        await user.type(screen.getByPlaceholderText(/Name, Telefon/i), 'm');

        await waitFor(() => {
            const request = letzterListenRequest();
            expect(request.get('q')).toBe('m');
            expect(request.get('page')).toBe('0');
        });
    });

    it('springt auf die letzte gültige Seite, wenn die Trefferzahl schrumpft', async () => {
        const user = userEvent.setup();
        renderKundeneditor();

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
        expect(screen.getByText('Max Mustermann 1')).toBeInTheDocument();

        // Der Guard darf sich nicht selbst nachtriggern. Erwartet sind 6 Requests
        // (Start, 3x Weiter, Aktualisieren, Korrektur-Sprung); eine Endlosschleife
        // würde hier sofort auffallen.
        await act(async () => {
            await Promise.resolve();
        });
        expect(listenRequests().length).toBeLessThanOrEqual(8);
    });

    it('zeigt keinen Filtern-Button, weil live gefiltert wird', async () => {
        renderKundeneditor();

        await waitFor(() => expect(listenRequests()).toHaveLength(1));

        expect(screen.queryByRole('button', { name: 'Filtern' })).not.toBeInTheDocument();
    });

    it('lädt bei Enter im Suchfeld nicht die Seite neu', async () => {
        const user = userEvent.setup();
        renderKundeneditor();

        await waitFor(() => expect(listenRequests()).toHaveLength(1));

        // Ohne abgefangenes submit würde der Browser das Formular abschicken und
        // die Seite neu laden – in jsdom ein "Not implemented: navigation"-Fehler.
        await user.type(screen.getByPlaceholderText(/Name, Telefon/i), '{Enter}');
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

        fetchMock = vi.fn((url: string) => {
            const suchbegriff = new URLSearchParams(url.substring(url.indexOf('?') + 1)).get('q');
            const antwortFuer = (kunden: ReturnType<typeof dummyKunden>) => ({
                ok: true,
                json: () => Promise.resolve({ kunden, gesamt: kunden.length }),
            });

            if (suchbegriff === 'a') {
                const veraltet = [{ ...dummyKunden(1)[0], id: 101, name: 'Anna Musterfrau' }];
                return new Promise(resolve => {
                    verspaeteteAntworten.push(() => resolve(antwortFuer(veraltet)));
                });
            }
            if (suchbegriff === 'ab') {
                return Promise.resolve(
                    antwortFuer([{ ...dummyKunden(1)[0], id: 102, name: 'Bernd Mustermann' }])
                );
            }
            return Promise.resolve(antwortFuer(dummyKunden(PAGE_SIZE)));
        });
        global.fetch = fetchMock as unknown as typeof fetch;

        renderKundeneditor();
        await waitFor(() => expect(screen.getByText('Max Mustermann 1')).toBeInTheDocument());

        await user.type(screen.getByPlaceholderText(/Name, Telefon/i), 'ab');
        await waitFor(() => expect(screen.getByText('Bernd Mustermann')).toBeInTheDocument());

        await act(async () => {
            verspaeteteAntworten.forEach(aufloesen => aufloesen());
            await Promise.resolve();
        });

        expect(screen.queryByText('Anna Musterfrau')).not.toBeInTheDocument();
        expect(screen.getByText('Bernd Mustermann')).toBeInTheDocument();
    });
});

// ==================== Bearbeiten: Zahlungsziel & Neuladen (#289, #264) ====================

/** Vollständige Akte wie GET /api/kunden/{id} – nach dem Speichern zählt, dass sie neu geladen wird. */
function dummyAkte(zahlungsziel: number) {
    return {
        id: 7,
        kundennummer: '1007',
        name: 'Max Mustermann',
        plz: '12345',
        ort: 'Musterstadt',
        zahlungsziel,
        kundenEmails: ['max@example.com'],
        projekte: [{ id: 1, bauvorhaben: 'Carport Mustermann', auftragsnummer: 'A-1', bruttoPreis: 1000, bezahlt: false }],
        anfragen: [],
        geschaeftsdokumente: [],
        kommunikation: [],
        notizen: [],
    };
}

describe('Kundeneditor – Kunde bearbeiten', () => {
    let gespeichert: Record<string, unknown>[];
    let aktuellesZahlungsziel: number;

    beforeEach(() => {
        gespeichert = [];
        aktuellesZahlungsziel = 8;
        fetchMock = vi.fn((url: string, init?: RequestInit) => {
            const json = (body: unknown, status = 200) => Promise.resolve({
                ok: status < 400, status, json: () => Promise.resolve(body),
            });
            if (url === '/api/kunden/7' && init?.method === 'PUT') {
                const body = JSON.parse(String(init.body));
                gespeichert.push(body);
                aktuellesZahlungsziel = body.zahlungsziel;
                // Die echte Antwort enthält nur die Stammdaten, keine Projekte.
                return json({ id: 7, kundennummer: '1007', name: 'Max Mustermann', zahlungsziel: body.zahlungsziel });
            }
            if (url === '/api/kunden' && init?.method === 'POST') {
                return json({ duplikate: [{ id: 7, kundennummer: '1007', name: 'Max Mustermann', gruende: ['EMAIL'] }] }, 409);
            }
            if (url === '/api/kunden/7') return json(dummyAkte(aktuellesZahlungsziel));
            if (url.startsWith('/api/kunden?')) return json({ kunden: [], gesamt: 0 });
            return json({}, 404);
        });
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function renderAkte() {
        return render(
            <ToastProvider>
                <MemoryRouter initialEntries={['/kunden?kundeId=7']}>
                    <Kundeneditor />
                </MemoryRouter>
            </ToastProvider>
        );
    }

    it('speichert ein geändertes Zahlungsziel und lädt danach die ganze Akte neu', async () => {
        const user = userEvent.setup();
        renderAkte();
        expect(await screen.findByText('8 Tage')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: /Bearbeiten/ }));
        const feld = screen.getByLabelText('Zahlungsziel (Tage)');
        await user.clear(feld);
        // Leeres Feld springt beim Tippen nicht mehr auf 8 zurück.
        expect(feld).toHaveValue('');
        await user.type(feld, '14');
        expect(feld).toHaveValue('14');
        await user.click(screen.getByRole('button', { name: 'Aktualisieren' }));

        await waitFor(() => expect(gespeichert).toHaveLength(1));
        expect(gespeichert[0].zahlungsziel).toBe(14);
        expect(await screen.findByText('14 Tage')).toBeInTheDocument();
        // Projekte bleiben nach dem Speichern sichtbar (Akte neu geladen statt knapper Antwort).
        const aktenAbrufe = fetchMock.mock.calls.filter(
            (call: unknown[]) => call[0] === '/api/kunden/7' && !(call[1] as RequestInit | undefined)?.method);
        expect(aktenAbrufe.length).toBeGreaterThanOrEqual(2);
    });

    it('„Diesen verwenden“ beim Doppel-Eintrag öffnet den vorhandenen Kunden', async () => {
        const user = userEvent.setup();
        render(
            <ToastProvider>
                <MemoryRouter>
                    <Kundeneditor />
                </MemoryRouter>
            </ToastProvider>
        );
        await user.click(await screen.findByRole('button', { name: /Neuer Kunde/ }));
        await user.type(screen.getByLabelText('Kundenname *'), 'Max Mustermann');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await user.click(await screen.findByRole('button', { name: /Diesen verwenden/ }));

        // Formular zu, Akte des vorhandenen Kunden offen.
        expect(await screen.findByText('8 Tage')).toBeInTheDocument();
        expect(screen.queryByLabelText('Kundenname *')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Bearbeiten/ })).toBeInTheDocument();
    });

    it('lässt das Formular offen, wenn der vorhandene Kunde nicht geladen werden kann', async () => {
        const user = userEvent.setup();
        const standard = fetchMock.getMockImplementation()!;
        fetchMock.mockImplementation((url: string, init?: RequestInit) => (url === '/api/kunden/7' && !init?.method
            ? Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) })
            : standard(url, init)));
        render(
            <ToastProvider>
                <MemoryRouter>
                    <Kundeneditor />
                </MemoryRouter>
            </ToastProvider>
        );
        await user.click(await screen.findByRole('button', { name: /Neuer Kunde/ }));
        await user.type(screen.getByLabelText('Kundenname *'), 'Max Mustermann');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await user.click(await screen.findByRole('button', { name: /Diesen verwenden/ }));

        expect(await screen.findByText('Der vorhandene Kunde konnte nicht geöffnet werden.')).toBeInTheDocument();
        expect(screen.getByLabelText('Kundenname *')).toHaveValue('Max Mustermann');
    });

    it('lehnt ein Zahlungsziel außerhalb von 1 bis 365 Tagen ab, ohne zu speichern', async () => {
        const user = userEvent.setup();
        renderAkte();
        await screen.findByText('8 Tage');

        await user.click(screen.getByRole('button', { name: /Bearbeiten/ }));
        const feld = screen.getByLabelText('Zahlungsziel (Tage)');
        for (const falsch of ['0', '366', '', '1,5']) {
            await user.clear(feld);
            if (falsch) await user.type(feld, falsch);
            await user.click(screen.getByRole('button', { name: 'Aktualisieren' }));
            expect(await screen.findByText('Das Zahlungsziel muss zwischen 1 und 365 Tagen liegen.')).toBeInTheDocument();
        }
        expect(gespeichert).toHaveLength(0);
    });
});
