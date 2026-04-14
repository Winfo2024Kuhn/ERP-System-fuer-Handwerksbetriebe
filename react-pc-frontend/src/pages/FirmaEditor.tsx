import { useState, useEffect, useCallback, useMemo } from 'react';
import { Building2, Wallet, Users, Plus, Edit2, Trash2, Save, X, RefreshCw, FileText, Download, Calendar, Settings, Shield, CheckCircle, XCircle, ChevronRight, Pencil, Search, Layers } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { PageLayout } from '../components/layout/PageLayout';
import { cn } from '../lib/utils';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../components/ui/dialog';
import { KostenstelleDetailView } from '../components/firma/KostenstelleDetailView';
import { DatePicker } from '../components/ui/datepicker';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { SystemSetupConfigurator } from '../components/settings/SystemSetupConfigurator';

import { useFeatures } from '../hooks/useFeatures';
import { StundensatzEditModal } from '../components/StundensatzEditModal';
import { type Abteilung, type Arbeitsgang } from '../types';

// Types
interface Firmeninformation {
    id: number;
    firmenname: string;
    strasse: string;
    plz: string;
    ort: string;
    telefon: string;
    fax: string;
    email: string;
    website: string;
    steuernummer: string;
    ustIdNr: string;
    handelsregister: string;
    handelsregisterNummer: string;
    bankName: string;
    iban: string;
    bic: string;
    logoDateiname: string;
    geschaeftsfuehrer: string;
    fusszeileText: string;
}

interface Kostenstelle {
    id: number;
    bezeichnung: string;
    typ: 'LAGER' | 'GEMEINKOSTEN' | 'PROJEKT' | 'SONSTIG';
    beschreibung: string;
    istFixkosten: boolean;
    istInvestition: boolean;
    aktiv: boolean;
    sortierung: number;
}

interface SteuerberaterKontakt {
    id: number;
    name: string;
    email: string;
    telefon: string;
    ansprechpartner: string;
    autoProcessEmails: boolean;
    aktiv: boolean;
    notizen: string;
    gueltigAb: string | null;
    gueltigBis: string | null;
    weitereEmails: string[];
}


interface LohnabrechnungDto {
    id: number;
    mitarbeiterId: number;
    mitarbeiterName: string;
    steuerberaterId: number;
    steuerberaterName: string;
    jahr: number;
    monat: number;
    originalDateiname: string;
    downloadUrl: string;
    bruttolohn: number;
    nettolohn: number;
    importDatum: string;
    status: string;
}

interface BwaUploadDto {
    id: number;
    typ: 'MONATLICH' | 'JAEHRLICH';
    jahr: number;
    monat: number | null;
    originalDateiname: string;
    pdfUrl: string;
    uploadDatum: string;
    aiConfidence: number | null;
    analysiert: boolean;
    freigegeben: boolean;
    gesamtGemeinkosten: number | null;
    steuerberaterName: string;
}

interface En1090RolleDto {
    id: number;
    kurztext: string;
    beschreibung: string | null;
    sortierung: number;
    aktiv: boolean;
}

interface En1090RolleDto {
    id: number;
    kurztext: string;
    beschreibung: string | null;
    sortierung: number;
    aktiv: boolean;
}

type ActiveTab = 'firma' | 'kostenstellen' | 'steuerberater' | 'en1090rollen' | 'abteilungen' | 'systemsetup';
type SteuerberaterSubTab = 'kontakte' | 'lohnabrechnungen' | 'bwa';

const KOSTENSTELLEN_TYP_OPTIONS = [
    { value: 'LAGER', label: 'Lager (Investitionen)' },
    { value: 'GEMEINKOSTEN', label: 'Gemeinkosten (Fixkosten)' },
    { value: 'PROJEKT', label: 'Projekt' },
    { value: 'SONSTIG', label: 'Sonstige' },
];

export default function FirmaEditor() {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const features = useFeatures();
    const [activeTab, setActiveTab] = useState<ActiveTab>('firma');
    const [sbSubTab, setSbSubTab] = useState<SteuerberaterSubTab>('kontakte');
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);

    // Firmeninformation State
    const [firma, setFirma] = useState<Firmeninformation | null>(null);

    // Kostenstellen State
    const [kostenstellen, setKostenstellen] = useState<Kostenstelle[]>([]);
    const [showKostenstelleModal, setShowKostenstelleModal] = useState(false);
    const [editingKostenstelle, setEditingKostenstelle] = useState<Partial<Kostenstelle> | null>(null);
    const [selectedKostenstelle, setSelectedKostenstelle] = useState<Kostenstelle | null>(null);

    // Steuerberater State
    const [steuerberater, setSteuerberater] = useState<SteuerberaterKontakt[]>([]);
    const [showSteuerberaterModal, setShowSteuerberaterModal] = useState(false);
    const [editingSteuerberater, setEditingSteuerberater] = useState<Partial<SteuerberaterKontakt> | null>(null);

    // Lohnabrechnungen State
    const [lohnabrechnungen, setLohnabrechnungen] = useState<LohnabrechnungDto[]>([]);
    const [selectedJahr, setSelectedJahr] = useState<number>(new Date().getFullYear());
    const [verfuegbareJahre, setVerfuegbareJahre] = useState<number[]>([new Date().getFullYear()]);
    const [selectedSbFilter, setSelectedSbFilter] = useState<string>('ALL');

    // BWA State
    const [bwaListe, setBwaListe] = useState<BwaUploadDto[]>([]);

    // EN 1090 Rollen State
    const [en1090Rollen, setEn1090Rollen] = useState<En1090RolleDto[]>([]);
    const [showRolleModal, setShowRolleModal] = useState(false);
    const [editingRolle, setEditingRolle] = useState<Partial<En1090RolleDto> | null>(null);

    // Abteilungen & Arbeitsgänge State
    const [abteilungen, setAbteilungen] = useState<Abteilung[]>([]);
    const [arbeitsgaenge, setArbeitsgaenge] = useState<Arbeitsgang[]>([]);
    const [selectedAbteilungId, setSelectedAbteilungId] = useState<number | null>(null);
    const [newAbteilungName, setNewAbteilungName] = useState('');
    const [newArbeitsgangBeschr, setNewArbeitsgangBeschr] = useState('');
    const [creatingAbteilung, setCreatingAbteilung] = useState(false);
    const [creatingArbeitsgang, setCreatingArbeitsgang] = useState(false);
    const [editingArbeitsgang, setEditingArbeitsgang] = useState<Arbeitsgang | null>(null);
    const [agSearchTerm, setAgSearchTerm] = useState('');

    const selectedAbteilung = useMemo(
        () => abteilungen.find(a => a.id === selectedAbteilungId) || null,
        [abteilungen, selectedAbteilungId]
    );
    const filteredArbeitsgaenge = useMemo(() => {
        if (!selectedAbteilungId) return [];
        const base = arbeitsgaenge.filter(a => a.abteilungId === selectedAbteilungId);
        if (!agSearchTerm.trim()) return base;
        const term = agSearchTerm.toLowerCase();
        return base.filter(a => a.beschreibung.toLowerCase().includes(term));
    }, [arbeitsgaenge, selectedAbteilungId, agSearchTerm]);

    // Load Firmeninformation
    const loadFirma = useCallback(async () => {
        try {
            const res = await fetch('/api/firma');
            if (res.ok) {
                setFirma(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Laden der Firmendaten', e);
        }
    }, []);

    // Load Kostenstellen
    const loadKostenstellen = useCallback(async () => {
        try {
            const res = await fetch('/api/firma/kostenstellen');
            if (res.ok) {
                setKostenstellen(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Laden der Kostenstellen', e);
        }
    }, []);

    // Load Steuerberater
    const loadSteuerberater = useCallback(async () => {
        try {
            const res = await fetch('/api/firma/steuerberater');
            if (res.ok) {
                setSteuerberater(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Laden der Steuerberater', e);
        }
    }, []);

    // Load EN 1090 Rollen
    const loadEn1090Rollen = useCallback(async () => {
        try {
            const res = await fetch('/api/en1090/rollen');
            if (res.ok) {
                setEn1090Rollen(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Laden der EN-1090-Rollen', e);
        }
    }, []);

    // Load Abteilungen & Arbeitsgänge
    const loadAbteilungenData = useCallback(async () => {
        try {
            const [abtRes, agRes] = await Promise.all([
                fetch('/api/abteilungen'),
                fetch('/api/arbeitsgaenge')
            ]);
            if (abtRes.ok) {
                const data = await abtRes.json();
                setAbteilungen(Array.isArray(data) ? data : []);
            }
            if (agRes.ok) {
                const data = await agRes.json();
                setArbeitsgaenge(Array.isArray(data) ? data : []);
            }
        } catch (e) {
            console.error('Fehler beim Laden der Abteilungen', e);
        }
    }, []);

    // Load Meta (Years)
    const loadMeta = useCallback(async () => {
        try {
            const [lohnJahreRes, bwaJahreRes] = await Promise.all([
                fetch('/api/lohnabrechnungen/jahre'),
                fetch('/api/bwa/jahre')
            ]);
            
            const jahreSet = new Set<number>();
            jahreSet.add(new Date().getFullYear());

            if (lohnJahreRes.ok) {
                const j = await lohnJahreRes.json();
                if (Array.isArray(j)) j.forEach((y: number) => jahreSet.add(y));
            }
            if (bwaJahreRes.ok) {
                const j = await bwaJahreRes.json();
                if (Array.isArray(j)) j.forEach((y: number) => jahreSet.add(y));
            }
            
            setVerfuegbareJahre(Array.from(jahreSet).sort((a, b) => b - a));
        } catch (e) {
            console.error('Fehler beim Laden der Jahre', e);
        }
    }, []);

    // Load Lohnabrechnungen List
    const loadLohnabrechnungen = useCallback(async () => {
        try {
            let url = `/api/lohnabrechnungen/jahr/${selectedJahr}`;
            if (selectedSbFilter !== 'ALL') {
                url = `/api/lohnabrechnungen/steuerberater/${selectedSbFilter}/jahr/${selectedJahr}`;
            }
            const res = await fetch(url);
            if (res.ok) {
                setLohnabrechnungen(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Laden der Lohnabrechnungen', e);
        }
    }, [selectedJahr, selectedSbFilter]);

    // Load BWA List
    const loadBwaListe = useCallback(async () => {
        try {
            const res = await fetch(`/api/bwa/jahr/${selectedJahr}`);
            if (res.ok) {
                setBwaListe(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Laden der BWA-Liste', e);
        }
    }, [selectedJahr]);

    useEffect(() => {
        if (activeTab === 'steuerberater') {
            if (sbSubTab === 'lohnabrechnungen') loadLohnabrechnungen();
            if (sbSubTab === 'bwa') loadBwaListe();
        }
        if (activeTab === 'abteilungen') loadAbteilungenData();
    }, [activeTab, sbSubTab, loadLohnabrechnungen, loadBwaListe, loadAbteilungenData]);

    // Auto-select first Abteilung when data loads
    useEffect(() => {
        if (abteilungen.length > 0 && selectedAbteilungId === null) {
            setSelectedAbteilungId(abteilungen[0].id);
        } else if (abteilungen.length > 0 && !abteilungen.some(a => a.id === selectedAbteilungId)) {
            setSelectedAbteilungId(abteilungen[0].id);
        }
    }, [abteilungen, selectedAbteilungId]);

    useEffect(() => {
        setLoading(true);
        const base = [loadFirma(), loadKostenstellen(), loadSteuerberater(), loadMeta()];
        if (features.en1090) base.push(loadEn1090Rollen());
        Promise.all(base).finally(() => setLoading(false));
    }, [loadFirma, loadKostenstellen, loadSteuerberater, loadMeta, loadEn1090Rollen, features.en1090]);

    // Save Firmeninformation
    const saveFirma = async () => {
        if (!firma) return;
        setSaving(true);
        try {
            const res = await fetch('/api/firma', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(firma)
            });
            if (res.ok) {
                setFirma(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Speichern', e);
        } finally {
            setSaving(false);
        }
    };

    // Save Kostenstelle
    const saveKostenstelle = async () => {
        if (!editingKostenstelle) return;
        setSaving(true);
        try {
            const typ = editingKostenstelle.typ || 'GEMEINKOSTEN';
            const payload = {
                ...editingKostenstelle,
                typ,
                istFixkosten: typ === 'GEMEINKOSTEN',
                istInvestition: typ === 'LAGER',
            };
            const method = payload.id ? 'PUT' : 'POST';
            const url = payload.id 
                ? `/api/firma/kostenstellen/${payload.id}`
                : '/api/firma/kostenstellen';
            
            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (res.ok) {
                await loadKostenstellen();
                setShowKostenstelleModal(false);
                setEditingKostenstelle(null);
            }
        } catch (e) {
            console.error('Fehler beim Speichern', e);
        } finally {
            setSaving(false);
        }
    };

    // Delete Kostenstelle
    const deleteKostenstelle = async (id: number) => {
        if (!await confirmDialog({ title: 'Kostenstelle löschen', message: 'Kostenstelle wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            await fetch(`/api/firma/kostenstellen/${id}`, { method: 'DELETE' });
            await loadKostenstellen();
        } catch (e) {
            console.error('Fehler beim Löschen', e);
        }
    };

    // Save Steuerberater
    const saveSteuerberater = async () => {
        if (!editingSteuerberater) return;
        setSaving(true);
        try {
            const method = editingSteuerberater.id ? 'PUT' : 'POST';
            const url = editingSteuerberater.id 
                ? `/api/firma/steuerberater/${editingSteuerberater.id}`
                : '/api/firma/steuerberater';
            
            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(editingSteuerberater)
            });
            if (res.ok) {
                await loadSteuerberater();
                setShowSteuerberaterModal(false);
                setEditingSteuerberater(null);
            } else {
                const text = await res.text();
                // Extract message if possible or just show alert
                // Often JSON { message: "..." } or plain text
                try {
                    const json = JSON.parse(text);
                    toast.error(json.message || 'Fehler beim Speichern');
                } catch {
                    toast.error('Fehler beim Speichern: ' + text);
                }
            }
        } catch (e) {
            console.error('Fehler beim Speichern', e);
            toast.error('Netzwerkfehler beim Speichern');
        } finally {
            setSaving(false);
        }
    };

    // Delete Steuerberater
    const deleteSteuerberater = async (id: number) => {
        if (!await confirmDialog({ title: 'Steuerberater löschen', message: 'Steuerberater wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            await fetch(`/api/firma/steuerberater/${id}`, { method: 'DELETE' });
            await loadSteuerberater();
        } catch (e) {
            console.error('Fehler beim Löschen', e);
        }
    };

    // Init Standard Kostenstellen
    const initKostenstellen = async () => {
        try {
            const res = await fetch('/api/firma/kostenstellen/init', { method: 'POST' });
            if (res.ok) {
                setKostenstellen(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Initialisieren', e);
        }
    };

    // EN 1090 Rollen CRUD
    const saveRolle = async () => {
        if (!editingRolle?.kurztext?.trim()) return;
        setSaving(true);
        try {
            const method = editingRolle.id ? 'PUT' : 'POST';
            const url = editingRolle.id ? `/api/en1090/rollen/${editingRolle.id}` : '/api/en1090/rollen';
            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ...editingRolle, aktiv: editingRolle.aktiv ?? true, sortierung: editingRolle.sortierung ?? 0 })
            });
            if (res.ok) {
                await loadEn1090Rollen();
                setShowRolleModal(false);
                setEditingRolle(null);
            }
        } catch (e) {
            console.error('Fehler beim Speichern der Rolle', e);
        } finally {
            setSaving(false);
        }
    };

    const deleteRolle = async (id: number) => {
        if (!await confirmDialog({ title: 'Rolle l\u00f6schen', message: 'Rolle wirklich l\u00f6schen? Zuweisungen werden entfernt.', variant: 'danger', confirmLabel: 'L\u00f6schen' })) return;
        try {
            await fetch(`/api/en1090/rollen/${id}`, { method: 'DELETE' });
            await loadEn1090Rollen();
        } catch (e) {
            console.error('Fehler beim L\u00f6schen', e);
        }
    };

    // Abteilung CRUD
    const handleCreateAbteilung = async () => {
        if (!newAbteilungName.trim()) return;
        setCreatingAbteilung(true);
        try {
            const res = await fetch('/api/abteilungen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: newAbteilungName.trim() })
            });
            if (res.ok) {
                setNewAbteilungName('');
                await loadAbteilungenData();
            } else {
                toast.error('Fehler beim Erstellen der Abteilung.');
            }
        } catch (e) {
            console.error(e);
            toast.error('Fehler beim Erstellen der Abteilung.');
        } finally {
            setCreatingAbteilung(false);
        }
    };

    const handleDeleteAbteilung = async (id: number) => {
        if (!await confirmDialog({ title: 'Abteilung löschen', message: 'Abteilung wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`/api/abteilungen/${id}`, { method: 'DELETE' });
            if (res.ok) {
                await loadAbteilungenData();
            } else if (res.status === 409) {
                toast.warning('Abteilung kann nicht gelöscht werden – noch Arbeitsgänge zugeordnet.');
            } else {
                toast.error('Fehler beim Löschen der Abteilung.');
            }
        } catch (e) {
            console.error(e);
        }
    };

    const handleCreateArbeitsgang = async () => {
        if (!newArbeitsgangBeschr.trim() || !selectedAbteilungId) return;
        setCreatingArbeitsgang(true);
        try {
            const res = await fetch('/api/arbeitsgaenge', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ beschreibung: newArbeitsgangBeschr.trim(), abteilungId: selectedAbteilungId })
            });
            if (res.ok) {
                setNewArbeitsgangBeschr('');
                await loadAbteilungenData();
            } else {
                toast.error('Fehler beim Erstellen des Arbeitsgangs.');
            }
        } catch (e) {
            console.error(e);
            toast.error('Fehler beim Erstellen des Arbeitsgangs.');
        } finally {
            setCreatingArbeitsgang(false);
        }
    };

    const handleDeleteArbeitsgang = async (id: number) => {
        if (!await confirmDialog({ title: 'Arbeitsgang löschen', message: 'Arbeitsgang wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`/api/arbeitsgaenge/${id}`, { method: 'DELETE' });
            if (res.ok) {
                await loadAbteilungenData();
            } else if (res.status === 409) {
                toast.warning('Arbeitsgang kann nicht gelöscht werden – wird noch verwendet.');
            } else {
                toast.error('Fehler beim Löschen des Arbeitsgangs.');
            }
        } catch (e) {
            console.error(e);
        }
    };

    const handleSaveStundensatz = async (arbeitsgangId: number, neuerStundensatz: number) => {
        const res = await fetch(`/api/arbeitsgaenge/${arbeitsgangId}/stundensatz`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ stundensatz: neuerStundensatz })
        });
        if (!res.ok) throw new Error('Speichern fehlgeschlagen');
        await loadAbteilungenData();
    };

    const getKostenstelleTypLabel = (typ: string) => {
        const option = KOSTENSTELLEN_TYP_OPTIONS.find(o => o.value === typ);
        return option?.label || typ;
    };

    const getKostenstelleTypColor = (typ: string) => {
        switch (typ) {
            case 'LAGER': return 'bg-blue-100 text-blue-700 border-blue-200';
            case 'GEMEINKOSTEN': return 'bg-rose-100 text-rose-700 border-rose-200';
            case 'PROJEKT': return 'bg-green-100 text-green-700 border-green-200';
            default: return 'bg-slate-100 text-slate-700 border-slate-200';
        }
    };

    return (
        <PageLayout
            ribbonCategory="Vorlagen & Stammdaten"
            title="FIRMENINFORMATIONEN"
            subtitle="Firmendaten, Kostenstellen, Steuerberater und Systemkonfiguration"
        >
            {loading ? (
                <div className="flex items-center justify-center py-20">
                    <RefreshCw className="w-8 h-8 animate-spin text-rose-600" />
                </div>
            ) : (
                <>
                    {/* Tab Navigation */}
                    <div className="flex gap-2 mb-6 border-b border-slate-200 pb-2">
                        <button
                            onClick={() => setActiveTab('firma')}
                            className={cn(
                                "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                activeTab === 'firma'
                                    ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                    : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            )}
                        >
                            <Building2 className="w-4 h-4 inline-block mr-2" />
                            Firmendaten
                        </button>
                        <button
                            onClick={() => setActiveTab('kostenstellen')}
                            className={cn(
                                "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                activeTab === 'kostenstellen'
                                    ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                    : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            )}
                        >
                            <Wallet className="w-4 h-4 inline-block mr-2" />
                            Kostenstellen ({kostenstellen.length})
                        </button>
                        <button
                            onClick={() => setActiveTab('steuerberater')}
                            className={cn(
                                "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                activeTab === 'steuerberater'
                                    ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                    : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            )}
                        >
                            <Users className="w-4 h-4 inline-block mr-2" />
                            Steuerberater ({steuerberater.length})
                        </button>
                        {features.en1090 && (
                            <button
                                onClick={() => setActiveTab('en1090rollen')}
                                className={cn(
                                    "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                    activeTab === 'en1090rollen'
                                        ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                        : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                <Shield className="w-4 h-4 inline-block mr-2" />
                                EN 1090 Rollen ({en1090Rollen.length})
                            </button>
                        )}
                        <button
                            onClick={() => setActiveTab('abteilungen')}
                            className={cn(
                                "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                activeTab === 'abteilungen'
                                    ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                    : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            )}
                        >
                            <Layers className="w-4 h-4 inline-block mr-2" />
                            Abteilungen ({abteilungen.length})
                        </button>
                        <button
                            onClick={() => setActiveTab('systemsetup')}
                            className={cn(
                                "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                activeTab === 'systemsetup'
                                    ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                    : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            )}
                        >
                            <Settings className="w-4 h-4 inline-block mr-2" />
                            System-Setup
                        </button>
                    </div>

                    {/* Tab Content */}
                    {activeTab === 'firma' && firma && (
                        <Card className="p-6">
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                {/* Allgemeine Daten */}
                                <div className="space-y-4">
                                    <h3 className="text-lg font-semibold text-slate-900 border-b pb-2">Allgemeine Daten</h3>
                                    <div>
                                        <Label>Firmenname *</Label>
                                        <Input
                                            value={firma.firmenname || ''}
                                            onChange={e => setFirma({ ...firma, firmenname: e.target.value })}
                                        />
                                    </div>
                                    <div>
                                        <Label>Geschäftsführer / Inhaber</Label>
                                        <Input
                                            value={firma.geschaeftsfuehrer || ''}
                                            onChange={e => setFirma({ ...firma, geschaeftsfuehrer: e.target.value })}
                                        />
                                    </div>
                                    <div>
                                        <Label>Straße</Label>
                                        <Input
                                            value={firma.strasse || ''}
                                            onChange={e => setFirma({ ...firma, strasse: e.target.value })}
                                        />
                                    </div>
                                    <div className="grid grid-cols-3 gap-2">
                                        <div>
                                            <Label>PLZ</Label>
                                            <Input
                                                value={firma.plz || ''}
                                                onChange={e => setFirma({ ...firma, plz: e.target.value })}
                                            />
                                        </div>
                                        <div className="col-span-2">
                                            <Label>Ort</Label>
                                            <Input
                                                value={firma.ort || ''}
                                                onChange={e => setFirma({ ...firma, ort: e.target.value })}
                                            />
                                        </div>
                                    </div>
                                </div>

                                {/* Kontakt */}
                                <div className="space-y-4">
                                    <h3 className="text-lg font-semibold text-slate-900 border-b pb-2">Kontaktdaten</h3>
                                    <div>
                                        <Label>Telefon</Label>
                                        <Input
                                            value={firma.telefon || ''}
                                            onChange={e => setFirma({ ...firma, telefon: e.target.value })}
                                        />
                                    </div>
                                    <div>
                                        <Label>Fax</Label>
                                        <Input
                                            value={firma.fax || ''}
                                            onChange={e => setFirma({ ...firma, fax: e.target.value })}
                                        />
                                    </div>
                                    <div>
                                        <Label>E-Mail</Label>
                                        <Input
                                            type="email"
                                            value={firma.email || ''}
                                            onChange={e => setFirma({ ...firma, email: e.target.value })}
                                        />
                                    </div>
                                    <div>
                                        <Label>Website</Label>
                                        <Input
                                            value={firma.website || ''}
                                            onChange={e => setFirma({ ...firma, website: e.target.value })}
                                        />
                                    </div>
                                </div>

                                {/* Steuerliche Daten */}
                                <div className="space-y-4">
                                    <h3 className="text-lg font-semibold text-slate-900 border-b pb-2">Steuerliche Angaben</h3>
                                    <div>
                                        <Label>Steuernummer</Label>
                                        <Input
                                            value={firma.steuernummer || ''}
                                            onChange={e => setFirma({ ...firma, steuernummer: e.target.value })}
                                        />
                                    </div>
                                    <div>
                                        <Label>USt-IdNr.</Label>
                                        <Input
                                            value={firma.ustIdNr || ''}
                                            onChange={e => setFirma({ ...firma, ustIdNr: e.target.value })}
                                        />
                                    </div>
                                    <div>
                                        <Label>Handelsregister</Label>
                                        <Input
                                            value={firma.handelsregister || ''}
                                            onChange={e => setFirma({ ...firma, handelsregister: e.target.value })}
                                            placeholder="z.B. Amtsgericht Würzburg"
                                        />
                                    </div>
                                    <div>
                                        <Label>Handelsregister-Nr.</Label>
                                        <Input
                                            value={firma.handelsregisterNummer || ''}
                                            onChange={e => setFirma({ ...firma, handelsregisterNummer: e.target.value })}
                                            placeholder="z.B. HRB 12345"
                                        />
                                    </div>
                                </div>

                                {/* Bankverbindung */}
                                <div className="space-y-4">
                                    <h3 className="text-lg font-semibold text-slate-900 border-b pb-2">Bankverbindung</h3>
                                    <div>
                                        <Label>Bank</Label>
                                        <Input
                                            value={firma.bankName || ''}
                                            onChange={e => setFirma({ ...firma, bankName: e.target.value })}
                                        />
                                    </div>
                                    <div>
                                        <Label>IBAN</Label>
                                        <Input
                                            value={firma.iban || ''}
                                            onChange={e => setFirma({ ...firma, iban: e.target.value })}
                                        />
                                    </div>
                                    <div>
                                        <Label>BIC</Label>
                                        <Input
                                            value={firma.bic || ''}
                                            onChange={e => setFirma({ ...firma, bic: e.target.value })}
                                        />
                                    </div>
                                </div>
                            </div>

                            <div className="mt-6 pt-4 border-t flex justify-end">
                                <Button
                                    onClick={saveFirma}
                                    disabled={saving}
                                    className="bg-rose-600 text-white hover:bg-rose-700"
                                >
                                    {saving ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                                    Speichern
                                </Button>
                            </div>
                        </Card>
                    )}

                    {activeTab === 'kostenstellen' && (
                        <div className="space-y-4">
                            <div className="flex justify-between items-center">
                                <p className="text-slate-500 text-sm">
                                    Kostenstellen für die Zuordnung von Lieferantenrechnungen
                                </p>
                                <div className="flex gap-2">
                                    {kostenstellen.length === 0 && (
                                        <Button
                                            variant="outline"
                                            onClick={initKostenstellen}
                                            className="border-rose-300 text-rose-700"
                                        >
                                            Standard anlegen
                                        </Button>
                                    )}
                                    <Button
                                        onClick={() => {
                                            setEditingKostenstelle({ aktiv: true, sortierung: kostenstellen.length + 1 });
                                            setShowKostenstelleModal(true);
                                        }}
                                        className="bg-rose-600 text-white hover:bg-rose-700"
                                    >
                                        <Plus className="w-4 h-4 mr-2" />
                                        Neue Kostenstelle
                                    </Button>
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                {selectedKostenstelle ? (
                                    <div className="col-span-full">
                                        <KostenstelleDetailView 
                                            kostenstelle={selectedKostenstelle} 
                                            onBack={() => setSelectedKostenstelle(null)} 
                                        />
                                    </div>
                                ) : (
                                    kostenstellen.map(ks => (
                                    <Card 
                                        key={ks.id} 
                                        className="p-4 cursor-pointer hover:shadow-md transition-shadow group"
                                        onClick={() => setSelectedKostenstelle(ks)}
                                    >
                                        <div className="flex justify-between items-start">
                                            <div>
                                                <h4 className="font-semibold text-slate-900 group-hover:text-rose-600 transition-colors">{ks.bezeichnung}</h4>
                                                <span className={cn(
                                                    "inline-block px-2 py-0.5 text-xs rounded border mt-1",
                                                    getKostenstelleTypColor(ks.typ)
                                                )}>
                                                    {getKostenstelleTypLabel(ks.typ)}
                                                </span>
                                            </div>
                                            <div className="flex gap-1">
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        setEditingKostenstelle(ks);
                                                        setShowKostenstelleModal(true);
                                                    }}
                                                >
                                                    <Edit2 className="w-4 h-4" />
                                                </Button>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        deleteKostenstelle(ks.id);
                                                    }}
                                                    className="text-red-600 hover:text-red-700"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </Button>
                                            </div>
                                        </div>
                                        {ks.beschreibung && (
                                            <p className="text-sm text-slate-500 mt-2">{ks.beschreibung}</p>
                                        )}
                                        <div className="flex gap-2 mt-2">
                                            {ks.istFixkosten && (
                                                <span className="text-xs bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded">Fixkosten</span>
                                            )}
                                            {ks.istInvestition && (
                                                <span className="text-xs bg-purple-100 text-purple-700 px-1.5 py-0.5 rounded">Investition</span>
                                            )}
                                        </div>
                                    </Card>
                                )))}
                            </div>
                        </div>
                    )}

                    {activeTab === 'steuerberater' && (
                        <div className="space-y-6">
                            {/* Sub-Navigation */}
                            <div className="flex gap-2 border-b border-slate-200 pb-1">
                                <button
                                    onClick={() => setSbSubTab('kontakte')}
                                    className={cn(
                                        "px-3 py-1 text-sm font-medium rounded-md transition",
                                        sbSubTab === 'kontakte'
                                            ? "bg-slate-100 text-slate-900"
                                            : "text-slate-500 hover:text-slate-700"
                                    )}
                                >
                                    Kontakte
                                </button>
                                <button
                                    onClick={() => setSbSubTab('lohnabrechnungen')}
                                    className={cn(
                                        "px-3 py-1 text-sm font-medium rounded-md transition",
                                        sbSubTab === 'lohnabrechnungen'
                                            ? "bg-slate-100 text-slate-900"
                                            : "text-slate-500 hover:text-slate-700"
                                    )}
                                >
                                    Lohnabrechnungen im System
                                </button>
                                <button
                                    onClick={() => setSbSubTab('bwa')}
                                    className={cn(
                                        "px-3 py-1 text-sm font-medium rounded-md transition",
                                        sbSubTab === 'bwa'
                                            ? "bg-slate-100 text-slate-900"
                                            : "text-slate-500 hover:text-slate-700"
                                    )}
                                >
                                    BWA / Auswertungen
                                </button>
                            </div>

                            {sbSubTab === 'kontakte' && (
                                <div className="space-y-4">
                                    <div className="flex justify-between items-center">
                                        <p className="text-slate-500 text-sm">
                                            Steuerberater-Kontakte für automatische BWA-Erkennung
                                        </p>
                                        <Button
                                            onClick={() => {
                                                setEditingSteuerberater({ aktiv: true, autoProcessEmails: true, gueltigAb: new Date().toISOString().split('T')[0] });
                                                setShowSteuerberaterModal(true);
                                            }}
                                            className="bg-rose-600 text-white hover:bg-rose-700"
                                        >
                                            <Plus className="w-4 h-4 mr-2" />
                                            Neuer Steuerberater
                                        </Button>
                                    </div>

                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        {steuerberater.map(sb => (
                                            <Card key={sb.id} className="p-4">
                                                <div className="flex justify-between items-start">
                                                    <div>
                                                        <h4 className="font-semibold text-slate-900">{sb.name}</h4>
                                                        <p className="text-sm text-slate-500">{sb.email}</p>
                                                        {sb.ansprechpartner && (
                                                            <p className="text-sm text-slate-400">Ansprechpartner: {sb.ansprechpartner}</p>
                                                        )}
                                                        <div className="flex gap-4 mt-2 text-xs text-slate-500">
                                                            <div>
                                                                <span className="font-medium">Von:</span> {sb.gueltigAb ? new Date(sb.gueltigAb).toLocaleDateString() : 'Offen'}
                                                            </div>
                                                            <div>
                                                                <span className="font-medium">Bis:</span> {sb.gueltigBis ? new Date(sb.gueltigBis).toLocaleDateString() : 'Offen'}
                                                            </div>
                                                        </div>
                                                        {sb.weitereEmails && sb.weitereEmails.length > 0 && (
                                                            <div className="mt-2 text-xs text-slate-500">
                                                                <span className="font-medium">Weitere E-Mails:</span> {sb.weitereEmails.join(', ')}
                                                            </div>
                                                        )}
                                                    </div>
                                                    <div className="flex gap-1">
                                                        <Button
                                                            variant="ghost"
                                                            size="sm"
                                                            onClick={() => {
                                                                setEditingSteuerberater(sb);
                                                                setShowSteuerberaterModal(true);
                                                            }}
                                                        >
                                                            <Edit2 className="w-4 h-4" />
                                                        </Button>
                                                        <Button
                                                            variant="ghost"
                                                            size="sm"
                                                            onClick={() => deleteSteuerberater(sb.id)}
                                                            className="text-red-600 hover:text-red-700"
                                                        >
                                                            <Trash2 className="w-4 h-4" />
                                                        </Button>
                                                    </div>
                                                </div>
                                                <div className="flex gap-2 mt-3">
                                                    {sb.autoProcessEmails && (
                                                        <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded">
                                                            Auto-Verarbeitung aktiv
                                                        </span>
                                                    )}
                                                </div>
                                            </Card>
                                        ))}
                                        {steuerberater.length === 0 && (
                                            <div className="col-span-2 text-center py-8 text-slate-400">
                                                Noch keine Steuerberater angelegt
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}

                            {sbSubTab === 'lohnabrechnungen' && (
                                <div className="space-y-4">
                                    <div className="flex gap-4 items-end bg-slate-50 p-4 rounded-lg border border-slate-200">
                                        <div className="w-48">
                                            <Label>Jahr</Label>
                                            <Select
                                                value={selectedJahr.toString()}
                                                onChange={v => setSelectedJahr(parseInt(v))}
                                                options={verfuegbareJahre.map(j => ({ value: j.toString(), label: j.toString() }))}
                                            />
                                        </div>
                                        <div className="w-64">
                                            <Label>Steuerberater Filter</Label>
                                            <Select
                                                value={selectedSbFilter}
                                                onChange={setSelectedSbFilter}
                                                options={[
                                                    { value: 'ALL', label: 'Alle anzeigen' },
                                                    ...steuerberater.map(sb => ({ value: sb.id.toString(), label: sb.name }))
                                                ]}
                                            />
                                        </div>
                                        <div className="ml-auto">
                                            <p className="text-sm text-slate-500 text-right">
                                                {lohnabrechnungen.length} Dokumente gefunden
                                            </p>
                                        </div>
                                    </div>

                                    <div className="grid grid-cols-1 gap-2">
                                        {lohnabrechnungen.map(la => (
                                            <div key={la.id} className="flex items-center justify-between p-3 bg-white border border-slate-100 rounded-lg hover:border-rose-200 transition shadow-sm">
                                                <div className="flex items-center gap-4">
                                                    <div className="h-10 w-10 bg-rose-50 rounded-full flex items-center justify-center text-rose-600">
                                                        <FileText className="w-5 h-5" />
                                                    </div>
                                                    <div>
                                                        <p className="font-medium text-slate-900">{la.mitarbeiterName}</p>
                                                        <div className="flex gap-2 text-xs text-slate-500">
                                                            <span className="flex items-center">
                                                                <Calendar className="w-3 h-3 mr-1" />
                                                                {la.monat}/{la.jahr}
                                                            </span>
                                                            {la.bruttolohn && (
                                                                <span>• Brutto: {la.bruttolohn.toFixed(2)} €</span>
                                                            )}
                                                            {la.nettolohn && (
                                                                <span>• Netto: {la.nettolohn.toFixed(2)} €</span>
                                                            )}
                                                        </div>
                                                    </div>
                                                </div>
                                                <div className="flex items-center gap-4">
                                                    {la.status === 'NEU' && (
                                                        <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded">Neu</span>
                                                    )}
                                                    <a 
                                                        href={la.downloadUrl}
                                                        target="_blank"
                                                        rel="noopener noreferrer"
                                                        className="text-rose-600 hover:text-rose-700 hover:bg-rose-50 p-2 rounded-full transition"
                                                        title="PDF öffnen"
                                                    >
                                                        <Download className="w-4 h-4" />
                                                    </a>
                                                </div>
                                            </div>
                                        ))}
                                        {lohnabrechnungen.length === 0 && (
                                            <div className="text-center py-12 text-slate-400 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                                                Keine Lohnabrechnungen für diesen Zeitraum gefunden
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}

                            {sbSubTab === 'bwa' && (
                                <div className="space-y-4">
                                    <div className="flex gap-4 items-end bg-slate-50 p-4 rounded-lg border border-slate-200">
                                        <div className="w-48">
                                            <Label>Jahr</Label>
                                            <Select
                                                value={selectedJahr.toString()}
                                                onChange={v => setSelectedJahr(parseInt(v))}
                                                options={verfuegbareJahre.map(j => ({ value: j.toString(), label: j.toString() }))}
                                            />
                                        </div>
                                        <div className="ml-auto">
                                            <p className="text-sm text-slate-500 text-right">
                                                {bwaListe.length} Auswertungen gefunden
                                            </p>
                                        </div>
                                    </div>

                                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                        {bwaListe.map(bwa => (
                                            <Card key={bwa.id} className="p-4 flex flex-col gap-3 hover:border-rose-200 transition">
                                                <div className="flex justify-between items-start">
                                                    <div>
                                                        <h4 className="font-semibold text-slate-900">
                                                            {bwa.typ === 'MONATLICH' ? `BWA ${bwa.monat}/${bwa.jahr}` : `Jahresabschluss ${bwa.jahr}`}
                                                        </h4>
                                                        <p className="text-xs text-slate-500">
                                                            {bwa.steuerberaterName || 'Unbekannter Steuerberater'}
                                                        </p>
                                                    </div>
                                                    <a 
                                                        href={bwa.pdfUrl}
                                                        target="_blank"
                                                        rel="noopener noreferrer"
                                                        className="text-rose-600 hover:bg-rose-50 p-1.5 rounded-full"
                                                        title="PDF anzeigen"
                                                    >
                                                        <Download className="w-4 h-4" />
                                                    </a>
                                                </div>

                                                <div className="space-y-1 text-sm border-t pt-2 mt-auto">
                                                    <div className="flex justify-between">
                                                        <span className="text-slate-500">Gemeinkosten:</span>
                                                        <span className="font-medium text-slate-900">
                                                            {(bwa.gesamtGemeinkosten || 0).toFixed(2)} €
                                                        </span>
                                                    </div>
                                                </div>

                                                <div className="flex gap-2 text-xs pt-1">
                                                    {bwa.analysiert ? (
                                                        <span className="bg-green-100 text-green-700 px-2 py-0.5 rounded flex items-center">
                                                            <RefreshCw className="w-3 h-3 mr-1" />
                                                            Analysiert
                                                        </span>
                                                    ) : (
                                                        <span className="bg-yellow-100 text-yellow-700 px-2 py-0.5 rounded">
                                                            Ausstehend
                                                        </span>
                                                    )}
                                                    {bwa.freigegeben && (
                                                        <span className="bg-blue-100 text-blue-700 px-2 py-0.5 rounded">
                                                            Freigegeben
                                                        </span>
                                                    )}
                                                </div>
                                            </Card>
                                        ))}
                                        {bwaListe.length === 0 && (
                                            <div className="col-span-full text-center py-12 text-slate-400 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                                                Keine BWA-Dokumente für {selectedJahr} gefunden
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}

                    {activeTab === 'abteilungen' && (
                        <div className="grid grid-cols-1 xl:grid-cols-[1fr_2fr] gap-6">
                            {/* Left: Abteilungen List + Create */}
                            <Card className="p-6 border-0 shadow-sm rounded-xl">
                                <div className="mb-4">
                                    <p className="text-xs uppercase tracking-wide text-slate-500">Struktur</p>
                                    <h4 className="text-lg font-semibold text-slate-900">Abteilungen</h4>
                                </div>

                                <div className="space-y-2">
                                    {abteilungen.length === 0 ? (
                                        <div className="p-8 text-center text-slate-500 border border-dashed rounded-lg">
                                            <Building2 className="w-8 h-8 mx-auto mb-2 text-rose-200" />
                                            Keine Abteilungen vorhanden
                                        </div>
                                    ) : (
                                        abteilungen.map(abt => (
                                            <div
                                                key={abt.id}
                                                className={cn(
                                                    'group flex items-center justify-between gap-2 rounded-lg border px-3 py-2 cursor-pointer transition',
                                                    'border-slate-200 bg-white hover:border-rose-200 hover:shadow-sm',
                                                    selectedAbteilungId === abt.id ? 'border-rose-500 bg-rose-50 shadow-sm' : ''
                                                )}
                                                onClick={() => setSelectedAbteilungId(abt.id)}
                                            >
                                                <div className="flex items-center gap-2 min-w-0">
                                                    <Building2 className="w-4 h-4 text-rose-600 flex-shrink-0" />
                                                    <span className="text-sm font-semibold text-slate-900 truncate">{abt.name}</span>
                                                </div>
                                                <div className="flex items-center gap-1 flex-shrink-0">
                                                    {selectedAbteilungId === abt.id && (
                                                        <ChevronRight className="w-4 h-4 text-rose-600" />
                                                    )}
                                                    <button
                                                        onClick={e => { e.stopPropagation(); handleDeleteAbteilung(abt.id); }}
                                                        className="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors opacity-0 group-hover:opacity-100"
                                                        title="Abteilung löschen"
                                                    >
                                                        <Trash2 className="w-3 h-3" />
                                                    </button>
                                                </div>
                                            </div>
                                        ))
                                    )}
                                </div>

                                <div className="mt-4 pt-4 border-t border-slate-100 space-y-2">
                                    <Input
                                        value={newAbteilungName}
                                        onChange={e => setNewAbteilungName(e.target.value)}
                                        placeholder="Neue Abteilung..."
                                        onKeyDown={e => e.key === 'Enter' && handleCreateAbteilung()}
                                    />
                                    <Button
                                        className="w-full bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                        size="sm"
                                        onClick={handleCreateAbteilung}
                                        disabled={!newAbteilungName.trim() || creatingAbteilung}
                                    >
                                        <Plus className="w-4 h-4 mr-1" /> Abteilung anlegen
                                    </Button>
                                </div>
                            </Card>

                            {/* Right: Arbeitsgänge */}
                            <Card className="p-6 border-0 shadow-sm rounded-xl">
                                <div className="flex items-center justify-between mb-4">
                                    <div>
                                        <p className="text-xs uppercase tracking-wide text-slate-500">Arbeitsgänge</p>
                                        <h4 className="text-lg font-semibold text-slate-900">
                                            {selectedAbteilung?.name || 'Abteilung auswählen'}
                                        </h4>
                                    </div>
                                </div>

                                {selectedAbteilungId ? (
                                    <div className="space-y-4">
                                        {/* Search */}
                                        <div className="relative">
                                            <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-400" />
                                            <Input
                                                value={agSearchTerm}
                                                onChange={e => setAgSearchTerm(e.target.value)}
                                                placeholder="Arbeitsgänge durchsuchen..."
                                                className="pl-9"
                                            />
                                        </div>

                                        {/* List */}
                                        <div className="space-y-2">
                                            {filteredArbeitsgaenge.length === 0 ? (
                                                <div className="p-8 text-center text-slate-500 border border-dashed rounded-lg">
                                                    <Plus className="w-8 h-8 mx-auto mb-2 text-rose-200" />
                                                    Keine Arbeitsgänge in dieser Abteilung
                                                </div>
                                            ) : (
                                                filteredArbeitsgaenge.map(ag => {
                                                    const currentYear = new Date().getFullYear();
                                                    const isOutdated = ag.stundensatzJahr !== null && currentYear - ag.stundensatzJahr >= 1;
                                                    const hasNoRate = ag.stundensatz === null;
                                                    return (
                                                        <div key={ag.id} className="flex items-center justify-between gap-3 p-3 rounded-lg border border-slate-200 bg-white hover:border-rose-200 hover:shadow-sm transition">
                                                            <div className="min-w-0 flex-1">
                                                                <div className="flex items-center gap-2">
                                                                    <p className="text-sm font-semibold text-slate-900 truncate">{ag.beschreibung}</p>
                                                                    {(isOutdated || hasNoRate) && (
                                                                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-orange-100 text-orange-700">
                                                                            Stundensatz veraltet
                                                                        </span>
                                                                    )}
                                                                </div>
                                                                <p className="text-sm text-rose-700 mt-0.5">
                                                                    {ag.stundensatz !== null
                                                                        ? new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(ag.stundensatz)
                                                                        : '—'}
                                                                    <span className="text-slate-400 mx-1">/</span>
                                                                    <span className="text-slate-600">Stunde</span>
                                                                    {ag.stundensatzJahr && (
                                                                        <span className="text-xs text-slate-400 ml-2">({ag.stundensatzJahr})</span>
                                                                    )}
                                                                </p>
                                                            </div>
                                                            <div className="flex items-center gap-1 flex-shrink-0">
                                                                <Button variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100"
                                                                    onClick={() => setEditingArbeitsgang(ag)} title="Stundensatz bearbeiten">
                                                                    <Pencil className="w-4 h-4" />
                                                                </Button>
                                                                <Button variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100"
                                                                    onClick={() => handleDeleteArbeitsgang(ag.id)} title="Löschen">
                                                                    <Trash2 className="w-4 h-4" />
                                                                </Button>
                                                            </div>
                                                        </div>
                                                    );
                                                })
                                            )}
                                        </div>

                                        {/* Add Arbeitsgang */}
                                        <div className="flex gap-2 pt-2 border-t border-slate-100">
                                            <Input
                                                value={newArbeitsgangBeschr}
                                                onChange={e => setNewArbeitsgangBeschr(e.target.value)}
                                                placeholder="Neuer Arbeitsgang, z.B. Montage Metallfassade"
                                                onKeyDown={e => e.key === 'Enter' && handleCreateArbeitsgang()}
                                                className="flex-1"
                                            />
                                            <Button
                                                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                                size="sm"
                                                onClick={handleCreateArbeitsgang}
                                                disabled={!newArbeitsgangBeschr.trim() || creatingArbeitsgang}
                                            >
                                                <Plus className="w-4 h-4 mr-1" />
                                                Erstellen
                                            </Button>
                                        </div>
                                    </div>
                                ) : (
                                    <div className="p-10 text-center text-slate-500 border border-dashed rounded-lg">
                                        Wähle eine Abteilung aus
                                    </div>
                                )}
                            </Card>
                        </div>
                    )}

                    {activeTab === 'systemsetup' && (
                        <div className="space-y-4">
                            <p className="text-sm text-slate-500">
                                Gemini API Key und SMTP-Verbindung zentral konfigurieren und direkt im System prüfen.
                            </p>
                            <SystemSetupConfigurator />
                        </div>
                    )}

                    {activeTab === 'en1090rollen' && features.en1090 && (
                        <div className="space-y-4">
                            <div className="flex justify-between items-center">
                                <p className="text-slate-500 text-sm">
                                    EN&nbsp;1090 Rollen definieren – z.&thinsp;B. WPK-Leiter, Schweißaufsicht, Monteur. Diese Rollen können Mitarbeitern zugewiesen werden.
                                </p>
                                <Button
                                    size="sm"
                                    className="bg-rose-600 text-white hover:bg-rose-700"
                                    onClick={() => { setEditingRolle({ aktiv: true, sortierung: (en1090Rollen.length + 1) * 10 }); setShowRolleModal(true); }}
                                >
                                    <Plus className="w-4 h-4 mr-2" />
                                    Neue Rolle
                                </Button>
                            </div>

                            {en1090Rollen.length === 0 ? (
                                <div className="text-center py-16 text-slate-400 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                                    <Shield className="w-10 h-10 mx-auto mb-3 text-slate-300" />
                                    <p className="font-medium">Noch keine Rollen angelegt</p>
                                    <p className="text-sm mt-1">Standard-Rollen werden beim ersten Start automatisch angelegt.</p>
                                </div>
                            ) : (
                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                    {en1090Rollen.map(rolle => (
                                        <Card key={rolle.id} className="p-4 flex flex-col gap-3">
                                            <div className="flex items-start justify-between gap-2">
                                                <div className="flex-1 min-w-0">
                                                    <p className="font-semibold text-slate-900 truncate">{rolle.kurztext}</p>
                                                    {rolle.beschreibung && (
                                                        <p className="text-xs text-slate-500 mt-1 line-clamp-3">{rolle.beschreibung}</p>
                                                    )}
                                                </div>
                                                <span className={`flex items-center gap-1 text-xs px-2 py-0.5 rounded-full border font-medium shrink-0 ${rolle.aktiv ? 'bg-green-50 text-green-700 border-green-200' : 'bg-slate-100 text-slate-500 border-slate-200'}`}>
                                                    {rolle.aktiv ? <CheckCircle className="w-3 h-3" /> : <XCircle className="w-3 h-3" />}
                                                    {rolle.aktiv ? 'Aktiv' : 'Inaktiv'}
                                                </span>
                                            </div>
                                            <div className="flex gap-2 pt-2 border-t border-slate-100">
                                                <Button size="sm" variant="outline" className="flex-1 border-slate-200 text-slate-600 hover:bg-slate-50"
                                                    onClick={() => { setEditingRolle({ ...rolle }); setShowRolleModal(true); }}>
                                                    <Edit2 className="w-3 h-3 mr-1" /> Bearbeiten
                                                </Button>
                                                <Button size="sm" variant="ghost" className="text-rose-600 hover:bg-rose-50 hover:text-rose-700"
                                                    onClick={() => deleteRolle(rolle.id)}>
                                                    <Trash2 className="w-3 h-3" />
                                                </Button>
                                            </div>
                                        </Card>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </>
            )}

            {/* EN 1090 Rolle Modal */}
            <Dialog open={showRolleModal} onOpenChange={setShowRolleModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{editingRolle?.id ? 'Rolle bearbeiten' : 'Neue EN\u00a01090 Rolle'}</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <div>
                            <Label>Kurzbezeichnung *</Label>
                            <Input
                                placeholder="z.B. Schweißaufsicht (SAP)"
                                value={editingRolle?.kurztext || ''}
                                onChange={e => setEditingRolle(prev => ({ ...prev, kurztext: e.target.value }))}
                            />
                        </div>
                        <div>
                            <Label>Beschreibung</Label>
                            <textarea
                                className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 min-h-[100px] resize-y"
                                placeholder="Aufgaben und Verantwortlichkeiten dieser Rolle..."
                                value={editingRolle?.beschreibung || ''}
                                onChange={e => setEditingRolle(prev => ({ ...prev, beschreibung: e.target.value }))}
                            />
                        </div>
                        <div className="flex items-center gap-3">
                            <input
                                type="checkbox"
                                id="rolle-aktiv"
                                checked={editingRolle?.aktiv ?? true}
                                onChange={e => setEditingRolle(prev => ({ ...prev, aktiv: e.target.checked }))}
                                className="rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                            />
                            <label htmlFor="rolle-aktiv" className="text-sm text-slate-700">Rolle ist aktiv und kann Mitarbeitern zugewiesen werden</label>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => { setShowRolleModal(false); setEditingRolle(null); }}>
                            <X className="w-4 h-4 mr-2" /> Abbrechen
                        </Button>
                        <Button
                            onClick={saveRolle}
                            disabled={saving || !editingRolle?.kurztext?.trim()}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            <Save className="w-4 h-4 mr-2" /> Speichern
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Kostenstelle Modal */}
            <Dialog open={showKostenstelleModal} onOpenChange={setShowKostenstelleModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>
                            {editingKostenstelle?.id ? 'Kostenstelle bearbeiten' : 'Neue Kostenstelle'}
                        </DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <div>
                            <Label>Bezeichnung *</Label>
                            <Input
                                value={editingKostenstelle?.bezeichnung || ''}
                                onChange={e => setEditingKostenstelle({ ...editingKostenstelle, bezeichnung: e.target.value })}
                            />
                        </div>
                        <div>
                            <Label>Typ *</Label>
                            <Select
                                value={editingKostenstelle?.typ || 'GEMEINKOSTEN'}
                                onChange={value => setEditingKostenstelle({ ...editingKostenstelle, typ: value as Kostenstelle['typ'] })}
                                options={KOSTENSTELLEN_TYP_OPTIONS}
                            />
                        </div>
                        <div>
                            <Label>Beschreibung</Label>
                            <Input
                                value={editingKostenstelle?.beschreibung || ''}
                                onChange={e => setEditingKostenstelle({ ...editingKostenstelle, beschreibung: e.target.value })}
                            />
                        </div>
                        <p className="text-xs text-slate-500">
                            {(editingKostenstelle?.typ || 'GEMEINKOSTEN') === 'GEMEINKOSTEN'
                                ? 'Wird als Fixkosten für die Gemeinkostenberechnung verwendet.'
                                : (editingKostenstelle?.typ || '') === 'LAGER'
                                    ? 'Wird als Investition gewertet (keine echten Kosten).'
                                    : (editingKostenstelle?.typ || '') === 'PROJEKT'
                                        ? 'Kosten werden dem jeweiligen Projekt zugeordnet.'
                                        : 'Sonstige Kostenzuordnung.'}
                        </p>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowKostenstelleModal(false)}>
                            <X className="w-4 h-4 mr-2" />
                            Abbrechen
                        </Button>
                        <Button
                            onClick={saveKostenstelle}
                            disabled={saving || !editingKostenstelle?.bezeichnung}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {saving ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                            Speichern
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Steuerberater Modal */}
            <Dialog open={showSteuerberaterModal} onOpenChange={setShowSteuerberaterModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>
                            {editingSteuerberater?.id ? 'Steuerberater bearbeiten' : 'Neuer Steuerberater'}
                        </DialogTitle>
                    </DialogHeader>
                    <div className="grid gap-4 py-4">
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label>Name</Label>
                                <Input 
                                    value={editingSteuerberater?.name || ''} 
                                    onChange={e => setEditingSteuerberater(prev => ({ ...prev, name: e.target.value }))}
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>Ansprechpartner</Label>
                                <Input 
                                    value={editingSteuerberater?.ansprechpartner || ''} 
                                    onChange={e => setEditingSteuerberater(prev => ({ ...prev, ansprechpartner: e.target.value }))}
                                />
                            </div>
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label>E-Mail</Label>
                                <Input 
                                    value={editingSteuerberater?.email || ''} 
                                    onChange={e => setEditingSteuerberater(prev => ({ ...prev, email: e.target.value }))}
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>Telefon</Label>
                                <Input 
                                    value={editingSteuerberater?.telefon || ''} 
                                    onChange={e => setEditingSteuerberater(prev => ({ ...prev, telefon: e.target.value }))}
                                />
                            </div>
                        </div>
                        <div className="space-y-2">
                            <Label>Weitere E-Mails (kommagetrennt)</Label>
                            <Input 
                                value={editingSteuerberater?.weitereEmails ? editingSteuerberater.weitereEmails.join(', ') : ''} 
                                onChange={e => setEditingSteuerberater(prev => ({ 
                                    ...prev, 
                                    weitereEmails: e.target.value.split(',').map(s => s.trim()).filter(Boolean)
                                }))}
                                placeholder="z.B. buchhaltung@kanzlei.de, sekretariat@kanzlei.de"
                            />
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label>Gültig Ab *</Label>
                                <DatePicker 
                                    value={editingSteuerberater?.gueltigAb || ''} 
                                    onChange={v => setEditingSteuerberater(prev => ({ ...prev, gueltigAb: v }))}
                                    placeholder="Startdatum"
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>Gültig Bis</Label>
                                <DatePicker 
                                    value={editingSteuerberater?.gueltigBis || ''} 
                                    onChange={v => setEditingSteuerberater(prev => ({ ...prev, gueltigBis: v }))}
                                    placeholder="Enddatum (optional)"
                                />
                            </div>
                        </div>
                        <div className="space-y-2">
                            <div className="flex items-center space-x-2">
                                <input 
                                    type="checkbox" 
                                    id="sb_auto"
                                    aria-label="Automatische E-Mail Verarbeitung"
                                    title="Automatische E-Mail Verarbeitung"
                                    checked={editingSteuerberater?.autoProcessEmails !== false}
                                    onChange={e => setEditingSteuerberater(prev => ({ ...prev, autoProcessEmails: e.target.checked }))}
                                    className="rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                />
                                <Label htmlFor="sb_auto">Automatische E-Mail Verarbeitung (BWA)</Label>
                            </div>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowSteuerberaterModal(false)}>Abbrechen</Button>
                        <Button onClick={saveSteuerberater} className="bg-rose-600 text-white hover:bg-rose-700">Speichern</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
            {/* Stundensatz Modal (Abteilungen-Tab) */}
            {editingArbeitsgang && (
                <StundensatzEditModal
                    arbeitsgang={editingArbeitsgang}
                    isOpen={!!editingArbeitsgang}
                    onClose={() => setEditingArbeitsgang(null)}
                    onSave={handleSaveStundensatz}
                />
            )}
        </PageLayout>
    );
}
