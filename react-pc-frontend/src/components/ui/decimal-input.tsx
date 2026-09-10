import { useEffect, useId, useRef, useState } from 'react';
import type { InputHTMLAttributes } from 'react';
import { Input } from './input';
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

/** Ein Entwurf, der nur aus Nullen besteht: `0`, `-0`, `0,00`. */
const istNullEntwurf = (entwurf: string) => /^[+-]?0+(?:,0+)?$/.test(entwurf.trim());

export function DecimalInput({ value, onChange, label, id, min, max, integer, error, required, onFocus, onInvalid, ...props }: DecimalInputProps) {
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const ref = useRef<HTMLInputElement>(null);
    const [attempted, setAttempted] = useState(false);
    const eigeneEingabe = useRef(false);
    // Die 0 muss auch dann verschwinden, wenn sie erst NACH dem Fokus ankommt:
    // Dialoge setzen den Fokus beim Oeffnen, der gespeicherte Wert wird aber
    // erst danach nachgeladen — ein focus-Event kommt dann nie mehr. Nur
    // fremde Werte leeren; tippt jemand selbst eine 0 (Anfang von "0,5"),
    // bleibt sie stehen.
    useEffect(() => {
        const eigen = eigeneEingabe.current;
        eigeneEingabe.current = false;
        if (eigen || props.readOnly || props.disabled) return;
        if (document.activeElement === ref.current && istNullEntwurf(value)) onChange('');
    }, [value, onChange, props.readOnly, props.disabled]);
    const result = validateDecimalInput(value, { label: label ?? props['aria-label'] ?? 'Wert', min, max, integer, required });
    const validationMessage = result.valid ? '' : result.message;
    const shownError = error || (attempted ? validationMessage : '');
    useEffect(() => { ref.current?.setCustomValidity(validationMessage); }, [validationMessage]);
    return <div>
        {label && <label htmlFor={inputId} className="mb-1 block text-sm font-medium text-slate-700">{label}</label>}
        <Input {...props} ref={ref} id={inputId} type="text" inputMode={integer ? 'numeric' : 'decimal'} value={value} required={required}
            aria-invalid={shownError ? true : props['aria-invalid']}
            aria-describedby={[props['aria-describedby'], shownError ? `${inputId}-error` : undefined].filter(Boolean).join(' ') || undefined}
            onChange={event => { eigeneEingabe.current = true; onChange(event.target.value); }}
            onFocus={event => {
                if (!props.readOnly && !props.disabled && istNullEntwurf(value)) onChange('');
                onFocus?.(event);
            }}
            onInvalid={event => { event.preventDefault(); setAttempted(true); onInvalid?.(event); }} />
        {shownError && <p id={`${inputId}-error`} role="alert" className="mt-1 text-sm text-rose-700">{shownError}</p>}
    </div>;
}
