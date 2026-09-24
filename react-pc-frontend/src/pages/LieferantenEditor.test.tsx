import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { AuthProvider } from '../auth/AuthContext';
import LieferantenEditor from './LieferantenEditor';

// PAGE_SIZE im LieferantenEditor ist 12 – bei 40 Treffern also 4 Seiten.
const PAGE_SIZE = 12;
const GESAMT = 40;

// DSGVO: ausschließlich Dummy-Daten in Tests.
function dummyLieferanten(anzahl: number) {
    return Array.from({ length: anzahl }, (_, i) => ({
        id: i + 1,
        lieferantenname: `Musterbedarf ${i + 1} GmbH`,
        strasse: 'Musterweg 1',
        plz: '12345',
        ort: 'Musterstadt',
        lieferantenTyp: 'Lieferant',
        rollen: [],
    }));
}

let fetchMock: ReturnType<typeof vi.fn>;
// Über diese Variable lässt sich im Test simulieren, dass die Trefferzahl schrumpft.
let gesamtTreffer = GESAMT;

function mockLieferantenApi() {
    return vi.fn((url: string) => {
        if (typeof url === 'string' && url.startsWith('/api/lieferanten')) {
            const params = new URLSearchParams(url.substring(url.indexOf('?') + 1));
            const seite = Number(params.get('page') ?? 0);
            const verbleibend = Math.max(0, gesamtTreffer - seite * PAGE_SIZE);
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({
                    lieferanten: dummyLieferanten(Math.min(PAGE_SIZE, verbleibend)),
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
        .filter((url: unknown): url is string => typeof url === 'string' && url.startsWith('/api/lieferanten?'));
}

/** Query-Parameter des zuletzt abgesetzten Listen-Requests. */
function letzterListenRequest(): URLSearchParams {
    const alle = listenRequests();
    const letzte = alle[alle.length - 1];
    return new URLSearchParams(letzte.substring(letzte.indexOf('?') + 1));
}

function renderLieferantenEditor() {
    return render(
        <MemoryRouter>
            <ConfirmProvider>
                <ToastProvider>
                    <LieferantenEditor />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

describe('LieferantenEditor – Pagination und Filter', () => {
    beforeEach(() => {
        gesamtTreffer = GESAMT;
        fetchMock = mockLieferantenApi();
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('springt bei neuem Suchbegriff zurück auf die erste Seite', async () => {
        const user = userEvent.setup();
        renderLieferantenEditor();

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('0'));

        await user.click(screen.getByRole('button', { name: /weiter/i }));
        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('1'));

        await user.type(screen.getByPlaceholderText(/Name, zweiter Name/i), 'm');

        await waitFor(() => {
            const request = letzterListenRequest();
            expect(request.get('q')).toBe('m');
            expect(request.get('page')).toBe('0');
        });
    });

    it('springt auf die letzte gültige Seite, wenn die Trefferzahl schrumpft', async () => {
        const user = userEvent.setup();
        renderLieferantenEditor();

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
        expect(screen.getByText('Musterbedarf 1 GmbH')).toBeInTheDocument();

        // Der Guard darf sich nicht selbst nachtriggern. Erwartet sind 6 Requests
        // (Start, 3x Weiter, Aktualisieren, Korrektur-Sprung); eine Endlosschleife
        // würde hier sofort auffallen.
        await act(async () => {
            await Promise.resolve();
        });
        expect(listenRequests().length).toBeLessThanOrEqual(8);
    });

    it('zeigt keinen Filtern-Button, weil live gefiltert wird', async () => {
        renderLieferantenEditor();

        await waitFor(() => expect(listenRequests()).toHaveLength(1));

        expect(screen.queryByRole('button', { name: 'Filtern' })).not.toBeInTheDocument();
    });

    it('lädt bei Enter im Suchfeld nicht die Seite neu', async () => {
        const user = userEvent.setup();
        renderLieferantenEditor();

        await waitFor(() => expect(listenRequests()).toHaveLength(1));

        // Ohne abgefangenes submit würde der Browser das Formular abschicken und
        // die Seite neu laden – in jsdom ein "Not implemented: navigation"-Fehler.
        await user.type(screen.getByPlaceholderText(/Name, zweiter Name/i), '{Enter}');
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
            const antwortFuer = (lieferanten: ReturnType<typeof dummyLieferanten>) => ({
                ok: true,
                json: () => Promise.resolve({ lieferanten, gesamt: lieferanten.length }),
            });

            if (suchbegriff === 'a') {
                const veraltet = [{ ...dummyLieferanten(1)[0], id: 101, lieferantenname: 'Alte Treffer GmbH' }];
                return new Promise(resolve => {
                    verspaeteteAntworten.push(() => resolve(antwortFuer(veraltet)));
                });
            }
            if (suchbegriff === 'ab') {
                return Promise.resolve(
                    antwortFuer([{ ...dummyLieferanten(1)[0], id: 102, lieferantenname: 'Neue Treffer GmbH' }])
                );
            }
            return Promise.resolve(antwortFuer(dummyLieferanten(PAGE_SIZE)));
        });
        global.fetch = fetchMock as unknown as typeof fetch;

        renderLieferantenEditor();
        await waitFor(() => expect(screen.getByText('Musterbedarf 1 GmbH')).toBeInTheDocument());

        await user.type(screen.getByPlaceholderText(/Name, zweiter Name/i), 'ab');
        await waitFor(() => expect(screen.getByText('Neue Treffer GmbH')).toBeInTheDocument());

        await act(async () => {
            verspaeteteAntworten.forEach(aufloesen => aufloesen());
            await Promise.resolve();
        });

        expect(screen.queryByText('Alte Treffer GmbH')).not.toBeInTheDocument();
        expect(screen.getByText('Neue Treffer GmbH')).toBeInTheDocument();
    });
});

// ==================== DEEP-LINKS AUF DIE REITER ====================

/** DSGVO: reine Dummy-Daten. */
const DUMMY_DETAIL = {
    id: 7,
    lieferantenname: 'Musterbedarf 7 GmbH',
    strasse: 'Musterweg 1',
    plz: '12345',
    ort: 'Musterstadt',
    lieferantenTyp: 'Lieferant',
    rollen: [],
    emails: [],
    kommunikation: [],
    dokumente: [],
    notizen: [],
    kundenEmails: [],
};

/**
 * Beantwortet Detail-Abruf und Listen-Abruf getrennt; alles andere (die
 * Unter-Reiter laden ihre Inhalte selbst nach) bekommt eine leere Antwort,
 * damit der Test nicht an Nebenschauplätzen scheitert.
 */
function mockDetailApi() {
    return vi.fn((url: string) => {
        const antwort = (body: unknown) => Promise.resolve({ ok: true, json: () => Promise.resolve(body) });
        if (typeof url === 'string' && /^\/api\/lieferanten\/\d+$/.test(url)) {
            return antwort(DUMMY_DETAIL);
        }
        if (typeof url === 'string' && url.startsWith('/api/lieferanten?')) {
            return antwort({ lieferanten: dummyLieferanten(1), gesamt: 1 });
        }
        if (typeof url === 'string' && url.startsWith('/api/auth/me')) {
            // Der Dokumente-Reiter fragt über useAuth die Admin-Rechte ab.
            return antwort({
                id: 1,
                displayName: 'Max Mustermann',
                username: 'max.mustermann',
                active: true,
                roles: ['ADMIN'],
                admin: true,
                requiresInitialSetup: false,
            });
        }
        return antwort([]);
    });
}

/** Macht die Adresszeile sichtbar und erlaubt einen echten Browser-Zurück-Schritt. */
function RouterSonde() {
    const location = useLocation();
    const navigate = useNavigate();
    return (
        <>
            <span data-testid="adresse">{location.pathname + location.search}</span>
            <button onClick={() => navigate(-1)}>Browserzurueck</button>
        </>
    );
}

function renderMitAdresse(eintraege: string[], startIndex?: number) {
    return render(
        <MemoryRouter initialEntries={eintraege} initialIndex={startIndex}>
            <AuthProvider>
                <ConfirmProvider>
                    <ToastProvider>
                        <LieferantenEditor />
                        <RouterSonde />
                    </ToastProvider>
                </ConfirmProvider>
            </AuthProvider>
        </MemoryRouter>
    );
}

describe('LieferantenEditor – Reiter in der Adresszeile', () => {
    beforeEach(() => {
        fetchMock = mockDetailApi();
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('öffnet über ?tab=dokumente direkt die Dokumente statt des E-Mail-Verlaufs', async () => {
        // Genau dieser Link steckt hinter "Neuer Lieferschein" in der Glocke.
        renderMitAdresse(['/lieferanten?lieferantId=7&tab=dokumente']);

        const offenerReiter = await screen.findByRole('tab', { selected: true });
        expect(offenerReiter).toHaveTextContent(/Dokumente/);
    });

    it('fällt bei unbekanntem Reiter auf den E-Mail-Verlauf zurück', async () => {
        renderMitAdresse(['/lieferanten?lieferantId=7&tab=quatsch']);

        const offenerReiter = await screen.findByRole('tab', { selected: true });
        expect(offenerReiter).toHaveTextContent(/E-Mail-Verlauf/);
    });

    it('schreibt den angetippten Reiter in die Adresszeile', async () => {
        const user = userEvent.setup();
        renderMitAdresse(['/lieferanten?lieferantId=7']);

        await screen.findByRole('tab', { selected: true });
        await user.click(screen.getByRole('tab', { name: /Notizen/ }));

        await waitFor(() => {
            expect(screen.getByTestId('adresse')).toHaveTextContent('tab=notizen');
        });
        expect(screen.getByRole('tab', { selected: true })).toHaveTextContent(/Notizen/);
    });

    it('schließt die Detailansicht, wenn der Browser-Zurück-Knopf die Kennung entfernt', async () => {
        const user = userEvent.setup();
        // Zustand wie nach einem Klick aus der Liste heraus: Liste im Rücken,
        // Detailansicht im Vordergrund.
        renderMitAdresse(['/lieferanten', '/lieferanten?lieferantId=7&tab=dokumente'], 1);

        await screen.findByRole('tab', { selected: true });

        await user.click(screen.getByRole('button', { name: 'Browserzurueck' }));

        await waitFor(() => {
            expect(screen.queryByRole('tab')).not.toBeInTheDocument();
        });
        expect(screen.getByTestId('adresse')).toHaveTextContent('/lieferanten');
    });
});
