import React, { useEffect, useRef } from 'react';
import { Phone, Smartphone } from 'lucide-react';
import { Input, type InputProps } from './ui/input';
import { cn } from '../lib/utils';
import { lookupAreaCode } from '../lib/germanAreaCodes';
import { sanitizePhoneInput } from '../lib/phoneUtils';

export type PhoneInputProps = Omit<InputProps, 'value' | 'onChange' | 'type'> & {
    value: string;
    onChange: (next: string) => void;
    /** Wenn true und das Feld leer ist, wird die deutsche Ortsvorwahl basierend auf PLZ/Ort vorgeschlagen. */
    autoPrefillAreaCode?: boolean;
    plz?: string;
    ort?: string;
    variant?: 'festnetz' | 'mobil';
};

export const PhoneInput: React.FC<PhoneInputProps> = ({
    value,
    onChange,
    autoPrefillAreaCode = false,
    plz,
    ort,
    variant = 'festnetz',
    className,
    placeholder,
    onBlur,
    ...rest
}) => {
    const lastSuggestedRef = useRef<string | null>(null);

    useEffect(() => {
        if (!autoPrefillAreaCode) return;
        if (variant !== 'festnetz') return;
        if (value && value.trim().length > 0) return;
        const suggested = lookupAreaCode(plz || '', ort);
        if (!suggested) return;
        if (lastSuggestedRef.current === suggested) return;
        lastSuggestedRef.current = suggested;
        onChange(`${suggested} `);
    }, [autoPrefillAreaCode, variant, plz, ort, value, onChange]);

    const Icon = variant === 'mobil' ? Smartphone : Phone;
    const defaultPlaceholder = variant === 'mobil' ? '+49 170 ...' : '0511 12 34 56';

    const handleBlur = (e: React.FocusEvent<HTMLInputElement>) => {
        const trimmed = value.trim();
        const suggested = lastSuggestedRef.current;
        if (suggested && trimmed === suggested) {
            onChange('');
        }
        if (onBlur) onBlur(e);
    };

    return (
        <div className="relative">
            <Icon className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
            <Input
                {...rest}
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                value={value}
                onChange={e => onChange(sanitizePhoneInput(e.target.value))}
                onBlur={handleBlur}
                placeholder={placeholder ?? defaultPlaceholder}
                className={cn('pl-9', className)}
            />
        </div>
    );
};

export default PhoneInput;
