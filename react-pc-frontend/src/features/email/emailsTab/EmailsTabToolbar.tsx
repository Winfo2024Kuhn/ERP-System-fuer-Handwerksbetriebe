import { Plus, Search } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { SegmentAuswahl, type SegmentOption } from '../../../components/ui/segment-auswahl';
import type { EmailsTabFilter } from './emailsTabTypes';

interface EmailsTabToolbarProps {
    search: string;
    onSearchChange: (search: string) => void;
    filter: EmailsTabFilter;
    onFilterChange: (filter: EmailsTabFilter) => void;
    showCompose: boolean;
    onCompose: () => void;
}

const FILTER: SegmentOption[] = [
    { wert: 'all', text: 'Alle' },
    { wert: 'in', text: 'Eingang' },
    { wert: 'out', text: 'Ausgang' },
];

/** Kopf des E-Mail-Reiters: Überschrift, Suche, Filter Alle/Eingang/Ausgang, „Neue E-Mail“. */
export function EmailsTabToolbar({ search, onSearchChange, filter, onFilterChange, showCompose, onCompose }: EmailsTabToolbarProps) {
    return (
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-4">
            <h3 className="text-lg font-medium text-slate-900">E-Mails</h3>
            <div className="flex flex-wrap items-center gap-3">
                <div className="relative">
                    <Search aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <Input
                        type="text"
                        aria-label="E-Mails durchsuchen"
                        placeholder="E-Mails durchsuchen…"
                        value={search}
                        onChange={e => onSearchChange(e.target.value)}
                        className="pl-9 pr-3 py-2 rounded-lg w-60"
                    />
                </div>
                <SegmentAuswahl
                    beschriftung="E-Mails filtern"
                    optionen={FILTER}
                    wert={filter}
                    onChange={wert => onFilterChange(wert as EmailsTabFilter)}
                />
                {showCompose && (
                    <Button onClick={onCompose}>
                        <Plus aria-hidden="true" className="w-4 h-4 mr-2" /> Neue E-Mail
                    </Button>
                )}
            </div>
        </div>
    );
}
