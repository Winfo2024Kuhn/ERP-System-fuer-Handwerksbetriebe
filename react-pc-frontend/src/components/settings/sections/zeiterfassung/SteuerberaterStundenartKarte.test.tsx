import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SteuerberaterStundenartKarte } from './SteuerberaterStundenartKarte';

const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../../ui/toast', () => ({ useToast: () => toast }));

type Wert = { stundenart: 'SOLL' | 'IST' | null };
let gespeichert: Wert;
let puts: Wert[];
let ladeStatus: number;

function antwort(status: number, body: unknown) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

beforeEach(() => {
    vi.clearAllMocks();
    gespeichert = { stundenart: null };
    puts = [];
    ladeStatus = 200;
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        expect(String(input)).toBe('/api/settings/steuerberater-stundenart');
        if (init?.method === 'PUT') {
            const body = JSON.parse(String(init.body)) as Wert;
            puts.push(body);
            gespeichert = body;
            return antwort(200, gespeichert);
        }
        return ladeStatus === 200 ? antwort(200, gespeichert) : antwort(ladeStatus, { message: 'Diese Einstellungen dürfen nur Admins ändern.' });
    }));
});

afterEach(() => vi.unstubAllGlobals());

describe('SteuerberaterStundenartKarte', () => {
    it('sagt, dass ohne Wahl Sollstunden übermittelt werden, und legt die Wahl beim Speichern fest', async () => {
        const onSaved = vi.fn();
        render(<SteuerberaterStundenartKarte onSaved={onSaved} />);
        expect(await screen.findByRole('radio', { name: /Sollstunden – bezahlt nach Vertrag/ })).toBeChecked();
        expect(screen.getByText('Noch nicht festgelegt – bis dahin werden Sollstunden übermittelt.')).toBeVisible();
        fireEvent.click(screen.getByRole('button', { name: /Stundenart speichern/ }));
        await waitFor(() => expect(puts).toEqual([{ stundenart: 'SOLL' }]));
        expect(toast.success).toHaveBeenCalledWith('Gespeichert: Der Steuerberater bekommt ab jetzt Sollstunden.');
        expect(onSaved).toHaveBeenCalled();
        expect(screen.queryByText(/Noch nicht festgelegt/)).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Stundenart speichern/ })).toBeDisabled();
    });

    it('wechselt auf Iststunden', async () => {
        gespeichert = { stundenart: 'SOLL' };
        render(<SteuerberaterStundenartKarte />);
        const ist = await screen.findByRole('radio', { name: /Iststunden – tatsächlich gearbeitet/ });
        expect(screen.queryByText(/Noch nicht festgelegt/)).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Stundenart speichern/ })).toBeDisabled();
        fireEvent.click(ist);
        fireEvent.click(screen.getByRole('button', { name: /Stundenart speichern/ }));
        await waitFor(() => expect(puts).toEqual([{ stundenart: 'IST' }]));
        expect(toast.success).toHaveBeenCalledWith('Gespeichert: Der Steuerberater bekommt ab jetzt Iststunden.');
        expect(ist).toBeChecked();
    });

    it('erklärt per Info-Knopf, was die Wahl bewirkt', async () => {
        render(<SteuerberaterStundenartKarte />);
        fireEvent.click(await screen.findByRole('button', { name: 'Mehr zu: Stunden für den Steuerberater' }));
        const info = screen.getByRole('dialog', { name: 'Stunden für den Steuerberater' });
        expect(within(info).getByText(/Hier legst du fest, welche/)).toBeInTheDocument();
        expect(within(info).getByText(/Der DATEV-Export hat eine eigene Einstellung/)).toBeInTheDocument();
    });

    it('zeigt einen Ladefehler mit „Erneut laden“', async () => {
        ladeStatus = 403;
        render(<SteuerberaterStundenartKarte />);
        expect(await screen.findByRole('alert')).toHaveTextContent('Diese Einstellungen dürfen nur Admins ändern.');
        expect(toast.error).toHaveBeenCalledWith('Diese Einstellungen dürfen nur Admins ändern.');
        ladeStatus = 200;
        fireEvent.click(screen.getByRole('button', { name: 'Erneut laden' }));
        expect(await screen.findByRole('radio', { name: /Sollstunden/ })).toBeChecked();
    });
});
