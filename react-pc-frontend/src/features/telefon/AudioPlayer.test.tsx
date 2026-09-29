import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { AudioPlayer } from './AudioPlayer';

function zeige(props: Partial<React.ComponentProps<typeof AudioPlayer>> = {}) {
    return render(
        <ToastProvider>
            <AudioPlayer src="/api/telefon/sprachnachrichten/11/audio" dauerSekunden={65} beschriftung="Nachricht von Max Mustermann" {...props} />
        </ToastProvider>,
    );
}

describe('AudioPlayer', () => {
    let play: ReturnType<typeof vi.fn>;
    let pause: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        play = vi.fn().mockResolvedValue(undefined);
        pause = vi.fn();
        Object.defineProperty(HTMLMediaElement.prototype, 'play', { configurable: true, value: play });
        Object.defineProperty(HTMLMediaElement.prototype, 'pause', { configurable: true, value: pause });
    });
    afterEach(() => vi.restoreAllMocks());

    it('spielt ab, meldet den Start und hält wieder an', async () => {
        const onStart = vi.fn();
        zeige({ onWiedergabeStart: onStart });
        expect(screen.getByText('0:00 / 1:05')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Abspielen: Nachricht von Max Mustermann' }));
        await waitFor(() => expect(onStart).toHaveBeenCalledTimes(1));
        expect(play).toHaveBeenCalled();
        fireEvent.click(await screen.findByRole('button', { name: 'Anhalten: Nachricht von Max Mustermann' }));
        expect(pause).toHaveBeenCalled();
    });

    it('spult per Pfeiltasten, Pos1 und Ende', () => {
        zeige();
        const regler = screen.getByRole('slider', { name: /Position in der Nachricht/ });
        expect(regler).toHaveAttribute('aria-valuemax', '65');
        fireEvent.keyDown(regler, { key: 'ArrowRight' });
        fireEvent.keyDown(regler, { key: 'ArrowRight' });
        expect(regler).toHaveAttribute('aria-valuenow', '10');
        expect(regler).toHaveAttribute('aria-valuetext', '0:10 von 1:05');
        fireEvent.keyDown(regler, { key: 'ArrowLeft' });
        expect(regler).toHaveAttribute('aria-valuenow', '5');
        fireEvent.keyDown(regler, { key: 'End' });
        expect(regler).toHaveAttribute('aria-valuenow', '65');
        fireEvent.keyDown(regler, { key: 'Home' });
        expect(regler).toHaveAttribute('aria-valuenow', '0');
    });

    it('spult per Klick auf den Balken', () => {
        zeige();
        const regler = screen.getByRole('slider');
        vi.spyOn(regler, 'getBoundingClientRect').mockReturnValue({ left: 0, width: 200, top: 0, height: 10, right: 200, bottom: 10, x: 0, y: 0, toJSON: () => ({}) });
        fireEvent.pointerDown(regler, { clientX: 100, pointerId: 1 });
        fireEvent.pointerUp(regler, { pointerId: 1 });
        expect(regler).toHaveAttribute('aria-valuenow', '33');
    });

    it('meldet einen Fehler beim Abspielen per Toast', async () => {
        play.mockRejectedValueOnce(new Error('kaputt'));
        const onStart = vi.fn();
        zeige({ onWiedergabeStart: onStart });
        fireEvent.click(screen.getByRole('button', { name: /Abspielen/ }));
        expect(await screen.findByText('Die Nachricht konnte nicht abgespielt werden.')).toBeInTheDocument();
        expect(onStart).not.toHaveBeenCalled();
    });

    it('hält an, wenn eine andere Nachricht startet', async () => {
        render(
            <ToastProvider>
                <AudioPlayer src="/a" dauerSekunden={10} beschriftung="Nachricht A" />
                <AudioPlayer src="/b" dauerSekunden={10} beschriftung="Nachricht B" />
            </ToastProvider>,
        );
        fireEvent.click(screen.getByRole('button', { name: 'Abspielen: Nachricht A' }));
        await screen.findByRole('button', { name: 'Anhalten: Nachricht A' });
        pause.mockClear();
        fireEvent.click(screen.getByRole('button', { name: 'Abspielen: Nachricht B' }));
        await waitFor(() => expect(pause).toHaveBeenCalled());
    });

    it('verweigert gefährliche Adressen', () => {
        zeige({ src: 'javascript:alert(1)' });
        expect(screen.getByRole('button', { name: /Abspielen/ })).toBeDisabled();
    });
});
