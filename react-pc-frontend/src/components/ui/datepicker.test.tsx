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

describe('DatePicker zugängliche Grenzen', () => {
    it('öffnet per Tastatur und fokussiert den gewählten Tag, Escape stellt Fokus wieder her', async () => {
        const user = userEvent.setup();
        render(<DatePicker aria-label="Gültig ab" value="2026-03-10" onChange={vi.fn()} />);
        await user.tab(); await user.keyboard('{Enter}');
        expect(screen.getByRole('button', { name: '10.03.2026' })).toHaveFocus();
        await user.keyboard('{Escape}'); expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Gültig ab' })).toHaveFocus();
    });
    it('sperrt Tage und Heute außerhalb der Grenzen', async () => {
        const user = userEvent.setup();
        render(<DatePicker value="2026-03-10" min="2026-03-10" max="2026-03-12" onChange={vi.fn()} />);
        await user.click(screen.getByText('10.03.2026'));
        expect(screen.getByRole('button', { name: '09.03.2026' })).toBeDisabled();
        expect(screen.getByRole('button', { name: '13.03.2026' })).toBeDisabled();
        expect(screen.getByText('Heute')).toBeDisabled();
    });
});

describe('DatePicker: schnelle Jahres- und Monatswahl', () => {
    it('erreicht 1967 über Jahresraster statt über hunderte Monatsklicks', async () => {
        const handleChange = vi.fn();
        const user = userEvent.setup();
        render(<DatePicker aria-label="Geburtstag" value="2026-03-10" onChange={handleChange} />);
        await user.click(screen.getByText('10.03.2026'));

        // Kopfzeile: Tage -> Monate -> Jahre
        await user.click(screen.getByRole('button', { name: 'Monat und Jahr wählen' }));
        expect(screen.getByRole('button', { name: 'März 2026' })).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Jahr wählen' }));

        // Jahrzehntweise zurück statt Monat für Monat: 2016er-Seite ... 1956er-Seite
        expect(screen.getByText('2016 – 2027')).toBeInTheDocument();
        for (let i = 0; i < 5; i++) await user.click(screen.getByRole('button', { name: 'Frühere Jahre' }));
        expect(screen.getByText('1956 – 1967')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: '1967' }));
        await user.click(screen.getByRole('button', { name: 'September 1967' }));
        await user.click(screen.getByRole('button', { name: '09.09.1967' }));
        expect(handleChange).toHaveBeenCalledWith('1967-09-09');
    });

    it('übernimmt eine getippte deutsche Eingabe direkt', async () => {
        const handleChange = vi.fn();
        const user = userEvent.setup();
        render(<DatePicker aria-label="Geburtstag" value="" onChange={handleChange} />);
        await user.click(screen.getByText('Datum wählen'));
        const eingabe = screen.getByLabelText('Datum eingeben');
        await user.type(eingabe, '9.9.1967');
        // Vollständige Eingabe springt sofort in die Ansicht, übernimmt aber noch nicht.
        expect(screen.getByText(/September 1967/)).toBeInTheDocument();
        expect(handleChange).not.toHaveBeenCalled();
        await user.keyboard('{Enter}');
        expect(handleChange).toHaveBeenCalledWith('1967-09-09');
    });

    it('meldet unvollständige oder gesperrte Eingaben, statt sie stillschweigend zu übernehmen', async () => {
        const handleChange = vi.fn();
        const user = userEvent.setup();
        render(<DatePicker aria-label="Gültig ab" value="" min="2026-01-01" onChange={handleChange} />);
        await user.click(screen.getByText('Datum wählen'));
        const eingabe = screen.getByLabelText('Datum eingeben');
        await user.type(eingabe, '31.02.2026{Enter}');
        expect(screen.getByRole('alert')).toHaveTextContent('TT.MM.JJJJ');
        expect(handleChange).not.toHaveBeenCalled();
        await user.clear(eingabe);
        await user.type(eingabe, '09.09.1967{Enter}');
        expect(screen.getByRole('alert')).toHaveTextContent('außerhalb des erlaubten Zeitraums');
        expect(handleChange).not.toHaveBeenCalled();
    });

    it('springt mit Bild auf/ab ganze Monate und mit Umschalt ganze Jahre', async () => {
        const user = userEvent.setup();
        render(<DatePicker aria-label="Geburtstag" value="2026-03-10" onChange={vi.fn()} />);
        await user.click(screen.getByText('10.03.2026'));
        expect(screen.getByRole('button', { name: '10.03.2026' })).toHaveFocus();
        await user.keyboard('{PageUp}');
        expect(screen.getByText(/Februar 2026/)).toBeInTheDocument();
        await user.keyboard('{Shift>}{PageUp}{/Shift}');
        expect(screen.getByText(/Februar 2025/)).toBeInTheDocument();
    });

    it('sperrt Monate und Jahre ausserhalb der Grenzen', async () => {
        const user = userEvent.setup();
        render(<DatePicker aria-label="Gültig ab" value="2026-03-10" min="2026-03-01" max="2026-05-31" onChange={vi.fn()} />);
        await user.click(screen.getByText('10.03.2026'));
        await user.click(screen.getByRole('button', { name: 'Monat und Jahr wählen' }));
        expect(screen.getByRole('button', { name: 'Februar 2026' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'April 2026' })).toBeEnabled();
        expect(screen.getByRole('button', { name: 'Juni 2026' })).toBeDisabled();
        await user.click(screen.getByRole('button', { name: 'Jahr wählen' }));
        expect(screen.getByRole('button', { name: '2025' })).toBeDisabled();
        expect(screen.getByRole('button', { name: '2026' })).toBeEnabled();
    });
});
