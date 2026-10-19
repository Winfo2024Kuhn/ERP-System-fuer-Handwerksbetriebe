import { lazy } from 'react';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Seite } from './Seite';
import { istLadefehlerEinerSeite } from '../ErrorBoundary';

afterEach(() => vi.restoreAllMocks());

describe('Seite', () => {
    it('zeigt die nachgeladene Seite', async () => {
        const Projekte = lazy(async () => ({ default: () => <p>Projektliste</p> }));
        render(<Seite><Projekte /></Seite>);

        expect(await screen.findByText('Projektliste')).toBeInTheDocument();
    });

    it('zeigt eine Ladeanzeige, solange die Seite noch kommt', () => {
        const NieFertig = lazy(() => new Promise<{ default: () => null }>(() => { }));
        render(<Seite><NieFertig /></Seite>);

        expect(screen.getByRole('status')).toHaveTextContent('Seite wird geladen');
    });

    it('meldet eine nicht ladbare Seitendatei verständlich mit „Erneut versuchen“', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => { });
        const Kaputt = lazy(() => Promise.reject(new TypeError('Failed to fetch dynamically imported module: /assets/Kunden-abc.js')));
        render(<Seite><Kaputt /></Seite>);

        expect(await screen.findByText(/Seite konnte nicht geladen werden/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Erneut versuchen/ })).toBeInTheDocument();
        expect(screen.queryByText('Ein Fehler ist aufgetreten')).not.toBeInTheDocument();
    });

    it('zeigt andere Fehler weiterhin mit Details', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => { });
        function Kaputt(): never { throw new Error('Rechenfehler'); }
        render(<Seite><Kaputt /></Seite>);

        expect(await screen.findByText('Ein Fehler ist aufgetreten')).toBeInTheDocument();
    });
});

describe('istLadefehlerEinerSeite', () => {
    it.each([
        'Failed to fetch dynamically imported module: /assets/a.js', // Chrome, Edge, Brave
        'Importing a module script failed.', // Safari
        'error loading dynamically imported module', // Firefox
        'Unable to preload CSS for /assets/a.css', // Vite
    ])('erkennt „%s“', (meldung) => {
        expect(istLadefehlerEinerSeite(new TypeError(meldung))).toBe(true);
    });

    it('erkennt den ChunkLoadError am Namen', () => {
        const fehler = new Error('Laden fehlgeschlagen');
        fehler.name = 'ChunkLoadError';
        expect(istLadefehlerEinerSeite(fehler)).toBe(true);
    });

    it('lässt gewöhnliche Fehler und null durch', () => {
        expect(istLadefehlerEinerSeite(new Error('Rechenfehler'))).toBe(false);
        expect(istLadefehlerEinerSeite(null)).toBe(false);
    });
});
