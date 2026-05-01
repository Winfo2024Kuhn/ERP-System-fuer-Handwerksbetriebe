import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { useState } from 'react';
import { PhoneInput } from './PhoneInput';

function Wrapper(props: {
    initial?: string;
    plz?: string;
    ort?: string;
    autoPrefillAreaCode?: boolean;
    variant?: 'festnetz' | 'mobil';
    onValue?: (v: string) => void;
}) {
    const [v, setV] = useState(props.initial ?? '');
    return (
        <div>
            <PhoneInput
                value={v}
                onChange={next => {
                    setV(next);
                    props.onValue?.(next);
                }}
                plz={props.plz}
                ort={props.ort}
                autoPrefillAreaCode={props.autoPrefillAreaCode}
                variant={props.variant ?? 'festnetz'}
                aria-label="phone"
            />
            <button onClick={() => setV('')}>reset</button>
        </div>
    );
}

describe('PhoneInput', () => {
    it('rendert ein tel-Eingabefeld', () => {
        render(<Wrapper />);
        const input = screen.getByLabelText('phone');
        expect(input).toHaveAttribute('type', 'tel');
    });

    it('akzeptiert -, /, ( und Leerzeichen', async () => {
        const user = userEvent.setup();
        const onValue = vi.fn();
        render(<Wrapper onValue={onValue} />);
        await user.type(screen.getByLabelText('phone'), '0511 / 123-45 (intern)');
        const input = screen.getByLabelText('phone') as HTMLInputElement;
        // Buchstaben werden gefiltert
        expect(input.value).not.toContain('intern');
        expect(input.value).toContain('/');
        expect(input.value).toContain('-');
    });

    it('lädt Vorwahl aus PLZ vor wenn Festnetz-Feld leer ist', () => {
        render(<Wrapper autoPrefillAreaCode plz="30163" ort="Hannover" />);
        const input = screen.getByLabelText('phone') as HTMLInputElement;
        expect(input.value.startsWith('0511')).toBe(true);
    });

    it('lädt KEINE Vorwahl bei variant=mobil', () => {
        render(<Wrapper autoPrefillAreaCode plz="30163" variant="mobil" />);
        const input = screen.getByLabelText('phone') as HTMLInputElement;
        expect(input.value).toBe('');
    });

    it('überschreibt KEINEN bereits eingegebenen Wert', () => {
        render(<Wrapper initial="0177 1234567" autoPrefillAreaCode plz="30163" />);
        const input = screen.getByLabelText('phone') as HTMLInputElement;
        expect(input.value).toBe('0177 1234567');
    });

    it('leert das Feld bei Blur wenn nur die vorgeschlagene Vorwahl drin steht', async () => {
        const user = userEvent.setup();
        render(<Wrapper autoPrefillAreaCode plz="30163" />);
        const input = screen.getByLabelText('phone') as HTMLInputElement;
        expect(input.value.startsWith('0511')).toBe(true);

        await act(async () => {
            input.focus();
            input.blur();
        });

        expect(input.value).toBe('');
    });

    it('leert NICHT wenn der Nutzer eine Nummer ergänzt hat', async () => {
        const user = userEvent.setup();
        render(<Wrapper autoPrefillAreaCode plz="30163" />);
        const input = screen.getByLabelText('phone') as HTMLInputElement;

        await user.click(input);
        await user.type(input, '123456');
        await act(async () => {
            input.blur();
        });

        expect(input.value).toContain('123456');
        expect(input.value.startsWith('0511')).toBe(true);
    });
});
