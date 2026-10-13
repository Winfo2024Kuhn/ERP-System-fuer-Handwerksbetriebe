import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtikelStammdatenForm } from './ArtikelStammdatenForm';
import { ToastProvider } from '../ui/toast';
import type { Artikel } from '../../types';

vi.mock('../CategoryTreeModal', () => ({
    CategoryTreeModal: ({ onSelect }: { onSelect: (id: number, name: string) => void }) => (
        <button type="button" onClick={() => onSelect(5, 'Hohlprofile > Rundrohr')}>Kategorie 5 wählen</button>
    ),
}));

const artikel: Artikel = {
    id: 7, version: 3, produktname: 'Rundrohr 42,4 x 2', artikelnummer: 'ST-RR-042', ean: undefined,
    verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' }, verpackungseinheit: 1,
    kategorieId: 2, kategoriePfad: 'Werkstoffe > Rohre', werkstoffId: 4, werkstoffName: 'Baustahl',
    profilform: { name: 'RUNDROHR', anzeigename: 'Rundrohr' }, durchmesser: 42.4, wandstaerke: 2, stegdicke: 1.5, standardlaenge: 6000,
};

const antwort = (daten: unknown, ok = true, status = 200) => ({ ok, status, text: async () => JSON.stringify(daten), json: async () => daten });

let fetcher: ReturnType<typeof vi.fn>;
const puts = () => fetcher.mock.calls.filter(([u, i]) => String(u).endsWith('/stammdaten') && i?.method === 'PUT');
const sendeBody = () => JSON.parse(puts()[0][1].body as string);

beforeEach(() => {
    fetcher = vi.fn(async (url: string, init?: RequestInit) => {
        if (url.endsWith('/filteroptionen')) return antwort({ profilform: [{ wert: 'RUNDROHR', name: 'Rundrohr' }], herstellverfahren: [], fertigungszustand: [] });
        if (url.endsWith('/werkstoffe/details')) return antwort([{ id: 4, name: 'Baustahl' }]);
        if (init?.method === 'PUT') return antwort({ id: 7 });
        return antwort({});
    });
    vi.stubGlobal('fetch', fetcher);
});
afterEach(() => { vi.unstubAllGlobals(); });

const zeige = (onGespeichert = vi.fn(), onAbbrechen = vi.fn()) => {
    render(<ToastProvider><ArtikelStammdatenForm artikel={artikel} onGespeichert={onGespeichert} onAbbrechen={onAbbrechen} /></ToastProvider>);
    return { onGespeichert, onAbbrechen };
};

describe('ArtikelStammdatenForm', () => {
    it('ist mit allen bisherigen Stammdaten vorbelegt, auch mit den Maßen', async () => {
        zeige();
        expect(await screen.findByLabelText('Produktname *')).toHaveValue('Rundrohr 42,4 x 2');
        expect(screen.getByLabelText('Artikelnummer')).toHaveValue('ST-RR-042');
        expect(screen.getByLabelText('Durchmesser (mm)')).toHaveValue('42,4');
        expect(screen.getByLabelText('Stegdicke (mm)')).toHaveValue('1,5');
        expect(screen.getByLabelText('Übliche Länge (mm)')).toHaveValue('6000');
        expect(screen.getByText('Werkstoffe > Rohre')).toBeVisible();
    });

    it('sendet beim Speichern alle Felder samt Stand und meldet das Ergebnis', async () => {
        const user = userEvent.setup();
        const { onGespeichert } = zeige();
        const name = await screen.findByLabelText('Produktname *');
        await user.clear(name);
        await user.type(name, 'Rundrohr 48,3 x 2,6');
        await user.type(screen.getByLabelText('EAN / Strichcode'), '4006 3813 33931');
        await user.clear(screen.getByLabelText('Durchmesser (mm)'));
        await user.type(screen.getByLabelText('Durchmesser (mm)'), '48,3');

        await user.click(screen.getByRole('button', { name: /^Speichern/ }));

        await waitFor(() => expect(onGespeichert).toHaveBeenCalled());
        expect(puts()[0][0]).toBe('/api/artikel/7/stammdaten');
        const b = sendeBody();
        expect(b).toMatchObject({
            version: 3, produktname: 'Rundrohr 48,3 x 2,6', artikelnummer: 'ST-RR-042', ean: '4006381333931',
            verrechnungseinheit: 'LAUFENDE_METER', kategorieId: 2, werkstoffId: 4, profilform: 'RUNDROHR',
            durchmesser: 48.3, wandstaerke: 2, stegdicke: 1.5, standardlaenge: 6000,
        });
    });

    it('verlangt einen Produktnamen und prüft Zahlen vor dem Senden', async () => {
        const user = userEvent.setup();
        zeige();
        const name = await screen.findByLabelText('Produktname *');
        await user.clear(name);
        await user.click(screen.getByRole('button', { name: /^Speichern/ }));
        expect((await screen.findAllByRole('alert')).some(a => a.textContent?.includes('Produktnamen'))).toBe(true);

        await user.type(name, 'x');
        await user.clear(screen.getByLabelText('Höhe (mm)'));
        await user.type(screen.getByLabelText('Höhe (mm)'), '12,x');
        await user.click(screen.getByRole('button', { name: /^Speichern/ }));
        expect((await screen.findAllByRole('alert')).some(a => a.textContent?.includes('Höhe'))).toBe(true);
        expect(puts()).toHaveLength(0);
    });

    it('zeigt die Meldung des Servers, zum Beispiel bei doppelter Artikelnummer', async () => {
        const user = userEvent.setup();
        fetcher.mockImplementation(async (url: string, init?: RequestInit) => {
            if (init?.method === 'PUT') return antwort({ message: 'Die Artikelnummer ST-RR-042 gibt es schon bei einem anderen Artikel.' }, false, 409);
            return url.endsWith('/filteroptionen') ? antwort({ profilform: [], herstellverfahren: [], fertigungszustand: [] }) : antwort([]);
        });
        const { onGespeichert } = zeige();
        await user.click(await screen.findByRole('button', { name: /^Speichern/ }));

        expect((await screen.findAllByRole('alert')).some(a => a.textContent?.includes('gibt es schon bei einem anderen Artikel'))).toBe(true);
        expect(onGespeichert).not.toHaveBeenCalled();
    });

    it('warnt bei geänderter Einheit, dass Mengen nicht umgerechnet werden', async () => {
        const user = userEvent.setup();
        zeige();
        await screen.findByLabelText('Produktname *');
        expect(screen.queryByRole('note')).toBeNull();

        await user.click(screen.getAllByRole('combobox')[0]);
        await user.click(await screen.findByRole('option', { name: 'Kilogramm' }));

        expect(await screen.findByRole('note')).toHaveTextContent('nicht umgerechnet');
    });

    it('übernimmt die gewählte Kategorie und lässt sie entfernen', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByText('Werkstoffe > Rohre'));
        await user.click(screen.getByRole('button', { name: 'Kategorie 5 wählen' }));
        expect(screen.getByText('Hohlprofile > Rundrohr')).toBeVisible();

        await user.click(screen.getByRole('button', { name: 'Kategorie entfernen' }));
        await user.click(screen.getByRole('button', { name: /^Speichern/ }));

        await waitFor(() => expect(puts()).toHaveLength(1));
        expect(sendeBody().kategorieId).toBeNull();
    });

    it('bricht ab ohne zu speichern', async () => {
        const user = userEvent.setup();
        const { onAbbrechen } = zeige();
        await user.click(await screen.findByRole('button', { name: 'Abbrechen' }));
        expect(onAbbrechen).toHaveBeenCalled();
        expect(puts()).toHaveLength(0);
    });

    it('erklärt die Einheit in einem Info-Button, ohne dass etwas gespeichert wird', async () => {
        const user = userEvent.setup();
        zeige();
        await user.click(await screen.findByRole('button', { name: /Mehr zu: Verrechnungseinheit/ }));
        expect(await screen.findByText('Was passiert im Hintergrund?')).toBeVisible();
        expect(screen.getByText('Was bewirkt das?')).toBeVisible();
        expect(puts()).toHaveLength(0);
    });

    it('speichert Sonderzeichen als Text, ohne sie zu verändern', async () => {
        const user = userEvent.setup();
        const { onGespeichert } = zeige();
        const name = await screen.findByLabelText('Produktname *');
        await user.clear(name);
        await user.click(name);
        await user.paste('<img src=x onerror=alert(1)> \'; DROP TABLE artikel; --');
        await user.click(screen.getByRole('button', { name: /^Speichern/ }));

        await waitFor(() => expect(onGespeichert).toHaveBeenCalled());
        expect(sendeBody().produktname).toBe('<img src=x onerror=alert(1)> \'; DROP TABLE artikel; --');
    });
});
