import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Reiter, ReiterPanel, type ReiterEintrag } from './reiter';

type Id = 'eins' | 'zwei' | 'drei';

const REITER: ReiterEintrag<Id>[] = [
    { id: 'eins', label: 'Das Wichtigste', kennzeichen: { art: 'haken', text: 'ausgefüllt' } },
    { id: 'zwei', label: 'Preis', zusatz: 'optional', anzahl: 2 },
    { id: 'drei', label: 'Mehr', kennzeichen: { art: 'punkt', text: 'nicht gespeichert' } },
];

function Leiste({ onWechsel }: { onWechsel?: (id: Id) => void }) {
    const [aktiv, setAktiv] = useState<Id>('eins');
    return (
        <>
            <Reiter reiter={REITER} aktiv={aktiv} ariaLabel="Bereiche" idPrefix="probe"
                onWechsel={(id) => { setAktiv(id); onWechsel?.(id); }} />
            <ReiterPanel idPrefix="probe" id={aktiv}>Inhalt {aktiv}</ReiterPanel>
        </>
    );
}

describe('Reiter', () => {
    it('verknüpft Reiter und Bereich per ARIA und hält nur den aktiven in der Tab-Reihenfolge', () => {
        render(<Leiste />);

        expect(screen.getByRole('tablist', { name: 'Bereiche' })).toBeInTheDocument();
        const tabs = screen.getAllByRole('tab');
        expect(tabs).toHaveLength(3);
        expect(tabs[0]).toHaveAttribute('aria-selected', 'true');
        expect(tabs[0]).toHaveAttribute('tabindex', '0');
        expect(tabs[1]).toHaveAttribute('aria-selected', 'false');
        expect(tabs[1]).toHaveAttribute('tabindex', '-1');
        expect(tabs[0]).toHaveAttribute('aria-controls', 'probe-panel-eins');

        const panel = screen.getByRole('tabpanel', { name: /Das Wichtigste/ });
        expect(panel).toHaveAttribute('id', 'probe-panel-eins');
        expect(panel).toHaveTextContent('Inhalt eins');
    });

    it('zeigt Zusatz, Anzahl und Kennzeichen auch für Screenreader', () => {
        render(<Leiste />);
        const tabs = screen.getAllByRole('tab');
        expect(tabs[0]).toHaveTextContent('ausgefüllt');
        expect(tabs[1]).toHaveTextContent('optional');
        expect(tabs[1]).toHaveTextContent('2');
        expect(tabs[2]).toHaveTextContent('nicht gespeichert');
    });

    it('wechselt per Klick', async () => {
        const onWechsel = vi.fn();
        render(<Leiste onWechsel={onWechsel} />);
        await userEvent.click(screen.getByRole('tab', { name: /Preis/ }));
        expect(onWechsel).toHaveBeenCalledWith('zwei');
        expect(screen.getByRole('tabpanel')).toHaveTextContent('Inhalt zwei');
    });

    it('wandert mit Pfeiltasten (rundum) sowie Pos1/Ende und nimmt den Fokus mit', async () => {
        const user = userEvent.setup();
        render(<Leiste />);
        const [eins, zwei, drei] = screen.getAllByRole('tab');

        await user.click(eins);
        await user.keyboard('{ArrowRight}');
        expect(zwei).toHaveFocus();
        expect(zwei).toHaveAttribute('aria-selected', 'true');

        await user.keyboard('{End}');
        expect(drei).toHaveFocus();
        await user.keyboard('{ArrowRight}');
        expect(eins).toHaveFocus();
        await user.keyboard('{ArrowLeft}');
        expect(drei).toHaveFocus();
        await user.keyboard('{Home}');
        expect(eins).toHaveFocus();
        expect(screen.getByRole('tabpanel')).toHaveTextContent('Inhalt eins');
    });

    it('lässt andere Tasten in Ruhe', async () => {
        const user = userEvent.setup();
        const onWechsel = vi.fn();
        render(<Leiste onWechsel={onWechsel} />);
        await user.click(screen.getAllByRole('tab')[0]);
        onWechsel.mockClear();
        await user.keyboard('{ArrowDown}a');
        expect(onWechsel).not.toHaveBeenCalled();
    });
});
