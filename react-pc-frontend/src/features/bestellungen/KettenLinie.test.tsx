import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { KettenLinie, type KettenLinieProps } from './KettenLinie';
import type { KettenLinienDokument } from './kettenLinie';
import { vergissDokumentPositionen } from './dokumentPositionen';

function dok(id: number, typ: KettenLinienDokument['typ'], nummer: string, datum: string | null, extra: Partial<KettenLinienDokument> = {}): KettenLinienDokument {
    return {
        id, typ, dokumentNummer: nummer, dokumentDatum: datum, betragBrutto: null, liefertermin: null,
        dateiname: `${nummer}.pdf`, pdfUrl: `/pdf/${nummer}`, ...extra,
    };
}

// Krönlein-Beispiel mit Dummy-Nummern
const AB = dok(1, 'AUFTRAGSBESTAETIGUNG', 'AB-55', '2026-03-14', { betragBrutto: 1250, liefertermin: '2026-03-25' });
const LS1 = dok(2, 'LIEFERSCHEIN', 'LS-1', '2026-03-20');
const LS2 = dok(3, 'LIEFERSCHEIN', 'LS-2', '2026-03-27');
const WZ = dok(4, 'WERKSTOFFZEUGNIS', '4107891', null, { eingangsDatum: '2026-03-20' });
const RE = dok(5, 'RECHNUNG', 'R-900', '2026-04-02', { betragBrutto: 1250 });
const VERBINDUNGEN = [{ vonId: 2, zuId: 1 }, { vonId: 3, zuId: 1 }, { vonId: 4, zuId: 2 }, { vonId: 5, zuId: 3 }];

function zeige(props: Partial<KettenLinieProps> = {}, mitProvidern = true) {
    const onOpenPdf = vi.fn();
    const onGeaendert = vi.fn();
    const onRechnungSuchen = vi.fn();
    const linie = (
        <KettenLinie
            dokumente={[RE, LS2, WZ, AB, LS1]}
            verbindungen={VERBINDUNGEN}
            onOpenPdf={onOpenPdf}
            onGeaendert={onGeaendert}
            {...props}
        />
    );
    render(mitProvidern ? <ToastProvider><ConfirmProvider>{linie}</ConfirmProvider></ToastProvider> : linie);
    return { onOpenPdf, onGeaendert, onRechnungSuchen };
}

const zeilenTexte = () => within(screen.getByTestId('ketten-linie')).getAllByRole('listitem').map(z => z.textContent ?? '');

describe('KettenLinie', () => {
    beforeEach(() => {
        vergissDokumentPositionen();
        vi.stubGlobal('fetch', vi.fn());
    });
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt alle Belege auf einer Linie, Zeugnis unter seinem Lieferschein, und öffnet das PDF', async () => {
        const { onOpenPdf } = zeige();
        expect(screen.getByRole('list', { name: 'Belege der Kette' })).toBeInTheDocument();
        expect(zeilenTexte()).toEqual([
            expect.stringContaining('AB-55'),
            expect.stringContaining('LS-1'),
            expect.stringContaining('4107891'),
            expect.stringContaining('LS-2'),
            expect.stringContaining('R-900'),
        ]);
        // Liefertermin bei der AB, Betrag rechts
        expect(screen.getByText(/Liefertermin 25\.0?3\.2026/)).toBeInTheDocument();
        expect(screen.getAllByText('1.250,00 €')).toHaveLength(2);
        expect(screen.getAllByText('1.250,00 €')[0]).toHaveAttribute('title', 'Betrag brutto');

        // Eine gerade Linie: fünf Punkte, eine Rechnung hervorgehoben, keine gestrichelten Stücke
        const linie = screen.getByTestId('ketten-linie');
        expect(linie.querySelectorAll('[data-punkt]')).toHaveLength(5);
        expect(linie.querySelectorAll('[data-punkt="rechnung"]')).toHaveLength(1);
        expect(linie.querySelector('[data-punkt="rechnung"]')).toHaveClass('bg-emerald-500');
        expect(linie.querySelector('[data-punkt="beleg"]')).toHaveClass('border-rose-500');
        // 5 Punkte → 4 Abstände, je ein Stück unten und oben
        expect(linie.querySelectorAll('[data-linie="durchgehend"]')).toHaveLength(8);
        expect(linie.querySelectorAll('[data-linie="gestrichelt"]')).toHaveLength(0);

        await userEvent.click(screen.getByRole('button', { name: /^Lieferschein LS-2 \d/ }));
        expect(onOpenPdf).toHaveBeenCalledWith('/pdf/LS-2', 'LS-2');
    });

    it('zeigt das Eingangsdatum mit Hinweis, wenn kein Dokumentdatum erkannt wurde', () => {
        zeige({ dokumente: [WZ] });
        const hinweis = screen.getByText('Eingang');
        expect(hinweis.parentElement).toHaveAttribute('title', 'Kein Dokumentdatum erkannt – Eingangsdatum');
        expect(hinweis.parentElement?.textContent).toMatch(/20\.0?3\.2026/);
    });

    it('zeigt ausgeblendete Belege gedämpft mit Hinweis und öffnet sie trotzdem', async () => {
        const versteckt = dok(9, 'RECHNUNG', 'RE-9', '2026-09-02', { ausgeblendet: true });
        const versteckterLs = dok(8, 'LIEFERSCHEIN', 'LS-8', '2026-09-01', { ausgeblendet: true });
        const { onOpenPdf } = zeige({ dokumente: [AB, versteckterLs, versteckt], verbindungen: [] });
        expect(screen.getAllByText('ausgeblendet')).toHaveLength(2);
        const linie = screen.getByTestId('ketten-linie');
        expect(linie.querySelector('[data-punkt="rechnung"]')).toHaveClass('bg-slate-300');
        expect(linie.querySelectorAll('[data-punkt="beleg"]')[1]).toHaveClass('border-slate-300');
        await userEvent.click(screen.getByRole('button', { name: /^Rechnung RE-9 \d/ }));
        expect(onOpenPdf).toHaveBeenCalledWith('/pdf/RE-9', 'RE-9');
    });

    it('sperrt Zeilen ohne Vorschau und nimmt sonst Dateiname oder Art als Titel', async () => {
        const ohneNummer = dok(6, 'SONSTIG', '', '2026-08-01', { dokumentNummer: null, dateiname: null, pdfUrl: '/pdf/x' });
        const { onOpenPdf } = zeige({ dokumente: [dok(7, 'LIEFERSCHEIN', 'LS-7', '2026-08-01', { pdfUrl: null }), ohneNummer], verbindungen: null });
        expect(screen.getByRole('button', { name: /^Lieferschein LS-7 \d/ })).toBeDisabled();
        expect(screen.getByRole('button', { name: /^Lieferschein LS-7 \d/ })).toHaveAttribute('title', 'Keine Vorschau vorhanden');
        await userEvent.click(screen.getByRole('button', { name: /^Sonstiges \d/ }));
        expect(onOpenPdf).toHaveBeenCalledWith('/pdf/x', 'Sonstiges');
    });

    it('ruft statt des PDFs den Zeilen-Klick auf und zeigt eine Zusatzzeile', async () => {
        const onZeileKlick = vi.fn();
        zeige({
            dokumente: [dok(7, 'LIEFERSCHEIN', 'LS-7', null, { pdfUrl: null })],
            onOpenPdf: undefined,
            onZeileKlick,
            zeilenZusatz: d => `Ref: REF-${d.id}`,
        }, false);
        const knopf = screen.getByRole('button', { name: /^Lieferschein LS-7/ });
        expect(knopf).toBeEnabled();
        expect(within(knopf).getByText('Ref: REF-7')).toBeInTheDocument();
        expect(within(knopf).getByText('–')).toBeInTheDocument();
        await userEvent.click(knopf);
        expect(onZeileKlick).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }));
    });

    it('zeigt unlesbare Daten als Strich, lässt ungültige Beträge weg und dämpft ausgeblendete ABs', () => {
        zeige({
            dokumente: [
                dok(20, 'AUFTRAGSBESTAETIGUNG', 'AB-9', '2026-03-01', { ausgeblendet: true, liefertermin: '2026-03-10', betragBrutto: 50 }),
                dok(21, 'LIEFERSCHEIN', 'LS-9', 'kein Datum', { betragBrutto: Number.POSITIVE_INFINITY }),
            ],
            verbindungen: [],
        }, false);
        const ab = screen.getByText(/Liefertermin 10\.0?3\.2026/).closest('span[title]') as HTMLElement;
        expect(ab).not.toHaveClass('text-rose-600');
        expect(screen.getByText('50,00 €')).not.toHaveClass('font-medium');
        const ls = screen.getByRole('button', { name: /^Lieferschein LS-9/ });
        expect(within(ls).getByText('–')).toBeInTheDocument();
        expect(ls.textContent).not.toContain('€');
    });

    it('zeigt auf Wunsch den Nettobetrag', () => {
        zeige({ dokumente: [dok(7, 'RECHNUNG', 'RE-7', '2026-08-01', { betragBrutto: 119, betragNetto: 100 })], betrag: 'netto' }, false);
        expect(screen.getByText('100,00 €')).toHaveAttribute('title', 'Betrag netto');
        expect(screen.queryByText('119,00 €')).not.toBeInTheDocument();
    });

    it('bietet Abhängen nur bei verknüpften Belegen an und ruft nach Bestätigung die API', async () => {
        vi.mocked(fetch).mockResolvedValueOnce({ ok: true, json: async () => ({ geloest: 1 }) } as Response);
        const lose = dok(10, 'SONSTIG', 'SO-1', '2026-03-30');
        const { onGeaendert } = zeige({ dokumente: [AB, LS1, lose], verbindungen: [{ vonId: 2, zuId: 1 }], abhaengbar: true });
        expect(screen.queryByRole('button', { name: /Sonstiges SO-1 von der Bestellung abhängen/ })).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Lieferschein LS-1 von der Bestellung abhängen' }));
        const frage = await screen.findByRole('dialog', { name: 'Beleg abhängen?' });
        expect(within(frage).getByText('Dieser Beleg wird von der Bestellung gelöst und nicht mehr automatisch zugeordnet.')).toBeInTheDocument();
        await userEvent.click(within(frage).getByRole('button', { name: 'Abhängen' }));

        await waitFor(() => expect(onGeaendert).toHaveBeenCalled());
        const [url, init] = vi.mocked(fetch).mock.calls[0];
        expect(url).toBe('/api/bestellungen-uebersicht/abhaengen');
        expect(JSON.parse((init as RequestInit).body as string)).toEqual({ dokumentId: 2 });
        expect(await screen.findByText('Lieferschein LS-1 abgehängt.')).toBeInTheDocument();
    });

    it('hängt nichts ab, wenn die Rückfrage abgebrochen wird, und meldet Fehler', async () => {
        const { onGeaendert } = zeige({ dokumente: [AB, LS1], verbindungen: [{ vonId: 2, zuId: 1 }], abhaengbar: true });
        await userEvent.click(screen.getByRole('button', { name: 'Auftragsbestätigung AB-55 von der Bestellung abhängen' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Beleg abhängen?' })).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Beleg abhängen?' })).not.toBeInTheDocument());
        expect(fetch).not.toHaveBeenCalled();

        vi.mocked(fetch).mockResolvedValueOnce({ ok: false, json: async () => ({ message: 'Beleg nicht gefunden' }) } as Response);
        await userEvent.click(screen.getByRole('button', { name: 'Auftragsbestätigung AB-55 von der Bestellung abhängen' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Beleg abhängen?' })).getByRole('button', { name: 'Abhängen' }));
        expect(await screen.findByText('Beleg nicht gefunden')).toBeInTheDocument();
        expect(onGeaendert).not.toHaveBeenCalled();
    });

    it('meldet einen unbekannten Abhängen-Fehler mit eigenem Text und sperrt währenddessen alle Knöpfe', async () => {
        let fertig: (wert: Response) => void = () => undefined;
        vi.mocked(fetch).mockReturnValueOnce(new Promise<Response>(resolve => { fertig = resolve; }));
        zeige({ dokumente: [AB, LS1], verbindungen: [{ vonId: 2, zuId: 1 }], abhaengbar: true, onGeaendert: undefined });
        await userEvent.click(screen.getByRole('button', { name: 'Lieferschein LS-1 von der Bestellung abhängen' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Beleg abhängen?' })).getByRole('button', { name: 'Abhängen' }));
        await waitFor(() => expect(screen.getByRole('button', { name: 'Auftragsbestätigung AB-55 von der Bestellung abhängen' })).toBeDisabled());
        fertig({ ok: true, json: async () => ({}) } as Response);
        await waitFor(() => expect(screen.getByRole('button', { name: 'Auftragsbestätigung AB-55 von der Bestellung abhängen' })).toBeEnabled());

        vi.mocked(fetch).mockRejectedValueOnce('kaputt');
        await userEvent.click(screen.getByRole('button', { name: 'Lieferschein LS-1 von der Bestellung abhängen' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Beleg abhängen?' })).getByRole('button', { name: 'Abhängen' }));
        expect(await screen.findByText('Der Beleg konnte nicht abgehängt werden.')).toBeInTheDocument();
    });

    it('zeigt ohne Rechnung ein gestricheltes offenes Ende und lädt zum jüngsten Bestelldokument hoch', async () => {
        vi.mocked(fetch).mockResolvedValueOnce({ ok: true, json: async () => ({ id: 99, typ: 'RECHNUNG' }) } as Response);
        const onRechnungSuchen = vi.fn();
        const { onGeaendert } = zeige({ dokumente: [AB, LS1, WZ, LS2], offenesEnde: true, onRechnungSuchen });
        expect(screen.getByText('Rechnung fehlt noch')).toBeInTheDocument();
        const linie = screen.getByTestId('ketten-linie');
        expect(linie.querySelectorAll('[data-punkt="offen"]')).toHaveLength(1);
        // Letzter Beleg unten und offenes Ende oben: gestrichelt
        expect(linie.querySelectorAll('[data-linie="gestrichelt"]')).toHaveLength(2);

        await userEvent.click(screen.getByRole('button', { name: 'Suchen' }));
        expect(onRechnungSuchen).toHaveBeenCalled();

        await userEvent.upload(screen.getByTestId('rechnung-datei'), new File(['%PDF'], 'rechnung.pdf', { type: 'application/pdf' }));
        await waitFor(() => expect(onGeaendert).toHaveBeenCalled());
        const [url, init] = vi.mocked(fetch).mock.calls[0];
        expect(url).toBe('/api/bestellungen-uebersicht/rechnung-hochladen');
        expect(((init as RequestInit).body as FormData).get('bestellDokumentId')).toBe('3');
    });

    it('lädt auch ohne Rückmeldungs-Funktion hoch', async () => {
        vi.mocked(fetch).mockResolvedValueOnce({ ok: true, json: async () => ({ id: 99, typ: 'RECHNUNG' }) } as Response);
        zeige({ dokumente: [LS1], offenesEnde: true, onRechnungSuchen: vi.fn(), onGeaendert: undefined });
        await userEvent.upload(screen.getByTestId('rechnung-datei'), new File(['%PDF'], 'rechnung.pdf', { type: 'application/pdf' }));
        expect(await screen.findByText(/Rechnung hochgeladen und zugeordnet/)).toBeInTheDocument();
    });

    it('zeigt das offene Ende ohne Knöpfe, wenn keine Suche angeboten wird', () => {
        zeige({ dokumente: [AB], offenesEnde: true }, false);
        expect(screen.getByText('Rechnung fehlt noch')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Rechnung hochladen/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Suchen' })).not.toBeInTheDocument();
    });

    it('nennt die Liste wie gewünscht und zeigt ohne Belege nichts', () => {
        zeige({ dokumente: [AB], listenName: 'Belege der Bestellung' }, false);
        expect(screen.getByRole('list', { name: 'Belege der Bestellung' })).toBeInTheDocument();
    });

    it('zeigt nichts ohne Belege', () => {
        zeige({ dokumente: [] }, false);
        expect(screen.queryByTestId('ketten-linie')).not.toBeInTheDocument();
    });

    describe('Artikelpositionen aufklappen', () => {
        const positionen = [
            { id: 1, positionNr: 1, positionsArt: 'WARE', externeArtikelnummer: 'ART-1', bezeichnung: 'Flachstahl 50x5', menge: 12, mengeneinheit: 'Stk',
                einzelpreis: 3.5, preiseinheit: null, gesamtpreisNetto: 42, projektId: null, projektName: null, kostenstelleId: null, kostenstelleName: null },
        ];

        it('klappt die Positionen unter der Zeile auf, ohne das PDF zu öffnen, und lädt nur einmal', async () => {
            vi.mocked(fetch).mockResolvedValue({ ok: true, status: 200, json: async () => ({ positionen }) } as Response);
            const { onOpenPdf } = zeige({ suchbegriff: 'flachstahl' });
            const knopf = screen.getByRole('button', { name: 'Positionen von Lieferschein LS-1 anzeigen' });
            expect(knopf).toHaveAttribute('aria-expanded', 'false');
            await userEvent.click(knopf);

            const zeile = knopf.closest('li') as HTMLElement;
            const tabelle = await within(zeile).findByRole('table', { name: 'Artikelpositionen' });
            expect(tabelle).toHaveTextContent('Flachstahl 50x5');
            expect(tabelle.closest('[id]')).toHaveAttribute('id', knopf.getAttribute('aria-controls'));
            expect(within(tabelle).getAllByRole('row')[1]).toHaveClass('bg-rose-50');
            expect(onOpenPdf).not.toHaveBeenCalled();
            expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/bestellungen-uebersicht/positionen/2');
            // Linie läuft neben den Positionen weiter (unteres Stück reicht bis zum Zeilenende)
            expect(zeile.querySelector('[data-linie="durchgehend"]')).toBeInTheDocument();

            // Mehrere gleichzeitig offen
            await userEvent.click(screen.getByRole('button', { name: 'Positionen von Rechnung R-900 anzeigen' }));
            await waitFor(() => expect(screen.getAllByRole('table', { name: 'Artikelpositionen' })).toHaveLength(2));

            await userEvent.click(screen.getByRole('button', { name: 'Positionen von Lieferschein LS-1 ausblenden' }));
            expect(within(zeile).queryByRole('table')).not.toBeInTheDocument();
            await userEvent.click(screen.getByRole('button', { name: 'Positionen von Lieferschein LS-1 anzeigen' }));
            expect(within(zeile).getByRole('table', { name: 'Artikelpositionen' })).toBeInTheDocument();
            expect(fetch).toHaveBeenCalledTimes(2);
        });

        it('zeigt keinen Pfeil bei Sonstigem', () => {
            zeige({ dokumente: [AB, dok(10, 'SONSTIG', 'SO-1', '2026-03-30')], verbindungen: [] }, false);
            expect(screen.getByRole('button', { name: 'Positionen von Auftragsbestätigung AB-55 anzeigen' })).toBeInTheDocument();
            expect(screen.queryByRole('button', { name: /Positionen von Sonstiges/ })).not.toBeInTheDocument();
        });
    });
});
