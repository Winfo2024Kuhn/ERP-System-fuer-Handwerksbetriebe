import type { ComponentProps } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { BearbeitenLeiste } from './BearbeitenLeiste';

// Wortlaut exakt aus der Wording-Tabelle des Plans (Datensatz-Sperren-Fundament).
const COUNTDOWN_TEXT_45 =
    'Wird in 45 Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten.';
const VERBINDUNG_WEG_TEXT = 'Verbindung weg — Ihre Änderungen sind noch nicht gespeichert.';

function renderLeiste(overrides: Partial<ComponentProps<typeof BearbeitenLeiste>> = {}) {
    const onBearbeiten = vi.fn();
    const onFertig = vi.fn();
    const props: ComponentProps<typeof BearbeitenLeiste> = {
        modus: 'lesen',
        kannBearbeiten: true,
        verbleibendeSekunden: null,
        verbindungWeg: false,
        onBearbeiten,
        onFertig,
        ...overrides,
    };
    render(<BearbeitenLeiste {...props} />);
    return { onBearbeiten, onFertig };
}

describe('BearbeitenLeiste', () => {
    describe('Umschalt-Knopf', () => {
        it('zeigt im Lesen-Modus mit Bearbeitungsrecht "Bearbeiten" (nicht deaktiviert, kein "Fertig")', () => {
            renderLeiste({ modus: 'lesen', kannBearbeiten: true });

            expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeEnabled();
            expect(screen.queryByRole('button', { name: 'Fertig' })).not.toBeInTheDocument();
        });

        it('deaktiviert den Knopf im Lesen-Modus, wenn ein anderer haelt (kannBearbeiten=false)', () => {
            renderLeiste({ modus: 'lesen', kannBearbeiten: false });

            expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeDisabled();
        });

        it('zeigt im Bearbeiten-Modus "Fertig" (nicht deaktiviert, kein "Bearbeiten")', () => {
            renderLeiste({ modus: 'bearbeiten' });

            expect(screen.getByRole('button', { name: 'Fertig' })).toBeEnabled();
            expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument();
        });

        it('loest onBearbeiten beim Klick genau einmal aus, onFertig gar nicht', async () => {
            const user = userEvent.setup();
            const { onBearbeiten, onFertig } = renderLeiste({ modus: 'lesen', kannBearbeiten: true });

            await user.click(screen.getByRole('button', { name: 'Bearbeiten' }));

            expect(onBearbeiten).toHaveBeenCalledTimes(1);
            expect(onFertig).not.toHaveBeenCalled();
        });

        it('loest onFertig beim Klick genau einmal aus, onBearbeiten gar nicht', async () => {
            const user = userEvent.setup();
            const { onBearbeiten, onFertig } = renderLeiste({ modus: 'bearbeiten' });

            await user.click(screen.getByRole('button', { name: 'Fertig' }));

            expect(onFertig).toHaveBeenCalledTimes(1);
            expect(onBearbeiten).not.toHaveBeenCalled();
        });

        it('loest onBearbeiten NICHT aus, wenn der Knopf deaktiviert ist (kannBearbeiten=false)', async () => {
            const user = userEvent.setup();
            const { onBearbeiten } = renderLeiste({ modus: 'lesen', kannBearbeiten: false });

            await user.click(screen.getByRole('button', { name: 'Bearbeiten' }));

            expect(onBearbeiten).not.toHaveBeenCalled();
        });
    });

    describe('Countdown-Banner (Inaktivitaets-Vorwarnung)', () => {
        it('zeigt bei gesetzter Sekundenzahl den exakten Wortlaut inkl. der Zahl', () => {
            renderLeiste({ verbleibendeSekunden: 45 });

            expect(screen.getByText(COUNTDOWN_TEXT_45)).toBeInTheDocument();
        });

        it('zeigt keinen Banner, wenn verbleibendeSekunden null ist', () => {
            renderLeiste({ verbleibendeSekunden: null });

            expect(screen.queryByRole('status')).not.toBeInTheDocument();
            expect(screen.queryByText(/freigegeben/)).not.toBeInTheDocument();
        });

        it('zeigt den Banner auch bei 0 Sekunden (0 ist ein gueltiger Countdown-Wert, nicht "kein Countdown")', () => {
            renderLeiste({ verbleibendeSekunden: 0 });

            expect(
                screen.getByText(
                    'Wird in 0 Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten.'
                )
            ).toBeInTheDocument();
        });

        it('hat role="status" und aria-live="polite", damit Screenreader es ohne Fokusklau vorlesen', () => {
            renderLeiste({ verbleibendeSekunden: 12 });

            const banner = screen.getByRole('status');
            expect(banner).toHaveAttribute('aria-live', 'polite');
            expect(banner).toHaveTextContent(
                'Wird in 12 Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten.'
            );
        });
    });

    describe('Verbindungswarnung', () => {
        it('zeigt bei verbindungWeg=true die Warnzeile mit exaktem Wortlaut und role="alert"', () => {
            renderLeiste({ verbindungWeg: true });

            expect(screen.getByRole('alert')).toHaveTextContent(VERBINDUNG_WEG_TEXT);
        });

        it('zeigt keine Warnzeile, wenn verbindungWeg false ist', () => {
            renderLeiste({ verbindungWeg: false });

            expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        });
    });

    it('zeigt Countdown-Banner und Verbindungswarnung gleichzeitig, wenn beide Zustaende vorliegen', () => {
        renderLeiste({ verbleibendeSekunden: 30, verbindungWeg: true });

        expect(screen.getByRole('status')).toHaveTextContent(
            'Wird in 30 Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten.'
        );
        expect(screen.getByRole('alert')).toHaveTextContent(VERBINDUNG_WEG_TEXT);
    });

    it('enthaelt keine englischen Fachbegriffe und keine Du-Form im sichtbaren Text', () => {
        renderLeiste({ verbleibendeSekunden: 20, verbindungWeg: true });

        const text = document.body.textContent ?? '';
        expect(text).not.toMatch(/\block\b|\bcheckout\b|\bstale\b/i);
        expect(text).not.toMatch(/\bbewege\b/); // Du-Form ("bewege die Maus") waere falsch, Sie-Form ist "bewegen Sie"
    });
});
