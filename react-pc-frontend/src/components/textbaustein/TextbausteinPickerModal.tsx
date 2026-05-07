import { Search, FileText, X, Plus, Star, Eye } from 'lucide-react';
import { useMemo, useState } from 'react';
import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN } from '../../types';
import type { AusgangsGeschaeftsDokumentTyp } from '../../types';

/** Minimal-Form eines Textbausteins, wie ihn der Picker braucht. */
export interface TextbausteinPickerItem {
    id: number;
    name: string;
    typ?: string;
    beschreibung?: string;
    html?: string;
    dokumenttypen?: string[];
}

function stripHtml(html: string): string {
    let text = html;
    let prev: string;
    do { prev = text; text = text.replace(/<[^>]*>/g, ''); } while (text !== prev);
    return text.trim();
}

function HoverPreview({ text, visible, anchorRect }: { text: string; visible: boolean; anchorRect: DOMRect | null }) {
    if (!visible || !text || !anchorRect) return null;
    const plain = stripHtml(text);
    if (!plain) return null;

    const viewportH = window.innerHeight;
    const spaceBelow = viewportH - anchorRect.bottom;
    const spaceAbove = anchorRect.top;
    let top: number, maxHeight: number;
    if (spaceBelow >= 120 || spaceBelow >= spaceAbove) {
        top = anchorRect.bottom + 6;
        maxHeight = Math.min(spaceBelow - 16, 250);
    } else {
        maxHeight = Math.min(spaceAbove - 16, 250);
        top = Math.max(8, anchorRect.top - 6 - maxHeight);
    }
    const left = anchorRect.left;

    return (
        <div
            className="fixed z-[100] bg-white border border-slate-200 shadow-xl rounded-xl p-3 text-xs text-slate-600 leading-relaxed overflow-hidden pointer-events-none animate-in fade-in zoom-in-95 duration-150"
            style={{ top, left, maxHeight, width: Math.min(400, window.innerWidth - left - 24) }}
        >
            <div className="flex items-center gap-1.5 mb-1.5 text-[10px] font-semibold text-rose-500 uppercase tracking-wide">
                <Eye className="w-3 h-3" /> Vorschau
            </div>
            <div className="whitespace-pre-wrap break-words line-clamp-[12]">{plain}</div>
        </div>
    );
}

export interface TextbausteinPickerModalProps<T extends TextbausteinPickerItem> {
    textbausteine: T[];
    onSelect: (tb: T) => void;
    onClose: () => void;
    /** Wenn gesetzt, werden zugehoerige Bausteine als "Empfohlen fuer ..." hervorgehoben. */
    dokumentTyp?: AusgangsGeschaeftsDokumentTyp;
    /** Optionaler Titel-Override (z.B. "Vortext einfuegen"). */
    title?: string;
    /** Optional: vorhandene IDs ausblenden (wenn der Picker zum Hinzufuegen einer Liste dient). */
    hideIds?: ReadonlySet<number>;
}

/**
 * Wiederverwendbarer Picker fuer Textbausteine mit Suche, Empfehlung pro Dokumenttyp
 * und Hover-Preview. Wird vom DocumentEditor und FormularwesenEditor genutzt.
 */
export function TextbausteinPickerModal<T extends TextbausteinPickerItem>({
    textbausteine,
    onSelect,
    onClose,
    dokumentTyp,
    title,
    hideIds,
}: TextbausteinPickerModalProps<T>) {
    const [search, setSearch] = useState('');

    const dokumentTypLabel = dokumentTyp
        ? AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp
        : null;

    const { matching, rest } = useMemo(() => {
        let items = textbausteine;
        if (hideIds && hideIds.size > 0) {
            items = items.filter(tb => !hideIds.has(tb.id));
        }
        if (search) {
            const q = search.toLowerCase();
            items = items.filter(tb =>
                tb.name.toLowerCase().includes(q) || (tb.beschreibung || '').toLowerCase().includes(q)
            );
        }

        if (!dokumentTypLabel) {
            return { matching: [] as T[], rest: items };
        }

        const matchGroup: T[] = [];
        const restGroup: T[] = [];
        items.forEach(tb => {
            const hasMatch = tb.dokumenttypen?.some((dt: string) =>
                dt.toLowerCase() === dokumentTyp!.toLowerCase() ||
                dt.toLowerCase() === dokumentTypLabel.toLowerCase()
            );
            if (hasMatch) matchGroup.push(tb); else restGroup.push(tb);
        });
        return { matching: matchGroup, rest: restGroup };
    }, [textbausteine, search, dokumentTyp, dokumentTypLabel, hideIds]);

    const totalCount = matching.length + rest.length;
    const [hoveredTb, setHoveredTb] = useState<{ id: number; rect: DOMRect } | null>(null);
    const hoveredTbData = hoveredTb ? [...matching, ...rest].find(tb => tb.id === hoveredTb.id) : null;
    const hoveredTbText = hoveredTbData?.html || hoveredTbData?.beschreibung || '';

    const renderItem = (tb: T, isRecommended: boolean) => (
        <button
            key={tb.id}
            type="button"
            onClick={() => onSelect(tb)}
            onMouseEnter={(e) => setHoveredTb({ id: tb.id, rect: e.currentTarget.getBoundingClientRect() })}
            onMouseLeave={() => setHoveredTb(null)}
            className={`w-full group p-3 text-left rounded-xl transition-all duration-150 cursor-pointer ${
                isRecommended
                    ? 'bg-rose-50/60 hover:bg-rose-50 border border-rose-200 hover:border-rose-300'
                    : 'bg-white hover:bg-rose-50 border border-slate-200 hover:border-rose-200'
            }`}
        >
            <div className="flex items-start gap-3">
                <div className={`mt-0.5 p-1.5 rounded-lg transition-colors flex-shrink-0 ${
                    isRecommended
                        ? 'bg-rose-100 group-hover:bg-rose-200'
                        : 'bg-slate-100 group-hover:bg-rose-100'
                }`}>
                    <FileText className={`w-3.5 h-3.5 ${
                        isRecommended ? 'text-rose-500' : 'text-slate-400 group-hover:text-rose-500'
                    }`} />
                </div>
                <div className="min-w-0 flex-1">
                    <span className={`text-sm font-medium block truncate ${
                        isRecommended
                            ? 'text-rose-700 group-hover:text-rose-800'
                            : 'text-slate-700 group-hover:text-rose-700'
                    }`}>
                        {tb.name}
                    </span>
                    {tb.beschreibung && (
                        <span className="text-xs text-slate-400 block truncate mt-0.5">
                            {stripHtml(tb.beschreibung).slice(0, 80)}
                        </span>
                    )}
                </div>
                <Plus className="w-4 h-4 text-slate-300 group-hover:text-rose-400 flex-shrink-0 mt-0.5 opacity-0 group-hover:opacity-100 transition-opacity" />
            </div>
        </button>
    );

    return (
        <div className="fixed inset-0 z-[60] bg-black/40 backdrop-blur-sm flex items-center justify-center" onClick={onClose}>
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-4 border border-slate-100 flex flex-col max-h-[80vh] animate-in fade-in zoom-in-95 duration-200"
                onClick={e => e.stopPropagation()}
            >
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100 flex-shrink-0">
                    <div className="flex items-center gap-2.5">
                        <div className="p-2 bg-rose-50 rounded-xl">
                            <FileText className="w-4 h-4 text-rose-600" />
                        </div>
                        <h3 className="text-base font-bold text-slate-900">{title ?? 'Textbaustein einfügen'}</h3>
                    </div>
                    <button
                        type="button"
                        aria-label="Schließen"
                        onClick={onClose}
                        className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                    >
                        <X className="w-4 h-4 text-slate-400" />
                    </button>
                </div>

                <div className="px-5 pt-3 pb-2 flex-shrink-0">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                        <input
                            type="text"
                            placeholder="Textbaustein suchen…"
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                            autoFocus
                            className="w-full pl-10 pr-4 py-2.5 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 placeholder:text-slate-400 transition-all"
                        />
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto px-5 pb-4 min-h-0">
                    {totalCount === 0 ? (
                        <div className="py-10 text-center">
                            <Search className="w-10 h-10 text-slate-200 mx-auto mb-2" />
                            <p className="text-sm text-slate-400">{search ? 'Keine Ergebnisse' : 'Keine Textbausteine vorhanden'}</p>
                        </div>
                    ) : (
                        <>
                            {matching.length > 0 && (
                                <div className="mb-3">
                                    <div className="flex items-center gap-2 mb-2 px-1">
                                        <Star className="w-3.5 h-3.5 text-rose-500" />
                                        <span className="text-xs font-semibold text-rose-600 uppercase tracking-wide">
                                            Empfohlen für {dokumentTypLabel}
                                        </span>
                                    </div>
                                    <div className="space-y-1.5">
                                        {matching.map(tb => renderItem(tb, true))}
                                    </div>
                                </div>
                            )}
                            {matching.length > 0 && rest.length > 0 && (
                                <div className="flex items-center gap-3 my-3 px-1">
                                    <div className="flex-1 h-px bg-slate-200" />
                                    <span className="text-xs text-slate-400 font-medium">Weitere Textbausteine</span>
                                    <div className="flex-1 h-px bg-slate-200" />
                                </div>
                            )}
                            {rest.length > 0 && (
                                <div className="space-y-1.5">
                                    {rest.map(tb => renderItem(tb, false))}
                                </div>
                            )}
                        </>
                    )}
                </div>
                <HoverPreview text={hoveredTbText} visible={hoveredTb !== null} anchorRect={hoveredTb?.rect ?? null} />
            </div>
        </div>
    );
}

export default TextbausteinPickerModal;
