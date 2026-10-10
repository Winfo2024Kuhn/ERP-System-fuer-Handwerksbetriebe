import { useCallback, useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, CircleSlash, Clock, Eye, Inbox, Mail, Pencil, Plus, RefreshCw, Trash2, Users } from 'lucide-react';
import { Button } from '../../../ui/button';
import { useToast } from '../../../ui/toast';
import { useConfirm } from '../../../ui/confirm-dialog';
import { SectionLoading, SettingsCard } from '../../settingsUi';
import { parseErrorMessage } from '../../settingsApi';
import { cn } from '../../../../lib/utils';
import {
    beschreibeAbruf, beschreibeSichtbarkeit, immerFuerAlleSichtbar, parsePostfaecher, type AbrufStatusArt, type PostfachDto,
} from '../../../../features/email/postfach';
import { PostfachDialog } from './PostfachDialog';

const statusStil: Record<AbrufStatusArt, { klasse: string; icon: typeof CheckCircle2 }> = {
    ok: { klasse: 'text-emerald-700', icon: CheckCircle2 },
    fehler: { klasse: 'text-rose-700 font-medium', icon: AlertCircle },
    aus: { klasse: 'text-slate-500', icon: CircleSlash },
    unvollstaendig: { klasse: 'text-slate-500', icon: CircleSlash },
    wartet: { klasse: 'text-slate-500', icon: Clock },
};

type DialogZustand = { offen: false } | { offen: true; postfach: PostfachDto | null; schluessel: number };

/**
 * Einstellungen → E-Mail → Postfächer: alle Mail-Konten des Betriebs in einer
 * Liste, mit Abruf-Status und den Benutzern, denen sie gehören.
 *
 * <p>Ersetzt die früheren Kästen „Ihr Postfach“ und „Postfach für Rechnungen
 * und Mahnungen“ – beides sind jetzt normale Postfächer mit einem Haken.</p>
 */
export function PostfachSettings({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const confirm = useConfirm();
    const [postfaecher, setPostfaecher] = useState<PostfachDto[]>([]);
    const [laedt, setLaedt] = useState(true);
    const [ladeFehler, setLadeFehler] = useState<string | null>(null);
    const [dialog, setDialog] = useState<DialogZustand>({ offen: false });
    const [loeschtId, setLoeschtId] = useState<number | null>(null);

    const laden = useCallback(async (mitSpinner = true) => {
        if (mitSpinner) setLaedt(true);
        try {
            const res = await fetch('/api/postfaecher');
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Postfächer konnten nicht geladen werden.'));
            const daten = await res.json();
            setPostfaecher(parsePostfaecher(daten));
            setLadeFehler(null);
        } catch (err) {
            const text = err instanceof Error && err.message ? err.message : 'Postfächer konnten nicht geladen werden.';
            setLadeFehler(text);
            toast.error(text);
        } finally {
            setLaedt(false);
        }
    }, [toast]);

    useEffect(() => { void laden(); }, [laden]);

    const hauptpostfach = postfaecher.find(p => p.hauptpostfach);

    const oeffneDialog = (postfach: PostfachDto | null) =>
        setDialog({ offen: true, postfach, schluessel: Date.now() });

    const nachSpeichern = async () => {
        setDialog({ offen: false });
        // Neu laden: Ein neues Hauptpostfach oder Rechnungs-Postfach nimmt den anderen den Haken.
        await laden(false);
        onSaved?.();
    };

    const handleLoeschen = async (postfach: PostfachDto) => {
        const ok = await confirm({
            title: 'Postfach löschen',
            message: `Postfach ${postfach.emailAdresse} wirklich löschen? Benutzer, denen es zugeordnet ist, verlieren die Zuordnung.`,
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ok) return;
        setLoeschtId(postfach.id);
        try {
            const res = await fetch(`/api/postfaecher/${postfach.id}`, { method: 'DELETE' });
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'Postfach konnte nicht gelöscht werden.'));
                return;
            }
            toast.success('Postfach gelöscht.');
            await laden(false);
            onSaved?.();
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen. Postfach wurde nicht gelöscht.');
        } finally {
            setLoeschtId(null);
        }
    };

    if (laedt) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<Mail className="h-5 w-5 text-rose-600" aria-hidden="true" />}
            title="Postfächer"
            description={
                <p>
                    Jedes Postfach ist ein echtes E-Mail-Konto Ihres Betriebs, z. B. info@, rechnungen@
                    oder max@. Das System holt neue Mails aus allen eingeschalteten Postfächern ab und
                    verschickt aus ihnen. Wer welches Postfach als eigenes hat, legen Sie bei den
                    Benutzern fest.
                </p>
            }
        >
            <div className="mb-4 flex justify-end">
                <Button size="sm" onClick={() => oeffneDialog(null)}>
                    <Plus className="h-4 w-4" />
                    Neues Postfach
                </Button>
            </div>

            {ladeFehler ? (
                <div role="alert" className="flex flex-col items-start gap-3 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 sm:flex-row sm:items-center sm:justify-between">
                    <span className="flex items-center gap-2"><AlertCircle className="h-4 w-4" aria-hidden="true" />{ladeFehler}</span>
                    <Button variant="outline" size="sm" onClick={() => void laden()}>
                        <RefreshCw className="h-4 w-4" />
                        Erneut laden
                    </Button>
                </div>
            ) : postfaecher.length === 0 ? (
                <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-slate-300 px-6 py-10 text-center">
                    <Inbox className="h-8 w-8 text-slate-400" aria-hidden="true" />
                    <p className="font-medium text-slate-700">Noch kein Postfach eingerichtet.</p>
                    <p className="max-w-md text-sm text-slate-500">
                        Legen Sie zuerst Ihr Hauptpostfach an, z. B. info@ihre-firma.de. Darüber laufen
                        alle Mails, für die kein anderes Postfach passt.
                    </p>
                </div>
            ) : (
                <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200" aria-label="Postfächer">
                    {postfaecher.map(postfach => {
                        const status = beschreibeAbruf(postfach);
                        const StatusIcon = statusStil[status.art].icon;
                        const sichtbar = beschreibeSichtbarkeit(postfach);
                        return (
                            <li key={postfach.id} className={cn('flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between',
                                !postfach.aktiv && 'bg-slate-50/70')}>
                                <div className="min-w-0 flex-1 space-y-1">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <span className="break-all font-semibold text-slate-900">{postfach.emailAdresse}</span>
                                        {postfach.hauptpostfach && (
                                            <span className="rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-xs font-medium text-rose-700">
                                                Hauptpostfach
                                            </span>
                                        )}
                                        {postfach.fuerGeschaeftsdokumente && (
                                            <span className="rounded-full border border-slate-200 bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                                                Rechnungen &amp; Mahnungen
                                            </span>
                                        )}
                                        {postfach.laeuftAus && (
                                            <span className="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-600"
                                                title="Läuft aus: Mails kommen weiter an, Antworten gehen über das Hauptpostfach raus, für neue Mails nicht mehr wählbar.">
                                                läuft aus
                                            </span>
                                        )}
                                    </div>
                                    {postfach.anzeigename && <p className="text-sm text-slate-600">{postfach.anzeigename}</p>}
                                    <p className={cn('flex items-start gap-1.5 text-xs', statusStil[status.art].klasse)}>
                                        <StatusIcon className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                                        <span>{status.text}</span>
                                    </p>
                                    <p className="flex items-start gap-1.5 text-xs text-slate-500">
                                        <Users className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                                        <span>
                                            {postfach.zugewieseneBenutzer.length > 0
                                                ? postfach.zugewieseneBenutzer.map(b => b.displayName).join(', ')
                                                : 'Keinem Benutzer als eigenes Postfach zugeordnet'}
                                        </span>
                                    </p>
                                    <p className="flex items-start gap-1.5 text-xs text-slate-500" title={sichtbar.vollstaendig}
                                        data-testid="postfach-sichtbarkeit">
                                        <Eye className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                                        <span className="min-w-0">
                                            Sichtbar: {sichtbar.text}
                                            {sichtbar.weitere > 0 && (
                                                <>
                                                    <span aria-hidden="true" className="ml-1 rounded-full bg-slate-100 px-1.5 py-px font-medium text-slate-600">
                                                        +{sichtbar.weitere}
                                                    </span>
                                                    <span className="sr-only">, insgesamt: {sichtbar.vollstaendig}</span>
                                                </>
                                            )}
                                            {!immerFuerAlleSichtbar(postfach) && (
                                                <>
                                                    {' · '}
                                                    <a href="#berechtigungen"
                                                        aria-label={`Sichtbarkeit von ${postfach.emailAdresse} unter Berechtigungen ändern`}
                                                        className="font-medium text-rose-700 underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 rounded">
                                                        ändern
                                                    </a>
                                                </>
                                            )}
                                        </span>
                                    </p>
                                </div>
                                <div className="flex shrink-0 gap-1">
                                    <Button variant="ghost" size="sm" onClick={() => oeffneDialog(postfach)}
                                        aria-label={`Postfach ${postfach.emailAdresse} bearbeiten`} title="Bearbeiten">
                                        <Pencil className="h-4 w-4" />
                                    </Button>
                                    <Button variant="ghost" size="sm" onClick={() => void handleLoeschen(postfach)}
                                        disabled={postfach.hauptpostfach || loeschtId === postfach.id}
                                        aria-label={`Postfach ${postfach.emailAdresse} löschen`}
                                        title={postfach.hauptpostfach
                                            ? 'Das Hauptpostfach kann nicht gelöscht werden. Zuerst ein anderes als Hauptpostfach markieren.'
                                            : 'Löschen'}
                                        className="text-slate-500 hover:bg-rose-50 hover:text-rose-700">
                                        <Trash2 className="h-4 w-4" />
                                    </Button>
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}

            {dialog.offen && (
                <PostfachDialog
                    key={dialog.schluessel}
                    open
                    postfach={dialog.postfach}
                    vorlage={hauptpostfach}
                    erstesPostfach={postfaecher.length === 0}
                    anzahlPostfaecher={postfaecher.length}
                    onClose={() => setDialog({ offen: false })}
                    onSaved={() => void nachSpeichern()}
                />
            )}
        </SettingsCard>
    );
}
