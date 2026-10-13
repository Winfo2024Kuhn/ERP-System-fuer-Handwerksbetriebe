import { Layers } from 'lucide-react';
import type { FormBlock } from '../../types';
import { cn } from '../../lib/utils';
import { BAUSTEIN_ZIEH_TYP, BAUSTEINE, bausteinName, type BausteinArt } from './bausteinVorlagen';
import { BausteinSymbol } from './BausteinSymbol';

interface BlocksSidebarProps {
    items: FormBlock[];
    seite: number;
    ausgewaehltId: string | null;
    onHinzufuegen: (art: BausteinArt) => void;
    onAuswaehlen: (id: string) => void;
}

/** Kurzer Text, woran man einen Baustein in der Ebenen-Liste wiedererkennt. */
function vorschauText(block: FormBlock): string {
    if (block.type === 'table') return 'Leistungstabelle';
    if (block.type === 'logo') return 'Bild';
    const text = (block.content ?? '').replace(/\{\{(\w+)\}\}/g, (_t, name: string) => name.toLowerCase()).replace(/\s+/g, ' ').trim();
    return text || 'leer';
}

/** Linke Spalte: Bausteine zum Hinzufügen (anklicken oder auf die Seite ziehen) und die Ebenen der Seite. */
export default function BlocksSidebar({ items, seite, ausgewaehltId, onHinzufuegen, onAuswaehlen }: BlocksSidebarProps) {
    const ebenen = items.filter(i => i.page === seite).sort((a, b) => b.z - a.z);

    return (
        <aside aria-label="Bausteine und Ebenen" className="flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-100 px-4 py-3">
                <h2 className="text-sm font-semibold text-slate-900">Bausteine</h2>
                <p className="mt-0.5 text-xs text-slate-500">Anklicken oder auf die Seite ziehen</p>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto">
                <ul className="grid grid-cols-2 gap-2 p-3">
                    {BAUSTEINE.map(({ art, titel, beschreibung, Icon }) => (
                        <li key={art}>
                            <button
                                type="button"
                                draggable
                                onDragStart={e => {
                                    e.dataTransfer.setData(BAUSTEIN_ZIEH_TYP, art);
                                    e.dataTransfer.effectAllowed = 'copy';
                                }}
                                onClick={() => onHinzufuegen(art)}
                                title={beschreibung}
                                aria-label={`${titel} hinzufügen`}
                                className="group flex h-full w-full cursor-grab flex-col items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2 py-3 text-center transition-colors hover:border-rose-300 hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 active:cursor-grabbing"
                            >
                                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-600 transition-colors group-hover:bg-rose-100 group-hover:text-rose-600">
                                    <Icon aria-hidden="true" className="h-5 w-5" />
                                </span>
                                <span className="text-xs font-semibold leading-tight text-slate-700 group-hover:text-rose-700">{titel}</span>
                            </button>
                        </li>
                    ))}
                </ul>

                <div className="border-t border-slate-100 px-3 pb-3 pt-3">
                    <h3 className="mb-2 flex items-center gap-1.5 px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <Layers aria-hidden="true" className="h-3.5 w-3.5" />
                        Ebenen auf {seite === 1 ? 'Seite 1' : 'Folgeseiten'}
                    </h3>
                    {ebenen.length === 0 ? (
                        <p className="px-1 text-xs text-slate-400">Noch keine Bausteine auf dieser Seite.</p>
                    ) : (
                        <ul className="space-y-0.5" aria-label="Ebenen">
                            {ebenen.map(block => {
                                const gewaehlt = block.id === ausgewaehltId;
                                return (
                                    <li key={block.id}>
                                        <button
                                            type="button"
                                            onClick={() => onAuswaehlen(block.id)}
                                            aria-pressed={gewaehlt}
                                            className={cn(
                                                'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                                                gewaehlt ? 'bg-rose-50 text-rose-700' : 'text-slate-700 hover:bg-slate-50',
                                            )}
                                        >
                                            <BausteinSymbol block={block} className={cn('h-4 w-4 shrink-0', gewaehlt ? 'text-rose-600' : 'text-slate-400')} />
                                            <span className="min-w-0 flex-1">
                                                <span className="block text-xs font-medium">{bausteinName(block)}</span>
                                                <span className="block truncate text-[11px] text-slate-400" data-kuerzung-erlaubt>{vorschauText(block)}</span>
                                            </span>
                                        </button>
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </div>
            </div>
        </aside>
    );
}
