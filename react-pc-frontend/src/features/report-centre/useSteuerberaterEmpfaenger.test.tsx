import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import type { SteuerberaterKontakt } from './steuerberaterKontakt';
import { useSteuerberaterEmpfaenger } from './useSteuerberaterEmpfaenger';

vi.mock('../email/mailEditorHtml', () => ({ ladeStandardSignatur: vi.fn(() => Promise.resolve('<p>Max Mustermann</p>')) }));

const ap = (id: number, nachname: string, email: string, lohn = false) => ({
    id, anrede: 'HERR', vorname: 'Max', nachname, email, telefon: '', istLohnAnsprechpartner: lohn,
});

const KANZLEI_A: SteuerberaterKontakt = {
    id: 1, name: 'Kanzlei Mustermann', email: 'kanzlei@example.com', telefon: '', weitereEmails: ['info@example.com', 'kanzlei@example.com'],
    ansprechpartnerListe: [ap(10, 'Mustermann', 'max@example.com'), ap(11, 'Lohnmann', 'lohn@example.com', true)],
};
const KANZLEI_B: SteuerberaterKontakt = {
    id: 2, name: 'Kanzlei Musterfrau', email: 'b@example.com', telefon: '', weitereEmails: [], ansprechpartnerListe: [],
};

function antwort(ok: boolean, inhalt: unknown) {
    return Promise.resolve({ ok, json: () => Promise.resolve(inhalt) });
}

let liste: unknown = [KANZLEI_A, KANZLEI_B];
let firma: unknown = { firmenname: ' Musterbetrieb ' };

beforeEach(() => {
    liste = [KANZLEI_A, KANZLEI_B];
    firma = { firmenname: ' Musterbetrieb ' };
    localStorage.clear();
    global.fetch = vi.fn((url: string) => (url === '/api/firma/steuerberater' ? antwort(true, liste) : antwort(true, firma))) as unknown as typeof fetch;
});
afterEach(() => vi.restoreAllMocks());

const wrapper = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;

describe('useSteuerberaterEmpfaenger', () => {
    it('lädt, wählt den Lohn-Ansprechpartner vor und baut den Betreff', async () => {
        const { result } = renderHook(() => useSteuerberaterEmpfaenger(true, 10, 2026), { wrapper });
        expect(result.current.laedt).toBe(true);
        await waitFor(() => expect(result.current.laedt).toBe(false));
        expect(result.current.steuerberaterId).toBe(1);
        expect(result.current.ansprechpartner?.nachname).toBe('Lohnmann');
        expect(result.current.empfaenger).toBe('lohn@example.com');
        expect(result.current.betreff).toBe('Stundenaufstellung Oktober 2026 - Musterbetrieb');
        expect(result.current.signatur).toBe('<p>Max Mustermann</p>');
        expect(result.current.optionen.ansprechpartner).toEqual([
            { value: '10', label: 'Max Mustermann' }, { value: '11', label: 'Max Lohnmann · Löhne' },
        ]);
        expect(result.current.optionen.steuerberater).toHaveLength(2);
    });

    it('setzt beim Wechsel Ansprechpartner und Empfänger', async () => {
        const { result } = renderHook(() => useSteuerberaterEmpfaenger(true, 10, 2026), { wrapper });
        await waitFor(() => expect(result.current.laedt).toBe(false));
        act(() => result.current.waehleAnsprechpartner(10));
        expect(result.current.empfaenger).toBe('max@example.com');
        act(() => result.current.waehleSteuerberater(2));
        expect(result.current.ansprechpartnerId).toBeNull();
        expect(result.current.empfaenger).toBe('b@example.com');
        act(() => result.current.waehleAnsprechpartner(null));
        expect(result.current.empfaenger).toBe('b@example.com');
    });

    it('wählt beim Empfänger-Wechsel den passenden Ansprechpartner oder keinen', async () => {
        const { result } = renderHook(() => useSteuerberaterEmpfaenger(true, 10, 2026), { wrapper });
        await waitFor(() => expect(result.current.laedt).toBe(false));
        act(() => result.current.waehleEmpfaenger('max@example.com'));
        expect(result.current.empfaenger).toBe('max@example.com');
        expect(result.current.ansprechpartner?.nachname).toBe('Mustermann');
        act(() => result.current.waehleEmpfaenger('kanzlei@example.com'));
        expect(result.current.ansprechpartnerId).toBeNull();
        expect(result.current.ansprechpartner).toBeNull();
        // Freies Feld: beim Tippen läuft die Zuordnung mit, Leerzeichen und Großschreibung egal.
        act(() => result.current.waehleEmpfaenger(' LOHN@example.com'));
        expect(result.current.empfaenger).toBe(' LOHN@example.com');
        expect(result.current.ansprechpartnerId).toBe(11);
        act(() => result.current.waehleEmpfaenger('fremd@example.com'));
        expect(result.current.ansprechpartnerId).toBeNull();
    });

    it('kommt ohne Steuerberater und ohne Firmennamen aus', async () => {
        liste = { kaputt: true };
        firma = null;
        const { result } = renderHook(() => useSteuerberaterEmpfaenger(true, 1, 2027), { wrapper });
        await waitFor(() => expect(result.current.laedt).toBe(false));
        expect(result.current.liste).toEqual([]);
        expect(result.current.empfaenger).toBe('');
        expect(result.current.betreff).toBe('Stundenaufstellung Januar 2027');
    });

    it('meldet einen Ladefehler und lädt geschlossen nichts', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        global.fetch = vi.fn(() => Promise.reject(new Error('weg'))) as unknown as typeof fetch;
        const { result, rerender } = renderHook(({ offen }) => useSteuerberaterEmpfaenger(offen, 10, 2026), { wrapper, initialProps: { offen: true } });
        await waitFor(() => expect(result.current.laedt).toBe(false));
        expect(result.current.liste).toEqual([]);
        rerender({ offen: false });
        expect(result.current.laedt).toBe(false);
        expect(result.current.betreff).toBe('');
    });
});
