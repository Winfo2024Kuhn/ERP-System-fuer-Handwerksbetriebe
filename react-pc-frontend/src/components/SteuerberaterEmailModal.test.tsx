import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { SteuerberaterEmailModal } from './SteuerberaterEmailModal';
import { ToastProvider } from './ui/toast';

/** Stundenübermittlung an den Steuerberater (Dummy-Daten). */

function steuerberaterMit(ansprechpartner: { anrede: string | null; nachname: string }) {
    return [{
        id: 1,
        name: 'Kanzlei Mustermann',
        email: 'kanzlei@example.com',
        telefon: '',
        weitereEmails: [],
        ansprechpartnerListe: [{
            id: 2, vorname: 'Max', email: 'max@example.com', telefon: '', istLohnAnsprechpartner: true, ...ansprechpartner,
        }],
    }];
}

const PDF_NAME = 'Stundenaufstellung_2026-10.pdf';

function antwort(status: number, inhalt: unknown, kopf: Record<string, string> = {}) {
    return Promise.resolve({
        ok: status < 300, status, headers: new Headers(kopf),
        json: () => Promise.resolve(inhalt),
        blob: () => Promise.resolve(new Blob(['%PDF-1.7 Stunden'], { type: 'application/pdf' })),
    });
}

function modal(isOpen: boolean, onClose: () => void) {
    return (
        <ToastProvider>
            <SteuerberaterEmailModal isOpen={isOpen} onClose={onClose} jahr={2026} monat={10} mitarbeiterIds={[1, 2]}
                mitResturlaub mitStundenkonto={false} anzahlMitarbeiter={2} stundenart="IST" />
        </ToastProvider>
    );
}

function starte(ansprechpartner = { anrede: 'HERR', nachname: 'Mustermann' }, pdfStatus = 200) {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/firma/steuerberater') return antwort(200, steuerberaterMit(ansprechpartner));
        if (url === '/api/firma') return antwort(200, { firmenname: 'Musterbetrieb' });
        if (url.startsWith('/api/zeitverwaltung/berichte/steuerberater/pdf')) {
            return pdfStatus === 200
                ? antwort(200, null, { 'Content-Disposition': `attachment; filename="${PDF_NAME}"` })
                : antwort(pdfStatus, { status: pdfStatus, message: 'Der Monat ist ungültig.' });
        }
        if (url === '/api/emails/send' && init?.method === 'POST') return antwort(200, {});
        return antwort(404, null);
    });
    global.fetch = fetchMock as unknown as typeof fetch;
    const onClose = vi.fn();
    const { container, rerender } = render(modal(true, onClose));
    const editor = () => container.ownerDocument.querySelector('[contenteditable]') as HTMLElement | null;
    return { fetchMock, onClose, editor, schliessen: () => rerender(modal(false, onClose)) };
}

/** Erst wenn der Ansprechpartner gewählt ist (Empfänger gesetzt), steht seine Anrede im Text. */
async function ansprechpartnerGewaehlt() {
    await screen.findByDisplayValue('max@example.com');
}

const pdfChip = () => screen.findByRole('button', { name: `Anhang ${PDF_NAME} ansehen` });

beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:stunden');
    URL.revokeObjectURL = vi.fn();
});
afterEach(() => vi.restoreAllMocks());

describe('SteuerberaterEmailModal: Anrede', () => {
    it('nimmt bei Herr mit Nachname die persönliche Anrede', async () => {
        const { editor } = starte({ anrede: 'HERR', nachname: 'Mustermann' });
        await waitFor(() => expect(editor()?.innerHTML).toContain('Sehr geehrter Herr Mustermann,'));
    });

    it('nimmt bei Herr ohne Nachname „Sehr geehrte Damen und Herren,“', async () => {
        const { editor } = starte({ anrede: 'HERR', nachname: '' });
        await ansprechpartnerGewaehlt();
        await waitFor(() => expect(editor()?.innerHTML).toContain('Sehr geehrte Damen und Herren,'));
        expect(editor()?.innerHTML).not.toContain('Sehr geehrter Herr');
    });

    it('maskiert HTML im Nachnamen', async () => {
        const { editor } = starte({ anrede: 'FRAU', nachname: '<img src=x onerror=alert(1)>' });
        await waitFor(() => expect(editor()?.innerHTML).toContain('Sehr geehrte Frau &lt;img src=x onerror=alert(1)&gt;,'));
        expect(editor()?.querySelector('img')).toBeNull();
    });
});

describe('SteuerberaterEmailModal: PDF-Anhang', () => {
    it('schreibt nur einen kurzen Text ohne Tabelle', async () => {
        const { editor } = starte();
        await waitFor(() => expect(editor()?.textContent).toContain(
            'anbei sende ich Ihnen die Stundenaufstellung (Iststunden) unserer Mitarbeiter für den Monat Oktober 2026 als PDF.'));
        expect(editor()?.querySelector('table')).toBeNull();
        expect(editor()?.textContent).toContain('Mit freundlichen Grüßen');
        expect(screen.getByDisplayValue('Stundenaufstellung Oktober 2026 - Musterbetrieb')).toBeInTheDocument();
        expect(screen.getByText('2 Mitarbeiter für Oktober 2026')).toBeInTheDocument();
        expect(screen.getByText(/Übermittelt werden/)).toHaveTextContent('Übermittelt werden: Iststunden');
    });

    it('holt das PDF mit der Auswahl und zeigt es als Anhang mit Vorschau', async () => {
        const { fetchMock } = starte();
        const chip = await pdfChip();
        const pdfAufruf = fetchMock.mock.calls.find(([url]) => url.startsWith('/api/zeitverwaltung/berichte/steuerberater/pdf'))!;
        expect(Object.fromEntries(new URL(pdfAufruf[0], 'http://x').searchParams)).toEqual({
            jahr: '2026', monat: '10', mitarbeiterIds: '1,2', mitResturlaub: 'true', mitStundenkonto: 'false', stundenart: 'IST',
        });
        expect(chip).toHaveTextContent('1 KB');
        const oeffnen = vi.spyOn(window, 'open').mockReturnValue(null);
        fireEvent.click(chip);
        expect(oeffnen).toHaveBeenCalledWith('blob:stunden', '_blank', 'noopener');
    });

    it('sendet die Mail mit dem PDF als Teil „attachments“', async () => {
        const { fetchMock, onClose } = starte();
        await pdfChip();
        await ansprechpartnerGewaehlt();
        fireEvent.click(screen.getByRole('button', { name: 'Senden' }));

        await waitFor(() => expect(onClose).toHaveBeenCalled());
        const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/emails/send')!;
        const formular = init!.body as FormData;
        const anhang = formular.get('attachments') as File;
        expect(anhang.name).toBe(PDF_NAME);
        expect(anhang.type).toBe('application/pdf');
        const dto = JSON.parse(await (formular.get('dto') as Blob).text());
        expect(dto.recipients).toEqual(['max@example.com']);
        expect(dto.body).not.toContain('<table');
    });

    it('sperrt das Senden, wenn das PDF nicht erstellt werden konnte', async () => {
        const { fetchMock } = starte(undefined, 400);
        expect(await screen.findByText('PDF konnte nicht erstellt werden: Der Monat ist ungültig.')).toBeInTheDocument();
        const senden = screen.getByRole('button', { name: 'Senden' });
        expect(senden).toBeDisabled();
        expect(senden).toHaveAttribute('title', 'Ohne PDF kann die Stundenaufstellung nicht gesendet werden.');
        fireEvent.click(senden);
        expect(fetchMock.mock.calls.some(([url]) => url === '/api/emails/send')).toBe(false);
    });

    it('gibt die Vorschau-Adresse beim Schließen frei', async () => {
        const { schliessen } = starte();
        await pdfChip();
        schliessen();
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:stunden');
        expect(screen.queryByRole('dialog', { name: 'Stundenübermittlung' })).not.toBeInTheDocument();
    });
});
