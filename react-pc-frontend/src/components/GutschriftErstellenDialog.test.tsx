import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GutschriftErstellenDialog } from './GutschriftErstellenDialog';
import { ToastProvider } from './ui/toast';
import type { AusgangsGeschaeftsDokument, GutschriftRahmen } from '../types';

/** Dummy-Rechnung (DSGVO: nur Musterdaten). */
const rechnung: AusgangsGeschaeftsDokument = {
    id: 42,
    dokumentNummer: 'RE-2026-0042',
    typ: 'RECHNUNG',
    datum: '2026-10-01',
    betragNetto: 1000,
    betragBrutto: 1190,
    gebucht: true,
    storniert: false,
    bearbeitbar: false,
    projektId: 7,
    kundenName: 'Max Mustermann',
};

const rahmenStandard: GutschriftRahmen = {
    rechnungId: 42,
    rechnungNummer: 'RE-2026-0042',
    rechnungBrutto: 1190,
    rechnungNetto: 1000,
    bereitsGutgeschriebenBrutto: 0,
    bereitsGutgeschriebenNetto: 0,
    hoechstensNetto: 1000,
    hoechstensBrutto: 1190,
    mwstSatz: 0.19,
};

let fetchMock: ReturnType<typeof vi.fn>;

/** Antwort wie fetch: JSON-Körper als Objekt, reiner Text als String. */
const antwort = (ok: boolean, status: number, body: unknown) => Promise.resolve({
    ok, status,
    json: () => (typeof body === 'string' ? Promise.reject(new SyntaxError('kein JSON')) : Promise.resolve(body)),
    text: () => Promise.resolve(typeof body === 'string' ? body : JSON.stringify(body)),
});
let openMock: ReturnType<typeof vi.fn>;

function mocken(rahmen: GutschriftRahmen | { fehler: string }, post?: { ok: boolean; status: number; body: unknown }) {
    fetchMock = vi.fn((url: string, options?: RequestInit) => {
        if (url.endsWith('/gutschrift-rahmen')) {
            if ('fehler' in rahmen) {
                return antwort(false, 400, { status: 400, message: rahmen.fehler });
            }
            return antwort(true, 200, rahmen);
        }
        if (url === '/api/ausgangs-dokumente' && options?.method === 'POST') {
            const p = post ?? { ok: true, status: 200, body: { id: 99, dokumentNummer: 'GU-2026-0001', typ: 'GUTSCHRIFT' } };
            return antwort(p.ok, p.status, p.body);
        }
        return antwort(false, 404, {});
    });
    vi.stubGlobal('fetch', fetchMock);
}

function oeffnen(r: AusgangsGeschaeftsDokument | null = rechnung) {
    const onClose = vi.fn();
    const onErstellt = vi.fn();
    render(
        <ToastProvider>
            <GutschriftErstellenDialog rechnung={r} onClose={onClose} onErstellt={onErstellt} />
        </ToastProvider>,
    );
    return { onClose, onErstellt };
}

const postBody = () => {
    const aufruf = fetchMock.mock.calls.find(([url, o]) => url === '/api/ausgangs-dokumente' && o?.method === 'POST');
    return aufruf ? JSON.parse(String(aufruf[1]?.body)) : null;
};

describe('GutschriftErstellenDialog', () => {
    beforeEach(() => {
        openMock = vi.fn();
        vi.stubGlobal('open', openMock);
        vi.useFakeTimers({ toFake: ['Date'] });
        vi.setSystemTime(new Date(2026, 9, 20, 9, 0));
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
    });

    it('rendert nichts ohne Rechnung', () => {
        mocken(rahmenStandard);
        oeffnen(null);
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('lädt den Rahmen und zeigt Rechnungsbetrag, bereits gutgeschrieben und höchstens möglich', async () => {
        mocken({ ...rahmenStandard, bereitsGutgeschriebenNetto: 100, bereitsGutgeschriebenBrutto: 119, hoechstensNetto: 900, hoechstensBrutto: 1071 });
        oeffnen();

        expect(await screen.findByText('Höchstens noch möglich')).toBeInTheDocument();
        expect(fetchMock).toHaveBeenCalledWith('/api/ausgangs-dokumente/42/gutschrift-rahmen');
        expect(screen.getByText(/1\.000,00\s€ netto/)).toBeInTheDocument();
        expect(screen.getByText(/100,00\s€ netto/)).toBeInTheDocument();
        expect(screen.getByTestId('gutschrift-hoechstens')).toHaveTextContent(/900,00\s€ netto/);
        // Schon etwas gutgeschrieben: die ganze Rechnung geht nicht mehr.
        expect(screen.getByRole('radio', { name: /Ganze Rechnung gutschreiben/ })).toBeDisabled();
        expect(screen.getByRole('radio', { name: /Teilbetrag gutschreiben/ })).toBeChecked();
    });

    it('legt eine Gutschrift über die ganze Rechnung ohne positionenJson an und öffnet den Editor', async () => {
        mocken(rahmenStandard);
        const { onErstellt, onClose } = oeffnen();

        await screen.findByText('Höchstens noch möglich');
        expect(screen.getByRole('radio', { name: /Ganze Rechnung gutschreiben/ })).toBeChecked();
        await userEvent.click(screen.getByRole('button', { name: 'Gutschrift anlegen' }));

        await waitFor(() => expect(postBody()).not.toBeNull());
        expect(postBody()).toEqual({
            typ: 'GUTSCHRIFT',
            vorgaengerId: 42,
            datum: '2026-10-20',
            projektId: 7,
        });
        await waitFor(() => expect(openMock).toHaveBeenCalledWith('/dokument-editor?projektId=7&dokumentId=99', '_blank'));
        expect(onErstellt).toHaveBeenCalledWith(expect.objectContaining({ id: 99 }));
        expect(onClose).toHaveBeenCalled();
    });

    it('baut für einen Teilbetrag eine einzige positive Position mit Grund', async () => {
        mocken(rahmenStandard);
        oeffnen();

        await screen.findByText('Höchstens noch möglich');
        await userEvent.click(screen.getByRole('radio', { name: /Teilbetrag gutschreiben/ }));
        await userEvent.type(screen.getByLabelText('Betrag netto in Euro *'), '1.000,00');
        expect(screen.getByText(/1\.190,00\s€ brutto\. Höchstens/)).toBeInTheDocument();
        await userEvent.type(screen.getByLabelText('Grund *'), 'Preisnachlass wegen Kratzer');
        await userEvent.click(screen.getByRole('button', { name: 'Gutschrift anlegen' }));

        await waitFor(() => expect(postBody()).not.toBeNull());
        const body = postBody();
        expect(body).toMatchObject({ typ: 'GUTSCHRIFT', vorgaengerId: 42, datum: '2026-10-20', projektId: 7 });
        expect(body).not.toHaveProperty('betreff');
        const positionen = JSON.parse(body.positionenJson);
        expect(positionen.globalRabatt).toBe(0);
        expect(positionen.blocks).toHaveLength(1);
        expect(positionen.blocks[0]).toMatchObject({
            type: 'SERVICE',
            title: 'Gutschrift zu Rechnung RE-2026-0042: Preisnachlass wegen Kratzer',
            quantity: 1,
            price: 1000,
        });
    });

    it('lehnt einen Teilbetrag über dem Höchstbetrag ab, ohne zu senden', async () => {
        mocken({ ...rahmenStandard, hoechstensNetto: 100, hoechstensBrutto: 119 });
        oeffnen();

        await screen.findByText('Höchstens noch möglich');
        await userEvent.click(screen.getByRole('radio', { name: /Teilbetrag gutschreiben/ }));
        await userEvent.type(screen.getByLabelText('Betrag netto in Euro *'), '100,01');
        await userEvent.type(screen.getByLabelText('Grund *'), 'Reklamation');
        await userEvent.click(screen.getByRole('button', { name: 'Gutschrift anlegen' }));

        expect((await screen.findAllByText('Höchstens 100,00 € netto sind noch möglich.')).length).toBeGreaterThan(0);
        expect(screen.getByLabelText('Betrag netto in Euro *')).toHaveAttribute('aria-invalid', 'true');
        expect(postBody()).toBeNull();
    });

    it('verlangt einen Grund und einen Betrag über 0', async () => {
        mocken(rahmenStandard);
        oeffnen();

        await screen.findByText('Höchstens noch möglich');
        await userEvent.click(screen.getByRole('radio', { name: /Teilbetrag gutschreiben/ }));
        await userEvent.type(screen.getByLabelText('Betrag netto in Euro *'), '0');
        await userEvent.click(screen.getByRole('button', { name: 'Gutschrift anlegen' }));

        expect((await screen.findAllByText('Der Betrag muss größer als 0 sein.')).length).toBeGreaterThan(0);
        expect(screen.getByText(/Bitte einen Grund eingeben/)).toBeInTheDocument();
        expect(postBody()).toBeNull();
    });

    it('zeigt die Meldung des Servers, wenn das Anlegen abgelehnt wird', async () => {
        const meldung = 'Die Gutschrift ist höher als der noch offene Rechnungsbetrag. Höchstens 100,00 € netto sind noch möglich.';
        mocken(rahmenStandard, { ok: false, status: 400, body: { status: 400, message: meldung } });
        const { onClose } = oeffnen();

        await screen.findByText('Höchstens noch möglich');
        await userEvent.click(screen.getByRole('button', { name: 'Gutschrift anlegen' }));

        expect(await screen.findByText(meldung)).toBeInTheDocument();
        expect(openMock).not.toHaveBeenCalled();
        expect(onClose).not.toHaveBeenCalled();
    });

    it('zeigt auch eine ältere Fehlermeldung als reinen Text', async () => {
        mocken(rahmenStandard, { ok: false, status: 400, body: 'Kein Kunde am Projekt hinterlegt.' });
        oeffnen();

        await screen.findByText('Höchstens noch möglich');
        await userEvent.click(screen.getByRole('button', { name: 'Gutschrift anlegen' }));

        expect(await screen.findByText('Kein Kunde am Projekt hinterlegt.')).toBeInTheDocument();
    });

    it('zeigt einen Ladefehler des Rahmens statt der Auswahl', async () => {
        mocken({ fehler: 'Die Rechnung ist storniert.' });
        oeffnen();

        await waitFor(() => expect(screen.getAllByRole('alert').some(a => a.textContent?.includes('Die Rechnung ist storniert.'))).toBe(true));
        expect(screen.queryByRole('radio')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Gutschrift anlegen' })).toBeDisabled();
    });

    it('sperrt das Anlegen, wenn nichts mehr gutgeschrieben werden kann', async () => {
        mocken({ ...rahmenStandard, bereitsGutgeschriebenNetto: 1000, bereitsGutgeschriebenBrutto: 1190, hoechstensNetto: 0, hoechstensBrutto: 0 });
        oeffnen();

        expect(await screen.findByText(/schon vollständig gutgeschrieben/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Gutschrift anlegen' })).toBeDisabled();
    });

    it('erklärt im Info-Button, was im Hintergrund passiert und was es bewirkt', async () => {
        mocken(rahmenStandard);
        oeffnen();

        await userEvent.click(screen.getByRole('button', { name: 'Mehr zu: Gutschrift' }));
        expect(await screen.findByText(/mit Nummer GU-… angelegt/)).toBeInTheDocument();
        expect(screen.getByText(/Der Auftragswert des Projekts ändert sich nicht/)).toBeInTheDocument();
    });
});
