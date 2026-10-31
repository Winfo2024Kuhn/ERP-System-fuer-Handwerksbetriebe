import { Search, X } from 'lucide-react';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import type { FilterOption } from './textbausteinFilter';

interface TextbausteinFilterleisteProps {
    suche: string;
    onSucheChange: (suche: string) => void;
    dokumentart: string;
    onDokumentartChange: (dokumentart: string) => void;
    optionen: FilterOption[];
}

/** Suchfeld (Name und Text) und Auswahl „Verwendet in“ über der Kartenliste. */
export function TextbausteinFilterleiste({ suche, onSucheChange, dokumentart, onDokumentartChange, optionen }: TextbausteinFilterleisteProps) {
    return (
        <div className="flex flex-col gap-3 md:flex-row md:items-end">
            <div className="flex-1">
                <Label htmlFor="textbausteine-suche">Suchen</Label>
                <div className="relative">
                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <Input
                        id="textbausteine-suche"
                        type="text"
                        value={suche}
                        onChange={(event) => onSucheChange(event.target.value)}
                        placeholder="Name oder Text, z. B. „Zahlungsziel“"
                        className="bg-white pl-9 pr-9"
                    />
                    {suche && (
                        <button
                            type="button"
                            onClick={() => onSucheChange('')}
                            aria-label="Suche leeren"
                            className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:bg-rose-50 hover:text-rose-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                        >
                            <X className="h-4 w-4" aria-hidden="true" />
                        </button>
                    )}
                </div>
            </div>
            <div className="md:w-80">
                <Label htmlFor="textbausteine-dokumentart">Verwendet in</Label>
                <Select
                    id="textbausteine-dokumentart"
                    options={optionen}
                    value={dokumentart}
                    onChange={onDokumentartChange}
                    className="bg-white"
                />
            </div>
        </div>
    );
}
