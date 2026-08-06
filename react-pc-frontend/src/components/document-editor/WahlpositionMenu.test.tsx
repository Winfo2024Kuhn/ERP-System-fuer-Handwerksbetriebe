/**
 * Vitest-Suite fuer WahlpositionMenu.
 *
 * Die Liste haengt per Portal an `document.body`, weil die Bauabschnitt-Karte
 * `overflow-hidden` traegt — inline gerendert waere der unterste Eintrag
 * "Alternativ" unter der letzten Leistung eines Abschnitts abgeschnitten und
 * damit unklickbar. Diese Suite haelt genau das fest, dazu die Tastaturwege.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { WahlpositionMenu } from './WahlpositionMenu';
import type { DocBlock } from './types';

const block: DocBlock = {
    id: 'b1', type: 'SERVICE', title: 'Parkett Eiche',
    quantity: 1, unit: 'm²', price: 89,
};

const props = {
    block, isLocked: false,
    onModusWechsel: vi.fn(),
    onAlternativOeffnen: vi.fn(),
};

const oeffne = () => fireEvent.click(screen.getByRole('button', { name: /Kunde wählt/ }));
const punkte = () => screen.getAllByRole('menuitemradio');

beforeEach(() => vi.clearAllMocks());

describe('WahlpositionMenu Portal', () => {
    it('haengt die Liste an document.body statt in den Karten-Baum', () => {
        const { container } = render(<WahlpositionMenu {...props} />);
        oeffne();

        const menu = screen.getByRole('menu');
        expect(container.contains(menu)).toBe(false);
        expect(document.body.contains(menu)).toBe(true);
    });

    it('positioniert die Liste fixed, damit overflow-hidden sie nicht abschneidet', () => {
        render(<WahlpositionMenu {...props} />);
        oeffne();

        expect(screen.getByRole('menu')).toHaveStyle({ position: 'fixed' });
    });

    it('laesst beim Schliessen nichts im DOM zurueck', () => {
        render(<WahlpositionMenu {...props} />);
        oeffne();
        expect(screen.getByRole('menu')).toBeInTheDocument();

        fireEvent.keyDown(document, { key: 'Escape' });
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    });

    it('schliesst beim Scrollen, weil der Trigger unter der Liste wegwandert', () => {
        render(<WahlpositionMenu {...props} />);
        oeffne();

        fireEvent.scroll(window);
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    });

    it('oeffnet im gesperrten Dokument gar nicht', () => {
        render(<WahlpositionMenu {...props} isLocked />);
        oeffne();

        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    });
});

describe('WahlpositionMenu Tastatur', () => {
    it('springt mit ArrowDown vom Trigger auf den ersten Eintrag', () => {
        render(<WahlpositionMenu {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'ArrowDown' });
        expect(document.activeElement).toBe(punkte()[0]);
    });

    it('springt mit ArrowUp vom Trigger auf den letzten Eintrag', () => {
        render(<WahlpositionMenu {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'ArrowUp' });
        expect(document.activeElement).toBe(punkte()[punkte().length - 1]);
    });

    it('laeuft mit ArrowDown am Ende wieder auf den Anfang', () => {
        render(<WahlpositionMenu {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'End' });
        fireEvent.keyDown(document, { key: 'ArrowDown' });
        expect(document.activeElement).toBe(punkte()[0]);
    });

    it('erreicht mit Home und End die Raender', () => {
        render(<WahlpositionMenu {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'End' });
        expect(document.activeElement).toBe(punkte()[punkte().length - 1]);

        fireEvent.keyDown(document, { key: 'Home' });
        expect(document.activeElement).toBe(punkte()[0]);
    });

    it('schliesst mit Tab, damit der Fokus nicht hinter der Liste verschwindet', () => {
        render(<WahlpositionMenu {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'Tab' });
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    });
});

describe('WahlpositionMenu Zustand', () => {
    it('markiert bei einer festen Position "Fest beauftragt" als aktiv', () => {
        render(<WahlpositionMenu {...props} />);
        oeffne();

        expect(screen.getByRole('menuitemradio', { name: /Fest beauftragt/ })).toBeChecked();
        expect(screen.getByRole('menuitemradio', { name: /Optional/ })).not.toBeChecked();
    });

    it('markiert bei einem Gruppenmitglied "Alternativ" als aktiv', () => {
        render(<WahlpositionMenu {...props}
            block={{ ...block, optional: true, alternativGruppe: 'Bodenbelag' }} />);
        fireEvent.click(screen.getByRole('button', { name: /Alternative/ }));

        expect(screen.getByRole('menuitemradio', { name: /Alternativ/ })).toBeChecked();
    });

    it('gibt den Fokus nach der Wahl an den Trigger zurueck', () => {
        render(<WahlpositionMenu {...props} />);
        const trigger = screen.getByRole('button', { name: /Kunde wählt/ });
        fireEvent.click(trigger);
        fireEvent.click(screen.getByRole('menuitemradio', { name: /Optional/ }));

        expect(document.activeElement).toBe(trigger);
    });
});
