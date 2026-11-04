import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EmailsTabToolbar } from './EmailsTabToolbar';
import type { EmailsTabFilter } from './emailsTabTypes';

function rendere(filter: EmailsTabFilter = 'all', showCompose = true) {
    const props = { onSearchChange: vi.fn(), onFilterChange: vi.fn(), onCompose: vi.fn() };
    const view = render(<EmailsTabToolbar search="" filter={filter} showCompose={showCompose} {...props} />);
    return { ...view, ...props };
}

describe('EmailsTabToolbar', () => {
    it('filtert über die gemeinsame Segment-Auswahl, aktiv immer in Rose, mit Pfeiltasten', () => {
        const { container, onFilterChange } = rendere('in');
        const gruppe = screen.getByRole('radiogroup', { name: 'E-Mails filtern' });
        expect(gruppe).toBeInTheDocument();
        const eingang = screen.getByRole('radio', { name: 'Eingang' });
        expect(eingang).toHaveAttribute('aria-checked', 'true');
        expect(eingang.className).toContain('rose');
        expect(container.innerHTML).not.toMatch(/blue-|green-/);

        fireEvent.keyDown(eingang, { key: 'ArrowRight' });
        expect(onFilterChange).toHaveBeenLastCalledWith('out');
        fireEvent.click(screen.getByRole('radio', { name: 'Alle' }));
        expect(onFilterChange).toHaveBeenLastCalledWith('all');
    });

    it('hat ein beschriftetes Suchfeld mit echtem Auslassungszeichen', () => {
        const { onSearchChange } = rendere();
        const suche = screen.getByRole('textbox', { name: 'E-Mails durchsuchen' });
        expect(suche).toHaveAttribute('placeholder', 'E-Mails durchsuchen…');
        fireEvent.change(suche, { target: { value: 'Geländer' } });
        expect(onSearchChange).toHaveBeenCalledWith('Geländer');
    });

    it('zeigt „Neue E-Mail“ nur, wenn gewünscht', () => {
        const { onCompose, rerender } = rendere('all', true);
        fireEvent.click(screen.getByRole('button', { name: /Neue E-Mail/ }));
        expect(onCompose).toHaveBeenCalledTimes(1);

        rerender(<EmailsTabToolbar search="" filter="all" showCompose={false}
            onSearchChange={vi.fn()} onFilterChange={vi.fn()} onCompose={vi.fn()} />);
        expect(screen.queryByRole('button', { name: /Neue E-Mail/ })).not.toBeInTheDocument();
    });
});
