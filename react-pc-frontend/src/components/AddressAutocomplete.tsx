import React, { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { Loader2, MapPin, Search } from 'lucide-react';
import { cn } from '../lib/utils';

export type AddressValue = {
    strasse: string;
    plz: string;
    ort: string;
};

type PhotonProperties = {
    street?: string;
    housenumber?: string;
    postcode?: string;
    city?: string;
    locality?: string;
    name?: string;
    district?: string;
    state?: string;
    country?: string;
    countrycode?: string;
    osm_value?: string;
    type?: string;
};

type PhotonFeature = {
    properties: PhotonProperties;
};

type Suggestion = {
    key: string;
    primary: string;
    secondary: string;
    value: AddressValue;
};

export type AddressAutocompleteProps = {
    value: AddressValue;
    onChange: (next: AddressValue) => void;
    disabled?: boolean;
    showLabels?: boolean;
    strasseLabel?: string;
    plzLabel?: string;
    ortLabel?: string;
    strassePlaceholder?: string;
    plzPlaceholder?: string;
    ortPlaceholder?: string;
    className?: string;
    inputClassName?: string;
    countryCodes?: string[];
    minChars?: number;
};

const PHOTON_URL = 'https://photon.komoot.io/api/';
const DEFAULT_COUNTRIES = ['de', 'at', 'ch'];

function mapFeature(feature: PhotonFeature): Suggestion | null {
    const props = feature.properties || {};
    const ort = props.city || props.locality || props.name || '';
    const strasseParts = [props.street, props.housenumber].filter(Boolean);
    const strasse = strasseParts.join(' ');

    if (!ort && !strasse && !props.postcode) return null;

    const primaryParts = strasse ? [strasse] : [props.name || ''].filter(Boolean);
    const secondaryParts = [props.postcode, ort].filter(Boolean).join(' ');
    const country = props.country ? `, ${props.country}` : '';

    return {
        key: `${strasse}|${props.postcode || ''}|${ort}|${props.state || ''}|${props.country || ''}`,
        primary: primaryParts.join(' ') || ort,
        secondary: `${secondaryParts}${country}`.trim(),
        value: {
            strasse,
            plz: props.postcode || '',
            ort
        }
    };
}

export const AddressAutocomplete: React.FC<AddressAutocompleteProps> = ({
    value,
    onChange,
    disabled = false,
    showLabels = true,
    strasseLabel = 'Straße',
    plzLabel = 'PLZ',
    ortLabel = 'Ort',
    strassePlaceholder = 'Straße + Hausnummer eingeben...',
    plzPlaceholder = 'PLZ',
    ortPlaceholder = 'Ort',
    className,
    inputClassName,
    countryCodes = DEFAULT_COUNTRIES,
    minChars = 3
}) => {
    const idPrefix = useId();
    const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [activeIdx, setActiveIdx] = useState(-1);
    const containerRef = useRef<HTMLDivElement>(null);
    const abortRef = useRef<AbortController | null>(null);
    const debounceRef = useRef<number | null>(null);
    const skipNextSearchRef = useRef(false);

    const allowedCountries = useMemo(
        () => new Set(countryCodes.map(c => c.toLowerCase())),
        [countryCodes]
    );

    const updateField = useCallback(
        (patch: Partial<AddressValue>) => {
            onChange({ ...value, ...patch });
        },
        [onChange, value]
    );

    const runSearch = useCallback(
        async (query: string) => {
            if (abortRef.current) abortRef.current.abort();
            const controller = new AbortController();
            abortRef.current = controller;
            setLoading(true);
            try {
                const url = `${PHOTON_URL}?q=${encodeURIComponent(query)}&lang=de&limit=6`;
                const res = await fetch(url, { signal: controller.signal });
                if (!res.ok) throw new Error(`Photon ${res.status}`);
                const data = (await res.json()) as { features?: PhotonFeature[] };
                const items = (data.features || [])
                    .filter(f => {
                        const cc = (f.properties?.countrycode || '').toLowerCase();
                        return !cc || allowedCountries.has(cc);
                    })
                    .map(mapFeature)
                    .filter((s): s is Suggestion => s !== null);

                const seen = new Set<string>();
                const unique = items.filter(s => {
                    if (seen.has(s.key)) return false;
                    seen.add(s.key);
                    return true;
                });

                setSuggestions(unique);
                setOpen(unique.length > 0);
                setActiveIdx(-1);
            } catch (err) {
                if ((err as { name?: string })?.name !== 'AbortError') {
                    setSuggestions([]);
                    setOpen(false);
                }
            } finally {
                setLoading(false);
            }
        },
        [allowedCountries]
    );

    useEffect(() => {
        if (skipNextSearchRef.current) {
            skipNextSearchRef.current = false;
            return;
        }
        const query = value.strasse.trim();
        if (debounceRef.current) window.clearTimeout(debounceRef.current);
        if (query.length < minChars) {
            setSuggestions([]);
            setOpen(false);
            setLoading(false);
            return;
        }
        debounceRef.current = window.setTimeout(() => {
            void runSearch(query);
        }, 280);
        return () => {
            if (debounceRef.current) window.clearTimeout(debounceRef.current);
        };
    }, [value.strasse, minChars, runSearch]);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (!containerRef.current) return;
            if (!containerRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const selectSuggestion = (s: Suggestion) => {
        skipNextSearchRef.current = true;
        onChange(s.value);
        setOpen(false);
        setSuggestions([]);
        setActiveIdx(-1);
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (!open || suggestions.length === 0) return;
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            setActiveIdx(idx => (idx + 1) % suggestions.length);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setActiveIdx(idx => (idx <= 0 ? suggestions.length - 1 : idx - 1));
        } else if (e.key === 'Enter' && activeIdx >= 0) {
            e.preventDefault();
            selectSuggestion(suggestions[activeIdx]);
        } else if (e.key === 'Escape') {
            setOpen(false);
        }
    };

    const inputBase = cn(
        'w-full border border-slate-200 rounded-lg px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:bg-slate-50 disabled:text-slate-500',
        inputClassName
    );

    return (
        <div className={cn('grid grid-cols-1 md:grid-cols-3 gap-4', className)} ref={containerRef}>
            <div className="md:col-span-2 relative">
                {showLabels && (
                    <label htmlFor={`${idPrefix}-strasse`} className="block text-sm font-medium text-slate-700 mb-1">
                        {strasseLabel}
                    </label>
                )}
                <div className="relative">
                    <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                    <input
                        id={`${idPrefix}-strasse`}
                        type="text"
                        value={value.strasse}
                        onChange={e => updateField({ strasse: e.target.value })}
                        onFocus={() => suggestions.length > 0 && setOpen(true)}
                        onKeyDown={handleKeyDown}
                        placeholder={strassePlaceholder}
                        disabled={disabled}
                        autoComplete="off"
                        className={cn(inputBase, 'pl-9 pr-9')}
                    />
                    {loading && (
                        <Loader2 className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 animate-spin" />
                    )}
                </div>
                {open && suggestions.length > 0 && (
                    <div className="absolute z-50 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg max-h-72 overflow-y-auto">
                        {suggestions.map((s, idx) => (
                            <button
                                key={s.key + idx}
                                type="button"
                                onMouseDown={e => {
                                    e.preventDefault();
                                    selectSuggestion(s);
                                }}
                                onMouseEnter={() => setActiveIdx(idx)}
                                className={cn(
                                    'w-full text-left px-3 py-2 text-sm flex items-start gap-2 border-b border-slate-100 last:border-b-0',
                                    idx === activeIdx ? 'bg-rose-50' : 'hover:bg-slate-50'
                                )}
                            >
                                <MapPin className="w-4 h-4 text-rose-500 mt-0.5 shrink-0" />
                                <div className="min-w-0">
                                    <div className="text-slate-900 truncate">{s.primary}</div>
                                    {s.secondary && (
                                        <div className="text-xs text-slate-500 truncate">{s.secondary}</div>
                                    )}
                                </div>
                            </button>
                        ))}
                        <div className="px-3 py-1.5 text-[10px] text-slate-400 bg-slate-50 border-t border-slate-100">
                            Vorschläge von OpenStreetMap (Photon)
                        </div>
                    </div>
                )}
            </div>
            <div>
                {showLabels && (
                    <label htmlFor={`${idPrefix}-plz`} className="block text-sm font-medium text-slate-700 mb-1">
                        {plzLabel}
                    </label>
                )}
                <input
                    id={`${idPrefix}-plz`}
                    type="text"
                    value={value.plz}
                    onChange={e => updateField({ plz: e.target.value })}
                    placeholder={plzPlaceholder}
                    disabled={disabled}
                    autoComplete="off"
                    className={inputBase}
                />
            </div>
            <div className="md:col-span-3">
                {showLabels && (
                    <label htmlFor={`${idPrefix}-ort`} className="block text-sm font-medium text-slate-700 mb-1">
                        {ortLabel}
                    </label>
                )}
                <input
                    id={`${idPrefix}-ort`}
                    type="text"
                    value={value.ort}
                    onChange={e => updateField({ ort: e.target.value })}
                    placeholder={ortPlaceholder}
                    disabled={disabled}
                    autoComplete="off"
                    className={inputBase}
                />
            </div>
        </div>
    );
};

export default AddressAutocomplete;
