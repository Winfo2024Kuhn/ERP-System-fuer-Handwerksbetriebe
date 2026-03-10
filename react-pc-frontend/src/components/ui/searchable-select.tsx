import { useState, useRef, useEffect, useCallback } from 'react';
import { cn } from '../../lib/utils';
import { Check, ChevronsUpDown, Search, X, Loader2 } from 'lucide-react';

export interface SearchableSelectOption {
    value: string;
    label: string;
    sublabel?: string;
}

export interface SearchableSelectProps {
    value: string;
    onChange: (value: string) => void;
    options?: SearchableSelectOption[];
    /** Async search callback – when provided, options are fetched from backend instead of local filtering */
    onSearch?: (term: string) => Promise<SearchableSelectOption[]>;
    /** Label to show for the currently selected value when options haven't been loaded yet */
    selectedLabel?: string;
    placeholder?: string;
    searchPlaceholder?: string;
    className?: string;
    icon?: React.ReactNode;
}

/**
 * A searchable dropdown select component.
 * Supports both local filtering (via options) and async backend search (via onSearch).
 */
export function SearchableSelect({
    value,
    onChange,
    options = [],
    onSearch,
    selectedLabel,
    placeholder = 'Auswählen...',
    searchPlaceholder = 'Suchen...',
    className,
    icon
}: SearchableSelectProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');
    const [filteredOptions, setFilteredOptions] = useState<SearchableSelectOption[]>(options);
    const [asyncLoading, setAsyncLoading] = useState(false);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Get selected option label
    const selectedOption = options.find(o => o.value === value)
        || filteredOptions.find(o => o.value === value);
    const displayLabel = selectedOption?.label || selectedLabel;

    // Async search handler (debounced)
    const handleAsyncSearch = useCallback((term: string) => {
        if (!onSearch) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(async () => {
            setAsyncLoading(true);
            try {
                const results = await onSearch(term);
                setFilteredOptions(results);
            } catch {
                setFilteredOptions([]);
            } finally {
                setAsyncLoading(false);
            }
        }, 250);
    }, [onSearch]);

    // Load initial results when dropdown opens in async mode
    useEffect(() => {
        if (isOpen && onSearch && filteredOptions.length === 0 && !searchTerm) {
            handleAsyncSearch('');
        }
    }, [isOpen, onSearch]);

    // Filter options based on search term (local mode)
    useEffect(() => {
        if (onSearch) return; // Skip local filtering in async mode
        if (!searchTerm) {
            setFilteredOptions(options);
            return;
        }
        const lower = searchTerm.toLowerCase();
        const filtered = options.filter(o =>
            o.label.toLowerCase().includes(lower) ||
            (o.sublabel && o.sublabel.toLowerCase().includes(lower))
        );
        setFilteredOptions(filtered);
    }, [searchTerm, options, onSearch]);

    // Handle click outside to close
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
                setIsOpen(false);
                setSearchTerm('');
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // Focus input when opening
    useEffect(() => {
        if (isOpen && inputRef.current) {
            setTimeout(() => inputRef.current?.focus(), 50);
        }
    }, [isOpen]);

    const handleSelect = (optionValue: string) => {
        onChange(optionValue);
        setIsOpen(false);
        setSearchTerm('');
    };

    const handleClear = (e: React.MouseEvent) => {
        e.stopPropagation();
        onChange('');
        setSearchTerm('');
    };

    return (
        <div className={cn("relative", className)} ref={wrapperRef}>
            {/* Trigger Button */}
            <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                className={cn(
                    "w-full flex items-center justify-between gap-2 px-3 py-2 text-left",
                    "bg-white border border-slate-300 rounded-md",
                    "hover:border-slate-400 transition-colors",
                    "focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500",
                    isOpen && "ring-2 ring-rose-200 border-rose-300"
                )}
            >
                <div className="flex items-center gap-2 flex-1 min-w-0">
                    {icon && <span className="text-slate-400 shrink-0">{icon}</span>}
                    <span className={cn(
                        "truncate text-sm",
                        displayLabel ? "text-slate-900" : "text-slate-400"
                    )}>
                        {displayLabel || placeholder}
                    </span>
                </div>
                <div className="flex items-center gap-1 shrink-0">
                    {value && (
                        <span
                            onClick={handleClear}
                            className="p-0.5 hover:bg-slate-100 rounded transition-colors"
                        >
                            <X className="w-3 h-3 text-slate-400 hover:text-slate-600" />
                        </span>
                    )}
                    <ChevronsUpDown className="w-4 h-4 text-slate-400" />
                </div>
            </button>

            {/* Dropdown */}
            {isOpen && (
                <div className="absolute z-[60] w-full mt-1 bg-white border border-slate-200 rounded-lg shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-100">
                    {/* Search Input */}
                    <div className="p-2 border-b border-slate-100">
                        <div className="relative">
                            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                            <input
                                ref={inputRef}
                                type="text"
                                value={searchTerm}
                                onChange={(e) => {
                                    setSearchTerm(e.target.value);
                                    if (onSearch) handleAsyncSearch(e.target.value);
                                }}
                                placeholder={searchPlaceholder}
                                className="w-full pl-8 pr-3 py-2 text-sm border border-slate-200 rounded-md focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            />
                        </div>
                    </div>

                    {/* Options List */}
                    <ul className="max-h-48 overflow-auto py-1">
                        {asyncLoading ? (
                            <li className="px-3 py-3 flex justify-center">
                                <Loader2 className="w-4 h-4 animate-spin text-slate-400" />
                            </li>
                        ) : filteredOptions.length === 0 ? (
                            <li className="px-3 py-2 text-sm text-slate-400 text-center">
                                {searchTerm ? 'Keine Ergebnisse gefunden' : 'Suchbegriff eingeben...'}
                            </li>
                        ) : (
                            filteredOptions.map((option) => (
                                <li
                                    key={option.value}
                                    onClick={() => handleSelect(option.value)}
                                    className={cn(
                                        "px-3 py-2 text-sm cursor-pointer flex items-center justify-between gap-2",
                                        "hover:bg-rose-50 hover:text-rose-700 transition-colors",
                                        value === option.value && "bg-rose-50 text-rose-700"
                                    )}
                                >
                                    <div className="flex flex-col min-w-0">
                                        <span className="truncate font-medium">{option.label}</span>
                                        {option.sublabel && (
                                            <span className="truncate text-xs text-slate-400">{option.sublabel}</span>
                                        )}
                                    </div>
                                    {value === option.value && (
                                        <Check className="w-4 h-4 text-rose-600 shrink-0" />
                                    )}
                                </li>
                            ))
                        )}
                    </ul>
                </div>
            )}
        </div>
    );
}
