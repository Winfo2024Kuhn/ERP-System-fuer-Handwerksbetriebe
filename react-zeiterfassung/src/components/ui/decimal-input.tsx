import { useEffect, useId, useRef, useState } from 'react';
import type { InputHTMLAttributes } from 'react';
import { validateDecimalInput } from '../../lib/numberInput';

export interface DecimalInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type' | 'min' | 'max' | 'step'> {
    value: string;
    onChange: (draft: string) => void;
    label?: string;
    min?: number;
    max?: number;
    integer?: boolean;
    error?: string;
}

export function DecimalInput({ value, onChange, label, id, min, max, integer, error, required, onFocus, onInvalid, ...props }: DecimalInputProps) {
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const ref = useRef<HTMLInputElement>(null);
    const [attempted, setAttempted] = useState(false);
    const result = validateDecimalInput(value, { label: label ?? props['aria-label'] ?? 'Wert', min, max, integer, required });
    const validationMessage = result.valid ? '' : result.message;
    const shownError = error || (attempted ? validationMessage : '');
    useEffect(() => { ref.current?.setCustomValidity(validationMessage); }, [validationMessage]);
    return <div>
        {label && <label htmlFor={inputId} className="mb-1 block text-sm font-medium text-slate-700">{label}</label>}
        <input {...props} className={'min-h-12 w-full rounded-lg border border-slate-200 bg-white px-3 text-base text-slate-900 outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-50 ' + (props.className ?? '')} ref={ref} id={inputId} type="text" inputMode={integer ? 'numeric' : 'decimal'} value={value} required={required}
            aria-invalid={shownError ? true : props['aria-invalid']}
            aria-describedby={[props['aria-describedby'], shownError ? `${inputId}-error` : undefined].filter(Boolean).join(' ') || undefined}
            onChange={event => onChange(event.target.value)}
            onFocus={event => {
                if (!props.readOnly && !props.disabled && /^[+-]?0+(?:,0+)?$/.test(value.trim())) onChange('');
                onFocus?.(event);
            }}
            onInvalid={event => { event.preventDefault(); setAttempted(true); onInvalid?.(event); }} />
        {shownError && <p id={`${inputId}-error`} role="alert" className="mt-1 text-sm text-rose-700">{shownError}</p>}
    </div>;
}
