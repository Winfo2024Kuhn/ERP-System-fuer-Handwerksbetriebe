import { useLayoutEffect, useId, useRef, useState } from 'react';
import ReactDOM from 'react-dom';
import { Palette } from 'lucide-react';
import { Input } from './input';
import { cn } from '../../lib/utils';

export interface ColorInputProps {
    value: string;
    onChange: (value: string) => void;
    'aria-label'?: string;
    className?: string;
    disabled?: boolean;
}
const COLORS = [
    ['Rose', '#e11d48'], ['Schwarz', '#000000'], ['Weiß', '#ffffff'], ['Schiefer', '#475569'],
    ['Rot', '#dc2626'], ['Orange', '#ea580c'], ['Gelb', '#eab308'], ['Grün', '#16a34a'], ['Blau', '#2563eb'], ['Violett', '#9333ea'],
];
const isHex = (value: string) => /^#(?:[\da-f]{3}|[\da-f]{6})$/i.test(value);
export function ColorInput({ value, onChange, 'aria-label': label = 'Farbe', className, disabled }: ColorInputProps) {
    const id = useId();
    const [open, setOpen] = useState(false);
    const [draft, setDraft] = useState(value);
    const [error, setError] = useState('');
    const [position, setPosition] = useState({ top: 0, left: 0, width: 280, maxHeight: 320 });
    const trigger = useRef<HTMLButtonElement>(null);
    const popup = useRef<HTMLDivElement>(null);
    const input = useRef<HTMLInputElement>(null);
    const close = () => { setOpen(false); trigger.current?.focus(); };
    const commit = (color: string) => {
        if (!isHex(color)) { setError('Bitte einen vollständigen Hex-Farbwert eingeben, z. B. #e11d48.'); return; }
        onChange(color); close();
    };
    useLayoutEffect(() => {
        if (!open || disabled) return;
        const update = () => {
            const rect = trigger.current?.getBoundingClientRect(); if (!rect) return;
            const width = Math.min(280, window.innerWidth - 16);
            const below = window.innerHeight - rect.bottom - 12;
            const upward = below < 260 && rect.top > below;
            const maxHeight = Math.max(80, Math.min(320, upward ? rect.top - 12 : below));
            setPosition({ top: upward ? Math.max(8, rect.top - maxHeight - 4) : rect.bottom + 4, left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)), width, maxHeight });
        };
        const outside = (event: MouseEvent) => { const target = event.target as Node; if (!trigger.current?.contains(target) && !popup.current?.contains(target)) setOpen(false); };
        update(); input.current?.focus(); window.addEventListener('resize', update); window.addEventListener('scroll', update, true); document.addEventListener('mousedown', outside);
        return () => { window.removeEventListener('resize', update); window.removeEventListener('scroll', update, true); document.removeEventListener('mousedown', outside); };
    }, [open, disabled]);
    return <div className={className}>
        <button type="button" ref={trigger} disabled={disabled} aria-label={label} aria-haspopup="dialog" aria-expanded={open && !disabled} aria-controls={open ? id : undefined}
            className="flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-50"
            onMouseDown={event => event.preventDefault()}
            onClick={() => { if (open) close(); else { setDraft(value); setError(''); setOpen(true); } }}>
            <span aria-hidden="true" className="h-5 w-5 rounded border border-slate-300" style={{ backgroundColor: isHex(value) ? value : 'transparent' }} />
            <Palette aria-hidden="true" className="h-4 w-4 text-slate-500" />
        </button>
        {open && !disabled && ReactDOM.createPortal(<div ref={popup} id={id} role="dialog" aria-label={`${label} auswählen`}
            className="overflow-auto rounded-lg border border-slate-200 bg-white p-3 shadow-2xl" style={{ position: 'fixed', ...position, zIndex: 99999 }}
            onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); } }}>
            <label htmlFor={`${id}-hex`} className="mb-1 block text-sm font-medium text-slate-700">Hex-Farbwert</label>
            <Input id={`${id}-hex`} ref={input} type="text" value={draft} spellCheck={false} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}
                onChange={event => { setDraft(event.target.value); setError(''); }} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); commit(draft); } }} />
            {error && <p id={`${id}-error`} role="alert" className="mt-1 text-sm text-rose-700">{error}</p>}
            <div className="my-3 grid grid-cols-5 gap-2" aria-label="Farbpalette">
                {COLORS.map(([name, color]) => <button key={color} type="button" aria-label={`${name} (${color})`} aria-pressed={value.toLowerCase() === color}
                    className={cn('h-9 rounded-md border border-slate-300 focus:outline-none focus:ring-2 focus:ring-rose-500', value.toLowerCase() === color && 'ring-2 ring-rose-500 ring-offset-1')}
                    style={{ backgroundColor: color }} onClick={() => commit(color)} />)}
            </div>
            <div className="flex gap-2">
                <button type="button" className="flex-1 rounded border border-slate-200 px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-rose-500" onClick={close}>Abbrechen</button>
                <button type="button" className="flex-1 rounded bg-rose-600 px-3 py-2 text-sm text-white hover:bg-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500" onClick={() => commit(draft)}>Übernehmen</button>
            </div>
        </div>, document.body)}
    </div>;
}
