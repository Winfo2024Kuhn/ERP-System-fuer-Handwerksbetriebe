import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
});
