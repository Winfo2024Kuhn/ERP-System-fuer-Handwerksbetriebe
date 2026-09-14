import { useState } from 'react';
import { MoreHorizontal, Reply, Forward, Star, Trash2, FolderPlus, FolderInput, Download,
    Inbox, Newspaper, ShieldAlert, ShieldCheck, ShieldX, CheckCircle2, RotateCcw } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
    DropdownMenuSeparator, DropdownMenuLabel, DropdownMenuSub, DropdownMenuSubTrigger,
    DropdownMenuSubContent, DropdownMenuPortal } from '../../components/ui/dropdown-menu';
import { EmailRecipientDropdown } from '../../components/EmailRecipientDropdown';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { getSenderName, type EmailItem, type FolderType } from './emailCenterModel';

type Action = () => void | Promise<void>;
type MoveTarget = 'inbox' | 'trash' | 'spam' | 'newsletter';
interface Props {
    email: EmailItem; folder: FolderType; onReply: Action; onForward: Action; onAssign: Action;
    onStar: Action; onDelete: Action; onMove: (target: MoveTarget) => void | Promise<void>;
    onSpam: Action; onNotSpam: Action; onBlock: Action; onNotNewsletter: Action; onConfirmNewsletter: Action;
}

const moveTargets = [
    { id: 'inbox', label: 'Posteingang', icon: Inbox },
    { id: 'newsletter', label: 'Newsletter', icon: Newspaper },
    { id: 'spam', label: 'Spam', icon: ShieldAlert },
    { id: 'trash', label: 'Papierkorb', icon: Trash2 },
] as const;

/** Häufige Aktionen bleiben sichtbar; weitere Aktionen öffnen ein Portal mit Tastatursteuerung. */
export function EmailDetailHeader({ email, folder, onReply, onForward, onAssign, onStar, onDelete,
    onMove, onSpam, onNotSpam, onBlock, onNotNewsletter, onConfirmNewsletter }: Props) {
    const [busy, setBusy] = useState(false);
    const toast = useToast();
    const run = async (action: Action) => {
        if (busy) return;
        setBusy(true);
        try { await action(); }
        catch { toast.error('Die E-Mail-Aktion konnte nicht abgeschlossen werden.'); }
        finally { setBusy(false); }
    };
    const date = email.sentAt ? new Date(email.sentAt).toLocaleString('de-DE', {
        day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
    }) : '';
    return <header data-testid="email-detail-header" aria-busy={busy}
        className="shrink-0 border-b border-slate-200 bg-white px-4 py-3">
        <div className="flex items-start justify-between gap-3">
            <h2 className="min-w-0 flex-1 break-words text-base font-semibold leading-6 text-slate-900">
                {email.subject || '(Kein Betreff)'}
            </h2>
            <div className="flex shrink-0 items-center gap-1">
                {folder !== 'trash' && <Button variant="outline" size="sm" onClick={() => void run(onReply)}
                    disabled={busy} className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 h-8 rounded-lg border-slate-200 text-slate-700 gap-1.5">
                    <Reply className="h-4 w-4" />Antworten
                </Button>}
                <Button variant="ghost" size="sm" onClick={() => void run(onStar)} disabled={busy}
                    title={email.isStarred ? 'Markierung entfernen' : 'Markieren'} aria-label={email.isStarred ? 'Markierung entfernen' : 'Markieren'}
                    className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 h-8 w-8 rounded-lg p-0 text-slate-500">
                    <Star className={cn('h-4 w-4', email.isStarred && 'fill-amber-400 text-amber-500')} />
                </Button>
                <DropdownMenu modal={false}>
                    <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="sm" disabled={busy} aria-label="Weitere E-Mail-Aktionen"
                            title="Weitere E-Mail-Aktionen" className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 h-8 w-8 rounded-lg p-0 text-slate-600">
                            <MoreHorizontal className="h-5 w-5" />
                        </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" collisionPadding={12} className="w-60 rounded-xl p-1.5 shadow-lg">
                        <DropdownMenuItem onSelect={() => void run(onForward)}><Forward />Weiterleiten</DropdownMenuItem>
                        {folder !== 'sent' && <DropdownMenuItem onSelect={() => void run(onAssign)}><FolderPlus />Zuordnen</DropdownMenuItem>}
                        {(email.attachments?.filter(a => !a.inline).length ?? 0) > 1 &&
                            <DropdownMenuItem asChild><a href={`/api/emails/${email.id}/attachments/download-all`} download>
                                <Download />Alle Anhänge herunterladen
                            </a></DropdownMenuItem>}
                        {folder !== 'sent' && <>
                            <DropdownMenuSeparator />
                            <DropdownMenuSub>
                                <DropdownMenuSubTrigger><FolderInput />Verschieben nach</DropdownMenuSubTrigger>
                                <DropdownMenuPortal><DropdownMenuSubContent collisionPadding={12}>
                                    {moveTargets.filter(target => target.id !== folder).map(target =>
                                        <DropdownMenuItem key={target.id} onSelect={() => void run(() => onMove(target.id))}>
                                            <target.icon />{target.label}
                                        </DropdownMenuItem>)}
                                </DropdownMenuSubContent></DropdownMenuPortal>
                            </DropdownMenuSub>
                            {folder === 'trash' && <DropdownMenuItem onSelect={() => void run(() => onMove('inbox'))}>
                                <RotateCcw />Wiederherstellen
                            </DropdownMenuItem>}
                            {folder === 'spam' && <DropdownMenuItem onSelect={() => void run(onNotSpam)}><ShieldCheck />Kein Spam</DropdownMenuItem>}
                            {folder !== 'spam' && folder !== 'trash' && <DropdownMenuItem onSelect={() => void run(onSpam)}><ShieldX />Als Spam markieren</DropdownMenuItem>}
                            {folder === 'newsletter' && <>
                                <DropdownMenuItem onSelect={() => void run(onNotNewsletter)}><Inbox />Kein Newsletter</DropdownMenuItem>
                                <DropdownMenuItem onSelect={() => void run(onConfirmNewsletter)}><CheckCircle2 />Newsletter bestätigen</DropdownMenuItem>
                            </>}
                            <DropdownMenuItem onSelect={() => void run(onBlock)}><ShieldAlert />Absender sperren</DropdownMenuItem>
                        </>}
                        <DropdownMenuSeparator />
                        <DropdownMenuItem onSelect={() => void run(onDelete)} className="text-rose-700 focus:bg-rose-50 focus:text-rose-800">
                            <Trash2 />{folder === 'trash' ? 'Endgültig löschen' : 'In Papierkorb'}
                        </DropdownMenuItem>
                        {busy && <DropdownMenuLabel>Wird ausgeführt…</DropdownMenuLabel>}
                    </DropdownMenuContent>
                </DropdownMenu>
            </div>
        </div>
        <div className="mt-2 flex min-w-0 items-baseline justify-between gap-3 text-xs">
            <span className="min-w-0 break-words font-medium text-slate-700" title={email.fromAddress}>{getSenderName(email)}</span>
            <time className="shrink-0 text-slate-400" dateTime={email.sentAt}>{date}</time>
        </div>
        <EmailRecipientDropdown recipients={email.recipient} cc={email.cc} className="mt-1 max-w-full" />
    </header>;
}
