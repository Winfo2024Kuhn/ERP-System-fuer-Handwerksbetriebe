import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useEmailPaneWidth } from './useEmailPaneWidth';

type Callback = (entries: { contentRect: { width: number } }[]) => void;
const win = window as unknown as { ResizeObserver: unknown };

function Probe() {
    const { ref, width } = useEmailPaneWidth();
    return <div ref={ref} data-testid="pane">{width}</div>;
}

describe('useEmailPaneWidth', () => {
    const original = win.ResizeObserver;
    let callback: Callback;
    const observe = vi.fn();
    const disconnect = vi.fn();

    beforeEach(() => {
        observe.mockClear();
        disconnect.mockClear();
        callback = () => undefined;
        win.ResizeObserver = class {
            constructor(cb: Callback) { callback = cb; }
            observe = observe;
            disconnect = disconnect;
        };
    });
    afterEach(() => { win.ResizeObserver = original; });

    it('startet mit der Fensterbreite', () => {
        render(<Probe />);
        expect(screen.getByTestId('pane')).toHaveTextContent(String(window.innerWidth));
    });

    it('beobachtet das Element, uebernimmt Breiten > 0 und ignoriert 0', () => {
        const { unmount } = render(<Probe />);
        expect(observe).toHaveBeenCalledTimes(1);
        act(() => callback([{ contentRect: { width: 480 } }]));
        expect(screen.getByTestId('pane')).toHaveTextContent('480');
        act(() => callback([{ contentRect: { width: 0 } }]));
        expect(screen.getByTestId('pane')).toHaveTextContent('480');
        unmount();
        expect(disconnect).toHaveBeenCalledTimes(1);
    });

    it('ohne ResizeObserver bleibt die Fensterbreite bestehen und es gibt keinen Fehler', () => {
        win.ResizeObserver = undefined;
        expect(() => render(<Probe />)).not.toThrow();
        expect(screen.getByTestId('pane')).toHaveTextContent(String(window.innerWidth));
        expect(observe).not.toHaveBeenCalled();
    });
});
