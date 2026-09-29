import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import TelefonPage from './TelefonPage';
import {
    anruf, antwort, aufrufe, KANZLEI_BEISPIEL, KUNDE_ERIKA, KUNDE_MAX, nachricht, STATUS, stubbeFetch,
} from '../features/telefon/telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from '../features/telefon/useTelefonBerechtigung';
import { heuteIso } from '../lib/datum';
import type { Sprachnachricht, TelefonAnruf, TelefonStatus } from '../features/telefon/types';

interface Stand {
    darf?: boolean;
    admin?: boolean;
    status?: TelefonStatus;
    anrufe?: TelefonAnruf[];
    nachrichten?: Sprachnachricht[];
}

function stubbeTelefon(stand: Stand = {}) {
    const nachrichten = [...(stand.nachrichten ?? [nachricht()])];
    return stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort({ id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: stand.admin ? ['ADMIN'] : [], admin: !!stand.admin, requiresInitialSetup: false })
            : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: stand.darf ?? true }) : undefined),
        (url) => (url.pathname === '/api/telefon/status' ? antwort(stand.status ?? STATUS) : undefined),
        (url) => (url.pathname === '/api/telefon/sprachnachrichten/anzahl-neu' ? antwort({ anzahl: 1 }) : undefined),
        (url) => (url.pathname === '/api/telefon/anrufe'
            ? antwort({ content: stand.anrufe ?? [anruf()], totalElements: (stand.anrufe ?? [anruf()]).length, totalPages: 1, number: 0, size: 50 })
            : undefined),
        (url) => (url.pathname === '/api/telefon/sprachnachrichten' ? antwort(nachrichten) : undefined),
        (url, init) => {
            const treffer = /^\/api\/telefon\/sprachnachrichten\/(\d+)$/.exec(url.pathname);
            if (!treffer || init?.method !== 'PATCH') return undefined;
            const { abgehoert } = JSON.parse(String(init.body));
            return antwort(nachricht({
                id: Number(treffer[1]),
                neu: !abgehoert,
                abgehoertAm: abgehoert ? '2026-09-29T12:04:00' : null,
                abgehoertVon: abgehoert ? 'Max Mustermann' : null,
            }));
        },
        (url, init) => {
            if (!/^\/api\/telefon\/anrufe\/\d+\/zuordnung$/.test(url.pathname) || init?.method !== 'POST') return undefined;
            return antwort(anruf({ kontakt: KUNDE_MAX, zuordnung: 'MANUELL' }));
        },
        (url) => (url.pathname === '/api/telefon/abholen'
            ? antwort({ erfolgreich: true, meldung: 'ok', neueAnrufe: 3, neueSprachnachrichten: 1, nachtraeglichZugeordnet: 0 })
            : undefined),
        (url) => (url.pathname === '/api/kunden'
            ? antwort({ kunden: [{ id: 7, name: 'Max Mustermann', kundennummer: 'K-1007', ort: 'Musterstadt' }], gesamt: 1 })
            : undefined),
    );
}

function zeige(adresse = '/telefon/anrufe') {
    return render(
        <MemoryRouter initialEntries={[adresse]}>
            <AuthProvider>
                <ConfirmProvider>
                    <ToastProvider>
                        <Routes>
                            <Route path="/telefon/:reiter" element={<TelefonPage />} />
                            <Route path="/telefon" element={<TelefonPage />} />
                        </Routes>
                    </ToastProvider>
                </ConfirmProvider>
            </AuthProvider>
        </MemoryRouter>,
    );
}

function anrufParams(fetchMock: ReturnType<typeof stubbeFetch>): URLSearchParams {
    const letzte = aufrufe(fetchMock, '/api/telefon/anrufe').at(-1)!;
    return new URL(String(letzte[0]), 'http://localhost').searchParams;
}

describe('TelefonPage', () => {
    beforeEach(() => {
        setzeTelefonBerechtigungZurueck();
        Object.defineProperty(HTMLMediaElement.prototype, 'play', { configurable: true, value: vi.fn().mockResolvedValue(undefined) });
        Object.defineProperty(HTMLMediaElement.prototype, 'pause', { configurable: true, value: vi.fn() });
    });
    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
    });

    it('zeigt ohne Recht nur einen freundlichen Hinweis und lädt keine Daten', async () => {
        const fetchMock = stubbeTelefon({ darf: false });
        zeige();
        expect(await screen.findByText('Anrufe sind für Sie nicht freigeschaltet')).toBeInTheDocument();
        expect(aufrufe(fetchMock, '/api/telefon/status')).toHaveLength(0);
        expect(aufrufe(fetchMock, '/api/telefon/anrufe')).toHaveLength(0);
    });

    it('verweist Administratoren im leeren Zustand auf die Einstellungen', async () => {
        stubbeTelefon({ admin: true, status: { ...STATUS, eingerichtet: false } });
        zeige();
        expect(await screen.findByText('Telefon ist noch nicht eingerichtet')).toBeInTheDocument();
        expect(await screen.findByRole('link', { name: /Zu den Telefon-Einstellungen/ })).toHaveAttribute('href', '/einstellungen#telefon');
    });

    it('bittet andere Benutzer im leeren Zustand, sich an den Administrator zu wenden', async () => {
        stubbeTelefon({ status: { ...STATUS, eingerichtet: false } });
        zeige();
        expect(await screen.findByText('Noch nicht eingerichtet – bitte an den Administrator wenden.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Jetzt abholen/ })).toBeDisabled();
    });

    it('zeigt Anrufe mit Art, Wer, Nummer und Stand der Abholung', async () => {
        stubbeTelefon({
            anrufe: [
                anruf({ id: 1, art: 'VERPASST', kontakt: KUNDE_MAX }),
                anruf({ id: 2, art: 'ANRUFBEANTWORTER', anrufbeantworter: 1, nummer: '', sprachnachrichtId: 11 }),
                anruf({ id: 3, nameFritzbox: 'Baustelle Nord' }),
            ],
        });
        zeige();
        const tabelle = await screen.findByRole('table');
        expect(within(tabelle).getByRole('link', { name: 'Max Mustermann' })).toHaveAttribute('href', '/kunden?kundeId=7');
        expect(within(tabelle).getByText('Verpasst')).toBeInTheDocument();
        expect(within(tabelle).getByText('AB Nacht')).toBeInTheDocument();
        expect(within(tabelle).getByText('Nummer unterdrückt')).toBeInTheDocument();
        expect(within(tabelle).getByText('Baustelle Nord')).toBeInTheDocument();
        expect(within(tabelle).getByRole('button', { name: /Nachricht von .* anhören/ })).toBeInTheDocument();
        expect(screen.getByText(/Zuletzt abgeholt:/)).toBeInTheDocument();
    });

    it('filtert nach Verpasst und Unbekannt und übernimmt ?art=VERPASST', async () => {
        const fetchMock = stubbeTelefon();
        zeige('/telefon/anrufe?art=VERPASST');
        await screen.findByRole('table');
        expect(screen.getByRole('button', { name: 'Verpasst', pressed: true })).toBeInTheDocument();
        expect(anrufParams(fetchMock).get('art')).toBe('VERPASST');

        fireEvent.click(screen.getByRole('button', { name: 'Unbekannt' }));
        await waitFor(() => expect(anrufParams(fetchMock).get('nurUnbekannt')).toBe('true'));
        expect(anrufParams(fetchMock).get('art')).toBeNull();

        fireEvent.click(screen.getByRole('button', { name: 'Alle' }));
        await waitFor(() => expect(anrufParams(fetchMock).get('nurUnbekannt')).toBeNull());
    });

    it('übernimmt ?offen=1 aus der Glocke und fragt nur offene Rückrufe ab', async () => {
        const fetchMock = stubbeTelefon();
        zeige('/telefon/anrufe?offen=1');
        await screen.findByRole('table');
        expect(screen.getByRole('button', { name: 'Rückruf offen', pressed: true })).toBeInTheDocument();
        expect(anrufParams(fetchMock).get('nurOffen')).toBe('true');
        expect(anrufParams(fetchMock).get('art')).toBeNull();
        expect(screen.getByRole('textbox', { name: 'Anrufe durchsuchen' })).toBeDisabled();

        fireEvent.click(screen.getByRole('button', { name: 'Verpasst' }));
        await waitFor(() => expect(anrufParams(fetchMock).get('art')).toBe('VERPASST'));
        expect(anrufParams(fetchMock).get('nurOffen')).toBeNull();
    });

    it('sucht erst nach einer Tipp-Pause', async () => {
        const fetchMock = stubbeTelefon();
        zeige();
        await screen.findByRole('table');
        const vorher = aufrufe(fetchMock, '/api/telefon/anrufe').length;
        fireEvent.change(screen.getByRole('textbox', { name: /durchsuchen/ }), { target: { value: 'Muster' } });
        expect(aufrufe(fetchMock, '/api/telefon/anrufe')).toHaveLength(vorher);
        await waitFor(() => expect(anrufParams(fetchMock).get('suche')).toBe('Muster'));
    });

    it('ordnet einen unbekannten Anruf über den Dialog zu und merkt die Nummer', async () => {
        const fetchMock = stubbeTelefon();
        zeige();
        const tabelle = await screen.findByRole('table');
        fireEvent.click(within(tabelle).getByRole('button', { name: 'Zuordnen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Anruf zuordnen' });
        fireEvent.click(within(dialog).getByRole('button', { name: /Kunde suchen/ }));
        fireEvent.click(await screen.findByRole('button', { name: /Max Mustermann/ }));
        fireEvent.click(within(dialog).getByRole('button', { name: 'Zuordnen' }));
        await waitFor(() => expect(within(tabelle).getByRole('link', { name: 'Max Mustermann' })).toBeInTheDocument());
        const [, init] = aufrufe(fetchMock, '/api/telefon/anrufe/1/zuordnung', 'POST')[0];
        expect(JSON.parse(String(init?.body))).toEqual({ kundeId: 7, lieferantId: null, steuerberaterId: null, nummerMerken: true });
    });

    it('übernimmt einen der möglichen Kontakte mit einem Klick', async () => {
        const fetchMock = stubbeTelefon({ anrufe: [anruf({ kandidaten: [KUNDE_MAX, KUNDE_ERIKA] })] });
        zeige();
        await screen.findByText(/Mögliche Kontakte:/);
        fireEvent.click(screen.getByRole('button', { name: 'Max Mustermann' }));
        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/anrufe/1/zuordnung', 'POST')).toHaveLength(1));
        const [, init] = aufrufe(fetchMock, '/api/telefon/anrufe/1/zuordnung', 'POST')[0];
        expect(JSON.parse(String(init?.body))).toEqual({ kundeId: 7, lieferantId: null, steuerberaterId: null, nummerMerken: false });
    });

    it('holt jetzt ab und zeigt das Ergebnis', async () => {
        const fetchMock = stubbeTelefon();
        zeige();
        await screen.findByRole('table');
        fireEvent.click(screen.getByRole('button', { name: /Jetzt abholen/ }));
        expect(await screen.findByText('Abgeholt: 3 neue Anrufe, 1 neue Nachricht.')).toBeInTheDocument();
        expect(aufrufe(fetchMock, '/api/telefon/abholen', 'POST')).toHaveLength(1);
    });

    it('zeigt pro Anrufbeantworter einen Chip mit seinem Namen', async () => {
        const fetchMock = stubbeTelefon();
        zeige('/telefon/anrufbeantworter');
        await screen.findByRole('list', { name: 'Nachrichten auf dem Anrufbeantworter' });
        expect(screen.getByRole('button', { name: 'AB Tag' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'AB Nacht' }));
        await waitFor(() => {
            const letzte = aufrufe(fetchMock, '/api/telefon/sprachnachrichten').at(-1)!;
            expect(new URL(String(letzte[0]), 'http://localhost').searchParams.get('anrufbeantworter')).toBe('1');
        });
    });

    it('vermerkt beim Abspielen "abgehört" und lässt die Nachricht wieder als neu markieren', async () => {
        const refresh = vi.fn();
        window.addEventListener('notifications:refresh', refresh);
        const fetchMock = stubbeTelefon();
        zeige('/telefon/anrufbeantworter');
        expect(await screen.findByRole('img', { name: 'Neue Nachricht' })).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Abspielen: Nachricht von Max Mustermann' }));
        expect(await screen.findByText(/^Abgehört von Max Mustermann, /)).toBeInTheDocument();
        const [, init] = aufrufe(fetchMock, '/api/telefon/sprachnachrichten/11', 'PATCH')[0];
        expect(JSON.parse(String(init?.body))).toEqual({ abgehoert: true });
        expect(screen.queryByRole('img', { name: 'Neue Nachricht' })).toBeNull();
        expect(refresh).toHaveBeenCalled();

        const user = userEvent.setup();
        await user.click(screen.getByRole('button', { name: 'Weitere Aktionen: Nachricht von Max Mustermann' }));
        await user.click(await screen.findByRole('menuitem', { name: 'Wieder als neu markieren' }));
        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/sprachnachrichten/11', 'PATCH')).toHaveLength(2));
        expect(JSON.parse(String(aufrufe(fetchMock, '/api/telefon/sprachnachrichten/11', 'PATCH')[1][1]?.body))).toEqual({ abgehoert: false });
        expect(await screen.findByRole('img', { name: 'Neue Nachricht' })).toBeInTheDocument();
        window.removeEventListener('notifications:refresh', refresh);
    });

    it('hebt ?nachricht= hervor', async () => {
        stubbeTelefon({ nachrichten: [nachricht({ id: 11 }), nachricht({ id: 12, neu: false })] });
        zeige('/telefon/anrufbeantworter?nachricht=12');
        await screen.findByRole('list', { name: 'Nachrichten auf dem Anrufbeantworter' });
        await act(async () => {});
        expect(document.getElementById('nachricht-12')).toHaveAttribute('data-hervorgehoben', 'true');
        expect(document.getElementById('nachricht-11')).not.toHaveAttribute('data-hervorgehoben');
    });

    describe('ohne eigene Reiter, mit Tagesfilter', () => {
        it('nennt die Ansicht im Titel statt Reiter auf der Seite', async () => {
            stubbeTelefon();
            const { unmount } = zeige('/telefon/anrufe');
            await screen.findByRole('table');
            expect(screen.getByRole('heading', { name: 'Anrufe' })).toBeInTheDocument();
            expect(screen.queryByRole('tablist')).toBeNull();
            expect(screen.queryByRole('tab')).toBeNull();
            unmount();
            zeige('/telefon/anrufbeantworter');
            await screen.findByRole('list', { name: 'Nachrichten auf dem Anrufbeantworter' });
            expect(screen.getByRole('heading', { name: 'Anrufbeantworter' })).toBeInTheDocument();
            expect(screen.queryByRole('tablist')).toBeNull();
        });

        it('filtert Anrufe nach Tag aus der Adresse, per Kalender und setzt ihn wieder zurück', async () => {
            const user = userEvent.setup();
            const fetchMock = stubbeTelefon({ anrufe: [] });
            zeige('/telefon/anrufe?tag=2026-09-28');
            expect(await screen.findByText('Keine Anrufe am 28.09.2026.')).toBeInTheDocument();
            expect(anrufParams(fetchMock).get('tag')).toBe('2026-09-28');
            expect(screen.getByRole('button', { name: /^Tag filtern/ })).toHaveTextContent('28.09.2026');

            await user.click(screen.getByRole('button', { name: 'Tagesfilter entfernen' }));
            await waitFor(() => expect(anrufParams(fetchMock).has('tag')).toBe(false));
            expect(screen.getByRole('button', { name: /^Tag filtern/ })).toHaveTextContent('Alle Tage');
            expect(screen.queryByRole('button', { name: 'Tagesfilter entfernen' })).toBeNull();

            await user.click(screen.getByRole('button', { name: /^Tag filtern/ }));
            await user.click(screen.getByRole('button', { name: 'Heute' }));
            await waitFor(() => expect(anrufParams(fetchMock).get('tag')).toBe(heuteIso()));
        });

        it('sperrt den Tagesfilter bei „Rückruf offen“ und schickt keinen Tag mit', async () => {
            const fetchMock = stubbeTelefon();
            zeige('/telefon/anrufe?offen=1&tag=2026-09-28');
            await screen.findByRole('table');
            expect(screen.getByRole('button', { name: /^Tag filtern/ })).toBeDisabled();
            expect(anrufParams(fetchMock).has('tag')).toBe(false);
            expect(anrufParams(fetchMock).get('nurOffen')).toBe('true');
        });

        it('filtert den Anrufbeantworter nach Tag', async () => {
            const fetchMock = stubbeTelefon({ nachrichten: [] });
            zeige('/telefon/anrufbeantworter?tag=2026-09-28');
            expect(await screen.findByText('Keine Nachrichten am 28.09.2026.')).toBeInTheDocument();
            const letzte = aufrufe(fetchMock, '/api/telefon/sprachnachrichten').at(-1)!;
            expect(new URL(String(letzte[0]), 'http://localhost').searchParams.get('tag')).toBe('2026-09-28');
        });
    });

    describe('Kunden, Lieferanten und Steuerberater', () => {
        it('zeigt Anrufe vom Steuerberater mit Schild, aber ohne Link zur Akte', async () => {
            stubbeTelefon({ anrufe: [anruf({ id: 9, kontakt: KANZLEI_BEISPIEL, zuordnung: 'AUTOMATISCH' })] });
            zeige();
            const tabelle = await screen.findByRole('table');
            expect(within(tabelle).getByText('Kanzlei Beispiel')).toBeInTheDocument();
            expect(within(tabelle).queryByRole('link', { name: 'Kanzlei Beispiel' })).toBeNull();
            expect(within(tabelle).getByText('Steuerberater')).toBeInTheDocument();
        });

        it('filtert nach Kontaktart aus der Adresse und per Auswahl', async () => {
            const user = userEvent.setup();
            const fetchMock = stubbeTelefon({ anrufe: [] });
            zeige('/telefon/anrufe?kontakt=STEUERBERATER');
            expect(await screen.findByText('Keine Anrufe von Steuerberatern.')).toBeInTheDocument();
            expect(anrufParams(fetchMock).get('kontaktart')).toBe('STEUERBERATER');

            await user.click(screen.getByRole('combobox', { name: 'Kontaktart' }));
            await user.click(screen.getByRole('option', { name: 'Lieferanten' }));
            await waitFor(() => expect(anrufParams(fetchMock).get('kontaktart')).toBe('LIEFERANT'));

            await user.click(screen.getByRole('combobox', { name: 'Kontaktart' }));
            await user.click(screen.getByRole('option', { name: 'Alle Kontakte' }));
            await waitFor(() => expect(anrufParams(fetchMock).has('kontaktart')).toBe(false));
        });

        it('sperrt die Kontaktart bei „Unbekannt“ und schickt sie nicht mit', async () => {
            const fetchMock = stubbeTelefon();
            zeige('/telefon/anrufe?unbekannt=1&kontakt=KUNDE');
            await screen.findByRole('table');
            expect(screen.getByRole('combobox', { name: 'Kontaktart' })).toBeDisabled();
            expect(anrufParams(fetchMock).has('kontaktart')).toBe(false);
            expect(anrufParams(fetchMock).get('nurUnbekannt')).toBe('true');
        });
    });
});
