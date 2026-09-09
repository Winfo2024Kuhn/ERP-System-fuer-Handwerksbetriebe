import { useState, useRef, useEffect, useCallback } from 'react';
import ReactDOM from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '../../lib/utils';

interface Option {
    value: string;
    label: string;
    /** Optional: Gruppen-Name. Gesetzte Gruppen bekommen im Dropdown eine
     * nicht anklickbare Überschrift über ihren Optionen. Optionen ohne
     * `gruppe` stehen ohne Überschrift ganz oben. */
    gruppe?: string;
}

interface SelectProps {
    options: Option[];
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    className?: string;
    disabled?: boolean;
}

interface DropdownPosition {
    top: number;
    left: number;
    minWidth: number;
    maxWidth: number;
    maxHeight: number;
    nachOben: boolean;
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

export function Select({ options, value, onChange, placeholder = "Bitte wählen...", className, disabled }: SelectProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [position, setPosition] = useState<DropdownPosition>({
        top: 0, left: 0, minWidth: 0, maxWidth: 480, maxHeight: PANEL_MAX_HOEHE, nachOben: false,
    });
    const triggerRef = useRef<HTMLDivElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);

    const selectedOption = options.find(opt => opt.value === value);

    // Berechnet Position und Maße des Panels neu -- beim Öffnen, und danach
    // bei jedem Scroll/Resize, solange das Dropdown offen ist.
    const positioniere = useCallback(() => {
        const trigger = triggerRef.current;
        if (!trigger) return;

        const rect = trigger.getBoundingClientRect();
        const minWidth = rect.width;
        const maxWidth = Math.min(window.innerWidth * 0.9, 480);

        // Tatsaechliche Panel-Hoehe messen (beim zweiten Aufruf -- Scroll/
        // Resize bzw. erneutes Oeffnen -- ist das Panel schon gerendert);
        // vor dem allerersten Rendern nimmt es die Obergrenze an.
        const gemesseneHoehe = dropdownRef.current?.scrollHeight ?? PANEL_MAX_HOEHE;
        const panelHoehe = Math.min(gemesseneHoehe, PANEL_MAX_HOEHE);

        const platzUnten = window.innerHeight - rect.bottom - ABSTAND - RAND;
        const platzOben = rect.top - ABSTAND - RAND;

        let top: number;
        let maxHeight: number;
        let nachOben = false;

        if (platzUnten >= panelHoehe) {
            // Genug Platz unter dem Ausloeser -- Normalfall.
            top = rect.bottom + ABSTAND;
            maxHeight = PANEL_MAX_HOEHE;
        } else if (platzOben >= panelHoehe) {
            // Unten reicht es nicht, oben schon -- hochklappen.
            nachOben = true;
            top = rect.top - panelHoehe - ABSTAND;
            maxHeight = PANEL_MAX_HOEHE;
        } else {
            // Beide Richtungen zu eng -- unten oeffnen und auf den
            // verbleibenden Platz stauchen, damit das Panel nie aus dem
            // Viewport ragt.
            top = rect.bottom + ABSTAND;
            maxHeight = Math.max(platzUnten, 0);
        }

        // Breite fuer die Links/Rechts-Deckelung schaetzen: die zuletzt
        // gemessene Panel-Breite, mindestens aber `minWidth` (das Panel wird
        // per CSS nie schmaler als der Ausloeser).
        const gemesseneBreite = dropdownRef.current?.offsetWidth ?? maxWidth;
        const breite = Math.max(minWidth, Math.min(gemesseneBreite, maxWidth));
        const left = Math.max(RAND, Math.min(rect.left, window.innerWidth - breite - RAND));

        setPosition({ top, left, minWidth, maxWidth, maxHeight, nachOben });
    }, []);

    // Position bei jedem Oeffnen berechnen, und bei offenem Dropdown auf
    // Scroll (capture, damit auch Scroll-Container innerhalb eines Dialogs
    // greifen) und Resize neu berechnen.
    useEffect(() => {
        if (!isOpen) return;
        positioniere();
        window.addEventListener('scroll', positioniere, true);
        window.addEventListener('resize', positioniere);
        return () => {
            window.removeEventListener('scroll', positioniere, true);
            window.removeEventListener('resize', positioniere);
        };
    }, [isOpen, positioniere]);

    // Close dropdown when clicking outside
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            const target = event.target as Node;
            const isOutsideTrigger = triggerRef.current && !triggerRef.current.contains(target);
            const isOutsideDropdown = dropdownRef.current && !dropdownRef.current.contains(target);

            if (isOutsideTrigger && isOutsideDropdown) {
                setIsOpen(false);
            }
        };

        if (isOpen) {
            setTimeout(() => {
                document.addEventListener('mousedown', handleClickOutside);
            }, 0);
        }
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen]);

    const handleSelect = (val: string) => {
        onChange(val);
        setIsOpen(false);
    };

    const handleTriggerKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
        if (disabled) return;
        if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') {
            event.preventDefault();
            setIsOpen(o => !o);
        } else if (event.key === 'Escape' && isOpen) {
            setIsOpen(false);
        }
    };

    const segmente = segmentiereNachGruppe(options);

    const dropdownContent = (
        <div
            ref={dropdownRef}
            role="listbox"
            className="overflow-auto rounded-md border border-slate-200 bg-white p-1 text-slate-950 shadow-2xl"
            style={{
                position: 'fixed',
                top: position.top,
                left: position.left,
                minWidth: position.minWidth,
                maxWidth: position.maxWidth,
                width: 'max-content',
                maxHeight: position.maxHeight,
                zIndex: 99999,
            }}
        >
            {options.length === 0 ? (
                <div className="py-2 px-2 text-sm text-slate-500 text-center">Keine Optionen</div>
            ) : (
                segmente.map((segment, i) => (
                    <div key={segment.gruppe ?? `__ohne-gruppe-${i}`}>
                        {segment.gruppe && (
                            <div
                                role="presentation"
                                className="px-2 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-500"
                            >
                                {segment.gruppe}
                            </div>
                        )}
                        {segment.optionen.map((option) => (
                            <div
                                key={option.value}
                                role="option"
                                aria-selected={value === option.value}
                                title={option.label}
                                className={cn(
                                    "relative flex w-full min-w-0 cursor-pointer select-none items-center rounded-sm py-1.5 pl-2 pr-8 text-sm outline-none hover:bg-rose-50 hover:text-rose-900",
                                    value === option.value && "bg-rose-50 text-rose-900 font-medium"
                                )}
                                onClick={() => handleSelect(option.value)}
                            >
                                <span className="min-w-0 whitespace-normal break-words">{option.label}</span>
                                {value === option.value && (
                                    <span className="absolute right-2 flex h-3.5 w-3.5 items-center justify-center">
                                        <Check className="h-4 w-4" />
                                    </span>
                                )}
                            </div>
                        ))}
                    </div>
                ))
            )}
        </div>
    );

    return (
        <div className={cn("relative w-full", className)}>
            <div
                ref={triggerRef}
                role="combobox"
                aria-haspopup="listbox"
                aria-expanded={isOpen}
                tabIndex={disabled ? -1 : 0}
                className={cn(
                    "flex h-10 w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer hover:bg-slate-50 transition-colors",
                    disabled && "opacity-50 cursor-not-allowed hover:bg-white",
                    isOpen && "ring-2 ring-rose-500 ring-offset-2 border-rose-500"
                )}
                onClick={() => !disabled && setIsOpen(!isOpen)}
                onKeyDown={handleTriggerKeyDown}
            >
                <span className={cn("truncate", !selectedOption && "text-slate-500")}>
                    {selectedOption ? selectedOption.label : placeholder}
                </span>
                <ChevronDown className={cn("h-4 w-4 opacity-50 transition-transform", isOpen && "rotate-180")} />
            </div>

            {isOpen && !disabled && ReactDOM.createPortal(dropdownContent, document.body)}
        </div>
    );
}
