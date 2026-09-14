/* eslint-disable react-refresh/only-export-components */
import { useEffect, useId, useRef, useState } from 'react';
import type { InputHTMLAttributes } from 'react';
import { Input } from './input';
import type { InputValidation } from '../../lib/numberInput';

/**
 * Parst und normalisiert fehlertolerante Zeiteingaben in das Format HH:mm.
 * Beispiele:
 * - "11", "11.", "11,", "11:" -> "11:00"
 * - "8" -> "08:00"
 * - "11:30", " 11 : 30 ", "11 30" -> "11:30"
 * - "11.30", "11,30" -> "11:30"
 * - "8,5", "8.5" -> "08:30"
 * - "830" -> "08:30"
 * - "1130" -> "11:30"
 */
export function normalizeForgivingTime(input: string): string | null {
    if (!input) return null;
    const clean = input.trim().replace(/\s+/g, '');
    if (!clean) return null;

    // Standard HH:mm
    if (/^([01]\d|2[0-3]):[0-5]\d$/.test(clean)) {
        return clean;
    }

    // 1-2 Ziffern mit optionalem Trennzeichen am Ende (z. B. "11", "11.", "11,", "11:", "8")
    const hourOnlyMatch = /^(\d{1,2})[.,:]?$/.exec(clean);
    if (hourOnlyMatch) {
        const h = parseInt(hourOnlyMatch[1], 10);
        if (h >= 0 && h <= 23) {
            return `${String(h).padStart(2, '0')}:00`;
        }
        return null;
    }

    // H:M mit Doppelpunkt (z. B. "8:30", "9:5", "8:3")
    const colonMatch = /^(\d{1,2}):(\d{1,2})$/.exec(clean);
    if (colonMatch) {
        const h = parseInt(colonMatch[1], 10);
        const minRaw = colonMatch[2];
        if (h >= 0 && h <= 23) {
            const m = minRaw.length === 1 ? parseInt(minRaw, 10) * 10 : parseInt(minRaw, 10);
            if (m >= 0 && m <= 59) {
                return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
            }
        }
        return null;
    }

    // Punkt oder Komma (z. B. "11.30", "11,30", "8,5", "8.5")
    const sepMatch = /^(\d{1,2})[.,](\d{1,2})$/.exec(clean);
    if (sepMatch) {
        const h = parseInt(sepMatch[1], 10);
        const frac = sepMatch[2];
        if (h >= 0 && h <= 23) {
            let m: number;
            if (frac === '5') {
                m = 30; // Dezimale halbe Stunde
            } else if (frac === '25') {
                m = 15;
            } else if (frac === '75') {
                m = 45;
            } else if (frac.length === 1) {
                m = parseInt(frac, 10) * 10;
            } else {
                m = parseInt(frac, 10);
            }
            if (m >= 0 && m <= 59) {
                return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
            }
        }
        return null;
    }

    // 3 oder 4 Ziffern ohne Trennzeichen (z. B. "830" -> 08:30, "1130" -> 11:30)
    if (/^\d{3,4}$/.test(clean)) {
        const hStr = clean.length === 3 ? clean.slice(0, 1) : clean.slice(0, 2);
        const mStr = clean.length === 3 ? clean.slice(1) : clean.slice(2);
        const h = parseInt(hStr, 10);
        const m = parseInt(mStr, 10);
        if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
            return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
        }
    }

    return null;
}

export function validateTimeInput(draft: string, { label, required, forgiving = false }: { label: string; required?: boolean; forgiving?: boolean }): InputValidation<string> {
    if (!draft || !draft.trim()) return required ? { valid: false, message: `Bitte ${label} eingeben (HH:mm).` } : { valid: true, value: null };
    const normalized = normalizeForgivingTime(draft);
    if (!normalized) {
        return { valid: false, message: `Bitte ${label} als gültige Uhrzeit von 00:00 bis 23:59 eingeben (HH:mm).` };
    }
    if (!forgiving && /^([01]\d|2[0-3]):[0-5]\d$/.test(normalized)) {
        return { valid: true, value: normalized };
    }
    return forgiving ? { valid: true, value: normalized } : { valid: true, value: normalized };
}

export interface TimeInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'> {
    value: string;
    onChange: (draft: string) => void;
    label?: string;
    error?: string;
}

export function TimeInput({ value, onChange, label, id, error, required, onInvalid, onBlur, onKeyDown, ...props }: TimeInputProps) {
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const ref = useRef<HTMLInputElement>(null);
    const [attempted, setAttempted] = useState(false);
    const result = validateTimeInput(value, { label: label ?? props['aria-label'] ?? 'Uhrzeit', required, forgiving: true });
    const validationMessage = result.valid ? '' : result.message;
    const shownError = error || (attempted ? validationMessage : '');
    useEffect(() => { ref.current?.setCustomValidity(validationMessage); }, [validationMessage]);

    const handleBlur = (event: React.FocusEvent<HTMLInputElement>) => {
        const normalized = normalizeForgivingTime(event.target.value);
        if (normalized && normalized !== event.target.value) {
            onChange(normalized);
        }
        onBlur?.(event);
    };

    const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'Enter') {
            const normalized = normalizeForgivingTime(value);
            if (normalized && normalized !== value) {
                onChange(normalized);
            }
        }
        onKeyDown?.(event);
    };

    return <div>
        {label && <label htmlFor={inputId} className="mb-1 block text-sm font-medium text-slate-700">{label}</label>}
        <Input {...props} ref={ref} id={inputId} type="text" value={value} required={required} placeholder={props.placeholder ?? 'HH:mm'}
            aria-invalid={shownError ? true : props['aria-invalid']}
            aria-describedby={[props['aria-describedby'], `${inputId}-hint`, shownError ? `${inputId}-error` : undefined].filter(Boolean).join(' ')}
            onChange={event => onChange(event.target.value)}
            onBlur={handleBlur}
            onKeyDown={handleKeyDown}
            onInvalid={event => { event.preventDefault(); setAttempted(true); onInvalid?.(event); }} />
        <p id={`${inputId}-hint`} className="mt-1 text-xs text-slate-500">HH:mm, z. B. 11, 11:30 oder 09:05</p>
        {shownError && <p id={`${inputId}-error`} role="alert" className="mt-1 text-sm text-rose-700">{shownError}</p>}
    </div>;
}
