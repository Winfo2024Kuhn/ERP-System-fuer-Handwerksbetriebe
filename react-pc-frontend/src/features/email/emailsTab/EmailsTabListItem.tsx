import { AlertTriangle, Mail, MessagesSquare, Paperclip } from 'lucide-react';
import { klartextGrund } from '../../../lib/zustellGrund';
import { cn } from '../../../lib/utils';
import { formatEmailDate } from './emailsTabFormat';
import type { ThreadRootEmail } from './emailsTabThreadRoots';

interface EmailsTabListItemProps {
    email: ThreadRootEmail;
    /** Vorschauzeile ohne zitierten Verlauf, einmal je Mailliste im Reiter berechnet. */
    preview: string | undefined;
    onOpen: (email: ThreadRootEmail) => void;
}

/** Eine Zeile der Mailliste im E-Mail-Reiter (Kunde, Projekt, Anfrage, Lieferant). */
export function EmailsTabListItem({ email, preview, onOpen }: EmailsTabListItemProps) {
    const isThread = email._replyCount > 0;
    return (
        <div
            className={cn(
                "rounded-xl border transition-all cursor-pointer group relative overflow-hidden",
                isThread
                    ? "bg-white border-rose-200 hover:border-rose-400 hover:shadow-md shadow-sm"
                    : "bg-white border-slate-200 hover:border-rose-300 hover:shadow-md"
            )}
            onClick={() => onOpen(email)}
        >
            {/* Linker Akzent-Balken für Threads */}
            {isThread && (
                <div className="absolute left-0 top-0 bottom-0 w-1 bg-rose-400" />
            )}
            <div className={cn("p-4", isThread && "pl-5")}>
                <div className="flex items-start justify-between gap-4">
                    <div className="flex items-start gap-3 flex-1 min-w-0">
                        <div className="flex-1 min-w-0">
                            <EmailsTabListItemChips email={email} isThread={isThread} />
                            <h4 className="font-medium text-slate-900 truncate">{email.subject || '(kein Betreff)'}</h4>
                            <p className="text-sm text-slate-500 truncate">
                                {email.direction === 'IN' ? 'Von: ' : 'An: '}
                                {email.from || email.fromAddress || email.sender || 'Unbekannt'}
                            </p>
                            {(email.bodyHtml || email.bodyPreview || email.body) && (
                                <p className="text-sm text-slate-400 mt-1 line-clamp-2">{preview}</p>
                            )}
                            {/* Rueckläufer-Warnung: Ohne diesen Hinweis sieht eine nie
                                angekommene Mail exakt aus wie eine erfolgreich versendete —
                                der Fehler faellt im Alltag sonst gar nicht auf. */}
                            {email.zustellStatus === 'UNZUSTELLBAR' && (
                                <EmailsTabNichtAngekommen zustellFehler={email.zustellFehler} />
                            )}
                        </div>
                    </div>
                    <div className="flex flex-col items-end gap-2 shrink-0">
                        {isThread
                            ? <MessagesSquare className="w-5 h-5 text-rose-300 group-hover:text-rose-500 transition-colors" />
                            : <Mail className="w-5 h-5 text-slate-300 group-hover:text-rose-400 transition-colors" />
                        }
                        {email.attachments && email.attachments.filter(a => !a.inline).length > 0 && (
                            <Paperclip className="w-4 h-4 text-slate-400" />
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}

function EmailsTabListItemChips({ email, isThread }: { email: ThreadRootEmail; isThread: boolean }) {
    return (
        <div className="flex items-center gap-2 mb-1.5 flex-wrap">
            <span className={cn(
                "text-xs font-medium px-2 py-0.5 rounded-full",
                email.direction === 'IN' ? "bg-blue-50 text-blue-700" : "bg-green-50 text-green-700"
            )}>
                {email.direction === 'IN' ? 'Eingang' : 'Ausgang'}
            </span>
            {isThread && (
                <span
                    className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-rose-600 text-white shadow-sm"
                    title={`${email._replyCount + 1} Nachrichten in diesem Thread`}
                >
                    <MessagesSquare className="w-3 h-3" />
                    {email._replyCount + 1} Nachrichten
                </span>
            )}
            <span className="text-xs text-slate-400">{formatEmailDate(email)}</span>
        </div>
    );
}

function EmailsTabNichtAngekommen({ zustellFehler }: { zustellFehler?: string }) {
    return (
        <div className="mt-2 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2">
            <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
            <div className="min-w-0">
                <p className="text-sm font-semibold text-rose-700">Nicht angekommen</p>
                <p className="text-xs text-rose-600 break-words" title={zustellFehler}>
                    {klartextGrund(zustellFehler)}
                </p>
            </div>
        </div>
    );
}
