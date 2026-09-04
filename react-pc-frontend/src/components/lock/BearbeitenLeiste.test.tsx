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
    const { container } = render(<BearbeitenLeiste {...props} />);
    return { onBearbeiten, onFertig, container };
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

    describe('Deaktivierter Bearbeiten-Knopf erklaert sich (Gulf of Execution)', () => {
        it('traegt title UND aria-describedby, wenn der Knopf deaktiviert ist und ein Grund gesetzt wurde', () => {
            renderLeiste({
                modus: 'lesen',
                kannBearbeiten: false,
                bearbeitenGesperrtGrund: 'Sperre wird gerade geprüft…',
            });

            const knopf = screen.getByRole('button', { name: 'Bearbeiten' });
            expect(knopf).toHaveAttribute('title', 'Sperre wird gerade geprüft…');

            const beschreibungsId = knopf.getAttribute('aria-describedby');
            expect(beschreibungsId).toBeTruthy();
            expect(document.getElementById(beschreibungsId!)).toHaveTextContent('Sperre wird gerade geprüft…');
        });

        it('traegt KEIN (auch kein leeres) title-Attribut, wenn der Knopf deaktiviert ist, aber kein Grund uebergeben wurde', () => {
            renderLeiste({ modus: 'lesen', kannBearbeiten: false });

            const knopf = screen.getByRole('button', { name: 'Bearbeiten' });
            expect(knopf).not.toHaveAttribute('title');
            expect(knopf).not.toHaveAttribute('aria-describedby');
        });

        it('zeigt keinen Tooltip, wenn der Knopf aktiviert ist -- selbst wenn ein Grund uebergeben wurde', () => {
            renderLeiste({
                modus: 'lesen',
                kannBearbeiten: true,
                bearbeitenGesperrtGrund: 'Sollte hier nie erscheinen',
            });

            const knopf = screen.getByRole('button', { name: 'Bearbeiten' });
            expect(knopf).not.toHaveAttribute('title');
            expect(knopf).not.toHaveAttribute('aria-describedby');
        });

        it('ignoriert einen nur aus Leerraum bestehenden Grund wie "kein Grund"', () => {
            renderLeiste({ modus: 'lesen', kannBearbeiten: false, bearbeitenGesperrtGrund: '   ' });

            expect(screen.getByRole('button', { name: 'Bearbeiten' })).not.toHaveAttribute('title');
        });
    });

    describe('Drei unterscheidbare Baender (Design-Review Abschnitt 6)', () => {
        it('Countdown ist eine Warnung: amber-Farben und ein Lucide-Icon, kein rose', () => {
            renderLeiste({ verbleibendeSekunden: 45 });

            const band = screen.getByRole('status');
            expect(band.className).toMatch(/amber-50/);
            expect(band.className).toMatch(/amber-300/);
            expect(band.className).toMatch(/amber-800/);
            expect(band.className).not.toMatch(/rose-/);
            expect(band.querySelector('svg')).not.toBeNull();
        });

        it('Verbindung weg ist eine Stoerung: kraeftiges Rot wie das Fehlerband im Lieferant-Modal, kein rose', () => {
            renderLeiste({ verbindungWeg: true });

            const band = screen.getByRole('alert');
            expect(band.className).toMatch(/red-50/);
            expect(band.className).toMatch(/red-300/);
            expect(band.className).toMatch(/red-700/);
            expect(band.className).not.toMatch(/rose-/);
            expect(band.querySelector('svg')).not.toBeNull();
        });
    });

    describe('Fertig/Bearbeiten springt nicht (Design-Review Abschnitt 6)', () => {
        it('rendert den Umschalt-Knopf als letztes Kind, egal wie viele Baender davor stehen', () => {
            const { container } = renderLeiste({
                modus: 'bearbeiten',
                verbleibendeSekunden: 30,
                verbindungWeg: true,
            });

            const leiste = container.firstElementChild as HTMLElement;
            const kinder = Array.from(leiste.children);
            const knopfIndex = kinder.findIndex(el => el.tagName === 'BUTTON');

            expect(knopfIndex).toBeGreaterThanOrEqual(0);
            expect(knopfIndex).toBe(kinder.length - 1);
        });

        it('bleibt beim Knopf als letztes Kind auch ganz ohne Baender (kein Sonderfall fuer den Leerzustand)', () => {
            const { container } = renderLeiste({ modus: 'bearbeiten' });

            const leiste = container.firstElementChild as HTMLElement;
            expect(leiste.children).toHaveLength(1);
            expect(leiste.children[0].tagName).toBe('BUTTON');
        });

        it('nach dem Knopf folgt hoechstens der unsichtbare sr-only-Beschreibungs-Span, nichts, das Platz einnimmt', () => {
            // Deckt genau die Luecke ab, die der Code-Review an der
            // Klassen-Doku bemaengelt hat: mit gesetztem
            // bearbeitenGesperrtGrund ist der Knopf NICHT mehr das letzte
            // Kind im DOM (der sr-only-Span fuer aria-describedby folgt
            // ihm) -- fuer das Layout zaehlt das aber nicht, weil der Span
            // unsichtbar ist und keinen Platz einnimmt.
            const { container } = renderLeiste({
                modus: 'lesen',
                kannBearbeiten: false,
                bearbeitenGesperrtGrund: 'Sperre wird gerade geprüft…',
            });

            const leiste = container.firstElementChild as HTMLElement;
            const kinder = Array.from(leiste.children);
            const knopfIndex = kinder.findIndex(el => el.tagName === 'BUTTON');
            const nachDemKnopf = kinder.slice(knopfIndex + 1);

            expect(nachDemKnopf).toHaveLength(1);
            expect(nachDemKnopf[0].tagName).toBe('SPAN');
            expect(nachDemKnopf[0].className).toContain('sr-only');
        });
    });

    describe('Lesen-Modus-Hinweis "Sie lesen nur mit."', () => {
        it('zeigt den Hinweis im Lesen-Modus, wenn zeigeNurLesenHinweis=true gesetzt ist', () => {
            renderLeiste({ modus: 'lesen', zeigeNurLesenHinweis: true });

            expect(screen.getByText('Sie lesen nur mit.')).toBeInTheDocument();
        });

        it('zeigt den Hinweis NICHT, wenn zeigeNurLesenHinweis nicht gesetzt ist (Standardwert false)', () => {
            renderLeiste({ modus: 'lesen' });

            expect(screen.queryByText('Sie lesen nur mit.')).not.toBeInTheDocument();
        });

        it('zeigt den Hinweis NICHT im Bearbeiten-Modus, selbst wenn zeigeNurLesenHinweis=true ist', () => {
            renderLeiste({ modus: 'bearbeiten', zeigeNurLesenHinweis: true });

            expect(screen.queryByText('Sie lesen nur mit.')).not.toBeInTheDocument();
        });
    });
});
