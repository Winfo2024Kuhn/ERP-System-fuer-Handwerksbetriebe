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
];

describe('ArtikelAuswahlDialog', () => {
    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn(async (url: string) => {
            if (url.includes('/filteroptionen')) {
                return { ok: true, json: async () => ({ produktlinien: [], werkstoffe: [], profilformen: [] }) };
            }
            return { ok: true, json: async () => ({ artikel: TREFFER, gesamt: 2, seite: 0, seitenGroesse: 12 }) };
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
            beschreibungHtml: '',
            einzelpreis: 0,
        });
    });

    it('zeigt den Hinweis bei ungepflegten Artikeln schon in der Trefferzeile', async () => {
        render(
            <ArtikelAuswahlDialog offen onSchliessen={() => {}} onUebernehmen={() => {}} />,
            { wrapper: MemoryRouter },
        );
        expect(await screen.findByText('kein Preis')).toBeInTheDocument();
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
