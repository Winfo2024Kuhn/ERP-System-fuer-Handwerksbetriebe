import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import { ArbeitszeitFelder } from './ArbeitszeitFelder';
import { BUCHUNGSZEIT_LABELS, ZEITFENSTER_LABELS } from './arbeitszeitInput';
const draft = { montagStunden: '8', dienstagStunden: '8', mittwochStunden: '8', donnerstagStunden: '8', freitagStunden: '8', samstagStunden: '0', sonntagStunden: '0,00', buchungStartZeit: '', buchungEndeZeit: '' };
it.each([false, true])('bewahrt Textentwürfe und leert Nullstunden per Klick und Tab (kompakt=%s)', async kompakt => {
 const user = userEvent.setup();
 function Form() { const [value, onChange] = useState(draft); return <ArbeitszeitFelder value={value} onChange={onChange} kompakt={kompakt} zeitfenster={kompakt ? BUCHUNGSZEIT_LABELS : ZEITFENSTER_LABELS} optionalHinweis={kompakt} />; }
 render(<Form />);
 const monday = screen.getByRole('textbox', { name: 'Montag Stunden' }); await user.click(monday); expect(monday).toHaveValue('8');
 const saturday = screen.getByRole('textbox', { name: 'Samstag Stunden' }); expect(saturday).toHaveValue('0'); await user.click(saturday); expect(saturday).toHaveValue(''); await user.type(saturday, '7,5'); expect(saturday).toHaveValue('7,5');
 await user.tab(); const sunday = screen.getByRole('textbox', { name: 'Sonntag Stunden' }); expect(sunday).toHaveFocus(); expect(sunday).toHaveValue(''); await user.type(sunday, '0'); expect(sunday).toHaveValue('0');
 const start = screen.getByRole('textbox', { name: kompakt ? 'Früheste Buchung – optional' : 'Frühester Start' }); await user.type(start, '09:05'); expect(start).toHaveValue('09:05');
});
