import { ArrowDownAZ, ArrowUpAZ, Globe, Search, X } from 'lucide-react';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { Input } from '../../components/ui/input';
import { cn } from '../../lib/utils';
import type { ReadFilter } from './emailCenterModel';
import { SUCHE_MAX_ZEICHEN, SUCHE_MIN_ZEICHEN, type SuchModus } from './emailSuche';

export interface EmailSuchleisteProps {
    begriff: string;
    onBegriff: (begriff: string) => void;
    modus: SuchModus;
    onModus: (modus: SuchModus) => void;
    /** Es werden gerade Suchtreffer gezeigt: Sortierung und Lese-Filter wirken dann nicht. */
    sucheAktiv: boolean;
    sortierung: 'desc' | 'asc';
    onSortierung: (sortierung: 'desc' | 'asc') => void;
    leseFilter: ReadFilter;
    onLeseFilter: (filter: ReadFilter) => void;
    /** Gesendet und Entwürfe haben keinen Lese-Status. */
    zeigeLeseFilter: boolean;
}

const UMSCHALTER = 'flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded-md text-xs font-medium transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500';
const SORTIERT_BEI_SUCHE = 'Suchtreffer sind immer nach Datum sortiert, neueste zuerst.';
const LESE_FILTER: { id: ReadFilter; label: string }[] = [
    { id: 'all', label: 'Alle' }, { id: 'unread', label: 'Ungelesen' }, { id: 'read', label: 'Gelesen' },
];

function SuchInfo() {
    return <InfoButton titel="So sucht das E-Mail-Center">
        <InfoAbschnitt titel="Was passiert im Hintergrund?">
            <p>Durchsucht werden Absender und Empfänger samt Namen, CC, Betreff, der ganze Text mit dem zitierten Verlauf
                und die Namen der Anhänge. Groß- und Kleinschreibung spielt keine Rolle, Umlaute auch nicht:
                „wurth“, „wuerth“ und „Würth“ finden dasselbe. Auch Wortteile zählen: „gel“ findet „Geländer“.</p>
        </InfoAbschnitt>
        <InfoAbschnitt titel="Was bewirkt das?">
            <p>Bei mehreren Wörtern erscheinen nur Mails, in denen alle Wörter vorkommen, egal an welcher Stelle.
                „Alle Ordner“ sucht auch im Papierkorb, im Spam und in Gesendet; ein Schild an jeder Mail zeigt, wo sie liegt.
                Entwürfe werden nicht durchsucht.</p>
        </InfoAbschnitt>
    </InfoButton>;
}

function Sortierknopf({ sortierung, onSortierung, gesperrt }: { sortierung: 'desc' | 'asc'; onSortierung: (s: 'desc' | 'asc') => void; gesperrt: boolean }) {
    // Gesperrt per aria-disabled statt disabled: So bleibt der Tooltip mit dem Grund sichtbar.
    const titel = gesperrt ? SORTIERT_BEI_SUCHE
        : sortierung === 'desc' ? 'Neueste zuerst, klicken für Älteste zuerst' : 'Älteste zuerst, klicken für Neueste zuerst';
    return <button type="button" title={titel} aria-label={titel} aria-disabled={gesperrt || undefined}
        onClick={() => { if (!gesperrt) onSortierung(sortierung === 'desc' ? 'asc' : 'desc'); }}
        className={cn('flex items-center justify-center gap-1 px-2 py-1.5 rounded-md text-xs font-medium transition-colors shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
            gesperrt ? 'cursor-not-allowed text-slate-300' : 'cursor-pointer text-slate-500 hover:bg-rose-50 hover:text-rose-600')}>
        {sortierung === 'desc' || gesperrt ? <ArrowDownAZ className="w-3.5 h-3.5" /> : <ArrowUpAZ className="w-3.5 h-3.5" />}
    </button>;
}

/** Suchfeld, Umschalter „Ordner“/„Alle Ordner“, Sortierung und Lese-Filter über der Mail-Liste. */
export function EmailSuchleiste({ begriff, onBegriff, modus, onModus, sucheAktiv, sortierung, onSortierung,
    leseFilter, onLeseFilter, zeigeLeseFilter }: EmailSuchleisteProps) {
    const alle = modus === 'alle';
    const Icon = alle ? Globe : Search;
    return (
        <div className="p-3 border-b border-slate-200 space-y-2">
            <div className="relative">
                <Icon aria-hidden="true" className={cn('w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2', alle ? 'text-rose-500' : 'text-slate-400')} />
                <Input type="search" aria-label="E-Mails durchsuchen" maxLength={SUCHE_MAX_ZEICHEN}
                    placeholder={alle ? 'In allen Ordnern suchen …' : 'In diesem Ordner suchen …'}
                    className={cn('pl-9 pr-9 border-slate-200 focus:border-rose-300 focus:ring-rose-200 transition-colors [&::-webkit-search-cancel-button]:appearance-none',
                        alle ? 'bg-rose-50/50 border-rose-200' : 'bg-slate-50 focus:bg-white')}
                    value={begriff} onChange={(e) => onBegriff(e.target.value)} />
                {begriff && (
                    <button type="button" onClick={() => onBegriff('')} aria-label="Suche leeren" title="Suche leeren"
                        className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:text-slate-600 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                        <X className="w-4 h-4" />
                    </button>
                )}
            </div>
            <div className="flex items-center gap-2">
                <button type="button" aria-pressed={!alle} onClick={() => onModus('ordner')}
                    className={cn(UMSCHALTER, !alle ? 'bg-slate-100 text-slate-800 shadow-sm' : 'text-slate-500 hover:bg-slate-50')}>
                    <Search aria-hidden="true" className="w-3 h-3" />Ordner
                </button>
                <button type="button" aria-pressed={alle} onClick={() => onModus('alle')}
                    className={cn(UMSCHALTER, alle ? 'bg-rose-100 text-rose-700 shadow-sm' : 'text-slate-500 hover:bg-slate-50')}>
                    <Globe aria-hidden="true" className="w-3 h-3" />Alle Ordner
                </button>
                <SuchInfo />
                <Sortierknopf sortierung={sortierung} onSortierung={onSortierung} gesperrt={sucheAktiv} />
            </div>
            {zeigeLeseFilter && !sucheAktiv && (
                <div className="flex items-center gap-1 bg-slate-50 rounded-md p-0.5">
                    {LESE_FILTER.map(opt => (
                        <button key={opt.id} type="button" aria-pressed={leseFilter === opt.id} onClick={() => onLeseFilter(opt.id)}
                            className={cn('flex-1 px-2 py-1 rounded text-xs font-medium transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                                leseFilter === opt.id ? 'bg-white text-rose-700 shadow-sm ring-1 ring-rose-200' : 'text-slate-500 hover:text-slate-700')}>
                            {opt.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}

export interface EmailSuchHinweisProps {
    modus: SuchModus;
    begriff: string;
    aktiv: boolean;
    laedt: boolean;
    fehler: boolean;
    anzahl: number;
    hatMehr: boolean;
    /** Im Entwürfe-Ordner filtert die Seite selbst – dann gibt es keine Mindestlänge. */
    serverSuche: boolean;
}

function hinweisText({ modus, begriff, aktiv, laedt, fehler, anzahl, hatMehr, serverSuche }: EmailSuchHinweisProps): string | null {
    if (aktiv) {
        if (fehler) return null;
        if (laedt) return 'Suche läuft …';
        const ort = modus === 'alle' ? 'in allen Ordnern' : 'in diesem Ordner';
        return `${anzahl} Treffer ${ort}${hatMehr ? ', weitere beim Scrollen' : ''}`;
    }
    const laenge = begriff.trim().length;
    if (serverSuche && laenge > 0 && laenge < SUCHE_MIN_ZEICHEN) return `Mindestens ${SUCHE_MIN_ZEICHEN} Zeichen eingeben.`;
    if (modus === 'alle' && laenge === 0) return 'Sucht in allen Ordnern, auch im Papierkorb, im Spam und in Gesendet.';
    return null;
}

/** Zeile unter der Suchleiste: was die Suche gerade tut bzw. gefunden hat. */
export function EmailSuchHinweis(props: EmailSuchHinweisProps) {
    const text = hinweisText(props);
    if (!text) return null;
    const Icon = props.modus === 'alle' ? Globe : Search;
    return <div role="status" className="px-3 py-2 bg-slate-50 border-b border-slate-200 text-xs text-slate-600 flex items-center gap-2">
        <Icon aria-hidden="true" className="w-3.5 h-3.5 shrink-0 text-slate-400" />
        {text}
    </div>;
}
