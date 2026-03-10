import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { DatePicker } from './datepicker';

describe('DatePicker', () => {
    it('rendert mit Platzhaltertext', () => {
        render(<DatePicker value="" onChange={() => {}} placeholder="Datum wählen" />);
        expect(screen.getByText('Datum wählen')).toBeInTheDocument();
    });

    it('zeigt formatiertes Datum an wenn Value gesetzt', () => {
        render(<DatePicker value="2026-03-10" onChange={() => {}} />);
        expect(screen.getByText('10.03.2026')).toBeInTheDocument();
    });

    it('öffnet Kalender bei Klick', async () => {
        const user = userEvent.setup();
        render(<DatePicker value="" onChange={() => {}} />);
        await user.click(screen.getByText('Datum wählen'));
        // Wochentage sollten sichtbar sein
        expect(screen.getByText('Mo')).toBeInTheDocument();
        expect(screen.getByText('Di')).toBeInTheDocument();
        expect(screen.getByText('Fr')).toBeInTheDocument();
    });

    it('zeigt deutsche Monatsnamen', async () => {
        const user = userEvent.setup();
        render(<DatePicker value="2026-01-15" onChange={() => {}} />);
        await user.click(screen.getByText('15.01.2026'));
        expect(screen.getByText(/Januar/)).toBeInTheDocument();
    });

    it('ruft onChange mit YYYY-MM-DD Format auf', async () => {
        const handleChange = vi.fn();
        const user = userEvent.setup();
        render(<DatePicker value="2026-03-01" onChange={handleChange} />);
        await user.click(screen.getByText('01.03.2026'));
        // Klick auf Tag 15
        await user.click(screen.getByText('15'));
        expect(handleChange).toHaveBeenCalledWith('2026-03-15');
    });

    it('öffnet nicht bei disabled', async () => {
        const user = userEvent.setup();
        render(<DatePicker value="" onChange={() => {}} disabled />);
        await user.click(screen.getByText('Datum wählen'));
        expect(screen.queryByText('Mo')).not.toBeInTheDocument();
    });

    it('hat Löschen-Button der das Datum entfernt', async () => {
        const handleChange = vi.fn();
        const user = userEvent.setup();
        render(<DatePicker value="2026-03-10" onChange={handleChange} />);
        await user.click(screen.getByText('10.03.2026'));
        await user.click(screen.getByText('Löschen'));
        expect(handleChange).toHaveBeenCalledWith('');
    });

    it('hat Heute-Button der aktuelles Datum setzt', async () => {
        const handleChange = vi.fn();
        const user = userEvent.setup();
        render(<DatePicker value="" onChange={handleChange} />);
        await user.click(screen.getByText('Datum wählen'));
        await user.click(screen.getByText('Heute'));
        expect(handleChange).toHaveBeenCalled();
        const calledValue = handleChange.mock.calls[0][0];
        // Should be in YYYY-MM-DD format
        expect(calledValue).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    it('navigiert zum nächsten Monat', async () => {
        const user = userEvent.setup();
        render(<DatePicker value="2026-01-15" onChange={() => {}} />);
        await user.click(screen.getByText('15.01.2026'));
        expect(screen.getByText(/Januar 2026/)).toBeInTheDocument();
        await user.click(screen.getByTitle('Nächster Monat'));
        expect(screen.getByText(/Februar 2026/)).toBeInTheDocument();
    });

    it('navigiert zum vorherigen Monat', async () => {
        const user = userEvent.setup();
        render(<DatePicker value="2026-03-15" onChange={() => {}} />);
        await user.click(screen.getByText('15.03.2026'));
        expect(screen.getByText(/März 2026/)).toBeInTheDocument();
        await user.click(screen.getByTitle('Vorheriger Monat'));
        expect(screen.getByText(/Februar 2026/)).toBeInTheDocument();
    });

    it('zeigt Standard-Platzhalter wenn keiner angegeben', () => {
        render(<DatePicker value="" onChange={() => {}} />);
        expect(screen.getByText('Datum wählen')).toBeInTheDocument();
    });
});
