import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StartDerZeiterfassungKarte } from './StartDerZeiterfassungKarte';

const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../../ui/toast', () => ({ useToast: () => toast }));

type Antwort = { status: number; body: unknown };
let gespeichert: string | null;
let putAntwort: Antwort | null;
let ladeFehler: boolean;
let puts: unknown[];

function antwort({ status, body }: Antwort) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

beforeEach(() => {
    vi.clearAllMocks();
    gespeichert = '2025-03-01'; putAntwort = null; ladeFehler = false; puts = [];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        expect(String(input)).toBe('/api/settings/zeiterfassung');
        if (init?.method === 'PUT') {
            const body = JSON.parse(String(init.body));
            puts.push(body);
            if (putAntwort) return antwort(putAntwort);
            gespeichert = body.eingefuehrtAm;
            return antwort({ status: 200, body: { eingefuehrtAm: gespeichert } });
        }
        if (ladeFehler) return antwort({ status: 500, body: { message: 'Der Server ist gerade nicht erreichbar.' } });
        return antwort({ status: 200, body: { eingefuehrtAm: gespeichert } });
    }));
});

afterEach(() => vi.unstubAllGlobals());

/** Wählt über die Direkteingabe des Kalenders ein Datum (TT.MM.JJJJ). */
function waehleDatum(text: string) {
    fireEvent.click(screen.getByRole('button', { name: 'Zeiterfassung eingeführt am' }));
    fireEvent.change(screen.getByLabelText('Datum eingeben'), { target: { value: text } });
    fireEvent.click(screen.getByRole('button', { name: 'Übernehmen' }));
}

describe('StartDerZeiterfassungKarte', () => {
    it('zeigt das gespeicherte Datum und was es bedeutet', async () => {
        render(<StartDerZeiterfassungKarte />);
        expect(await screen.findByText('01.03.2025')).toBeVisible();
        expect(screen.getByText('Gespeichert: Zeiterfassung seit 01.03.2025. Monate vor März 2025 müssen nicht abgeschlossen werden.')).toBeVisible();
        expect(screen.getByRole('button', { name: /Start speichern/ })).toBeDisabled();
    });

    it('speichert ein neues Datum, meldet Erfolg und frischt die Glocke auf', async () => {
        const glocke = vi.fn();
        window.addEventListener('notifications:refresh', glocke);
        const onSaved = vi.fn();
        render(<StartDerZeiterfassungKarte onSaved={onSaved} />);
        await screen.findByText('01.03.2025');

        waehleDatum('15.01.2024');
        fireEvent.click(screen.getByRole('button', { name: /Start speichern/ }));

        await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Start der Zeiterfassung gespeichert.'));
        expect(puts).toEqual([{ eingefuehrtAm: '2024-01-15' }]);
        expect(screen.getByText(/Zeiterfassung seit 15\.01\.2024\. Monate vor Januar 2024/)).toBeVisible();
        expect(glocke).toHaveBeenCalled();
        expect(onSaved).toHaveBeenCalled();
        window.removeEventListener('notifications:refresh', glocke);
    });

    it('entfernt das Datum erst mit Speichern und schickt dann null', async () => {
        render(<StartDerZeiterfassungKarte />);
        await screen.findByText('01.03.2025');

        fireEvent.click(screen.getByRole('button', { name: /Datum entfernen/ }));
        expect(puts).toHaveLength(0);
        expect(screen.getByText('Kein Datum – keine Grenze')).toBeVisible();
        expect(screen.getByRole('button', { name: /Datum entfernen/ })).toBeDisabled();

        fireEvent.click(screen.getByRole('button', { name: /Start speichern/ }));
        await waitFor(() => expect(puts).toEqual([{ eingefuehrtAm: null }]));
        expect(toast.success).toHaveBeenCalledWith('Datum entfernt – es gibt keine Grenze mehr.');
        expect(screen.getByText('Noch kein Datum gespeichert – alle Monate mit Zeiten müssen abgeschlossen werden.')).toBeVisible();
    });

    it('zeigt die Meldung des Servers bei einem abgelehnten Datum und behält den Entwurf', async () => {
        putAntwort = { status: 400, body: { message: 'Das Datum darf nicht in der Zukunft liegen.' } };
        render(<StartDerZeiterfassungKarte />);
        await screen.findByText('01.03.2025');

        waehleDatum('02.02.2025');
        fireEvent.click(screen.getByRole('button', { name: /Start speichern/ }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Das Datum darf nicht in der Zukunft liegen.');
        expect(toast.error).toHaveBeenCalledWith('Das Datum darf nicht in der Zukunft liegen.');
        expect(screen.getByText('02.02.2025')).toBeVisible();
        expect(screen.getByText(/Zeiterfassung seit 01\.03\.2025/)).toBeVisible();
    });

    it('sperrt Tage in der Zukunft schon im Kalender', async () => {
        gespeichert = null;
        render(<StartDerZeiterfassungKarte />);
        await screen.findByText('Kein Datum – keine Grenze');
        const morgen = new Date();
        morgen.setDate(morgen.getDate() + 1);
        waehleDatum(morgen.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }));
        expect(screen.getByText(/außerhalb des erlaubten Zeitraums/)).toBeVisible();
        expect(screen.getByRole('button', { name: /Start speichern/ })).toBeDisabled();
    });

    it('erklärt per Info-Knopf neben der Überschrift, was das Datum bewirkt', async () => {
        render(<StartDerZeiterfassungKarte />);
        await screen.findByText('01.03.2025');
        const knopf = screen.getByRole('button', { name: 'Mehr zu: Start der Zeiterfassung' });
        expect(screen.getByRole('heading', { name: 'Start der Zeiterfassung' })).toBeVisible();
        fireEvent.click(knopf);
        const info = screen.getByRole('dialog', { name: 'Start der Zeiterfassung' });
        expect(info).toHaveTextContent('frühestens im Monat dieses Tages');
        expect(info).toHaveTextContent('Der Monat des Einführungstags selbst schon.');
        expect(info).toHaveTextContent('Leer lassen heißt: keine Grenze.');
        fireEvent.keyDown(info, { key: 'Escape' });
        expect(screen.queryByRole('dialog', { name: 'Start der Zeiterfassung' })).not.toBeInTheDocument();
        expect(knopf).toHaveFocus();
    });

    it('meldet einen Ladefehler und lädt auf Wunsch erneut', async () => {
        ladeFehler = true;
        render(<StartDerZeiterfassungKarte />);
        expect(await screen.findByRole('alert')).toHaveTextContent('Der Server ist gerade nicht erreichbar.');
        expect(toast.error).toHaveBeenCalledWith('Der Server ist gerade nicht erreichbar.');

        ladeFehler = false;
        fireEvent.click(screen.getByRole('button', { name: 'Erneut laden' }));
        expect(await screen.findByText('01.03.2025')).toBeVisible();
    });
});
