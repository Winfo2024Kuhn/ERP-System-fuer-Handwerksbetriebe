import React, { useCallback, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Loader2, MapPin, Search } from 'lucide-react';
import { cn } from '../lib/utils';

export type AddressValue = {
    strasse: string;
    plz: string;
    ort: string;
};

type Suggestion = {
    key: string;
    primary: string;
    secondary: string;
    source: 'photon' | 'nominatim';
    countryCode: string;
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
const NOMINATIM_URL = 'https://nominatim.openstreetmap.org/search';
const DEFAULT_COUNTRIES = ['de'];
const DEBOUNCE_MS = 150;
const REQUEST_TIMEOUT_MS = 6000;
const RESULT_LIMIT = 6;
const CACHE_MAX = 80;
const queryCache = new Map<string, Suggestion[]>();

function rememberInCache(key: string, items: Suggestion[]) {
    queryCache.set(key, items);
    if (queryCache.size > CACHE_MAX) {
        const firstKey = queryCache.keys().next().value;
        if (firstKey !== undefined) queryCache.delete(firstKey);
    }
}

type PhotonProperties = {
    street?: string;
    housenumber?: string;
    postcode?: string;
    city?: string;
    locality?: string;
    name?: string;
    country?: string;
    countrycode?: string;
};

type PhotonFeature = { properties: PhotonProperties };

type NominatimItem = {
    display_name?: string;
    address?: {
        road?: string;
        pedestrian?: string;
        footway?: string;
        house_number?: string;
        postcode?: string;
        city?: string;
        town?: string;
        village?: string;
        municipality?: string;
        suburb?: string;
        country?: string;
        country_code?: string;
    };
};

function mapPhotonFeature(f: PhotonFeature): Suggestion | null {
    const p = f.properties || {};
    const ort = p.city || p.locality || p.name || '';
    const strasse = [p.street, p.housenumber].filter(Boolean).join(' ');
    if (!ort && !strasse && !p.postcode) return null;
    const primary = strasse || p.name || ort;
    const secondary = [[p.postcode, ort].filter(Boolean).join(' '), p.country].filter(Boolean).join(', ');
    return {
        key: `${strasse}|${p.postcode || ''}|${ort}|${p.country || ''}`,
        primary,
        secondary,
        source: 'photon',
        countryCode: (p.countrycode || '').toLowerCase(),
        value: { strasse, plz: p.postcode || '', ort }
    };
}

function mapNominatimItem(it: NominatimItem): Suggestion | null {
    const a = it.address || {};
    const street = a.road || a.pedestrian || a.footway || '';
    const house = a.house_number || '';
    const strasse = [street, house].filter(Boolean).join(' ');
    const ort = a.city || a.town || a.village || a.municipality || a.suburb || '';
    if (!ort && !strasse && !a.postcode) return null;
    const primary = strasse || ort;
    const secondary = [[a.postcode, ort].filter(Boolean).join(' '), a.country].filter(Boolean).join(', ');
    return {
        key: `${strasse}|${a.postcode || ''}|${ort}|${a.country || ''}`,
        primary,
        secondary,
        source: 'nominatim',
        countryCode: (a.country_code || '').toLowerCase(),
        value: { strasse, plz: a.postcode || '', ort }
    };
}

function dedupe(items: Suggestion[]): Suggestion[] {
    const seen = new Set<string>();
    const out: Suggestion[] = [];
    for (const s of items) {
        if (!seen.has(s.key)) {
            seen.add(s.key);
            out.push(s);
        }
    }
    return out;
}

async function searchPhoton(query: string, signal: AbortSignal): Promise<Suggestion[]> {
    const url = `${PHOTON_URL}?q=${encodeURIComponent(query)}&lang=de&limit=${RESULT_LIMIT}`;
    const res = await fetch(url, { signal });
    if (!res.ok) throw new Error(`Photon ${res.status}`);
    const data = (await res.json()) as { features?: PhotonFeature[] };
    return (data.features || []).map(mapPhotonFeature).filter((s): s is Suggestion => s !== null);
}

async function searchNominatim(query: string, signal: AbortSignal, countries: string[]): Promise<Suggestion[]> {
    const cc = countries.length > 0 ? `&countrycodes=${countries.map(c => c.toLowerCase()).join(',')}` : '';
    const url = `${NOMINATIM_URL}?q=${encodeURIComponent(query)}&format=json&addressdetails=1&limit=${RESULT_LIMIT}&accept-language=de${cc}`;
    const res = await fetch(url, { signal, headers: { 'Accept': 'application/json' } });
    if (!res.ok) throw new Error(`Nominatim ${res.status}`);
    const data = (await res.json()) as NominatimItem[];
    return data.map(mapNominatimItem).filter((s): s is Suggestion => s !== null);
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
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const [activeIdx, setActiveIdx] = useState(-1);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const abortRef = useRef<AbortController | null>(null);
    const debounceRef = useRef<number | null>(null);
    const timeoutRef = useRef<number | null>(null);
    const skipNextSearchRef = useRef(false);

    const countriesKey = useMemo(
        () => [...countryCodes].map(c => c.toLowerCase()).sort().join(','),
        [countryCodes]
    );

    const cacheKey = useCallback(
        (q: string) => `${countriesKey}|${q.toLowerCase()}`,
        [countriesKey]
    );

    const allowedCountries = useMemo(
        () => countryCodes.map(c => c.toLowerCase()),
        [countryCodes]
    );

    const allowedSet = useMemo(() => new Set(allowedCountries), [allowedCountries]);

    const filterByCountry = useCallback(
        (items: Suggestion[]) => {
            if (allowedSet.size === 0) return items;
            return items.filter(s => !s.countryCode || allowedSet.has(s.countryCode));
        },
        [allowedSet]
    );

    const updateField = useCallback(
        (patch: Partial<AddressValue>) => {
            onChange({ ...value, ...patch });
        },
        [onChange, value]
    );

    const runSearch = useCallback(
        async (query: string) => {
            const ck = cacheKey(query);
            const cached = queryCache.get(ck);
            if (cached) {
                setSuggestions(cached);
                setOpen(true);
                setActiveIdx(-1);
                setLoading(false);
                setErrorMsg(null);
                return;
            }

            if (abortRef.current) abortRef.current.abort();
            if (timeoutRef.current) window.clearTimeout(timeoutRef.current);

            const controller = new AbortController();
            abortRef.current = controller;
            let didTimeout = false;
            timeoutRef.current = window.setTimeout(() => {
                didTimeout = true;
                controller.abort();
            }, REQUEST_TIMEOUT_MS);

            setLoading(true);
            setOpen(true);
            setErrorMsg(null);

            const wrap = (p: Promise<Suggestion[]>) =>
                p.then(items => {
                    if (items.length === 0) throw new Error('empty');
                    return items;
                });

            const cleanup = () => {
                if (timeoutRef.current) {
                    window.clearTimeout(timeoutRef.current);
                    timeoutRef.current = null;
                }
                if (abortRef.current === controller) {
                    abortRef.current = null;
                }
            };

            try {
                const items = await Promise.any([
                    wrap(searchPhoton(query, controller.signal).then(filterByCountry)),
                    wrap(searchNominatim(query, controller.signal, allowedCountries))
                ]);
                const unique = dedupe(items);
                rememberInCache(ck, unique);
                setSuggestions(unique);
                setActiveIdx(-1);
                setErrorMsg(null);
                controller.abort();
            } catch (err) {
                if (didTimeout) {
                    setSuggestions([]);
                    setErrorMsg('Suchdienst antwortet nicht. Bitte manuell eintragen.');
                } else if (err instanceof AggregateError) {
                    const allEmpty = err.errors.every(e =>
                        e instanceof Error && e.message === 'empty'
                    );
                    setSuggestions([]);
                    if (!allEmpty) {
                        setErrorMsg('Suchdienst nicht erreichbar. Bitte manuell eintragen.');
                    }
                } else {
                    const name = (err as { name?: string })?.name;
                    if (name !== 'AbortError') {
                        setSuggestions([]);
                        setErrorMsg('Suchdienst nicht erreichbar. Bitte manuell eintragen.');
                    }
                }
            } finally {
                cleanup();
                setLoading(false);
            }
        },
        [cacheKey, allowedCountries, filterByCountry]
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
            setErrorMsg(null);
            return;
        }
        const cached = queryCache.get(cacheKey(query));
        if (cached) {
            setSuggestions(cached);
            setOpen(true);
            setLoading(false);
            setErrorMsg(null);
        } else {
            setLoading(true);
            setErrorMsg(null);
            setOpen(true);
        }
        debounceRef.current = window.setTimeout(() => {
            void runSearch(query);
        }, DEBOUNCE_MS);
        return () => {
            if (debounceRef.current) window.clearTimeout(debounceRef.current);
        };
    }, [value.strasse, minChars, runSearch, cacheKey]);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            const target = e.target as Node;
            const inWrapper = wrapperRef.current?.contains(target);
            const inDropdown = dropdownRef.current?.contains(target);
            if (!inWrapper && !inDropdown) setOpen(false);
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const showDropdown = open && !disabled && (
        suggestions.length > 0 || loading || errorMsg !== null || value.strasse.trim().length >= minChars
    );

    useLayoutEffect(() => {
        if (!showDropdown || !wrapperRef.current) return;
        const wrap = wrapperRef.current;
        const updatePosition = () => {
            const dd = dropdownRef.current;
            if (!dd) return;
            const rect = wrap.getBoundingClientRect();
            dd.style.top = `${rect.bottom + 4}px`;
            dd.style.left = `${rect.left}px`;
            dd.style.width = `${rect.width}px`;
        };
        updatePosition();
        window.addEventListener('scroll', updatePosition, true);
        window.addEventListener('resize', updatePosition);
        return () => {
            window.removeEventListener('scroll', updatePosition, true);
            window.removeEventListener('resize', updatePosition);
        };
    }, [showDropdown, suggestions.length, loading, errorMsg]);

    const selectSuggestion = (s: Suggestion) => {
        skipNextSearchRef.current = true;
        onChange(s.value);
        setOpen(false);
        setSuggestions([]);
        setActiveIdx(-1);
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (!showDropdown) return;
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (suggestions.length === 0) return;
            setActiveIdx(idx => (idx + 1) % suggestions.length);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (suggestions.length === 0) return;
            setActiveIdx(idx => (idx <= 0 ? suggestions.length - 1 : idx - 1));
        } else if (e.key === 'Enter' && activeIdx >= 0 && suggestions[activeIdx]) {
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
        <div className={cn('grid grid-cols-1 md:grid-cols-3 gap-4', className)}>
            <div className="md:col-span-2 relative" ref={wrapperRef}>
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
                        onChange={e => {
                            updateField({ strasse: e.target.value });
                            setOpen(true);
                        }}
                        onFocus={() => {
                            if (value.strasse.trim().length >= minChars) setOpen(true);
                        }}
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

            {showDropdown && createPortal(
                <div
                    ref={dropdownRef}
                    style={{ position: 'fixed', zIndex: 1000 }}
                    className="bg-white border border-slate-200 rounded-lg shadow-xl max-h-80 overflow-hidden flex flex-col"
                >
                    {loading && (
                        <div className="h-1 w-full bg-rose-100 overflow-hidden shrink-0 relative">
                            <div className="address-loader-bar absolute top-0 h-full bg-rose-500 rounded-full" />
                        </div>
                    )}
                    <div className="overflow-y-auto">
                        {suggestions.length === 0 && loading && (
                            <div className="px-3 py-3 text-sm text-slate-500 flex items-center gap-2">
                                <Loader2 className="w-4 h-4 animate-spin" />
                                Suche Adressen ...
                            </div>
                        )}
                        {suggestions.length === 0 && !loading && errorMsg && (
                            <div className="px-3 py-3 text-sm text-amber-700 bg-amber-50">
                                {errorMsg}
                            </div>
                        )}
                        {suggestions.length === 0 && !loading && !errorMsg && (
                            <div className="px-3 py-3 text-sm text-slate-500">
                                Keine Vorschläge — du kannst die Adresse manuell eintragen.
                            </div>
                        )}
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
                    </div>
                    {suggestions.length > 0 && (
                        <div className="px-3 py-1.5 text-[10px] text-slate-400 bg-slate-50 border-t border-slate-100 shrink-0">
                            Vorschläge von OpenStreetMap
                        </div>
                    )}
                </div>,
                document.body
            )}
        </div>
    );
};

export default AddressAutocomplete;
