import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { useState } from 'react';
import { KostenstellenSplitsEditor, type KostenstellenSplit } from './KostenstellenSplitsEditor';
import { validateKostenstellenSplits } from '../../features/finanzen/kostenstellenDrafts';
import { ToastProvider } from '../ui/toast';
const split: KostenstellenSplit = { kostenstelleId: 1, prozent: 0, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: 2026 };
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it('hält leere Prozententwürfe und validiert vor Übernahme', () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => [] })));
    let current = [split];
    function Form() { const [values, setValues] = useState([split]); return <KostenstellenSplitsEditor splits={values} onChange={next => { current = next; setValues(next); }} defaultStartJahr={2026}/>; }
    render(<ToastProvider><Form /></ToastProvider>);
    const input = screen.getByRole('textbox', { name: 'Anteil Split 1 (%)' });
    expect(input).toHaveValue('0');
    fireEvent.focus(input);
    expect(input).toHaveValue('');
    expect(validateKostenstellenSplits(current).valid).toBe(false);
    fireEvent.change(input, { target: { value: '12' } });
    expect(validateKostenstellenSplits(current)).toMatchObject({ valid: true, splits: [{ prozent: 12 }] });
    fireEvent.change(input, { target: { value: '101' } });
    expect(validateKostenstellenSplits(current).valid).toBe(false);
});
it('weist ungültige Streckung zurück und erhält absolute negative Werte', () => {
    expect(validateKostenstellenSplits([{ ...split, prozent: null, absoluterBetrag: -12.5 }])).toMatchObject({ valid: true, splits: [{ absoluterBetrag: -12.5 }] });
    expect(validateKostenstellenSplits([{ ...split, _drafts: { prozent: '12,5' } }]).valid).toBe(false);
    for (const value of ['', '1,5', '21'])
        expect(validateKostenstellenSplits([{ ...split, _drafts: { streckungJahre: value } }]).valid).toBe(false);
});
