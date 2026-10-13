import { describe, expect, it, vi } from 'vitest';
import { createEvent, fireEvent, render, screen } from '@testing-library/react';
import type { FormBlock } from '../../types';
import EditorCanvas, { type EditorCanvasProps } from './EditorCanvas';
import { BAUSTEIN_ZIEH_TYP } from './bausteinVorlagen';

const BLOECKE: FormBlock[] = [
    { id: 'a', type: 'text', page: 1, x: 100, y: 100, z: 1, width: 100, height: 50, content: '{{KUNDENADRESSE}}' },
    { id: 'b', type: 'text', page: 1, x: 300, y: 400, z: 2, width: 100, height: 50, content: 'Hallo' },
    { id: 'c', type: 'table', page: 2, x: 24, y: 60, z: 1, width: 548, height: 720 },
];

function zeichne(teil: Partial<EditorCanvasProps> = {}) {
    const props: EditorCanvasProps = {
        items: BLOECKE,
        seite: 1,
        ausgewaehltId: null,
        zoom: 1,
        bereichRef: { current: null },
        briefpapier: null,
        daten: { kundenadresse: 'Max Mustermann\nMusterweg 1' },
        normAn: false,
        normForm: 'B',
        anschriftStatus: 'ragt-heraus',
        onAuswaehlen: vi.fn(),
        onAendern: vi.fn(),
        onLoeschen: vi.fn(),
        onDuplizieren: vi.fn(),
        onAblegen: vi.fn(),
        onAnschriftInsFenster: vi.fn(),
        ...teil,
    };
    const ergebnis = render(<EditorCanvas {...props} />);
    return { props, ...ergebnis };
}

/** jsdom kennt kein PointerEvent – ein MouseEvent mit den Koordinaten reicht den Listenern. */
const zeiger = (typ: 'pointerdown' | 'pointermove' | 'pointerup', ziel: EventTarget, x: number, y: number) => {
    const ereignis = new MouseEvent(typ, { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 });
    ziel.dispatchEvent(ereignis);
};

const baustein = (name: string) => screen.getByRole('group', { name });

describe('EditorCanvas', () => {
    it('zeigt nur die Bausteine der aktiven Seite mit Beispieldaten', () => {
        zeichne();
        expect(screen.getAllByTestId('editor-baustein')).toHaveLength(2);
        expect(baustein('Anschrift')).toHaveTextContent('Max Mustermann');
    });

    it('verschiebt per Ziehen als ein Rückgängig-Schritt und rastet am Seitenrand ein', () => {
        const { props } = zeichne();
        zeiger('pointerdown', baustein('Anschrift'), 10, 10);
        expect(props.onAuswaehlen).toHaveBeenCalledWith('a');
        zeiger('pointermove', window, 11, 11); // unter der Schwelle: noch kein Zug
        expect(props.onAendern).not.toHaveBeenCalled();
        zeiger('pointermove', window, 60, 30);
        zeiger('pointermove', window, -88, 30);
        zeiger('pointerup', window, -88, 30);
        const aufrufe = vi.mocked(props.onAendern).mock.calls;
        expect(aufrufe[0]).toEqual(['a', { x: 150, y: 120 }, 'zug-1']);
        expect(aufrufe[1]).toEqual(['a', { x: 0, y: 120 }, 'zug-1']);
        zeiger('pointermove', window, 200, 200);
        expect(aufrufe).toHaveLength(2);
    });

    it('ändert die Größe über die Griffe, nicht kleiner als das Minimum', () => {
        const { props, container } = zeichne({ ausgewaehltId: 'a' });
        expect(screen.getByTestId('editor-auswahl')).toHaveTextContent('Anschrift');
        zeiger('pointerdown', container.querySelector('[data-griff="so"]')!, 0, 0);
        zeiger('pointermove', window, 37, 23);
        zeiger('pointerdown', container.querySelector('[data-griff="nw"]')!, 0, 0);
        zeiger('pointermove', window, 500, 500);
        zeiger('pointerup', window, 500, 500);
        const aufrufe = vi.mocked(props.onAendern).mock.calls;
        expect(aufrufe[0]).toEqual(['a', { x: 100, y: 100, width: 137, height: 73 }, 'zug-1']);
        expect(aufrufe[1][1]).toEqual({ x: 180, y: 140, width: 20, height: 10 });
    });

    it('Aktionsleiste: duplizieren und löschen', () => {
        const { props } = zeichne({ ausgewaehltId: 'b' });
        fireEvent.click(screen.getByRole('button', { name: 'Baustein duplizieren' }));
        fireEvent.click(screen.getByRole('button', { name: 'Baustein löschen' }));
        expect(props.onDuplizieren).toHaveBeenCalled();
        expect(props.onLoeschen).toHaveBeenCalledWith('b');
    });

    it('Klick auf freie Fläche hebt die Auswahl auf, Enter wählt per Tastatur', () => {
        const { props } = zeichne();
        zeiger('pointerdown', screen.getByTestId('editor-seite'), 5, 5);
        zeiger('pointerdown', screen.getByTestId('editor-arbeitsflaeche'), 5, 5);
        fireEvent.keyDown(baustein('Text'), { key: 'Enter' });
        expect(props.onAuswaehlen).toHaveBeenNthCalledWith(1, null);
        expect(props.onAuswaehlen).toHaveBeenNthCalledWith(2, null);
        expect(props.onAuswaehlen).toHaveBeenLastCalledWith('b');
    });

    it('legt einen gezogenen Baustein an der Mausposition ab', () => {
        const { props } = zeichne({ zoom: 0.5 });
        const seite = screen.getByTestId('editor-seite');
        const daten = { types: [BAUSTEIN_ZIEH_TYP], getData: () => 'anschrift', dropEffect: 'none' };
        fireEvent.dragOver(seite, { dataTransfer: daten });
        const ablage = createEvent.drop(seite, { dataTransfer: daten });
        Object.defineProperties(ablage, { clientX: { value: 100 }, clientY: { value: 50 } });
        fireEvent(seite, ablage);
        expect(props.onAblegen).toHaveBeenCalledWith('anschrift', { x: 200, y: 100 });
        fireEvent.drop(seite, { dataTransfer: { types: [BAUSTEIN_ZIEH_TYP], getData: () => 'quatsch' } });
        fireEvent.dragOver(seite, { dataTransfer: { types: ['text/plain'] } });
        expect(props.onAblegen).toHaveBeenCalledTimes(1);
    });

    it('zeigt mit Norm-Hilfslinien Overlay und Hinweis – nur auf Seite 1', () => {
        const { props, rerender } = zeichne({ normAn: true, briefpapier: 'data:image/png;base64,iVBORw0KGgo=' });
        expect(screen.getByTestId('norm-overlay')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Anschrift ins Fenster setzen' }));
        expect(props.onAnschriftInsFenster).toHaveBeenCalled();
        rerender(<EditorCanvas {...props} seite={2} />);
        expect(screen.queryByTestId('norm-overlay')).not.toBeInTheDocument();
        expect(screen.getAllByTestId('editor-baustein')).toHaveLength(1);
    });
});
