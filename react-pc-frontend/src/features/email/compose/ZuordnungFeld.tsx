import { Briefcase, FileText, Link2, X } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { Label } from '../../../components/ui/label';
import type { EmailZuordnung } from '../../../components/EmailZuordnungSearchModal';

interface ZuordnungFeldProps {
    inline: boolean;
    zuordnung: EmailZuordnung | null;
    name: string;
    /** Antwort: erbt den Vorgang der Ursprungsmail, „Entfernen“ hätte keine Wirkung. */
    antwort: boolean;
    onSuchen: () => void;
    onAendern: () => void;
    onEntfernen: () => void;
}

/** Zeile „Gehört zu“: Projekt oder Anfrage verknüpfen, ändern oder lösen. */
export function ZuordnungFeld({ inline, zuordnung, name, antwort, onSuchen, onAendern, onEntfernen }: ZuordnungFeldProps) {
    const typText = zuordnung?.typ === 'PROJEKT' ? 'Projekt' : 'Anfrage';
    return (
        <div className={inline ? "grid grid-cols-[68px_minmax(0,1fr)] items-center gap-2" : "space-y-2"}>
            <Label>Gehört zu</Label>
            {zuordnung ? (
                <div className={inline ? "flex min-w-0 items-center gap-1 rounded-lg border border-rose-200 bg-rose-50 px-2 py-1" : "flex items-center gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3"}>
                    <div className={inline ? "hidden" : "flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-white"}>
                        {zuordnung.typ === 'PROJEKT'
                            ? <Briefcase className="h-4 w-4 text-rose-600" />
                            : <FileText className="h-4 w-4 text-rose-600" />}
                    </div>
                    <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-900">{name || typText}</p>
                        <p className="text-xs text-slate-500">
                            {typText}
                            {zuordnung.nummer ? ` · ${zuordnung.nummer}` : ''}
                        </p>
                    </div>
                    <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className={inline ? "h-8 min-h-8 text-xs text-rose-700" : "min-h-11 text-xs text-rose-700 hover:bg-rose-100"}
                        onClick={onAendern}
                    >
                        Ändern
                    </Button>
                    {!antwort && (
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className={inline ? "h-8 min-h-8 text-slate-500" : "min-h-11 text-slate-500 hover:text-slate-700"}
                            onClick={onEntfernen}
                            aria-label="Verknüpfung entfernen"
                        >
                            <X className="h-4 w-4" />
                        </Button>
                    )}
                </div>
            ) : (
                <Button
                    type="button"
                    variant="outline"
                    onClick={onSuchen}
                    className={inline ? "h-9 min-h-9 w-full justify-start border-rose-200 text-rose-700 text-xs" : "min-h-11 w-full justify-start border-rose-200 text-rose-700 hover:bg-rose-50"}
                >
                    <Link2 className="mr-2 h-4 w-4" />
                    Projekt oder Anfrage suchen
                </Button>
            )}
            <p className={inline ? "hidden" : "text-xs text-slate-500"}>
                Damit die E-Mail später beim richtigen Vorgang auftaucht.
            </p>
        </div>
    );
}
