import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UrlaubEintrittsjahrKarte } from './UrlaubEintrittsjahrKarte';

const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../../ui/toast', () => ({ useToast: () => toast }));

type Regel = { mehrurlaubAnteilig: boolean };
let gespeichert: Regel;
let puts: Regel[];
let putFehler: string | null;

function antwort(status: number, body: unknown) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

beforeEach(() => {
    vi.clearAllMocks();
    gespeichert = { mehrurlaubAnteilig: false };
    puts = [];
    putFehler = null;
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        expect(String(input)).toBe('/api/settings/urlaub-eintrittsjahr');
        if (init?.method === 'PUT') {
            const body = JSON.parse(String(init.body)) as Regel;
            puts.push(body);
            if (putFehler) return antwort(400, { message: putFehler });
            gespeichert = body;
        }
        return antwort(200, gespeichert);
    }));
});

afterEach(() => vi.unstubAllGlobals());

describe('UrlaubEintrittsjahrKarte', () => {
    it('zeigt die gesetzliche Regel als Standard', async () => {
        render(<UrlaubEintrittsjahrKarte />);
        expect(await screen.findByRole('radio', { name: /Voller Urlaub bei Eintritt bis 1\. Juli/ })).toBeChecked();
        expect(screen.getByText('Gesetzlicher Standard')).toBeVisible();
        expect(screen.getByRole('button', { name: /Eintrittsjahr speichern/ })).toBeDisabled();
        expect(screen.queryByText(/Nur wählen, wenn Ihr Arbeitsvertrag/)).toBeNull();
    });

    it('speichert „immer anteilig" mit Hinweis auf Vertrag oder Tarif', async () => {
        const onSaved = vi.fn();
        render(<UrlaubEintrittsjahrKarte onSaved={onSaved} />);
        fireEvent.click(await screen.findByRole('radio', { name: /Immer anteilig/ }));
        expect(screen.getByText(/Nur wählen, wenn Ihr Arbeitsvertrag oder Tarifvertrag/)).toBeVisible();

        fireEvent.click(screen.getByRole('button', { name: /Eintrittsjahr speichern/ }));

        await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Regel für den Urlaub im Eintrittsjahr gespeichert.'));
        expect(puts).toEqual([{ mehrurlaubAnteilig: true }]);
        expect(onSaved).toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /Eintrittsjahr speichern/ })).toBeDisabled();
    });

    it('zeigt Speicherfehler und behält die Auswahl', async () => {
        putFehler = 'Bitte angeben, ob der Urlaub im Eintrittsjahr anteilig gerechnet wird.';
        render(<UrlaubEintrittsjahrKarte />);
        fireEvent.click(await screen.findByRole('radio', { name: /Immer anteilig/ }));
        fireEvent.click(screen.getByRole('button', { name: /Eintrittsjahr speichern/ }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith(putFehler));
        expect(screen.getByRole('radio', { name: /Immer anteilig/ })).toBeChecked();
    });

    it('bietet bei Ladefehler erneutes Laden an', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => antwort(500, { message: 'Server nicht erreichbar.' })));
        render(<UrlaubEintrittsjahrKarte />);
        expect(await screen.findByRole('button', { name: 'Erneut laden' })).toBeVisible();
        expect(toast.error).toHaveBeenCalledWith('Server nicht erreichbar.');
    });
});
