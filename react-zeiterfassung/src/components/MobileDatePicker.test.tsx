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

it('begrenzt Tage und Heute und stellt Fokus nach Auswahl wieder her', async () => {
 const user = userEvent.setup(); const changed = vi.fn()
 render(<MobileDatePicker label="Datum" value="2020-01-15" min="2020-01-10" max="2020-01-20" onChange={changed}/>)
 await user.click(screen.getByRole('button', {name:'Datum'}))
 expect(screen.getByRole('button', {name:'Heute'})).toBeDisabled()
 expect(screen.getByRole('button', {name:'09.01.2020'})).toBeDisabled()
 expect(screen.getByRole('button', {name:'21.01.2020'})).toBeDisabled()
 await user.click(screen.getByRole('button', {name:'10.01.2020'}))
 expect(changed).toHaveBeenCalledExactlyOnceWith('2020-01-10')
 expect(screen.getByRole('button', {name:'Datum'})).toHaveFocus()
})
it.each(['', '2020-01-09', '2020-01-21', '2020-02-31'])('blockiert ungültigen Pflichtwert %s systemeigen', async value => {
 const user = userEvent.setup(); const submit = vi.fn(e=>e.preventDefault())
 render(<form onSubmit={submit}><MobileDatePicker label="Datum" value={value} required min="2020-01-10" max="2020-01-20" onChange={()=>{}}/><button>Speichern</button></form>)
 await user.click(screen.getByText('Speichern'))
 expect(submit).not.toHaveBeenCalled()
 expect(screen.getByRole('alert')).toHaveTextContent(/Datum/)
})
it('schließt per Escape und erhält die Auswahl', async () => {
 const user=userEvent.setup(); const changed=vi.fn()
 render(<MobileDatePicker label="Datum" value="2020-01-15" onChange={changed}/>)
 await user.click(screen.getByRole('button',{name:'Datum'}))
 await user.keyboard('{Escape}')
 expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
 expect(changed).not.toHaveBeenCalled()
 expect(screen.getByRole('button',{name:'Datum'})).toHaveFocus()
})

it('gibt beim Tippen auf den Hintergrund den Fokus zurück', async () => {
    const user = userEvent.setup()
    render(<MobileDatePicker label="Datum" value="2020-01-15" onChange={() => {}} />)
    const trigger = screen.getByRole('button', { name: 'Datum' })
    await user.click(trigger)
    await user.click(screen.getByRole('dialog').parentElement!)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
})
