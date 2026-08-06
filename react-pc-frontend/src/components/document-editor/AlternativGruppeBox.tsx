import { useState } from 'react';
import { Diamond, Unlink } from 'lucide-react';
import { Button } from '../ui/button';

interface AlternativGruppeBoxProps {
    name: string;
    isLocked: boolean;
    onUmbenennen: (alt: string, neu: string) => void;
    onAufloesen: (name: string) => void;
    children: React.ReactNode;
}

/**
 * Rahmen um die Varianten einer Entweder-Oder-Gruppe.
 *
 * Rein visuell: die Zusammengehoerigkeit steckt im `alternativGruppe`-Feld der
 * einzelnen Bloecke, nicht in einem Container-Block. Das haelt das gespeicherte
 * positionenJson zwei Ebenen flach — tiefer liest weder das Backend noch die
 * Freigabe-Seite.
 */
export function AlternativGruppeBox({
    name, isLocked, onUmbenennen, onAufloesen, children,
}: AlternativGruppeBoxProps) {
    const [editing, setEditing] = useState(false);
    const [localName, setLocalName] = useState(name);

    const uebernehmen = () => {
        setEditing(false);
        const neu = localName.trim();
        if (neu && neu !== name) onUmbenennen(name, neu);
        else setLocalName(name);
    };

    return (
        <div className="border-2 border-amber-300 bg-amber-50/60 rounded-xl p-2 mb-2">
            <div className="flex items-center gap-2 px-1 pb-2">
                <Diamond className="w-3 h-3 text-amber-600 flex-shrink-0" />
                <span className="text-[10px] font-bold uppercase tracking-wider text-amber-700">
                    Kunde wählt genau eines
                </span>
                <div className="ml-auto flex items-center gap-1">
                    {editing && !isLocked ? (
                        <input
                            type="text"
                            value={localName}
                            autoFocus
                            aria-label="Name der Auswahl"
                            onChange={(e) => setLocalName(e.target.value)}
                            onBlur={uebernehmen}
                            onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur(); }}
                            className="text-xs font-semibold text-amber-900 bg-white border border-amber-300 rounded px-2 py-0.5 focus:outline-none focus:ring-2 focus:ring-amber-400/30"
                        />
                    ) : (
                        <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); if (!isLocked) setEditing(true); }}
                            title={isLocked ? undefined : 'Auswahl umbenennen'}
                            className="text-xs font-semibold text-amber-900 hover:text-amber-700 hover:underline"
                        >
                            {name}
                        </button>
                    )}
                    {!isLocked && (
                        <Button
                            variant="ghost"
                            size="sm"
                            aria-label="Auswahl auflösen"
                            title="Auswahl auflösen — die Varianten werden zu Optional-Positionen"
                            onClick={(e) => { e.stopPropagation(); onAufloesen(name); }}
                            className="h-6 w-6 p-0 text-amber-500 hover:text-amber-700 hover:bg-amber-100 rounded"
                        >
                            <Unlink className="w-3 h-3" />
                        </Button>
                    )}
                </div>
            </div>
            <div className="space-y-1.5">{children}</div>
        </div>
    );
}
