import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { Kundeneditor } from './Kundeneditor';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

// Admin-Rolle je Test umschaltbar.
let auth: { isAdmin: boolean } | null = { isAdmin: true };
vi.mock('../auth/AuthContext', () => ({ useOptionalAuth: () => auth }));

// DSGVO: ausschließlich Dummy-Daten.
function akte(ueberschreiben: Record<string, unknown> = {}) {
    return {
        id: 7,
        kundennummer: '1007',
        name: 'Max Mustermann',
        ansprechspartner: 'Erika Musterfrau',
        strasse: 'Musterweg 1',
        plz: '12345',
        ort: 'Musterstadt',
        zahlungsziel: 8,
        kundenEmails: ['max@example.com'],
        projekte: [],
        anfragen: [],
        geschaeftsdokumente: [],
        kommunikation: [],
        notizen: [],
        gesperrt: false,
        gesperrtAm: null,
        aufbewahrenBis: null,
        loeschPruefung: { loeschbar: true, hindernisse: [], aufbewahrenBis: null },
        ...ueberschreiben,
    };
}

const NICHT_LOESCHBAR = {
    loeschPruefung: { loeschbar: false, hindernisse: ['2 Projekte', '3 Angebote/Rechnungen'], aufbewahrenBis: '2034-12-31' },
};

type Antwort = { status: number; body?: unknown };
let aktenAntwort: Record<string, unknown>;
let deleteAntwort: Antwort;
let sperreAntwort: Antwort;
let fetchMock: ReturnType<typeof vi.fn>;

function json({ status, body }: Antwort) {
    return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body ?? {}) });
}

beforeEach(() => {
    auth = { isAdmin: true };
    aktenAntwort = akte();
    deleteAntwort = { status: 204 };
    sperreAntwort = { status: 200, body: akte({
        ...NICHT_LOESCHBAR,
        ansprechspartner: '',
        kundenEmails: [],
        gesperrt: true,
        gesperrtAm: '2026-10-20T09:30:00',
        aufbewahrenBis: '2034-12-31',
    }) };
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/kunden/7' && init?.method === 'DELETE') return json(deleteAntwort);
        if (url === '/api/kunden/7/datenschutz-sperre') return json(sperreAntwort);
        if (url === '/api/kunden/7/anonymisieren') return json({ status: 200, body: akte({
            ...NICHT_LOESCHBAR,
            name: 'Gelöschter Kunde 1007',
            ansprechspartner: '',
            strasse: '',
            plz: '',
            ort: '',
            kundenEmails: [],
            gesperrt: true,
            gesperrtAm: '2026-02-03T09:30:00',
            aufbewahrenBis: '2025-12-31',
            anonymisiertAm: '2026-10-20T09:15:00',
        }) });
        if (url === '/api/kunden/7') return json({ status: 200, body: aktenAntwort });
        if (url.startsWith('/api/kunden?')) return json({ status: 200, body: { kunden: [], gesamt: 0 } });
        return json({ status: 404 });
    });
    global.fetch = fetchMock as unknown as typeof fetch;
});

afterEach(() => {
    vi.restoreAllMocks();
});

function renderAkte(pfad = '/kunden?kundeId=7') {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <MemoryRouter initialEntries={[pfad]}>
                    <Kundeneditor />
                </MemoryRouter>
            </ConfirmProvider>
        </ToastProvider>
    );
}

function aufrufe(methode: string, url: string) {
    return fetchMock.mock.calls.filter((call: unknown[]) =>
        call[0] === url && (call[1] as RequestInit | undefined)?.method === methode);
}

describe('Kundeneditor – Kunde löschen (Datenschutz)', () => {
    it('zeigt „Löschen…“ nur für Admins', async () => {
        auth = { isAdmin: false };
        renderAkte();
        expect(await screen.findByRole('button', { name: /Bearbeiten/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Löschen…/ })).not.toBeInTheDocument();
    });

    it('löscht einen Kunden ohne Unterlagen nach Rückfrage und kehrt zur Liste zurück', async () => {
        const user = userEvent.setup();
        renderAkte();
        await user.click(await screen.findByRole('button', { name: /Löschen…/ }));

        const dialog = await screen.findByRole('dialog', { name: 'Kunde endgültig löschen?' });
        expect(within(dialog).getByText('Kunde endgültig löschen?')).toBeInTheDocument();
        expect(within(dialog).getByText(/1007 · Max Mustermann/)).toBeInTheDocument();
        expect(within(dialog).getByText(/lässt sich nicht rückgängig machen/)).toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', { name: 'Endgültig löschen' }));

        await waitFor(() => expect(aufrufe('DELETE', '/api/kunden/7')).toHaveLength(1));
        expect(await screen.findByText('Kunde wurde gelöscht.')).toBeInTheDocument();
        expect(await screen.findByText('KUNDENÜBERSICHT')).toBeInTheDocument();
    });

    it('löscht nichts, wenn die Rückfrage abgebrochen wird', async () => {
        const user = userEvent.setup();
        renderAkte();
        await user.click(await screen.findByRole('button', { name: /Löschen…/ }));
        await user.click(await screen.findByRole('button', { name: 'Abbrechen' }));
        expect(aufrufe('DELETE', '/api/kunden/7')).toHaveLength(0);
        expect(screen.getByRole('button', { name: /Bearbeiten/ })).toBeInTheDocument();
    });

    it('zeigt bei 409 die Meldung des Servers und bleibt in der Akte', async () => {
        const user = userEvent.setup();
        deleteAntwort = { status: 409, body: { message: 'An diesem Kunden hängen inzwischen Unterlagen.' } };
        renderAkte();
        await user.click(await screen.findByRole('button', { name: /Löschen…/ }));
        await user.click(await screen.findByRole('button', { name: 'Endgültig löschen' }));

        expect(await screen.findByText('An diesem Kunden hängen inzwischen Unterlagen.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Bearbeiten/ })).toBeInTheDocument();
    });

    it('öffnet bei aufzubewahrenden Unterlagen den Datenschutz-Dialog und sperrt den Kunden', async () => {
        const user = userEvent.setup();
        aktenAntwort = akte(NICHT_LOESCHBAR);
        renderAkte();
        await user.click(await screen.findByRole('button', { name: /Löschen…/ }));

        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText('Kundendaten löschen (Datenschutz)')).toBeInTheDocument();
        expect(within(dialog).getByText('An diesem Kunden hängen: 2 Projekte, 3 Angebote/Rechnungen.')).toBeInTheDocument();
        expect(within(dialog).getByText('31.12.2034')).toBeInTheDocument();
        expect(within(dialog).getByText('E-Mail-Adressen')).toBeInTheDocument();
        expect(within(dialog).getByRole('button', { name: /Mehr zu/ })).toBeInTheDocument();

        const grund = within(dialog).getByLabelText('Grund (optional)');
        expect(grund).toHaveAttribute('maxLength', '500');
        await user.type(grund, 'Löschwunsch per E-Mail vom 20.10.2026');
        expect(within(dialog).getByText('37/500 Zeichen')).toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', { name: /Daten löschen und sperren/ }));

        await waitFor(() => expect(aufrufe('POST', '/api/kunden/7/datenschutz-sperre')).toHaveLength(1));
        const body = JSON.parse(String((aufrufe('POST', '/api/kunden/7/datenschutz-sperre')[0][1] as RequestInit).body));
        expect(body).toEqual({ grund: 'Löschwunsch per E-Mail vom 20.10.2026' });
        expect(await screen.findByText('Kundendaten wurden gelöscht, der Kunde ist gesperrt.')).toBeInTheDocument();
        expect(await screen.findByTestId('kunde-gesperrt-band')).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('schickt ohne Grund einen leeren Körper und zeigt Fehler des Servers', async () => {
        const user = userEvent.setup();
        aktenAntwort = akte(NICHT_LOESCHBAR);
        sperreAntwort = { status: 409, body: { message: 'Hier muss nichts aufbewahrt werden, bitte direkt löschen.' } };
        renderAkte();
        await user.click(await screen.findByRole('button', { name: /Löschen…/ }));
        await user.click(await screen.findByRole('button', { name: /Daten löschen und sperren/ }));

        expect(await screen.findByText('Hier muss nichts aufbewahrt werden, bitte direkt löschen.')).toBeInTheDocument();
        const body = JSON.parse(String((aufrufe('POST', '/api/kunden/7/datenschutz-sperre')[0][1] as RequestInit).body));
        expect(body).toEqual({});
        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('zeigt bei gesperrtem Kunden das Band und blendet Bearbeiten, Löschen, Notizen und Rufnummern aus', async () => {
        aktenAntwort = akte({
            ...NICHT_LOESCHBAR,
            gesperrt: true,
            gesperrtAm: '2026-10-20T09:30:00',
            aufbewahrenBis: '2034-12-31',
        });
        renderAkte();
        const band = await screen.findByTestId('kunde-gesperrt-band');
        expect(band).toHaveTextContent('Aus Datenschutzgründen gesperrt seit 20.10.2026. Unterlagen werden bis 31.12.2034 aufbewahrt');
        expect(within(band).getByRole('button', { name: /Mehr zu/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Bearbeiten/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Löschen…/ })).not.toBeInTheDocument();
        expect(within(screen.getByTestId('kunde-reiterleiste')).queryByText('Notizen')).not.toBeInTheDocument();
    });

    it('anonymisiert einen gesperrten Kunden nach Fristende und aktualisiert die Akte', async () => {
        const user = userEvent.setup();
        aktenAntwort = akte({
            gesperrt: true,
            ansprechspartner: '',
            kundenEmails: [],
            gesperrtAm: '2026-02-03T09:30:00',
            aufbewahrenBis: '2025-12-31',
            anonymisiertAm: null,
            loeschPruefung: { loeschbar: false, hindernisse: ['1 Projekt'], aufbewahrenBis: '2025-12-31', anonymisierbar: true },
        });
        renderAkte();
        await user.click(await screen.findByRole('button', { name: /Anonymisieren…/ }));
        await user.click(await screen.findByRole('button', { name: /Endgültig anonymisieren/ }));

        await waitFor(() => expect(aufrufe('POST', '/api/kunden/7/anonymisieren')).toHaveLength(1));
        const band = await screen.findByTestId('kunde-gesperrt-band');
        await waitFor(() => expect(band).toHaveTextContent('Anonymisiert am 20.10.2026.'));
        expect(screen.getAllByText('Gelöschter Kunde 1007').length).toBeGreaterThan(0);
        expect(screen.queryByRole('button', { name: /Anonymisieren…/ })).not.toBeInTheDocument();
    });
});

describe('Kundeneditor – Liste und gesperrte Kunden', () => {
    function listenParameter(): URLSearchParams[] {
        return fetchMock.mock.calls
            .map((call: unknown[]) => call[0])
            .filter((url: unknown): url is string => typeof url === 'string' && url.startsWith('/api/kunden?'))
            .map(url => new URLSearchParams(url.substring(url.indexOf('?') + 1)));
    }

    it('lädt standardmäßig ohne gesperrte und filtert für Admins auf gesperrte', async () => {
        const user = userEvent.setup();
        fetchMock.mockImplementation((url: string) => json({ status: 200, body: {
            kunden: [{ id: 9, kundennummer: '1009', name: 'Max Mustermann', gesperrt: url.includes('gesperrte=true') }],
            gesamt: 1,
        } }));
        renderAkte('/kunden');
        await screen.findByText('Max Mustermann');
        expect(listenParameter()[0].has('gesperrte')).toBe(false);
        expect(screen.queryByText('Gesperrt')).not.toBeInTheDocument();

        await user.click(screen.getByLabelText(/Nur gesperrte Kunden/));
        await waitFor(() => expect(listenParameter().at(-1)?.get('gesperrte')).toBe('true'));
        expect(await screen.findByText('Gesperrt')).toBeInTheDocument();
    });

    it('zeigt Nicht-Admins keinen Umschalter für gesperrte Kunden', async () => {
        auth = { isAdmin: false };
        renderAkte('/kunden');
        await waitFor(() => expect(fetchMock).toHaveBeenCalled());
        expect(screen.queryByLabelText(/Nur gesperrte Kunden/)).not.toBeInTheDocument();
    });
});
