import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KettenZuordnung, type KettenZuordnungDokument } from './KettenZuordnung';

/**
 * „Projekten zuordnen“ für eine Bestellung mit mehreren Rechnungen: Leiste
 * oben, Wechsel per Klick, nach dem Speichern weiter zur nächsten offenen.
 * Nur Dummy-Daten (DSGVO).
 */

const toastSuccess = vi.hoisted(() => vi.fn());
vi.mock('../../components/ui/toast', () => ({
    useToast: () => ({ error: vi.fn(), success: toastSuccess }),
}));
vi.mock('../../components/ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf">{url}</div>,
}));

const rechnung = (id: number, nummer: string, datum: string, extra: Partial<KettenZuordnungDokument> = {}): KettenZuordnungDokument => ({
    id, typ: 'RECHNUNG', dokumentNummer: nummer, dokumentDatum: datum, betragBrutto: 1200, betragNetto: 1008.4,
    dateiname: `${nummer}.pdf`, pdfUrl: `/pdf/${nummer}.pdf`, zugeordnet: false, lagerbestellung: false, ...extra,
});

const lieferschein: KettenZuordnungDokument = {
    id: 1, typ: 'LIEFERSCHEIN', dokumentNummer: 'LS-1', dokumentDatum: '2026-03-01', betragBrutto: null, dateiname: 'LS-1.pdf', pdfUrl: null,
};

function antwort(body: unknown, status = 200) {
    return Promise.resolve({ ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) } as Response);
}

/** Jede Rechnung hat schon ein Projekt (100 %) – so lässt sich sofort speichern. */
function richteFetchEin() {
    vi.stubGlobal('fetch', vi.fn((url: string, options?: RequestInit) => {
        const id = Number(/\/(\d+)$/.exec(url)?.[1]);
        if (url.includes('/geschaeftsdaten/')) {
            return antwort({ id, dokumentNummer: null, dokumentDatum: '2026-03-12', betragNetto: 1008.4, betragBrutto: 1200, lieferantName: 'Muster GmbH' });
        }
        if (url.includes('/zuordnungen/')) return antwort([{ projektId: 7, projektName: 'Carport Mustermann', prozentanteil: 100, betrag: 1008.4, beschreibung: '' }]);
        if (url.includes('/positionen/')) return antwort({}, 404);
        if (options?.method === 'POST') return antwort({ success: true });
        return antwort({});
    }));
}

const posts = () => vi.mocked(fetch).mock.calls
    .filter(([, options]) => (options as RequestInit | undefined)?.method === 'POST')
    .map(([url, options]) => ({ url: String(url), body: JSON.parse((options as RequestInit).body as string) }));

const leiste = () => screen.getByRole('group', { name: 'Rechnungen dieser Bestellung' });
const chip = (nummer: string) => within(leiste()).getByRole('button', { name: new RegExp(`^${nummer}`) });
const speichern = () => screen.getByRole('button', { name: /Zuordnen & Abschließen/ });

describe('KettenZuordnung – mehrere Rechnungen einer Bestellung', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        richteFetchEin();
    });
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt bei einer Rechnung keine Leiste und schließt nach dem Speichern', async () => {
        const onSchliessen = vi.fn();
        render(<KettenZuordnung kette={{ lieferantName: 'Muster GmbH', dokumente: [lieferschein, rechnung(11, 'RE-4711', '2026-03-12')] }} onSchliessen={onSchliessen} />);

        expect(await screen.findByRole('heading', { name: 'Rechnung RE-4711' })).toBeInTheDocument();
        expect(screen.queryByRole('group', { name: 'Rechnungen dieser Bestellung' })).toBeNull();
        await waitFor(() => expect(speichern()).toBeEnabled());
        await userEvent.click(speichern());

        await waitFor(() => expect(onSchliessen).toHaveBeenCalledWith(true));
        expect(toastSuccess).not.toHaveBeenCalled();
    });

    it('startet bei der ersten offenen Rechnung, zeigt den Stand je Rechnung und wechselt per Klick', async () => {
        render(<KettenZuordnung
            kette={{
                lieferantName: 'Muster GmbH',
                dokumente: [
                    rechnung(11, 'RE-4711', '2026-03-12', { zugeordnet: true }),
                    rechnung(12, 'RE-4712', '2026-03-20'),
                    rechnung(13, 'RE-4713', '2026-03-25', { lagerbestellung: true }),
                    rechnung(14, 'RE-4714', '2026-03-28', { ausgeblendet: true }),
                    { ...rechnung(15, 'GS-1', '2026-03-30'), typ: 'GUTSCHRIFT' },
                ],
            }}
            onSchliessen={vi.fn()}
        />);

        expect(await screen.findByRole('heading', { name: 'Rechnung RE-4712' })).toBeInTheDocument();
        // Gutschriften bleiben außen vor
        expect(within(leiste()).getAllByRole('button')).toHaveLength(4);
        expect(chip('RE-4711')).toHaveTextContent('RE-4711·12.03.2026·1.200,00 €·zugeordnet');
        expect(chip('RE-4712')).toHaveAttribute('aria-pressed', 'true');
        expect(chip('RE-4712')).toHaveTextContent('offen');
        expect(chip('RE-4713')).toHaveTextContent('Lager');
        expect(chip('RE-4714')).toHaveTextContent('ausgeblendet');
        expect(screen.getByText('noch 1 offen')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Mehrere Rechnungen zu einer Bestellung' })).toBeInTheDocument();

        // Wechsel lädt die Daten der anderen Rechnung
        await userEvent.click(chip('RE-4711'));
        expect(await screen.findByRole('heading', { name: 'Rechnung RE-4711' })).toBeInTheDocument();
        expect(chip('RE-4711')).toHaveAttribute('aria-pressed', 'true');
        expect(chip('RE-4711')).toHaveFocus();
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/bestellungen-uebersicht/geschaeftsdaten/11'));
    });

    it('springt nach dem Speichern zur nächsten offenen Rechnung und schließt nach der letzten', async () => {
        const onSchliessen = vi.fn();
        render(<KettenZuordnung
            kette={{ lieferantName: 'Muster GmbH', dokumente: [rechnung(11, 'RE-4711', '2026-03-12'), rechnung(12, 'RE-4712', '2026-03-20')] }}
            onSchliessen={onSchliessen}
        />);
        expect(await screen.findByRole('heading', { name: 'Rechnung RE-4711' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /^RE-4711/ })).toHaveTextContent('offen');

        await waitFor(() => expect(speichern()).toBeEnabled());
        await userEvent.click(speichern());

        // weiter mit der zweiten – die erste ist sofort als zugeordnet markiert
        expect(await screen.findByRole('heading', { name: 'Rechnung RE-4712' })).toBeInTheDocument();
        expect(chip('RE-4711')).toHaveTextContent('zugeordnet');
        expect(chip('RE-4712')).toHaveAttribute('aria-pressed', 'true');
        expect(toastSuccess).toHaveBeenCalledWith('Rechnung RE-4711 zugeordnet – weiter mit RE-4712.');
        expect(onSchliessen).not.toHaveBeenCalled();

        await waitFor(() => expect(speichern()).toBeEnabled());
        await userEvent.click(speichern());
        await waitFor(() => expect(onSchliessen).toHaveBeenCalledWith(true));
        expect(posts().map(p => p.body.geschaeftsdokumentId)).toEqual([11, 12]);
    });

    it('meldet beim Schließen ohne Speichern, dass nichts neu zu laden ist', async () => {
        const onSchliessen = vi.fn();
        render(<KettenZuordnung
            kette={{ lieferantName: 'Muster GmbH', dokumente: [rechnung(11, 'RE-4711', '2026-03-12'), rechnung(12, 'RE-4712', '2026-03-20')] }}
            onSchliessen={onSchliessen}
        />);
        await screen.findByRole('heading', { name: 'Rechnung RE-4711' });
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onSchliessen).toHaveBeenCalledWith(false);
    });

    it('zeigt ohne Rechnung nichts', () => {
        const { container } = render(<KettenZuordnung kette={{ lieferantName: null, dokumente: [lieferschein] }} onSchliessen={vi.fn()} />);
        expect(container).toBeEmptyDOMElement();
    });
});
