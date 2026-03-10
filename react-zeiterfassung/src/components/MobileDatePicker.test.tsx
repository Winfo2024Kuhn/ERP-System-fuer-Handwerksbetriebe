import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import MobileDatePicker from './MobileDatePicker';

describe('MobileDatePicker', () => {
    it('rendert mit Standard-Platzhalter', () => {
        render(<MobileDatePicker value="" onChange={() => {}} />);
        expect(screen.getByText('Datum wählen...')).toBeInTheDocument();
    });

    it('zeigt formatiertes Datum an', () => {
        render(<MobileDatePicker value="2026-03-10" onChange={() => {}} />);
        expect(screen.getByText('10.03.2026')).toBeInTheDocument();
    });

    it('rendert Label wenn angegeben', () => {
        render(<MobileDatePicker value="" onChange={() => {}} label="Startdatum" />);
        expect(screen.getByText('Startdatum')).toBeInTheDocument();
    });

    it('öffnet Kalender bei Klick', async () => {
        const user = userEvent.setup();
        render(<MobileDatePicker value="" onChange={() => {}} />);
        await user.click(screen.getByText('Datum wählen...'));
        // Wochentage sollten sichtbar sein
        expect(screen.getByText('Mo')).toBeInTheDocument();
        expect(screen.getByText('Di')).toBeInTheDocument();
        expect(screen.getByText('So')).toBeInTheDocument();
    });

    it('zeigt deutsche Monatsnamen', async () => {
        const user = userEvent.setup();
        render(<MobileDatePicker value="2026-01-15" onChange={() => {}} />);
        await user.click(screen.getByText('15.01.2026'));
        expect(screen.getByText(/Januar 2026/)).toBeInTheDocument();
    });

    it('ruft onChange bei Datumsauswahl auf', async () => {
        const handleChange = vi.fn();
        const user = userEvent.setup();
        render(<MobileDatePicker value="2026-03-01" onChange={handleChange} />);
        await user.click(screen.getByText('01.03.2026'));
        // Klick auf einen Tag
        await user.click(screen.getByText('15'));
        expect(handleChange).toHaveBeenCalledWith('2026-03-15');
    });

    it('navigiert zum nächsten Monat', async () => {
        const user = userEvent.setup();
        render(<MobileDatePicker value="2026-01-15" onChange={() => {}} />);
        await user.click(screen.getByText('15.01.2026'));
        expect(screen.getByText(/Januar 2026/)).toBeInTheDocument();
        // Klick auf den Vorwärts-Button
        const buttons = screen.getAllByRole('button');
        const nextButton = buttons.find(b => b.querySelector('.lucide-chevron-right'));
        if (nextButton) {
            await user.click(nextButton);
            expect(screen.getByText(/Februar 2026/)).toBeInTheDocument();
        }
    });

    it('hat Heute-Button', async () => {
        const handleChange = vi.fn();
        const user = userEvent.setup();
        render(<MobileDatePicker value="" onChange={handleChange} />);
        await user.click(screen.getByText('Datum wählen...'));
        await user.click(screen.getByText('Heute'));
        expect(handleChange).toHaveBeenCalled();
        const calledValue = handleChange.mock.calls[0][0];
        expect(calledValue).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    it('hat Schließen-Button', async () => {
        const user = userEvent.setup();
        render(<MobileDatePicker value="" onChange={() => {}} />);
        await user.click(screen.getByText('Datum wählen...'));
        expect(screen.getByText('Schließen')).toBeInTheDocument();
        await user.click(screen.getByText('Schließen'));
        // Kalender sollte geschlossen sein
        expect(screen.queryByText('Schließen')).not.toBeInTheDocument();
    });

    it('rendert hidden input für required-Validierung', () => {
        const { container } = render(<MobileDatePicker value="2026-01-01" onChange={() => {}} required />);
        const hiddenInput = container.querySelector('input[required]');
        expect(hiddenInput).toBeInTheDocument();
    });
});
