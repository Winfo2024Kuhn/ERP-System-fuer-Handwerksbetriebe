import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { KundeGesperrtBand } from './KundeDatenschutz';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import type { KundeDetail } from '../../types';

// DSGVO: ausschließlich Dummy-Daten.
function gesperrterKunde(ueberschreiben: Partial<KundeDetail> = {}): KundeDetail {
    return {
        id: 1,
        kundennummer: 'K-1042',
        name: 'Max Mustermann',
        strasse: 'Musterweg 1',
        plz: '12345',
        ort: 'Musterstadt',
        gesperrt: true,
        gesperrtAm: '2026-02-03T10:00:00',
        aufbewahrenBis: '2025-12-31',
        anonymisiertAm: null,
        loeschPruefung: { loeschbar: false, hindernisse: ['1 Projekt'], aufbewahrenBis: '2025-12-31', anonymisierbar: true },
        ...ueberschreiben,
    } as KundeDetail;
}

const ANONYMISIERT = gesperrterKunde({
    name: 'Gelöschter Kunde K-1042',
    strasse: '',
    plz: '',
    ort: '',
    anonymisiertAm: '2026-10-20T09:15:00',
    loeschPruefung: { loeschbar: false, hindernisse: ['1 Projekt'], aufbewahrenBis: '2025-12-31', anonymisierbar: false },
});

type Antwort = { status: number; body?: unknown } | 'netzwerkfehler';
let antwort: Antwort;
let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
    antwort = { status: 200, body: ANONYMISIERT };
    fetchMock = vi.fn(() => {
        if (antwort === 'netzwerkfehler') return Promise.reject(new TypeError('Failed to fetch'));
        const { status, body } = antwort;
        return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body ?? {}) });
    });
    global.fetch = fetchMock as unknown as typeof fetch;
});

afterEach(() => {
    vi.restoreAllMocks();
});

function renderBand(kunde: KundeDetail, onAnonymisiert = vi.fn()) {
    render(
        <ToastProvider>
            <ConfirmProvider>
                <KundeGesperrtBand kunde={kunde} onAnonymisiert={onAnonymisiert} />
            </ConfirmProvider>
        </ToastProvider>
    );
    return onAnonymisiert;
}

describe('KundeGesperrtBand – Anonymisieren', () => {
    it('zeigt keinen Knopf, solange die Frist läuft', () => {
        renderBand(gesperrterKunde({
            loeschPruefung: { loeschbar: false, hindernisse: [], aufbewahrenBis: '2034-12-31', anonymisierbar: false },
        }));
        expect(screen.getByTestId('kunde-gesperrt-band')).toHaveTextContent(
            'Aus Datenschutzgründen gesperrt seit 03.02.2026. Unterlagen werden bis 31.12.2034 aufbewahrt, danach kann der Kunde anonymisiert werden.');
        expect(screen.queryByRole('button', { name: /Anonymisieren…/ })).not.toBeInTheDocument();
    });

    it('zeigt keinen Knopf ohne Löschprüfung (Nicht-Admin)', () => {
        renderBand(gesperrterKunde({ loeschPruefung: undefined }));
        expect(screen.getByTestId('kunde-gesperrt-band')).toHaveTextContent('bis 31.12.2025 aufbewahrt');
        expect(screen.queryByRole('button', { name: /Anonymisieren…/ })).not.toBeInTheDocument();
    });

    it('bevorzugt das frisch berechnete Fristende aus der Löschprüfung', () => {
        renderBand(gesperrterKunde({
            aufbewahrenBis: '2033-12-31',
            loeschPruefung: { loeschbar: false, hindernisse: [], aufbewahrenBis: '2034-12-31', anonymisierbar: false },
        }));
        expect(screen.getByTestId('kunde-gesperrt-band')).toHaveTextContent('bis 31.12.2034 aufbewahrt');
    });

    it('zeigt nach dem Anonymisieren den Hinweis und keinen Knopf mehr', () => {
        renderBand(ANONYMISIERT);
        const band = screen.getByTestId('kunde-gesperrt-band');
        expect(band).toHaveTextContent('Anonymisiert am 20.10.2026. Name und Anschrift wurden entfernt, die Unterlagen bleiben erhalten.');
        expect(band).not.toHaveTextContent('gesperrt seit');
        expect(screen.queryByRole('button', { name: /Anonymisieren…/ })).not.toBeInTheDocument();
    });

    it('anonymisiert nach Bestätigung und gibt die neue Akte weiter', async () => {
        const user = userEvent.setup();
        const onAnonymisiert = renderBand(gesperrterKunde());
        await user.click(screen.getByRole('button', { name: /Anonymisieren…/ }));

        const dialog = await screen.findByRole('dialog', { name: 'Kunde anonymisieren?' });
        expect(within(dialog).getByText('K-1042 · Max Mustermann')).toBeInTheDocument();
        expect(within(dialog).getByText(/Der Name wird zu „Gelöschter Kunde K-1042“/)).toBeInTheDocument();
        expect(within(dialog).getByText(/Baustellen-Anschrift seiner Projekte und Anfragen/)).toBeInTheDocument();
        expect(within(dialog).getByText('Name in Freigabe-Links wird entfernt (angenommene Angebote behalten den Auftragsnachweis).')).toBeInTheDocument();
        expect(within(dialog).getByText('Rechnungen, Angebote und E-Mails bleiben unverändert.')).toBeInTheDocument();
        expect(within(dialog).getByText('Das lässt sich nicht rückgängig machen.')).toBeInTheDocument();
        expect(within(dialog).getByRole('button', { name: /Mehr zu/ })).toBeInTheDocument();

        await user.click(within(dialog).getByRole('button', { name: /Endgültig anonymisieren/ }));

        await waitFor(() => expect(onAnonymisiert).toHaveBeenCalledWith(ANONYMISIERT));
        expect(fetchMock).toHaveBeenCalledWith('/api/kunden/1/anonymisieren', { method: 'POST' });
        expect(await screen.findByText('Kunde wurde anonymisiert.')).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('sperrt den Knopf während der Anfrage', async () => {
        const user = userEvent.setup();
        let freigeben: (wert: unknown) => void = () => {};
        fetchMock.mockImplementation(() => new Promise(resolve => { freigeben = resolve; }));
        renderBand(gesperrterKunde());
        await user.click(screen.getByRole('button', { name: /Anonymisieren…/ }));
        await user.click(await screen.findByRole('button', { name: /Endgültig anonymisieren/ }));

        const knopf = await screen.findByRole('button', { name: /Wird anonymisiert…/ });
        expect(knopf).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
        freigeben({ ok: true, status: 200, json: () => Promise.resolve(ANONYMISIERT) });
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    });

    it('zeigt bei 409 die Meldung des Servers und lässt den Dialog offen', async () => {
        const user = userEvent.setup();
        antwort = { status: 409, body: { message: 'Die Aufbewahrungsfrist läuft noch bis 31.12.2026.' } };
        const onAnonymisiert = renderBand(gesperrterKunde());
        await user.click(screen.getByRole('button', { name: /Anonymisieren…/ }));
        await user.click(await screen.findByRole('button', { name: /Endgültig anonymisieren/ }));

        expect(await screen.findByText('Die Aufbewahrungsfrist läuft noch bis 31.12.2026.')).toBeInTheDocument();
        expect(onAnonymisiert).not.toHaveBeenCalled();
        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('nutzt ohne Meldung im Körper den Standardtext', async () => {
        const user = userEvent.setup();
        antwort = { status: 403 };
        renderBand(gesperrterKunde());
        await user.click(screen.getByRole('button', { name: /Anonymisieren…/ }));
        await user.click(await screen.findByRole('button', { name: /Endgültig anonymisieren/ }));
        expect(await screen.findByText('Der Kunde konnte nicht anonymisiert werden.')).toBeInTheDocument();
    });

    it('meldet Netzwerkfehler mit Hinweis auf die Verbindung', async () => {
        const user = userEvent.setup();
        antwort = 'netzwerkfehler';
        renderBand(gesperrterKunde());
        await user.click(screen.getByRole('button', { name: /Anonymisieren…/ }));
        await user.click(await screen.findByRole('button', { name: /Endgültig anonymisieren/ }));
        expect(await screen.findByText('Der Kunde konnte nicht anonymisiert werden. Bitte die Verbindung prüfen.')).toBeInTheDocument();
    });

    it('sendet beim Abbrechen nichts', async () => {
        const user = userEvent.setup();
        const onAnonymisiert = renderBand(gesperrterKunde());
        await user.click(screen.getByRole('button', { name: /Anonymisieren…/ }));
        await user.click(await screen.findByRole('button', { name: 'Abbrechen' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(fetchMock).not.toHaveBeenCalled();
        expect(onAnonymisiert).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /Anonymisieren…/ })).toBeInTheDocument();
    });
});
