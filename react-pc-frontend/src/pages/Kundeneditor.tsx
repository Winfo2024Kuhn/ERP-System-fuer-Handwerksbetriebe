import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
    ArrowLeft,
    ChevronLeft,
    ChevronRight,
    Mail,
    MapPin,
    Phone,
    Plus,
    RefreshCw,
    Save,
    User,
    X,
    CreditCard,
    Edit2,
    Smartphone
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { type KundeDetail } from '../types';
import GoogleMapsEmbed from '../components/GoogleMapsEmbed';
import EmailHistory from '../components/EmailHistory';
import { DetailLayout } from '../components/DetailLayout';
import { Select } from '../components/ui/select-custom';
import { PageLayout } from '../components/layout/PageLayout';

const ANREDE_OPTIONS = [
    { value: '', label: 'Bitte wählen' },
    { value: 'HERR', label: 'Sehr geehrter Herr' },
    { value: 'FRAU', label: 'Sehr geehrte Frau' },
    { value: 'FAMILIE', label: 'Sehr geehrte Familie' },
    { value: 'DAMEN_HERREN', label: 'Sehr geehrte Damen und Herren' }
];

const PAGE_SIZE = 12;

const EMPTY_KUNDE: KundeDetail = {
    id: 0,
    kundennummer: '',
    name: '',
    anrede: '',
    ansprechspartner: '',
    strasse: '',
    plz: '',
    ort: '',
    telefon: '',
    mobiltelefon: '',
    zahlungsziel: 8,
    kundenEmails: []
};



// GoogleMapsEmbed imported from components

// EmailHistory imported from components

// ==================== DETAIL VIEW ====================

interface KundenDetailViewProps {
    kunde: KundeDetail;
    onBack: () => void;
    onEdit: () => void;
}

const KundenDetailView: React.FC<KundenDetailViewProps> = ({ kunde, onBack, onEdit }) => {
    const initials = kunde.name.slice(0, 2).toUpperCase();

    // Formatter helpers
    const formatCurrency = (val?: number) => new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val || 0);

    const header = (
        <Card className="p-6">
            <div className="flex flex-col xl:flex-row gap-8 justify-between">
                <div className="flex items-start gap-4">
                    <Button variant="ghost" size="sm" onClick={onBack} className="-ml-2 h-auto py-1 self-start">
                        <ArrowLeft className="w-5 h-5" />
                    </Button>
                    <div className="w-16 h-16 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center text-xl font-bold shrink-0">
                        {initials}
                    </div>
                    <div>
                        <div className="flex items-center gap-3">
                            <h1 className="text-2xl font-bold text-slate-900">{kunde.name}</h1>
                            <span className="px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 text-xs font-medium border border-slate-200">
                                {kunde.kundennummer}
                            </span>
                        </div>
                        <div className="mt-1 text-slate-500 space-y-0.5">
                            {kunde.ansprechspartner && <p className="flex items-center gap-2"><User className="w-4 h-4" /> {kunde.ansprechspartner}</p>}
                            <p className="flex items-center gap-2"><MapPin className="w-4 h-4" /> {kunde.strasse}, {kunde.plz} {kunde.ort}</p>
                        </div>
                    </div>
                </div>

                {/* Bento Stats Grid */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 flex-1 max-w-4xl">
                    <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                        <p className="text-xs text-slate-500 uppercase tracking-wide">Gesamtumsatz</p>
                        <p className="text-lg font-semibold text-slate-900">{formatCurrency(kunde.statistik?.gesamtUmsatz)}</p>
                    </div>
                    <div className="bg-emerald-50 p-3 rounded-xl border border-emerald-100">
                        <p className="text-xs text-emerald-600 uppercase tracking-wide">Gewinn</p>
                        <p className="text-lg font-semibold text-emerald-900">{formatCurrency(kunde.statistik?.gesamtGewinn)}</p>
                    </div>
                    <div className="bg-blue-50 p-3 rounded-xl border border-blue-100">
                        <p className="text-xs text-blue-600 uppercase tracking-wide">Projekte</p>
                        <p className="text-lg font-semibold text-blue-900">{kunde.statistik?.projektAnzahl || 0}</p>
                    </div>
                    <div className="bg-purple-50 p-3 rounded-xl border border-purple-100">
                        <p className="text-xs text-purple-600 uppercase tracking-wide">Anfragen</p>
                        <p className="text-lg font-semibold text-purple-900">{kunde.statistik?.anfrageAnzahl || 0}</p>
                    </div>
                </div>

                <div className="flex items-start">
                    <Button variant="outline" onClick={onEdit}>
                        <Edit2 className="w-4 h-4 mr-2" /> Bearbeiten
                    </Button>
                </div>
            </div>
        </Card>
    );

    const mainContent = (
        <>
            <div className="flex items-center justify-between mb-6">
                <h2 className="text-lg font-semibold text-slate-900 flex items-center gap-2">
                    <Mail className="w-5 h-5 text-rose-500" />
                    E-Mail-Verlauf
                </h2>
                <span className="text-sm text-slate-500 bg-slate-100 px-2 py-1 rounded-full">
                    {kunde.kommunikation?.length || 0} Nachrichten
                </span>
            </div>
            <div className="flex-1 min-h-0 relative">
                <div className="absolute inset-0 overflow-y-auto pr-2">
                    <EmailHistory kommunikation={kunde.kommunikation || []} />
                </div>
            </div>
        </>
    );

    const sideContent = (
        <>
            <h2 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                <Phone className="w-5 h-5 text-rose-500" />
                Kontaktdaten
            </h2>
            <div className="space-y-4">
                {kunde.ansprechspartner && (
                    <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                        <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                            <User className="w-4 h-4" />
                        </div>
                        <div>
                            <p className="text-xs text-slate-500">Ansprechpartner</p>
                            <p className="font-medium text-slate-900">{kunde.ansprechspartner}</p>
                        </div>
                    </div>
                )}
                <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                    <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                        <Phone className="w-4 h-4" />
                    </div>
                    <div>
                        <p className="text-xs text-slate-500">Telefon</p>
                        <p className="font-medium text-slate-900">{kunde.telefon || '-'}</p>
                    </div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                    <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                        <Smartphone className="w-4 h-4" />
                    </div>
                    <div>
                        <p className="text-xs text-slate-500">Mobiltelefon</p>
                        <p className="font-medium text-slate-900">{kunde.mobiltelefon || '-'}</p>
                    </div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                    <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                        <Mail className="w-4 h-4" />
                    </div>
                    <div>
                        <p className="text-xs text-slate-500">E-Mail</p>
                        <div className="flex flex-col">
                            {kunde.kundenEmails && kunde.kundenEmails.length > 0 ? (
                                kunde.kundenEmails.map(email => (
                                    <a key={email} href={`mailto:${email}`} className="font-medium text-rose-600 hover:underline">{email}</a>
                                ))
                            ) : (
                                <span className="text-slate-400">-</span>
                            )}
                        </div>
                    </div>
                </div>

                {/* Zahlungsziel */}
                <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                    <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                        <CreditCard className="w-4 h-4" />
                    </div>
                    <div>
                        <p className="text-xs text-slate-500">Zahlungsziel</p>
                        <p className="font-medium text-slate-900">{kunde.zahlungsziel ?? 8} Tage</p>
                    </div>
                </div>
            </div>

            <div className="mt-8 pt-6 border-t border-slate-100 flex-1 flex flex-col min-h-0">
                <h3 className="text-sm font-medium text-slate-900 mb-3 flex items-center gap-2 shrink-0">
                    <MapPin className="w-4 h-4 text-slate-400" /> Standort
                </h3>
                <div className="flex-1 min-h-[200px] rounded-lg overflow-hidden border border-slate-200">
                    <GoogleMapsEmbed
                        strasse={kunde.strasse}
                        plz={kunde.plz}
                        ort={kunde.ort}
                        className="w-full h-full"
                    />
                </div>
            </div>
        </>
    );

    return (
        <DetailLayout
            header={header}
            mainContent={mainContent}
            sideContent={sideContent}
        />
    );
};

// ==================== FORMULAR (Modal) ====================

interface KundenFormularProps {
    kunde: KundeDetail;
    isCreating: boolean;
    onSave: (kunde: KundeDetail) => void;
    onCancel: () => void;
}

const KundenFormular: React.FC<KundenFormularProps> = ({ kunde, isCreating, onSave, onCancel }) => {
    const [formData, setFormData] = useState<KundeDetail>(kunde);
    const [emails, setEmails] = useState<string[]>(kunde.kundenEmails || ['']);
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);
    const [autoKundennummer, setAutoKundennummer] = useState(isCreating && !kunde.kundennummer);

    useEffect(() => {
        setFormData(kunde);
        setEmails(kunde.kundenEmails?.length ? kunde.kundenEmails : ['']);
        setError('');
    }, [kunde]);

    // Nächste verfügbare Kundennummer laden
    useEffect(() => {
        if (isCreating && autoKundennummer) {
            fetch('/api/kunden/next-kundennummer')
                .then(res => res.json())
                .then(data => {
                    setFormData(prev => ({ ...prev, kundennummer: data.kundennummer || '' }));
                })
                .catch(() => {});
        }
    }, [isCreating, autoKundennummer]);

    const handleChange = (field: keyof KundeDetail, value: string | number) => {
        setFormData(prev => ({ ...prev, [field]: value }));
    };

    const handleEmailChange = (index: number, value: string) => {
        setEmails(prev => prev.map((email, i) => i === index ? value : email));
    };

    const addEmail = () => {
        if (emails.length < 5) {
            setEmails(prev => [...prev, '']);
        }
    };

    const removeEmail = (index: number) => {
        setEmails(prev => {
            const next = prev.filter((_, i) => i !== index);
            return next.length === 0 ? [''] : next;
        });
    };

    const handleSubmit = async () => {
        setError('');
        if (!formData.name.trim()) {
            setError('Bitte einen Kundennamen angeben.');
            return;
        }

        if (!isCreating || !autoKundennummer) {
            if (!formData.kundennummer.trim()) {
                setError('Bitte eine Kundennummer angeben oder "Automatisch" aktivieren.');
                return;
            }
        }

        const uniqueEmails = [...new Set(emails.map(e => e.trim()).filter(Boolean))];
        const payload = {
            ...formData,
            kundennummer: (isCreating && autoKundennummer) ? '' : formData.kundennummer.trim(),
            kundenEmails: uniqueEmails,
            zahlungsziel: formData.zahlungsziel || 8
        };

        setSaving(true);
        try {
            const url = isCreating ? '/api/kunden' : `/api/kunden/${kunde.id}`;
            const method = isCreating ? 'POST' : 'PUT';
            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                const body = await res.json().catch(() => null);
                throw new Error(body?.message || 'Speichern fehlgeschlagen.');
            }

            const saved = await res.json().catch(() => payload);
            onSave(saved);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : 'Speichern fehlgeschlagen.');
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={onCancel}>
            <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl mx-4 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
                <div className="p-6 space-y-6">
                    <div className="flex items-center justify-between gap-3">
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500">
                                {isCreating ? 'Neuanlage' : 'Bearbeitung'}
                            </p>
                            <h3 className="text-xl font-semibold text-slate-900">
                                {isCreating ? 'Neuen Kunden anlegen' : 'Kunde bearbeiten'}
                            </h3>
                        </div>
                        <button onClick={onCancel} className="text-slate-400 hover:text-slate-600">
                            <X className="w-6 h-6" />
                        </button>
                    </div>

                    {error && (
                        <div className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">
                            {error}
                        </div>
                    )}

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <div className="flex items-center justify-between mb-1">
                                <Label htmlFor="kundennummer">Kundennummer</Label>
                                {isCreating && (
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
                                                        .then(data => {
                                                            setFormData(prev => ({ ...prev, kundennummer: data.kundennummer || '' }));
                                                        })
                                                        .catch(() => {});
                                                } else {
                                                    setFormData(prev => ({ ...prev, kundennummer: '' }));
                                                }
                                            }}
                                            className="accent-rose-600 w-4 h-4"
                                        />
                                        <span className="text-slate-600">Automatisch</span>
                                    </label>
                                )}
                            </div>
                            <Input
                                id="kundennummer"
                                value={formData.kundennummer}
                                onChange={e => handleChange('kundennummer', e.target.value)}
                                placeholder={autoKundennummer ? 'Wird automatisch vergeben' : 'Kundennummer eingeben'}
                                disabled={isCreating && autoKundennummer}
                            />
                        </div>
                        <div>
                            <Label htmlFor="name">Kundenname *</Label>
                            <Input id="name" value={formData.name} onChange={e => handleChange('name', e.target.value)} placeholder="Unternehmensname" />
                        </div>
                        <div>
                            <Label htmlFor="ansprechspartner">Ansprechpartner</Label>
                            <Input id="ansprechspartner" value={formData.ansprechspartner || ''} onChange={e => handleChange('ansprechspartner', e.target.value)} placeholder="Max Mustermann" />
                        </div>
                        <div>
                            <Label htmlFor="anrede">Anrede</Label>
                            <Select
                                value={formData.anrede || ''}
                                onChange={(value) => handleChange('anrede', value)}
                                options={ANREDE_OPTIONS}
                                placeholder="Anrede wählen"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div className="md:col-span-2">
                            <Label htmlFor="strasse">Straße</Label>
                            <Input id="strasse" value={formData.strasse || ''} onChange={e => handleChange('strasse', e.target.value)} placeholder="Straße Hausnr." />
                        </div>
                        <div>
                            <Label htmlFor="plz">PLZ</Label>
                            <Input id="plz" value={formData.plz || ''} onChange={e => handleChange('plz', e.target.value)} placeholder="PLZ" />
                        </div>
                        <div className="md:col-span-2">
                            <Label htmlFor="ort">Ort</Label>
                            <Input id="ort" value={formData.ort || ''} onChange={e => handleChange('ort', e.target.value)} placeholder="Ort" />
                        </div>
                        <div>
                            <Label htmlFor="zahlungsziel">Zahlungsziel (Tage)</Label>
                            <Input id="zahlungsziel" type="number" min={0} value={formData.zahlungsziel ?? 8} onChange={e => handleChange('zahlungsziel', parseInt(e.target.value, 10) || 8)} placeholder="8" />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <Label htmlFor="telefon">Telefon</Label>
                            <Input id="telefon" value={formData.telefon || ''} onChange={e => handleChange('telefon', e.target.value)} placeholder="+49 ..." />
                        </div>
                        <div>
                            <Label htmlFor="mobiltelefon">Mobiltelefon</Label>
                            <Input id="mobiltelefon" value={formData.mobiltelefon || ''} onChange={e => handleChange('mobiltelefon', e.target.value)} placeholder="+49 ..." />
                        </div>
                    </div>

                    <div>
                        <div className="flex items-center justify-between mb-2">
                            <Label>E-Mail-Adressen</Label>
                            <Button variant="outline" size="sm" onClick={addEmail} disabled={emails.length >= 5}>
                                <Plus className="w-4 h-4" /> Hinzufügen
                            </Button>
                        </div>
                        <div className="space-y-2">
                            {emails.map((email, index) => (
                                <div key={index} className="flex gap-2">
                                    <Input type="email" value={email} onChange={e => handleEmailChange(index, e.target.value)} placeholder="beispiel@firma.de" className="flex-1" />
                                    <Button variant="outline" size="sm" onClick={() => removeEmail(index)}>Entfernen</Button>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="flex gap-3 pt-2 border-t">
                        <Button className="bg-rose-600 text-white hover:bg-rose-700" onClick={handleSubmit} disabled={saving}>
                            <Save className="w-4 h-4" />
                            {saving ? 'Speichern...' : (isCreating ? 'Speichern' : 'Aktualisieren')}
                        </Button>
                        <Button variant="outline" onClick={onCancel}>
                            <X className="w-4 h-4" /> Abbrechen
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
};

// ==================== KUNDEN KARTE ====================

interface KundenKarteProps {
    kunde: KundeDetail;
    onSelect: () => void;
}

const KundenKarte: React.FC<KundenKarteProps> = ({ kunde, onSelect }) => {
    const ortText = [kunde.plz, kunde.ort].filter(Boolean).join(' ');

    return (
        <Card className="p-4 cursor-pointer hover:border-rose-200 hover:shadow-md transition-all bg-white" onClick={onSelect}>
            <div className="space-y-3">
                <div>
                    <div className="flex items-center justify-between">
                        <p className="text-xs uppercase text-slate-500 tracking-wide">{kunde.kundennummer || 'ohne Nr.'}</p>
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wider ${
                            kunde.hatProjekte
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                : 'bg-amber-50 text-amber-700 border border-amber-200'
                        }`}>
                            {kunde.hatProjekte ? 'Kunde' : 'Anfrager'}
                        </span>
                    </div>
                    <h3 className="font-semibold text-slate-900 truncate">{kunde.name || '-'}</h3>
                    {ortText && <p className="text-sm text-slate-500">{ortText}</p>}
                </div>
                <div className="text-sm text-slate-600 space-y-1">
                    {kunde.ansprechspartner && (
                        <p className="flex items-center gap-2"><User className="w-4 h-4 text-rose-400" />{kunde.ansprechspartner}</p>
                    )}
                    {kunde.telefon && (
                        <p className="flex items-center gap-2"><Phone className="w-4 h-4 text-rose-400" />{kunde.telefon}</p>
                    )}
                    {kunde.kundenEmails?.[0] && (
                        <p className="flex items-center gap-2 truncate"><Mail className="w-4 h-4 text-rose-400" />{kunde.kundenEmails[0]}</p>
                    )}
                </div>
            </div>
        </Card>
    );
};

// ==================== FILTER STATE ====================

interface FilterState {
    q: string;
    nummer: string;
    ort: string;
    typ: string;
}

const TYP_OPTIONS = [
    { value: '', label: 'Alle (Kunden & Anfrager)' },
    { value: 'KUNDE', label: 'Nur Kunden (mit Projekt)' },
    { value: 'ANFRAGER', label: 'Nur Anfrager (ohne Projekt)' }
];

// ==================== MAIN COMPONENT ====================

type ViewMode = 'list' | 'detail';

export const Kundeneditor: React.FC = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const [viewMode, setViewMode] = useState<ViewMode>('list');
    const [selectedKunde, setSelectedKunde] = useState<KundeDetail | null>(null);
    const [kunden, setKunden] = useState<KundeDetail[]>([]);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(false);
    const [filters, setFilters] = useState<FilterState>({ q: '', nummer: '', ort: '', typ: '' });
    const [editingKunde, setEditingKunde] = useState<KundeDetail | null>(null);
    const [isCreating, setIsCreating] = useState(false);

    // Deep-link: restore detail view from URL param ?kundeId=123
    const deepLinkProcessed = useRef(false);
    useEffect(() => {
        if (deepLinkProcessed.current) return;
        const kundeIdParam = searchParams.get('kundeId');
        if (!kundeIdParam) return;
        const kundeId = Number(kundeIdParam);
        if (isNaN(kundeId) || !kundeId) return;
        deepLinkProcessed.current = true;
        (async () => {
            try {
                setLoading(true);
                const res = await fetch(`/api/kunden/${kundeId}`);
                if (!res.ok) throw new Error('Fehler beim Laden der Details');
                const data: KundeDetail = await res.json();
                setSelectedKunde(data);
                setViewMode('detail');
            } catch (err) {
                console.error('Deep-link load error', err);
            } finally {
                setLoading(false);
            }
        })();
    }, [searchParams]);

    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

    const fetchKunden = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set('page', String(page));
            params.set('size', String(PAGE_SIZE));
            Object.entries(filters).forEach(([key, value]) => {
                if (value) params.set(key, value);
            });

            const res = await fetch(`/api/kunden?${params.toString()}`);
            if (!res.ok) throw new Error('Laden fehlgeschlagen');

            const data = await res.json();
            setKunden(Array.isArray(data?.kunden) ? data.kunden : []);
            setTotal(typeof data?.gesamt === 'number' ? data.gesamt : 0);
        } catch (err) {
            console.warn('Kunden konnten nicht geladen werden', err);
            setKunden([]);
            setTotal(0);
        } finally {
            setLoading(false);
        }
    }, [page, filters]);

    useEffect(() => {
        fetchKunden();
    }, [fetchKunden]);

    const handleFilterChange = (field: keyof FilterState, value: string) => {
        setFilters(prev => ({ ...prev, [field]: value }));
    };

    const handleFilterSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        fetchKunden();
    };

    const handleResetFilters = () => {
        setFilters({ q: '', nummer: '', ort: '', typ: '' });
        setPage(0);
    };

    const handleCreate = () => {
        setEditingKunde({ ...EMPTY_KUNDE });
        setIsCreating(true);
    };

    const handleOpenDetail = async (kunde: KundeDetail) => {
        // Fetch full details including communication history
        setLoading(true);
        try {
            const res = await fetch(`/api/kunden/${kunde.id}`);
            if (res.ok) {
                const fullDetails = await res.json();
                setSelectedKunde(fullDetails);
                setViewMode('detail');
                setSearchParams({ kundeId: String(fullDetails.id) }, { replace: true });
            } else {
                console.error('Failed to load full customer details');
                // Fallback to list data if fetch fails
                setSelectedKunde(kunde);
                setViewMode('detail');
                setSearchParams({ kundeId: String(kunde.id) }, { replace: true });
            }
        } catch (err) {
            console.error('Error loading customer details', err);
            setSelectedKunde(kunde);
            setViewMode('detail');
            setSearchParams({ kundeId: String(kunde.id) }, { replace: true });
        } finally {
            setLoading(false);
        }
    };

    const handleBackToList = () => {
        setViewMode('list');
        setSelectedKunde(null);
        setSearchParams({}, { replace: true });
    };

    const handleEditFromDetail = () => {
        if (selectedKunde) {
            setEditingKunde(selectedKunde);
            setIsCreating(false);
        }
    };

    const handleSave = (saved: KundeDetail) => {
        setEditingKunde(null);
        setIsCreating(false);
        if (viewMode === 'detail') {
            setSelectedKunde(saved);
        }
        if (isCreating) setPage(0);
        fetchKunden();
    };

    const handleCancel = () => {
        setEditingKunde(null);
        setIsCreating(false);
    };

    const statusText = useMemo(() => {
        if (loading) return 'Kunden werden geladen...';
        if (total === 0) return 'Keine Kunden gefunden.';
        const start = page * PAGE_SIZE + 1;
        const end = Math.min(start + kunden.length - 1, total);
        return `Zeige ${start}-${end} von ${total} Kunden`;
    }, [loading, total, page, kunden.length]);

    // ==================== DETAIL VIEW ====================
    if (viewMode === 'detail' && selectedKunde) {
        return (
            <>
                <KundenDetailView
                    kunde={selectedKunde}
                    onBack={handleBackToList}
                    onEdit={handleEditFromDetail}
                />
                {editingKunde && (
                    <KundenFormular
                        kunde={editingKunde}
                        isCreating={isCreating}
                        onSave={handleSave}
                        onCancel={handleCancel}
                    />
                )}
            </>
        );
    }

    // ==================== LIST VIEW ====================
    return (
        <PageLayout
            ribbonCategory="Stammdaten"
            title="KUNDENÜBERSICHT"
            subtitle="Kunden anlegen, bearbeiten und verwalten."
            actions={
                <>
                    <Button size="sm" className="bg-rose-600 text-white hover:bg-rose-700" onClick={handleCreate}>
                        <Plus className="w-4 h-4 mr-2" />
                        Neuer Kunde
                    </Button>
                    <Button variant="outline" size="sm" onClick={fetchKunden}>
                        <RefreshCw className="w-4 h-4 mr-2" />
                        Aktualisieren
                    </Button>
                </>
            }
        >

            {/* Filter - volle Breite */}
            <div className="bg-white p-6 rounded-2xl shadow-lg border border-slate-100">
                <form onSubmit={handleFilterSubmit} className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Freitext</label>
                        <input type="text" className="filter-input w-full mt-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="Name, E-Mail, Nummer, Straße..." value={filters.q} onChange={e => handleFilterChange('q', e.target.value)} />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Kundennummer</label>
                        <input type="text" className="filter-input w-full mt-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="z.B. K-1005" value={filters.nummer} onChange={e => handleFilterChange('nummer', e.target.value)} />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Ort</label>
                        <input type="text" className="filter-input w-full mt-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="Ort" value={filters.ort} onChange={e => handleFilterChange('ort', e.target.value)} />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Typ</label>
                        <Select
                            value={filters.typ}
                            onChange={(val) => handleFilterChange('typ', val)}
                            options={TYP_OPTIONS}
                            placeholder="Alle (Kunden & Anfrager)"
                            className="w-full mt-1"
                        />
                    </div>
                    <div className="flex items-end gap-3">
                        <button type="submit" className="btn flex-1 bg-rose-600 text-white px-4 py-2 rounded-lg hover:bg-rose-700">Filtern</button>
                        <button type="button" className="btn-secondary flex-1 px-4 py-2 border rounded-lg hover:bg-slate-50" onClick={handleResetFilters}>Reset</button>
                    </div>
                </form>
                <p className="text-xs text-gray-500 mt-3">Für Performance werden immer nur {PAGE_SIZE} Einträge auf einmal geladen.</p>
            </div>

            {/* Kunden-Grid */}
            {loading ? (
                <div className="text-center py-8 text-slate-500">Kunden werden geladen...</div>
            ) : kunden.length === 0 ? (
                <div className="bg-white p-8 rounded-2xl text-center text-slate-500 border-dashed border-2">
                    <MapPin className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                    Keine Kunden gefunden.
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {kunden.map(kunde => (
                        <KundenKarte key={kunde.id} kunde={kunde} onSelect={() => handleOpenDetail(kunde)} />
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

            {/* Modal für Neuanlage */}
            {editingKunde && (
                <KundenFormular kunde={editingKunde} isCreating={isCreating} onSave={handleSave} onCancel={handleCancel} />
            )}
        </PageLayout>
    );
};

export default Kundeneditor;
