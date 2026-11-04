import { useId } from 'react';
import { AlertTriangle, ArrowDownLeft, ArrowUpRight, Mail, MessagesSquare, Paperclip } from 'lucide-react';
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

/**
 * Eine Zeile der Mailliste im E-Mail-Reiter (Kunde, Projekt, Anfrage, Lieferant).
 * Echter Knopf (Tab, Enter, Leertaste); Name ist der Betreff, der Rest die Beschreibung.
 * Innen nur `span`, weil ein Knopf keine Block-Elemente enthalten darf.
 */
export function EmailsTabListItem({ email, preview, onOpen }: EmailsTabListItemProps) {
    const isThread = email._replyCount > 0;
    const mitAnhang = !!email.attachments?.some(a => !a.inline);
    const betreffId = useId();
    const chipsId = useId();
    const detailsId = useId();
    return (
        <button
            type="button"
            aria-labelledby={betreffId}
            aria-describedby={`${chipsId} ${detailsId}`}
            className={cn(
                // Ring innen: Die Reiter-Inhalte scrollen in einem overflow-Container, der einen äußeren Ring abschneiden würde.
                "block w-full text-left rounded-xl border bg-white transition-colors cursor-pointer group relative overflow-hidden",
                "focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500 focus-visible:border-rose-500",
                isThread ? "border-rose-200 hover:border-rose-400 shadow-sm" : "border-slate-200 hover:border-rose-300",
            )}
            onClick={() => onOpen(email)}
        >
            {/* Linker Akzent-Balken für Verläufe */}
            {isThread && <span aria-hidden="true" className="absolute left-0 top-0 bottom-0 w-1 bg-rose-400" />}
            <span className={cn("flex items-start justify-between gap-4 p-4", isThread && "pl-5")}>
                <span className="block flex-1 min-w-0">
                    <EmailsTabListItemChips id={chipsId} email={email} isThread={isThread} />
                    <span id={betreffId} className="block font-medium text-slate-900 truncate">{email.subject || '(kein Betreff)'}</span>
                    <span id={detailsId} className="block">
                        <span className="block text-sm text-slate-500 truncate">
                            {email.direction === 'IN' ? 'Von: ' : 'An: '}
                            {email.from || email.fromAddress || email.sender || 'Unbekannt'}
                        </span>
                        {(email.bodyHtml || email.bodyPreview || email.body) && (
                            <span className="block text-sm text-slate-500 mt-1 line-clamp-2">{preview}</span>
                        )}
                        {mitAnhang && <span className="sr-only">mit Anhang</span>}
                        {/* Rückläufer-Warnung: Ohne diesen Hinweis sieht eine nie
                            angekommene Mail exakt aus wie eine erfolgreich versendete –
                            der Fehler fällt im Alltag sonst gar nicht auf. */}
                        {email.zustellStatus === 'UNZUSTELLBAR' && (
                            <EmailsTabNichtAngekommen zustellFehler={email.zustellFehler} />
                        )}
                    </span>
                </span>
                <span aria-hidden="true" className="flex flex-col items-end gap-2 shrink-0">
                    {isThread
                        ? <MessagesSquare className="w-5 h-5 text-rose-300 group-hover:text-rose-500 transition-colors" />
                        : <Mail className="w-5 h-5 text-slate-300 group-hover:text-rose-400 transition-colors" />
                    }
                    {mitAnhang && <Paperclip className="w-4 h-4 text-slate-400" />}
                </span>
            </span>
        </button>
    );
}

function EmailsTabListItemChips({ id, email, isThread }: { id: string; email: ThreadRootEmail; isThread: boolean }) {
    const RichtungsIcon = email.direction === 'IN' ? ArrowDownLeft : ArrowUpRight;
    return (
        <span id={id} className="flex items-center gap-2 mb-1.5 flex-wrap">
            <span className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full bg-slate-100 text-slate-700">
                <RichtungsIcon aria-hidden="true" className="w-3 h-3" />
                {email.direction === 'IN' ? 'Eingang' : 'Ausgang'}
            </span>
            {isThread && (
                <span
                    className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-700 ring-1 ring-rose-200"
                    title={`${email._replyCount + 1} Nachrichten in diesem Verlauf`}
                >
                    <MessagesSquare aria-hidden="true" className="w-3 h-3" />
                    {email._replyCount + 1} Nachrichten
                </span>
            )}
            <span className="text-xs text-slate-500">{formatEmailDate(email)}</span>
        </span>
    );
}

function EmailsTabNichtAngekommen({ zustellFehler }: { zustellFehler?: string }) {
    return (
        <span className="mt-2 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2">
            <AlertTriangle aria-hidden="true" className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
            <span className="block min-w-0">
                <span className="block text-sm font-semibold text-rose-700">Nicht angekommen</span>
                <span className="block text-xs text-rose-600 break-words" title={zustellFehler}>
                    {klartextGrund(zustellFehler)}
                </span>
            </span>
        </span>
    );
}
