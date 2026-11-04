import type React from 'react';
import { FileEdit, MessagesSquare, Trash2 } from 'lucide-react';
import { htmlZuEinzeiler } from '../../lib/htmlSanitizer';
import { formatListenDatum, fremderEntwurfVon, type DraftItem } from './emailCenterModel';

interface EmailEntwurfZeileProps {
    entwurf: DraftItem;
    /** Eigener Anzeigename – fremde Entwürfe (nur Admins sehen sie) bekommen „von …“. */
    eigenerName: string | null;
    onOeffnen: (entwurf: DraftItem) => void;
    onLoeschen: (entwurfId: number, ereignis: React.MouseEvent) => void;
}

/** Eine Zeile im Ordner „Entwürfe“. */
export function EmailEntwurfZeile({ entwurf, eigenerName, onOeffnen, onLoeschen }: EmailEntwurfZeileProps) {
    const fremd = fremderEntwurfVon(entwurf, eigenerName);
    return (
        <div onClick={() => onOeffnen(entwurf)}
            className="px-4 py-3 cursor-pointer transition-colors border-l-[3px] bg-amber-50/50 border-l-amber-400 hover:bg-amber-50">
            <div className="flex items-start justify-between gap-2 mb-1">
                <p className="text-sm font-semibold text-slate-800 truncate">{entwurf.recipient?.trim() || 'Kein Empfänger'}</p>
                <div className="flex items-center gap-1 shrink-0">
                    <button type="button" onClick={(e) => onLoeschen(entwurf.id, e)} title="Entwurf löschen" aria-label="Entwurf löschen"
                        className="p-0.5 rounded hover:bg-red-50 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                        <Trash2 className="w-3.5 h-3.5 text-slate-300 hover:text-red-500" />
                    </button>
                    <span className="text-xs text-slate-400 whitespace-nowrap">{formatListenDatum(entwurf.updatedAt)}</span>
                </div>
            </div>
            <p className="text-sm mb-1 truncate font-medium text-slate-700">{entwurf.subject || '(Kein Betreff)'}</p>
            {fremd && <p className="text-xs text-slate-500 mb-1 truncate">von {fremd}</p>}
            <p className="text-xs text-slate-400 line-clamp-2">{(htmlZuEinzeiler(entwurf.body) || '...').substring(0, 150)}</p>
            <div className="flex gap-2 mt-2 flex-wrap">
                <div className="flex items-center gap-1 text-xs font-semibold text-amber-700 bg-amber-100 px-2 py-0.5 rounded-full border border-amber-200">
                    <FileEdit className="w-3 h-3" />
                    Entwurf
                </div>
                {entwurf.replyEmailId && (
                    <div className="flex items-center gap-1 text-xs text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                        <MessagesSquare className="w-3 h-3" />
                        Mit Verlauf verknüpft
                    </div>
                )}
            </div>
        </div>
    );
}
