import { useState, useEffect, useCallback } from 'react';
import DOMPurify from 'dompurify';
import {
    Plus,
    Trash2,
    Edit2,
    Save,
    X,
    Calendar,
    Clock,
    CheckCircle,
    Mail
} from 'lucide-react';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { cn } from '../lib/utils';
import { useToast } from './ui/toast';
import { useConfirm } from './ui/confirm-dialog';

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
                active: false // Always inactive manually - auto activates based on date
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
                    {/* OOO Form */}
                    {editingOoo !== null ? (
                        <Card className="p-6 border-rose-200 bg-rose-50/30">
                            <h3 className="text-lg font-semibold text-slate-900 mb-4">
                                {editingOoo.id ? 'Abwesenheit bearbeiten' : 'Neue Abwesenheit planen'}
                            </h3>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div className="md:col-span-2">
                                    <Label>Titel</Label>
                                    <Input
                                        value={editingOoo.title || ''}
                                        onChange={(e) => setEditingOoo({ ...editingOoo, title: e.target.value })}
                                        placeholder="z.B. Urlaub, Betriebsferien"
                                        className="mt-1"
                                    />
                                </div>
                                <div>
                                    <Label>Von</Label>
                                    <Input
                                        type="date"
                                        value={editingOoo.startDate || ''}
                                        onChange={(e) => setEditingOoo({ ...editingOoo, startDate: e.target.value })}
                                        className="mt-1"
                                    />
                                </div>
                                <div>
                                    <Label>Bis</Label>
                                    <Input
                                        type="date"
                                        value={editingOoo.endDate || ''}
                                        onChange={(e) => setEditingOoo({ ...editingOoo, endDate: e.target.value })}
                                        className="mt-1"
                                    />
                                </div>

                                <div className="md:col-span-2">
                                    <Label>Betreff der automatischen Antwort</Label>
                                    <Input
                                        value={editingOoo.subject || ''}
                                        onChange={(e) => setEditingOoo({ ...editingOoo, subject: e.target.value })}
                                        placeholder="Automatische Antwort: {{subject}}"
                                        className="mt-1"
                                    />
                                    <p className="text-xs text-slate-500 mt-1">
                                        Platzhalter: {"{{subject}}"}, {"{{start}}"}, {"{{ende}}"}, {"{{title}}"}
                                    </p>
                                </div>
                                <div className="md:col-span-2">
                                    <Label>Nachricht</Label>
                                    <div 
                                        className="mt-1 border border-slate-200 rounded-lg bg-white p-4 min-h-[300px] overflow-auto focus:border-rose-300 focus:ring-1 focus:ring-rose-200 outline-none"
                                        contentEditable
                                        suppressContentEditableWarning
                                        dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(editingOoo.message || '') }}
                                        onBlur={(e) => setEditingOoo({ ...editingOoo, message: e.currentTarget.innerHTML })}
                                    />
                                    <p className="text-xs text-slate-500 mt-1">
                                        Platzhalter werden automatisch ersetzt: {"{{start}}"}, {"{{ende}}"}
                                    </p>
                                </div>
                            </div>
                            <div className="flex gap-2 mt-4">
                                <Button
                                    onClick={handleSaveOoo}
                                    disabled={savingOoo}
                                    className="bg-rose-600 text-white hover:bg-rose-700"
                                >
                                    <Save className="w-4 h-4 mr-2" />
                                    {savingOoo ? 'Speichern...' : 'Planen'}
                                </Button>
                                <Button variant="outline" onClick={() => setEditingOoo(null)}>
                                    <X className="w-4 h-4 mr-2" /> Abbrechen
                                </Button>
                            </div>
                        </Card>
                    ) : (
                        <Button
                            onClick={() => {
                                // Find default signature and extract text
                                const defaultSig = signatures.find(s => s.defaultSignature) || (signatures.length > 0 ? signatures[0] : null);
                                // Build HTML message with signature
                                const signatureHtml = defaultSig?.html 
                                    ? `<div class="email-signature" style="margin-top: 20px; padding-top: 10px; border-top: 1px solid #ddd;">${defaultSig.html}</div>`
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
                                    signatureId: null // Not used anymore
                                });
                            }}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            <Plus className="w-4 h-4 mr-2" /> Neue Abwesenheit planen
                        </Button>
                    )}

                    {/* OOO List */}
                    {loadingOoo ? (
                        <p className="text-slate-500 text-center py-8">Lade Abwesenheitsnotizen...</p>
                    ) : oooEntries.length === 0 ? (
                        <Card className="p-8 text-center text-slate-500">
                            <Calendar className="w-10 h-10 mx-auto text-slate-300 mb-3" />
                            <p>Keine Abwesenheitsnotizen geplant.</p>
                        </Card>
                    ) : (
                        <div className="space-y-3">
                            {oooEntries.map((entry) => {
                                const active = isOooActive(entry);
                                const upcoming = isOooUpcoming(entry);

                                return (
                                    <Card
                                        key={entry.id}
                                        className={cn(
                                            "p-4 transition-all group",
                                            active && "border-green-300 bg-green-50/50",
                                            upcoming && "border-blue-200 bg-blue-50/30"
                                        )}
                                    >
                                        <div className="flex items-start justify-between gap-4">
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 mb-1">
                                                    <h4 className="font-medium text-slate-900">{entry.title}</h4>
                                                    {active && (
                                                        <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full flex items-center gap-1">
                                                            <CheckCircle className="w-3 h-3" /> Aktiv
                                                        </span>
                                                    )}
                                                    {upcoming && (
                                                        <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full flex items-center gap-1">
                                                            <Clock className="w-3 h-3" /> Geplant
                                                        </span>
                                                    )}
                                                    {!active && !upcoming && (
                                                        <span className="text-xs bg-slate-100 text-slate-500 px-2 py-0.5 rounded-full">
                                                            Abgelaufen
                                                        </span>
                                                    )}
                                                </div>
                                                <p className="text-sm text-slate-600 flex items-center gap-2">
                                                    <Calendar className="w-4 h-4 text-slate-400" />
                                                    {formatDate(entry.startDate)} – {formatDate(entry.endDate)}
                                                </p>
                                                {entry.message && (
                                                    <p className="mt-2 text-sm text-slate-500 line-clamp-2">{entry.message}</p>
                                                )}
                                            </div>
                                            <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => setEditingOoo(entry)}
                                                    className="text-slate-500 hover:text-rose-600"
                                                >
                                                    <Edit2 className="w-4 h-4" />
                                                </Button>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => handleDeleteOoo(entry.id)}
                                                    className="text-slate-500 hover:text-red-600"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </Button>
                                            </div>
                                        </div>
                                    </Card>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
