import { useState } from 'react';
import { ArrowLeft, CheckCircle2, Eye, FileText, Loader2, Pencil, Redo2, Save, Undo2 } from 'lucide-react';
import { Button } from '../ui/button';
import { cn } from '../../lib/utils';

interface EditorToolbarProps {
    vorlagenName: string | null;
    ungespeichert: boolean;
    speichert: boolean;
    onZurueck: () => void;
    onUmbenennen: (name: string) => void;
    onRueckgaengig: () => void;
    onWiederholen: () => void;
    kannRueckgaengig: boolean;
    kannWiederholen: boolean;
    onVorschau: () => void;
    onSpeichern: () => void;
    onStandardTexte: () => void;
}

const symbolKnopf = 'inline-flex h-9 w-9 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent';

/** Kopfleiste des Vorlagen-Editors: Zurück, Name, Speicherstand, Rückgängig, Vorschau, Speichern. */
export default function EditorToolbar({
    vorlagenName, ungespeichert, speichert, onZurueck, onUmbenennen,
    onRueckgaengig, onWiederholen, kannRueckgaengig, kannWiederholen,
    onVorschau, onSpeichern, onStandardTexte,
}: EditorToolbarProps) {
    const [bearbeiten, setBearbeiten] = useState(false);
    const [entwurf, setEntwurf] = useState('');
    const name = vorlagenName || 'Neue Vorlage';

    const starten = () => { setEntwurf(vorlagenName ?? ''); setBearbeiten(true); };
    const uebernehmen = () => {
        setBearbeiten(false);
        const neu = entwurf.trim();
        if (neu && neu !== vorlagenName) onUmbenennen(neu);
    };

    return (
        <header className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border border-slate-200 bg-white px-3 py-2 shadow-sm">
            <Button variant="ghost" size="sm" onClick={onZurueck} className="text-slate-600 hover:text-rose-700">
                <ArrowLeft aria-hidden="true" className="mr-1.5 h-4 w-4" />Alle Vorlagen
            </Button>
            <span className="h-6 w-px bg-slate-200" aria-hidden="true" />

            <div className="flex min-w-0 flex-1 items-center gap-3">
                {bearbeiten ? (
                    <input
                        autoFocus
                        aria-label="Name der Vorlage"
                        value={entwurf}
                        placeholder="z. B. Briefpapier Standard"
                        onChange={e => setEntwurf(e.target.value)}
                        onBlur={uebernehmen}
                        onKeyDown={e => {
                            if (e.key === 'Enter') { e.preventDefault(); uebernehmen(); }
                            if (e.key === 'Escape') { e.preventDefault(); setBearbeiten(false); }
                        }}
                        className="h-9 w-72 max-w-full rounded-md border border-rose-300 bg-white px-2.5 text-sm font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                ) : (
                    <button
                        type="button"
                        onClick={starten}
                        title="Vorlage umbenennen"
                        aria-label={`Vorlage „${name}“ umbenennen`}
                        className="group flex min-w-0 items-center gap-2 rounded-md px-2 py-1.5 text-left hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                    >
                        <span className="truncate text-base font-semibold text-slate-900" data-kuerzung-erlaubt>{name}</span>
                        <Pencil aria-hidden="true" className="h-3.5 w-3.5 shrink-0 text-slate-400 group-hover:text-rose-600" />
                    </button>
                )}

                <span
                    role="status"
                    data-testid="speicherstand"
                    className={cn(
                        'inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium',
                        speichert ? 'bg-slate-100 text-slate-600' : ungespeichert ? 'bg-amber-50 text-amber-800' : 'bg-emerald-50 text-emerald-700',
                    )}
                >
                    {speichert
                        ? <><Loader2 aria-hidden="true" className="h-3.5 w-3.5 motion-safe:animate-spin" />Wird gespeichert …</>
                        : ungespeichert
                            ? <><span aria-hidden="true" className="h-2 w-2 rounded-full bg-amber-500" />Ungespeicherte Änderungen</>
                            : <><CheckCircle2 aria-hidden="true" className="h-3.5 w-3.5" />Gespeichert</>}
                </span>
            </div>

            <div className="flex items-center gap-1">
                <button type="button" className={symbolKnopf} onClick={onRueckgaengig} disabled={!kannRueckgaengig} aria-label="Rückgängig" title="Rückgängig (Strg+Z)">
                    <Undo2 aria-hidden="true" className="h-4 w-4" />
                </button>
                <button type="button" className={symbolKnopf} onClick={onWiederholen} disabled={!kannWiederholen} aria-label="Wiederholen" title="Wiederholen (Strg+Y)">
                    <Redo2 aria-hidden="true" className="h-4 w-4" />
                </button>
            </div>
            <span className="h-6 w-px bg-slate-200" aria-hidden="true" />

            <div className="flex items-center gap-2">
                <Button
                    variant="outline"
                    size="sm"
                    onClick={onStandardTexte}
                    disabled={!vorlagenName}
                    title={vorlagenName ? 'Standard-Texte je Dokumenttyp festlegen' : 'Vorlage zuerst speichern, um Standard-Texte festzulegen'}
                >
                    <FileText aria-hidden="true" className="mr-1.5 h-4 w-4" />Standard-Texte
                </Button>
                <Button variant="outline" size="sm" onClick={onVorschau}>
                    <Eye aria-hidden="true" className="mr-1.5 h-4 w-4" />Vorschau
                </Button>
                <Button size="sm" onClick={onSpeichern} disabled={speichert}>
                    <Save aria-hidden="true" className="mr-1.5 h-4 w-4" />Speichern
                </Button>
            </div>
        </header>
    );
}
