import { Plus, Search } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { cn } from '../../../lib/utils';
import type { EmailsTabFilter } from './emailsTabTypes';

interface EmailsTabToolbarProps {
    search: string;
    onSearchChange: (search: string) => void;
    filter: EmailsTabFilter;
    onFilterChange: (filter: EmailsTabFilter) => void;
    showCompose: boolean;
    onCompose: () => void;
}

/** Kopf des E-Mail-Reiters: Überschrift, Suche, Filter Alle/Eingang/Ausgang, „Neue E-Mail“. */
export function EmailsTabToolbar({ search, onSearchChange, filter, onFilterChange, showCompose, onCompose }: EmailsTabToolbarProps) {
    return (
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-4">
            <h3 className="text-lg font-medium text-slate-900">E-Mails</h3>
            <div className="flex flex-wrap items-center gap-3">
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                        type="text"
                        placeholder="E-Mails durchsuchen..."
                        value={search}
                        onChange={e => onSearchChange(e.target.value)}
                        className="pl-9 pr-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500 w-48"
                    />
                </div>
                <div className="flex rounded-lg border border-slate-200 overflow-hidden">
                    <button
                        onClick={() => onFilterChange('all')}
                        className={cn("px-3 py-1.5 text-sm font-medium transition-colors",
                            filter === 'all' ? "bg-rose-600 text-white" : "bg-white text-slate-600 hover:bg-slate-50"
                        )}
                    >Alle</button>
                    <button
                        onClick={() => onFilterChange('in')}
                        className={cn("px-3 py-1.5 text-sm font-medium transition-colors border-l border-slate-200",
                            filter === 'in' ? "bg-blue-600 text-white" : "bg-white text-slate-600 hover:bg-slate-50"
                        )}
                    >Eingang</button>
                    <button
                        onClick={() => onFilterChange('out')}
                        className={cn("px-3 py-1.5 text-sm font-medium transition-colors border-l border-slate-200",
                            filter === 'out' ? "bg-green-600 text-white" : "bg-white text-slate-600 hover:bg-slate-50"
                        )}
                    >Ausgang</button>
                </div>
                {showCompose && (
                    <Button onClick={onCompose} className="bg-rose-600 text-white hover:bg-rose-700">
                        <Plus className="w-4 h-4 mr-2" /> Neue E-Mail
                    </Button>
                )}
            </div>
        </div>
    );
}
