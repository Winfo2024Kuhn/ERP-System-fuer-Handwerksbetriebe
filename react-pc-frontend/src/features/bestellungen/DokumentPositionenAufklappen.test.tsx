import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import type { DokumentPosition } from '../../components/zuordnung/positionen';
import { DokumentPositionenListe, PositionenKnopf } from './DokumentPositionenAufklappen';
import { vergissDokumentPositionen } from './dokumentPositionen';

function position(id: number, extra: Partial<DokumentPosition> = {}): DokumentPosition {
    return {
        id, positionNr: id, positionsArt: 'WARE', externeArtikelnummer: `ART-${id}`, bezeichnung: `Rundrohr Nr. ${id}`,
        menge: 2.5, mengeneinheit: 'm', einzelpreis: 3.5, preiseinheit: null, gesamtpreisNetto: 8.75,
        projektId: null, projektName: null, kostenstelleId: null, kostenstelleName: null, ...extra,
    };
}

const antwort = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;

function zeigeListe(dokumentId = 7, suchbegriff?: string) {
    return render(<ToastProvider><DokumentPositionenListe id="bereich" dokumentId={dokumentId} suchbegriff={suchbegriff} /></ToastProvider>);
}

describe('DokumentPositionenListe', () => {
    beforeEach(() => {
        vergissDokumentPositionen();
        vi.stubGlobal('fetch', vi.fn());
    });
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt erst einen Ladezustand, dann die Positionen kompakt', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({ positionen: [position(1, { preiseinheit: '100 kg' }), position(2, { menge: null, mengeneinheit: null, einzelpreis: null, gesamtpreisNetto: null, externeArtikelnummer: ' ', bezeichnung: null })] }));
        zeigeListe();
        expect(screen.getByRole('status', { name: 'Artikelpositionen werden geladen' })).toBeInTheDocument();
        const tabelle = await screen.findByRole('table', { name: 'Artikelpositionen' });
        const zeilen = within(tabelle).getAllByRole('row');
        expect(within(tabelle).getAllByRole('columnheader').map(z => z.textContent)).toEqual(['Pos.', 'Art.-Nr.', 'Bezeichnung', 'Menge', 'Einzelpreis', 'Gesamt netto']);
        expect(zeilen[1]).toHaveTextContent('ART-1');
        expect(zeilen[1]).toHaveTextContent('Rundrohr Nr. 1');
        expect(zeilen[1]).toHaveTextContent('2,5 m');
        expect(zeilen[1]).toHaveTextContent('3,50 € / 100 kg');
        expect(zeilen[1]).toHaveTextContent('8,75 €');
        // Fehlende Angaben als Strich
        expect(within(zeilen[2]).getAllByText('–')).toHaveLength(5);
        // Ohne Werkstoffangaben keine graue Zusatzzeile
        expect(screen.queryByText(/Werkstoff/)).not.toBeInTheDocument();
    });

    it('zeigt Werkstoff, Charge und Abmessung nur, wo es welche gibt', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({ positionen: [
            position(1, { werkstoff: 'S235JR', charge: '123456', abmessung: '50 x 5' }),
            position(2, { werkstoff: null, charge: '778899', abmessung: '' }),
            position(3),
        ] }));
        zeigeListe();
        expect(await screen.findByText('Werkstoff S235JR · Charge 123456 · Abmessung 50 x 5')).toBeInTheDocument();
        expect(screen.getByText('Charge 778899')).toBeInTheDocument();
        expect(screen.getAllByText(/Charge/)).toHaveLength(2);
    });

    it('legt die Art.-Nr. in schmaler Ansicht unter die Bezeichnung', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({ positionen: [position(1, { werkstoff: 'S235JR' }), position(2, { externeArtikelnummer: null })] }));
        render(<ToastProvider><DokumentPositionenListe dokumentId={7} kompakt /></ToastProvider>);
        const tabelle = await screen.findByRole('table', { name: 'Artikelpositionen' });
        expect(within(tabelle).getAllByRole('columnheader').map(z => z.textContent)).toEqual(['Pos.', 'Bezeichnung', 'Menge', 'Einzelpreis', 'Gesamt']);
        expect(within(tabelle).getByText('Art.-Nr. ART-1 · Werkstoff S235JR')).toBeInTheDocument();
        expect(within(tabelle).getAllByRole('row')[2]).not.toHaveTextContent('Art.-Nr.');
    });

    it('hebt Positionen mit dem Suchbegriff dezent hervor', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({ positionen: [position(1), position(2, { bezeichnung: 'Flachstahl 50x5' })] }));
        zeigeListe(7, 'flachstahl');
        const tabelle = await screen.findByRole('table', { name: 'Artikelpositionen' });
        const zeilen = within(tabelle).getAllByRole('row').slice(1);
        expect(zeilen[0]).not.toHaveClass('bg-rose-50');
        expect(zeilen[1]).toHaveClass('bg-rose-50');
        expect(zeilen[1]).toHaveAttribute('data-treffer');
    });

    it('zeigt bei 404 den Leerzustand', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({}, 404));
        zeigeListe();
        expect(await screen.findByText('Keine Artikelpositionen erkannt.')).toBeInTheDocument();
    });

    it('meldet Fehler per Toast und als Text', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({}, 500));
        zeigeListe();
        expect(await screen.findAllByText('Artikelpositionen konnten nicht geladen werden.')).toHaveLength(2);
    });

    it('nimmt bei Netzwerkfehlern den eigenen Text', async () => {
        vi.mocked(fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'));
        zeigeListe();
        expect(await screen.findAllByText('Artikelpositionen konnten nicht geladen werden.')).toHaveLength(2);
        vi.mocked(fetch).mockRejectedValueOnce('kaputt');
        zeigeListe(8);
        await waitFor(() => expect(screen.getAllByText('Artikelpositionen konnten nicht geladen werden.').length).toBeGreaterThanOrEqual(3));
    });

    it('lädt dasselbe Dokument nur einmal, auch an zweiter Stelle', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({ positionen: [position(1)] }));
        const { unmount } = zeigeListe();
        await screen.findByRole('table', { name: 'Artikelpositionen' });
        unmount();
        zeigeListe();
        // Gemerkt: sofort da, ohne Ladezustand
        expect(screen.getByRole('table', { name: 'Artikelpositionen' })).toBeInTheDocument();
        expect(fetch).toHaveBeenCalledTimes(1);
    });

    it('lädt eine offene Liste neu, wenn die Positionen vergessen werden (z. B. nach dem Auslesen)', async () => {
        vi.mocked(fetch)
            .mockResolvedValueOnce(antwort({ positionen: [position(1)] }))
            .mockResolvedValueOnce(antwort({ positionen: [position(1), position(2)] }));
        zeigeListe(21);
        expect(await screen.findByText('Rundrohr Nr. 1')).toBeInTheDocument();
        expect(screen.queryByText('Rundrohr Nr. 2')).not.toBeInTheDocument();

        act(() => vergissDokumentPositionen(21));

        expect(await screen.findByText('Rundrohr Nr. 2')).toBeInTheDocument();
        expect(fetch).toHaveBeenCalledTimes(2);
    });

    it('bleibt beim Vergessen eines anderen Dokuments ruhig', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({ positionen: [position(1)] }));
        zeigeListe(22);
        expect(await screen.findByText('Rundrohr Nr. 1')).toBeInTheDocument();

        act(() => vergissDokumentPositionen(99));

        expect(screen.getByText('Rundrohr Nr. 1')).toBeInTheDocument();
        expect(fetch).toHaveBeenCalledTimes(1);
    });

    it('ignoriert Antworten, wenn die Liste schon wieder zu ist', async () => {
        let fertig: (r: Response) => void = () => undefined;
        vi.mocked(fetch).mockReturnValueOnce(new Promise<Response>(resolve => { fertig = resolve; }));
        const { unmount } = zeigeListe(11);
        unmount();
        fertig(antwort({}, 500));
        await new Promise(r => setTimeout(r, 0));
        expect(screen.queryByText('Artikelpositionen konnten nicht geladen werden.')).not.toBeInTheDocument();

        let fertig2: (r: Response) => void = () => undefined;
        vi.mocked(fetch).mockReturnValueOnce(new Promise<Response>(resolve => { fertig2 = resolve; }));
        const zweite = zeigeListe(12);
        zweite.unmount();
        fertig2(antwort({ positionen: [position(1)] }));
        await new Promise(r => setTimeout(r, 0));
        expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });
});

describe('PositionenKnopf', () => {
    it('klappt mit sprechendem Namen auf und zu, ohne den Klick weiterzugeben', async () => {
        const onUmschalten = vi.fn();
        const aussen = vi.fn();
        const { rerender } = render(
            <div onClick={aussen}><PositionenKnopf offen={false} onUmschalten={onUmschalten} dokumentName="Lieferschein LS-1" bereichId="b1" /></div>,
        );
        const knopf = screen.getByRole('button', { name: 'Positionen von Lieferschein LS-1 anzeigen' });
        expect(knopf).toHaveAttribute('aria-expanded', 'false');
        expect(knopf).toHaveAttribute('aria-controls', 'b1');
        await userEvent.click(knopf);
        expect(onUmschalten).toHaveBeenCalledTimes(1);
        expect(aussen).not.toHaveBeenCalled();

        rerender(<div onClick={aussen}><PositionenKnopf offen onUmschalten={onUmschalten} dokumentName="Lieferschein LS-1" bereichId="b1" /></div>);
        expect(screen.getByRole('button', { name: 'Positionen von Lieferschein LS-1 ausblenden' })).toHaveAttribute('aria-expanded', 'true');
    });
});
