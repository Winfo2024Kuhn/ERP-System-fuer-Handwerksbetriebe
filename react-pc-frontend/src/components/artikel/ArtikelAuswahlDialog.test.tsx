import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ArtikelAuswahlDialog } from './ArtikelAuswahlDialog';

const TREFFER = [
    {
        id: 7, produktname: 'T-Stahl', abmessung: '40 x 40 x 5',
        kurzbeschreibung: 'T-Stahl 40x40 Lager',
        beschreibung: '<p>T-Stahl 40 x 40 x 5 mm, verzinkt</p>',
        positionsEinheit: 'lfm', positionsEinzelpreis: 8.4, preisHinweis: 'OK',
        // guenstigsterPreis gehoert zur Standardspalte von ArtikelSuche, nicht zur
        // Positionsberechnung. Ohne ihn faellt auch diese Spalte auf "kein Preis"
        // zurueck und kollidiert mit dem Hinweis-Badge unten im selben Test.
        guenstigsterPreis: 7.9,
    },
    {
        id: 8, produktname: 'Vierkantrohr', abmessung: '40 x 40 x 2',
        positionsEinheit: 'lfm', preisHinweis: 'KEIN_PREIS',
        guenstigsterPreis: 6.2,
    },
    {
        // Kryptisches Lieferantenkuerzel ohne Profilform: Daraus laesst sich kein
        // Satz bauen, den ein Kunde lesen sollte. Diese Zeile deckt den Fall ab,
        // in dem die Position bewusst ohne Kundentext eingefuegt wird.
        id: 9, produktname: 'RO 42,4X2,0',
        positionsEinheit: 'lfm', positionsEinzelpreis: 5.5, preisHinweis: 'OK',
        guenstigsterPreis: 4.1,
    },
];

describe('ArtikelAuswahlDialog', () => {
    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn(async (url: string) => {
            if (url.includes('/filteroptionen')) {
                return { ok: true, json: async () => ({ produktlinien: [], werkstoffe: [], profilformen: [] }) };
            }
            return { ok: true, json: async () => ({ artikel: TREFFER, gesamt: TREFFER.length, seite: 0, seitenGroesse: 12 }) };
        }));
    });

    it('rendert nichts, solange es geschlossen ist', () => {
        const { container } = render(
            <ArtikelAuswahlDialog offen={false} onSchliessen={() => {}} onUebernehmen={() => {}} />,
            { wrapper: MemoryRouter },
        );
        expect(container).toBeEmptyDOMElement();
    });

    it('übernimmt einen Artikel mit Menge, Einheit und Preis', async () => {
        const onUebernehmen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={onUebernehmen} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('T-Stahl auswählen'));
        await userEvent.clear(screen.getByLabelText('Menge für T-Stahl'));
        await userEvent.type(screen.getByLabelText('Menge für T-Stahl'), '12');
        await userEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(onUebernehmen).toHaveBeenCalledWith([{
            artikelId: 7,
            titel: 'T-Stahl 40x40 Lager',
            beschreibungHtml: '<p>T-Stahl 40 x 40 x 5 mm, verzinkt</p>',
            menge: 12,
            einheit: 'lfm',
            einzelpreis: 8.4,
        }]);
    });

    it('nimmt mehrere Artikel auf einmal', async () => {
        const onUebernehmen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={onUebernehmen} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('T-Stahl auswählen'));
        await userEvent.click(screen.getByLabelText('Vierkantrohr auswählen'));
        await userEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(onUebernehmen.mock.calls[0][0]).toHaveLength(2);
    });

    it('fällt ohne Kurzbeschreibung auf Produktname und Abmessung zurück', async () => {
        const onUebernehmen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={onUebernehmen} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('Vierkantrohr auswählen'));
        await userEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(onUebernehmen.mock.calls[0][0][0]).toMatchObject({
            titel: 'Vierkantrohr 40 x 40 x 2',
            einzelpreis: 0,
        });
    });

    it('baut den Kundentext aus den Stammdaten, wenn am Artikel keiner gepflegt ist', async () => {
        // Ohne diesen Ersatztext bliebe die description leer - und der PDF-Druck
        // faellt dann auf den title zurueck, also auf die Innensicht.
        const onUebernehmen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={onUebernehmen} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('Vierkantrohr auswählen'));
        await userEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(onUebernehmen.mock.calls[0][0][0].beschreibungHtml)
            .toBe('<p>Vierkantrohr, 40 x 40 x 2 mm</p>');
    });

    it('lässt den Kundentext leer, wenn die Stammdaten nichts Kundentaugliches hergeben', async () => {
        const onUebernehmen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={onUebernehmen} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('RO 42,4X2,0 auswählen'));
        await userEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        // "RO 42,4X2,0" ist kein Satz fuer ein Angebot - lieber gar kein Text,
        // auf den der Editor sichtbar hinweist.
        expect(onUebernehmen.mock.calls[0][0][0].beschreibungHtml).toBe('');
    });

    it('lässt einen gepflegten Kundentext unangetastet', async () => {
        const onUebernehmen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={onUebernehmen} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('T-Stahl auswählen'));
        await userEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(onUebernehmen.mock.calls[0][0][0].beschreibungHtml)
            .toBe('<p>T-Stahl 40 x 40 x 5 mm, verzinkt</p>');
    });

    it('zeigt den Hinweis bei ungepflegten Artikeln schon in der Trefferzeile', async () => {
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={() => {}} />,
            { wrapper: MemoryRouter },
        );
        expect(await screen.findByText('kein Preis')).toBeInTheDocument();
    });

    it('zeigt fehlenden Kundentext vor dem Übernehmen an — und nur dort, wo er fehlt', async () => {
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={() => {}} />,
            { wrapper: MemoryRouter },
        );

        // Vierkantrohr (id 8) und RO 42,4X2,0 (id 9) haben keinen Kundentext,
        // T-Stahl (id 7) hat einen.
        expect(await screen.findAllByText('kein Kundentext')).toHaveLength(2);
    });

    // ------------------------------------------------------------------
    // Menge: 0 und negative Werte duerfen nie in eine Position wandern
    // ------------------------------------------------------------------

    it('sperrt Übernehmen, solange das Mengenfeld leer ist', async () => {
        // Ein geleertes Zahlenfeld liefert Number('') === 0 - daraus wuerde eine
        // Position mit Zeilensumme 0.
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={() => {}} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('T-Stahl auswählen'));
        await userEvent.clear(screen.getByLabelText('Menge für T-Stahl'));

        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeDisabled();
        expect(screen.getByText(/Menge größer 0/)).toBeInTheDocument();
    });

    it('sperrt Übernehmen bei einer negativen Menge', async () => {
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={() => {}} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('T-Stahl auswählen'));
        const feld = screen.getByLabelText('Menge für T-Stahl');
        await userEvent.clear(feld);
        await userEvent.type(feld, '-5');

        // Ohne diese Zusicherung koennte der Test auch dann gruen sein, wenn das
        // Feld gar keine -5 angenommen haette und schlicht leer waere.
        expect(feld).toHaveValue(-5);
        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeDisabled();
    });

    it('behält den letzten gültigen Wert, wenn zwischendurch nichts Gültiges dasteht', async () => {
        const onUebernehmen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={onUebernehmen} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('T-Stahl auswählen'));
        const feld = screen.getByLabelText('Menge für T-Stahl');
        await userEvent.clear(feld);
        await userEvent.type(feld, '7');
        await userEvent.clear(feld);
        await userEvent.type(feld, '3');
        await userEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(onUebernehmen.mock.calls[0][0][0].menge).toBe(3);
    });

    it('gibt Übernehmen wieder frei, sobald eine gültige Menge dasteht', async () => {
        const onUebernehmen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={onUebernehmen} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('T-Stahl auswählen'));
        const feld = screen.getByLabelText('Menge für T-Stahl');
        await userEvent.clear(feld);
        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeDisabled();

        await userEvent.type(feld, '2.5');
        await userEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(onUebernehmen.mock.calls[0][0][0].menge).toBe(2.5);
    });

    it('lässt eine ungültige Menge in einer anderen Zeile das Übernehmen blockieren', async () => {
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={() => {}} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('T-Stahl auswählen'));
        await userEvent.click(screen.getByLabelText('Vierkantrohr auswählen'));
        await userEvent.clear(screen.getByLabelText('Menge für Vierkantrohr'));

        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeDisabled();
    });

    it('schließt mit Escape', async () => {
        const onSchliessen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={onSchliessen} onUebernehmen={() => {}} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.keyboard('{Escape}');
        await waitFor(() => expect(onSchliessen).toHaveBeenCalled());
    });

    it('schaltet die Auswahl per Zeilenklick um, statt aus dem Fenster heraus zu navigieren', async () => {
        // Regression: Ohne onZeilenKlick fiel ArtikelSuche auf
        // navigate('/artikel/...') zurueck - der Klick irgendwo auf die Zeile
        // (ausserhalb der Checkbox-Zelle) warf den Dokument-Editor samt
        // ungespeicherter Arbeit weg.
        const onUebernehmen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={onUebernehmen} />,
            { wrapper: MemoryRouter },
        );

        const zeile = await screen.findByRole('button', { name: 'T-Stahl an- oder abwählen' });
        await userEvent.click(zeile);
        expect(screen.getByLabelText('T-Stahl auswählen')).toBeChecked();

        // Der zweite Klick waehlt wieder ab - Zeile und Checkbox meinen dasselbe.
        await userEvent.click(zeile);
        expect(screen.getByLabelText('T-Stahl auswählen')).not.toBeChecked();
    });

    it('Escape im Lieferanten-Fenster schließt nur dieses, nicht den ganzen Dialog', async () => {
        // Regression: Der Escape-Zuhoerer des Dialogs haengt an window und warf
        // frueher auch dann das ganze Fenster (samt angehakter Artikel) weg,
        // wenn der Bediener nur den Lieferanten-Picker schliessen wollte.
        const onSchliessen = vi.fn();
        render(
            <ArtikelAuswahlDialog offen onSchliessen={onSchliessen} onUebernehmen={() => {}} />,
            { wrapper: MemoryRouter },
        );

        await userEvent.click(await screen.findByLabelText('T-Stahl auswählen'));
        await userEvent.click(screen.getByText('Alle Lieferanten'));
        expect(await screen.findByRole('heading', { name: 'Lieferant auswählen' })).toBeInTheDocument();

        await userEvent.keyboard('{Escape}');

        await waitFor(() =>
            expect(screen.queryByRole('heading', { name: 'Lieferant auswählen' })).not.toBeInTheDocument());
        expect(onSchliessen).not.toHaveBeenCalled();
        // Die Auswahl hat das Untermodal ueberlebt.
        expect(screen.getByLabelText('T-Stahl auswählen')).toBeChecked();
    });

    // ------------------------------------------------------------------
    // Bedienbarkeit ohne Maus
    // ------------------------------------------------------------------

    describe('Tastatur und Screenreader', () => {
        /** Knopf + Fenster, wie im DocumentEditor - fuer den Rueckgabe-Fokus. */
        function Buehne() {
            const [offen, setOffen] = useState(false);
            return (
                <MemoryRouter>
                    <button onClick={() => setOffen(true)}>Material</button>
                    <ArtikelAuswahlDialog
                        offen={offen}
                        onSchliessen={() => setOffen(false)}
                        onUebernehmen={() => setOffen(false)}
                    />
                </MemoryRouter>
            );
        }

        it('meldet sich als Dialog mit Überschrift an', () => {
            render(
                <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={() => {}} />,
                { wrapper: MemoryRouter },
            );

            const dialog = screen.getByRole('dialog', { name: 'Material auswählen' });
            expect(dialog).toHaveAttribute('aria-modal', 'true');
        });

        it('setzt den Fokus beim Öffnen ins Suchfeld', async () => {
            render(<Buehne />);

            await userEvent.click(screen.getByRole('button', { name: 'Material' }));

            await waitFor(() =>
                expect(document.activeElement).toBe(screen.getByLabelText('Freitext')));
        });

        it('lässt Tab nicht aus dem Fenster heraus', async () => {
            render(
                <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={() => {}} />,
                { wrapper: MemoryRouter },
            );
            await screen.findByLabelText('T-Stahl auswählen');

            const dialog = screen.getByRole('dialog');
            const bedienbar = Array.from(dialog.querySelectorAll<HTMLElement>(
                'a[href], button:not([disabled]), input:not([disabled]), '
                + 'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'));
            const erstes = bedienbar[0];
            const letztes = bedienbar[bedienbar.length - 1];

            letztes.focus();
            await userEvent.tab();
            expect(document.activeElement).toBe(erstes);

            await userEvent.tab({ shift: true });
            expect(document.activeElement).toBe(letztes);
        });

        it('gibt den Fokus beim Schließen an den auslösenden Knopf zurück', async () => {
            render(<Buehne />);
            const knopf = screen.getByRole('button', { name: 'Material' });

            await userEvent.click(knopf);
            await waitFor(() =>
                expect(document.activeElement).toBe(screen.getByLabelText('Freitext')));

            await userEvent.keyboard('{Escape}');

            await waitFor(() => expect(document.activeElement).toBe(knopf));
        });
    });
});
