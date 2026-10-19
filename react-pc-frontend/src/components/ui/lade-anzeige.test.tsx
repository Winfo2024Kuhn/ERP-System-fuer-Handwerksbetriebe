import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LANGSAM_NACH_MS, LadeAnzeige, VerbindungsHinweis } from './lade-anzeige';

afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

describe('LadeAnzeige', () => {
    it('zeigt zuerst nur den Ladetext', () => {
        render(<LadeAnzeige text="Seite wird geladen …" />);

        expect(screen.getByRole('status')).toHaveTextContent('Seite wird geladen …');
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('meldet nach der Wartezeit die langsame Verbindung mit „Erneut versuchen“', async () => {
        vi.useFakeTimers();
        const erneut = vi.fn();
        render(<LadeAnzeige text="Seite wird geladen …" onErneut={erneut} />);

        await act(() => vi.advanceTimersByTimeAsync(LANGSAM_NACH_MS - 1));
        expect(screen.queryByText(/Verbindung ist gerade langsam/)).not.toBeInTheDocument();

        await act(() => vi.advanceTimersByTimeAsync(1));
        expect(screen.getByText(/Verbindung ist gerade langsam/)).toBeInTheDocument();

        vi.useRealTimers();
        await userEvent.click(screen.getByRole('button', { name: /Erneut versuchen/ }));
        expect(erneut).toHaveBeenCalledTimes(1);
    });

    it('füllt als Vollbild den ganzen Bildschirm', () => {
        render(<LadeAnzeige text="Anmeldung wird geprüft …" vollbild />);

        expect(screen.getByRole('status')).toHaveClass('min-h-screen');
    });
});

describe('VerbindungsHinweis', () => {
    it('lädt ohne eigene Aktion die Seite neu', async () => {
        const reload = vi.fn();
        vi.stubGlobal('location', { ...window.location, reload });
        render(<VerbindungsHinweis text="Die Seite konnte nicht geladen werden." />);

        await userEvent.click(screen.getByRole('button', { name: /Erneut versuchen/ }));

        expect(reload).toHaveBeenCalledTimes(1);
    });
});
