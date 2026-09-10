import { useState, useRef, useEffect, useLayoutEffect, useId } from 'react';
import ReactDOM from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '../../lib/utils';

interface Option { value: string; label: string; disabled?: boolean }
export interface SelectProps {
    options: Option[];
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    className?: string;
    disabled?: boolean;
    id?: string;
    name?: string;
    required?: boolean;
    error?: string;
    'aria-label'?: string;
    'aria-describedby'?: string;
}

export function Select({ options, value, onChange, placeholder = 'Bitte wählen...', className, disabled, id, name, required, error, 'aria-label': ariaLabel, 'aria-describedby': describedBy }: SelectProps) {
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const listId = `${inputId}-options`;
    const [isOpen, setIsOpen] = useState(false);
    const [activeIndex, setActiveIndex] = useState(-1);
    const [attempted, setAttempted] = useState(false);
    const [position, setPosition] = useState({ top: 0, left: 0, width: 0, maxHeight: 240 });
    const triggerRef = useRef<HTMLButtonElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const validationRef = useRef<HTMLInputElement>(null);
    const selected = options.find(option => option.value === value);
    const validationMessage = required && !value ? `Bitte ${ariaLabel ?? 'eine Auswahl'} auswählen.` : '';
    const shownError = error || (attempted ? validationMessage : '');
    useEffect(() => { validationRef.current?.setCustomValidity(validationMessage); }, [validationMessage]);

    const open = (last = false) => {
        const selectedIndex = options.findIndex(option => option.value === value && !option.disabled);
        const enabled = options.map((option, index) => option.disabled ? -1 : index).filter(index => index >= 0);
        setActiveIndex(selectedIndex >= 0 ? selectedIndex : (last ? enabled.at(-1) : enabled[0]) ?? -1);
        setIsOpen(true);
    };
    const choose = (index: number) => {
        const option = options[index];
        if (!option || option.disabled || disabled) return;
        onChange(option.value);
        setIsOpen(false);
        triggerRef.current?.focus();
    };
    useLayoutEffect(() => {
        if (!isOpen || disabled) return;
        const update = () => {
            const rect = triggerRef.current?.getBoundingClientRect();
            if (!rect) return;
            const below = window.innerHeight - rect.bottom - 12;
            const above = rect.top - 12;
            const upward = below < 160 && above > below;
            const maxHeight = Math.max(48, Math.min(240, upward ? above : below));
            const width = Math.min(rect.width, window.innerWidth - 16);
            setPosition({ left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)), top: upward ? Math.max(8, rect.top - maxHeight - 4) : rect.bottom + 4, width, maxHeight });
        };
        const outside = (event: MouseEvent) => {
            const target = event.target as Node;
            if (!triggerRef.current?.contains(target) && !dropdownRef.current?.contains(target)) setIsOpen(false);
        };
        update();
        window.addEventListener('resize', update);
        window.addEventListener('scroll', update, true);
        document.addEventListener('mousedown', outside);
        return () => { window.removeEventListener('resize', update); window.removeEventListener('scroll', update, true); document.removeEventListener('mousedown', outside); };
    }, [isOpen, disabled]);
    useEffect(() => {
        if (isOpen) document.getElementById(`${listId}-${activeIndex}`)?.scrollIntoView?.({ block: 'nearest' });
    }, [isOpen, activeIndex, listId]);

    return <div className={cn('relative w-full', className)}>
        <button ref={triggerRef} id={inputId} type="button" role="combobox" disabled={disabled}
            aria-label={ariaLabel} aria-expanded={isOpen && !disabled} aria-haspopup="listbox" aria-controls={isOpen ? listId : undefined}
            aria-activedescendant={isOpen && activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
            aria-required={required} aria-invalid={shownError ? true : undefined}
            aria-describedby={[describedBy, shownError ? `${inputId}-error` : undefined].filter(Boolean).join(' ') || undefined}
            className="flex h-10 w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-left text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-50 hover:bg-slate-50"
            onClick={() => isOpen ? setIsOpen(false) : open()}
            onBlur={event => { if (!dropdownRef.current?.contains(event.relatedTarget as Node)) setIsOpen(false); }}
            onKeyDown={event => {
                if (event.key === 'Escape') { if (isOpen) { event.preventDefault(); event.stopPropagation(); setIsOpen(false); } return; }
                if (event.key === 'Tab') { setIsOpen(false); return; }
                if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Home' || event.key === 'End') {
                    event.preventDefault();
                    if (!isOpen) { open(event.key === 'ArrowUp' || event.key === 'End'); return; }
                    const enabled = options.map((option, index) => option.disabled ? -1 : index).filter(index => index >= 0);
                    const current = enabled.indexOf(activeIndex);
                    const next = event.key === 'Home' ? 0 : event.key === 'End' ? enabled.length - 1 : (current + (event.key === 'ArrowDown' ? 1 : -1) + enabled.length) % enabled.length;
                    setActiveIndex(enabled[next] ?? -1);
                } else if ((event.key === 'Enter' || event.key === ' ') && isOpen) { event.preventDefault(); choose(activeIndex); }
            }}>
            <span className={cn('truncate', !selected && 'text-slate-500')}>{selected?.label ?? placeholder}</span>
            <ChevronDown aria-hidden="true" className={cn('h-4 w-4 shrink-0 opacity-50', isOpen && 'rotate-180')} />
        </button>
        <input ref={validationRef} type="text" tabIndex={-1} aria-hidden="true" className="sr-only" name={name} value={value} required={required} disabled={disabled} onChange={() => {}}
            onInvalid={event => { event.preventDefault(); setAttempted(true); triggerRef.current?.focus(); }} />
        {shownError && <p id={`${inputId}-error`} role="alert" className="mt-1 text-sm text-rose-700">{shownError}</p>}
        {isOpen && !disabled && ReactDOM.createPortal(<div ref={dropdownRef} id={listId} role="listbox" aria-label={ariaLabel ?? 'Auswahl'}
            className="overflow-auto rounded-md border border-slate-200 bg-white p-1 text-slate-950 shadow-2xl"
            style={{ position: 'fixed', ...position, zIndex: 99999 }} onMouseDown={event => event.preventDefault()}>
            {options.length === 0 ? <div className="px-2 py-2 text-center text-sm text-slate-500">Keine Optionen</div> : options.map((option, index) =>
                <div key={option.value} id={`${listId}-${index}`} role="option" aria-selected={value === option.value} aria-disabled={option.disabled}
                    className={cn('relative rounded-sm py-2 pl-2 pr-8 text-sm', option.disabled ? 'cursor-not-allowed text-slate-400' : 'cursor-pointer hover:bg-rose-50 hover:text-rose-900', activeIndex === index && !option.disabled && 'bg-rose-50 text-rose-900', value === option.value && 'font-medium')}
                    onMouseMove={() => !option.disabled && setActiveIndex(index)} onClick={() => choose(index)}>
                    {option.label}{value === option.value && <Check aria-hidden="true" className="absolute right-2 top-2 h-4 w-4" />}
                </div>)}
        </div>, document.body)}
    </div>;
}
