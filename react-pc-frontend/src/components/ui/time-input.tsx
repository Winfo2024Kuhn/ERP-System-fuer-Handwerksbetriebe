/* eslint-disable react-refresh/only-export-components */
import { useEffect, useId, useRef, useState } from 'react';
import type { InputHTMLAttributes } from 'react';
import { Input } from './input';
import type { InputValidation } from '../../lib/numberInput';

export function validateTimeInput(draft: string, { label, required }: { label: string; required?: boolean }): InputValidation<string> {
    if (!draft.trim()) return required ? { valid: false, message: `Bitte ${label} eingeben (HH:mm).` } : { valid: true, value: null };
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(draft)) return { valid: false, message: `Bitte ${label} als gültige Uhrzeit von 00:00 bis 23:59 eingeben (HH:mm).` };
    return { valid: true, value: draft };
}
export interface TimeInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'> {
    value: string;
    onChange: (draft: string) => void;
    label?: string;
    error?: string;
}
export function TimeInput({ value, onChange, label, id, error, required, onInvalid, ...props }: TimeInputProps) {
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const ref = useRef<HTMLInputElement>(null);
    const [attempted, setAttempted] = useState(false);
    const result = validateTimeInput(value, { label: label ?? props['aria-label'] ?? 'Uhrzeit', required });
    const validationMessage = result.valid ? '' : result.message;
    const shownError = error || (attempted ? validationMessage : '');
    useEffect(() => { ref.current?.setCustomValidity(validationMessage); }, [validationMessage]);
    return <div>
        {label && <label htmlFor={inputId} className="mb-1 block text-sm font-medium text-slate-700">{label}</label>}
        <Input {...props} ref={ref} id={inputId} type="text" value={value} required={required} placeholder={props.placeholder ?? 'HH:mm'}
            aria-invalid={shownError ? true : props['aria-invalid']}
            aria-describedby={[props['aria-describedby'], `${inputId}-hint`, shownError ? `${inputId}-error` : undefined].filter(Boolean).join(' ')}
            onChange={event => onChange(event.target.value)}
            onInvalid={event => { event.preventDefault(); setAttempted(true); onInvalid?.(event); }} />
        <p id={`${inputId}-hint`} className="mt-1 text-xs text-slate-500">HH:mm, z. B. 09:05</p>
        {shownError && <p id={`${inputId}-error`} role="alert" className="mt-1 text-sm text-rose-700">{shownError}</p>}
    </div>;
}
