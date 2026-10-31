/**
 * Einstellungen „Lieferanten-Shops (IDS)“ – nur für Administratoren.
 * DSGVO: nur Dummy-Daten.
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ToastProvider } from '../../ui/toast';
import { ConfirmProvider } from '../../ui/confirm-dialog';

const auth = { isAdmin: true };
vi.mock('../../../auth/AuthContext', () => ({ useOptionalAuth: () => auth, useAuth: () => auth }));

import { IdsSettingsSection } from './IdsSettingsSection';
import { SystemSetupConfigurator } from '../SystemSetupConfigurator';

const ZUGANG = {
    lieferantId: 7, lieferantName: 'Muster Großhandel', aktiv: true, protokoll: 'IDS_CONNECT_2_5',
    shopUrl: 'https://ids.muster.example/einstieg', kundennummer: '12345', benutzername: 'max.mustermann',
    passwortGesetzt: true, geaendertAm: '2026-10-30T09:15:00Z',
};

interface StubOptionen {
    verschluesselung?: boolean;
    zugaenge?: unknown[];
}

function stubFetch({ verschluesselung = true, zugaenge = [ZUGANG] }: StubOptionen = {}) {
    let stand = { aktiv: true, verschluesselungEingerichtet: verschluesselung, zugaenge };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
        if (url === '/api/ids/einstellungen' && init?.method === 'PUT') {
            stand = { ...stand, ...JSON.parse(String(init.body)) };
            return json(stand);
        }
        if (url === '/api/ids/einstellungen') return json(stand);
        if (url.startsWith('/api/ids/einstellungen/zugaenge/') && init?.method === 'PUT') return json(ZUGANG);
        if (url.startsWith('/api/ids/einstellungen/zugaenge/') && init?.method === 'DELETE') return new Response(null, { status: 204 });
        if (url.startsWith('/api/lieferanten')) return json({ lieferanten: [{ id: 9, lieferantenname: 'Beispiel AG', istAktiv: true }], gesamt: 1 });
        return json({});
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function aufrufe(fetchMock: ReturnType<typeof stubFetch>, methode: string) {
    return fetchMock.mock.calls.filter(([, init]) => init?.method === methode);
}

function zeige() {
    return render(<ToastProvider><ConfirmProvider><IdsSettingsSection /></ConfirmProvider></ToastProvider>);
}

afterEach(() => {
    vi.unstubAllGlobals();
    auth.isAdmin = true;
    window.history.replaceState(null, '', '/');
});

describe('IdsSettingsSection', () => {
    it('zeigt Schalter und Zugänge – das Passwort nur als „gesetzt“', async () => {
        stubFetch();
        zeige();
        expect(await screen.findByText('Lieferanten-Shops (IDS)')).toBeInTheDocument();
        expect(screen.getByRole('switch', { name: /im Dokumenteditor anbieten/ })).toHaveAttribute('aria-checked', 'true');
        const liste = screen.getByRole('list', { name: 'Shop-Zugänge' });
        expect(within(liste).getByText('Muster Großhandel')).toBeInTheDocument();
        expect(within(liste).getByText('gesetzt')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Lieferanten-Shops (IDS)' })).toBeInTheDocument();
    });

    it('speichert den Grundschalter sofort', async () => {
        const fetchMock = stubFetch();
        zeige();
        fireEvent.click(await screen.findByRole('switch', { name: /im Dokumenteditor anbieten/ }));
        await waitFor(() => expect(aufrufe(fetchMock, 'PUT')).toHaveLength(1));
        expect(JSON.parse(String(aufrufe(fetchMock, 'PUT')[0][1]!.body))).toEqual({ aktiv: false });
        expect(await screen.findByText('Lieferanten-Shops ausgeschaltet.')).toBeInTheDocument();
    });

    it('ändert einen Zugang – leeres Passwortfeld wird nicht gesendet', async () => {
        const fetchMock = stubFetch();
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Zugang für Muster Großhandel ändern' }));
        expect(screen.getByLabelText('Shop-Adresse (URL)')).toHaveValue('https://ids.muster.example/einstieg');
        expect(screen.getByLabelText(/^Passwort/, { selector: 'input' })).toHaveValue('');
        fireEvent.change(screen.getByLabelText('Kundennummer (kndnr)'), { target: { value: '54321' } });
        fireEvent.click(screen.getByRole('button', { name: /Zugang speichern/ }));
        await waitFor(() => expect(aufrufe(fetchMock, 'PUT')).toHaveLength(1));
        const [url, init] = aufrufe(fetchMock, 'PUT')[0];
        expect(url).toBe('/api/ids/einstellungen/zugaenge/7');
        expect(JSON.parse(String(init!.body))).toEqual({
            aktiv: true, protokoll: 'IDS_CONNECT_2_5', shopUrl: 'https://ids.muster.example/einstieg',
            kundennummer: '54321', benutzername: 'max.mustermann', passwort: null,
        });
        expect(await screen.findByText('Shop-Zugang für Muster Großhandel gespeichert.')).toBeInTheDocument();
    });

    it('legt einen neuen Zugang mit Lieferanten-Suche an und prüft die Adresse', async () => {
        const fetchMock = stubFetch({ zugaenge: [] });
        zeige();
        expect(await screen.findByText('Noch kein Shop-Zugang eingerichtet.')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Shop-Zugang hinzufügen/ }));
        expect(screen.getByRole('button', { name: /Zugang speichern/ })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: /Lieferant wählen/ }));
        fireEvent.click(await screen.findByText('Beispiel AG'));
        fireEvent.change(screen.getByLabelText('Shop-Adresse (URL)'), { target: { value: 'http://unsicher.example' } });
        fireEvent.change(screen.getByLabelText(/^Passwort/, { selector: 'input' }), { target: { value: 'Geheim-123' } });
        fireEvent.click(screen.getByRole('button', { name: /Zugang speichern/ }));
        expect(await screen.findByText('Die Shop-Adresse muss mit https:// beginnen (http:// nur für Tests auf diesem Rechner).')).toBeInTheDocument();
        expect(aufrufe(fetchMock, 'PUT')).toHaveLength(0);

        fireEvent.change(screen.getByLabelText('Shop-Adresse (URL)'), { target: { value: 'https://ids.beispiel.example' } });
        fireEvent.click(screen.getByRole('button', { name: /Zugang speichern/ }));
        await waitFor(() => expect(aufrufe(fetchMock, 'PUT')).toHaveLength(1));
        const [url, init] = aufrufe(fetchMock, 'PUT')[0];
        expect(url).toBe('/api/ids/einstellungen/zugaenge/9');
        expect(JSON.parse(String(init!.body))).toMatchObject({ shopUrl: 'https://ids.beispiel.example', passwort: 'Geheim-123', kundennummer: null });
    });

    it('schickt das Passwort unverändert (Punkt und Leerzeichen am Ende bleiben) und das gewählte Verfahren', async () => {
        const fetchMock = stubFetch();
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Zugang für Muster Großhandel ändern' }));
        expect(screen.getByRole('radio', { name: 'IDS-Connect 2.5 (Standard)' })).toHaveAttribute('aria-checked', 'true');
        fireEvent.click(screen.getByRole('radio', { name: 'Nur Zugangsfelder (wie Würth-Zugangsblatt)' }));
        expect(screen.getByLabelText('Benutzername (name_kunde)')).toBeInTheDocument();
        expect(screen.getByText(/Rücksprung-Adresse \(hookurl\) setzt das Programm bei jedem Shop-Besuch selbst/)).toBeInTheDocument();
        const passwort = screen.getByLabelText(/^Passwort \(pw_kunde\)/, { selector: 'input' });
        fireEvent.change(passwort, { target: { value: 'abc. ' } });
        // Auge: Passwort zum Prüfen sichtbar machen.
        expect(passwort).toHaveAttribute('type', 'password');
        fireEvent.click(screen.getByRole('button', { name: 'Passwort anzeigen' }));
        expect(passwort).toHaveAttribute('type', 'text');
        expect(passwort).toHaveValue('abc. ');
        fireEvent.click(screen.getByRole('button', { name: /Zugang speichern/ }));
        await waitFor(() => expect(aufrufe(fetchMock, 'PUT')).toHaveLength(1));
        expect(JSON.parse(String(aufrufe(fetchMock, 'PUT')[0][1]!.body))).toMatchObject({ passwort: 'abc. ', protokoll: 'IDS_NUR_ZUGANGSFELDER' });
    });

    it('erlaubt http:// für den Übungs-Shop auf diesem Rechner', async () => {
        const fetchMock = stubFetch();
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Zugang für Muster Großhandel ändern' }));
        fireEvent.change(screen.getByLabelText('Shop-Adresse (URL)'), { target: { value: 'http://localhost:9000/ids' } });
        fireEvent.click(screen.getByRole('button', { name: /Zugang speichern/ }));
        await waitFor(() => expect(aufrufe(fetchMock, 'PUT')).toHaveLength(1));
        expect(JSON.parse(String(aufrufe(fetchMock, 'PUT')[0][1]!.body))).toMatchObject({ shopUrl: 'http://localhost:9000/ids' });
    });

    it('sperrt das Passwort ohne Verschlüsselungsschlüssel und sagt warum', async () => {
        stubFetch({ verschluesselung: false });
        zeige();
        expect(await screen.findByText('Passwörter können noch nicht gespeichert werden.')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Zugang für Muster Großhandel ändern' }));
        expect(screen.getByLabelText(/^Passwort/, { selector: 'input' })).toBeDisabled();
        expect(screen.getByText(/Passwort gesperrt, bis der Verschlüsselungsschlüssel eingerichtet ist/)).toBeInTheDocument();
    });

    it('entfernt einen Zugang erst nach Rückfrage', async () => {
        const fetchMock = stubFetch();
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Zugang für Muster Großhandel entfernen' }));
        fireEvent.click(await screen.findByRole('button', { name: 'Entfernen' }));
        await waitFor(() => expect(aufrufe(fetchMock, 'DELETE')).toHaveLength(1));
        expect(aufrufe(fetchMock, 'DELETE')[0][0]).toBe('/api/ids/einstellungen/zugaenge/7');
    });

    it('zeigt einen Ladefehler mit „Erneut laden“', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ status: 403, message: 'Nur für Administratoren.' }), { status: 403 })));
        zeige();
        expect(await screen.findByRole('button', { name: 'Erneut laden' })).toBeInTheDocument();
        expect(screen.getAllByText('Nur für Administratoren.').length).toBeGreaterThan(0);
        expect(screen.getByRole('button', { name: 'Erneut laden' })).toBeInTheDocument();
    });
});

describe('SystemSetupConfigurator – Reiter „Lieferanten-Shops“', () => {
    function zeigeKonfigurator() {
        return render(<ToastProvider><ConfirmProvider><SystemSetupConfigurator /></ConfirmProvider></ToastProvider>);
    }

    it('ist für Administratoren da', async () => {
        stubFetch();
        zeigeKonfigurator();
        fireEvent.click(await screen.findByRole('tab', { name: /Lieferanten-Shops/ }));
        expect(await screen.findByText('Lieferanten-Shops (IDS)')).toBeInTheDocument();
        expect(window.location.hash).toBe('#ids');
    });

    it('fehlt für alle anderen – auch über die Adresse', async () => {
        auth.isAdmin = false;
        window.history.replaceState(null, '', '/#ids');
        const fetchMock = stubFetch();
        zeigeKonfigurator();
        expect(await screen.findByRole('tab', { name: /E-Mail/ })).toHaveAttribute('aria-selected', 'true');
        expect(screen.queryByRole('tab', { name: /Lieferanten-Shops/ })).not.toBeInTheDocument();
        expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/ids'))).toBe(false);
    });
});
