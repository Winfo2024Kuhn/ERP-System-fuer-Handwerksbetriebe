import type { ComponentProps } from 'react';
import { render, screen, waitFor, act, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import DocumentEditor from './index';
import type { EmailComposeModal } from '../EmailComposeModal';

/**
 * Dialogfolge vor PDF-Export und E-Mail-Versand (Issue #261 + Online-Annahme):
 * - PDF einer offenen Rechnung: erst Warnung, Abbrechen ändert nichts.
 * - E-Mail einer Rechnung: gebucht wird erst beim Senden, Vorschau gesperrt.
 * - Angebot: Wahl „online annehmen?“ und Rückfrage, wenn ein alter Link abgelöst wird.
 *
 * Das Mailfenster ist hier ein Stub, der seine Props festhält; sein eigenes
 * Verhalten prüft EmailComposeForm.versand.test.tsx. Nur Dummy-Daten (DSGVO).
 */

vi.mock('../../lib/scrollInDenBlick', () => ({ scrolleMittigInDenBlick: vi.fn() }));

type MailfensterProps = ComponentProps<typeof EmailComposeModal>;
const mailfenster = vi.hoisted(() => ({ props: null as MailfensterProps | null }));
vi.mock('../EmailComposeModal', () => ({
    EmailComposeModal: (props: MailfensterProps) => {
        mailfenster.props = props;
        return props.isOpen ? <div data-testid="mailfenster" /> : null;
    },
}));

const HEUTE_ISO = new Date().toISOString().split('T')[0];

const basisDokument = {
    id: 1,
    typ: 'RECHNUNG',
    dokumentNummer: 'RE-2026-0001',
    datum: HEUTE_ISO,
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

function jsonAntwort(body: unknown, ok = true, status = 200) {
    return Promise.resolve({
        ok,
        status,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: () => Promise.resolve(body),
        text: () => Promise.resolve(JSON.stringify(body)),
    });
}

const pdfAntwort = () => Promise.resolve({
    ok: true,
    status: 200,
    blob: () => Promise.resolve(new Blob(['%PDF-1.4'], { type: 'application/pdf' })),
});

interface Einstellungen {
    dokument?: Record<string, unknown>;
    abzuloesende?: unknown[];
    buchenOk?: boolean;
    /** Nettobetrag, den der Server beim Buchen übernimmt (Schlussrechnung). Standard: unverändert. */
    nettoNachBuchung?: number;
    pdfSpeichernOk?: boolean;
}

function mockFetch({
    dokument: abweichung = {}, abzuloesende = [], buchenOk = true, nettoNachBuchung, pdfSpeichernOk = true,
}: Einstellungen = {}) {
    const dokument = { ...basisDokument, ...abweichung };
    let stand: Record<string, unknown> = { ...dokument };
    return vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/ausgangs-dokumente/1' && init?.method === 'PUT') {
            stand = { ...stand, ...JSON.parse(init.body as string) };
            return jsonAntwort(stand);
        }
        if (url === '/api/ausgangs-dokumente/1') return jsonAntwort(dokument);
        if (url === '/api/ausgangs-dokumente/1/buchen') {
            return buchenOk
                ? jsonAntwort({ ...stand, gebucht: true, ...(nettoNachBuchung != null ? { betragNetto: nettoNachBuchung } : {}) })
                : jsonAntwort('Buchung abgelehnt', false, 400);
        }
        if (url === '/api/ausgangs-dokumente/1/pdf-speichern') {
            return pdfSpeichernOk ? jsonAntwort({ dateiname: 'abc.pdf' }) : jsonAntwort('Platte voll', false, 500);
        }
        if (url === '/api/ausgangs-dokumente/1/abzuloesende-freigaben') return jsonAntwort(abzuloesende);
        if (url === '/api/email/template') return jsonAntwort({ subject: 'Betreff', body: '<p>Hallo</p>' });
        if (url.startsWith('/api/dokument-generator/')) return pdfAntwort();
        if (url.startsWith('/api/formulare/templates/selection')) return jsonAntwort({ templateName: null });
        return jsonAntwort([]);
    });
}

let fetchMock: ReturnType<typeof mockFetch>;

function aufrufeAn(pfad: string): Array<RequestInit | undefined> {
    return fetchMock.mock.calls.filter(call => call[0] === pfad).map(call => call[1]);
}

function index(pfad: string): number {
    return fetchMock.mock.calls.findIndex(call => call[0] === pfad);
}

function starte(einstellungen: Einstellungen = {}) {
    fetchMock = mockFetch(einstellungen);
    global.fetch = fetchMock as unknown as typeof fetch;
    return render(
        <MemoryRouter initialEntries={['/dokumente/1']}>
            <ConfirmProvider>
                <ToastProvider>
                    <DocumentEditor dokumentId={1} onClose={() => { }} />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

async function warteAufEditor() {
    await screen.findByTitle('Dokument per E-Mail senden');
    await waitFor(() => expect(aufrufeAn('/api/ausgangs-dokumente/1').length).toBeGreaterThan(0));
}

async function pdfExportStarten(user: ReturnType<typeof userEvent.setup>) {
    await warteAufEditor();
    await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
    await user.click(await screen.findByText('Standard PDF-Datei herunterladen'));
}

async function emailStarten(user: ReturnType<typeof userEvent.setup>) {
    await warteAufEditor();
    await user.click(screen.getByTitle('Dokument per E-Mail senden'));
    await user.click(await screen.findByText('PDF-Datei als E-Mail-Anhang versenden'));
}

beforeEach(() => {
    mailfenster.props = null;
    global.URL.createObjectURL = vi.fn(() => 'blob:test');
    global.URL.revokeObjectURL = vi.fn();
});

describe('PDF-Export einer Rechnung', () => {
    it('warnt vor dem Buchen; Abbrechen bucht nichts und erzeugt kein PDF', async () => {
        const user = userEvent.setup();
        starte();
        await pdfExportStarten(user);

        const warnung = await screen.findByRole('dialog', { name: 'Dokument exportieren?' });
        expect(warnung).toHaveTextContent('gebucht und gesperrt');
        await user.click(within(warnung).getByRole('button', { name: 'Abbrechen' }));

        expect(screen.queryByRole('dialog', { name: 'Dokument exportieren?' })).not.toBeInTheDocument();
        expect(aufrufeAn('/api/ausgangs-dokumente/1/buchen')).toHaveLength(0);
        expect(aufrufeAn('/api/dokument-generator/pdf')).toHaveLength(0);
    });

    it('bucht und exportiert erst nach der Bestätigung', async () => {
        const user = userEvent.setup();
        starte();
        await pdfExportStarten(user);

        const warnung = await screen.findByRole('dialog', { name: 'Dokument exportieren?' });
        expect(aufrufeAn('/api/ausgangs-dokumente/1/buchen')).toHaveLength(0);
        await user.click(within(warnung).getByRole('button', { name: 'Exportieren & Buchen' }));

        await waitFor(() => expect(aufrufeAn('/api/dokument-generator/pdf')).toHaveLength(1));
        expect(aufrufeAn('/api/ausgangs-dokumente/1/buchen')).toHaveLength(1);
        expect(index('/api/ausgangs-dokumente/1/buchen')).toBeLessThan(index('/api/dokument-generator/pdf'));
    });

    it('fragt bei einer schon gebuchten Rechnung nicht nach', async () => {
        const user = userEvent.setup();
        starte({ dokument: { gebucht: true } });
        await pdfExportStarten(user);

        await waitFor(() => expect(aufrufeAn('/api/dokument-generator/pdf')).toHaveLength(1));
        expect(screen.queryByRole('dialog', { name: 'Dokument exportieren?' })).not.toBeInTheDocument();
    });

    it('fragt bei einem Angebot nicht nach – es wird nicht gebucht', async () => {
        const user = userEvent.setup();
        starte({ dokument: { typ: 'ANGEBOT', dokumentNummer: 'AG-2026-0002' } });
        await pdfExportStarten(user);

        await waitFor(() => expect(aufrufeAn('/api/dokument-generator/pdf')).toHaveLength(1));
        expect(screen.queryByRole('dialog', { name: 'Dokument exportieren?' })).not.toBeInTheDocument();
        expect(aufrufeAn('/api/ausgangs-dokumente/1/buchen')).toHaveLength(0);
    });
});

describe('E-Mail-Versand einer Rechnung', () => {
    it('bucht beim Öffnen des Mailfensters noch nicht und sperrt Vorschau und Entwurf', async () => {
        const user = userEvent.setup();
        starte();
        await emailStarten(user);

        await screen.findByTestId('mailfenster');
        expect(aufrufeAn('/api/ausgangs-dokumente/1/buchen')).toHaveLength(0);
        // Ungebuchte Rechnung wird noch nicht für die Offenen Posten gespeichert.
        expect(aufrufeAn('/api/ausgangs-dokumente/1/pdf-speichern')).toHaveLength(0);
        expect(mailfenster.props?.vorDemSenden).toBeTypeOf('function');
        expect(mailfenster.props?.anhangVorschauGesperrt).toMatch(/erst nach dem Senden/);
        expect(mailfenster.props?.entwurfSpeichern).toBe(false);
    });

    it('bucht beim Senden und speichert danach das PDF', async () => {
        const user = userEvent.setup();
        starte();
        await emailStarten(user);
        await screen.findByTestId('mailfenster');

        let ergebnis: Awaited<ReturnType<NonNullable<MailfensterProps['vorDemSenden']>>> | undefined;
        await act(async () => { ergebnis = await mailfenster.props!.vorDemSenden!(); });

        expect(ergebnis?.weiter).toBe(true);
        expect(ergebnis?.hinweisBeiSendefehler).toMatch(/bereits gebucht/);
        expect(aufrufeAn('/api/ausgangs-dokumente/1/buchen')).toHaveLength(1);
        expect(index('/api/ausgangs-dokumente/1/buchen')).toBeLessThan(index('/api/ausgangs-dokumente/1/pdf-speichern'));
    });

    it('sendet nicht, wenn die Buchung scheitert', async () => {
        const user = userEvent.setup();
        starte({ buchenOk: false });
        await emailStarten(user);
        await screen.findByTestId('mailfenster');

        let ergebnis: Awaited<ReturnType<NonNullable<MailfensterProps['vorDemSenden']>>> | undefined;
        await act(async () => { ergebnis = await mailfenster.props!.vorDemSenden!(); });

        expect(ergebnis?.weiter).toBe(false);
        expect(await screen.findByText(/Dokument konnte nicht gebucht werden/)).toBeInTheDocument();
        expect(aufrufeAn('/api/ausgangs-dokumente/1/pdf-speichern')).toHaveLength(0);
    });

    it('sendet nicht, wenn sich der Betrag beim Buchen geändert hat, und sagt, dass gebucht ist', async () => {
        const user = userEvent.setup();
        starte({ dokument: { typ: 'SCHLUSSRECHNUNG' }, nettoNachBuchung: 812.5 });
        await emailStarten(user);
        await screen.findByTestId('mailfenster');

        let ergebnis: Awaited<ReturnType<NonNullable<MailfensterProps['vorDemSenden']>>> | undefined;
        await act(async () => { ergebnis = await mailfenster.props!.vorDemSenden!(); });

        expect(ergebnis?.weiter).toBe(false);
        expect(await screen.findByText(/Betrag der Rechnung hat sich beim Buchen geändert/)).toHaveTextContent(/Die Rechnung ist jetzt gebucht/);
        expect(aufrufeAn('/api/ausgangs-dokumente/1/buchen')).toHaveLength(1);
        // Das alte PDF wird nicht als Rechnungs-PDF abgelegt.
        expect(aufrufeAn('/api/ausgangs-dokumente/1/pdf-speichern')).toHaveLength(0);
    });

    it('sendet trotzdem, wenn nur das Ablegen des PDFs scheitert, und warnt', async () => {
        const user = userEvent.setup();
        starte({ pdfSpeichernOk: false });
        await emailStarten(user);
        await screen.findByTestId('mailfenster');

        let ergebnis: Awaited<ReturnType<NonNullable<MailfensterProps['vorDemSenden']>>> | undefined;
        await act(async () => { ergebnis = await mailfenster.props!.vorDemSenden!(); });

        expect(ergebnis?.weiter).toBe(true);
        expect(await screen.findByText(/PDF konnte aber nicht bei den Offenen Posten abgelegt werden/)).toBeInTheDocument();
    });

    it('verlangt, dass das Rechnungs-PDF angehängt bleibt', async () => {
        const user = userEvent.setup();
        starte();
        await emailStarten(user);
        await screen.findByTestId('mailfenster');

        expect(mailfenster.props?.pflichtanhangHinweis).toMatch(/muss angehängt bleiben/);
    });

    it('bucht beim Schließen ohne Senden nichts', async () => {
        const user = userEvent.setup();
        starte();
        await emailStarten(user);
        await screen.findByTestId('mailfenster');

        act(() => mailfenster.props!.onClose());

        await waitFor(() => expect(screen.queryByTestId('mailfenster')).not.toBeInTheDocument());
        expect(aufrufeAn('/api/ausgangs-dokumente/1/buchen')).toHaveLength(0);
    });
});

describe('E-Mail-Versand eines Angebots', () => {
    const angebot = { typ: 'ANGEBOT', dokumentNummer: 'AG-2026-0002' };

    function templateAnfrage(): Record<string, unknown> {
        const init = aufrufeAn('/api/email/template')[0];
        return JSON.parse(init!.body as string);
    }

    it('„Nein“ verschickt ohne Link: keine Gültigkeitsfrage, mitFreigabe=false', async () => {
        const user = userEvent.setup();
        starte({ dokument: angebot });
        await emailStarten(user);

        const wahl = await screen.findByRole('dialog', { name: /online annehmen können/ });
        await user.click(within(wahl).getByRole('button', { name: /Nein, nur das PDF schicken/ }));

        await screen.findByTestId('mailfenster');
        expect(screen.queryByText('Wie lange soll der Annahme-Link gültig sein?')).not.toBeInTheDocument();
        expect(templateAnfrage().mitFreigabe).toBe(false);
        expect(mailfenster.props?.vorDemSenden).toBeUndefined();
        expect(mailfenster.props?.freigabenAbloesenFuerDokumentId).toBeUndefined();
        expect(mailfenster.props?.entwurfSpeichern).toBe(true);
    });

    it('„Ja“ fragt nach der Gültigkeit und verschickt mit Link', async () => {
        const user = userEvent.setup();
        starte({ dokument: angebot });
        await emailStarten(user);

        const wahl = await screen.findByRole('dialog', { name: /online annehmen können/ });
        await user.click(within(wahl).getByRole('button', { name: /Ja, mit Link zum Annehmen/ }));
        await screen.findByText('Wie lange soll der Annahme-Link gültig sein?');
        await user.click(screen.getByRole('button', { name: 'Weiter zum E-Mail-Versand' }));

        await screen.findByTestId('mailfenster');
        expect(templateAnfrage().mitFreigabe).toBe(true);
        expect(templateAnfrage().gueltigkeitTage).toBe(14);
    });

    it('fragt nach, wenn ein alter Online-Link ungültig wird, und gibt die Ablösung an den Versand', async () => {
        const user = userEvent.setup();
        starte({
            dokument: angebot,
            abzuloesende: [{ dokumentId: 2, dokumentNummer: 'AG-2026-0001', dokumentArt: 'Angebot' }],
        });
        await emailStarten(user);

        expect(await screen.findByText(/Der Online-Link zu Angebot AG-2026-0001 wird damit ungültig/)).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Trotzdem fortfahren' }));
        // Gilt auch ohne neuen Link.
        const wahl = await screen.findByRole('dialog', { name: /online annehmen können/ });
        await user.click(within(wahl).getByRole('button', { name: /Nein, nur das PDF schicken/ }));

        await screen.findByTestId('mailfenster');
        expect(mailfenster.props?.freigabenAbloesenFuerDokumentId).toBe(1);
        // Ein später aus der Entwurfsliste verschickter Entwurf würde die Ablösung überspringen.
        expect(mailfenster.props?.entwurfSpeichern).toBe(false);
    });

    it('fragt auch beim eigenen älteren Link nach (erneuter Versand)', async () => {
        const user = userEvent.setup();
        starte({
            dokument: angebot,
            abzuloesende: [{ dokumentId: 1, dokumentNummer: 'AG-2026-0002', dokumentArt: 'Angebot', diesesDokument: true }],
        });
        await emailStarten(user);

        expect(await screen.findByText(/Der bisherige Online-Link zu diesem Angebot wird damit ungültig/)).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Trotzdem fortfahren' }));
        const wahl = await screen.findByRole('dialog', { name: /online annehmen können/ });
        await user.click(within(wahl).getByRole('button', { name: /Nein, nur das PDF schicken/ }));

        await screen.findByTestId('mailfenster');
        expect(mailfenster.props?.freigabenAbloesenFuerDokumentId).toBe(1);
    });

    it('bricht ab, wenn der Anwender die Ablösung nicht will', async () => {
        const user = userEvent.setup();
        starte({
            dokument: angebot,
            abzuloesende: [{ dokumentId: 2, dokumentNummer: 'AG-2026-0001', dokumentArt: 'Angebot' }],
        });
        await emailStarten(user);

        const rueckfrage = await screen.findByRole('dialog', { name: 'Alter Online-Link wird ungültig' });
        await user.click(within(rueckfrage).getByRole('button', { name: 'Abbrechen' }));

        await waitFor(() => expect(screen.queryByText(/wird damit ungültig/)).not.toBeInTheDocument());
        expect(screen.queryByRole('dialog', { name: /online annehmen können/ })).not.toBeInTheDocument();
        expect(screen.queryByTestId('mailfenster')).not.toBeInTheDocument();
        expect(aufrufeAn('/api/email/template')).toHaveLength(0);
    });
});

describe('Buchen einer einfachen Rechnung, die den Auftrag abschliesst', () => {
    // Abrechnungsverlauf am Angebot 10: nur die Rechnung selbst, keine Vorrechnungen.
    const VERLAUF = {
        basisdokumentId: 10, basisdokumentNummer: 'AN-2026-0010', basisdokumentTyp: 'ANGEBOT',
        basisdokumentBetragNetto: 1000, bereitsAbgerechnet: 1000, restbetrag: 0,
        positionen: [{ id: 1, dokumentNummer: 'RE-2026-0001', typ: 'RECHNUNG', datum: HEUTE_ISO, betragNetto: 1000, storniert: false }],
        basisdokumentPositionenJson: JSON.stringify({ blocks: [], globalRabatt: 0 }),
    };

    function starteMitVerlauf(positionenJson: string) {
        const basis = mockFetch({ dokument: { vorgaengerId: 10, positionenJson } });
        fetchMock = vi.fn((url: string, init?: RequestInit) => (
            url === '/api/ausgangs-dokumente/10/abrechnungsverlauf' ? jsonAntwort(VERLAUF) : basis(url, init)
        )) as unknown as ReturnType<typeof mockFetch>;
        global.fetch = fetchMock as unknown as typeof fetch;
        return render(
            <MemoryRouter initialEntries={['/dokumente/1']}>
                <ConfirmProvider>
                    <ToastProvider>
                        <DocumentEditor dokumentId={1} onClose={() => { }} />
                    </ToastProvider>
                </ConfirmProvider>
            </MemoryRouter>
        );
    }

    async function exportierenUndBuchen(user: ReturnType<typeof userEvent.setup>) {
        await warteAufEditor();
        await waitFor(() => expect(aufrufeAn('/api/ausgangs-dokumente/10/abrechnungsverlauf').length).toBeGreaterThan(0));
        await user.click(screen.getByRole('button', { name: 'PDF', exact: true }));
        await user.click(await screen.findByText('Standard PDF-Datei herunterladen'));
        const warnung = await screen.findByRole('dialog', { name: 'Dokument exportieren?' });
        await user.click(within(warnung).getByRole('button', { name: 'Exportieren & Buchen' }));
        await waitFor(() => expect(aufrufeAn('/api/ausgangs-dokumente/1/buchen')).toHaveLength(1));
    }

    function puts(): Array<Record<string, unknown>> {
        return aufrufeAn('/api/ausgangs-dokumente/1')
            .filter(init => init?.method === 'PUT')
            .map(init => JSON.parse(init!.body as string));
    }

    it('traegt das fehlende Kennzeichen vor dem Buchen nach, auch ohne Aenderung', async () => {
        const user = userEvent.setup();
        starteMitVerlauf(JSON.stringify({ blocks: [], globalRabatt: 0 }));
        await exportierenUndBuchen(user);

        expect(puts().length).toBeGreaterThan(0);
        expect(JSON.parse(puts().at(-1)!.positionenJson as string).schliesstAuftragAb).toBe(true);
        const letzterPut = fetchMock.mock.calls.map((c, i) => [c, i] as const)
            .filter(([c]) => c[0] === '/api/ausgangs-dokumente/1' && (c[1] as RequestInit | undefined)?.method === 'PUT')
            .at(-1)![1];
        expect(letzterPut).toBeLessThan(index('/api/ausgangs-dokumente/1/buchen'));
        // Export speichert schon selbst vor dem Buchen; das Nachtragen darf
        // kein zweites Speichern ausloesen.
        expect(puts()).toHaveLength(1);
    });
});
