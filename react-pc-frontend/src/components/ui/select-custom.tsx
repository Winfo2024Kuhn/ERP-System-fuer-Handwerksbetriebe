import { useState, useRef, useEffect, useLayoutEffect, useId, Fragment } from 'react';
import ReactDOM from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '../../lib/utils';

interface Option {
    value: string;
    label: string;
    disabled?: boolean;
    /** Optional: Gruppen-Name. Gesetzte Gruppen bekommen im Dropdown eine
     * nicht anklickbare Überschrift über ihren Optionen. Optionen ohne
     * `gruppe` stehen ohne Überschrift ganz oben. */
    gruppe?: string;
}

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

interface DropdownPosition {
    top: number;
    left: number;
    minWidth: number;
    maxWidth: number;
    maxHeight: number;
}

const PANEL_MAX_HOEHE = 240; // entspricht Tailwind max-h-60
const ABSTAND = 4; // Abstand Panel <-> Ausloeser
const RAND = 8; // Sicherheitsabstand zum Viewport-Rand

interface Segment {
    gruppe: string | null;
    optionen: Option[];
}

/**
 * Segmentiert Optionen nach `gruppe`. Optionen ohne Gruppe stehen ohne
 * Überschrift immer ganz oben; die gesetzten Gruppen folgen danach in der
 * Reihenfolge ihres ersten Auftretens in `options`.
 */
function segmentiereNachGruppe(options: Option[]): Segment[] {
    const ohneGruppe = options.filter(o => !o.gruppe);
    const gruppenIndex = new Map<string, number>();
    const gruppen: Segment[] = [];
    for (const option of options) {
        if (!option.gruppe) continue;
        let index = gruppenIndex.get(option.gruppe);
        if (index === undefined) {
            index = gruppen.length;
            gruppenIndex.set(option.gruppe, index);
            gruppen.push({ gruppe: option.gruppe, optionen: [] });
        }
        gruppen[index].optionen.push(option);
    }
    const segmente: Segment[] = [];
    if (ohneGruppe.length > 0) segmente.push({ gruppe: null, optionen: ohneGruppe });
    segmente.push(...gruppen);
    return segmente;
}

export function Select({ options, value, onChange, placeholder = 'Bitte wählen...', className, disabled, id, name, required, error, 'aria-label': ariaLabel, 'aria-describedby': describedBy }: SelectProps) {
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const listId = `${inputId}-options`;
    const [isOpen, setIsOpen] = useState(false);
    const [activeIndex, setActiveIndex] = useState(-1);
    const [attempted, setAttempted] = useState(false);
    const [position, setPosition] = useState<DropdownPosition>({ top: 0, left: 0, minWidth: 0, maxWidth: 480, maxHeight: PANEL_MAX_HOEHE });
    // Tastatur und Maus folgen derselben sichtbaren Gruppenreihenfolge.
    const segments = segmentiereNachGruppe(options);
    const orderedOptions = segments.flatMap(segment => segment.optionen);
    const optionIndices = new Map(orderedOptions.map((option, index) => [option, index]));
    const triggerRef = useRef<HTMLButtonElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const validationRef = useRef<HTMLInputElement>(null);
    const selected = options.find(option => option.value === value);
    const validationMessage = required && !value ? `Bitte ${ariaLabel ?? 'eine Auswahl'} auswählen.` : '';
    const shownError = error || (attempted ? validationMessage : '');
    useEffect(() => { validationRef.current?.setCustomValidity(validationMessage); }, [validationMessage]);

    const open = (last = false) => {
        const selectedIndex = orderedOptions.findIndex(option => option.value === value && !option.disabled);
        const enabled = orderedOptions.map((option, index) => option.disabled ? -1 : index).filter(index => index >= 0);
        setActiveIndex(selectedIndex >= 0 ? selectedIndex : (last ? enabled.at(-1) : enabled[0]) ?? -1);
        setIsOpen(true);
    };
    const choose = (index: number) => {
        const option = orderedOptions[index];
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
            const availableWidth = Math.max(0, window.innerWidth - RAND * 2);
            const minWidth = Math.min(rect.width, availableWidth);
            const maxWidth = Math.min(availableWidth, Math.max(minWidth, Math.min(window.innerWidth * 0.9, 480)));
            const panel = dropdownRef.current;
            // Erst die Breite setzen: Umgebrochene Labels bestimmen die echte Höhe.
            if (panel) {
                panel.style.minWidth = `${minWidth}px`;
                panel.style.maxWidth = `${maxWidth}px`;
            }
            const panelHeight = Math.min((panel?.scrollHeight ?? PANEL_MAX_HOEHE) + 2, PANEL_MAX_HOEHE);
            const below = Math.max(0, window.innerHeight - rect.bottom - ABSTAND - RAND);
            const above = Math.max(0, rect.top - ABSTAND - RAND);
            const upward = below < panelHeight && above > below;
            const maxHeight = Math.min(PANEL_MAX_HOEHE, upward ? above : below);
            const width = Math.max(minWidth, Math.min(panel?.offsetWidth ?? maxWidth, maxWidth));
            const top = upward ? rect.top - Math.min(panelHeight, maxHeight) - ABSTAND : rect.bottom + ABSTAND;
            setPosition({
                left: Math.max(RAND, Math.min(rect.left, window.innerWidth - width - RAND)),
                top: Math.max(RAND, Math.min(top, window.innerHeight - Math.min(panelHeight, maxHeight) - RAND)),
                minWidth, maxWidth, maxHeight,
            });
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
    }, [isOpen, disabled, options]);
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
                    const enabled = orderedOptions.map((option, index) => option.disabled ? -1 : index).filter(index => index >= 0);
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
            style={{ position: 'fixed', ...position, width: 'max-content', zIndex: 99999 }} onMouseDown={event => event.preventDefault()}>
            {options.length === 0 ? <div className="px-2 py-2 text-center text-sm text-slate-500">Keine Optionen</div> : segments.map(segment => {
                const items = segment.optionen.map(option => {
                        const index = optionIndices.get(option)!;
                        return <div key={option.value} id={`${listId}-${index}`} role="option" aria-selected={value === option.value} aria-disabled={option.disabled} title={option.label}
                            className={cn('relative flex w-full min-w-0 select-none items-center rounded-sm py-2 pl-2 pr-8 text-sm', option.disabled ? 'cursor-not-allowed text-slate-400' : 'cursor-pointer hover:bg-rose-50 hover:text-rose-900', activeIndex === index && !option.disabled && 'bg-rose-50 text-rose-900', value === option.value && 'font-medium')}
                            onMouseMove={() => !option.disabled && setActiveIndex(index)} onClick={() => choose(index)}>
                            <span className="min-w-0 whitespace-normal break-words">{option.label}</span>{value === option.value && <Check aria-hidden="true" className="absolute right-2 top-2 h-4 w-4" />}
                        </div>;
                    });
                return segment.gruppe ? (
                    <div key={`gruppe:${segment.gruppe}`} role="group" aria-label={segment.gruppe}>
                        <div aria-hidden="true" className="px-2 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                            {segment.gruppe}
                        </div>
                        {items}
                    </div>
                ) : <Fragment key="ohne-gruppe">{items}</Fragment>;
            })}
        </div>, document.body)}
    </div>;
}
