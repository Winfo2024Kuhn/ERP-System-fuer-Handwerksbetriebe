import { useState, type ReactNode } from 'react';
import { FolderPlus, Loader2 } from 'lucide-react';
import { cn } from '../../lib/utils';
import { leistungenWandernHinweis } from './ordnerLogik';

/** Eingaben direkt im Ordnerbaum: Name beim Umbenennen und Zeile für einen neuen Ordner. */

export function NamensEingabe({ start, beschriftung, onAbbrechen, onSpeichern, hinweis }: {
    start: string;
    beschriftung: string;
    onAbbrechen: () => void;
    /** Wirft bei einem Fehler – die Meldung erscheint unter der Eingabe. */
    onSpeichern: (name: string) => Promise<void>;
    hinweis?: ReactNode;
}) {
    const [name, setName] = useState(start);
    const [fehler, setFehler] = useState<string | null>(null);
    const [sendet, setSendet] = useState(false);

    const speichern = async () => {
        const sauber = name.trim();
        if (!sauber) { setFehler('Bitte geben Sie dem Ordner einen Namen.'); return; }
        if (sauber === start.trim() && start) { onAbbrechen(); return; }
        setSendet(true);
        setFehler(null);
        try {
            await onSpeichern(sauber);
        } catch (err) {
            setFehler(err instanceof Error ? err.message : 'Der Ordner konnte nicht gespeichert werden.');
            setSendet(false);
        }
    };

    return (
        <div className="min-w-0 flex-1" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center gap-1">
                <input
                    autoFocus
                    value={name}
                    disabled={sendet}
                    onChange={(e) => { setName(e.target.value); setFehler(null); }}
                    onFocus={(e) => e.currentTarget.select()}
                    onKeyDown={(e) => {
                        e.stopPropagation();
                        if (e.key === 'Enter') { e.preventDefault(); void speichern(); }
                        if (e.key === 'Escape') { e.preventDefault(); onAbbrechen(); }
                    }}
                    onBlur={() => { if (!sendet && (!name.trim() || name.trim() === start.trim())) onAbbrechen(); }}
                    aria-label={beschriftung}
                    aria-invalid={fehler ? true : undefined}
                    maxLength={255}
                    placeholder="Name des Ordners"
                    className={cn(
                        'w-full min-w-0 rounded border bg-white px-2 py-0.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500',
                        fehler ? 'border-red-400' : 'border-rose-300',
                    )}
                />
                {sendet && <Loader2 className="h-4 w-4 shrink-0 text-rose-500 motion-safe:animate-spin" aria-label="Wird gespeichert" />}
            </div>
            {hinweis}
            {fehler && <p role="alert" className="mt-1 text-xs text-red-600">{fehler}</p>}
            <p className="mt-0.5 text-[11px] text-slate-500">Enter speichert · Esc bricht ab</p>
        </div>
    );
}

export function EingabeZeile({ ebene, elternName, wandern, onAbbrechen, onSpeichern }: {
    ebene: number;
    elternName: string | null;
    /** Leistungen, die direkt im Eltern-Ordner liegen und mitwandern. */
    wandern: number;
    onAbbrechen: () => void;
    onSpeichern: (name: string) => Promise<void>;
}) {
    return (
        <div role="treeitem" aria-level={ebene + 1} aria-selected={false} aria-label="Neuer Ordner"
            className="flex items-start gap-1.5 rounded-md bg-rose-50/60 py-1.5 pr-1" style={{ paddingLeft: 4 + ebene * 16 }}>
            <span className="h-5 w-5 shrink-0" aria-hidden="true" />
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded bg-rose-100" aria-hidden="true">
                <FolderPlus className="h-4 w-4 text-rose-600" />
            </span>
            <NamensEingabe
                start=""
                beschriftung={elternName ? `Name des neuen Unterordners in „${elternName}“` : 'Name des neuen Ordners'}
                onAbbrechen={onAbbrechen}
                onSpeichern={onSpeichern}
                hinweis={wandern > 0 && elternName ? (
                    <p className="mt-1 rounded border border-amber-200 bg-amber-50 px-2 py-1 text-xs text-amber-800">
                        {leistungenWandernHinweis(wandern, elternName)}
                    </p>
                ) : null}
            />
        </div>
    );
}
