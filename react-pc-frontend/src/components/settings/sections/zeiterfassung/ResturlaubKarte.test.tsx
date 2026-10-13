import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ResturlaubKarte } from './ResturlaubKarte';
import { ConfirmProvider } from '../../../ui/confirm-dialog';

const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../../ui/toast', () => ({ useToast: () => toast }));

type Regel = { verfaellt: boolean; tag: number; monat: number };
let gespeichert: Regel;
let puts: Regel[];
let putFehler: string | null;

function antwort(status: number, body: unknown) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

beforeEach(() => {
    vi.clearAllMocks();
    gespeichert = { verfaellt: true, tag: 31, monat: 3 };
    puts = [];
    putFehler = null;
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        expect(String(input)).toBe('/api/settings/urlaubsverfall');
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

describe('ResturlaubKarte', () => {
    it('zeigt den gesetzlichen Standard und den Hinweispflicht-Text', async () => {
        render(<ConfirmProvider><ResturlaubKarte /></ConfirmProvider>);
        expect(await screen.findByText('Gespeichert: Resturlaub verfällt nach dem 31. März des Folgejahres.')).toBeVisible();
        expect(screen.getByRole('radio', { name: /Resturlaub wird übertragen und verfällt/ })).toBeChecked();
        expect(screen.getByText(/Gesetzlich verfällt Resturlaub am 31\. März – aber nur, wenn Sie Ihre Leute vorher/)).toBeVisible();
        expect(screen.getByRole('button', { name: /Regel speichern/ })).toBeDisabled();
    });

    it('speichert „verfällt nicht" und blendet die Tagauswahl aus', async () => {
        const onSaved = vi.fn();
        render(<ConfirmProvider><ResturlaubKarte onSaved={onSaved} /></ConfirmProvider>);
        await screen.findByText(/Gespeichert:/);
        expect(screen.getByRole('combobox', { name: 'Verfallsmonat' })).toBeVisible();

        fireEvent.click(screen.getByRole('radio', { name: /Resturlaub verfällt nicht/ }));
        expect(screen.queryByRole('combobox', { name: 'Verfallsmonat' })).toBeNull();
        fireEvent.click(screen.getByRole('button', { name: /Regel speichern/ }));

        await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Regel für Resturlaub gespeichert.'));
        expect(puts).toEqual([{ verfaellt: false, tag: 31, monat: 3 }]);
        expect(screen.getByText('Gespeichert: Resturlaub verfällt nicht.')).toBeVisible();
        expect(onSaved).toHaveBeenCalled();
    });

    it('kürzt den Tag beim Wechsel auf einen kürzeren Monat und speichert ihn', async () => {
        const user = userEvent.setup();
        render(<ConfirmProvider><ResturlaubKarte /></ConfirmProvider>);
        await screen.findByText(/Gespeichert:/);

        await user.click(screen.getByRole('combobox', { name: 'Verfallsmonat' }));
        await user.click(screen.getByRole('option', { name: 'Juni' }));
        await user.click(screen.getByRole('button', { name: /Regel speichern/ }));

        await waitFor(() => expect(puts).toEqual([{ verfaellt: true, tag: 30, monat: 6 }]));
        expect(screen.getByText('Gespeichert: Resturlaub verfällt nach dem 30. Juni des Folgejahres.')).toBeVisible();
        expect(screen.queryByRole('button', { name: 'Auf 31. März zurücksetzen' })).toBeVisible();
    });

    it('zeigt Serverfehler als Toast und im Feld', async () => {
        putFehler = 'Diesen Tag gibt es nicht. Bitte ein gültiges Datum wählen.';
        render(<ConfirmProvider><ResturlaubKarte /></ConfirmProvider>);
        await screen.findByText(/Gespeichert:/);

        fireEvent.click(screen.getByRole('radio', { name: /Resturlaub verfällt nicht/ }));
        fireEvent.click(screen.getByRole('button', { name: /Regel speichern/ }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith(putFehler));
        expect(screen.getByRole('alert')).toHaveTextContent(putFehler);
    });

    it('meldet Ladefehler und kann neu laden', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => antwort(500, { message: 'Der Server ist gerade nicht erreichbar.' })));
        render(<ConfirmProvider><ResturlaubKarte /></ConfirmProvider>);
        expect(await screen.findByText('Der Server ist gerade nicht erreichbar.')).toBeVisible();
        expect(toast.error).toHaveBeenCalledWith('Der Server ist gerade nicht erreichbar.');
        expect(screen.getByRole('button', { name: 'Erneut laden' })).toBeVisible();
    });
    it('fragt bei einem Tag vor dem 31. März mit Rechtshinweis nach und speichert erst nach Bestätigung', async () => {
        const user = userEvent.setup();
        render(<ConfirmProvider><ResturlaubKarte /></ConfirmProvider>);
        await screen.findByText(/Gespeichert:/);

        await user.click(screen.getByRole('combobox', { name: 'Verfallsmonat' }));
        await user.click(screen.getByRole('option', { name: 'Januar' }));
        expect(screen.getByText('Früher als 31. März ist nur erlaubt, wenn Ihr Tarifvertrag das vorsieht.')).toBeVisible();

        await user.click(screen.getByRole('button', { name: /Regel speichern/ }));
        expect(await screen.findByText(/Gesetzlich muss Resturlaub bis mindestens 31\. März genommen werden können/)).toBeVisible();
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(puts).toHaveLength(0);

        await user.click(screen.getByRole('button', { name: /Regel speichern/ }));
        await user.click(await screen.findByRole('button', { name: 'Ja, laut Tarifvertrag' }));
        await waitFor(() => expect(puts).toEqual([{ verfaellt: true, tag: 31, monat: 1 }]));
    });
});
