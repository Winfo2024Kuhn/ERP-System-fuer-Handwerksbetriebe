import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { EmailListInput } from './EmailListInput';

function Harness({ initial = [], onChange, kunden, anfrage }: {
    initial?: string[]; onChange?: (e: string[]) => void; kunden?: string[]; anfrage?: string[];
}) {
    const [emails, setEmails] = useState(initial);
    return <EmailListInput emails={emails} kundenEmails={kunden} anfrageEmails={anfrage}
        onChange={e => { setEmails(e); onChange?.(e); }} />;
}

const input = () => screen.getByPlaceholderText('E-Mail-Adresse eingeben...');

describe('EmailListInput', () => {
    it('fuegt eine gueltige Adresse per Enter hinzu und leert das Feld', async () => {
        const onChange = vi.fn();
        render(<Harness onChange={onChange} />);
        await userEvent.type(input(), '  max.mustermann@example.com {Enter}');
        expect(onChange).toHaveBeenCalledWith(['max.mustermann@example.com']);
        expect(input()).toHaveValue('');
        expect(screen.getByText('Zusätzliche E-Mails:')).toBeInTheDocument();
    });

    it('fuegt per Plus-Button hinzu', async () => {
        const onChange = vi.fn();
        render(<Harness onChange={onChange} />);
        await userEvent.type(input(), 'erika@example.org');
        await userEvent.click(screen.getByRole('button', { name: '' }));
        expect(onChange).toHaveBeenCalledWith(['erika@example.org']);
    });

    it('leere Eingabe wird ignoriert', async () => {
        const onChange = vi.fn();
        render(<Harness onChange={onChange} />);
        await userEvent.type(input(), '   {Enter}');
        expect(onChange).not.toHaveBeenCalled();
        expect(screen.queryByText(/gültige/)).toBeNull();
    });

    it.each(['keine-mail', 'a@b', '@example.com', 'a b@example.com', '<script>alert(1)</script>', "'; DROP TABLE x; --"])(
        'ungueltige Adresse "%s" wird mit Fehlermeldung abgelehnt', async value => {
            const onChange = vi.fn();
            render(<Harness onChange={onChange} />);
            await userEvent.type(input(), `${value}{Enter}`);
            expect(onChange).not.toHaveBeenCalled();
            expect(screen.getByText('Bitte gültige E-Mail-Adresse eingeben')).toBeInTheDocument();
        });

    it('Duplikate (auch gegenueber Kunden-/Anfrage-Adressen, ohne Gross-/Kleinschreibung) werden abgelehnt', async () => {
        const onChange = vi.fn();
        render(<Harness initial={['a@example.com']} kunden={['kunde@example.com']} anfrage={['anfrage@example.com']}
            onChange={onChange} />);
        for (const dup of ['A@Example.com', 'KUNDE@example.com', 'anfrage@example.com']) {
            await userEvent.clear(input());
            await userEvent.type(input(), `${dup}{Enter}`);
            expect(screen.getByText('Diese E-Mail-Adresse existiert bereits')).toBeInTheDocument();
        }
        expect(onChange).not.toHaveBeenCalled();
    });

    it('Fehlermeldung verschwindet beim naechsten Tippen', async () => {
        render(<Harness />);
        await userEvent.type(input(), 'kaputt{Enter}');
        expect(screen.getByText('Bitte gültige E-Mail-Adresse eingeben')).toBeInTheDocument();
        await userEvent.type(input(), 'x');
        expect(screen.queryByText('Bitte gültige E-Mail-Adresse eingeben')).toBeNull();
    });

    it('entfernt eine Adresse ueber den Entfernen-Button', async () => {
        const onChange = vi.fn();
        render(<Harness initial={['a@example.com', 'b@example.com']} onChange={onChange} />);
        const buttons = screen.getAllByTitle('E-Mail entfernen');
        await userEvent.click(buttons[0]);
        expect(onChange).toHaveBeenCalledWith(['b@example.com']);
        expect(screen.queryByText('a@example.com')).toBeNull();
    });

    it('zeigt Kunden- und Anfrage-Adressen schreibgeschuetzt, doppelte Anfrage-Adressen nur einmal', () => {
        render(<Harness kunden={['kunde@example.com']} anfrage={['kunde@example.com', 'anfrage@example.com']} />);
        expect(screen.getByText('Vom Kunden:')).toBeInTheDocument();
        expect(screen.getByText('Vom Anfrage:')).toBeInTheDocument();
        expect(screen.getAllByText('kunde@example.com')).toHaveLength(1);
        expect(screen.getByText('anfrage@example.com')).toBeInTheDocument();
        expect(screen.queryByTitle('E-Mail entfernen')).toBeNull();
    });
});
