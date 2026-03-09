import { useState, useRef, useEffect } from 'react';
import ReactDOM from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '../../lib/utils';

interface Option {
    value: string;
    label: string;
}

interface SelectProps {
    options: Option[];
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    className?: string;
    disabled?: boolean;
}

export function Select({ options, value, onChange, placeholder = "Bitte wählen...", className, disabled }: SelectProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0, width: 0 });
    const triggerRef = useRef<HTMLDivElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);

    const selectedOption = options.find(opt => opt.value === value);

    // Position dropdown when opening
    useEffect(() => {
        if (isOpen && triggerRef.current) {
            const rect = triggerRef.current.getBoundingClientRect();
            setDropdownPosition({
                top: rect.bottom + 4,
                left: rect.left,
                width: rect.width
            });
        }
    }, [isOpen]);

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

    const dropdownContent = (
        <div
            ref={dropdownRef}
            className="max-h-60 overflow-auto rounded-md border border-slate-200 bg-white p-1 text-slate-950 shadow-2xl"
            style={{
                position: 'fixed',
                top: dropdownPosition.top,
                left: dropdownPosition.left,
                width: dropdownPosition.width,
                zIndex: 99999,
            }}
        >
            {options.length === 0 ? (
                <div className="py-2 px-2 text-sm text-slate-500 text-center">Keine Optionen</div>
            ) : (
                options.map((option) => (
                    <div
                        key={option.value}
                        className={cn(
                            "relative flex w-full cursor-pointer select-none items-center rounded-sm py-1.5 pl-2 pr-8 text-sm outline-none hover:bg-rose-50 hover:text-rose-900",
                            value === option.value && "bg-rose-50 text-rose-900 font-medium"
                        )}
                        onClick={() => handleSelect(option.value)}
                    >
                        <span className="truncate">{option.label}</span>
                        {value === option.value && (
                            <span className="absolute right-2 flex h-3.5 w-3.5 items-center justify-center">
                                <Check className="h-4 w-4" />
                            </span>
                        )}
                    </div>
                ))
            )}
        </div>
    );

    return (
        <div className={cn("relative w-full", className)}>
            <div
                ref={triggerRef}
                className={cn(
                    "flex h-10 w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer hover:bg-slate-50 transition-colors",
                    disabled && "opacity-50 cursor-not-allowed hover:bg-white",
                    isOpen && "ring-2 ring-rose-500 ring-offset-2 border-rose-500"
                )}
                onClick={() => !disabled && setIsOpen(!isOpen)}
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
