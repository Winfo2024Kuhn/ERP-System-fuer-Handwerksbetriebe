import { useState } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BundeslandAuswahl, type FeiertageVorschau } from './BundeslandAuswahl';

const NAMEN: Record<string, string> = { BY: 'Bayern', NW: 'Nordrhein-Westfalen' };

function antwort(url: string): FeiertageVorschau {
    const params = new URL(url, 'http://localhost').searchParams;
    const plz = params.get('plz');
    const land = params.get('bundesland') ?? 'BY';
    return {
        vorgeschlagenesBundesland: plz === '97259' ? 'BY' : plz === '50667' ? 'NW' : null,
        bundesland: land,
        bundeslandName: NAMEN[land] ?? land,
        jahr: 2026,
        feiertage: land === 'BY'
            ? [{ datum: '2026-01-01', bezeichnung: 'Neujahr' }, { datum: '2026-08-15', bezeichnung: 'Mariä Himmelfahrt' }]
            : [{ datum: '2026-01-01', bezeichnung: 'Neujahr' }],
        hinweise: land === 'BY' ? ['Mariä Himmelfahrt gilt nur in Gemeinden mit überwiegend katholischer Bevölkerung.'] : [],
    };
}

function Formular({ startPlz = '', startLand = 'NW', onLand }: { startPlz?: string; startLand?: string; onLand?: (land: string) => void }) {
    const [plz, setPlz] = useState(startPlz);
    const [land, setLand] = useState(startLand);
    return (
        <MemoryRouter>
            <label>PLZ<input value={plz} onChange={e => setPlz(e.target.value)} /></label>
            <BundeslandAuswahl plz={plz} value={land} onChange={l => { setLand(l); onLand?.(l); }} />
        </MemoryRouter>
    );
}

describe('BundeslandAuswahl', () => {
    const fetchMock = vi.fn();
    beforeEach(() => {
        fetchMock.mockReset();
        fetchMock.mockImplementation(async (url: string) => ({ ok: true, json: async () => antwort(url) }));
        vi.stubGlobal('fetch', fetchMock);
    });
    afterEach(() => { vi.unstubAllGlobals(); });

    it('schlägt nach Eingabe einer fünfstelligen PLZ das Bundesland vor und übernimmt es', async () => {
        const user = userEvent.setup();
        const onLand = vi.fn();
        render(<Formular onLand={onLand} />);
        await user.type(screen.getByRole('textbox', { name: 'PLZ' }), '9725');
        expect(screen.queryByText(/Laut Postleitzahl/)).not.toBeInTheDocument();
        await user.type(screen.getByRole('textbox', { name: 'PLZ' }), '9');

        expect(await screen.findByText('Laut Postleitzahl 97259 liegt euer Betrieb in Bayern.')).toBeVisible();
        // Entprellt: die PLZ-Abfrage kommt erst nach der letzten Ziffer, nicht je Tastendruck.
        expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('plz=')).length).toBe(1);
        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toContain('bundesland=NW');

        await user.click(screen.getByRole('button', { name: 'Übernehmen' }));
        expect(onLand).toHaveBeenCalledWith('BY');
        expect(screen.getByRole('combobox', { name: 'Bundesland' })).toHaveTextContent('Bayern');
        expect(screen.queryByText(/Laut Postleitzahl/)).not.toBeInTheDocument();
        expect(await screen.findByText('passt zur Postleitzahl')).toBeVisible();
    });

    it('zeigt bei passender PLZ nur das Häkchen und klappt die Feiertage des gewählten Landes auf', async () => {
        const user = userEvent.setup();
        render(<Formular startPlz="97259" startLand="BY" />);
        expect(await screen.findByText('passt zur Postleitzahl')).toBeVisible();
        expect(screen.queryByText(/Laut Postleitzahl/)).not.toBeInTheDocument();

        const aufklappen = await screen.findByRole('button', { name: 'Feiertage 2026 in Bayern (2)' });
        expect(aufklappen).toHaveAttribute('aria-expanded', 'false');
        expect(screen.queryByText('Mariä Himmelfahrt')).not.toBeInTheDocument();
        await user.click(aufklappen);
        expect(aufklappen).toHaveAttribute('aria-expanded', 'true');
        expect(screen.getByText('Mariä Himmelfahrt')).toBeVisible();
        expect(screen.getByText(/15\.08\.2026/)).toBeVisible();
        expect(screen.getByText('Mariä Himmelfahrt gilt nur in Gemeinden mit überwiegend katholischer Bevölkerung.')).toBeVisible();
        expect(screen.getByRole('link', { name: 'Feiertage anpassen' })).toHaveAttribute('href', '/feiertage');
        const url = String(fetchMock.mock.calls.at(-1)?.[0]);
        expect(url).toContain('bundesland=BY');
        expect(url).toContain('plz=97259');
    });

    it('fragt ohne gültige PLZ nur die Feiertage ab und zeigt keinen Vorschlag', async () => {
        render(<Formular startPlz="123" startLand="NW" />);
        expect(await screen.findByRole('button', { name: 'Feiertage 2026 in Nordrhein-Westfalen (1)' })).toBeVisible();
        expect(String(fetchMock.mock.calls[0][0])).not.toContain('plz=');
        expect(screen.queryByText('passt zur Postleitzahl')).not.toBeInTheDocument();
    });

    it('meldet ruhig, wenn die Feiertage nicht geladen werden konnten', async () => {
        fetchMock.mockImplementation(async () => ({ ok: false, status: 503, json: async () => ({}) }));
        render(<Formular startPlz="97259" />);
        await waitFor(() => expect(screen.getByText('Die Feiertage für dieses Bundesland konnten gerade nicht geladen werden.')).toBeVisible());
        expect(screen.queryByText(/Laut Postleitzahl/)).not.toBeInTheDocument();
    });

    it('erklärt im Info-Knopf auch den Vorschlag über die Postleitzahl', async () => {
        const user = userEvent.setup();
        render(<Formular />);
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Bundesland' }));
        expect(await screen.findByText(/schlägt das Bundesland anhand der Postleitzahl vor/)).toBeVisible();
    });
});
