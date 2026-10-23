import { useState, useEffect, useCallback, useRef } from 'react';
import {
    Plus,
    Trash2,
    Edit2,
    Save,
    X,
    Calendar,
    CalendarDays,
    CalendarOff,
    Clock,
    CheckCircle2,
    Hourglass,
    History,
    Mail,
    MessageSquare,
    Sparkles,
    Plane,
    Info,
    Bold,
    Italic,
    Underline,
    List,
    ListOrdered,
    Eraser,
    Inbox,
    AlertTriangle
} from 'lucide-react';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { DatePicker } from './ui/datepicker';
import { InfoAbschnitt, InfoButton } from './ui/info-button';
import { cn } from '../lib/utils';
import { htmlZuEinzeiler, sanitizeHtml } from '../lib/htmlSanitizer';
import { useToast } from './ui/toast';
import { useConfirm } from './ui/confirm-dialog';
import { formatPostfach, type PostfachKurz } from '../features/email/postfach';
import { ladeSignaturen } from '../features/email/signatur/signaturApi';
import type { SignaturDto } from '../features/email/signatur/signaturTypen';
import { SignaturVerwaltung } from '../features/email/signatur/SignaturVerwaltung';

// Types
interface OutOfOfficeEntry {
    id: number;
    title: string;
    startDate: string;
    endDate: string;
    subject: string;
    message: string;
    signatureId: number | null;
    active: boolean;
    /** Postfächer, für die die Notiz gilt – nur auf Mails dorthin wird automatisch geantwortet. */
    postfaecher: PostfachKurz[];
}

// Backend response type (different field names)
interface OutOfOfficeBackend {
    id: number;
    title: string;
    startAt: string;
    endAt: string;
    subjectTemplate: string | null;
    bodyTemplate: string | null;
    signature: { id: number } | null;
    active: boolean;
    postfaecher?: PostfachKurz[] | null;
}

/** Eintrag aus `GET /api/email/outofoffice/postfaecher` – was der Benutzer ankreuzen darf. */
interface WaehlbaresPostfach extends PostfachKurz {
    /** Das Postfach des angemeldeten Benutzers selbst. */
    eigenes: boolean;
}

/** Notiz im Formular: angehakte Postfächer als Ids. */
type OooEntwurf = Partial<OutOfOfficeEntry> & { postfachIds: number[] };

type PostfachLadeStatus = 'laden' | 'fertig' | 'fehler';

const istPostfach = (eintrag: unknown): eintrag is PostfachKurz =>
    !!eintrag && typeof eintrag === 'object'
    && typeof (eintrag as PostfachKurz).id === 'number'
    && typeof (eintrag as PostfachKurz).emailAdresse === 'string';

/** Meldung des Servers (`message`) – sonst der Standardtext. */
const fehlerTextAus = async (res: Response, standard: string): Promise<string> => {
    try {
        const body = await res.json() as { message?: unknown };
        if (typeof body?.message === 'string' && body.message.trim()) return body.message;
    } catch {
        // Antwort ohne Inhalt: es bleibt beim Standardtext
    }
    return standard;
};

// Aktuelles Frontend-Profil aus localStorage holen
const FRONTEND_USER_STORAGE_KEY = 'frontendUserSelection';

interface FrontendUserSelection {
    id: number;
    displayName: string;
}

const getCurrentFrontendUser = (): FrontendUserSelection | null => {
    try {
        const raw = localStorage.getItem(FRONTEND_USER_STORAGE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed.id === 'number') {
            return parsed as FrontendUserSelection;
        }
    } catch (err) {
        console.warn('Frontend-Profil konnte nicht gelesen werden:', err);
    }
    return null;
};

// Lädt die nutzerspezifische Standard-Signatur (FrontendUserProfile.defaultSignature)
const fetchUserDefaultSignatureHtml = async (): Promise<string> => {
    try {
        const currentUser = getCurrentFrontendUser();
        const params = new URLSearchParams();
        if (currentUser?.id) {
            params.set('frontendUserId', String(currentUser.id));
        }
        const url = params.toString()
            ? `/api/email/signatures/default?${params.toString()}`
            : '/api/email/signatures/default';
        const res = await fetch(url);
        if (res.ok && res.status !== 204) {
            const data = await res.json();
            if (data?.html) return data.html as string;
        }
    } catch (err) {
        console.error('Standard-Signatur konnte nicht geladen werden:', err);
    }
    return '';
};

// Visual chip button used to insert placeholders at cursor.
// mousedown preventDefault keeps the active editor (input or contentEditable)
// focused, so the saved selection/caret is preserved when the click fires.
function PlaceholderChip({ token, label, onClick }: { token: string; label: string; onClick: () => void }) {
    return (
        <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={onClick}
            className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-white border border-rose-200 text-rose-700 text-xs font-medium hover:bg-rose-50 hover:border-rose-300 transition-colors cursor-pointer shadow-sm"
            title={`${token} am Cursor einfügen`}
        >
            <Plus className="w-3 h-3" />
            {label}
            <code className="text-[10px] text-rose-400 font-mono">{token}</code>
        </button>
    );
}

export default function EmailSettings() {
    const toast = useToast();
    const confirmDialog = useConfirm();
    // Signatures state
    const [signatures, setSignatures] = useState<SignaturDto[]>([]);
    const [loadingSignatures, setLoadingSignatures] = useState(true);
    const [signaturLadeFehler, setSignaturLadeFehler] = useState(false);

    // OOO state
    const [oooEntries, setOooEntries] = useState<OutOfOfficeEntry[]>([]);
    const [loadingOoo, setLoadingOoo] = useState(true);
    const [editingOoo, setEditingOoo] = useState<OooEntwurf | null>(null);
    const [savingOoo, setSavingOoo] = useState(false);
    const [waehlbarePostfaecher, setWaehlbarePostfaecher] = useState<WaehlbaresPostfach[]>([]);
    const [postfachStatus, setPostfachStatus] = useState<PostfachLadeStatus>('laden');

    // Cursor tracking for placeholder insertion
    const subjectInputRef = useRef<HTMLInputElement>(null);
    const subjectCursorRef = useRef<number>(0);
    const messageEditorRef = useRef<HTMLDivElement>(null);
    const messageRangeRef = useRef<Range | null>(null);
    // Tracks which entry the contentEditable was last initialized for, so we
    // only set innerHTML when switching entries — never on every state change
    // (otherwise the user's caret jumps and partial edits get wiped).
    const editorInitForRef = useRef<number | string | null>(null);

    // Active tab within settings
    const [activeSection, setActiveSection] = useState<'signatures' | 'ooo'>('signatures');

    // Die hol…-Funktionen laden ohne Start-Ladeanzeige (Startwerte bzw. die load…-Varianten
    // für Knöpfe und nach dem Speichern setzen sie), damit beim Öffnen kein setState
    // synchron im Effekt läuft.

    // Signaturen laden (Benutzer: eigene, Admins: alle – das filtert das Backend)
    const holeSignaturen = useCallback(() =>
        ladeSignaturen()
            .then((liste) => {
                setSignatures(liste);
                setSignaturLadeFehler(false);
            })
            .catch((err) => {
                setSignaturLadeFehler(true);
                toast.error(err instanceof Error ? err.message : 'Signaturen konnten nicht geladen werden.');
            })
            .finally(() => {
                setLoadingSignatures(false);
            }), [toast]);

    const loadSignatures = useCallback(() => {
        setLoadingSignatures(true);
        return holeSignaturen();
    }, [holeSignaturen]);

    // Load OOO entries - map backend field names to frontend
    const holeOooEintraege = useCallback(() =>
        fetch('/api/email/outofoffice')
            .then(async (res) => {
                if (!res.ok) {
                    toast.error(await fehlerTextAus(res, 'Abwesenheitsnotizen konnten nicht geladen werden.'));
                    return;
                }
                const data: OutOfOfficeBackend[] = await res.json();
                // Map backend field names to frontend
                const mapped: OutOfOfficeEntry[] = (Array.isArray(data) ? data : []).map((item) => ({
                    id: item.id,
                    title: item.title,
                    startDate: item.startAt,
                    endDate: item.endAt,
                    subject: item.subjectTemplate || '',
                    message: item.bodyTemplate || '',
                    signatureId: item.signature?.id || null,
                    active: item.active,
                    postfaecher: Array.isArray(item.postfaecher) ? item.postfaecher.filter(istPostfach) : []
                }));
                setOooEntries(mapped);
            })
            .catch((err) => {
                console.error('Failed to load OOO entries', err);
                toast.error('Abwesenheitsnotizen konnten nicht geladen werden.');
            })
            .finally(() => {
                setLoadingOoo(false);
            }), [toast]);

    const loadOooEntries = useCallback(() => {
        setLoadingOoo(true);
        return holeOooEintraege();
    }, [holeOooEintraege]);

    // Postfächer, die der angemeldete Benutzer für Abwesenheitsnotizen ankreuzen darf.
    // Welche das sind, entscheidet allein das Backend (eigenes + allgemeine, Admins alle).
    const holeWaehlbarePostfaecher = useCallback(() =>
        fetch('/api/email/outofoffice/postfaecher')
            .then(async (res) => {
                if (!res.ok) {
                    setPostfachStatus('fehler');
                    toast.error(await fehlerTextAus(res, 'Postfächer für die Abwesenheitsnotiz konnten nicht geladen werden.'));
                    return;
                }
                const data: unknown = await res.json();
                const liste = Array.isArray(data)
                    ? data.filter(istPostfach).map((p) => ({ ...p, eigenes: (p as WaehlbaresPostfach).eigenes === true }))
                    : [];
                setWaehlbarePostfaecher(liste);
                setPostfachStatus('fertig');
            })
            .catch((err) => {
                console.error('Failed to load mailboxes for OOO', err);
                setPostfachStatus('fehler');
                toast.error('Postfächer für die Abwesenheitsnotiz konnten nicht geladen werden.');
            }), [toast]);

    const loadWaehlbarePostfaecher = useCallback(() => {
        setPostfachStatus('laden');
        return holeWaehlbarePostfaecher();
    }, [holeWaehlbarePostfaecher]);

    useEffect(() => {
        void holeSignaturen();
        void holeOooEintraege();
        void holeWaehlbarePostfaecher();
    }, [holeSignaturen, holeOooEintraege, holeWaehlbarePostfaecher]);

    // Initialize the contentEditable editor's HTML only when the entry changes,
    // not on every state update. Without this, dangerouslySetInnerHTML would
    // overwrite the user's in-progress edits and reset the caret on every keystroke.
    useEffect(() => {
        if (editingOoo === null) {
            editorInitForRef.current = null;
            return;
        }
        const editor = messageEditorRef.current;
        if (!editor) return;
        const key = editingOoo.id ?? 'new';
        if (editorInitForRef.current === key) return;
        editorInitForRef.current = key;
        editor.innerHTML = sanitizeHtml(editingOoo.message);
    }, [editingOoo]);

    // Save OOO
    const handleSaveOoo = async () => {
        if (!editingOoo?.title?.trim() || !editingOoo?.startDate || !editingOoo?.endDate) {
            toast.warning('Bitte Titel und Zeitraum angeben.');
            return;
        }
        if (editingOoo.postfachIds.length === 0) {
            toast.warning('Bitte mindestens ein Postfach auswählen.');
            return;
        }

        setSavingOoo(true);
        try {
            const payload = {
                id: editingOoo.id || null,
                title: editingOoo.title,
                startDate: editingOoo.startDate,
                endDate: editingOoo.endDate,
                subject: editingOoo.subject || 'Automatische Antwort: {{subject}}',
                message: editingOoo.message || '',
                signatureId: editingOoo.signatureId || null,
                // active=true heisst "Eintrag scharf geschaltet". Ob aktuell geantwortet wird,
                // entscheidet zusaetzlich der Datums-Filter im OutOfOfficeResponder. Abgelaufene
                // Eintraege werden vom taeglichen deactivateExpiredSchedules-Job auf false gesetzt.
                active: true,
                // Nur Postfächer, die noch wählbar sind – alte, inzwischen gesperrte fallen beim Speichern weg.
                postfachIds: editingOoo.postfachIds
            };

            const res = await fetch('/api/email/outofoffice', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                setEditingOoo(null);
                loadOooEntries();
                return;
            }
            if (res.status === 404) {
                toast.error(await fehlerTextAus(res, 'Diese Abwesenheitsnotiz gibt es nicht mehr.'));
                setEditingOoo(null);
                loadOooEntries();
                return;
            }
            toast.error(await fehlerTextAus(res, 'Abwesenheitsnotiz konnte nicht gespeichert werden.'));
        } catch (err) {
            console.error('Failed to save OOO', err);
            toast.error('Abwesenheitsnotiz konnte nicht gespeichert werden. Bitte Verbindung prüfen.');
        } finally {
            setSavingOoo(false);
        }
    };

    // Delete OOO
    const handleDeleteOoo = async (id: number) => {
        if (!await confirmDialog({ title: 'Abwesenheitsnotiz löschen', message: 'Abwesenheitsnotiz wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;

        try {
            const res = await fetch(`/api/email/outofoffice/${id}`, { method: 'DELETE' });
            if (res.ok) {
                loadOooEntries();
                return;
            }
            if (res.status === 404) {
                toast.error(await fehlerTextAus(res, 'Diese Abwesenheitsnotiz gibt es nicht mehr.'));
                loadOooEntries();
                return;
            }
            toast.error(await fehlerTextAus(res, 'Abwesenheitsnotiz konnte nicht gelöscht werden.'));
        } catch (err) {
            console.error('Failed to delete OOO', err);
            toast.error('Abwesenheitsnotiz konnte nicht gelöscht werden. Bitte Verbindung prüfen.');
        }
    };

    const eigenePostfachIds = waehlbarePostfaecher.filter((p) => p.eigenes).map((p) => p.id);
    const keinPostfachWaehlbar = postfachStatus === 'fertig' && waehlbarePostfaecher.length === 0;
    const neueNotizGesperrtGrund = postfachStatus === 'laden'
        ? 'Postfächer werden noch geladen …'
        : postfachStatus === 'fehler'
            ? 'Postfächer konnten nicht geladen werden. Bitte Seite neu laden.'
            : keinPostfachWaehlbar
                ? 'Für Sie ist kein Postfach eingerichtet. Bitte den Admin fragen.'
                : null;

    // Neue Notiz: Vorlage-Text mit Signatur, eigenes Postfach schon angehakt.
    const startNewOoo = async () => {
        if (neueNotizGesperrtGrund) return;
        const userSignatureHtml = await fetchUserDefaultSignatureHtml();
        const fallbackHtml = userSignatureHtml
            || signatures.find(s => s.defaultSignature)?.html
            || (signatures.length > 0 ? signatures[0].html : '');
        const signatureHtml = fallbackHtml
            ? `<div class="email-signature" style="margin-top: 20px; padding-top: 10px; border-top: 1px solid #ddd;">${fallbackHtml}</div>`
            : '';
        const message = `<p>Vielen Dank für Ihre Nachricht.</p>
<p>Ich bin vom {{start}} bis {{ende}} nicht erreichbar.</p>
<p>Mit freundlichen Grüßen</p>
${signatureHtml}`;
        setEditingOoo({
            title: '',
            startDate: '',
            endDate: '',
            subject: 'Automatische Antwort: {{subject}}',
            message: message,
            signatureId: null,
            postfachIds: eigenePostfachIds
        });
    };

    // Bestehende Notiz: gespeicherte Postfächer angehakt – soweit sie noch wählbar sind.
    const startEditOoo = (entry: OutOfOfficeEntry) => {
        const waehlbar = new Set(waehlbarePostfaecher.map((p) => p.id));
        setEditingOoo({
            ...entry,
            postfachIds: entry.postfaecher.map((p) => p.id).filter((id) => waehlbar.has(id))
        });
    };

    const togglePostfach = (id: number) => {
        if (!editingOoo) return;
        const an = editingOoo.postfachIds.includes(id);
        setEditingOoo({
            ...editingOoo,
            postfachIds: an
                ? editingOoo.postfachIds.filter((x) => x !== id)
                : [...editingOoo.postfachIds, id]
        });
    };

    // Gespeicherte Postfächer der bearbeiteten Notiz, die nicht mehr angeboten werden.
    const nichtMehrWaehlbar = editingOoo?.id
        ? (oooEntries.find((e) => e.id === editingOoo.id)?.postfaecher ?? [])
            .filter((p) => !waehlbarePostfaecher.some((w) => w.id === p.id))
        : [];

    // Format date for display
    const formatDate = (dateStr: string) => {
        if (!dateStr) return '-';
        return new Date(dateStr).toLocaleDateString('de-DE', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        });
    };

    // Check if OOO is currently active
    const isOooActive = (entry: OutOfOfficeEntry) => {
        const now = new Date();
        const start = new Date(entry.startDate);
        const end = new Date(entry.endDate);
        return now >= start && now <= end;
    };

    // Check if OOO is upcoming
    const isOooUpcoming = (entry: OutOfOfficeEntry) => {
        const now = new Date();
        const start = new Date(entry.startDate);
        return now < start;
    };

    // Calculates inclusive day count between two ISO dates (YYYY-MM-DD)
    const calculateDuration = (start: string, end: string): number => {
        if (!start || !end) return 0;
        const s = new Date(start);
        const e = new Date(end);
        const diff = Math.floor((e.getTime() - s.getTime()) / (1000 * 60 * 60 * 24)) + 1;
        return Math.max(0, diff);
    };

    // Days until start (negative = past)
    const daysUntilStart = (start: string): number => {
        if (!start) return 0;
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const s = new Date(start);
        s.setHours(0, 0, 0, 0);
        return Math.ceil((s.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
    };

    type OooStatus = {
        label: string;
        Icon: typeof CheckCircle2;
        accentBar: string;
        iconWrap: string;
        badge: string;
        cardBorder: string;
        cardBg: string;
    };

    const getOooStatus = (entry: OutOfOfficeEntry): OooStatus => {
        if (isOooActive(entry)) {
            return {
                label: 'Aktiv',
                Icon: CheckCircle2,
                accentBar: 'bg-emerald-500',
                iconWrap: 'bg-emerald-100 text-emerald-600 ring-4 ring-emerald-50',
                badge: 'bg-emerald-50 text-emerald-700 border-emerald-200',
                cardBorder: 'border-emerald-200',
                cardBg: 'bg-gradient-to-br from-emerald-50/60 via-white to-white'
            };
        }
        if (isOooUpcoming(entry)) {
            const days = daysUntilStart(entry.startDate);
            return {
                label: days === 0 ? 'Heute' : days === 1 ? 'Morgen' : `In ${days} Tagen`,
                Icon: Hourglass,
                accentBar: 'bg-amber-500',
                iconWrap: 'bg-amber-100 text-amber-600 ring-4 ring-amber-50',
                badge: 'bg-amber-50 text-amber-700 border-amber-200',
                cardBorder: 'border-amber-200',
                cardBg: 'bg-gradient-to-br from-amber-50/40 via-white to-white'
            };
        }
        return {
            label: 'Abgelaufen',
            Icon: History,
            accentBar: 'bg-slate-300',
            iconWrap: 'bg-slate-100 text-slate-400 ring-4 ring-slate-50',
            badge: 'bg-slate-100 text-slate-500 border-slate-200',
            cardBorder: 'border-slate-200',
            cardBg: 'bg-slate-50/60'
        };
    };

    // Inserts a placeholder string into the subject input at the last known cursor position.
    const insertSubjectPlaceholder = (placeholder: string) => {
        if (!editingOoo) return;
        const current = editingOoo.subject || '';
        const pos = Math.min(subjectCursorRef.current ?? current.length, current.length);
        const next = current.slice(0, pos) + placeholder + current.slice(pos);
        const newCaret = pos + placeholder.length;
        subjectCursorRef.current = newCaret;
        setEditingOoo({ ...editingOoo, subject: next });
        // setTimeout(0) waits for React to flush the new value into the DOM
        // before we restore focus and caret position.
        setTimeout(() => {
            const el = subjectInputRef.current;
            if (!el) return;
            el.focus();
            try {
                el.setSelectionRange(newCaret, newCaret);
            } catch {
                // setSelectionRange may throw on certain input types; ignore
            }
        }, 0);
    };

    // Inserts a placeholder into the contentEditable message editor at the
    // current (or last saved) selection range.
    const insertMessagePlaceholder = (placeholder: string) => {
        if (!editingOoo) return;
        const editor = messageEditorRef.current;
        if (!editor) return;

        const selection = window.getSelection();
        let range: Range | null = null;

        if (selection && selection.rangeCount > 0) {
            const live = selection.getRangeAt(0);
            if (editor.contains(live.startContainer)) {
                range = live;
            }
        }
        if (!range && messageRangeRef.current && editor.contains(messageRangeRef.current.startContainer)) {
            range = messageRangeRef.current;
            editor.focus();
            if (selection) {
                selection.removeAllRanges();
                selection.addRange(range);
            }
        }
        if (!range) {
            editor.focus();
            range = document.createRange();
            range.selectNodeContents(editor);
            range.collapse(false);
            if (selection) {
                selection.removeAllRanges();
                selection.addRange(range);
            }
        }

        const node = document.createTextNode(placeholder);
        range.deleteContents();
        range.insertNode(node);
        range.setStartAfter(node);
        range.setEndAfter(node);
        if (selection) {
            selection.removeAllRanges();
            selection.addRange(range);
        }
        messageRangeRef.current = range.cloneRange();
        setEditingOoo({ ...editingOoo, message: editor.innerHTML });
    };

    const saveMessageRange = () => {
        const editor = messageEditorRef.current;
        if (!editor) return;
        const selection = window.getSelection();
        if (!selection || selection.rangeCount === 0) return;
        const r = selection.getRangeAt(0);
        if (editor.contains(r.startContainer)) {
            messageRangeRef.current = r.cloneRange();
        }
    };

    // Simple rich-text formatting via document.execCommand.
    // execCommand is deprecated but still universally supported in browsers and
    // is exactly what EmailComposeForm-style native contentEditable editors use.
    const applyFormat = (command: string, value?: string) => {
        const editor = messageEditorRef.current;
        if (!editor) return;
        editor.focus();
        // Re-apply saved range if focus was lost (e.g. clicking a toolbar button).
        const selection = window.getSelection();
        if (messageRangeRef.current && editor.contains(messageRangeRef.current.startContainer)) {
            if (selection) {
                selection.removeAllRanges();
                selection.addRange(messageRangeRef.current);
            }
        }
        document.execCommand(command, false, value);
        if (editingOoo) {
            setEditingOoo({ ...editingOoo, message: editor.innerHTML });
        }
        saveMessageRange();
    };

    return (
        <div className="space-y-6">
            {/* Section Tabs */}
            <div className="flex gap-2 border-b border-slate-200 pb-2">
                <button
                    onClick={() => setActiveSection('signatures')}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition-colors flex items-center gap-2",
                        activeSection === 'signatures'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <Edit2 className="w-4 h-4" /> Signaturen
                </button>
                <button
                    onClick={() => setActiveSection('ooo')}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition-colors flex items-center gap-2",
                        activeSection === 'ooo'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <Calendar className="w-4 h-4" /> Abwesenheitsnotizen
                </button>
            </div>

            {/* Signatures Section – bleibt beim Reiterwechsel erhalten, damit ein offenes Studio nichts verliert */}
            <div hidden={activeSection !== 'signatures'}>
                <SignaturVerwaltung
                    signaturen={signatures}
                    laedt={loadingSignatures}
                    ladeFehler={signaturLadeFehler}
                    neuLaden={loadSignatures}
                />
            </div>

            {/* Out of Office Section */}
            {activeSection === 'ooo' && (
                <div className="space-y-6">
                    {/* Section header */}
                    <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4">
                        <div>
                            <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide flex items-center gap-2">
                                <Plane className="w-4 h-4" /> Abwesenheit
                            </p>
                            <h2 className="text-2xl font-bold text-slate-900">Abwesenheitsnotizen</h2>
                            <p className="text-slate-500 mt-1 text-sm">
                                Plane Urlaub und Betriebsferien — automatische Antworten werden zum Zeitraum aktiviert.
                            </p>
                        </div>
                        {!editingOoo && oooEntries.length > 0 && (
                            // span trägt den Tooltip, weil ein deaktivierter Knopf keinen eigenen zeigt
                            <span title={neueNotizGesperrtGrund ?? undefined} className="inline-flex">
                                <Button
                                    onClick={startNewOoo}
                                    disabled={!!neueNotizGesperrtGrund}
                                    className="bg-rose-600 text-white hover:bg-rose-700 shadow-sm"
                                >
                                    <Plus className="w-4 h-4 mr-2" /> Neue Abwesenheit
                                </Button>
                            </span>
                        )}
                    </div>

                    {keinPostfachWaehlbar && !editingOoo && (
                        <div
                            role="status"
                            className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"
                        >
                            <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
                            <span>Für Sie ist kein Postfach eingerichtet. Bitte den Admin fragen.</span>
                        </div>
                    )}

                    {/* OOO Edit Form */}
                    {editingOoo !== null && (
                        <Card className="overflow-hidden border-rose-200 shadow-sm">
                            {/* Form header strip */}
                            <div className="bg-gradient-to-r from-rose-600 to-rose-500 px-6 py-4 text-white">
                                <div className="flex items-center gap-3">
                                    <div className="w-10 h-10 rounded-full bg-white/20 backdrop-blur flex items-center justify-center">
                                        {editingOoo.id ? <Edit2 className="w-5 h-5" /> : <Plane className="w-5 h-5" />}
                                    </div>
                                    <div>
                                        <h3 className="text-lg font-semibold">
                                            {editingOoo.id ? 'Abwesenheit bearbeiten' : 'Neue Abwesenheit planen'}
                                        </h3>
                                        <p className="text-sm text-rose-50/90">
                                            Wird automatisch aktiviert und nach dem Endzeitpunkt deaktiviert.
                                        </p>
                                    </div>
                                </div>
                            </div>

                            <div className="p-6 space-y-6 bg-rose-50/20">
                                {/* Titel */}
                                <div>
                                    <Label className="text-sm font-semibold text-slate-700 flex items-center gap-2">
                                        <Sparkles className="w-3.5 h-3.5 text-rose-500" />
                                        Titel
                                        <code className="text-[10px] text-slate-400 font-mono font-normal">{"{{title}}"}</code>
                                    </Label>
                                    <Input
                                        value={editingOoo.title || ''}
                                        onChange={(e) => setEditingOoo({ ...editingOoo, title: e.target.value })}
                                        placeholder="z.B. Sommerurlaub, Betriebsferien Weihnachten"
                                        className="mt-1.5"
                                    />
                                    <p className="text-xs text-slate-500 mt-1.5">
                                        Wird in Betreff und Nachricht über den Platzhalter <code className="text-[11px] bg-slate-100 px-1 rounded text-rose-700">{"{{title}}"}</code> eingesetzt.
                                    </p>
                                </div>

                                {/* Zeitraum-Block */}
                                <div className="rounded-xl border border-rose-100 bg-white p-4 shadow-sm">
                                    <div className="flex items-center justify-between mb-3">
                                        <Label className="text-sm font-semibold text-slate-700 flex items-center gap-2 m-0">
                                            <CalendarDays className="w-3.5 h-3.5 text-rose-500" />
                                            Zeitraum
                                        </Label>
                                        {(() => {
                                            const dur = calculateDuration(editingOoo.startDate || '', editingOoo.endDate || '');
                                            return dur > 0 ? (
                                                <span className="text-xs bg-rose-100 text-rose-700 px-2.5 py-1 rounded-full font-medium flex items-center gap-1.5">
                                                    <Clock className="w-3 h-3" />
                                                    {dur} {dur === 1 ? 'Tag' : 'Tage'}
                                                </span>
                                            ) : null;
                                        })()}
                                    </div>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                                        <div>
                                            <Label className="text-xs text-slate-500 font-normal">Von</Label>
                                            <DatePicker
                                                value={editingOoo.startDate || ''}
                                                onChange={(v) => setEditingOoo({ ...editingOoo, startDate: v })}
                                                placeholder="Startdatum"
                                                aria-label="Startdatum"
                                                className="mt-1"
                                            />
                                        </div>
                                        <div>
                                            <Label className="text-xs text-slate-500 font-normal">Bis</Label>
                                            <DatePicker
                                                value={editingOoo.endDate || ''}
                                                onChange={(v) => setEditingOoo({ ...editingOoo, endDate: v })}
                                                placeholder="Enddatum"
                                                aria-label="Enddatum"
                                                className="mt-1"
                                            />
                                        </div>
                                    </div>
                                </div>

                                {/* Postfach-Block: für welche Postfächer gilt die Notiz? */}
                                <div
                                    role="group"
                                    aria-labelledby="ooo-postfaecher-titel"
                                    className="rounded-xl border border-rose-100 bg-white p-4 shadow-sm"
                                >
                                    <div className="flex items-center gap-1 mb-1">
                                        <p id="ooo-postfaecher-titel" className="text-sm font-semibold text-slate-700 flex items-center gap-2">
                                            <Inbox className="w-3.5 h-3.5 text-rose-500" />
                                            Gilt für diese Postfächer
                                        </p>
                                        <InfoButton titel="Für welche Postfächer?">
                                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                                <p>
                                                    Kommt im gewählten Zeitraum eine E-Mail in einem der angehakten
                                                    Postfächer an, schickt das Programm automatisch diese Abwesenheitsnotiz
                                                    zurück – und zwar aus genau diesem Postfach.
                                                </p>
                                            </InfoAbschnitt>
                                            <InfoAbschnitt titel="Was bewirkt das?">
                                                <p>
                                                    E-Mails an nicht angehakte Postfächer bekommen keine automatische Antwort.
                                                    Wählbar sind Ihr eigenes Postfach und die allgemeinen Postfächer des
                                                    Betriebs (z. B. info@) – Admins können alle Postfächer wählen.
                                                </p>
                                                <p>
                                                    Jeder Absender bekommt pro Abwesenheitsnotiz nur einmal eine automatische
                                                    Antwort. E-Mails aus den eigenen Postfächern des Betriebs bekommen keine.
                                                    Die E-Mails selbst bleiben unverändert im Posteingang.
                                                </p>
                                            </InfoAbschnitt>
                                        </InfoButton>
                                    </div>
                                    <p className="text-xs text-slate-500 mb-3">
                                        Die automatische Antwort geht nur auf E-Mails an die gewählten Postfächer – und kommt auch von dort.
                                    </p>

                                    {postfachStatus === 'laden' ? (
                                        <div className="space-y-2" role="status" aria-label="Postfächer werden geladen">
                                            <div className="h-9 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                                            <div className="h-9 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                                        </div>
                                    ) : postfachStatus === 'fehler' ? (
                                        <div className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                                            <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
                                            <span className="flex-1">Postfächer konnten nicht geladen werden.</span>
                                            <Button size="sm" variant="outline" onClick={loadWaehlbarePostfaecher}>
                                                Erneut laden
                                            </Button>
                                        </div>
                                    ) : waehlbarePostfaecher.length === 0 ? (
                                        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                                            <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
                                            <span>Für Sie ist kein Postfach eingerichtet. Bitte den Admin fragen.</span>
                                        </div>
                                    ) : (
                                        <div className="space-y-1.5">
                                            {waehlbarePostfaecher.map((postfach) => {
                                                const an = editingOoo.postfachIds.includes(postfach.id);
                                                const name = postfach.anzeigename?.trim();
                                                return (
                                                    <label
                                                        key={postfach.id}
                                                        className={cn(
                                                            'flex cursor-pointer items-center gap-3 rounded-lg border px-3 py-2 text-sm transition-colors',
                                                            an
                                                                ? 'border-rose-300 bg-rose-50/60 text-slate-900'
                                                                : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                                                        )}
                                                    >
                                                        <input
                                                            type="checkbox"
                                                            checked={an}
                                                            onChange={() => togglePostfach(postfach.id)}
                                                            className="h-4 w-4 shrink-0 rounded border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1"
                                                        />
                                                        <span className="min-w-0 flex-1">
                                                            <span className="font-medium break-all">{postfach.emailAdresse}</span>
                                                            {postfach.eigenes && (
                                                                <span className="ml-2 inline-flex items-center rounded-full bg-rose-100 px-2 py-0.5 text-xs font-medium text-rose-700">
                                                                    Ihr Postfach
                                                                </span>
                                                            )}
                                                            {name && (
                                                                <span className="block text-xs text-slate-500">{name}</span>
                                                            )}
                                                        </span>
                                                    </label>
                                                );
                                            })}
                                            {editingOoo.postfachIds.length === 0 && (
                                                <p className="flex items-center gap-1.5 pt-1 text-xs font-medium text-rose-700">
                                                    <AlertTriangle className="w-3.5 h-3.5" />
                                                    Bitte mindestens ein Postfach auswählen.
                                                </p>
                                            )}
                                        </div>
                                    )}
                                    {nichtMehrWaehlbar.length > 0 && (
                                        <p className="mt-2 text-xs text-slate-500">
                                            Nicht mehr wählbar und wird beim Speichern entfernt:{' '}
                                            {nichtMehrWaehlbar.map((p) => p.emailAdresse).join(', ')}
                                        </p>
                                    )}
                                </div>

                                {/* Betreff-Block */}
                                <div className="rounded-xl border border-rose-100 bg-white p-4 shadow-sm">
                                    <Label className="text-sm font-semibold text-slate-700 flex items-center gap-2 mb-2">
                                        <Mail className="w-3.5 h-3.5 text-rose-500" />
                                        Betreff der automatischen Antwort
                                    </Label>
                                    <Input
                                        ref={subjectInputRef}
                                        value={editingOoo.subject || ''}
                                        onChange={(e) => {
                                            subjectCursorRef.current = e.target.selectionStart ?? e.target.value.length;
                                            setEditingOoo({ ...editingOoo, subject: e.target.value });
                                        }}
                                        onSelect={(e) => {
                                            const t = e.currentTarget;
                                            subjectCursorRef.current = t.selectionStart ?? t.value.length;
                                        }}
                                        onClick={(e) => {
                                            const t = e.currentTarget;
                                            subjectCursorRef.current = t.selectionStart ?? t.value.length;
                                        }}
                                        onKeyUp={(e) => {
                                            const t = e.currentTarget;
                                            subjectCursorRef.current = t.selectionStart ?? t.value.length;
                                        }}
                                        placeholder="Automatische Antwort: {{subject}}"
                                    />
                                    <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
                                        <span className="text-xs text-slate-400 mr-1">Am Cursor einfügen:</span>
                                        <PlaceholderChip token="{{subject}}" label="Betreff" onClick={() => insertSubjectPlaceholder('{{subject}}')} />
                                        <PlaceholderChip token="{{title}}" label="Titel" onClick={() => insertSubjectPlaceholder('{{title}}')} />
                                        <PlaceholderChip token="{{start}}" label="Start" onClick={() => insertSubjectPlaceholder('{{start}}')} />
                                        <PlaceholderChip token="{{ende}}" label="Ende" onClick={() => insertSubjectPlaceholder('{{ende}}')} />
                                    </div>
                                </div>

                                {/* Nachricht-Block */}
                                <div className="rounded-xl border border-rose-100 bg-white p-4 shadow-sm">
                                    <Label className="text-sm font-semibold text-slate-700 flex items-center gap-2 mb-2">
                                        <MessageSquare className="w-3.5 h-3.5 text-rose-500" />
                                        Nachricht
                                    </Label>
                                    <div className="border border-slate-200 rounded-lg bg-white overflow-hidden focus-within:border-rose-300 focus-within:ring-2 focus-within:ring-rose-200 transition-colors">
                                        {/* Mini-Toolbar */}
                                        <div className="flex flex-wrap gap-1 items-center bg-rose-50 border-b border-rose-100 px-2 py-1.5">
                                            <button
                                                type="button"
                                                onMouseDown={(e) => e.preventDefault()}
                                                onClick={() => applyFormat('bold')}
                                                className="p-1.5 rounded text-rose-700 hover:bg-rose-100 transition-colors"
                                                title="Fett (Ctrl+B)"
                                            >
                                                <Bold className="w-4 h-4" />
                                            </button>
                                            <button
                                                type="button"
                                                onMouseDown={(e) => e.preventDefault()}
                                                onClick={() => applyFormat('italic')}
                                                className="p-1.5 rounded text-rose-700 hover:bg-rose-100 transition-colors"
                                                title="Kursiv (Ctrl+I)"
                                            >
                                                <Italic className="w-4 h-4" />
                                            </button>
                                            <button
                                                type="button"
                                                onMouseDown={(e) => e.preventDefault()}
                                                onClick={() => applyFormat('underline')}
                                                className="p-1.5 rounded text-rose-700 hover:bg-rose-100 transition-colors"
                                                title="Unterstrichen (Ctrl+U)"
                                            >
                                                <Underline className="w-4 h-4" />
                                            </button>
                                            <span className="w-px h-5 bg-rose-200 mx-1" />
                                            <button
                                                type="button"
                                                onMouseDown={(e) => e.preventDefault()}
                                                onClick={() => applyFormat('insertUnorderedList')}
                                                className="p-1.5 rounded text-rose-700 hover:bg-rose-100 transition-colors"
                                                title="Aufzählung"
                                            >
                                                <List className="w-4 h-4" />
                                            </button>
                                            <button
                                                type="button"
                                                onMouseDown={(e) => e.preventDefault()}
                                                onClick={() => applyFormat('insertOrderedList')}
                                                className="p-1.5 rounded text-rose-700 hover:bg-rose-100 transition-colors"
                                                title="Nummerierte Liste"
                                            >
                                                <ListOrdered className="w-4 h-4" />
                                            </button>
                                            <span className="w-px h-5 bg-rose-200 mx-1" />
                                            <button
                                                type="button"
                                                onMouseDown={(e) => e.preventDefault()}
                                                onClick={() => applyFormat('removeFormat')}
                                                className="p-1.5 rounded text-rose-700 hover:bg-rose-100 transition-colors"
                                                title="Formatierung entfernen"
                                            >
                                                <Eraser className="w-4 h-4" />
                                            </button>
                                        </div>
                                        {/* Editor — uncontrolled contentEditable preserves complex HTML
                                            (signature tables, inline styles) without normalization.
                                            Mirrors the EmailComposeForm pattern. */}
                                        <div
                                            ref={messageEditorRef}
                                            className="p-4 min-h-[260px] max-h-[480px] overflow-auto outline-none prose prose-sm max-w-none"
                                            contentEditable
                                            suppressContentEditableWarning
                                            onMouseUp={saveMessageRange}
                                            onKeyUp={saveMessageRange}
                                            onInput={(e) => {
                                                if (editingOoo) {
                                                    setEditingOoo({ ...editingOoo, message: e.currentTarget.innerHTML });
                                                }
                                            }}
                                            onBlur={(e) => {
                                                saveMessageRange();
                                                if (editingOoo) {
                                                    setEditingOoo({ ...editingOoo, message: e.currentTarget.innerHTML });
                                                }
                                            }}
                                        />
                                    </div>
                                    <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
                                        <span className="text-xs text-slate-400 mr-1">Am Cursor einfügen:</span>
                                        <PlaceholderChip token="{{start}}" label="Start" onClick={() => insertMessagePlaceholder('{{start}}')} />
                                        <PlaceholderChip token="{{ende}}" label="Ende" onClick={() => insertMessagePlaceholder('{{ende}}')} />
                                        <PlaceholderChip token="{{title}}" label="Titel" onClick={() => insertMessagePlaceholder('{{title}}')} />
                                    </div>
                                    <div className="mt-3 flex gap-2 items-start text-xs text-slate-500 bg-slate-50 rounded-lg p-2.5 border border-slate-100">
                                        <Info className="w-3.5 h-3.5 mt-0.5 flex-shrink-0 text-slate-400" />
                                        <span>Platzhalter werden in der gesendeten E-Mail automatisch durch Datum, Betreff und Titel ersetzt.</span>
                                    </div>
                                </div>

                                {/* Actions */}
                                <div className="flex gap-2 pt-2">
                                    <Button
                                        onClick={handleSaveOoo}
                                        disabled={savingOoo}
                                        className="bg-rose-600 text-white hover:bg-rose-700 shadow-sm"
                                    >
                                        <Save className="w-4 h-4 mr-2" />
                                        {savingOoo ? 'Speichern...' : (editingOoo.id ? 'Änderungen speichern' : 'Abwesenheit planen')}
                                    </Button>
                                    <Button
                                        variant="outline"
                                        onClick={() => setEditingOoo(null)}
                                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                    >
                                        <X className="w-4 h-4 mr-2" /> Abbrechen
                                    </Button>
                                </div>
                            </div>
                        </Card>
                    )}

                    {/* OOO List */}
                    {loadingOoo ? (
                        <div className="flex items-center justify-center gap-3 py-12 text-slate-500">
                            <div className="w-5 h-5 border-2 border-rose-200 border-t-rose-600 rounded-full animate-spin" />
                            <span>Lade Abwesenheitsnotizen...</span>
                        </div>
                    ) : oooEntries.length === 0 && !editingOoo ? (
                        <Card className="p-12 text-center border-dashed border-2 border-rose-200 bg-rose-50/30">
                            <div className="w-16 h-16 mx-auto rounded-full bg-rose-100 flex items-center justify-center mb-4">
                                <CalendarOff className="w-8 h-8 text-rose-500" />
                            </div>
                            <h3 className="text-lg font-semibold text-slate-900 mb-1">Noch keine Abwesenheit geplant</h3>
                            <p className="text-sm text-slate-500 mb-5 max-w-md mx-auto">
                                Plane jetzt deinen Urlaub oder Betriebsferien. Während der Abwesenheit
                                bekommen Kunden automatisch eine freundliche Antwort.
                            </p>
                            <span title={neueNotizGesperrtGrund ?? undefined} className="inline-flex">
                                <Button
                                    onClick={startNewOoo}
                                    disabled={!!neueNotizGesperrtGrund}
                                    className="bg-rose-600 text-white hover:bg-rose-700 shadow-sm"
                                >
                                    <Plus className="w-4 h-4 mr-2" /> Erste Abwesenheit planen
                                </Button>
                            </span>
                        </Card>
                    ) : oooEntries.length > 0 ? (
                        <div className="space-y-3">
                            {[...oooEntries]
                                .sort((a, b) => {
                                    // Active first, then upcoming (sorted by start), then expired (sorted descending)
                                    const aActive = isOooActive(a) ? 0 : isOooUpcoming(a) ? 1 : 2;
                                    const bActive = isOooActive(b) ? 0 : isOooUpcoming(b) ? 1 : 2;
                                    if (aActive !== bActive) return aActive - bActive;
                                    if (aActive === 2) return new Date(b.startDate).getTime() - new Date(a.startDate).getTime();
                                    return new Date(a.startDate).getTime() - new Date(b.startDate).getTime();
                                })
                                .map((entry) => {
                                    const status = getOooStatus(entry);
                                    const StatusIcon = status.Icon;
                                    const duration = calculateDuration(entry.startDate, entry.endDate);
                                    const messagePreview = htmlZuEinzeiler(entry.message);

                                    return (
                                        <Card
                                            key={entry.id}
                                            className={cn(
                                                "relative overflow-hidden transition-all group hover:shadow-md",
                                                status.cardBorder,
                                                status.cardBg
                                            )}
                                        >
                                            {/* Left accent bar */}
                                            <div className={cn("absolute left-0 top-0 bottom-0 w-1", status.accentBar)} />

                                            <div className="p-4 pl-5 flex items-start gap-4">
                                                {/* Status icon circle */}
                                                <div className={cn(
                                                    "flex-shrink-0 w-11 h-11 rounded-full flex items-center justify-center",
                                                    status.iconWrap
                                                )}>
                                                    <StatusIcon className="w-5 h-5" />
                                                </div>

                                                {/* Content */}
                                                <div className="flex-1 min-w-0">
                                                    <div className="flex items-start justify-between gap-3">
                                                        <div className="min-w-0 flex-1">
                                                            <div className="flex items-center gap-2 flex-wrap">
                                                                <h4 className="font-semibold text-slate-900 truncate">
                                                                    {entry.title || 'Ohne Titel'}
                                                                </h4>
                                                                <span className={cn(
                                                                    "text-xs px-2 py-0.5 rounded-full border font-medium flex items-center gap-1 flex-shrink-0",
                                                                    status.badge
                                                                )}>
                                                                    <StatusIcon className="w-3 h-3" />
                                                                    {status.label}
                                                                </span>
                                                            </div>

                                                            {/* Date row */}
                                                            <div className="mt-1.5 flex items-center gap-3 text-sm text-slate-600 flex-wrap">
                                                                <span className="flex items-center gap-1.5">
                                                                    <Calendar className="w-3.5 h-3.5 text-slate-400" />
                                                                    {formatDate(entry.startDate)}
                                                                    <span className="text-slate-300 mx-0.5">→</span>
                                                                    {formatDate(entry.endDate)}
                                                                </span>
                                                                {duration > 0 && (
                                                                    <span className="text-xs text-slate-500 bg-white border border-slate-200 px-2 py-0.5 rounded-full flex items-center gap-1">
                                                                        <Clock className="w-3 h-3" />
                                                                        {duration} {duration === 1 ? 'Tag' : 'Tage'}
                                                                    </span>
                                                                )}
                                                            </div>

                                                            {/* Postfächer, für die die Notiz gilt */}
                                                            {entry.postfaecher.length === 0 ? (
                                                                // Alte Notiz ohne Postfach oder Postfach gelöscht – sieht nur der Admin.
                                                                <p
                                                                    role="status"
                                                                    className="mt-2 inline-flex items-center gap-1.5 rounded-full border border-amber-300 bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-800"
                                                                >
                                                                    <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                                                                    Gilt für kein Postfach – antwortet nicht. Bitte bearbeiten und Postfach wählen.
                                                                </p>
                                                            ) : (
                                                                <ul
                                                                    aria-label="Gilt für diese Postfächer"
                                                                    className="mt-2 flex flex-wrap gap-1.5"
                                                                >
                                                                    {entry.postfaecher.map((postfach) => (
                                                                        <li
                                                                            key={postfach.id}
                                                                            title={formatPostfach(postfach)}
                                                                            className="inline-flex max-w-full items-center gap-1 rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs text-slate-600"
                                                                        >
                                                                            <Inbox className="w-3 h-3 shrink-0 text-slate-400" />
                                                                            <span className="truncate">{postfach.emailAdresse}</span>
                                                                        </li>
                                                                    ))}
                                                                </ul>
                                                            )}

                                                            {/* Message preview */}
                                                            {messagePreview && (
                                                                <p className="mt-2 text-sm text-slate-500 line-clamp-2">
                                                                    {messagePreview}
                                                                </p>
                                                            )}
                                                        </div>

                                                        {/* Actions */}
                                                        <div className="flex gap-1 flex-shrink-0 opacity-60 group-hover:opacity-100 transition-opacity">
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={() => startEditOoo(entry)}
                                                                aria-label={`${entry.title || 'Abwesenheit'} bearbeiten`}
                                                                className="text-slate-500 hover:text-rose-700 hover:bg-rose-100"
                                                                title="Bearbeiten"
                                                            >
                                                                <Edit2 className="w-4 h-4" />
                                                            </Button>
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={() => handleDeleteOoo(entry.id)}
                                                                aria-label={`${entry.title || 'Abwesenheit'} löschen`}
                                                                className="text-slate-500 hover:text-red-600 hover:bg-red-50"
                                                                title="Löschen"
                                                            >
                                                                <Trash2 className="w-4 h-4" />
                                                            </Button>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </Card>
                                    );
                                })}
                        </div>
                    ) : null}
                </div>
            )}
        </div>
    );
}
