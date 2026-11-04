import type React from 'react';
import { useDraggable } from '@dnd-kit/core';
import { FileEdit, MessagesSquare, Paperclip, ShieldAlert, Star, Trash2 } from 'lucide-react';
import { cn } from '../../lib/utils';
import { formatListenDatum, type DraftItem, type EmailItem } from './emailCenterModel';
import { EmailOrdnerChip } from './EmailOrdnerChip';
import { EmailZuordnungLink } from './EmailZuordnungLink';
import { ermittleEmailZuordnung } from './emailZuordnung';
import { PostfachSchild } from './PostfachSchild';
import { getThreadPreview } from './threadQuotes';

export interface EmailListenZeileProps {
    email: EmailItem;
    /** Gegenstelle des Verlaufs bzw. Absender/Empfänger. */
    name: string;
    ausgewaehlt: boolean;
    ungelesen: boolean;
    /** Schild mit dem Ablageort zeigen (Suche in allen Ordnern). */
    zeigeOrdner: boolean;
    /** Im Papierkorb löscht der Knopf endgültig. */
    imPapierkorb: boolean;
    /** Auf einen Ordner links ziehbar. */
    ziehbar: boolean;
    /** Offener Antwort-Entwurf zu dieser Mail. */
    entwurf?: DraftItem;
    onOeffnen: (ereignis: React.MouseEvent, email: EmailItem) => void;
    onStern: (email: EmailItem) => void;
    onLoeschen: (email: EmailItem) => void;
    onEntwurf: (entwurf: DraftItem) => void;
}

/** Knöpfe in der Zeile dürfen die Mail nicht zusätzlich öffnen. */
const nurKnopf = (aktion: () => void) => (ereignis: React.MouseEvent) => {
    ereignis.stopPropagation();
    ereignis.preventDefault();
    aktion();
};

const KNOPF = 'shrink-0 rounded p-0.5 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500';

function SpamSchild({ wert }: { wert: number }) {
    return <div className={cn('flex items-center gap-1 text-xs px-1.5 py-0.5 rounded',
        wert >= 90 ? 'text-red-700 bg-red-100 border border-red-200 font-semibold'
            : wert >= 50 ? 'text-orange-700 bg-orange-50 border border-orange-200'
                : 'text-slate-500 bg-slate-50 border border-slate-200')}>
        <ShieldAlert className="w-3 h-3" />
        {wert}% Spam
    </div>;
}

function Schilder({ email, zeigeOrdner, entwurf, onEntwurf }: Pick<EmailListenZeileProps, 'email' | 'zeigeOrdner' | 'entwurf' | 'onEntwurf'>) {
    return <div className="flex gap-2 mt-2 flex-wrap">
        {zeigeOrdner && <EmailOrdnerChip folder={email.folder} />}
        {email.replyCount != null && email.replyCount > 0 && (
            <div className="flex items-center gap-1 text-xs font-semibold text-white bg-rose-600 px-2 py-0.5 rounded-full shadow-sm"
                title={`${email.replyCount + 1} Nachrichten in diesem Verlauf`}>
                <MessagesSquare className="w-3 h-3" />
                {email.replyCount + 1} Nachrichten
            </div>
        )}
        {email.attachments && email.attachments.length > 0 && (
            <div className="flex items-center gap-1 text-xs text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                <Paperclip className="w-3 h-3" />
                {email.attachments.length}
            </div>
        )}
        {email.spamScore != null && email.spamScore > 0 && <SpamSchild wert={email.spamScore} />}
        <PostfachSchild postfaecher={email.postfaecher} />
        <EmailZuordnungLink zuordnung={ermittleEmailZuordnung(email)} variante="liste" />
        {entwurf && (
            <button type="button" onClick={nurKnopf(() => onEntwurf(entwurf))} title="Entwurf öffnen" aria-label="Entwurf öffnen"
                className="flex items-center gap-1 text-xs font-semibold text-amber-700 bg-amber-100 px-2 py-0.5 rounded-full border border-amber-200 hover:bg-amber-200 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                <FileEdit className="w-3 h-3" />
                Entwurf
            </button>
        )}
    </div>;
}

/**
 * Eine Zeile der Mail-Liste: Name, Betreff, Vorschau, Schilder – dazu Stern und Papierkorb.
 * Der Papierkorb-Knopf erscheint beim Drüberfahren, bei Tastaturfokus und an der gewählten Zeile;
 * er bleibt dabei immer per Tab erreichbar.
 */
export function EmailListenZeile(props: EmailListenZeileProps) {
    const { email, name, ausgewaehlt, ungelesen, imPapierkorb, ziehbar, onOeffnen, onStern, onLoeschen } = props;
    const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
        id: `email-${email.id}`, data: { emailId: email.id }, disabled: !ziehbar,
    });
    const loeschLabel = imPapierkorb ? 'Endgültig löschen (Entf)' : 'In den Papierkorb (Entf)';
    const sternLabel = email.isStarred ? 'Markierung entfernen' : 'Markieren';
    return (
        <div ref={setNodeRef} {...attributes} {...listeners} className={cn('outline-none', isDragging && 'opacity-40')}>
            <div onClick={(ereignis) => onOeffnen(ereignis, email)} data-testid="email-listenzeile"
                className={cn('group px-4 py-3 cursor-pointer transition-colors border-l-[3px]',
                    ausgewaehlt ? 'bg-rose-50/80 border-l-rose-500'
                        : ungelesen ? 'bg-white border-l-rose-400 hover:bg-rose-50/30'
                            : 'bg-white border-l-transparent hover:bg-slate-50')}>
                <div className="flex items-start justify-between gap-2 mb-1">
                    <p className={cn('text-sm truncate', ungelesen ? 'font-bold text-slate-900' : 'font-medium text-slate-700')}>{name}</p>
                    <div className="flex items-center gap-1.5 shrink-0">
                        <button type="button" onClick={nurKnopf(() => onStern(email))} title={sternLabel} aria-label={sternLabel}
                            className={cn(KNOPF, 'hover:bg-amber-50')}>
                            <Star className={cn('w-3.5 h-3.5 transition-colors',
                                email.isStarred ? 'fill-amber-400 text-amber-400' : 'text-slate-300 hover:text-amber-400')} />
                        </button>
                        <button type="button" onClick={nurKnopf(() => onLoeschen(email))} title={loeschLabel} aria-label={loeschLabel}
                            className={cn(KNOPF, 'text-slate-400 hover:bg-red-50 hover:text-red-600 opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 focus-visible:opacity-100',
                                ausgewaehlt && 'opacity-100')}>
                            <Trash2 className="w-3.5 h-3.5" />
                        </button>
                        <span className="text-xs text-slate-400 whitespace-nowrap">{formatListenDatum(email.sentAt)}</span>
                    </div>
                </div>
                <p className={cn('text-sm mb-1 truncate', ungelesen ? 'font-semibold text-slate-800' : 'text-slate-600')}>
                    {email.subject || '(Kein Betreff)'}
                </p>
                <p className="text-xs text-slate-400 line-clamp-2">
                    {getThreadPreview(email.htmlBody || email.body || '') || 'Kein neuer Nachrichtentext'}
                </p>
                <Schilder {...props} />
            </div>
        </div>
    );
}
