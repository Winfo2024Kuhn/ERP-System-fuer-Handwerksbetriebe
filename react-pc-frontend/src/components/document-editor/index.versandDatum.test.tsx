import type { ComponentProps } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import DocumentEditor from './index';
import type { EmailComposeModal } from '../EmailComposeModal';
import { formatDatum, heuteIso, isoDatum } from '../../lib/datum';

/**
 * „Datum oben auf dem Dokument“: Beim endgültigen Drucken, Herunterladen und
 * Senden wählt der Anwender das Datum (Vorauswahl heute). Der Editor stellt
 * beim Laden nichts mehr still um, Entwürfe mit Wasserzeichen ändern nichts.
 * Nur Dummy-Daten (DSGVO).
 */

vi.mock('../../lib/scrollInDenBlick', () => ({ scrolleMittigInDenBlick: vi.fn() }));

type MailfensterProps = ComponentProps<typeof EmailComposeModal>;
vi.mock('../EmailComposeModal', () => ({
    EmailComposeModal: (props: MailfensterProps) => (props.isOpen ? <div data-testid="mailfenster" /> : null),
}));

const HEUTE = heuteIso();
const VOR_ZWEI_WOCHEN = (() => {
    const d = new Date();
    d.setDate(d.getDate() - 14);
    return isoDatum(d);
})();

const basisDokument = {
    id: 1,
    typ: 'ANGEBOT',
    dokumentNummer: 'AG-2026-0001',
    datum: VOR_ZWEI_WOCHEN,
    betreff: 'Dachsanierung',
    betragNetto: 1000,
    htmlInhalt: '',
    positionenJson: JSON.stringify({ blocks: [], globalRabatt: 0 }),
    kundenName: 'Max Mustermann',
    kundenEmails: ['max.mustermann@example.com'],
    gebucht: false,
    storniert: false,
    zahlungszielTage: 14,
};

function jsonAntwort(body: unknown) {
    return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: () => Promise.resolve(body),
        text: () => Promise.resolve(JSON.stringify(body)),
    });
}

interface Besonderheiten {
    /** pdf-speichern antwortet ok, aber ohne lesbares JSON. */
    pdfAblageOhneJson?: boolean;
}

function mockFetch(abweichung: Record<string, unknown>, { pdfAblageOhneJson = false }: Besonderheiten = {}) {
    const dokument = { ...basisDokument, ...abweichung };
    let stand: Record<string, unknown> = { ...dokument };
    return vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT') {
            stand = { ...stand, ...JSON.parse(init.body as string) };
            return jsonAntwort(stand);
        }
        if (url === '/api/ausgangs-dokumente/1') return jsonAntwort(dokument);
        if (url === '/api/ausgangs-dokumente/1/buchen') return jsonAntwort({ ...stand, gebucht: true });
        if (url === '/api/ausgangs-dokumente/1/pdf-speichern') {
            if (pdfAblageOhneJson) {
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.reject(new SyntaxError('kein JSON')) });
            }
            return jsonAntwort({ dateiname: 'abc.pdf' });
        }
        if (url === '/api/email/template') return jsonAntwort({ subject: 'Betreff', body: '<p>Hallo</p>' });
        if (url.startsWith('/api/dokument-generator/')) {
            return Promise.resolve({
                ok: true,
                status: 200,
                blob: () => Promise.resolve(new Blob(['%PDF-1.4'], { type: 'application/pdf' })),
                arrayBuffer: () => Promise.resolve(new ArrayBuffer(8)),
            });
        }
        if (url.startsWith('/api/formulare/templates/selection')) return jsonAntwort({ templateName: null });
        return jsonAntwort([]);
    });
}

let fetchMock: ReturnType<typeof mockFetch>;

function koerperAn(pfad: string): Array<Record<string, unknown>> {
    return fetchMock.mock.calls
        .filter(call => call[0] === pfad && call[1]?.body && typeof call[1].body === 'string')
        .map(call => JSON.parse(call[1]!.body as string) as Record<string, unknown>);
}

const gespeicherteDaten = () => koerperAn('/api/ausgangs-dokumente/1').map(b => b.datum);
/** Endgültige PDF-Anfragen; die Live-Vorschau läuft mit templateName „preview“ daneben. */
const pdfAnfragen = (pfad: string) => koerperAn(pfad).filter(b => b.templateName !== 'preview');
const pdfDatum = (pfad: string) => (pdfAnfragen(pfad)[0].kopfdaten as { rechnungsDatum: string }).rechnungsDatum;

async function starte(abweichung: Record<string, unknown> = {}, besonderheiten: Besonderheiten = {}) {
    fetchMock = mockFetch(abweichung, besonderheiten);
    global.fetch = fetchMock as unknown as typeof fetch;
    render(
        <MemoryRouter initialEntries={['/dokumente/1']}>
            <ConfirmProvider>
                <ToastProvider>
                    <DocumentEditor dokumentId={1} onClose={() => { }} />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>,
    );
    await screen.findByTitle('Dokument per E-Mail senden');
    await waitFor(() => expect(fetchMock.mock.calls.some(c => c[0] === '/api/ausgangs-dokumente/1')).toBe(true));
}

const datumWahl = () => screen.queryByRole('radiogroup', { name: 'Datum oben auf dem Dokument' });

beforeEach(() => {
    global.URL.createObjectURL = vi.fn(() => 'blob:test');
    global.URL.revokeObjectURL = vi.fn();
});

describe('Beim Laden', () => {
    it('behält das gespeicherte Datum und stellt nicht still auf heute um', async () => {
        const user = userEvent.setup();
        await starte();
        // Stünde das Datum schon auf heute, gäbe es nichts zu fragen.
        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        expect(screen.getByRole('radio', { name: `Bisheriges Datum behalten (${formatDatum(VOR_ZWEI_WOCHEN)})` }))
            .toBeInTheDocument();
        expect(gespeicherteDaten()).toEqual([]);
    });
});

describe('PDF herunterladen', () => {
    it('schlägt heute vor und schreibt es in Dokument und PDF', async () => {
        const user = userEvent.setup();
        await starte();
        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        expect(within(datumWahl()!).getByRole('radio', { name: `Heute (${formatDatum(HEUTE)})` }))
            .toHaveAttribute('aria-checked', 'true');
        await user.click(screen.getByText('Standard PDF-Datei herunterladen'));

        await waitFor(() => expect(koerperAn('/api/dokument-generator/pdf')).toHaveLength(1));
        expect(gespeicherteDaten()).toContain(HEUTE);
        expect(pdfDatum('/api/dokument-generator/pdf')).toBe(HEUTE);
    });

    it('behält auf Wunsch das bisherige Datum', async () => {
        const user = userEvent.setup();
        await starte();
        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        await user.click(screen.getByRole('radio', { name: `Bisheriges Datum behalten (${formatDatum(VOR_ZWEI_WOCHEN)})` }));
        await user.click(screen.getByText('Standard PDF-Datei herunterladen'));

        await waitFor(() => expect(koerperAn('/api/dokument-generator/pdf')).toHaveLength(1));
        expect(gespeicherteDaten()).not.toContain(HEUTE);
        expect(pdfDatum('/api/dokument-generator/pdf')).toBe(VOR_ZWEI_WOCHEN);
    });

    it('fragt nicht, wenn das Dokument schon das heutige Datum trägt', async () => {
        const user = userEvent.setup();
        await starte({ datum: HEUTE });
        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        await screen.findByText('Standard PDF-Datei herunterladen');
        expect(datumWahl()).not.toBeInTheDocument();
    });

    it('nennt in der Buchungswarnung das gewählte Datum', async () => {
        const user = userEvent.setup();
        await starte({ typ: 'RECHNUNG', dokumentNummer: 'RE-2026-0001' });
        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        await user.click(screen.getByRole('radio', { name: /Bisheriges Datum behalten/ }));
        await user.click(screen.getByText('Standard PDF-Datei herunterladen'));

        const warnung = await screen.findByRole('dialog', { name: 'Dokument exportieren?' });
        expect(warnung).toHaveTextContent(`Die Rechnung wird mit Datum ${formatDatum(VOR_ZWEI_WOCHEN)} gebucht und gesperrt.`);
    });

    it('sperrt bei einer Rechnung „Anderes Datum“, solange keins gewählt ist', async () => {
        const user = userEvent.setup();
        await starte({ typ: 'RECHNUNG', dokumentNummer: 'RE-2026-0001' });
        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        await user.click(screen.getByRole('radio', { name: 'Anderes Datum …' }));

        expect(screen.getByText('Bitte ein Datum auswählen.')).toBeInTheDocument();
        expect(screen.getByText('Standard PDF-Datei herunterladen').closest('button')).toBeDisabled();
    });
});

describe('E-Mail senden', () => {
    it('übernimmt das bisherige Datum in Dokument, PDF und E-Mail-Text', async () => {
        const user = userEvent.setup();
        await starte();
        await user.click(screen.getByTitle('Dokument per E-Mail senden'));
        await user.click(screen.getByRole('radio', { name: /Bisheriges Datum behalten/ }));
        await user.click(screen.getByText('PDF-Datei als E-Mail-Anhang versenden'));
        const wahl = await screen.findByRole('dialog', { name: /online annehmen können/ });
        await user.click(within(wahl).getByRole('button', { name: /Nein, nur das PDF schicken/ }));

        await screen.findByTestId('mailfenster');
        expect(gespeicherteDaten()).not.toContain(HEUTE);
        expect(pdfDatum('/api/dokument-generator/pdf')).toBe(VOR_ZWEI_WOCHEN);
        expect(koerperAn('/api/email/template')[0].rechnungsdatum).toBe(VOR_ZWEI_WOCHEN);
    });

    it('ein anderes Datum per Eingabe landet im Dokument', async () => {
        const user = userEvent.setup();
        await starte();
        await user.click(screen.getByTitle('Dokument per E-Mail senden'));
        await user.click(screen.getByRole('radio', { name: 'Anderes Datum …' }));
        await user.click(screen.getByRole('button', { name: 'Anderes Datum' }));
        await user.type(await screen.findByLabelText('Datum eingeben'), '01.10.2026{Enter}');
        await user.click(screen.getByText('PDF-Datei als E-Mail-Anhang versenden'));
        const wahl = await screen.findByRole('dialog', { name: /online annehmen können/ });
        await user.click(within(wahl).getByRole('button', { name: /Nein, nur das PDF schicken/ }));

        await screen.findByTestId('mailfenster');
        expect(gespeicherteDaten()).toContain('2026-10-01');
        expect(pdfDatum('/api/dokument-generator/pdf')).toBe('2026-10-01');
    });

    it('öffnet das Mailfenster auch, wenn die PDF-Ablage kein lesbares JSON liefert', async () => {
        const user = userEvent.setup();
        vi.spyOn(console, 'warn').mockImplementation(() => { });
        await starte({}, { pdfAblageOhneJson: true });
        await user.click(screen.getByTitle('Dokument per E-Mail senden'));
        await user.click(screen.getByText('PDF-Datei als E-Mail-Anhang versenden'));
        const wahl = await screen.findByRole('dialog', { name: /online annehmen können/ });
        await user.click(within(wahl).getByRole('button', { name: /Nein, nur das PDF schicken/ }));

        expect(await screen.findByTestId('mailfenster')).toBeInTheDocument();
        expect(koerperAn('/api/email/template')[0].pdfDateiname).toBeNull();
    });

    it('Entwurf mit Wasserzeichen fragt nicht und ändert das Datum nicht', async () => {
        const user = userEvent.setup();
        await starte();
        await user.click(screen.getByTitle(/Entwurf mit Wasserzeichen senden/));
        await screen.findByText('PDF-Datei als E-Mail-Anhang versenden');
        expect(datumWahl()).not.toBeInTheDocument();
        await user.click(screen.getByText('PDF-Datei als E-Mail-Anhang versenden'));

        await screen.findByTestId('mailfenster');
        expect(gespeicherteDaten()).not.toContain(HEUTE);
        expect(pdfDatum('/api/dokument-generator/pdf')).toBe(VOR_ZWEI_WOCHEN);
    });
});

describe('Drucken', () => {
    it('Rechnung: das gewählte Datum wird vor dem Buchen gespeichert und gedruckt', async () => {
        const user = userEvent.setup();
        await starte({ typ: 'RECHNUNG', dokumentNummer: 'RE-2026-0001' });
        await user.click(screen.getByRole('button', { name: 'Drucken' }));
        await user.click(screen.getByRole('radio', { name: /Bisheriges Datum behalten/ }));
        await user.click(screen.getByRole('button', { name: /Drucken & Buchen/ }));

        await waitFor(() => expect(pdfAnfragen('/api/dokument-generator/preview')).toHaveLength(1));
        expect(gespeicherteDaten()).not.toContain(HEUTE);
        expect(pdfDatum('/api/dokument-generator/preview')).toBe(VOR_ZWEI_WOCHEN);
        expect(fetchMock.mock.calls.some(c => c[0] === '/api/ausgangs-dokumente/1/buchen')).toBe(true);
    });

    it('Angebot: Druck mit Wasserzeichen fragt nicht nach dem Datum', async () => {
        const user = userEvent.setup();
        await starte();
        await user.click(screen.getByRole('button', { name: 'Drucken' }));
        await screen.findByText('Druckoptionen');
        expect(datumWahl()).not.toBeInTheDocument();
    });
});
