import { useState, useEffect, useCallback, useMemo } from 'react';
import { Building2, Wallet, Users, Plus, Edit2, Trash2, Save, X, RefreshCw, FileText, Download, Calendar, ShieldCheck, AtSign, HeartPulse, CalendarClock, BellRing, Mail, MailWarning, Info, type LucideIcon } from 'lucide-react';
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
import { SteuerpruefungExport } from '../components/firma/SteuerpruefungExport';
import { LohnStammdatenPanel } from '../components/firma/LohnStammdatenPanel';

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
    googleBewertungsLink: string;
    mahnverfahrenAktiv: boolean;
    tageBisZahlungserinnerung: number;
    tageBisErsteMahnung: number;
    tageBisZweiteMahnung: number;
    mahnverfahrenNeuesZahlungszielTage: number;
    gewerkId?: number | null;
    gewerkName?: string | null;
    bgName?: string | null;
    bgSatzVorschlag?: number | null;
    bgSatzOverride?: number | null;
    bgSatzEffektiv?: number | null;
}

interface GewerkOption {
    id: number;
    name: string;
    bgName: string;
    bgSatzProzent: number;
    aktiv: boolean;
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

interface SteuerberaterAnsprechpartner {
    id?: number;
    anrede: string | null;
    vorname: string;
    nachname: string;
    email: string;
    telefon: string;
    istLohnAnsprechpartner: boolean;
    notizen?: string;
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
    ansprechpartnerListe: SteuerberaterAnsprechpartner[];
}

const ANREDE_OPTIONS = [
    { value: '', label: '(keine)' },
    { value: 'HERR', label: 'Sehr geehrter Herr' },
    { value: 'FRAU', label: 'Sehr geehrte Frau' },
    { value: 'DAMEN_HERREN', label: 'Sehr geehrte Damen und Herren' },
    { value: 'FAMILIE', label: 'Sehr geehrte Familie' },
];


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

interface EmailAbsender {
    id: number;
    emailAdresse: string;
    anzeigename: string;
    aktiv: boolean;
    sortierung: number;
}

type ActiveTab = 'firma' | 'kostenstellen' | 'steuerberater' | 'absender' | 'steuerpruefung' | 'lohn-stammdaten' | 'unfallversicherung';
type SteuerberaterSubTab = 'kontakte' | 'lohnabrechnungen' | 'bwa';

const KOSTENSTELLEN_TYP_OPTIONS = [
    { value: 'LAGER', label: 'Lager (Investitionen)' },
    { value: 'GEMEINKOSTEN', label: 'Gemeinkosten (Fixkosten)' },
    { value: 'PROJEKT', label: 'Projekt' },
    { value: 'SONSTIG', label: 'Sonstige' },
];

// --- Mahnverfahren-Zeitstrahl (lokale Bausteine, nur in dieser Seite verwendet) ---

const MAHN_DEFAULT_TAGE = 7;

/** Eine Station auf dem Zeitstrahl: Kreis-Marker mit Icon, Label daneben (mobil) bzw. darunter (Desktop). */
function ZeitstrahlStation({ icon: Icon, label }: { icon: LucideIcon; label: string }) {
    return (
        <div className="flex shrink-0 items-center gap-3 md:w-32 md:flex-col md:gap-2">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border-2 border-rose-300 bg-rose-50 text-rose-600">
                <Icon className="h-5 w-5" />
            </div>
            <span className="text-sm font-medium text-slate-700 md:text-center">{label}</span>
        </div>
    );
}

/** Verbindung zwischen zwei Stationen mit Tage-Eingabefeld: mobil vertikal, ab md horizontal. */
function ZeitstrahlAbstand({ value, onChange, beschriftung }: { value: number; onChange: (tage: number) => void; beschriftung: string }) {
    return (
        <div className="flex md:flex-1 md:flex-col">
            {/* Verbindungslinie: mobil senkrecht unter dem Kreis, ab md waagerecht auf Kreis-Mitte */}
            <div className="flex w-10 shrink-0 justify-center self-stretch md:h-5 md:w-full md:items-end md:self-auto">
                <div className="min-h-[3.5rem] w-px bg-rose-200 md:h-px md:min-h-0 md:w-full" />
            </div>
            <div className="flex items-center gap-2 py-3 md:mt-2 md:flex-col md:gap-1 md:px-2 md:py-0">
                <Input
                    type="number"
                    min={1}
                    value={value}
                    onChange={e => onChange(parseInt(e.target.value) || MAHN_DEFAULT_TAGE)}
                    className="w-20 text-center"
                />
                <span className="text-xs text-slate-500 md:text-center">{beschriftung}</span>
            </div>
        </div>
    );
}

export default function FirmaEditor() {
    const toast = useToast();
    const confirmDialog = useConfirm();
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

    // E-Mail-Absender State
    const [absenderListe, setAbsenderListe] = useState<EmailAbsender[]>([]);
    const [showAbsenderModal, setShowAbsenderModal] = useState(false);
    const [editingAbsender, setEditingAbsender] = useState<Partial<EmailAbsender> | null>(null);

    // Gewerke (fuer Firma-Auswahl)
    const [gewerke, setGewerke] = useState<GewerkOption[]>([]);

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

    // Load Gewerke (fuer Auswahl im Firma-Tab)
    const loadGewerke = useCallback(async () => {
        try {
            const res = await fetch('/api/lohn-stammdaten/gewerke?nurAktive=true');
            if (res.ok) {
                setGewerke(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Laden der Gewerke', e);
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

    // Load E-Mail-Absender
    const loadAbsender = useCallback(async () => {
        try {
            const res = await fetch('/api/firma/email-absender');
            if (res.ok) {
                setAbsenderListe(await res.json());
            }
        } catch (e) {
            console.error('Fehler beim Laden der Absender', e);
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
    }, [activeTab, sbSubTab, loadLohnabrechnungen, loadBwaListe]);

    useEffect(() => {
        setLoading(true);
        Promise.all([loadFirma(), loadKostenstellen(), loadSteuerberater(), loadAbsender(), loadMeta(), loadGewerke()])
            .finally(() => setLoading(false));
    }, [loadFirma, loadKostenstellen, loadSteuerberater, loadAbsender, loadMeta, loadGewerke]);

    // Logo-Upload-State: Cache-Buster, damit das <img> nach Upload/Löschen neu lädt
    const [logoVersion, setLogoVersion] = useState(0);
    const [logoUploading, setLogoUploading] = useState(false);
    const logoSrc = useMemo(
        () => (firma?.logoDateiname ? `/api/firma/logo?v=${logoVersion}` : null),
        [firma?.logoDateiname, logoVersion]
    );

    const uploadLogo = async (file: File) => {
        const erlaubt = ['image/png', 'image/jpeg', 'image/webp'];
        if (!erlaubt.includes(file.type)) {
            toast.error('Ungültiger Dateityp – erlaubt sind PNG, JPEG und WebP.');
            return;
        }
        if (file.size > 2 * 1024 * 1024) {
            toast.error('Datei zu groß – maximal 2 MB.');
            return;
        }
        setLogoUploading(true);
        try {
            const form = new FormData();
            form.append('datei', file);
            const res = await fetch('/api/firma/logo', { method: 'POST', body: form });
            if (res.ok) {
                const aktualisiert = await res.json();
                setFirma(prev => (prev ? { ...prev, logoDateiname: aktualisiert.logoDateiname } : prev));
                setLogoVersion(v => v + 1);
                toast.success('Firmenlogo gespeichert.');
            } else {
                const text = await res.text();
                try {
                    const json = JSON.parse(text);
                    toast.error(json.message || 'Upload fehlgeschlagen.');
                } catch {
                    toast.error('Upload fehlgeschlagen.');
                }
            }
        } catch (e) {
            console.error('Fehler beim Logo-Upload', e);
            toast.error('Netzwerkfehler beim Hochladen.');
        } finally {
            setLogoUploading(false);
        }
    };

    const deleteLogo = async () => {
        if (!await confirmDialog({
            title: 'Firmenlogo löschen',
            message: 'Das hinterlegte Firmenlogo wirklich entfernen?',
            variant: 'danger',
            confirmLabel: 'Löschen'
        })) return;
        try {
            const res = await fetch('/api/firma/logo', { method: 'DELETE' });
            if (res.ok) {
                setFirma(prev => (prev ? { ...prev, logoDateiname: '' } : prev));
                setLogoVersion(v => v + 1);
                toast.success('Firmenlogo entfernt.');
            } else {
                toast.error('Löschen fehlgeschlagen.');
            }
        } catch (e) {
            console.error('Fehler beim Löschen des Logos', e);
            toast.error('Netzwerkfehler beim Löschen.');
        }
    };

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

    // Save E-Mail-Absender
    const saveAbsender = async () => {
        if (!editingAbsender || !editingAbsender.emailAdresse?.trim()) return;
        setSaving(true);
        try {
            const method = editingAbsender.id ? 'PUT' : 'POST';
            const url = editingAbsender.id
                ? `/api/firma/email-absender/${editingAbsender.id}`
                : '/api/firma/email-absender';

            const payload = {
                ...editingAbsender,
                emailAdresse: editingAbsender.emailAdresse.trim(),
                anzeigename: editingAbsender.anzeigename?.trim() || null,
                aktiv: editingAbsender.aktiv ?? true,
                sortierung: editingAbsender.sortierung ?? absenderListe.length,
            };

            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (res.ok) {
                await loadAbsender();
                setShowAbsenderModal(false);
                setEditingAbsender(null);
            } else {
                const text = await res.text();
                try {
                    const json = JSON.parse(text);
                    toast.error(json.message || 'Fehler beim Speichern');
                } catch {
                    toast.error('Fehler beim Speichern: ' + text);
                }
            }
        } catch (e) {
            console.error('Fehler beim Speichern des Absenders', e);
            toast.error('Netzwerkfehler beim Speichern');
        } finally {
            setSaving(false);
        }
    };

    // Delete E-Mail-Absender
    const deleteAbsender = async (id: number) => {
        if (!await confirmDialog({ title: 'Absender löschen', message: 'Diese E-Mail-Adresse wirklich aus der Liste entfernen? Benutzer ohne andere Zuweisung müssen anschließend neu zugewiesen werden.', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`/api/firma/email-absender/${id}`, { method: 'DELETE' });
            if (!res.ok) {
                toast.error('Absender konnte nicht gelöscht werden.');
                return;
            }
            await loadAbsender();
        } catch (e) {
            console.error('Fehler beim Löschen', e);
            toast.error('Netzwerkfehler beim Löschen');
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
                            onClick={() => setActiveTab('lohn-stammdaten')}
                            className={cn(
                                "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                activeTab === 'lohn-stammdaten'
                                    ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                    : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            )}
                        >
                            <HeartPulse className="w-4 h-4 inline-block mr-2" />
                            Lohn-Stammdaten
                        </button>
                        <button
                            onClick={() => setActiveTab('unfallversicherung')}
                            className={cn(
                                "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                activeTab === 'unfallversicherung'
                                    ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                    : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            )}
                        >
                            <ShieldCheck className="w-4 h-4 inline-block mr-2" />
                            Unfallversicherung
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
                        <button
                            onClick={() => setActiveTab('absender')}
                            className={cn(
                                "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                activeTab === 'absender'
                                    ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                    : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            )}
                        >
                            <AtSign className="w-4 h-4 inline-block mr-2" />
                            E-Mail-Absender ({absenderListe.length})
                        </button>
                        <button
                            onClick={() => setActiveTab('steuerpruefung')}
                            className={cn(
                                "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                                activeTab === 'steuerpruefung'
                                    ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                                    : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            )}
                        >
                            <ShieldCheck className="w-4 h-4 inline-block mr-2" />
                            Steuerprüfung
                        </button>
                    </div>

                    {/* Tab Content */}
                    {activeTab === 'firma' && firma && (
                        <Card className="p-6">
                            {/* Firmenlogo */}
                            <div className="mb-8 pb-6 border-b">
                                <h3 className="text-lg font-semibold text-slate-900 mb-4">Firmenlogo</h3>
                                <div className="flex items-start gap-6">
                                    <div className="w-40 h-40 rounded-lg border-2 border-dashed border-slate-200 bg-slate-50 flex items-center justify-center overflow-hidden">
                                        {logoSrc ? (
                                            <img
                                                src={logoSrc}
                                                alt="Firmenlogo"
                                                className="max-w-full max-h-full object-contain"
                                            />
                                        ) : (
                                            <span className="text-xs text-slate-400 text-center px-4">
                                                Kein Logo hinterlegt
                                            </span>
                                        )}
                                    </div>
                                    <div className="flex-1 space-y-3">
                                        <p className="text-sm text-slate-600">
                                            Das hier hochgeladene Logo erscheint auf allen generierten PDFs
                                            (Angebote, Rechnungen, Bestellungen, Preisanfragen). Formate:
                                            PNG, JPEG oder WebP, maximal 2&nbsp;MB.
                                        </p>
                                        <div className="flex items-center gap-2">
                                            <label className={cn(
                                                "inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg cursor-pointer transition",
                                                logoUploading
                                                    ? "bg-slate-200 text-slate-500 cursor-not-allowed"
                                                    : "bg-rose-600 text-white hover:bg-rose-700"
                                            )}>
                                                {logoUploading ? (
                                                    <RefreshCw className="w-4 h-4 animate-spin" />
                                                ) : (
                                                    <Plus className="w-4 h-4" />
                                                )}
                                                {firma.logoDateiname ? 'Logo ersetzen' : 'Logo hochladen'}
                                                <input
                                                    type="file"
                                                    accept="image/png,image/jpeg,image/webp"
                                                    className="hidden"
                                                    disabled={logoUploading}
                                                    onChange={e => {
                                                        const file = e.target.files?.[0];
                                                        if (file) void uploadLogo(file);
                                                        e.target.value = '';
                                                    }}
                                                />
                                            </label>
                                            {firma.logoDateiname && (
                                                <Button
                                                    type="button"
                                                    variant="outline"
                                                    size="sm"
                                                    onClick={deleteLogo}
                                                    disabled={logoUploading}
                                                >
                                                    <Trash2 className="w-4 h-4 mr-1" />
                                                    Entfernen
                                                </Button>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>

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
                                    <div>
                                        <Label>Bewertungs-Link (Google)</Label>
                                        <Input
                                            type="url"
                                            value={firma.googleBewertungsLink || ''}
                                            onChange={e => setFirma({ ...firma, googleBewertungsLink: e.target.value })}
                                            placeholder="https://www.google.com/search?q=..."
                                        />
                                        <p className="text-xs text-slate-500 mt-1">
                                            Wird in E-Mails über den Platzhalter <code className="bg-slate-100 px-1 rounded">{'{{REVIEW_LINK}}'}</code> als Link „Jetzt Bewertung abgeben" eingefügt.
                                        </p>
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

                            {/* Mahnverfahren — Volle Breite, eigener Block */}
                            <div className="mt-6 pt-6 border-t space-y-4">
                                <div className="flex items-center justify-between">
                                    <h3 className="text-lg font-semibold text-slate-900">Automatisches Mahnverfahren</h3>
                                    <label className="inline-flex items-center gap-2 text-sm font-medium text-slate-700 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={firma.mahnverfahrenAktiv || false}
                                            onChange={e => setFirma({ ...firma, mahnverfahrenAktiv: e.target.checked })}
                                            className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                        />
                                        Aktiv
                                    </label>
                                </div>
                                <p className="text-sm text-slate-500">
                                    Wenn aktiviert, verschickt das System automatisch Zahlungserinnerung sowie 1. und 2. Mahnung
                                    per E-Mail an den Kunden. Jeder Schritt wird erst verschickt, wenn seit dem vorherigen
                                    Schritt die eingestellte Anzahl an Tagen vergangen ist.
                                </p>
                                <div className={cn("space-y-6 transition-opacity", !firma.mahnverfahrenAktiv && "opacity-50 pointer-events-none")}>
                                    {/* Zeitstrahl: Rechnung fällig -> Zahlungserinnerung -> 1. Mahnung -> 2. Mahnung */}
                                    <div className="flex flex-col pt-2 md:flex-row md:items-start">
                                        <ZeitstrahlStation icon={CalendarClock} label="Rechnung fällig" />
                                        <ZeitstrahlAbstand
                                            value={firma.tageBisZahlungserinnerung || MAHN_DEFAULT_TAGE}
                                            onChange={tage => setFirma({ ...firma, tageBisZahlungserinnerung: tage })}
                                            beschriftung="Tage nach Fälligkeit"
                                        />
                                        <ZeitstrahlStation icon={BellRing} label="Zahlungserinnerung" />
                                        <ZeitstrahlAbstand
                                            value={firma.tageBisErsteMahnung || MAHN_DEFAULT_TAGE}
                                            onChange={tage => setFirma({ ...firma, tageBisErsteMahnung: tage })}
                                            beschriftung="Tage nach der Zahlungserinnerung"
                                        />
                                        <ZeitstrahlStation icon={Mail} label="1. Mahnung" />
                                        <ZeitstrahlAbstand
                                            value={firma.tageBisZweiteMahnung || MAHN_DEFAULT_TAGE}
                                            onChange={tage => setFirma({ ...firma, tageBisZweiteMahnung: tage })}
                                            beschriftung="Tage nach der 1. Mahnung"
                                        />
                                        <ZeitstrahlStation icon={MailWarning} label="2. Mahnung" />
                                    </div>

                                    {/* Neues Zahlungsziel auf der Mahnung */}
                                    <div>
                                        <Label>Neues Zahlungsziel auf der Mahnung (Tage)</Label>
                                        <Input
                                            type="number"
                                            min={1}
                                            value={firma.mahnverfahrenNeuesZahlungszielTage || MAHN_DEFAULT_TAGE}
                                            onChange={e => setFirma({ ...firma, mahnverfahrenNeuesZahlungszielTage: parseInt(e.target.value) || MAHN_DEFAULT_TAGE })}
                                            className="w-24"
                                        />
                                        <p className="mt-1 text-xs text-slate-500">
                                            So viele Tage Zeit geben wir dem Kunden auf jeder Mahnung, den Betrag noch zu überweisen.
                                        </p>
                                    </div>
                                </div>

                                {/* Hinweis zur Funktionsweise */}
                                <div className="flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
                                    <Info className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
                                    <p className="text-sm text-slate-600">
                                        Schon überfällige Rechnungen starten mit der Zahlungserinnerung — danach gelten die
                                        eingestellten Abstände. Automatisch gemahnt werden nur Rechnungen, die mit dem Programm
                                        geschrieben wurden.
                                    </p>
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

                    {activeTab === 'absender' && (
                        <div className="space-y-4">
                            <div className="flex justify-between items-center">
                                <p className="text-slate-500 text-sm">
                                    Hier hinterlegen Sie alle E-Mail-Adressen, von denen das System aus E-Mails verschickt.
                                    Jeder Benutzer bekommt unter „Benutzer" eine dieser Adressen zugewiesen –
                                    die wird dann automatisch beim Versand als Absender verwendet.
                                </p>
                                <Button
                                    onClick={() => {
                                        setEditingAbsender({ aktiv: true, sortierung: absenderListe.length });
                                        setShowAbsenderModal(true);
                                    }}
                                    className="bg-rose-600 text-white hover:bg-rose-700"
                                >
                                    <Plus className="w-4 h-4 mr-2" />
                                    Neue Absender-Adresse
                                </Button>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                {absenderListe.map(a => (
                                    <Card key={a.id} className="p-4">
                                        <div className="flex justify-between items-start gap-2">
                                            <div className="min-w-0">
                                                <h4 className="font-semibold text-slate-900 truncate">{a.emailAdresse}</h4>
                                                {a.anzeigename && (
                                                    <p className="text-sm text-slate-500 truncate">{a.anzeigename}</p>
                                                )}
                                                <div className="flex gap-2 mt-2">
                                                    {a.aktiv ? (
                                                        <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded">Aktiv</span>
                                                    ) : (
                                                        <span className="text-xs bg-slate-100 text-slate-700 px-2 py-0.5 rounded">Deaktiviert</span>
                                                    )}
                                                </div>
                                            </div>
                                            <div className="flex gap-1 shrink-0">
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => {
                                                        setEditingAbsender(a);
                                                        setShowAbsenderModal(true);
                                                    }}
                                                >
                                                    <Edit2 className="w-4 h-4" />
                                                </Button>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => deleteAbsender(a.id)}
                                                    className="text-red-600 hover:text-red-700"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </Button>
                                            </div>
                                        </div>
                                    </Card>
                                ))}
                                {absenderListe.length === 0 && (
                                    <div className="col-span-full text-center py-8 text-slate-400">
                                        Noch keine Absender-Adresse angelegt
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                    {activeTab === 'steuerpruefung' && (
                        <SteuerpruefungExport />
                    )}

                    {activeTab === 'lohn-stammdaten' && (
                        <Card className="p-6">
                            <LohnStammdatenPanel />
                        </Card>
                    )}

                    {activeTab === 'unfallversicherung' && firma && (
                        <Card className="p-6 space-y-4">
                            <div>
                                <h3 className="text-lg font-semibold text-slate-900">Gewerk &amp; Unfallversicherung</h3>
                                <p className="text-sm text-slate-500 mt-1">
                                    Wähle dein Gewerk – wir schlagen dir die passende Berufsgenossenschaft (BG) und einen
                                    Standard-Beitragssatz vor. Wenn du den genauen Satz aus deinem Beitragsbescheid kennst,
                                    trägst du ihn unten ein.
                                </p>
                            </div>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div>
                                    <Label>Gewerk</Label>
                                    <Select
                                        value={firma.gewerkId ? String(firma.gewerkId) : ''}
                                        onChange={(v: string) => {
                                            const id = v ? Number(v) : null;
                                            const g = gewerke.find(x => x.id === id);
                                            setFirma({
                                                ...firma,
                                                gewerkId: id,
                                                gewerkName: g?.name ?? null,
                                                bgName: g?.bgName ?? null,
                                                bgSatzVorschlag: g?.bgSatzProzent ?? null,
                                            });
                                        }}
                                        options={[
                                            { value: '', label: '— Bitte wählen —' },
                                            ...gewerke.map(g => ({ value: String(g.id), label: `${g.name} (${g.bgName})` })),
                                        ]}
                                    />
                                </div>
                                <div>
                                    <Label>Berufsgenossenschaft</Label>
                                    <Input value={firma.bgName ?? ''} disabled placeholder="— wird durch Gewerk gesetzt —" />
                                </div>
                                <div>
                                    <Label>BG-Satz (Vorschlag)</Label>
                                    <Input
                                        value={firma.bgSatzVorschlag != null ? `${Number(firma.bgSatzVorschlag).toFixed(2)} %` : ''}
                                        disabled
                                        placeholder="—"
                                    />
                                </div>
                                <div>
                                    <Label>Tatsächlicher BG-Satz (aus Bescheid)</Label>
                                    <Input
                                        type="number"
                                        step="0.01"
                                        min={0}
                                        value={firma.bgSatzOverride ?? ''}
                                        onChange={e => setFirma({ ...firma, bgSatzOverride: e.target.value === '' ? null : parseFloat(e.target.value) })}
                                        placeholder={firma.bgSatzVorschlag != null ? `Standard ${Number(firma.bgSatzVorschlag).toFixed(2)} %` : 'optional'}
                                    />
                                    <p className="text-xs text-slate-500 mt-1">Leer lassen, um den Standard-Satz zu nutzen.</p>
                                </div>
                            </div>
                            <div className="pt-4 border-t flex justify-end">
                                <Button
                                    onClick={saveFirma}
                                    disabled={saving}
                                    className="bg-rose-600 text-white hover:bg-rose-700"
                                >
                                    <Save className="w-4 h-4 mr-2" />
                                    {saving ? 'Speichert...' : 'Speichern'}
                                </Button>
                            </div>
                        </Card>
                    )}
                </>
            )}

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

            {/* E-Mail-Absender Modal */}
            <Dialog open={showAbsenderModal} onOpenChange={setShowAbsenderModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>
                            {editingAbsender?.id ? 'Absender bearbeiten' : 'Neue Absender-Adresse'}
                        </DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <div>
                            <Label>E-Mail-Adresse *</Label>
                            <Input
                                type="email"
                                value={editingAbsender?.emailAdresse || ''}
                                onChange={e => setEditingAbsender({ ...editingAbsender, emailAdresse: e.target.value })}
                                placeholder="info@meinefirma.de"
                            />
                        </div>
                        <div>
                            <Label>Anzeigename (optional)</Label>
                            <Input
                                value={editingAbsender?.anzeigename || ''}
                                onChange={e => setEditingAbsender({ ...editingAbsender, anzeigename: e.target.value })}
                                placeholder="z. B. Buchhaltung"
                            />
                            <p className="text-xs text-slate-500 mt-1">
                                Reine Beschriftung in der Verwaltung – wird nicht im E-Mail-Header verwendet.
                            </p>
                        </div>
                        <label className="inline-flex items-center gap-2 text-sm font-medium text-slate-700 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={editingAbsender?.aktiv ?? true}
                                onChange={e => setEditingAbsender({ ...editingAbsender, aktiv: e.target.checked })}
                                className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                            />
                            Aktiv (steht für Versand zur Verfügung)
                        </label>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowAbsenderModal(false)}>
                            <X className="w-4 h-4 mr-2" />
                            Abbrechen
                        </Button>
                        <Button
                            onClick={saveAbsender}
                            disabled={saving || !editingAbsender?.emailAdresse?.trim()}
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
                    <div className="grid gap-4 py-4 overflow-y-auto pr-1 flex-1 min-h-0">
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label>Name</Label>
                                <Input 
                                    value={editingSteuerberater?.name || ''} 
                                    onChange={e => setEditingSteuerberater(prev => ({ ...prev, name: e.target.value }))}
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
                            <Label>Haupt-E-Mail (für BWA-Erkennung)</Label>
                            <Input
                                value={editingSteuerberater?.email || ''}
                                onChange={e => setEditingSteuerberater(prev => ({ ...prev, email: e.target.value }))}
                                placeholder="kanzlei@beispiel.de"
                            />
                        </div>
                        {/* Weitere E-Mails als Liste mit + und × */}
                        <div className="space-y-2">
                            <div className="flex items-center justify-between">
                                <Label>Weitere E-Mail-Adressen</Label>
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setEditingSteuerberater(prev => ({
                                        ...prev,
                                        weitereEmails: [...(prev?.weitereEmails || []), '']
                                    }))}
                                >
                                    <Plus className="w-3 h-3 mr-1" />E-Mail hinzufügen
                                </Button>
                            </div>
                            {(editingSteuerberater?.weitereEmails || []).length === 0 && (
                                <p className="text-xs text-slate-500">Keine weiteren Adressen.</p>
                            )}
                            {(editingSteuerberater?.weitereEmails || []).map((mail, idx) => (
                                <div key={idx} className="flex items-center gap-2">
                                    <Input
                                        value={mail}
                                        onChange={e => setEditingSteuerberater(prev => {
                                            const next = [...(prev?.weitereEmails || [])];
                                            next[idx] = e.target.value;
                                            return { ...prev, weitereEmails: next };
                                        })}
                                        placeholder="weitere@kanzlei.de"
                                    />
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => setEditingSteuerberater(prev => ({
                                            ...prev,
                                            weitereEmails: (prev?.weitereEmails || []).filter((_, i) => i !== idx)
                                        }))}
                                        aria-label="E-Mail entfernen"
                                    >
                                        <X className="w-4 h-4" />
                                    </Button>
                                </div>
                            ))}
                        </div>
                        {/* Ansprechpartner-Liste */}
                        <div className="space-y-2 pt-2 border-t border-slate-200">
                            <div className="flex items-center justify-between">
                                <Label>Ansprechpartner</Label>
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setEditingSteuerberater(prev => {
                                        const liste = prev?.ansprechpartnerListe || [];
                                        const istErster = liste.length === 0;
                                        return {
                                            ...prev,
                                            ansprechpartnerListe: [...liste, {
                                                anrede: 'HERR',
                                                vorname: '',
                                                nachname: '',
                                                email: '',
                                                telefon: '',
                                                istLohnAnsprechpartner: istErster,
                                            }]
                                        };
                                    })}
                                >
                                    <Plus className="w-3 h-3 mr-1" />Ansprechpartner hinzufügen
                                </Button>
                            </div>
                            {(editingSteuerberater?.ansprechpartnerListe || []).length === 0 && (
                                <p className="text-xs text-slate-500">Noch kein Ansprechpartner. Mindestens einer wird empfohlen, damit die Stundenaufstellung an die richtige Person geht.</p>
                            )}
                            {(editingSteuerberater?.ansprechpartnerListe || []).map((ap, idx) => (
                                <div key={idx} className="rounded-lg border border-slate-200 p-3 space-y-2 bg-slate-50">
                                    <div className="flex items-center justify-between">
                                        <span className="text-xs font-medium text-slate-600">Ansprechpartner {idx + 1}</span>
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            onClick={() => setEditingSteuerberater(prev => ({
                                                ...prev,
                                                ansprechpartnerListe: (prev?.ansprechpartnerListe || []).filter((_, i) => i !== idx)
                                            }))}
                                            aria-label="Ansprechpartner entfernen"
                                        >
                                            <X className="w-4 h-4" />
                                        </Button>
                                    </div>
                                    <div className="grid grid-cols-3 gap-2">
                                        <div className="space-y-1">
                                            <Label className="text-xs">Anrede</Label>
                                            <Select
                                                options={ANREDE_OPTIONS}
                                                value={ap.anrede || ''}
                                                onChange={v => setEditingSteuerberater(prev => {
                                                    const next = [...(prev?.ansprechpartnerListe || [])];
                                                    next[idx] = { ...next[idx], anrede: v || null };
                                                    return { ...prev, ansprechpartnerListe: next };
                                                })}
                                                placeholder="Anrede"
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <Label className="text-xs">Vorname</Label>
                                            <Input
                                                value={ap.vorname || ''}
                                                onChange={e => setEditingSteuerberater(prev => {
                                                    const next = [...(prev?.ansprechpartnerListe || [])];
                                                    next[idx] = { ...next[idx], vorname: e.target.value };
                                                    return { ...prev, ansprechpartnerListe: next };
                                                })}
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <Label className="text-xs">Nachname *</Label>
                                            <Input
                                                value={ap.nachname || ''}
                                                onChange={e => setEditingSteuerberater(prev => {
                                                    const next = [...(prev?.ansprechpartnerListe || [])];
                                                    next[idx] = { ...next[idx], nachname: e.target.value };
                                                    return { ...prev, ansprechpartnerListe: next };
                                                })}
                                            />
                                        </div>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2">
                                        <div className="space-y-1">
                                            <Label className="text-xs">E-Mail</Label>
                                            <Input
                                                value={ap.email || ''}
                                                onChange={e => setEditingSteuerberater(prev => {
                                                    const next = [...(prev?.ansprechpartnerListe || [])];
                                                    next[idx] = { ...next[idx], email: e.target.value };
                                                    return { ...prev, ansprechpartnerListe: next };
                                                })}
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <Label className="text-xs">Telefon</Label>
                                            <Input
                                                value={ap.telefon || ''}
                                                onChange={e => setEditingSteuerberater(prev => {
                                                    const next = [...(prev?.ansprechpartnerListe || [])];
                                                    next[idx] = { ...next[idx], telefon: e.target.value };
                                                    return { ...prev, ansprechpartnerListe: next };
                                                })}
                                            />
                                        </div>
                                    </div>
                                    <label className="flex items-center gap-2 text-sm cursor-pointer pt-1">
                                        <input
                                            type="radio"
                                            name="lohnAnsprechpartner"
                                            checked={!!ap.istLohnAnsprechpartner}
                                            onChange={() => setEditingSteuerberater(prev => {
                                                const next = (prev?.ansprechpartnerListe || []).map((a, i) => ({
                                                    ...a,
                                                    istLohnAnsprechpartner: i === idx,
                                                }));
                                                return { ...prev, ansprechpartnerListe: next };
                                            })}
                                            className="accent-rose-600"
                                        />
                                        <span className="text-slate-700">Zuständig für Löhne (Empfänger der Stundenaufstellung)</span>
                                    </label>
                                </div>
                            ))}
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
        </PageLayout>
    );
}
