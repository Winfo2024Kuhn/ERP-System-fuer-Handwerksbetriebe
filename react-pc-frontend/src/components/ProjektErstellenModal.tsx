import React, { useState, useEffect, useCallback } from 'react';
import { Button } from './ui/button';
import { X, Search, FileText, PlusCircle, Building2, User, Hash, MapPin, ChevronLeft, Plus, Check, Folder, Euro, Trash2, RefreshCw } from 'lucide-react';
import { Select } from './ui/select-custom';
import { CategoryMultiSelectModal } from './CategoryMultiSelectModal';
import { EmailListInput } from './EmailListInput';
import type { Kunde, Anfrage } from '../types';

interface SelectedCategory {
    id: number;
    projektProduktkategorieId?: number; // ProjektProduktkategorie.id für Updates
    bezeichnung: string;
    menge?: number;
    verrechnungseinheit?: string;
}

interface ProjektErstellenPayload {
    bauvorhaben: string;
    kunde: string;
    kundennummer: string;

    kundenId?: number;
    auftragsnummer: string;
    bruttoPreis?: number;
    strasse?: string;
    plz?: string;
    ort?: string;
    anfrageIds?: number[];
    projektArt?: string;
    excKlasse?: string | null;
}

interface ProjektBearbeiten {
    id: number;
    bauvorhaben: string;
    kunde: string;
    kundennummer: string;
    kundenId?: number;
    auftragsnummer: string;
    bruttoPreis?: number;
    strasse?: string;
    plz?: string;
    ort?: string;
    abgeschlossen?: boolean;
    projektArt?: string;
    excKlasse?: string | null;
    kundenEmails?: string[];
    kundeDto?: {
        id: number;
        name: string;
        kundennummer: string;
        strasse?: string;
        plz?: string;
        ort?: string;
        kundenEmails?: string[];
    };
    produktkategorien?: Array<{
        id?: number; // ProjektProduktkategorie.id
        produktkategorie: {
            id: number;
            bezeichnung: string;
            verrechnungseinheit?: { name: string; anzeigename: string };
        };
        menge?: number;
    }>;
}

interface ProjektErstellenModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: (projektId: number) => void;
    /** Falls gesetzt, wird das Projekt bearbeitet statt neu angelegt */
    editProjekt?: ProjektBearbeiten | null;
}

// Sub-view for customer search and selection
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

    // Debounced auto-search bei Eingabe
    useEffect(() => {
        const timer = setTimeout(() => {
            searchKunden();
        }, 300);
        return () => clearTimeout(timer);
    }, [searchTerm, searchKunden]);

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
                            className="p-3 border border-slate-200 rounded-lg hover:border-rose-300 hover:bg-rose-50 cursor-pointer transition-colors"
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

// Sub-view for creating a new customer
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
        } catch (err) {
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
                            options={[
                                { value: 'HERR', label: 'Sehr geehrter Herr' },
                                { value: 'FRAU', label: 'Sehr geehrte Frau' },
                                { value: 'DAMEN_HERREN', label: 'Sehr geehrte Damen und Herren' }
                            ]}
                            value={formData.anrede}
                            onChange={val => setFormData(prev => ({ ...prev, anrede: val }))}
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
                        <input
                            type="tel"
                            value={formData.telefon}
                            onChange={e => setFormData(prev => ({ ...prev, telefon: e.target.value }))}
                            placeholder="z.B. +49 123 456789"
                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Mobiltelefon</label>
                        <input
                            type="tel"
                            value={formData.mobiltelefon}
                            onChange={e => setFormData(prev => ({ ...prev, mobiltelefon: e.target.value }))}
                            placeholder="z.B. +49 170 1234567"
                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>
                </div>

                {/* Adresse */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="md:col-span-2">
                        <label className="block text-sm font-medium text-slate-700 mb-1">Straße</label>
                        <input
                            type="text"
                            value={formData.strasse}
                            onChange={e => setFormData(prev => ({ ...prev, strasse: e.target.value }))}
                            placeholder="Straße + Hausnummer"
                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">PLZ</label>
                            <input
                                type="text"
                                value={formData.plz}
                                onChange={e => setFormData(prev => ({ ...prev, plz: e.target.value }))}
                                className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Ort</label>
                            <input
                                type="text"
                                value={formData.ort}
                                onChange={e => setFormData(prev => ({ ...prev, ort: e.target.value }))}
                                className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                            />
                        </div>
                    </div>
                </div>

                {/* Zahlungsziel */}
                <div className="w-1/3">
                    <label className="block text-sm font-medium text-slate-700 mb-1">Zahlungsziel (Tage)</label>
                    <input
                        type="number"
                        min="0"
                        value={formData.zahlungsziel}
                        onChange={e => setFormData(prev => ({ ...prev, zahlungsziel: parseInt(e.target.value) || 8 }))}
                        className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>

                {error && (
                    <p className="text-sm text-red-600">{error}</p>
                )}

                <div className="flex justify-end gap-3 pt-2">
                    <Button type="button" variant="outline" onClick={onBack} disabled={saving}>
                        Abbrechen
                    </Button>
                    <Button type="submit" disabled={saving} className="bg-rose-600 text-white hover:bg-rose-700">
                        {saving ? 'Speichern...' : 'Kunde anlegen'}
                    </Button>
                </div>
            </form>
        </div>
    );
};

export const ProjektErstellenModal: React.FC<ProjektErstellenModalProps> = ({
    isOpen,
    onClose,
    onSuccess,
    editProjekt,
}) => {
    const isEditMode = !!editProjekt;
    const [mode, setMode] = useState<'select' | 'manual'>('select');
    const [subView, setSubView] = useState<'main' | 'kundensuche' | 'kundeNeu'>('main');
    const [anfragen, setAnfragen] = useState<Anfrage[]>([]);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Filter states
    const [filterJahr, setFilterJahr] = useState<string>('');
    const [filterSuche, setFilterSuche] = useState('');

    // Selected Anfrage
    const [selectedAnfrage, setSelectedAnfrage] = useState<Anfrage | null>(null);

    // Selected Kunde
    const [selectedKunde, setSelectedKunde] = useState<Kunde | null>(null);

    // Checkbox: Projektadresse = Kundenadresse
    const [useKundeAdresse, setUseKundeAdresse] = useState(false);

    // Produktkategorien (Pflichtfeld)
    const [selectedCategories, setSelectedCategories] = useState<SelectedCategory[]>([]);
    const [showCategoryModal, setShowCategoryModal] = useState(false);
    // Kategorie die gerade bearbeitet wird (für Austausch)
    const [editingCategoryIndex, setEditingCategoryIndex] = useState<number | null>(null);
    // Projekt beendet (manuelles Schließen)
    const [abgeschlossen, setAbgeschlossen] = useState(false);

    // Auftragsnummer: Prefix (YYYY/MM/) + Zähler (XXXXX)
    const [auftragsnummerPrefix, setAuftragsnummerPrefix] = useState('');
    const [auftragsnummerZaehler, setAuftragsnummerZaehler] = useState('');
    const [auftragsnummerError, setAuftragsnummerError] = useState<string | null>(null);
    const [validatingAuftragsnummer, setValidatingAuftragsnummer] = useState(false);

    // Manuelle Auftragsnummervergabe: Prefix editierbar
    const [manuelleAuftragsnummer, setManuelleAuftragsnummer] = useState(false);
    const [prefixJahr, setPrefixJahr] = useState('');
    const [prefixMonat, setPrefixMonat] = useState('');

    // Zusätzliche E-Mail-Adressen (über Kunden-E-Mails hinaus)
    const [zusaetzlicheEmails, setZusaetzlicheEmails] = useState<string[]>([]);

    // Manual form fields
    const [formData, setFormData] = useState<ProjektErstellenPayload>({
        bauvorhaben: '',
        kunde: '',
        kundennummer: '',
        auftragsnummer: '',
        bruttoPreis: undefined,
        strasse: '',
        plz: '',
        ort: '',
        projektArt: 'PAUSCHAL', // Default: Pauschalpreis
        excKlasse: null,
    });

    // Nächste Auftragsnummer laden
    const loadNaechsteAuftragsnummer = useCallback(async () => {
        try {
            const today = new Date().toISOString().split('T')[0];
            const res = await fetch(`/api/projekte/naechste-auftragsnummer?datum=${today}`);
            if (res.ok) {
                const data = await res.json();
                setAuftragsnummerPrefix(data.prefix);
                setAuftragsnummerZaehler(String(data.zaehler).padStart(5, '0'));
            }
        } catch (err) {
            console.error('Fehler beim Laden der Auftragsnummer:', err);
        }
    }, []);

    // Auftragsnummer validieren
    const validateAuftragsnummer = useCallback(async (nummer: string, projektId?: number) => {
        if (!nummer) {
            setAuftragsnummerError(null);
            return true;
        }

        setValidatingAuftragsnummer(true);
        try {
            const params = new URLSearchParams({ auftragsnummer: nummer });
            if (projektId) params.append('projektId', String(projektId));

            const res = await fetch(`/api/projekte/auftragsnummer-verfuegbar?${params.toString()}`);
            if (res.ok) {
                const data = await res.json();
                if (!data.verfuegbar) {
                    setAuftragsnummerError(data.message || 'Diese Auftragsnummer ist bereits vergeben.');
                    return false;
                }
                setAuftragsnummerError(null);
                return true;
            }
        } catch (err) {
            console.error('Fehler bei Validierung:', err);
        } finally {
            setValidatingAuftragsnummer(false);
        }
        return true;
    }, []);

    // Auftragsnummer aus Prefix + Zähler zusammensetzen
    const getFullAuftragsnummer = useCallback(() => {
        // Bei manueller Vergabe: Prefix aus prefixJahr/prefixMonat bauen
        const effectivePrefix = manuelleAuftragsnummer
            ? `${prefixJahr}/${prefixMonat.padStart(2, '0')}/`
            : auftragsnummerPrefix;
        if (!effectivePrefix || !auftragsnummerZaehler) return '';
        return `${effectivePrefix}${auftragsnummerZaehler.padStart(5, '0')}`;
    }, [manuelleAuftragsnummer, prefixJahr, prefixMonat, auftragsnummerPrefix, auftragsnummerZaehler]);

    // Load Anfragen
    const loadAnfragen = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = new URLSearchParams();
            if (filterJahr) params.append('jahr', filterJahr);
            if (filterSuche) params.append('q', filterSuche);
            params.append('nurOhneProjekt', 'true');

            const res = await fetch(`/api/anfragen?${params.toString()}`);
            if (!res.ok) throw new Error('Fehler beim Laden der Anfragen');
            const data = await res.json();
            setAnfragen(Array.isArray(data) ? data : data.anfragen || []);
        } catch (err) {
            console.error('Load Anfragen error', err);
            setError('Anfragen konnten nicht geladen werden');
        } finally {
            setLoading(false);
        }
    }, [filterJahr, filterSuche]);

    // Debounced auto-search: lädt automatisch nach bei Eingabe
    useEffect(() => {
        if (!isOpen || mode !== 'select' || subView !== 'main') return;
        const timer = setTimeout(() => {
            loadAnfragen();
        }, 350);
        return () => clearTimeout(timer);
    }, [isOpen, mode, subView, filterJahr, filterSuche, loadAnfragen]);

    // Reset form when closing OR initialize when opening in edit mode
    useEffect(() => {
        if (!isOpen) {
            setMode('select');
            setSubView('main');
            setSelectedAnfrage(null);
            setSelectedKunde(null);
            setUseKundeAdresse(false);
            setSelectedCategories([]);
            setShowCategoryModal(false);
            setAuftragsnummerPrefix('');
            setAuftragsnummerZaehler('');
            setAuftragsnummerError(null);
            setManuelleAuftragsnummer(false);
            setPrefixJahr('');
            setPrefixMonat('');
            setAbgeschlossen(false);
            setZusaetzlicheEmails([]);
            setFormData({
                bauvorhaben: '',
                kunde: '',
                kundennummer: '',
                auftragsnummer: '',
                bruttoPreis: undefined,
                strasse: '',
                plz: '',
                ort: '',
                projektArt: 'PAUSCHAL',
                excKlasse: null,
            });
            setError(null);
        } else if (editProjekt) {
            // Edit-Modus: Daten aus bestehendem Projekt laden
            setMode('manual'); // Im Edit-Modus immer Formular anzeigen
            setSubView('main');
            setSelectedAnfrage(null);

            // Kunde aus Projekt-Daten
            if (editProjekt.kundeDto) {
                setSelectedKunde({
                    id: editProjekt.kundeDto.id,
                    name: editProjekt.kundeDto.name,
                    kundennummer: editProjekt.kundeDto.kundennummer,
                    strasse: editProjekt.kundeDto.strasse,
                    plz: editProjekt.kundeDto.plz,
                    ort: editProjekt.kundeDto.ort,
                    kundenEmails: editProjekt.kundeDto.kundenEmails || [],
                });
            } else if (editProjekt.kundenId) {
                setSelectedKunde({
                    id: editProjekt.kundenId,
                    name: editProjekt.kunde,
                    kundennummer: editProjekt.kundennummer,
                });
            }

            // Auftragsnummer parsen (YYYY/MM/XXXXX)
            const existingNummer = editProjekt.auftragsnummer || '';
            const match = existingNummer.match(/^(\d{4}\/\d{2}\/)(\d+)$/);
            if (match) {
                setAuftragsnummerPrefix(match[1]);
                setAuftragsnummerZaehler(match[2]);
            } else {
                // Falls nicht im erwarteten Format, nächste laden
                loadNaechsteAuftragsnummer();
            }

            setFormData({
                bauvorhaben: editProjekt.bauvorhaben || '',
                kunde: editProjekt.kunde || '',
                kundennummer: editProjekt.kundennummer || '',
                kundenId: editProjekt.kundenId,
                auftragsnummer: editProjekt.auftragsnummer || '',
                bruttoPreis: editProjekt.bruttoPreis,
                strasse: editProjekt.strasse || '',
                plz: editProjekt.plz || '',
                ort: editProjekt.ort || '',
                projektArt: editProjekt.projektArt || 'PAUSCHAL',
                excKlasse: editProjekt.excKlasse ?? null,
            });

            // Produktkategorien aus bestehendem Projekt laden
            if (editProjekt.produktkategorien && editProjekt.produktkategorien.length > 0) {
                setSelectedCategories(editProjekt.produktkategorien.map(pk => ({
                    id: pk.produktkategorie.id,
                    projektProduktkategorieId: pk.id,
                    bezeichnung: pk.produktkategorie.bezeichnung,
                    menge: pk.menge ?? 0,
                    verrechnungseinheit: pk.produktkategorie.verrechnungseinheit?.anzeigename || pk.produktkategorie.verrechnungseinheit?.name || '',
                })));
            }

            // Abgeschlossen-Status laden
            setAbgeschlossen(editProjekt.abgeschlossen || false);

            // Projekt-spezifische E-Mail-Adressen laden
            if (editProjekt.kundenEmails && editProjekt.kundenEmails.length > 0) {
                // Nur die zusätzlichen Emails laden (die nicht vom Kunden kommen)
                const kundeEmails = editProjekt.kundeDto?.kundenEmails || [];
                const extras = editProjekt.kundenEmails.filter(e => !kundeEmails.includes(e));
                setZusaetzlicheEmails(extras);
            }

            setError(null);
        } else if (isOpen) {
            // Neues Projekt: Nächste Auftragsnummer laden
            loadNaechsteAuftragsnummer();
        }
    }, [isOpen, editProjekt, loadNaechsteAuftragsnummer]);

    // When Anfrage is selected, prefill form including Kunde
    useEffect(() => {
        if (selectedAnfrage) {
            // Vollständige Kundendaten laden (inkl. Adresse)
            const kundeId = selectedAnfrage.kundenId;
            if (kundeId) {
                fetch(`/api/kunden/${kundeId}`)
                    .then(res => res.ok ? res.json() : null)
                    .then((kunde: Kunde | null) => {
                        if (kunde) {
                            setSelectedKunde(kunde);
                        } else {
                            setSelectedKunde({
                                id: kundeId,
                                name: selectedAnfrage.kundenName || '',
                                kundennummer: selectedAnfrage.kundennummer || '',
                            });
                        }
                    })
                    .catch(() => {
                        setSelectedKunde({
                            id: kundeId,
                            name: selectedAnfrage.kundenName || '',
                            kundennummer: selectedAnfrage.kundennummer || '',
                        });
                    });
            } else {
                setSelectedKunde({
                    id: 0,
                    name: selectedAnfrage.kundenName || '',
                    kundennummer: selectedAnfrage.kundennummer || '',
                });
            }
            setFormData({
                bauvorhaben: selectedAnfrage.bauvorhaben || '',
                kunde: selectedAnfrage.kundenName || '',
                kundennummer: selectedAnfrage.kundennummer || '',
                kundenId: selectedAnfrage.kundenId,
                auftragsnummer: '',
                strasse: '',
                plz: '',
                ort: '',
                anfrageIds: [selectedAnfrage.id],
            });
        }
    }, [selectedAnfrage]);

    // When Kunde is selected manually, update formData
    const handleKundeSelect = (kunde: Kunde) => {
        setSelectedKunde(kunde);
        setFormData(prev => ({
            ...prev,
            kunde: kunde.name,
            kundennummer: kunde.kundennummer || '',
            kundenId: Number(kunde.id),
            // Wenn Checkbox aktiv, Adresse direkt übernehmen
            ...(useKundeAdresse ? {
                strasse: kunde.strasse || '',
                plz: kunde.plz || '',
                ort: kunde.ort || '',
            } : {}),
        }));
        setSubView('main');
    };

    // Checkbox-Handler: Adresse übernehmen (von Anfrage wenn vorhanden, sonst von Kunde)
    const handleUseKundeAdresseChange = (checked: boolean) => {
        setUseKundeAdresse(checked);
        if (checked) {
            // Priorisiere Anfrages-Objektadresse wenn Anfrage ausgewählt
            if (selectedAnfrage && (selectedAnfrage.projektStrasse || selectedAnfrage.projektPlz || selectedAnfrage.projektOrt)) {
                setFormData(prev => ({
                    ...prev,
                    strasse: selectedAnfrage.projektStrasse || '',
                    plz: selectedAnfrage.projektPlz || '',
                    ort: selectedAnfrage.projektOrt || '',
                }));
            } else if (selectedKunde) {
                // Fallback auf Kundenadresse
                setFormData(prev => ({
                    ...prev,
                    strasse: selectedKunde.strasse || '',
                    plz: selectedKunde.plz || '',
                    ort: selectedKunde.ort || '',
                }));
            }
        }
    };

    // When new Kunde is created
    const handleKundeCreated = (kunde: Kunde) => {
        handleKundeSelect(kunde);
    };

    const handleInputChange = (field: keyof ProjektErstellenPayload, value: string | number | null | undefined) => {
        setFormData(prev => ({ ...prev, [field]: value }));
    };

    const validateForm = (): string | null => {
        if (!formData.bauvorhaben?.trim()) return 'Bitte Bauvorhaben angeben';
        if (!selectedKunde) return 'Bitte Kunde auswählen';
        const fullAuftragsnummer = getFullAuftragsnummer();
        if (!fullAuftragsnummer) return 'Bitte Auftragsnummer angeben';
        if (auftragsnummerError) return auftragsnummerError;
        return null;
    };

    const handleSubmit = async () => {
        const validationError = validateForm();
        if (validationError) {
            setError(validationError);
            return;
        }

        // Auftragsnummer nochmal validieren
        const fullAuftragsnummer = getFullAuftragsnummer();
        const isValid = await validateAuftragsnummer(fullAuftragsnummer, editProjekt?.id);
        if (!isValid) {
            setError('Die Auftragsnummer ist bereits vergeben. Bitte wählen Sie eine andere.');
            return;
        }

        setSaving(true);
        setError(null);

        try {
            const payload = {
                ...formData,
                auftragsnummer: fullAuftragsnummer, // Volle Auftragsnummer verwenden
                kunde: selectedKunde!.name,
                kundennummer: selectedKunde!.kundennummer,
                kundenId: selectedKunde!.id,

                // Produktkategorien mit ID (für Updates) und Menge mitsenden
                produktkategorien: selectedCategories.map(cat => ({
                    id: cat.projektProduktkategorieId,
                    produktkategorieID: cat.id,
                    menge: cat.menge ?? 0
                })),
                // Alle E-Mails kombinieren: Kunden-E-Mails + zusätzliche E-Mails
                kundenEmails: [...(selectedKunde?.kundenEmails || []), ...zusaetzlicheEmails],
                abgeschlossen: isEditMode ? abgeschlossen : false,
                ...(isEditMode ? {} : {
                    anlegedatum: new Date().toISOString().split('T')[0],
                    zeitPositionen: [],
                    materialkosten: [],
                }),
            };

            const url = isEditMode
                ? `/api/projekte/${editProjekt!.id}`
                : '/api/projekte';

            const res = await fetch(url, {
                method: isEditMode ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            if (!res.ok) {
                const errorData = await res.json().catch(() => ({}));
                throw new Error(errorData.message || (isEditMode ? 'Projekt konnte nicht aktualisiert werden' : 'Projekt konnte nicht erstellt werden'));
            }

            const result = await res.json();
            onSuccess(isEditMode ? editProjekt!.id : result.id);
            onClose();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Fehler beim Speichern');
        } finally {
            setSaving(false);
        }
    };

    // Keine redundante Client-seitige Filterung nötig – Backend filtert bereits korrekt
    const filteredAnfragen = anfragen;

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
                {/* Header */}
                <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between bg-gradient-to-r from-rose-50 to-white">
                    <div>
                        <p className="text-xs font-semibold text-rose-600 uppercase tracking-wide">Projektmanagement</p>
                        <h2 className="text-xl font-bold text-slate-900">
                            {isEditMode ? 'Projekt bearbeiten' : 'Neues Projekt anlegen'}
                        </h2>
                    </div>
                    <Button variant="ghost" size="sm" onClick={onClose} className="text-slate-500 hover:bg-slate-100">
                        <X className="w-5 h-5" />
                    </Button>
                </div>

                {/* Mode Tabs - only show on main view and not in edit mode */}
                {subView === 'main' && !isEditMode && (
                    <div className="px-6 py-3 border-b border-slate-100 bg-slate-50">
                        <div className="flex gap-2">
                            <button
                                onClick={() => { setMode('select'); setSelectedAnfrage(null); setSelectedKunde(null); }}
                                className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${mode === 'select'
                                    ? 'bg-rose-600 text-white'
                                    : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                                    }`}
                            >
                                <FileText className="w-4 h-4 inline-block mr-2" />
                                Von Anfrage übernehmen
                            </button>
                            <button
                                onClick={() => { setMode('manual'); setSelectedAnfrage(null); }}
                                className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${mode === 'manual'
                                    ? 'bg-rose-600 text-white'
                                    : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                                    }`}
                            >
                                <PlusCircle className="w-4 h-4 inline-block mr-2" />
                                Ohne Anfrage anlegen
                            </button>
                        </div>
                    </div>
                )}

                {/* Content */}
                <div className="flex-1 overflow-y-auto p-6">
                    {/* Sub-view: Kundensuche */}
                    {subView === 'kundensuche' && (
                        <KundenAuswahlView
                            onSelect={handleKundeSelect}
                            onCreateNew={() => setSubView('kundeNeu')}
                            onBack={() => setSubView('main')}
                        />
                    )}

                    {/* Sub-view: Kunde anlegen */}
                    {subView === 'kundeNeu' && (
                        <KundeAnlegenView
                            onSuccess={handleKundeCreated}
                            onBack={() => setSubView('kundensuche')}
                        />
                    )}

                    {/* Main view: Anfrage selection */}
                    {subView === 'main' && mode === 'select' && !selectedAnfrage && (
                        <div className="space-y-4">
                            {/* Freitext-Suche + Jahr */}
                            <div className="flex gap-3">
                                <div className="flex-1 relative">
                                    <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                                    <input
                                        type="text"
                                        value={filterSuche}
                                        onChange={e => setFilterSuche(e.target.value)}
                                        placeholder="Suche nach Bauvorhaben, Kunde, Kundennummer, Ort, E-Mail..."
                                        className="w-full pl-10 pr-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                                        autoFocus
                                    />
                                </div>
                                <select
                                    value={filterJahr}
                                    onChange={e => setFilterJahr(e.target.value)}
                                    className="px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white min-w-[120px]"
                                >
                                    <option value="">Alle Jahre</option>
                                    {[...Array(5)].map((_, i) => {
                                        const year = new Date().getFullYear() - i;
                                        return <option key={year} value={year}>{year}</option>;
                                    })}
                                </select>
                            </div>
                            {loading && (
                                <p className="text-slate-400 text-sm text-center py-1">Suche...</p>
                            )}

                            {/* Anfrage List */}
                            {!loading && filteredAnfragen.length === 0 ? (
                                <p className="text-slate-500 py-8 text-center">Keine Anfragen gefunden</p>
                            ) : !loading && (
                                <div className="grid gap-3 max-h-[300px] overflow-y-auto">
                                    {filteredAnfragen.map(anfrage => (
                                        <div
                                            key={anfrage.id}
                                            onClick={() => setSelectedAnfrage(anfrage)}
                                            className="p-4 border border-slate-200 rounded-xl hover:border-rose-300 hover:bg-rose-50 cursor-pointer transition-colors"
                                        >
                                            <div className="flex justify-between items-start">
                                                <div>
                                                    <p className="font-semibold text-slate-900">{anfrage.bauvorhaben || 'Kein Bauvorhaben'}</p>
                                                    <p className="text-sm text-slate-500">
                                                        <User className="w-3 h-3 inline-block mr-1" />
                                                        {anfrage.kundenName || 'Kein Kunde'} • {anfrage.kundennummer}
                                                    </p>
                                                </div>
                                                <div className="text-right">
                                                    <p className="text-sm font-medium text-rose-600">{anfrage.anfragesnummer}</p>
                                                    {anfrage.betrag && (
                                                        <p className="text-sm text-slate-500">{anfrage.betrag.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' })}</p>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}

                    {/* Main view: Form (shown after Anfrage selection, in manual mode, or in edit mode) */}
                    {subView === 'main' && (mode === 'manual' || selectedAnfrage || isEditMode) && (
                        <div className="space-y-6">
                            {selectedAnfrage && (
                                <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl mb-4">
                                    <p className="text-sm text-rose-700">
                                        <FileText className="w-4 h-4 inline-block mr-2" />
                                        Basierend auf Anfrage: <strong>{selectedAnfrage.anfragesnummer}</strong> – {selectedAnfrage.bauvorhaben}
                                    </p>
                                    <button onClick={() => { setSelectedAnfrage(null); setSelectedKunde(null); }} className="text-sm text-rose-600 underline mt-1">
                                        Anderes Anfrage wählen
                                    </button>
                                </div>
                            )}

                            {/* Kunde Selection */}
                            <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl">
                                <div className="flex items-center justify-between">
                                    <div>
                                        <label className="block text-sm font-medium text-slate-700 mb-1">
                                            <User className="w-4 h-4 inline-block mr-1" />
                                            Kunde *
                                        </label>
                                        {selectedKunde ? (
                                            <div className="flex items-center gap-2">
                                                <span className="font-medium text-slate-900">{selectedKunde.name}</span>
                                                <span className="text-slate-500">({selectedKunde.kundennummer})</span>
                                            </div>
                                        ) : (
                                            <p className="text-slate-500 text-sm">Kein Kunde ausgewählt</p>
                                        )}
                                    </div>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setSubView('kundensuche')}
                                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                    >
                                        <Search className="w-4 h-4 mr-1" />
                                        {selectedKunde ? 'Ändern' : 'Kunde auswählen'}
                                    </Button>
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">
                                        <Building2 className="w-4 h-4 inline-block mr-1" />
                                        Bauvorhaben *
                                    </label>
                                    <input
                                        type="text"
                                        value={formData.bauvorhaben}
                                        onChange={e => handleInputChange('bauvorhaben', e.target.value)}
                                        placeholder="z.B. Neubau Einfamilienhaus"
                                        className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">
                                        <Hash className="w-4 h-4 inline-block mr-1" />
                                        Auftragsnummer *
                                    </label>
                                    <div className="flex items-center gap-1">
                                        {manuelleAuftragsnummer ? (
                                            <>
                                                {/* Jahr (editierbar) */}
                                                <input
                                                    type="text"
                                                    value={prefixJahr}
                                                    onChange={e => {
                                                        const val = e.target.value.replace(/\D/g, '').slice(0, 4);
                                                        setPrefixJahr(val);
                                                        setAuftragsnummerError(null);
                                                    }}
                                                    placeholder="YYYY"
                                                    className="w-16 px-2 py-2 border border-slate-200 rounded-l-lg focus:outline-none focus:ring-2 focus:ring-rose-500 font-mono text-sm text-center"
                                                />
                                                <span className="text-slate-400">/</span>
                                                {/* Monat (editierbar) */}
                                                <input
                                                    type="text"
                                                    value={prefixMonat}
                                                    onChange={e => {
                                                        const val = e.target.value.replace(/\D/g, '').slice(0, 2);
                                                        setPrefixMonat(val);
                                                        setAuftragsnummerError(null);
                                                    }}
                                                    placeholder="MM"
                                                    className="w-12 px-2 py-2 border border-slate-200 focus:outline-none focus:ring-2 focus:ring-rose-500 font-mono text-sm text-center"
                                                />
                                                <span className="text-slate-400">/</span>
                                            </>
                                        ) : (
                                            /* Prefix (nicht editierbar) */
                                            <span className="px-3 py-2 bg-slate-100 border border-slate-200 rounded-l-lg text-slate-600 font-mono text-sm whitespace-nowrap">
                                                {auftragsnummerPrefix || 'YYYY/MM/'}
                                            </span>
                                        )}
                                        {/* Zähler (immer editierbar) */}
                                        <input
                                            type="text"
                                            value={auftragsnummerZaehler}
                                            onChange={e => {
                                                const val = e.target.value.replace(/\D/g, '').slice(0, 5);
                                                setAuftragsnummerZaehler(val);
                                                setAuftragsnummerError(null);
                                            }}
                                            onBlur={async () => {
                                                // Automatisch auf 5 Stellen auffüllen
                                                if (auftragsnummerZaehler.length > 0 && auftragsnummerZaehler.length < 5) {
                                                    setAuftragsnummerZaehler(auftragsnummerZaehler.padStart(5, '0'));
                                                }
                                                // Validieren
                                                const fullNummer = getFullAuftragsnummer();
                                                if (fullNummer) {
                                                    await validateAuftragsnummer(fullNummer, editProjekt?.id);
                                                }
                                            }}
                                            placeholder="00001"
                                            className={`flex-1 px-3 py-2 border focus:outline-none focus:ring-2 focus:ring-rose-500 font-mono ${manuelleAuftragsnummer ? '' : 'rounded-r-lg'} ${auftragsnummerError
                                                ? 'border-red-400 bg-red-50'
                                                : 'border-slate-200'
                                                }`}
                                        />
                                        {manuelleAuftragsnummer && (
                                            <span className="w-1"></span>
                                        )}
                                        {validatingAuftragsnummer && (
                                            <span className="text-slate-400 text-sm">Prüfe...</span>
                                        )}
                                    </div>
                                    {auftragsnummerError && (
                                        <p className="text-xs text-red-600 mt-1">{auftragsnummerError}</p>
                                    )}
                                    {/* Checkbox für manuelle Vergabe */}
                                    <label className="flex items-center gap-2 mt-2 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={manuelleAuftragsnummer}
                                            onChange={(e) => {
                                                const checked = e.target.checked;
                                                setManuelleAuftragsnummer(checked);
                                                if (checked) {
                                                    // Aktuelle Werte aus Prefix übernehmen
                                                    const now = new Date();
                                                    setPrefixJahr(auftragsnummerPrefix ? auftragsnummerPrefix.split('/')[0] : String(now.getFullYear()));
                                                    setPrefixMonat(auftragsnummerPrefix ? auftragsnummerPrefix.split('/')[1] : String(now.getMonth() + 1).padStart(2, '0'));
                                                } else {
                                                    // Bei Deaktivierung: Prefix auf aktuelles Datum zurücksetzen
                                                    loadNaechsteAuftragsnummer();
                                                    setPrefixJahr('');
                                                    setPrefixMonat('');
                                                }
                                                setAuftragsnummerError(null);
                                            }}
                                            className="w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                                        />
                                        <span className="text-sm text-slate-600">Auftragsnummer manuell vergeben</span>
                                    </label>
                                </div>
                            </div>

                            {/* Projekt beendet - nur im Edit-Modus */}
                            {isEditMode && (
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">
                                        <Euro className="w-4 h-4 inline-block mr-1" />
                                        Bruttopreis (€)
                                    </label>
                                    <input
                                        type="number"
                                        step="0.01"
                                        value={formData.bruttoPreis ?? ''}
                                        onChange={e => handleInputChange('bruttoPreis', e.target.value ? parseFloat(e.target.value) : undefined)}
                                        placeholder="0,00"
                                        className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                                    />
                                </div>
                            )}

                            {/* Projekt beendet - nur im Edit-Modus */}
                            {isEditMode && (
                                <div>
                                    <label className="flex items-center gap-3 cursor-pointer group w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg hover:border-rose-300 transition-colors">
                                        <input
                                            type="checkbox"
                                            checked={abgeschlossen}
                                            onChange={(e) => setAbgeschlossen(e.target.checked)}
                                            className="w-5 h-5 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                                        />
                                        <span className="font-medium text-slate-700 text-sm group-hover:text-rose-600">
                                            Projekt beendet
                                        </span>
                                    </label>
                                </div>
                            )}

                            {/* Projektart Auswahl */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-2">
                                    <Folder className="w-4 h-4 inline-block mr-1" />
                                    Projektart *
                                </label>
                                <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                                    {[
                                        { value: 'PAUSCHAL', label: 'Pauschalpreis', desc: 'Festpreis-Projekt', produktiv: true },
                                        { value: 'REGIE', label: 'Regie', desc: 'Nach Aufwand', produktiv: true },
                                        { value: 'INTERN', label: 'Internes Projekt', desc: 'Ohne Abrechnung', produktiv: false },
                                        { value: 'GARANTIE', label: 'Garantie', desc: 'Nacharbeiten', produktiv: false },
                                    ].map(art => (
                                        <button
                                            key={art.value}
                                            type="button"
                                            onClick={() => handleInputChange('projektArt', art.value)}
                                            className={`p-3 rounded-lg border-2 text-left transition-all ${
                                                formData.projektArt === art.value
                                                    ? 'border-rose-500 bg-rose-50'
                                                    : 'border-slate-200 hover:border-slate-300 bg-white'
                                            }`}
                                        >
                                            <p className={`font-medium text-sm ${formData.projektArt === art.value ? 'text-rose-700' : 'text-slate-700'}`}>
                                                {art.label}
                                            </p>
                                            <p className="text-xs text-slate-500 mt-0.5">{art.desc}</p>
                                            <span className={`inline-block mt-1 text-xs px-1.5 py-0.5 rounded ${
                                                art.produktiv 
                                                    ? 'bg-green-100 text-green-700' 
                                                    : 'bg-slate-100 text-slate-600'
                                            }`}>
                                                {art.produktiv ? 'Produktiv' : 'Unproduktiv'}
                                            </span>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* EN 1090 Ausführungsklasse */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-2">
                                    EN 1090 Ausführungsklasse <span className="text-slate-400 font-normal">(optional)</span>
                                </label>
                                <div className="grid grid-cols-3 gap-2">
                                    {[
                                        { value: null, label: 'Keine', desc: 'Kein EN 1090' },
                                        { value: 'EXC_1', label: 'EXC 1', desc: 'Einfache Bauteile' },
                                        { value: 'EXC_2', label: 'EXC 2', desc: 'Typischer Stahlbau' },
                                    ].map(klasse => (
                                        <button
                                            key={klasse.value ?? 'none'}
                                            type="button"
                                            onClick={() => handleInputChange('excKlasse', klasse.value as string | undefined)}
                                            className={`p-3 rounded-lg border-2 text-left transition-all ${
                                                formData.excKlasse === klasse.value
                                                    ? 'border-rose-500 bg-rose-50'
                                                    : 'border-slate-200 hover:border-slate-300 bg-white'
                                            }`}
                                        >
                                            <p className={`font-semibold text-sm ${formData.excKlasse === klasse.value ? 'text-rose-700' : 'text-slate-700'}`}>
                                                {klasse.label}
                                            </p>
                                            <p className="text-xs text-slate-500 mt-0.5">{klasse.desc}</p>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* Produktkategorien */}
                            <div className="border-t border-slate-100 pt-4">
                                <div className="flex items-center justify-between mb-3">
                                    <label className="block text-sm font-medium text-slate-700">
                                        <Folder className="w-4 h-4 inline-block mr-1" />
                                        Produktkategorien
                                    </label>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() => {
                                            setEditingCategoryIndex(null);
                                            setShowCategoryModal(true);
                                        }}
                                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                    >
                                        <Plus className="w-4 h-4 mr-1" />
                                        Kategorie hinzufügen
                                    </Button>
                                </div>

                                {selectedCategories.length === 0 ? (
                                    <div
                                        onClick={() => {
                                            setEditingCategoryIndex(null);
                                            setShowCategoryModal(true);
                                        }}
                                        className="p-4 border-2 border-dashed border-slate-200 rounded-xl text-center cursor-pointer hover:border-rose-300 hover:bg-rose-50/50 transition-colors"
                                    >
                                        <Folder className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                                        <p className="text-sm text-slate-500">Keine Kategorien zugewiesen</p>
                                        <p className="text-xs text-rose-500 mt-1">Klicken um Kategorie hinzuzufügen</p>
                                    </div>
                                ) : (
                                    <div className="space-y-2">
                                        {selectedCategories.map((cat, index) => (
                                            <div key={`${cat.id}-${index}`} className="flex items-center gap-3 p-3 bg-slate-50 border border-slate-200 rounded-lg">
                                                <Folder className="w-4 h-4 text-rose-500 flex-shrink-0" />
                                                <div className="flex-1 min-w-0">
                                                    <span className="text-sm font-medium text-slate-900 truncate block">{cat.bezeichnung}</span>
                                                    {cat.verrechnungseinheit && (
                                                        <span className="text-xs text-slate-500">{cat.verrechnungseinheit}</span>
                                                    )}
                                                </div>
                                                <div className="flex items-center gap-1 flex-shrink-0">
                                                    {/* Menge eingeben */}
                                                    <input
                                                        type="number"
                                                        step="0.01"
                                                        min="0"
                                                        value={cat.menge ?? ''}
                                                        onChange={e => {
                                                            const val = e.target.value ? parseFloat(e.target.value) : 0;
                                                            setSelectedCategories(prev =>
                                                                prev.map((c, i) => i === index ? { ...c, menge: val } : c)
                                                            );
                                                        }}
                                                        placeholder="Menge"
                                                        className="w-20 px-2 py-1 text-sm border border-slate-200 rounded focus:outline-none focus:ring-1 focus:ring-rose-500"
                                                    />
                                                    {/* Kategorie ändern */}
                                                    <button
                                                        type="button"
                                                        onClick={() => {
                                                            setEditingCategoryIndex(index);
                                                            setShowCategoryModal(true);
                                                        }}
                                                        className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded"
                                                        title="Kategorie ändern"
                                                    >
                                                        <RefreshCw className="w-4 h-4" />
                                                    </button>
                                                    {/* Kategorie entfernen */}
                                                    <button
                                                        type="button"
                                                        onClick={async () => {
                                                            // Prüfe ob die Kategorie referenziert wird (nur bei bestehenden ProjektProduktkategorien)
                                                            if (cat.projektProduktkategorieId && isEditMode && editProjekt) {
                                                                try {
                                                                    const res = await fetch(`/api/projekte/${editProjekt.id}/produktkategorien/${cat.projektProduktkategorieId}/referenziert`);
                                                                    if (res.ok) {
                                                                        const data = await res.json();
                                                                        if (data.referenziert) {
                                                                            setError('Diese Kategorie kann nicht entfernt werden, da sie durch Zeitbuchungen referenziert wird. Sie können die Kategorie aber ändern.');
                                                                            return;
                                                                        }
                                                                    }
                                                                } catch (err) {
                                                                    console.error('Referenz-Check fehlgeschlagen:', err);
                                                                }
                                                            }
                                                            setSelectedCategories(prev => prev.filter((_, i) => i !== index));
                                                        }}
                                                        className="p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded"
                                                        title="Kategorie entfernen"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>

                            {/* E-Mail-Adressen */}
                            <div className="border-t border-slate-100 pt-4">
                                <EmailListInput
                                    emails={zusaetzlicheEmails}
                                    onChange={setZusaetzlicheEmails}
                                    kundenEmails={selectedKunde?.kundenEmails || []}
                                    anfrageEmails={selectedAnfrage?.kundenEmails || []}
                                    label="E-Mail-Adressen"
                                    placeholder="Weitere E-Mail hinzufügen (z.B. Statiker)..."
                                />
                            </div>

                            <div className="border-t border-slate-100 pt-4">
                                <div className="flex items-center justify-between mb-3">
                                    <p className="text-sm font-medium text-slate-700">
                                        <MapPin className="w-4 h-4 inline-block mr-1" />
                                        Projektadresse (optional)
                                    </p>
                                    {/* Checkbox: Adresse übernehmen */}
                                    <label className="flex items-center gap-2 cursor-pointer group">
                                        <input
                                            type="checkbox"
                                            checked={useKundeAdresse}
                                            onChange={(e) => handleUseKundeAdresseChange(e.target.checked)}
                                            disabled={!selectedKunde && !selectedAnfrage}
                                            className="w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500 disabled:opacity-50"
                                        />
                                        <span className={`text-sm ${(selectedKunde || selectedAnfrage) ? 'text-slate-700 group-hover:text-rose-600' : 'text-slate-400'}`}>
                                            {selectedAnfrage && (selectedAnfrage.projektStrasse || selectedAnfrage.projektPlz || selectedAnfrage.projektOrt)
                                                ? 'Anfrages-Objektadresse übernehmen'
                                                : 'Kundenadresse übernehmen'}
                                        </span>
                                    </label>
                                </div>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    <div className="md:col-span-2">
                                        <input
                                            type="text"
                                            value={formData.strasse || ''}
                                            onChange={e => {
                                                handleInputChange('strasse', e.target.value);
                                                if (useKundeAdresse) setUseKundeAdresse(false);
                                            }}
                                            placeholder="Straße"
                                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                                        />
                                    </div>
                                    <div className="grid grid-cols-2 gap-2">
                                        <input
                                            type="text"
                                            value={formData.plz || ''}
                                            onChange={e => {
                                                handleInputChange('plz', e.target.value);
                                                if (useKundeAdresse) setUseKundeAdresse(false);
                                            }}
                                            placeholder="PLZ"
                                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                                        />
                                        <input
                                            type="text"
                                            value={formData.ort || ''}
                                            onChange={e => {
                                                handleInputChange('ort', e.target.value);
                                                if (useKundeAdresse) setUseKundeAdresse(false);
                                            }}
                                            placeholder="Ort"
                                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                                        />
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </div>

                {/* Error */}
                {error && subView === 'main' && (
                    <div className="px-6 py-3 bg-red-50 border-t border-red-200">
                        <p className="text-sm text-red-600">{error}</p>
                    </div>
                )}

                {/* Footer - only on main view */}
                {subView === 'main' && (
                    <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex justify-end gap-3">
                        <Button variant="outline" onClick={onClose} disabled={saving}>
                            Abbrechen
                        </Button>
                        {(mode === 'manual' || selectedAnfrage || isEditMode) && (
                            <Button
                                onClick={handleSubmit}
                                disabled={saving}
                                className="bg-rose-600 text-white hover:bg-rose-700"
                            >
                                {saving ? 'Speichern...' : (isEditMode ? 'Änderungen speichern' : 'Projekt anlegen')}
                            </Button>
                        )}
                    </div>
                )}
            </div>

            {/* Category Multi-Select Modal */}
            {showCategoryModal && (
                <CategoryMultiSelectModal
                    initialSelected={editingCategoryIndex !== null ? [] : selectedCategories}
                    onConfirm={(newCategories) => {
                        if (editingCategoryIndex !== null && newCategories.length > 0) {
                            // Bearbeitungsmodus: Ersetze die Kategorie am Index, behalte projektProduktkategorieId
                            const oldCategory = selectedCategories[editingCategoryIndex];
                            const newCategory = newCategories[0];
                            setSelectedCategories(prev =>
                                prev.map((c, i) => i === editingCategoryIndex ? {
                                    ...newCategory,
                                    projektProduktkategorieId: oldCategory.projektProduktkategorieId, // Behalte die ID!
                                    menge: oldCategory.menge ?? 0, // Behalte die Menge
                                } : c)
                            );
                        } else {
                            // Hinzufügen-Modus: Alle gewählten Kategorien übernehmen
                            setSelectedCategories(newCategories);
                        }
                        setEditingCategoryIndex(null);
                    }}
                    onClose={() => {
                        setShowCategoryModal(false);
                        setEditingCategoryIndex(null);
                    }}
                />
            )}
        </div>
    );
};

export default ProjektErstellenModal;
