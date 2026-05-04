import { useState, useEffect, useCallback, useRef } from 'react';
import DOMPurify from 'dompurify';
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
    Info
} from 'lucide-react';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { DatePicker } from './ui/datepicker';
import { cn } from '../lib/utils';
import { useToast } from './ui/toast';
import { useConfirm } from './ui/confirm-dialog';
import { TiptapEditor } from './TiptapEditor';
import type { useEditor } from '@tiptap/react';

type TiptapEditorInstance = ReturnType<typeof useEditor>;

// Types
interface EmailSignature {
    id: number;
    name: string;
    html: string;
    defaultSignature: boolean;
    createdAt: string;
    updatedAt: string;
}

interface OutOfOfficeEntry {
    id: number;
    title: string;
    startDate: string;
    endDate: string;
    subject: string;
    message: string;
    signatureId: number | null;
    active: boolean;
}

// Backend response type (different field names)
interface OutOfOfficeBackend {
    id: number;
    title: string;
    startAt: string;
    endAt: string;
    subjectTemplate: string;
    bodyTemplate: string;
    signature: { id: number } | null;
    active: boolean;
}

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

export default function EmailSettings() {
    const toast = useToast();
    const confirmDialog = useConfirm();
    // Signatures state
    const [signatures, setSignatures] = useState<EmailSignature[]>([]);
    const [loadingSignatures, setLoadingSignatures] = useState(false);
    const [editingSignature, setEditingSignature] = useState<EmailSignature | null>(null);
    const [newSignatureName, setNewSignatureName] = useState('');
    const [newSignatureHtml, setNewSignatureHtml] = useState('');
    const [savingSignature, setSavingSignature] = useState(false);

    // OOO state
    const [oooEntries, setOooEntries] = useState<OutOfOfficeEntry[]>([]);
    const [loadingOoo, setLoadingOoo] = useState(false);
    const [editingOoo, setEditingOoo] = useState<Partial<OutOfOfficeEntry> | null>(null);
    const [savingOoo, setSavingOoo] = useState(false);

    // Cursor tracking for placeholder insertion
    const subjectInputRef = useRef<HTMLInputElement>(null);
    const subjectCursorRef = useRef<number>(0);
    const messageEditorRef = useRef<TiptapEditorInstance | null>(null);

    // Active tab within settings
    const [activeSection, setActiveSection] = useState<'signatures' | 'ooo'>('signatures');

    // Load signatures
    const loadSignatures = useCallback(async () => {
        setLoadingSignatures(true);
        try {
            const res = await fetch('/api/email/signatures');
            if (res.ok) {
                setSignatures(await res.json());
            }
        } catch (err) {
            console.error('Failed to load signatures', err);
        } finally {
            setLoadingSignatures(false);
        }
    }, []);

    // Load OOO entries - map backend field names to frontend
    const loadOooEntries = useCallback(async () => {
        setLoadingOoo(true);
        try {
            const res = await fetch('/api/email/outofoffice');
            if (res.ok) {
                const data: OutOfOfficeBackend[] = await res.json();
                // Map backend field names to frontend
                const mapped: OutOfOfficeEntry[] = data.map((item) => ({
                    id: item.id,
                    title: item.title,
                    startDate: item.startAt,
                    endDate: item.endAt,
                    subject: item.subjectTemplate || '',
                    message: item.bodyTemplate || '',
                    signatureId: item.signature?.id || null,
                    active: item.active
                }));
                setOooEntries(mapped);
            }
        } catch (err) {
            console.error('Failed to load OOO entries', err);
        } finally {
            setLoadingOoo(false);
        }
    }, []);

    useEffect(() => {
        loadSignatures();
        loadOooEntries();
    }, [loadSignatures, loadOooEntries]);

    // Save signature
    const handleSaveSignature = async () => {
        if (!newSignatureName.trim()) return;

        setSavingSignature(true);
        try {
            const payload = {
                id: editingSignature?.id || null,
                name: newSignatureName,
                html: newSignatureHtml,
                defaultSignature: false
            };

            const res = await fetch('/api/email/signatures', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                setEditingSignature(null);
                setNewSignatureName('');
                setNewSignatureHtml('');
                loadSignatures();
            }
        } catch (err) {
            console.error('Failed to save signature', err);
        } finally {
            setSavingSignature(false);
        }
    };

    // Delete signature
    const handleDeleteSignature = async (id: number) => {
        if (!await confirmDialog({ title: 'Signatur löschen', message: 'Signatur wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;

        try {
            const res = await fetch(`/api/email/signatures/${id}`, { method: 'DELETE' });
            if (res.ok) {
                loadSignatures();
            }
        } catch (err) {
            console.error('Failed to delete signature', err);
        }
    };

    // Edit signature
    const handleEditSignature = (sig: EmailSignature) => {
        setEditingSignature(sig);
        setNewSignatureName(sig.name);
        setNewSignatureHtml(sig.html);
    };

    // New signature
    const handleNewSignature = () => {
        setEditingSignature({ id: 0, name: '', html: '', defaultSignature: false, createdAt: '', updatedAt: '' });
        setNewSignatureName('');
        setNewSignatureHtml('');
    };

    // Cancel signature edit
    const handleCancelSignature = () => {
        setEditingSignature(null);
        setNewSignatureName('');
        setNewSignatureHtml('');
    };

    // Save OOO
    const handleSaveOoo = async () => {
        if (!editingOoo?.title?.trim() || !editingOoo?.startDate || !editingOoo?.endDate) {
            toast.warning('Bitte Titel und Zeitraum angeben.');
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
                active: true
            };

            const res = await fetch('/api/email/outofoffice', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                setEditingOoo(null);
                loadOooEntries();
            }
        } catch (err) {
            console.error('Failed to save OOO', err);
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
            }
        } catch (err) {
            console.error('Failed to delete OOO', err);
        }
    };

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

    const stripHtml = (html: string): string => {
        if (!html) return '';
        const tmp = document.createElement('div');
        tmp.innerHTML = html;
        return (tmp.textContent || tmp.innerText || '').replace(/\s+/g, ' ').trim();
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

    // Inserts a placeholder into the TipTap message editor at the current cursor.
    const insertMessagePlaceholder = (placeholder: string) => {
        const editor = messageEditorRef.current;
        if (!editor) return;
        editor.chain().focus().insertContent(placeholder).run();
    };

    // Visual chip button used to insert placeholders at cursor.
    // mousedown preventDefault keeps the active editor (input or contentEditable)
    // focused, so the saved selection/caret is preserved when the click fires.
    const PlaceholderChip = ({ token, label, onClick }: { token: string; label: string; onClick: () => void }) => (
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

            {/* Signatures Section */}
            {activeSection === 'signatures' && (
                <div className="space-y-6">
                    {/* Signature Editor */}
                    {editingSignature !== null ? (
                        <Card className="p-6 border-rose-200 bg-rose-50/30">
                            <h3 className="text-lg font-semibold text-slate-900 mb-4">
                                {editingSignature.id ? 'Signatur bearbeiten' : 'Neue Signatur'}
                            </h3>
                            <div className="space-y-4">
                                <div>
                                    <Label>Name</Label>
                                    <Input
                                        value={newSignatureName}
                                        onChange={(e) => setNewSignatureName(e.target.value)}
                                        placeholder="z.B. Standard-Signatur"
                                        className="mt-1"
                                    />
                                </div>
                                <div>
                                    <Label>HTML-Inhalt (Vorschau rechts)</Label>
                                    <div className="mt-1 grid grid-cols-1 lg:grid-cols-2 gap-4">
                                        {/* Raw HTML Editor */}
                                        <div>
                                            <p className="text-xs text-slate-500 mb-1">HTML-Code:</p>
                                            <textarea
                                                value={newSignatureHtml}
                                                onChange={(e) => setNewSignatureHtml(e.target.value)}
                                                rows={12}
                                                className="w-full px-3 py-2 border border-slate-200 rounded-lg bg-white focus:border-rose-300 focus:ring-1 focus:ring-rose-200 outline-none resize-none font-mono text-sm"
                                                placeholder="<table>...</table> oder HTML-Code hier einfügen"
                                            />
                                        </div>
                                        {/* Live Preview */}
                                        <div>
                                            <p className="text-xs text-slate-500 mb-1">Vorschau:</p>
                                            <div
                                                className="border border-slate-200 rounded-lg p-4 bg-white min-h-[200px] overflow-auto prose prose-sm max-w-none"
                                                dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(newSignatureHtml || '<em class="text-slate-400">Vorschau erscheint hier...</em>') }}
                                            />
                                        </div>
                                    </div>
                                </div>
                                <div className="flex gap-2">
                                    <Button
                                        onClick={handleSaveSignature}
                                        disabled={savingSignature || !newSignatureName.trim()}
                                        className="bg-rose-600 text-white hover:bg-rose-700"
                                    >
                                        <Save className="w-4 h-4 mr-2" />
                                        {savingSignature ? 'Speichern...' : 'Speichern'}
                                    </Button>
                                    <Button variant="outline" onClick={handleCancelSignature}>
                                        <X className="w-4 h-4 mr-2" /> Abbrechen
                                    </Button>
                                </div>
                            </div>
                        </Card>
                    ) : (
                        <Button onClick={handleNewSignature} className="bg-rose-600 text-white hover:bg-rose-700">
                            <Plus className="w-4 h-4 mr-2" /> Neue Signatur
                        </Button>
                    )}

                    {/* Signature List */}
                    {loadingSignatures ? (
                        <p className="text-slate-500 text-center py-8">Lade Signaturen...</p>
                    ) : signatures.length === 0 ? (
                        <Card className="p-8 text-center text-slate-500">
                            <Mail className="w-10 h-10 mx-auto text-slate-300 mb-3" />
                            <p>Keine Signaturen vorhanden.</p>
                        </Card>
                    ) : (
                        <div className="space-y-3">
                            {signatures.map((sig) => (
                                <Card key={sig.id} className="p-4 hover:shadow-md transition-shadow group">
                                    <div className="flex items-start justify-between gap-4">
                                        <div className="flex-1 min-w-0">
                                            <h4 className="font-medium text-slate-900">{sig.name}</h4>
                                            <div
                                                className="mt-2 text-sm text-slate-600 prose prose-sm max-w-none line-clamp-3"
                                                dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(sig.html || '<em>Kein Inhalt</em>') }}
                                            />
                                        </div>
                                        <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => handleEditSignature(sig)}
                                                className="text-slate-500 hover:text-rose-600"
                                            >
                                                <Edit2 className="w-4 h-4" />
                                            </Button>
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => handleDeleteSignature(sig.id)}
                                                className="text-slate-500 hover:text-red-600"
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </Button>
                                        </div>
                                    </div>
                                </Card>
                            ))}
                        </div>
                    )}
                </div>
            )}

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
                            <Button
                                onClick={async () => {
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
                                        signatureId: null
                                    });
                                }}
                                className="bg-rose-600 text-white hover:bg-rose-700 shadow-sm"
                            >
                                <Plus className="w-4 h-4 mr-2" /> Neue Abwesenheit
                            </Button>
                        )}
                    </div>

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
                                                className="mt-1"
                                            />
                                        </div>
                                        <div>
                                            <Label className="text-xs text-slate-500 font-normal">Bis</Label>
                                            <DatePicker
                                                value={editingOoo.endDate || ''}
                                                onChange={(v) => setEditingOoo({ ...editingOoo, endDate: v })}
                                                placeholder="Enddatum"
                                                className="mt-1"
                                            />
                                        </div>
                                    </div>
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
                                    <TiptapEditor
                                        value={editingOoo.message || ''}
                                        onChange={(html) => setEditingOoo((prev) => prev ? { ...prev, message: html } : prev)}
                                        onEditorReady={(ed) => { messageEditorRef.current = ed; }}
                                    />
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
                            <Button
                                onClick={async () => {
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
                                        signatureId: null
                                    });
                                }}
                                className="bg-rose-600 text-white hover:bg-rose-700 shadow-sm"
                            >
                                <Plus className="w-4 h-4 mr-2" /> Erste Abwesenheit planen
                            </Button>
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
                                    const messagePreview = stripHtml(entry.message);

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
                                                                onClick={() => setEditingOoo(entry)}
                                                                className="text-slate-500 hover:text-rose-700 hover:bg-rose-100"
                                                                title="Bearbeiten"
                                                            >
                                                                <Edit2 className="w-4 h-4" />
                                                            </Button>
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={() => handleDeleteOoo(entry.id)}
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
