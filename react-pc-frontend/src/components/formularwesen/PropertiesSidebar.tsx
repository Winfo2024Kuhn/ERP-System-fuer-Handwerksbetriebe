import { useRef } from 'react';
import { AlignHorizontalJustifyCenter, AlignHorizontalJustifyEnd, AlignHorizontalJustifyStart, ArrowDownToLine, ArrowUpToLine, Bold, Copy, Trash2, X } from 'lucide-react';
import { Button } from '../ui/button';
import { ColorInput } from '../ui/color-input';
import { cn } from '../../lib/utils';
import type { FormBlock, FormBlockStyles } from '../../types';
import { MIN_HEIGHT, MIN_WIDTH, PAGE_FORMATS, PLACEHOLDER_MAP } from './constants';
import { pdfStil, stilMitAenderung } from './pdfStil';
import { MassFeld } from './MassFeld';
import { Gruppe } from './Gruppe';
import { bausteinName } from './bausteinVorlagen';
import { BausteinSymbol } from './BausteinSymbol';
import { formatiereMm, mmZuPt, mmZuPtGerundet, ptZuMm } from './normMasse';

interface PropertiesSidebarProps {
    block: FormBlock;
    onAendern: (id: string, updates: Partial<FormBlock>, gruppe?: string) => void;
    onLoeschen: (id: string) => void;
    onDuplizieren: () => void;
    onEbene: (id: string, richtung: 'vorne' | 'hinten') => void;
    onSchliessen: () => void;
}

const FELD_TYPEN = ['doknr', 'projektnr', 'kundennummer', 'kunde', 'adresse', 'dokumenttyp', 'datum', 'seitenzahl'];
/** Ränder nach DIN 5008: links 25 mm, rechts 20 mm. */
const AUSRICHTUNGEN = [
    { name: 'Links', titel: 'An den linken Rand (25 mm)', Symbol: AlignHorizontalJustifyStart, x: () => Math.round(mmZuPt(25) * 100) / 100 },
    { name: 'Mitte', titel: 'In die Seitenmitte', Symbol: AlignHorizontalJustifyCenter, x: (breite: number) => Math.round((PAGE_FORMATS.A4.width - breite) / 2 * 100) / 100 },
    { name: 'Rechts', titel: 'An den rechten Rand (20 mm)', Symbol: AlignHorizontalJustifyEnd, x: (breite: number) => Math.round((PAGE_FORMATS.A4.width - mmZuPt(20) - breite) * 100) / 100 },
];
const SEGMENT = 'inline-flex h-8 w-9 items-center justify-center transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500';

/** Rechte Spalte, wenn ein Baustein gewählt ist: Lage in mm, Inhalt, Schrift, Anordnen. */
export default function PropertiesSidebar({ block, onAendern, onLoeschen, onDuplizieren, onEbene, onSchliessen }: PropertiesSidebarProps) {
    const textRef = useRef<HTMLTextAreaElement>(null);
    const stil = pdfStil(block);
    const istText = block.type === 'text' || block.type === 'heading';
    const istFeld = FELD_TYPEN.includes(block.type);
    const hatSchrift = istText || istFeld;

    const lage = (feld: 'x' | 'y' | 'width' | 'height') => (mm: number) => onAendern(block.id, { [feld]: mmZuPtGerundet(mm) });
    const stilAendern = (aenderung: Partial<FormBlockStyles>) => onAendern(block.id, { styles: stilMitAenderung(block, aenderung) });

    const platzhalterEinfuegen = (platzhalter: string) => {
        const feld = textRef.current;
        const text = block.content ?? '';
        const start = feld?.selectionStart ?? text.length;
        const ende = feld?.selectionEnd ?? text.length;
        onAendern(block.id, { content: text.slice(0, start) + platzhalter + text.slice(ende) });
        requestAnimationFrame(() => {
            feld?.focus();
            feld?.setSelectionRange(start + platzhalter.length, start + platzhalter.length);
        });
    };

    return (
        <aside aria-label="Eigenschaften des Bausteins" className="flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center gap-3 border-b border-slate-100 px-4 py-3">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-rose-100 text-rose-600">
                    <BausteinSymbol block={block} className="h-5 w-5" />
                </span>
                <div className="min-w-0 flex-1">
                    <h2 className="text-sm font-semibold text-slate-900">{bausteinName(block)}</h2>
                    <p className="text-xs text-slate-500">{block.page === 1 ? 'Seite 1' : 'Folgeseiten'}</p>
                </div>
                <button
                    type="button"
                    onClick={onSchliessen}
                    aria-label="Auswahl aufheben"
                    title="Auswahl aufheben (Esc)"
                    className="inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                >
                    <X aria-hidden="true" className="h-4 w-4" />
                </button>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto">
                <Gruppe titel="Position & Größe">
                    <div className="grid grid-cols-2 gap-x-2 gap-y-3">
                        <MassFeld label="Von links" einheit="mm" anzeige={formatiereMm(block.x)} min={-50} max={210} onUebernehmen={lage('x')} />
                        <MassFeld label="Von oben" einheit="mm" anzeige={formatiereMm(block.y)} min={-50} max={297} onUebernehmen={lage('y')} />
                        <MassFeld label="Breite" einheit="mm" anzeige={formatiereMm(block.width)} min={Math.ceil(ptZuMm(MIN_WIDTH))} max={210} onUebernehmen={lage('width')} />
                        <MassFeld label="Höhe" einheit="mm" anzeige={formatiereMm(block.height)} min={Math.ceil(ptZuMm(MIN_HEIGHT))} max={297} onUebernehmen={lage('height')} />
                    </div>
                </Gruppe>

                {istText && (
                    <Gruppe titel="Inhalt">
                        <label htmlFor="baustein-inhalt" className="sr-only">Text des Bausteins</label>
                        <textarea
                            id="baustein-inhalt"
                            ref={textRef}
                            value={block.content ?? ''}
                            onChange={e => onAendern(block.id, { content: e.target.value }, `inhalt-${block.id}`)}
                            rows={4}
                            placeholder="Text eingeben …"
                            className="w-full resize-y rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-rose-400 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                        <p className="mb-2 mt-3 text-[11px] font-medium text-slate-500">Platzhalter einfügen – das Programm setzt die echten Werte ein</p>
                        <div className="flex flex-wrap gap-1">
                            {Object.keys(PLACEHOLDER_MAP).filter(p => p !== '{{BEZUGSDOKUMENTNUMMER}}').map(p => (
                                <button
                                    key={p}
                                    type="button"
                                    onClick={() => platzhalterEinfuegen(p)}
                                    title={`Beispiel: ${PLACEHOLDER_MAP[p].replace(/\n/g, ', ')}`}
                                    className="rounded-md bg-rose-50 px-1.5 py-0.5 font-mono text-[10px] text-rose-700 transition-colors hover:bg-rose-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                >
                                    {p.replace(/[{}]/g, '').toLowerCase()}
                                </button>
                            ))}
                        </div>
                    </Gruppe>
                )}

                {istFeld && (
                    <Gruppe titel="Inhalt">
                        <p className="text-sm text-slate-600">Dieses Feld füllt das Programm beim Erstellen eines Dokuments automatisch.</p>
                    </Gruppe>
                )}

                {block.type === 'table' && (
                    <Gruppe titel="Positionen">
                        <p className="text-sm text-slate-600">
                            In diesem Bereich stehen im fertigen Dokument die Positionen. Reicht er nicht, geht es auf der
                            nächsten Seite im Positionen-Bereich der Folgeseiten weiter.
                        </p>
                    </Gruppe>
                )}

                {hatSchrift && (
                    <Gruppe titel="Schrift">
                        {stil.betreff ? (
                            <p className="text-sm text-slate-600">Der Betreff steht im Dokument immer in der festen Betreff-Schrift (11 pt, halbfett).</p>
                        ) : (
                            <div className="grid grid-cols-[1fr_auto_auto] items-end gap-2">
                                <MassFeld
                                    label="Größe"
                                    einheit="pt"
                                    anzeige={String(stil.schriftgroesse).replace('.', ',')}
                                    min={5}
                                    max={72}
                                    onUebernehmen={groesse => stilAendern({ fontSize: groesse })}
                                />
                                <div>
                                    <span className="mb-1 block text-[11px] font-medium text-slate-500">Fett</span>
                                    <button
                                        type="button"
                                        aria-pressed={stil.fett}
                                        aria-label="Fett"
                                        title="Fett"
                                        onClick={() => stilAendern({ fontWeight: stil.fett ? '400' : '700' })}
                                        className={cn(SEGMENT, 'rounded-md border', stil.fett ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50')}
                                    >
                                        <Bold aria-hidden="true" className="h-4 w-4" />
                                    </button>
                                </div>
                                <div>
                                    <span className="mb-1 block text-[11px] font-medium text-slate-500">Farbe</span>
                                    <ColorInput value={stil.farbe} onChange={farbe => stilAendern({ color: farbe })} aria-label="Schriftfarbe" className="[&>button]:h-8" />
                                </div>
                            </div>
                        )}
                    </Gruppe>
                )}

                <Gruppe titel="Auf der Seite ausrichten">
                    <div className="grid grid-cols-3 gap-2">
                        {AUSRICHTUNGEN.map(({ name, titel, Symbol, x }) => (
                            <Button key={name} size="sm" variant="secondary" title={titel} aria-label={titel} onClick={() => onAendern(block.id, { x: x(block.width) })}>
                                <Symbol aria-hidden="true" className="mr-1 h-4 w-4" />{name}
                            </Button>
                        ))}
                    </div>
                </Gruppe>

                <Gruppe titel="Anordnen">
                    <div className="grid grid-cols-2 gap-2">
                        <Button size="sm" variant="secondary" onClick={() => onEbene(block.id, 'vorne')}>
                            <ArrowUpToLine aria-hidden="true" className="mr-1.5 h-4 w-4" />Nach vorn
                        </Button>
                        <Button size="sm" variant="secondary" onClick={() => onEbene(block.id, 'hinten')}>
                            <ArrowDownToLine aria-hidden="true" className="mr-1.5 h-4 w-4" />Nach hinten
                        </Button>
                    </div>
                </Gruppe>
            </div>

            <div className="flex gap-2 border-t border-slate-100 p-3">
                <Button size="sm" variant="outline" className="flex-1" onClick={onDuplizieren}>
                    <Copy aria-hidden="true" className="mr-1.5 h-4 w-4" />Duplizieren
                </Button>
                <Button size="sm" variant="outline" className="flex-1 border-red-200 text-red-600 hover:bg-red-50" onClick={() => onLoeschen(block.id)}>
                    <Trash2 aria-hidden="true" className="mr-1.5 h-4 w-4" />Löschen
                </Button>
            </div>
        </aside>
    );
}
