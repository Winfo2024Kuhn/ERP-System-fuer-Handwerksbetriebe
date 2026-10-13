import { act, renderHook, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import { useHakenSpeichern } from './useHakenSpeichern';

// Keine personenbezogenen Daten: Es geht nur um Statuscodes und Meldungstexte.

const mitToasts = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;

const antwort = (status: number, body?: unknown) =>
    new Response(body === undefined ? null : JSON.stringify(body), { status });

const zeigeHook = (neuLaden = vi.fn(async () => {})) => ({
    neuLaden,
    ...renderHook(() => useHakenSpeichern(neuLaden), { wrapper: mitToasts }),
});

describe('useHakenSpeichern', () => {
    it('lädt nach Erfolg neu und zeigt keine Meldung', async () => {
        const { result, neuLaden } = zeigeHook();

        await act(() => result.current.speichern({
            id: 1, senden: async () => antwort(204), fehlertext: 'Ersatz.',
        }));

        expect(neuLaden).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('zeigt die Meldung vom Server und lädt trotzdem neu', async () => {
        const { result, neuLaden } = zeigeHook();

        await act(() => result.current.speichern({
            id: 1,
            senden: async () => antwort(409, { status: 409, message: 'Jemand anders hat gespeichert.' }),
            fehlertext: 'Ersatz.',
        }));

        expect(await screen.findByText('Jemand anders hat gespeichert.')).toBeInTheDocument();
        expect(neuLaden).toHaveBeenCalledTimes(1);
    });

    it('nimmt den Ersatztext, wenn der Server keine Meldung mitschickt', async () => {
        const { result } = zeigeHook();

        await act(() => result.current.speichern({
            id: 1, senden: async () => antwort(404), fehlertext: 'Haken nicht gespeichert.',
        }));

        expect(await screen.findByText('Haken nicht gespeichert.')).toBeInTheDocument();
    });

    it('nimmt die eigene Meldung für einen bestimmten Statuscode', async () => {
        const { result } = zeigeHook();

        await act(() => result.current.speichern({
            id: 1,
            senden: async () => antwort(403, { message: 'Verboten' }),
            fehlertext: 'Ersatz.',
            meldungJeStatus: { 403: 'Dafür fehlt Ihnen das Recht.' },
        }));

        expect(await screen.findByText('Dafür fehlt Ihnen das Recht.')).toBeInTheDocument();
        expect(screen.queryByText('Verboten')).not.toBeInTheDocument();
    });

    it('meldet einen Verbindungsfehler und lädt dann nicht neu', async () => {
        const { result, neuLaden } = zeigeHook();

        await act(() => result.current.speichern({
            id: 1,
            senden: async () => { throw new TypeError('Failed to fetch'); },
            fehlertext: 'Haken nicht gespeichert.',
        }));

        expect(await screen.findByText(
            'Haken nicht gespeichert. Bitte die Verbindung prüfen und es noch einmal versuchen.'
        )).toBeInTheDocument();
        expect(neuLaden).not.toHaveBeenCalled();
        expect(result.current.inArbeit.size).toBe(0);
    });

    it('sperrt nur die Zeile, die gerade gespeichert wird', async () => {
        const { result } = zeigeHook();
        let antworten: (res: Response) => void = () => {};
        const offen = new Promise<Response>(resolve => { antworten = resolve; });

        let laeuft: Promise<void> = Promise.resolve();
        act(() => {
            laeuft = result.current.speichern({ id: 7, senden: () => offen, fehlertext: 'Ersatz.' });
        });

        expect(result.current.inArbeit.has(7)).toBe(true);
        expect(result.current.inArbeit.has(8)).toBe(false);

        await act(async () => {
            antworten(antwort(204));
            await laeuft;
        });
        expect(result.current.inArbeit.has(7)).toBe(false);
    });

    it('schickt für dieselbe Zeile keine zweite Anfrage, solange die erste läuft', async () => {
        const { result, neuLaden } = zeigeHook();
        let antworten: (res: Response) => void = () => {};
        const senden = vi.fn(() => new Promise<Response>(resolve => { antworten = resolve; }));

        let erste: Promise<void> = Promise.resolve();
        let zweite: Promise<void> = Promise.resolve();
        act(() => {
            erste = result.current.speichern({ id: 7, senden, fehlertext: 'Ersatz.' });
            zweite = result.current.speichern({ id: 7, senden, fehlertext: 'Ersatz.' });
        });

        await act(async () => {
            antworten(antwort(204));
            await Promise.all([erste, zweite]);
        });
        expect(senden).toHaveBeenCalledTimes(1);
        expect(neuLaden).toHaveBeenCalledTimes(1);
        expect(result.current.inArbeit.has(7)).toBe(false);
    });
});
