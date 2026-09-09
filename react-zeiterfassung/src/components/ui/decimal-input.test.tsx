import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect } from 'vitest';
import { DecimalInput } from './decimal-input';
import { validateDecimalInput } from '../../lib/numberInput';
function Example({ initial = '0' }) {
    const [value, setValue] = useState(initial);
    const [error, setError] = useState('');
    return <><DecimalInput label="Stunden" value={value} onChange={setValue} required error={error} />
        <button onClick={() => { const result = validateDecimalInput(value, { label: 'Stunden', required: true }); setError(result.valid ? '' : result.message); }}>Übernehmen</button></>;
}
describe('DecimalInput', () => {
    it.each(['0', '0,00'])('leert %s bei Klick und erhält unvollständige Entwürfe', async initial => {
        const user = userEvent.setup(); render(<Example initial={initial} />);
        const field = screen.getByRole('textbox', { name: 'Stunden' });
        await user.click(field); expect(field).toHaveValue('');
        await user.type(field, '12,'); expect(field).toHaveValue('12,');
        await user.click(screen.getByText('Übernehmen')); expect(screen.getByRole('alert')).toBeVisible();
        expect(field).toHaveValue('12,');
        await user.click(field); await user.type(field, '5'); expect(field).toHaveValue('12,5');
        await user.click(screen.getByText('Übernehmen')); expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
    it('leert bei Tab, erhält Nichtnullwerte und blockiert leere Pflichtwerte', async () => {
        const user = userEvent.setup(); const { unmount } = render(<Example />);
        await user.tab(); expect(screen.getByRole('textbox')).toHaveValue('');
        await user.click(screen.getByText('Übernehmen')); expect(screen.getByRole('alert')).toHaveTextContent('Stunden');
        unmount(); render(<Example initial="7,7" />); await user.tab(); expect(screen.getByRole('textbox')).toHaveValue('7,7');
    });
});

it('blockiert ungültige Formulare auch ohne expliziten Buttonhandler und erhält readonly 0', async () => {
    const user = userEvent.setup(); let submitted = false;
    const { unmount } = render(<form onSubmit={event => { event.preventDefault(); submitted = true; }}><DecimalInput label="Stunden" value="25" max={24} onChange={() => {}} /><button>Speichern</button></form>);
    await user.click(screen.getByText('Speichern'));
    expect(submitted).toBe(false); expect(screen.getByRole('alert')).toHaveTextContent('höchstens 24');
    unmount(); render(<DecimalInput label="Stunden" value="0" readOnly onChange={() => { throw new Error('readonly darf nicht ändern'); }} />);
    await user.click(screen.getByRole('textbox')); expect(screen.getByRole('textbox')).toHaveValue('0');
});
