import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { SegmentAuswahl } from './segment-auswahl';
import type { SegmentOption } from './segment-auswahl';

const OPTIONEN: SegmentOption[] = [
    { wert: '', text: 'Standard', zusatz: 'folgt der Wahl oben' },
    { wert: 'A', text: 'Anbieter A', gesperrt: 'A kann das nicht.' },
    { wert: 'B', text: 'Anbieter B' },
];

function Probe({ start = '' }: { start?: string }) {
    const [wert, setWert] = useState(start);
    return <SegmentAuswahl beschriftung="Anbieter" optionen={OPTIONEN} wert={wert} onChange={setWert} />;
}

describe('SegmentAuswahl', () => {
    it('zeigt alle Werte als Radio-Gruppe, nur das gewählte liegt in der Tab-Reihenfolge', () => {
        render(<Probe />);
        const gruppe = screen.getByRole('radiogroup', { name: 'Anbieter' });
        expect(gruppe).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: /Standard/ })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('radio', { name: /Standard/ })).toHaveAttribute('tabindex', '0');
        expect(screen.getByRole('radio', { name: 'Anbieter B' })).toHaveAttribute('tabindex', '-1');
        expect(screen.getByText('folgt der Wahl oben')).toBeInTheDocument();
    });

    it('Klick wählt, gesperrte Werte bleiben sichtbar, sagen warum und lassen sich nicht wählen', async () => {
        const user = userEvent.setup();
        render(<Probe />);
        const a = screen.getByRole('radio', { name: 'Anbieter A' });
        expect(a).toHaveAttribute('aria-disabled', 'true');
        expect(a).toHaveAttribute('title', 'A kann das nicht.');
        await user.click(a);
        expect(a).toHaveAttribute('aria-checked', 'false');
        await user.click(screen.getByRole('radio', { name: 'Anbieter B' }));
        expect(screen.getByRole('radio', { name: 'Anbieter B' })).toHaveAttribute('aria-checked', 'true');
    });

    it('Pfeiltasten wandern und wählen, gesperrte werden übersprungen', async () => {
        const user = userEvent.setup();
        render(<Probe />);
        screen.getByRole('radio', { name: /Standard/ }).focus();
        await user.keyboard('{ArrowRight}');
        const b = screen.getByRole('radio', { name: 'Anbieter B' });
        expect(b).toHaveFocus();
        expect(b).toHaveAttribute('aria-checked', 'true');
        await user.keyboard('{ArrowRight}');
        expect(screen.getByRole('radio', { name: /Standard/ })).toHaveFocus();
        await user.keyboard('{ArrowLeft}');
        expect(b).toHaveFocus();
        await user.keyboard('{Enter}');
        expect(b).toHaveAttribute('aria-checked', 'true');
    });

    it('ohne gültige Wahl ist der erste nicht gesperrte Wert erreichbar', () => {
        render(<SegmentAuswahl beschriftung="X" optionen={[OPTIONEN[1], OPTIONEN[2]]} wert="Z" onChange={() => {}} />);
        expect(screen.getByRole('radio', { name: 'Anbieter B' })).toHaveAttribute('tabindex', '0');
    });
});
