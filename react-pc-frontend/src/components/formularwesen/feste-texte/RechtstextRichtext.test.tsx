import { createRef } from 'react';
import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { RechtstextRichtext, type RechtstextRichtextHandle } from './RechtstextRichtext';

describe('RechtstextRichtext', () => {
    it('hat genau die Knöpfe, deren Ergebnis das Backend behält', () => {
        render(<RechtstextRichtext anfangsHtml="<p>Text</p>" onChange={() => {}} label="Text" />);

        const leiste = screen.getByRole('toolbar', { name: 'Textformat' });
        expect(within(leiste).getAllByRole('button').map(b => b.getAttribute('aria-label')))
            .toEqual(['Fett', 'Überschrift', 'Aufzählung', 'Nummerierung', 'Rückgängig', 'Wiederholen']);
        // Ohne Änderung gibt es nichts rückgängig zu machen – der Tooltip sagt das.
        expect(within(leiste).getByRole('button', { name: 'Rückgängig' })).toBeDisabled();
        expect(within(leiste).getByRole('button', { name: 'Rückgängig' })).toHaveAttribute('title', 'Es gibt nichts rückgängig zu machen.');
    });

    it('macht aus einem Absatz eine Überschrift der Stufe 3 und meldet das nach oben', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        render(<RechtstextRichtext anfangsHtml="<p>Widerrufsrecht</p>" onChange={onChange} label="Text" />);

        await user.click(screen.getByText('Widerrufsrecht'));
        await user.click(screen.getByRole('button', { name: 'Überschrift' }));

        expect(onChange.mock.calls.at(-1)?.[0]).toMatch(/^<h3>Widerrufsrecht<\/h3>/);
        expect(screen.getByRole('button', { name: 'Überschrift' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('button', { name: 'Rückgängig' })).toBeEnabled();
    });

    it('verwirft fremde Auszeichnungen wie Links, Bilder und Überschrift 1', () => {
        const onChange = vi.fn();
        const ref = createRef<RechtstextRichtextHandle>();
        render(<RechtstextRichtext ref={ref} anfangsHtml="" onChange={onChange} label="Text" />);

        act(() => ref.current?.setzeInhalt('<h1>Gross</h1><p><a href="https://example.de">Link</a><img src="x.png"><u>unter</u></p>', true));

        expect(onChange.mock.calls.at(-1)?.[0]).toBe('<p>Gross</p><p>Linkunter</p>');
    });

    it('lädt neuen Inhalt ohne Meldung, wenn es keine Änderung des Nutzers ist', () => {
        const onChange = vi.fn();
        const ref = createRef<RechtstextRichtextHandle>();
        render(<RechtstextRichtext ref={ref} anfangsHtml="<p>Alt</p>" onChange={onChange} label="Text" />);

        act(() => ref.current?.setzeInhalt('<p>Neu</p>', false));

        expect(screen.getByText('Neu')).toBeInTheDocument();
        expect(onChange).not.toHaveBeenCalled();
    });
});
