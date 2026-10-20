import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import ProjektEditor from './ProjektEditor';

/**
 * Rechnungs-Dialog im Projekt: Hat der Vorgang schon eine abschliessende
 * Rechnung (einfache Rechnung oder Schlussrechnung), gibt es keine Auswahl und
 * keinen Erstellen-Knopf mehr, nur einen Hinweis mit der Rechnungsnummer.
 * Nur Dummy-Daten (DSGVO).
 */

vi.mock('../components/ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf-viewer" data-url={url} />,
}));

const PROJEKT_ID = 5;
const ANGEBOT_ID = 701;
const PROJEKT = {
    id: PROJEKT_ID, bauvorhaben: 'Geländer Musterweg', kunde: 'Max Mustermann', kundenId: 3,
    kundennummer: 'K-1003', auftragsnummer: 'A-2026-0005', anlegedatum: '2026-01-15',
    bruttoPreis: 1190, bezahlt: false, abgeschlossen: false, kundenEmails: [],
    materialkosten: [], artikel: [], produktkategorien: [], zeiten: [], emails: [],
};
const ANGEBOT = {
    id: ANGEBOT_ID, dokumentNummer: 'AN-2026-0701', typ: 'ANGEBOT', datum: '2026-02-01',
    betreff: 'Geländer', betragNetto: 1000, betragBrutto: 1190, gebucht: false, storniert: false,
    bearbeitbar: true, projektId: PROJEKT_ID,
};

function antwort(body: unknown, ok = true) {
    return Promise.resolve({ ok, status: ok ? 200 : 404, json: () => Promise.resolve(body), text: () => Promise.resolve(JSON.stringify(body)) });
}

function mockFetch(abschliessendeRechnungNummer: string | null) {
    return vi.fn((url: string) => {
        const pfad = url.split('?')[0];
        if (pfad === `/api/projekte/${PROJEKT_ID}`) return antwort(PROJEKT);
        if (pfad === '/api/projekte') return antwort({ projekte: [], gesamt: 0 });
        if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) return antwort([ANGEBOT]);
        if (pfad === `/api/ausgangs-dokumente/${ANGEBOT_ID}/abrechnungsverlauf`) {
            return antwort({
                basisdokumentId: ANGEBOT_ID, basisdokumentNummer: ANGEBOT.dokumentNummer, basisdokumentTyp: 'ANGEBOT',
                basisdokumentBetragNetto: 1000, positionen: [], bereitsAbgerechneteBlockIds: [],
                bereitsAbgerechnet: abschliessendeRechnungNummer ? 1000 : 0,
                restbetrag: abschliessendeRechnungNummer ? 0 : 1000,
                abschliessendeRechnungNummer,
            });
        }
        if (pfad === `/api/ausgangs-dokumente/${ANGEBOT_ID}`) {
            return antwort({ ...ANGEBOT, positionenJson: JSON.stringify({ blocks: [], globalRabatt: 0 }) });
        }
        if (pfad.endsWith('/freigabe-status') || pfad.startsWith('/api/last-accessed/')) return antwort({});
        return antwort([]);
    });
}

function renderDialog(abschliessendeRechnungNummer: string | null) {
    global.fetch = mockFetch(abschliessendeRechnungNummer) as unknown as typeof fetch;
    return render(
        <MemoryRouter initialEntries={[`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`]}>
            <ConfirmProvider>
                <ToastProvider>
                    <ProjektEditor />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

async function oeffneRechnungsDialog() {
    const user = userEvent.setup();
    await user.click(await screen.findByText(ANGEBOT.dokumentNummer, {}, { timeout: 5000 }));
    await user.click(await screen.findByRole('button', { name: 'Rechnung erstellen' }));
    const titel = await screen.findByRole('heading', { name: 'Rechnung erstellen' });
    const dialog = titel.closest<HTMLElement>('[role="dialog"]')!;
    await waitFor(() => expect(within(dialog).queryByText('Abrechnungsverlauf wird geladen...')).not.toBeInTheDocument());
    return dialog;
}

afterEach(() => {
    vi.restoreAllMocks();
});

describe('ProjektEditor – Rechnungs-Dialog bei abgeschlossenem Auftrag', () => {
    it('zeigt den Hinweis mit Rechnungsnummer statt Auswahl und Erstellen-Knopf', async () => {
        renderDialog('RE-2026-0042');
        const dialog = await oeffneRechnungsDialog();

        expect(within(dialog).getByRole('status')).toHaveTextContent(
            'Dieser Auftrag ist mit Rechnung RE-2026-0042 abgeschlossen.'
        );
        expect(within(dialog).getByRole('status')).toHaveTextContent('Eine Auftragsbestätigung können Sie weiterhin anlegen.');
        expect(within(dialog).queryByText('Rechnungstyp wählen')).not.toBeInTheDocument();
        expect(within(dialog).queryByRole('button', { name: /^Rechnung erstellen$/ })).not.toBeInTheDocument();
    });

    it('bietet ohne abschliessende Rechnung die Auswahl wie bisher an', async () => {
        renderDialog(null);
        const dialog = await oeffneRechnungsDialog();

        expect(within(dialog).getByText('Rechnungstyp wählen')).toBeInTheDocument();
        expect(within(dialog).getByRole('button', { name: /Einfache Rechnung/ })).toBeInTheDocument();
        expect(within(dialog).queryByRole('status')).not.toBeInTheDocument();
    });
});
