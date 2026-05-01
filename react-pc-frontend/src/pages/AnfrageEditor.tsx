import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
    ArrowLeft,
    Calendar,
    ChevronLeft,
    ChevronRight,
    Edit2,
    Euro,
    FileText,
    FolderOpen,
    Mail,
    MapPin,
    Plus,
    RefreshCw,
    Search,
    User,
    Building2,
    X,
    Check,
    PlusCircle,
    StickyNote,
    Trash2,
    Upload,
    Lock
} from "lucide-react";
import { PageLayout } from "../components/layout/PageLayout";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Select } from "../components/ui/select-custom";
import { cn } from "../lib/utils";
import { DetailLayout } from "../components/DetailLayout";
import { EmailsTab } from "../components/EmailsTab";
import { Input } from "../components/ui/input";
import GoogleMapsEmbed from "../components/GoogleMapsEmbed";
import { DocumentManager } from "../components/DocumentManager";
import { EmailListInput } from "../components/EmailListInput";
import { AddressAutocomplete } from "../components/AddressAutocomplete";
import { PhoneInput } from "../components/PhoneInput";
import type {
    Anfrage,
    AnfrageDetail,
    AusgangsGeschaeftsDokument,
} from "../types";
import { DokumentHierarchie } from "../components/DokumentHierarchie";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "../components/ui/dialog";
import { ImageViewer } from "../components/ui/image-viewer";
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { onDokumentChanged } from '../lib/dokumentChannel';
import { appendBildToNotiz, removeBildFromNotiz } from '../lib/optimisticUploads';

// Notizen Interfaces
interface AnfrageNotizBild {
    id: number;
    originalDateiname: string;
    url: string;
    erstelltAm: string;
}
interface AnfrageNotiz {
    id: number;
    notiz: string;
    erstelltAm: string;
    mitarbeiterId: number;
    mitarbeiterVorname: string;
    mitarbeiterNachname: string;
    mobileSichtbar: boolean;
    nurFuerErsteller: boolean;
    canEdit?: boolean;
    bilder?: AnfrageNotizBild[];
}

const PAGE_SIZE = 12;

// Helper Functions
const formatCurrency = (value?: number | null) => {
    if (value == null) return '0,00 €';
    return value.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
};

const formatDate = (dateStr?: string | null) => {
    if (!dateStr) return '-';
    try {
        return new Date(dateStr).toLocaleDateString('de-DE');
    } catch {
        return dateStr;
    }
};

// ==================== ANFRAGE CARD ====================
function AnfrageCard({ anfrage, onClick, onToggleAbgeschlossen }: {
    anfrage: Anfrage;
    onClick: () => void;
    onToggleAbgeschlossen?: (anfrageId: number, abgeschlossen: boolean) => void;
}) {
    const handleCheckboxClick = (e: React.MouseEvent) => {
        e.stopPropagation();
    };

    const handleCheckboxChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        e.stopPropagation();
        if (onToggleAbgeschlossen) {
            onToggleAbgeschlossen(anfrage.id, e.target.checked);
        }
    };

    return (
        <Card
            className={cn(
                "group relative cursor-pointer hover:shadow-md transition-all border-slate-200 bg-white overflow-hidden",
                anfrage.abgeschlossen && "opacity-60 bg-slate-50"
            )}
            onClick={onClick}
        >
            <div className="p-4 space-y-3">
                <div className="flex items-start justify-between">
                    <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                            <span className={cn(
                                "text-xs font-semibold tracking-wider uppercase px-2 py-0.5 rounded-full",
                                anfrage.abgeschlossen
                                    ? "bg-slate-200 text-slate-600"
                                    : anfrage.projektId
                                        ? "bg-green-50 text-green-700"
                                        : "bg-amber-50 text-amber-700"
                            )}>
                                {anfrage.abgeschlossen ? 'Beendet' : (anfrage.projektId ? 'Projekt erstellt' : 'Offen')}
                            </span>
                        </div>
                        <h3 className="font-semibold text-slate-900 mt-2 truncate text-base" title={anfrage.bauvorhaben}>
                            {anfrage.bauvorhaben || "Unbenannt"}
                        </h3>
                        <p className="text-sm text-slate-500 truncate">{anfrage.kundenName || "Kein Kunde"}</p>
                    </div>
                    {/* Checkbox zum Beenden */}
                    <div
                        className="shrink-0 ml-2"
                        onClick={handleCheckboxClick}
                        title={anfrage.abgeschlossen ? "Anfrage ist beendet" : "Als beendet markieren"}
                    >
                        <label className="flex items-center gap-1.5 cursor-pointer p-1.5 rounded-lg hover:bg-slate-100 transition-colors">
                            <input
                                type="checkbox"
                                checked={anfrage.abgeschlossen || false}
                                onChange={handleCheckboxChange}
                                className="w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                            />
                            <span className="text-xs text-slate-500 hidden sm:inline">Beendet</span>
                        </label>
                    </div>
                </div>

                <div className="space-y-1 pt-2 border-t border-slate-50">
                    {anfrage.anfragesnummer && (
                        <div className="flex items-center gap-2 text-sm text-slate-600">
                            <FileText className="w-4 h-4 text-slate-400 shrink-0" />
                            <span className="truncate">{anfrage.anfragesnummer}</span>
                        </div>
                    )}
                    <div className="flex items-center gap-2 text-sm text-slate-600">
                        <Calendar className="w-4 h-4 text-slate-400 shrink-0" />
                        <span>{formatDate(anfrage.anlegedatum)}</span>
                    </div>
                    <div className="flex items-center gap-2 text-sm font-medium text-rose-600">
                        <Euro className="w-4 h-4 shrink-0" />
                        <span>{formatCurrency(anfrage.betrag)}</span>
                    </div>
                </div>
            </div>
        </Card>
    );
}

// ==================== KUNDE INTERFACE ====================
interface Kunde {
    id: number;
    name: string;
    kundennummer: string;
    strasse?: string;
    plz?: string;
    ort?: string;
    kundenEmails?: string[];
}

// ==================== KUNDEN AUSWAHL VIEW ====================
const KundenAuswahlView: React.FC<{
    onSelect: (kunde: Kunde) => void;
    onCreateNew: () => void;
    onBack: () => void;
}> = ({ onSelect, onCreateNew, onBack }) => {
    const [kunden, setKunden] = useState<Kunde[]>([]);
    const [loading, setLoading] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');

    const searchKunden = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (searchTerm) params.append('q', searchTerm);
            params.append('size', '50');
            const res = await fetch(`/api/kunden?${params.toString()}`);
            if (!res.ok) throw new Error('Fehler beim Laden');
            const data = await res.json();
            setKunden(Array.isArray(data) ? data : data.kunden || []);
        } catch (err) {
            console.error('Load Kunden error', err);
        } finally {
            setLoading(false);
        }
    }, [searchTerm]);

    useEffect(() => {
        searchKunden();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        searchKunden();
    };

    return (
        <div className="space-y-4">
            <div className="flex items-center gap-3 mb-4">
                <button onClick={onBack} className="text-slate-500 hover:text-slate-700">
                    <ChevronLeft className="w-5 h-5" />
                </button>
                <h3 className="text-lg font-semibold text-slate-900">Kunde auswählen</h3>
            </div>

            <form onSubmit={handleSearch} className="flex gap-2">
                <input
                    type="text"
                    value={searchTerm}
                    onChange={e => setSearchTerm(e.target.value)}
                    placeholder="Kunde suchen (Name, Kundennummer)..."
                    className="flex-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                />
                <Button type="submit" size="sm" className="bg-rose-600 text-white hover:bg-rose-700">
                    <Search className="w-4 h-4 mr-1" />
                    Suchen
                </Button>
            </form>

            <div className="max-h-[300px] overflow-y-auto space-y-2">
                {loading ? (
                    <p className="text-slate-500 text-center py-4">Lade Kunden...</p>
                ) : kunden.length === 0 ? (
                    <div className="text-center py-8">
                        <p className="text-slate-500 mb-4">Keine Kunden gefunden</p>
                        <Button onClick={onCreateNew} className="bg-rose-600 text-white hover:bg-rose-700">
                            <Plus className="w-4 h-4 mr-2" />
                            Neuen Kunden anlegen
                        </Button>
                    </div>
                ) : (
                    kunden.map(kunde => (
                        <div
                            key={kunde.id}
                            onClick={() => onSelect(kunde)}
                            className="p-3 border border-slate-200 rounded-lg hover:border-rose-300 hover:bg-rose-50 cursor-pointer transition-colors group"
                        >
                            <div className="flex justify-between items-center">
                                <div>
                                    <p className="font-medium text-slate-900">{kunde.name}</p>
                                    <p className="text-sm text-slate-500">{kunde.kundennummer}</p>
                                    {(kunde.strasse || kunde.ort) && (
                                        <p className="text-xs text-slate-400">
                                            {[kunde.strasse, [kunde.plz, kunde.ort].filter(Boolean).join(' ')].filter(Boolean).join(', ')}
                                        </p>
                                    )}
                                </div>
                                <Check className="w-5 h-5 text-rose-600 opacity-0 group-hover:opacity-100" />
                            </div>
                        </div>
                    ))
                )}
            </div>

            {kunden.length > 0 && (
                <div className="pt-3 border-t border-slate-100">
                    <Button onClick={onCreateNew} variant="outline" className="w-full">
                        <Plus className="w-4 h-4 mr-2" />
                        Neuen Kunden anlegen
                    </Button>
                </div>
            )}
        </div>
    );
};

// Anrede Optionen
const ANREDE_OPTIONS = [
    { value: '', label: 'Bitte wählen' },
    { value: 'HERR', label: 'Sehr geehrter Herr' },
    { value: 'FRAU', label: 'Sehr geehrte Frau' },
    { value: 'FAMILIE', label: 'Sehr geehrte Familie' },
    { value: 'DAMEN_HERREN', label: 'Sehr geehrte Damen und Herren' }
];

// ==================== KUNDE ANLEGEN VIEW ====================
const KundeAnlegenView: React.FC<{
    onSuccess: (kunde: Kunde) => void;
    onBack: () => void;
}> = ({ onSuccess, onBack }) => {
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [kundennummerError, setKundennummerError] = useState<string | null>(null);
    const [autoKundennummer, setAutoKundennummer] = useState(true);
    const [formData, setFormData] = useState({
        name: '',
        kundennummer: '',
        anrede: '',
        ansprechspartner: '',
        strasse: '',
        plz: '',
        ort: '',
        telefon: '',
        mobiltelefon: '',
        email: '',
        zahlungsziel: 8,
    });

    // Nächste verfügbare Kundennummer laden
    useEffect(() => {
        if (autoKundennummer) {
            fetch('/api/kunden/next-kundennummer')
                .then(res => res.json())
                .then(data => {
                    setFormData(prev => ({ ...prev, kundennummer: data.kundennummer || '' }));
                })
                .catch(() => {});
        }
    }, [autoKundennummer]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!formData.name.trim()) {
            setError('Bitte Kundennamen angeben');
            return;
        }
        if (!autoKundennummer && !formData.kundennummer.trim()) {
            setError('Bitte Kundennummer angeben oder "Automatisch" aktivieren');
            return;
        }

        setSaving(true);
        setError(null);
        try {
            const payload = {
                name: formData.name.trim(),
                kundennummer: autoKundennummer ? '' : formData.kundennummer.trim(),
                anrede: formData.anrede || null,
                ansprechspartner: formData.ansprechspartner.trim() || null,
                strasse: formData.strasse.trim() || null,
                plz: formData.plz.trim() || null,
                ort: formData.ort.trim() || null,
                telefon: formData.telefon.trim() || null,
                mobiltelefon: formData.mobiltelefon.trim() || null,
                zahlungsziel: formData.zahlungsziel || 8,
                kundenEmails: formData.email.trim() ? [formData.email.trim()] : [],
            };

            const res = await fetch('/api/kunden', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                const msg = errData.message || errData.detail || 'Kunde konnte nicht angelegt werden';
                if (res.status === 409) {
                    setKundennummerError(msg);
                    return;
                }
                throw new Error(msg);
            }

            const created = await res.json();
            onSuccess(created);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : 'Fehler beim Speichern');
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="space-y-4">
            <div className="flex items-center gap-3 mb-4">
                <button onClick={onBack} className="text-slate-500 hover:text-slate-700">
                    <ChevronLeft className="w-5 h-5" />
                </button>
                <h3 className="text-lg font-semibold text-slate-900">Neuen Kunden anlegen</h3>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
                {error && (
                    <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">{error}</div>
                )}

                {/* Name & Kundennummer */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Kundenname *</label>
                        <input
                            type="text"
                            value={formData.name}
                            onChange={e => setFormData(prev => ({ ...prev, name: e.target.value }))}
                            placeholder="Firma / Name"
                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>
                    <div>
                        <div className="flex items-center justify-between mb-1">
                            <label className="block text-sm font-medium text-slate-700">Kundennummer</label>
                            <label className="flex items-center gap-1.5 text-sm cursor-pointer select-none">
                                <input
                                    type="checkbox"
                                    checked={autoKundennummer}
                                    onChange={e => {
                                        const checked = e.target.checked;
                                        setAutoKundennummer(checked);
                                        if (checked) {
                                            fetch('/api/kunden/next-kundennummer')
                                                .then(res => res.json())
                                                .then(data => setFormData(prev => ({ ...prev, kundennummer: data.kundennummer || '' })))
                                                .catch(() => {});
                                        } else {
                                            setFormData(prev => ({ ...prev, kundennummer: '' }));
                                        }
                                    }}
                                    className="accent-rose-600 w-4 h-4"
                                />
                                <span className="text-slate-600">Automatisch</span>
                            </label>
                        </div>
                        <input
                            type="text"
                            value={formData.kundennummer}
                            onChange={e => {
                                setFormData(prev => ({ ...prev, kundennummer: e.target.value }));
                                setKundennummerError(null);
                            }}
                            placeholder={autoKundennummer ? 'Wird automatisch vergeben' : 'z.B. K-1234'}
                            disabled={autoKundennummer}
                            className={`w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:bg-slate-50 disabled:text-slate-400 ${kundennummerError ? 'border-rose-500 bg-rose-50' : 'border-slate-200'}`}
                        />
                        {kundennummerError && (
                            <p className="mt-1 text-xs text-rose-600">{kundennummerError}</p>
                        )}
                    </div>
                </div>

                {/* Anrede & Ansprechpartner */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Anrede</label>
                        <Select
                            value={formData.anrede}
                            onChange={(value) => setFormData(prev => ({ ...prev, anrede: value }))}
                            options={ANREDE_OPTIONS.map(opt => ({
                                value: opt.value,
                                label: opt.label
                            }))}
                            placeholder="Anrede wählen"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Ansprechpartner</label>
                        <input
                            type="text"
                            value={formData.ansprechspartner}
                            onChange={e => setFormData(prev => ({ ...prev, ansprechspartner: e.target.value }))}
                            placeholder="Vor- und Nachname"
                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>
                </div>

                {/* E-Mail */}
                <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">E-Mail</label>
                    <input
                        type="email"
                        value={formData.email}
                        onChange={e => setFormData(prev => ({ ...prev, email: e.target.value }))}
                        placeholder="email@example.com"
                        className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>

                {/* Telefon & Mobiltelefon */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Telefon</label>
                        <PhoneInput
                            value={formData.telefon}
                            onChange={v => setFormData(prev => ({ ...prev, telefon: v }))}
                            variant="festnetz"
                            autoPrefillAreaCode
                            plz={formData.plz}
                            ort={formData.ort}
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Mobiltelefon</label>
                        <PhoneInput
                            value={formData.mobiltelefon}
                            onChange={v => setFormData(prev => ({ ...prev, mobiltelefon: v }))}
                            variant="mobil"
                        />
                    </div>
                </div>

                {/* Adresse */}
                <AddressAutocomplete
                    value={{
                        strasse: formData.strasse,
                        plz: formData.plz,
                        ort: formData.ort
                    }}
                    onChange={next => setFormData(prev => ({
                        ...prev,
                        strasse: next.strasse,
                        plz: next.plz,
                        ort: next.ort
                    }))}
                />

                {/* Zahlungsziel */}
                <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Zahlungsziel (Tage)</label>
                    <input
                        type="number"
                        min="0"
                        value={formData.zahlungsziel}
                        onChange={e => setFormData(prev => ({ ...prev, zahlungsziel: parseInt(e.target.value) || 8 }))}
                        placeholder="8"
                        className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>

                <div className="flex justify-end gap-3 pt-4">
                    <Button type="button" variant="outline" onClick={onBack}>Abbrechen</Button>
                    <Button type="submit" disabled={saving} className="bg-rose-600 text-white hover:bg-rose-700">
                        {saving ? 'Speichern...' : 'Kunde anlegen'}
                    </Button>
                </div>
            </form>
        </div>
    );
};

// ==================== ANFRAGE ERSTELLEN MODAL ====================
interface AnfrageErstellenModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: (anfrageId: number) => void;
    editAnfrage?: Anfrage | null;
}

const AnfrageErstellenModal: React.FC<AnfrageErstellenModalProps> = ({
    isOpen,
    onClose,
    onSuccess,
    editAnfrage,
}) => {
    const isEditMode = !!editAnfrage;
    const [subView, setSubView] = useState<'main' | 'selectKunde' | 'createKunde'>('main');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [selectedKunde, setSelectedKunde] = useState<Kunde | null>(null);
    const [adresseGleichKunde, setAdresseGleichKunde] = useState(false);
    const [zusaetzlicheEmails, setZusaetzlicheEmails] = useState<string[]>([]);

    const [formData, setFormData] = useState({
        bauvorhaben: '',
        betrag: undefined as number | undefined,
        projektStrasse: '',
        projektPlz: '',
        projektOrt: '',
    });

    // Reset beim Öffnen/Schließen
    useEffect(() => {
        if (!isOpen) {
            setFormData({ bauvorhaben: '', betrag: undefined, projektStrasse: '', projektPlz: '', projektOrt: '' });
            setSelectedKunde(null);
            setError(null);
            setSubView('main');
            setAdresseGleichKunde(false);
            setZusaetzlicheEmails([]);
        } else if (editAnfrage) {
            setFormData({
                bauvorhaben: editAnfrage.bauvorhaben || '',
                betrag: editAnfrage.betrag,
                projektStrasse: editAnfrage.projektStrasse || '',
                projektPlz: editAnfrage.projektPlz || '',
                projektOrt: editAnfrage.projektOrt || '',
            });
            if (editAnfrage.kundenId) {
                setSelectedKunde({
                    id: editAnfrage.kundenId,
                    name: editAnfrage.kundenName || '',
                    kundennummer: editAnfrage.kundennummer || ''
                });
            }
            setSubView('main');
            setAdresseGleichKunde(false);

            // Anfrage-spezifische E-Mail-Adressen laden
            if (editAnfrage.kundenEmails && editAnfrage.kundenEmails.length > 0) {
                setZusaetzlicheEmails(editAnfrage.kundenEmails);
            }
        }
    }, [isOpen, editAnfrage]);

    // Kundenadresse in Projektadresse übernehmen wenn Checkbox aktiviert
    useEffect(() => {
        if (adresseGleichKunde && selectedKunde) {
            setFormData(prev => ({
                ...prev,
                projektStrasse: selectedKunde.strasse || '',
                projektPlz: selectedKunde.plz || '',
                projektOrt: selectedKunde.ort || '',
            }));
        }
    }, [adresseGleichKunde, selectedKunde]);

    const handleKundeSelect = (kunde: Kunde) => {
        setSelectedKunde(kunde);
        setSubView('main');
    };

    const handleKundeCreated = (kunde: Kunde) => {
        setSelectedKunde(kunde);
        setSubView('main');
    };

    const handleSubmit = async () => {
        if (!formData.bauvorhaben.trim()) {
            setError('Bitte Bauvorhaben angeben.');
            return;
        }
        if (!selectedKunde) {
            setError('Bitte Kunde auswählen.');
            return;
        }
        setSaving(true);
        setError(null);

        try {
            const payload = {
                bauvorhaben: formData.bauvorhaben.trim(),
                kundenId: selectedKunde.id,
                betrag: formData.betrag,
                projektStrasse: formData.projektStrasse,
                projektPlz: formData.projektPlz,
                projektOrt: formData.projektOrt,
                // Alle E-Mails kombinieren: Kunden-E-Mails + zusätzliche E-Mails
                kundenEmails: [...(selectedKunde?.kundenEmails || []), ...zusaetzlicheEmails],
            };

            const url = isEditMode ? `/api/anfragen/${editAnfrage!.id}` : '/api/anfragen';
            const method = isEditMode ? 'PUT' : 'POST';

            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            if (!res.ok) throw new Error('Speichern fehlgeschlagen');
            const data = await res.json();
            onSuccess(data.id || editAnfrage?.id);
            onClose();
        } catch (err) {
            console.error(err);
            setError('Speichern fehlgeschlagen. Bitte erneut versuchen.');
        } finally {
            setSaving(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl mx-4 max-h-[90vh] overflow-hidden flex flex-col" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-rose-50">
                    <h2 className="text-lg font-semibold text-slate-900">
                        {isEditMode ? 'Anfrage bearbeiten' : 'Neue Anfrage anlegen'}
                    </h2>
                    <Button variant="ghost" size="sm" onClick={onClose}>
                        <X className="w-5 h-5" />
                    </Button>
                </div>

                <div className="flex-1 overflow-auto p-6">
                    {subView === 'selectKunde' && (
                        <KundenAuswahlView
                            onSelect={handleKundeSelect}
                            onCreateNew={() => setSubView('createKunde')}
                            onBack={() => setSubView('main')}
                        />
                    )}

                    {subView === 'createKunde' && (
                        <KundeAnlegenView
                            onSuccess={handleKundeCreated}
                            onBack={() => setSubView('selectKunde')}
                        />
                    )}

                    {subView === 'main' && (
                        <div className="space-y-4">
                            {error && (
                                <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">{error}</div>
                            )}

                            {/* Kunde auswählen */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">
                                    <User className="w-4 h-4 inline-block mr-1" />
                                    Kunde *
                                </label>
                                {selectedKunde ? (
                                    <div className="flex items-center gap-3 p-3 bg-rose-50 border border-rose-200 rounded-lg">
                                        <Building2 className="w-5 h-5 text-rose-600" />
                                        <div className="flex-1">
                                            <p className="font-medium text-slate-900">{selectedKunde.name}</p>
                                            <p className="text-sm text-slate-500">{selectedKunde.kundennummer}</p>
                                        </div>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={() => setSubView('selectKunde')}
                                            className="border-rose-300 text-rose-700 hover:bg-rose-100"
                                        >
                                            Ändern
                                        </Button>
                                    </div>
                                ) : (
                                    <Button
                                        variant="outline"
                                        onClick={() => setSubView('selectKunde')}
                                        className="w-full justify-start text-slate-500 border-dashed"
                                    >
                                        <PlusCircle className="w-4 h-4 mr-2" />
                                        Kunde auswählen oder anlegen...
                                    </Button>
                                )}
                            </div>

                            {/* Bauvorhaben */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">
                                    <FileText className="w-4 h-4 inline-block mr-1" />
                                    Bauvorhaben *
                                </label>
                                <Input
                                    value={formData.bauvorhaben}
                                    onChange={e => setFormData(prev => ({ ...prev, bauvorhaben: e.target.value }))}
                                    placeholder="Beschreibung des Bauvorhabens"
                                />
                            </div>

                            {/* Betrag */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">
                                    <Euro className="w-4 h-4 inline-block mr-1" />
                                    Bruttobetrag (€)
                                </label>
                                <Input
                                    type="number"
                                    step="0.01"
                                    value={formData.betrag ?? ''}
                                    onChange={e => setFormData(prev => ({ ...prev, betrag: e.target.value ? parseFloat(e.target.value) : undefined }))}
                                    placeholder="0,00"
                                />
                            </div>

                            {/* Projektadresse */}
                            <div className="pt-2 border-t border-slate-100">
                                <div className="flex items-center justify-between mb-2">
                                    <label className="block text-sm font-medium text-slate-700">
                                        <MapPin className="w-4 h-4 inline-block mr-1" />
                                        Projektadresse
                                    </label>
                                    {selectedKunde && (
                                        <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={adresseGleichKunde}
                                                onChange={e => setAdresseGleichKunde(e.target.checked)}
                                                className="w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                                            />
                                            Kundenadresse übernehmen
                                        </label>
                                    )}
                                </div>
                                <AddressAutocomplete
                                    showLabels={false}
                                    disabled={adresseGleichKunde}
                                    value={{
                                        strasse: formData.projektStrasse,
                                        plz: formData.projektPlz,
                                        ort: formData.projektOrt
                                    }}
                                    onChange={next => {
                                        setFormData(prev => ({
                                            ...prev,
                                            projektStrasse: next.strasse,
                                            projektPlz: next.plz,
                                            projektOrt: next.ort
                                        }));
                                        setAdresseGleichKunde(false);
                                    }}
                                />
                            </div>

                            {/* E-Mail-Adressen */}
                            <div className="pt-2 border-t border-slate-100">
                                <EmailListInput
                                    emails={zusaetzlicheEmails}
                                    onChange={setZusaetzlicheEmails}
                                    kundenEmails={selectedKunde?.kundenEmails || []}
                                    label="E-Mail-Adressen"
                                    placeholder="Weitere E-Mail hinzufügen (z.B. Statiker)..."
                                />
                            </div>
                        </div>
                    )}
                </div>

                {subView === 'main' && (
                    <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-slate-200 bg-slate-50">
                        <Button variant="outline" onClick={onClose} disabled={saving}>Abbrechen</Button>
                        <Button onClick={handleSubmit} disabled={saving || !selectedKunde} className="bg-rose-600 text-white hover:bg-rose-700">
                            {saving ? 'Speichern...' : (isEditMode ? 'Änderungen speichern' : 'Anfrage anlegen')}
                        </Button>
                    </div>
                )}
            </div>
        </div>
    );
};


// ==================== ANFRAGE DETAIL VIEW ====================
interface AnfrageDetailViewProps {
    anfrage: AnfrageDetail;
    onBack: () => void;
    onEdit: () => void;
    onRefresh: () => void;
}

const AnfrageDetailView: React.FC<AnfrageDetailViewProps> = ({ anfrage, onBack, onEdit, onRefresh }) => {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [activeTab, setActiveTab] = useState<'emails' | 'geschaeftsdokumente' | 'dokumente' | 'beschreibung' | 'notizen'>('emails');
    const [kurzbeschreibung, setKurzbeschreibung] = useState(anfrage.kurzbeschreibung || '');
    const [savingDesc, setSavingDesc] = useState(false);

    useEffect(() => {
        setKurzbeschreibung(anfrage.kurzbeschreibung || '');
    }, [anfrage.kurzbeschreibung]);

    const handleSaveDescription = async () => {
        setSavingDesc(true);
        try {
            const response = await fetch(`/api/anfragen/${anfrage.id}/kurzbeschreibung`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'text/plain'
                },
                body: kurzbeschreibung
            });
            if (response.ok) {
                onRefresh();
            } else {
                console.error("Failed to save description");
            }
        } catch (e) {
            console.error("Error saving description", e);
        } finally {
            setSavingDesc(false);
        }
    };

    // Notizen State
    const [notizen, setNotizen] = useState<AnfrageNotiz[]>([]);
    const [showNotizModal, setShowNotizModal] = useState(false);
    const [neueNotiz, setNeueNotiz] = useState('');
    const [mobileSichtbar, setMobileSichtbar] = useState(true);
    const [nurFuerErsteller, setNurFuerErsteller] = useState(false); // New state for private notes
    const [editingNotiz, setEditingNotiz] = useState<AnfrageNotiz | null>(null);
    const [savingNotiz, setSavingNotiz] = useState(false);
    const [uploadingNotizBildId, setUploadingNotizBildId] = useState<number | null>(null);
    const [notizBildViewer, setNotizBildViewer] = useState<{ images: { url: string; name?: string }[]; startIndex: number } | null>(null);

    // Notizen laden
    const loadNotizen = useCallback(async () => {
        if (anfrage.id) {
            try {
                const res = await fetch(`/api/anfragen/${anfrage.id}/notizen`);
                if (res.ok) {
                    setNotizen(await res.json());
                }
            } catch {
                setNotizen([]);
            }
        }
    }, [anfrage.id]);

    useEffect(() => {
        loadNotizen();
    }, [loadNotizen]);

    // Ausgangs-Geschäftsdokumente State
    const [ausgangsDokumente, setAusgangsDokumente] = useState<AusgangsGeschaeftsDokument[]>([]);

    // Ausgangs-Geschäftsdokumente laden
    const loadAusgangsDokumente = useCallback(async () => {
        if (anfrage.id) {
            try {
                const res = await fetch(`/api/ausgangs-dokumente/anfrage/${anfrage.id}`);
                if (res.ok) {
                    setAusgangsDokumente(await res.json());
                }
            } catch {
                setAusgangsDokumente([]);
            }
        }
    }, [anfrage.id]);

    useEffect(() => {
        loadAusgangsDokumente();
    }, [loadAusgangsDokumente]);

    // Cross-tab: Geschäftsdokumente automatisch aktualisieren wenn im DocumentEditor gespeichert wird
    useEffect(() => {
        return onDokumentChanged((event) => {
            if (event.anfrageId === anfrage.id) {
                loadAusgangsDokumente();
            }
        });
    }, [anfrage.id, loadAusgangsDokumente]);

    // Notiz speichern
    const handleSaveNotiz = async () => {
        if (!neueNotiz.trim()) return;
        setSavingNotiz(true);
        try {
            const userProfileId = localStorage.getItem('frontendUserSelection');
            const profileData = userProfileId ? JSON.parse(userProfileId) : null;

            const url = editingNotiz
                ? `/api/anfragen/${anfrage.id}/notizen/${editingNotiz.id}`
                : `/api/anfragen/${anfrage.id}/notizen`;

            const method = editingNotiz ? 'PATCH' : 'POST';

            const res = await fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                    ...(profileData?.id ? { 'X-User-Profile-Id': profileData.id.toString() } : {})
                },
                body: JSON.stringify({
                    notiz: neueNotiz.trim(),
                    mobileSichtbar: mobileSichtbar,
                    nurFuerErsteller: nurFuerErsteller, // Include new state
                })
            });
            if (res.ok) {
                setNeueNotiz('');
                setMobileSichtbar(true);
                setNurFuerErsteller(false); // Reset new state
                setEditingNotiz(null);
                setShowNotizModal(false);
                loadNotizen();
            }
        } catch (e) {
            console.error('Notiz speichern fehlgeschlagen', e);
        } finally {
            setSavingNotiz(false);
        }
    };

    const handleDeleteNotiz = async (notizId: number) => {
        if (!await confirmDialog({ title: 'Notiz löschen', message: 'Möchten Sie diese Notiz wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const userProfileId = localStorage.getItem('frontendUserSelection');
            const profileData = userProfileId ? JSON.parse(userProfileId) : null;

            const res = await fetch(`/api/anfragen/${anfrage.id}/notizen/${notizId}`, {
                method: 'DELETE',
                headers: {
                    ...(profileData?.id ? { 'X-User-Profile-Id': profileData.id.toString() } : {})
                }
            });

            if (res.ok) {
                loadNotizen();
            } else {
                toast.error('Fehler beim Löschen der Notiz');
            }
        } catch (e) {
            console.error('Fehler beim Löschen:', e);
            toast.error('Fehler beim Löschen');
        }
    };

    const openCreateNotizModal = () => {
        setNeueNotiz('');
        setMobileSichtbar(true);
        setNurFuerErsteller(false); // Reset for new note
        setEditingNotiz(null);
        setShowNotizModal(true);
    };

    const openEditNotizModal = (notiz: AnfrageNotiz) => {
        setNeueNotiz(notiz.notiz);
        setMobileSichtbar(notiz.mobileSichtbar);
        setNurFuerErsteller(notiz.nurFuerErsteller || false); // Set for editing
        setEditingNotiz(notiz);
        setShowNotizModal(true);
    };

    // Notiz Bild Upload
    const handleNotizBildUpload = async (notizId: number, file: File) => {
        setUploadingNotizBildId(notizId);
        try {
            const userProfileId = localStorage.getItem('frontendUserSelection');
            const profileData = userProfileId ? JSON.parse(userProfileId) : null;

            const formData = new FormData();
            formData.append('datei', file);

            const res = await fetch(`/api/anfragen/${anfrage.id}/notizen/${notizId}/bilder`, {
                method: 'POST',
                headers: {
                    ...(profileData?.id ? { 'X-User-Profile-Id': profileData.id.toString() } : {})
                },
                body: formData
            });
            if (res.ok) {
                const bild = await res.json();
                setNotizen(prev => appendBildToNotiz(prev, notizId, bild));
                void loadNotizen();
            } else {
                toast.error('Fehler beim Hochladen des Bildes');
            }
        } catch (e) {
            console.error('Bild-Upload fehlgeschlagen:', e);
            toast.error('Fehler beim Hochladen');
        } finally {
            setUploadingNotizBildId(null);
        }
    };

    // Notiz Bild Delete
    const handleNotizBildDelete = async (notizId: number, bildId: number) => {
        if (!await confirmDialog({ title: 'Bild löschen', message: 'Bild wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const userProfileId = localStorage.getItem('frontendUserSelection');
            const profileData = userProfileId ? JSON.parse(userProfileId) : null;

            const res = await fetch(`/api/anfragen/${anfrage.id}/notizen/${notizId}/bilder/${bildId}`, {
                method: 'DELETE',
                headers: {
                    ...(profileData?.id ? { 'X-User-Profile-Id': profileData.id.toString() } : {})
                }
            });
            if (res.ok) {
                setNotizen(prev => removeBildFromNotiz(prev, notizId, bildId));
                void loadNotizen();
            } else {
                toast.error('Fehler beim Löschen des Bildes');
            }
        } catch (e) {
            console.error('Bild-Löschen fehlgeschlagen:', e);
            toast.error('Fehler beim Löschen');
        }
    };

    const kundenEmails = anfrage.kundenEmails || [];

    const nettoPreis = (anfrage.betrag || 0) / 1.19;
    const adresse = [anfrage.projektStrasse, anfrage.projektPlz, anfrage.projektOrt].filter(Boolean).join(', ');

    // Header Card
    const header = (
        <Card className="p-6">
            <div className="flex flex-col xl:flex-row gap-8 justify-between">
                <div className="flex items-start gap-4">
                    <Button variant="ghost" size="sm" onClick={onBack} className="-ml-2 h-auto py-1 self-start">
                        <ArrowLeft className="w-5 h-5" />
                    </Button>
                    <div className="w-16 h-16 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center text-xl font-bold shrink-0">
                        <FileText className="w-8 h-8" />
                    </div>
                    <div>
                        <div className="flex items-center gap-3 flex-wrap">
                            <h1 className="text-2xl font-bold text-slate-900">{anfrage.bauvorhaben}</h1>
                            {anfrage.anfragesnummer && (
                                <span className="px-2.5 py-0.5 rounded-full text-xs font-medium border bg-rose-50 text-rose-700 border-rose-200">
                                    Anfrage {anfrage.anfragesnummer}
                                </span>
                            )}
                            {anfrage.projektId && (
                                <span className="px-2.5 py-0.5 rounded-full text-xs font-medium border bg-green-50 text-green-700 border-green-200">
                                    Projekt #{anfrage.projektId}
                                </span>
                            )}
                        </div>
                        <div className="mt-1 text-slate-500 space-y-0.5">
                            {anfrage.kundenName && <p className="flex items-center gap-2"><User className="w-4 h-4" /> {anfrage.kundenName}</p>}
                            {adresse && <p className="flex items-center gap-2"><MapPin className="w-4 h-4" /> {adresse}</p>}
                            {anfrage.anlegedatum && <p className="flex items-center gap-2"><Calendar className="w-4 h-4" /> {formatDate(anfrage.anlegedatum)}</p>}
                        </div>
                    </div>
                </div>

                {/* Stats Row */}
                <div className="flex items-center gap-6 flex-1 max-w-2xl">
                    <div className="flex flex-col items-center px-4 py-2 border-r border-slate-200">
                        <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Brutto</p>
                        <p className="text-base font-semibold text-slate-800">{formatCurrency(anfrage.betrag)}</p>
                    </div>
                    <div className="flex flex-col items-center px-4 py-2">
                        <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Netto</p>
                        <p className="text-base font-semibold text-slate-800">{formatCurrency(nettoPreis)}</p>
                    </div>
                </div>

                <div className="flex items-start gap-2">
                    <Button variant="outline" onClick={onEdit}>
                        <Edit2 className="w-4 h-4 mr-2" /> Bearbeiten
                    </Button>
                </div>
            </div>
        </Card>
    );

    // Main Content (Tabs + Tab Content)
    const mainContent = (
        <>
            {/* Tab Navigation */}
            <div className="flex gap-2 mb-6 border-b border-slate-200 pb-2 overflow-x-auto">
                <button
                    onClick={() => setActiveTab('emails')}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap",
                        activeTab === 'emails'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <Mail className="w-4 h-4 inline-block mr-2" />
                    E-Mails ({anfrage.emails?.length || 0})
                </button>
                <button
                    onClick={() => setActiveTab('geschaeftsdokumente')}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap",
                        activeTab === 'geschaeftsdokumente'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <FileText className="w-4 h-4 inline-block mr-2" />
                    Geschäftsdokumente ({ausgangsDokumente.length})
                </button>
                <button
                    onClick={() => setActiveTab('dokumente')}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap",
                        activeTab === 'dokumente'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <FolderOpen className="w-4 h-4 inline-block mr-2" />
                    Dateien ({anfrage.dokumente?.length || 0})
                </button>
                <button
                    onClick={() => setActiveTab('beschreibung')}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap",
                        activeTab === 'beschreibung'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <FileText className="w-4 h-4 inline-block mr-2" />
                    Beschreibung
                </button>
                <button
                    onClick={() => setActiveTab('notizen')}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap",
                        activeTab === 'notizen'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <StickyNote className="w-4 h-4 inline-block mr-2" />
                    Bau Tagebuch ({notizen.length})
                </button>
            </div>

            {/* Tab Content: Beschreibung */}
            {activeTab === 'beschreibung' && (
                <div className="space-y-4">
                    <textarea
                        className="flex min-h-[300px] w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-600 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                        placeholder="Kurzbeschreibung des Anfrages..."
                        value={kurzbeschreibung}
                        onChange={(e) => setKurzbeschreibung(e.target.value)}
                    />
                    <div className="flex justify-end">
                        <Button
                            onClick={handleSaveDescription}
                            disabled={savingDesc || kurzbeschreibung === (anfrage.kurzbeschreibung || '')}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {savingDesc ? <RefreshCw className="w-4 h-4 animate-spin mr-2" /> : <FileText className="w-4 h-4 mr-2" />}
                            Speichern
                        </Button>
                    </div>
                </div>
            )}

            {/* Tab Content: E-Mails */}
            {activeTab === 'emails' && (
                <EmailsTab
                    emails={(anfrage.emails || []).map((e) => ({
                        id: e.id,
                        subject: e.subject,
                        from: e.sender || e.fromAddress,
                        fromAddress: e.fromAddress || e.sender,
                        to: e.recipient || e.recipients?.join(', '),
                        bodyHtml: e.htmlBody || e.body,
                        bodyPreview: e.body,
                        direction: e.direction as 'IN' | 'OUT',
                        sentAt: e.sentAt,
                        parentEmailId: e.parentEmailId ?? e.parentId,
                        replyCount: e.replyCount,
                        attachments: e.attachments?.map((att) => ({
                            id: att.id,
                            originalFilename: att.originalFilename,
                            storedFilename: att.storedFilename,
                            contentId: att.contentId,
                            inline: att.inline,
                        })),
                    }))}
                    anfrageId={anfrage.id}
                    entityName={anfrage.bauvorhaben}
                    kundenEmail={kundenEmails[0]}
                    anfrage={{
                        bauvorhaben: anfrage.bauvorhaben,
                        kundenName: anfrage.kundenName,
                        kundenEmails: anfrage.kundenEmails,
                        kundenAnrede: anfrage.kundenAnrede,
                        kundenAnsprechpartner: anfrage.kundenAnsprechpartner,
                    }}
                    onEmailSent={onRefresh}
                />
            )}

            {/* Tab Content: Dokumente */}
            {activeTab === 'dokumente' && (
                <div className="space-y-4">
                    <DocumentManager anfrageId={anfrage.id} />
                </div>
            )}

            {/* Tab Content: Geschäftsdokumente */}
            {activeTab === 'geschaeftsdokumente' && (
                <DokumentHierarchie
                    ausgangsDokumente={ausgangsDokumente}
                    anfrageId={anfrage.id}
                    allowedTypes={['ANGEBOT', 'AUFTRAGSBESTAETIGUNG']}
                    hideRechnungActions={true}
                    onRefresh={loadAusgangsDokumente}
                    confirmDialog={confirmDialog}
                    toast={toast}
                />
            )}


            {/* Tab Content: Notizen */}
            {activeTab === 'notizen' && (
                <div className="space-y-4">
                    <div className="flex justify-between items-center">
                        <h3 className="text-lg font-medium text-slate-900">Bau Tagebuch</h3>
                        <Button onClick={openCreateNotizModal} className="bg-rose-600 text-white hover:bg-rose-700">
                            <Plus className="w-4 h-4 mr-2" /> Neuer Eintrag
                        </Button>
                    </div>

                    <div className="space-y-3">
                        {notizen.length > 0 ? (
                            notizen.map((n) => (
                                <div key={n.id} className="p-4 bg-white rounded-lg border border-slate-200 shadow-sm relative group">
                                    <div className="flex justify-between items-start mb-2">
                                        <div className="flex items-center gap-2">
                                            <div className="w-8 h-8 rounded-full bg-rose-100 flex items-center justify-center text-rose-600 font-bold text-xs uppercase">
                                                {(n.mitarbeiterVorname?.[0] || '')}{(n.mitarbeiterNachname?.[0] || '')}
                                            </div>
                                            <div>
                                                <div className="flex items-center gap-2">
                                                    <p className="text-sm font-medium text-slate-900">{n.mitarbeiterVorname} {n.mitarbeiterNachname}</p>
                                                    {!n.mobileSichtbar && (
                                                        <span className="text-[10px] px-1 rounded bg-white/50 border border-violet-200" title="Nicht auf Mobile sichtbar">
                                                            Mobile ausgeblendet
                                                        </span>
                                                    )}
                                                    {n.nurFuerErsteller && (
                                                        <span className="flex items-center gap-1 text-[10px] px-1 rounded bg-amber-50 border border-amber-200 text-amber-700" title="Nur für mich sichtbar">
                                                            <Lock className="w-3 h-3" /> Privat
                                                        </span>
                                                    )}
                                                    <span>
                                                        <p className="text-xs text-slate-500">
                                                            {new Date(n.erstelltAm).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })} {new Date(n.erstelltAm).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}
                                                        </p>
                                                    </span>
                                                </div>
                                            </div>
                                            <div className="flex gap-1 opacity-100 sm:opacity-0 group-hover:opacity-100 transition-opacity">
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    className="h-8 w-8 p-0 text-slate-400 hover:text-slate-600"
                                                    onClick={() => openEditNotizModal(n)}
                                                    title="Bearbeiten"
                                                >
                                                    <Edit2 className="w-4 h-4" />
                                                </Button>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    className="h-8 w-8 p-0 text-slate-400 hover:text-red-600"
                                                    onClick={() => handleDeleteNotiz(n.id)}
                                                    title="Löschen"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </Button>
                                            </div>
                                        </div>
                                        <p className="text-slate-700 whitespace-pre-wrap text-sm">{n.notiz}</p>

                                        {/* Bilder */}
                                        {n.bilder && n.bilder.length > 0 && (
                                            <div className="mt-3 grid grid-cols-4 gap-2">
                                                {n.bilder.map(bild => (
                                                    <div key={bild.id} className="relative group/img">
                                                        <button
                                                            onClick={() => setNotizBildViewer({ images: n.bilder!.map(b => ({ url: b.url, name: b.originalDateiname })), startIndex: n.bilder!.indexOf(bild) })}
                                                            className="aspect-square rounded-lg overflow-hidden bg-slate-100 hover:ring-2 hover:ring-rose-500 transition-all w-full"
                                                        >
                                                            <img
                                                                src={bild.url}
                                                                alt={bild.originalDateiname}
                                                                className="w-full h-full object-cover"
                                                            />
                                                        </button>
                                                        <button
                                                            onClick={() => handleNotizBildDelete(n.id, bild.id)}
                                                            className="absolute top-1 right-1 p-1 bg-red-500 hover:bg-red-600 text-white rounded-full shadow opacity-0 group-hover/img:opacity-100 transition-opacity"
                                                            title="Bild löschen"
                                                        >
                                                            <X className="w-3 h-3" />
                                                        </button>
                                                    </div>
                                                ))}
                                            </div>
                                        )}

                                        {/* Bild Upload */}
                                        <div className="mt-3 flex items-center gap-2">
                                            {uploadingNotizBildId === n.id ? (
                                                <span className="text-rose-600 text-sm flex items-center gap-2">
                                                    <RefreshCw className="w-4 h-4 animate-spin mr-1" />
                                                    Wird hochgeladen...
                                                </span>
                                            ) : (
                                                <label className="flex items-center gap-1 text-xs text-slate-500 hover:text-rose-600 px-2 py-1 rounded-lg hover:bg-rose-50 cursor-pointer transition-colors">
                                                    <Upload className="w-3.5 h-3.5" />
                                                    Bild hinzufügen
                                                    <input
                                                        type="file"
                                                        accept="image/*"
                                                        multiple
                                                        className="hidden"
                                                        onChange={async (e) => {
                                                            const files = e.target.files;
                                                            if (files && files.length > 0) {
                                                                const fileArray = Array.from(files);
                                                                for (const file of fileArray) {
                                                                    await handleNotizBildUpload(n.id, file);
                                                                }
                                                            }
                                                            e.target.value = '';
                                                        }}
                                                    />
                                                </label>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            ))
                        ) : (
                            <div className="flex flex-col items-center justify-center py-12 text-slate-500 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                                <StickyNote className="w-12 h-12 text-slate-300 mb-3" />
                                <p>Kein Tagebuch vorhanden.</p>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Notiz Modal */}
            <Dialog open={showNotizModal} onOpenChange={setShowNotizModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{editingNotiz ? 'Eintrag bearbeiten' : 'Neuer Eintrag'}</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <div>
                            <label className="text-sm font-medium mb-1 block">Notiz</label>
                            <textarea
                                className="w-full min-h-[150px] p-3 rounded-lg border border-slate-200 focus:outline-none focus:ring-2 focus:ring-rose-600"
                                placeholder="Notiz eingeben..."
                                value={neueNotiz}
                                onChange={(e) => setNeueNotiz(e.target.value)}
                            />
                        </div>
                        <div className="space-y-3">
                            <label className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 bg-slate-50 cursor-pointer hover:bg-slate-100 transition-colors">
                                <input
                                    type="checkbox"
                                    id="mobileVisible"
                                    checked={mobileSichtbar}
                                    onChange={(e) => setMobileSichtbar(e.target.checked)}
                                    className="w-4 h-4 text-rose-600 rounded border-slate-300 focus:ring-rose-500"
                                />
                                <div className="flex-1">
                                    <div className="text-sm font-medium text-slate-900">In mobiler App anzeigen</div>
                                    <div className="text-xs text-slate-500">Diese Notiz wird in der mobilen App für Mitarbeiter angezeigt</div>
                                </div>
                            </label>

                            <label className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 bg-slate-50 cursor-pointer hover:bg-slate-100 transition-colors">
                                <input
                                    type="checkbox"
                                    id="nurFuerErsteller"
                                    checked={nurFuerErsteller}
                                    onChange={(e) => setNurFuerErsteller(e.target.checked)}
                                    className="w-4 h-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                />
                                <div className="flex-1">
                                    <div className="text-sm font-medium text-slate-900">Nur für mich sichtbar</div>
                                    <div className="text-xs text-slate-500">Andere Mitarbeiter sehen diesen Eintrag nicht</div>
                                </div>
                                <Lock className="w-4 h-4 text-slate-400" />
                            </label>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowNotizModal(false)}>Abbrechen</Button>
                        <Button onClick={handleSaveNotiz} disabled={savingNotiz || !neueNotiz.trim()} className="bg-rose-600 text-white hover:bg-rose-700">
                            {savingNotiz ? 'Speichert...' : 'Speichern'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Bild Viewer Modal */}
            {/* Bild Viewer Modal */}
            <ImageViewer
                src={notizBildViewer ? notizBildViewer.images[notizBildViewer.startIndex]?.url : null}
                onClose={() => setNotizBildViewer(null)}
                alt="Vollbild"
                images={notizBildViewer?.images}
                startIndex={notizBildViewer?.startIndex}
            />
        </>
    );

    // Side Content
    const sideContent = (
        <>
            <h2 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                <FileText className="w-5 h-5 text-rose-500" />
                Anfragesdaten
            </h2>
            <div className="space-y-4">
                <div className="p-3 bg-slate-50 rounded-lg">
                    <p className="text-xs text-slate-500">Kunde</p>
                    <p className="font-medium text-slate-900">{anfrage.kundenName || '-'}</p>
                </div>
                {anfrage.kundennummer && (
                    <div className="p-3 bg-slate-50 rounded-lg">
                        <p className="text-xs text-slate-500">Kundennummer</p>
                        <p className="font-medium text-slate-900">{anfrage.kundennummer}</p>
                    </div>
                )}
                {anfrage.kundenAnsprechpartner && (
                    <div className="p-3 bg-slate-50 rounded-lg">
                        <p className="text-xs text-slate-500">Ansprechpartner</p>
                        <p className="font-medium text-slate-900">{anfrage.kundenAnsprechpartner}</p>
                    </div>
                )}
                <div className="p-3 bg-slate-50 rounded-lg">
                    <p className="text-xs text-slate-500">Anfragesnummer</p>
                    <p className="font-medium text-slate-900">{anfrage.anfragesnummer || '-'}</p>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg">
                    <p className="text-xs text-slate-500">Anlagedatum</p>
                    <p className="font-medium text-slate-900">{formatDate(anfrage.anlegedatum)}</p>
                </div>
                {kundenEmails.length > 0 && (
                    <div className="p-3 bg-slate-50 rounded-lg">
                        <p className="text-xs text-slate-500 mb-1">Kunden-E-Mails</p>
                        {kundenEmails.map((email) => (
                            <a key={email} href={`mailto:${email}`} className="block text-rose-600 hover:underline text-sm truncate">
                                {email}
                            </a>
                        ))}
                    </div>
                )}
                {(anfrage.kundenTelefon || anfrage.kundenMobiltelefon) && (
                    <div className="p-3 bg-slate-50 rounded-lg">
                        <p className="text-xs text-slate-500 mb-1">Telefon</p>
                        <div className="space-y-1">
                            {anfrage.kundenTelefon && (
                                <a href={`tel:${anfrage.kundenTelefon}`} className="block text-rose-600 hover:underline text-sm">
                                    {anfrage.kundenTelefon}
                                </a>
                            )}
                            {anfrage.kundenMobiltelefon && (
                                <a href={`tel:${anfrage.kundenMobiltelefon}`} className="block text-rose-600 hover:underline text-sm">
                                    {anfrage.kundenMobiltelefon} (Mobil)
                                </a>
                            )}
                        </div>
                    </div>
                )}
            </div>

            {/* Projektadresse mit Karte */}
            {(anfrage.projektStrasse || anfrage.projektPlz || anfrage.projektOrt ||
              anfrage.kundenStrasse || anfrage.kundenPlz || anfrage.kundenOrt) && (
                <div className="mt-6 pt-6 border-t border-slate-100">
                    <h3 className="text-sm font-medium text-slate-900 mb-3 flex items-center gap-2">
                        <MapPin className="w-4 h-4 text-rose-500" />
                        Projektadresse
                    </h3>
                    <div className="p-3 bg-slate-50 rounded-lg mb-3">
                        <p className="font-medium text-slate-900">{anfrage.projektStrasse || anfrage.kundenStrasse || '-'}</p>
                        <p className="text-sm text-slate-600">
                            {anfrage.projektPlz || anfrage.kundenPlz} {anfrage.projektOrt || anfrage.kundenOrt}
                        </p>
                    </div>
                    <GoogleMapsEmbed
                        strasse={anfrage.projektStrasse || anfrage.kundenStrasse}
                        plz={anfrage.projektPlz || anfrage.kundenPlz}
                        ort={anfrage.projektOrt || anfrage.kundenOrt}
                        className="h-48"
                    />
                </div>
            )}
        </>
    );

    return (
        <PageLayout>
            <DetailLayout
                header={header}
                mainContent={mainContent}
                sideContent={sideContent}
            />
        </PageLayout>
    );
};

// ==================== MAIN COMPONENT ====================
export default function AnfrageEditor() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [viewMode, setViewMode] = useState<'list' | 'detail'>('list');
    const [anfragen, setAnfragen] = useState<Anfrage[]>([]);
    const [selectedAnfrage, setSelectedAnfrage] = useState<AnfrageDetail | null>(null);
    const [loading, setLoading] = useState(false);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showEditModal, setShowEditModal] = useState(false);

    // Deep-link: restore detail view from URL param ?anfrageId=123
    const deepLinkProcessed = useRef(false);
    useEffect(() => {
        if (deepLinkProcessed.current) return;
        const anfrageIdParam = searchParams.get('anfrageId');
        if (!anfrageIdParam) return;
        const anfrageId = Number(anfrageIdParam);
        if (isNaN(anfrageId) || !anfrageId) return;
        deepLinkProcessed.current = true;
        (async () => {
            try {
                setLoading(true);
                const [anfrageRes, emailsRes, dokumenteRes] = await Promise.all([
                    fetch(`/api/anfragen/${anfrageId}`),
                    fetch(`/api/emails/anfrage/${anfrageId}`),
                    fetch(`/api/anfragen/${anfrageId}/dokumente`),
                ]);
                if (anfrageRes.ok) {
                    const anfrageData = await anfrageRes.json();
                    const emails = emailsRes.ok ? await emailsRes.json() : [];
                    const dokumente = dokumenteRes.ok ? await dokumenteRes.json() : [];
                    setSelectedAnfrage({
                        ...anfrageData,
                        emails: Array.isArray(emails) ? emails : [],
                        dokumente: Array.isArray(dokumente) ? dokumente : [],
                    });
                    setViewMode('detail');
                }
            } catch (err) {
                console.error('Deep-link: Anfrage konnte nicht geladen werden:', err);
            } finally {
                setLoading(false);
            }
        })();
    }, [searchParams]);

    // Filters
    const [filters, setFilters] = useState({
        q: "",
        jahr: "",
    });
    const [verfuegbareJahre, setVerfuegbareJahre] = useState<number[]>([]);

    // Load Jahre
    useEffect(() => {
        fetch('/api/anfragen/jahre')
            .then(res => res.json())
            .then(data => {
                const jahre = Array.isArray(data) ? data : [];
                setVerfuegbareJahre(jahre);
            })
            .catch(console.error);
    }, []);

    // Fetch List
    const loadAnfragen = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", String(PAGE_SIZE));
            if (filters.q) {
                params.set("q", filters.q);
            }
            if (filters.jahr) params.set("jahr", filters.jahr);

            const res = await fetch(`/api/anfragen?${params.toString()}`);
            if (!res.ok) throw new Error("Fehler beim Laden");
            const data = await res.json();

            // API gibt Array oder Objekt mit gesamt zurück
            if (Array.isArray(data)) {
                setAnfragen(data);
                setTotal(data.length);
            } else {
                setAnfragen(Array.isArray(data.anfragen) ? data.anfragen : []);
                setTotal(typeof data.gesamt === "number" ? data.gesamt : 0);
            }
        } catch (err) {
            console.error(err);
            setAnfragen([]);
            setTotal(0);
        } finally {
            setLoading(false);
        }
    }, [page, filters]);

    useEffect(() => {
        if (viewMode === 'list') {
            loadAnfragen();
        }
    }, [loadAnfragen, viewMode]);

    // Toggle abgeschlossen status
    const handleToggleAbgeschlossen = async (anfrageId: number, abgeschlossen: boolean) => {
        try {
            const anfrage = anfragen.find(a => a.id === anfrageId);
            if (!anfrage) return;

            const res = await fetch(`/api/anfragen/${anfrageId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    bauvorhaben: anfrage.bauvorhaben,
                    kundenId: anfrage.kundenId,
                    betrag: anfrage.betrag,
                    projektStrasse: anfrage.projektStrasse,
                    projektPlz: anfrage.projektPlz,
                    projektOrt: anfrage.projektOrt,
                    abgeschlossen: abgeschlossen
                })
            });

            if (res.ok) {
                // Optimistic update
                setAnfragen(prev => prev.map(a =>
                    a.id === anfrageId ? { ...a, abgeschlossen } : a
                ));
            }
        } catch (err) {
            console.error('Fehler beim Aktualisieren:', err);
        }
    };

    // Handlers
    const handleFilterChange = (key: string, value: string) => {
        setFilters((prev) => ({ ...prev, [key]: value }));
    };

    const handleFilterSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        loadAnfragen();
    };

    const handleResetFilters = () => {
        setFilters({ q: "", jahr: "" });
        setPage(0);
    };

    // Handlers (Refactored to loadDetails below)

    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

    const statusText = useMemo(() => {
        if (loading) return 'Anfragen werden geladen...';
        if (total === 0) return 'Keine Anfragen gefunden.';
        const start = page * PAGE_SIZE + 1;
        const end = Math.min(start + anfragen.length - 1, total);
        return `Zeige ${start}-${end} von ${total} Anfragenn`;
    }, [loading, total, page, anfragen.length]);

    const loadDetails = async (id: number) => {
        try {
            setLoading(true);
            const [anfrageRes, emailsRes, dokumenteRes] = await Promise.all([
                fetch(`/api/anfragen/${id}`),
                fetch(`/api/emails/anfrage/${id}`),
                fetch(`/api/anfragen/${id}/dokumente`),
            ]);
            if (anfrageRes.ok) {
                const anfrageData = await anfrageRes.json();
                const emails = emailsRes.ok ? await emailsRes.json() : [];
                const dokumente = dokumenteRes.ok ? await dokumenteRes.json() : [];
                setSelectedAnfrage({
                    ...anfrageData,
                    emails: Array.isArray(emails) ? emails : [],
                    dokumente: Array.isArray(dokumente) ? dokumente : [],
                });
                setViewMode('detail');
                setSearchParams({ anfrageId: String(id) }, { replace: true });
            }
        } catch (err) {
            console.error('Anfrage konnte nicht geladen werden:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleDetail = (anfrage: Anfrage) => {
        loadDetails(anfrage.id);
    };



    const handleEdit = () => {
        setShowEditModal(true);
    };

    // ... existing filter logic ...

    // Detail View
    if (viewMode === 'detail' && selectedAnfrage) {
        return (
            <>
                <AnfrageDetailView
                    anfrage={selectedAnfrage}
                    onBack={() => {
                        setSelectedAnfrage(null);
                        setViewMode('list');
                        setSearchParams({}, { replace: true });
                    }}
                    onEdit={handleEdit}
                    onRefresh={() => loadDetails(selectedAnfrage.id)}
                />

                {/* Edit Modal */}
                <AnfrageErstellenModal
                    isOpen={showEditModal}
                    onClose={() => setShowEditModal(false)}
                    onSuccess={(id) => loadDetails(id)}
                    editAnfrage={selectedAnfrage}
                />
            </>
        );
    }

    // ==================== LIST VIEW ====================
    return (
        <PageLayout
            ribbonCategory="Anfragesmanagement"
            title="ANFRAGESÜBERSICHT"
            subtitle="Übersicht und Verwaltung Ihrer Anfragen."
            actions={
                <>
                    <Button size="sm" onClick={() => setShowCreateModal(true)} className="bg-rose-600 text-white hover:bg-rose-700">
                        <Plus className="w-4 h-4 mr-2" />
                        Neue Anfrage
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => loadAnfragen()}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >

            {/* Filter */}
            <div className="bg-white p-6 rounded-2xl shadow-lg border border-slate-100">
                <form onSubmit={handleFilterSubmit} className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Freitext</label>
                        <input
                            type="text"
                            className="filter-input w-full mt-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                            placeholder="Bauvorhaben, Kunde..."
                            value={filters.q}
                            onChange={e => handleFilterChange('q', e.target.value)}
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Jahr</label>
                        <Select
                            value={filters.jahr}
                            onChange={(value) => handleFilterChange('jahr', value)}
                            options={[
                                { value: '', label: 'Alle Jahre' },
                                ...verfuegbareJahre.map(jahr => ({
                                    value: String(jahr),
                                    label: String(jahr)
                                }))
                            ]}
                            placeholder="Jahr wählen"
                            className="mt-1"
                        />
                    </div>
                    <div className="flex items-end gap-3 md:col-span-2 xl:col-span-2">
                        <Button type="submit" className="flex-1 bg-rose-600 text-white hover:bg-rose-700">Filtern</Button>
                        <Button type="button" variant="outline" className="flex-1" onClick={handleResetFilters}>Reset</Button>
                    </div>
                </form>
                <p className="text-xs text-gray-500 mt-3">Für Performance werden immer nur {PAGE_SIZE} Einträge auf einmal geladen.</p>
            </div>

            {/* Grid Content */}
            {loading ? (
                <div className="text-center py-8 text-slate-500">Anfragen werden geladen...</div>
            ) : anfragen.length === 0 ? (
                <div className="bg-white p-8 rounded-2xl text-center text-slate-500 border-dashed border-2">
                    <FileText className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                    Keine Anfragen gefunden.
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {anfragen.map((anfrage) => (
                        <AnfrageCard
                            key={anfrage.id}
                            anfrage={anfrage}
                            onClick={() => handleDetail(anfrage)}
                            onToggleAbgeschlossen={handleToggleAbgeschlossen}
                        />
                    ))}
                </div>
            )}

            {/* Pagination */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
                <p className="text-sm text-gray-600">{statusText}</p>
                <div className="flex gap-2 justify-end">
                    <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>
                        <ChevronLeft className="w-4 h-4" /> zurück
                    </Button>
                    <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
                        Weiter <ChevronRight className="w-4 h-4" />
                    </Button>
                </div>
            </div>

            {/* Create Modal */}
            <AnfrageErstellenModal
                isOpen={showCreateModal}
                onClose={() => setShowCreateModal(false)}
                onSuccess={() => {
                    loadAnfragen();
                }}
            />
        </PageLayout>
    );
}

export { AnfrageEditor };
