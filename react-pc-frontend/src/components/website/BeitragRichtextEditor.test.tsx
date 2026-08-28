import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { BeitragRichtextEditor } from './BeitragRichtextEditor';

// jsdom (aktuell v28) implementiert weder document.elementFromPoint noch
// Range.getClientRects/getBoundingClientRect. ProseMirror braucht beides, um bei
// Klick/Tastatureingabe die Cursor-Position im DOM zu berechnen - ohne die Stubs
// wirft jede Interaktion mit dem Editor in Tests eine TypeError-Exception.
// Lebt bewusst nur in dieser Testdatei statt in der geteilten src/setupTests.ts,
// da diese Aufgabe ausschliesslich die beiden BeitragRichtextEditor-Dateien anfasst.
if (!document.elementFromPoint) {
    document.elementFromPoint = () => null;
}
if (!Range.prototype.getBoundingClientRect) {
    Range.prototype.getBoundingClientRect = () => ({
        bottom: 0, height: 0, left: 0, right: 0, top: 0, width: 0, x: 0, y: 0, toJSON: () => {},
    });
}
if (!Range.prototype.getClientRects) {
    Range.prototype.getClientRects = () => ({
        length: 0,
        item: () => null,
        [Symbol.iterator]: function* () {},
    }) as unknown as DOMRectList;
}

describe('BeitragRichtextEditor', () => {
    it('zeigt den uebergebenen Text an', () => {
        render(<BeitragRichtextEditor html="<p>Schiebetor gesetzt.</p>" onChange={() => {}} />);

        expect(screen.getByText('Schiebetor gesetzt.')).toBeInTheDocument();
    });

    it('hat genau die Knoepfe, deren Ergebnis die Website behaelt', () => {
        render(<BeitragRichtextEditor html="" onChange={() => {}} />);

        expect(screen.getByRole('button', { name: 'Fett' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Kursiv' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Aufzählung' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Nummerierte Liste' })).toBeInTheDocument();
        // Ueberschriften und Ausrichtung wirft die Website weg, also gibt es sie hier nicht.
        expect(screen.queryByRole('button', { name: /Ueberschrift/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Zentriert/ })).not.toBeInTheDocument();
    });

    it('meldet Aenderungen nach oben', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        render(<BeitragRichtextEditor html="<p>Start</p>" onChange={onChange} />);

        await user.click(screen.getByText('Start'));
        await user.keyboard('!');

        expect(onChange).toHaveBeenCalled();
        expect(onChange.mock.calls.at(-1)?.[0]).toContain('Start');
    });

    it('uebernimmt einen Wechsel des Beitrags von aussen', () => {
        const { rerender } = render(<BeitragRichtextEditor html="<p>Erster</p>" onChange={() => {}} />);

        rerender(<BeitragRichtextEditor html="<p>Zweiter</p>" onChange={() => {}} />);

        expect(screen.getByText('Zweiter')).toBeInTheDocument();
        expect(screen.queryByText('Erster')).not.toBeInTheDocument();
    });
});
