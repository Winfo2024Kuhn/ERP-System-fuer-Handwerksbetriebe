/**
 * Vitest-Suite fuer useVerlaufTastatur -- globale Strg+Z/Strg+Y-Behandlung im
 * Dokumenteditor. Reine DOM-Interaktion, keine personenbezogenen Daten.
 */
import { renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { darfVerlaufTasteGreifen, useVerlaufTastatur } from './useVerlaufTastatur';

function tastaturEvent(init: KeyboardEventInit): KeyboardEvent {
    return new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init });
}

describe('darfVerlaufTasteGreifen', () => {
    let wurzel: HTMLDivElement;
    let innen: HTMLDivElement;
    let aussen: HTMLDivElement;

    beforeEach(() => {
        wurzel = document.createElement('div');
        innen = document.createElement('div');
        wurzel.appendChild(innen);
        aussen = document.createElement('div');
        document.body.appendChild(wurzel);
        document.body.appendChild(aussen);
    });

    afterEach(() => {
        wurzel.remove();
        aussen.remove();
        document.querySelectorAll('[aria-modal="true"]').forEach(el => el.remove());
    });

    it('greift, wenn das Ziel innerhalb der Wurzel liegt', () => {
        expect(darfVerlaufTasteGreifen(innen, wurzel)).toBe(true);
    });

    it('greift, wenn das Ziel die Wurzel selbst ist', () => {
        expect(darfVerlaufTasteGreifen(wurzel, wurzel)).toBe(true);
    });

    it('greift NICHT, wenn das Ziel ausserhalb der Wurzel liegt', () => {
        expect(darfVerlaufTasteGreifen(aussen, wurzel)).toBe(false);
    });

    it('greift bei document.body als Ziel (kein Element hat mehr den Fokus)', () => {
        expect(darfVerlaufTasteGreifen(document.body, wurzel)).toBe(true);
    });

    it('greift NICHT, wenn das Ziel selbst oder ein Vorfahre [data-eigenes-rueckgaengig] traegt', () => {
        innen.setAttribute('data-eigenes-rueckgaengig', 'true');
        const kind = document.createElement('input');
        innen.appendChild(kind);

        expect(darfVerlaufTasteGreifen(kind, wurzel)).toBe(false);
        expect(darfVerlaufTasteGreifen(innen, wurzel)).toBe(false);
    });

    it('greift NICHT, wenn irgendwo im Dokument ein modaler Dialog offen ist', () => {
        const dialog = document.createElement('div');
        dialog.setAttribute('aria-modal', 'true');
        document.body.appendChild(dialog);

        expect(darfVerlaufTasteGreifen(innen, wurzel)).toBe(false);

        dialog.remove();
    });

    it('greift NICHT ohne Wurzel-Element', () => {
        expect(darfVerlaufTasteGreifen(innen, null)).toBe(false);
    });

    it('greift NICHT, wenn das Ziel kein DOM-Node ist', () => {
        expect(darfVerlaufTasteGreifen(null, wurzel)).toBe(false);
    });
});

describe('useVerlaufTastatur', () => {
    let wurzel: HTMLDivElement;

    beforeEach(() => {
        wurzel = document.createElement('div');
        document.body.appendChild(wurzel);
    });

    afterEach(() => {
        wurzel.remove();
        document.querySelectorAll('[aria-modal="true"]').forEach(el => el.remove());
    });

    function aufbauen(aktiv = true) {
        const onRueckgaengig = vi.fn();
        const onWiederholen = vi.fn();
        const wurzelRef = { current: wurzel };
        const hook = renderHook(() => useVerlaufTastatur({ aktiv, wurzelRef, onRueckgaengig, onWiederholen }));
        return { onRueckgaengig, onWiederholen, hook };
    }

    it('ctrlKey+z loest onRueckgaengig aus', () => {
        const { onRueckgaengig } = aufbauen();
        wurzel.dispatchEvent(tastaturEvent({ key: 'z', ctrlKey: true }));
        expect(onRueckgaengig).toHaveBeenCalledTimes(1);
    });

    it('metaKey+z loest ebenfalls onRueckgaengig aus', () => {
        const { onRueckgaengig } = aufbauen();
        wurzel.dispatchEvent(tastaturEvent({ key: 'z', metaKey: true }));
        expect(onRueckgaengig).toHaveBeenCalledTimes(1);
    });

    it.each([
        ['ctrlKey+y', { key: 'y', ctrlKey: true }],
        ['ctrlKey+shiftKey+z', { key: 'z', ctrlKey: true, shiftKey: true }],
        ['metaKey+shiftKey+z', { key: 'z', metaKey: true, shiftKey: true }],
    ] as const)('%s loest onWiederholen aus', (_name, init) => {
        const { onWiederholen } = aufbauen();
        wurzel.dispatchEvent(tastaturEvent(init));
        expect(onWiederholen).toHaveBeenCalledTimes(1);
    });

    it('Grossschreibung (Shift ohne Redo-Kombination waere Z) wird ueber e.key wie das logische Zeichen behandelt', () => {
        // e.key liefert bei gedrueckter Shift-Taste bereits 'Z' -- toLowerCase()
        // im Hook normalisiert das, unabhaengig vom Tastaturlayout (QWERTZ).
        const { onWiederholen } = aufbauen();
        wurzel.dispatchEvent(tastaturEvent({ key: 'Z', ctrlKey: true, shiftKey: true }));
        expect(onWiederholen).toHaveBeenCalledTimes(1);
    });

    it('zusaetzliches altKey unterdrueckt die Tastenkombination', () => {
        const { onRueckgaengig, onWiederholen } = aufbauen();
        wurzel.dispatchEvent(tastaturEvent({ key: 'z', ctrlKey: true, altKey: true }));
        expect(onRueckgaengig).not.toHaveBeenCalled();
        expect(onWiederholen).not.toHaveBeenCalled();
    });

    it('eine andere Taste (kein z/y) loest nichts aus', () => {
        const { onRueckgaengig, onWiederholen } = aufbauen();
        wurzel.dispatchEvent(tastaturEvent({ key: 'a', ctrlKey: true }));
        expect(onRueckgaengig).not.toHaveBeenCalled();
        expect(onWiederholen).not.toHaveBeenCalled();
    });

    it('aktiv: false registriert keinen Listener', () => {
        const { onRueckgaengig } = aufbauen(false);
        wurzel.dispatchEvent(tastaturEvent({ key: 'z', ctrlKey: true }));
        expect(onRueckgaengig).not.toHaveBeenCalled();
    });

    it('ein Ziel ausserhalb der Wurzel loest nichts aus', () => {
        const { onRueckgaengig } = aufbauen();
        const aussen = document.createElement('div');
        document.body.appendChild(aussen);

        aussen.dispatchEvent(tastaturEvent({ key: 'z', ctrlKey: true }));

        expect(onRueckgaengig).not.toHaveBeenCalled();
        aussen.remove();
    });

    it('ein Vorfahre mit [data-eigenes-rueckgaengig] loest nichts aus', () => {
        const { onRueckgaengig } = aufbauen();
        const eigenesFeld = document.createElement('div');
        eigenesFeld.setAttribute('data-eigenes-rueckgaengig', 'true');
        wurzel.appendChild(eigenesFeld);

        eigenesFeld.dispatchEvent(tastaturEvent({ key: 'z', ctrlKey: true }));

        expect(onRueckgaengig).not.toHaveBeenCalled();
    });

    it('Ziel document.body loest aus (nach Loeschen hat kein Element mehr den Fokus)', () => {
        const { onRueckgaengig } = aufbauen();
        document.body.dispatchEvent(tastaturEvent({ key: 'z', ctrlKey: true }));
        expect(onRueckgaengig).toHaveBeenCalledTimes(1);
    });

    it('ein offener modaler Dialog irgendwo im Dokument unterdrueckt die Taste', () => {
        const { onRueckgaengig } = aufbauen();
        const dialog = document.createElement('div');
        dialog.setAttribute('aria-modal', 'true');
        document.body.appendChild(dialog);

        wurzel.dispatchEvent(tastaturEvent({ key: 'z', ctrlKey: true }));

        expect(onRueckgaengig).not.toHaveBeenCalled();
        dialog.remove();
    });

    it('ruft preventDefault auf, wenn die Taste greift', () => {
        aufbauen();
        const event = tastaturEvent({ key: 'z', ctrlKey: true });
        wurzel.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
    });

    it('ruft preventDefault NICHT auf, wenn die Taste nicht greift (ausserhalb der Wurzel)', () => {
        aufbauen();
        const aussen = document.createElement('div');
        document.body.appendChild(aussen);

        const event = tastaturEvent({ key: 'z', ctrlKey: true });
        aussen.dispatchEvent(event);

        expect(event.defaultPrevented).toBe(false);
        aussen.remove();
    });

    it('meldet den Listener beim Unmount ab', () => {
        const { onRueckgaengig, hook } = aufbauen();
        hook.unmount();
        wurzel.dispatchEvent(tastaturEvent({ key: 'z', ctrlKey: true }));
        expect(onRueckgaengig).not.toHaveBeenCalled();
    });
});
