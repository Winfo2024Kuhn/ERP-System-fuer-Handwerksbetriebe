import { useCallback, useEffect, useState } from 'react';
import {
    Pencil,
    Plus,
    RefreshCw,
    Save,
    Trash2,
    User,
    X
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { PageLayout } from '../components/layout/PageLayout';
import { cn } from '../lib/utils';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';

interface FrontendUser {
    id: number;
    displayName: string;
    shortCode: string | null;
    defaultSignature: { id: number; name: string } | null;
    mitarbeiter: { id: number; vorname: string; nachname: string } | null;
}

interface EmailSignature {
    id: number;
    name: string;
}

interface Mitarbeiter {
    id: number;
    vorname: string;
    nachname: string;
}

// ==================== User List Component ====================
interface UserListProps {
    users: FrontendUser[];
    selectedId: number | null;
    onSelect: (id: number) => void;
    onDelete: (id: number) => void;
}

const UserList: React.FC<UserListProps> = ({
    users,
    selectedId,
    onSelect,
    onDelete,
}) => {
    if (!users.length) {
        return (
            <Card className="p-8 text-center text-slate-500 border-dashed">
                <User className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                Keine Benutzer vorhanden
            </Card>
        );
    }

    return (
        <div className="space-y-2">
            {users.map((user) => {
                const isSelected = selectedId === user.id;
                return (
                    <div
                        key={user.id}
                        className={cn(
                            'group flex items-center justify-between gap-2 rounded-lg border px-3 py-2 cursor-pointer transition',
                            'border-slate-200 bg-white hover:border-rose-200 hover:shadow-sm',
                            isSelected ? 'border-rose-500 bg-rose-50 shadow-sm' : ''
                        )}
                        onClick={() => onSelect(user.id)}
                    >
                        <div className="flex items-center gap-3 min-w-0">
                            <div className="w-8 h-8 rounded-full bg-rose-100 flex items-center justify-center flex-shrink-0">
                                <span className="text-sm font-bold text-rose-600">
                                    {user.shortCode || user.displayName.charAt(0).toUpperCase()}
                                </span>
                            </div>
                            <div className="min-w-0">
                                <p className="text-sm font-semibold text-slate-900 truncate">
                                    {user.displayName}
                                </p>
                                {user.shortCode && (
                                    <p className="text-xs text-slate-500">{user.shortCode}</p>
                                )}
                            </div>
                        </div>
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                onDelete(user.id);
                            }}
                            className="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors opacity-0 group-hover:opacity-100"
                            title="Benutzer löschen"
                        >
                            <Trash2 className="w-3.5 h-3.5" />
                        </button>
                    </div>
                );
            })}
        </div>
    );
};

// ==================== Main Component ====================
export default function BenutzerEditor() {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [users, setUsers] = useState<FrontendUser[]>([]);
    const [signatures, setSignatures] = useState<EmailSignature[]>([]);
    const [mitarbeiterList, setMitarbeiterList] = useState<Mitarbeiter[]>([]);
    const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);

    // Form State
    const [formData, setFormData] = useState({
        id: null as number | null,
        displayName: '',
        shortCode: '',
        defaultSignatureId: null as number | null,
        mitarbeiterId: null as number | null,
    });

    const selectedUser = users.find(u => u.id === selectedUserId) || null;

    // Load data
    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const [usersRes, signaturesRes, mitarbeiterRes] = await Promise.all([
                fetch('/api/frontend-users'),
                fetch('/api/email/signatures'),
                fetch('/api/mitarbeiter')
            ]);
            if (usersRes.ok) {
                const data = await usersRes.json();
                setUsers(Array.isArray(data) ? data : []);
            }
            if (signaturesRes.ok) {
                const data = await signaturesRes.json();
                setSignatures(Array.isArray(data) ? data : []);
            }
            if (mitarbeiterRes.ok) {
                const data = await mitarbeiterRes.json();
                setMitarbeiterList(Array.isArray(data) ? data : []);
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // Sync form with selected user
    useEffect(() => {
        if (selectedUser) {
            setFormData({
                id: selectedUser.id,
                displayName: selectedUser.displayName,
                shortCode: selectedUser.shortCode || '',
                defaultSignatureId: selectedUser.defaultSignature?.id || null,
                mitarbeiterId: selectedUser.mitarbeiter?.id || null,
            });
        }
    }, [selectedUser]);

    // Reset form for new user
    const handleNewUser = () => {
        setSelectedUserId(null);
        setFormData({
            id: null,
            displayName: '',
            shortCode: '',
            defaultSignatureId: null,
            mitarbeiterId: null,
        });
    };

    // Save user
    const handleSave = async () => {
        if (!formData.displayName.trim()) return;
        setSaving(true);
        try {
            const res = await fetch('/api/frontend-users', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    id: formData.id,
                    displayName: formData.displayName.trim(),
                    shortCode: formData.shortCode.trim() || null,
                    defaultSignatureId: formData.defaultSignatureId,
                    mitarbeiterId: formData.mitarbeiterId,
                }),
            });
            if (res.ok) {
                await loadData();
                if (!formData.id) {
                    // Reset form after creating new user
                    handleNewUser();
                }
            } else {
                toast.error('Fehler beim Speichern.');
            }
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Speichern.');
        } finally {
            setSaving(false);
        }
    };

    // Delete user
    const handleDelete = async (id: number) => {
        if (!await confirmDialog({ title: 'Benutzer löschen', message: 'Benutzer wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;

        try {
            const res = await fetch(`/api/frontend-users/${id}`, {
                method: 'DELETE',
            });
            if (res.ok) {
                await loadData();
                handleNewUser();
            } else {
                toast.error('Fehler beim Löschen des Benutzers.');
            }
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Löschen.');
        }
    };

    return (
        <PageLayout
            ribbonCategory="Systemeinstellungen"
            title="Benutzerverwaltung"
            subtitle="Verwalten Sie die Frontend-Benutzerprofile."
            actions={
                <Button variant="outline" size="sm" onClick={loadData} disabled={loading}>
                    <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                    Aktualisieren
                </Button>
            }
        >
            {/* 2-Column Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-[1fr_2fr] gap-6">
                {/* Column 1: User List */}
                <Card className="p-6 border-rose-100 shadow-lg">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500">Benutzer</p>
                            <h4 className="text-lg font-semibold text-slate-900">Profile</h4>
                        </div>
                        <Button
                            size="sm"
                            onClick={handleNewUser}
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        >
                            <Plus className="w-4 h-4" />
                        </Button>
                    </div>

                    {loading ? (
                        <div className="text-slate-500 text-sm py-6">Wird geladen...</div>
                    ) : (
                        <UserList
                            users={users}
                            selectedId={selectedUserId}
                            onSelect={setSelectedUserId}
                            onDelete={handleDelete}
                        />
                    )}
                </Card>

                {/* Column 2: Edit Form */}
                <Card className="p-6 border-rose-100 shadow-lg">
                    <div className="flex items-center justify-between mb-6">
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500">
                                {formData.id ? 'Bearbeiten' : 'Neuanlage'}
                            </p>
                            <h3 className="text-xl font-semibold text-slate-900">
                                {formData.id ? 'Benutzer bearbeiten' : 'Neuer Benutzer'}
                            </h3>
                        </div>
                        {formData.id && (
                            <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-rose-50 text-rose-700 border border-rose-100 text-sm">
                                <Pencil className="w-3 h-3" />
                                Bearbeitung
                            </span>
                        )}
                    </div>

                    <div className="space-y-4">
                        {/* Display Name */}
                        <div className="space-y-2">
                            <Label htmlFor="displayName">Anzeigename *</Label>
                            <Input
                                id="displayName"
                                value={formData.displayName}
                                onChange={(e) => setFormData(prev => ({ ...prev, displayName: e.target.value }))}
                                placeholder="z.B. Max Mustermann"
                            />
                        </div>

                        {/* Short Code */}
                        <div className="space-y-2">
                            <Label htmlFor="shortCode">Kürzel (optional)</Label>
                            <Input
                                id="shortCode"
                                value={formData.shortCode}
                                onChange={(e) => setFormData(prev => ({ ...prev, shortCode: e.target.value }))}
                                placeholder="z.B. MM"
                                maxLength={10}
                            />
                            <p className="text-xs text-slate-500">
                                Wird als Initialen im Avatar angezeigt.
                            </p>
                        </div>

                        {/* Default Signature */}
                        <div className="space-y-2">
                            <Label htmlFor="signature">Standard-Signatur (optional)</Label>
                            <Select
                                value={formData.defaultSignatureId?.toString() || ''}
                                onChange={(value) => setFormData(prev => ({
                                    ...prev,
                                    defaultSignatureId: value ? Number(value) : null
                                }))}
                                options={[
                                    { value: '', label: 'Keine Signatur' },
                                    ...signatures.map((sig) => ({ value: sig.id.toString(), label: sig.name }))
                                ]}
                                placeholder="Signatur wählen"
                            />
                            <p className="text-xs text-slate-500">
                                Wird automatisch für E-Mails dieses Benutzers verwendet.
                            </p>
                        </div>

                        {/* Mitarbeiter Zuordnung */}
                        <div className="space-y-2">
                            <Label htmlFor="mitarbeiter">Mitarbeiter zuordnen</Label>
                            <Select
                                value={formData.mitarbeiterId?.toString() || ''}
                                onChange={(value) => setFormData(prev => ({
                                    ...prev,
                                    mitarbeiterId: value ? Number(value) : null
                                }))}
                                options={[
                                    { value: '', label: 'Kein Mitarbeiter' },
                                    ...mitarbeiterList.map((m) => ({ value: m.id.toString(), label: `${m.vorname} ${m.nachname}` }))
                                ]}
                                placeholder="Mitarbeiter wählen"
                            />
                            <p className="text-xs text-slate-500">
                                Verknüpft diesen Benutzer mit einem Mitarbeiter für die Nachverfolgung von Uploads.
                            </p>
                        </div>

                        {/* Info Box */}
                        <div className="rounded-2xl border border-dashed border-slate-300 p-4 bg-slate-50">
                            <h4 className="text-sm font-semibold text-slate-900 mb-2">Hinweis</h4>
                            <p className="text-sm text-slate-600 leading-relaxed">
                                Benutzerprofile werden für die Personalisierung von E-Mails und
                                Dokumenten verwendet. Durch die Mitarbeiter-Zuordnung können hochgeladene Dokumente
                                dem jeweiligen Mitarbeiter zugeordnet werden.
                            </p>
                        </div>

                        {/* Actions */}
                        <div className="flex gap-3 pt-4">
                            <Button
                                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                size="sm"
                                onClick={handleSave}
                                disabled={!formData.displayName.trim() || saving}
                            >
                                <Save className="w-4 h-4" />
                                {saving ? 'Speichert...' : formData.id ? 'Speichern' : 'Erstellen'}
                            </Button>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={handleNewUser}
                            >
                                <X className="w-4 h-4" /> Abbrechen
                            </Button>
                        </div>
                    </div>
                </Card>
            </div>
        </PageLayout>
    );
}
