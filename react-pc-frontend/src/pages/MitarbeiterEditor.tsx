import React, { useState, useEffect } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import DocumentPreviewModal from '../components/DocumentPreviewModal';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { DatePicker } from '../components/ui/datepicker';
import { DetailLayout } from '../components/DetailLayout';
import {
    Plus, User, Trash2, ArrowLeft,
    FileText, Upload, Calendar, Euro, File, Building2, QrCode, RefreshCw, Download, Loader2, Eye, Phone, GraduationCap, Home, StickyNote, Receipt
} from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "../components/ui/dialog";
import { Select } from '../components/ui/select-custom';
import { ImageViewer } from '../components/ui/image-viewer';
import { useConfirm } from '../components/ui/confirm-dialog';
import { StundenlohnHistorieList } from '../components/mitarbeiter/StundenlohnHistorieList';
import { BeschaeftigungsWizard, type Beschaeftigungsart } from '../components/mitarbeiter/BeschaeftigungsWizard';

// Interfaces
interface Abteilung {
    id: number;
    name: string;
}

interface Mitarbeiter {
    id: number;
    vorname: string;
    nachname: string;
    strasse: string | null;
    plz: string | null;
    ort: string | null;
    email: string | null;
    telefon: string | null;
    festnetz: string | null;
    qualifikation: string | null;
    stundenlohn: number | null;
    geburtstag: string | null;
    eintrittsdatum: string | null;
    aktiv: boolean;
    abteilungIds: number[] | null;  // N:M - Multiple abteilungen
    abteilungNames: string | null;  // Komma-separiert
    loginToken: string | null;
    jahresUrlaub: number | null;
    beschaeftigungsart?: Beschaeftigungsart | null;
    beschaeftigungsartLabel?: string | null;
    krankenkasseId?: number | null;
    krankenkasseName?: string | null;
    kinderlos?: boolean | null;
    istGeschaeftsfuehrer?: boolean | null;
    kalkulatorischerLohnMonat?: number | null;
    geldwertVorteilMonat?: number | null;
}

const QUALIFIKATIONEN = [
    { value: '', label: 'Keine Qualifikation' },
    { value: 'Auszubildender', label: 'Auszubildender' },
    { value: 'Facharbeiter', label: 'Facharbeiter' },
    { value: 'Meister', label: 'Meister' }
];

interface MitarbeiterDokument {
    id: number;
    originalDateiname: string;
    dateityp: string;
    dateigroesse: number;
    uploadDatum: string;
    dokumentGruppe: string;
    url?: string;
}

interface MitarbeiterNotiz {
    id: number;
    inhalt: string;
    erstelltAm: string;
    mitarbeiterId: number;
}

interface Lohnabrechnung {
    id: number;
    mitarbeiterId: number;
    mitarbeiterName: string;
    steuerberaterId: number | null;
    steuerberaterName: string | null;
    jahr: number;
    monat: number;
    originalDateiname: string;
    downloadUrl: string;
    bruttolohn: number | null;
    nettolohn: number | null;
    importDatum: string;
    status: string;
}

const BASE_API = '/api/mitarbeiter';

export default function MitarbeiterEditor() {
    const confirmDialog = useConfirm();
    const [view, setView] = useState<'LIST' | 'DETAIL'>('LIST');
    const [mitarbeiter, setMitarbeiter] = useState<Mitarbeiter[]>([]);
    const [selectedMitarbeiter, setSelectedMitarbeiter] = useState<Mitarbeiter | null>(null);
    const [dokumente, setDokumente] = useState<MitarbeiterDokument[]>([]);
    const [abteilungen, setAbteilungen] = useState<Abteilung[]>([]);

    // Form States
    const [isDialogOpen, setIsDialogOpen] = useState(false);
    const [formData, setFormData] = useState<Partial<Mitarbeiter>>({});

    // QR-Code States
    const [isQrModalOpen, setIsQrModalOpen] = useState(false);
    const [regenerating, setRegenerating] = useState(false);

    // Dokument Preview States
    const [previewDoc, setPreviewDoc] = useState<MitarbeiterDokument | null>(null);

    // Notizen States
    const [activeTab, setActiveTab] = useState<'dokumente' | 'notizen' | 'lohnabrechnungen' | 'stundenlohn'>('dokumente');
    const [notizen, setNotizen] = useState<MitarbeiterNotiz[]>([]);
    const [showNotizModal, setShowNotizModal] = useState(false);
    const [neueNotiz, setNeueNotiz] = useState('');

    // Lohnabrechnungen States
    const [lohnabrechnungen, setLohnabrechnungen] = useState<Lohnabrechnung[]>([]);
    const [loadingLohnabrechnungen, setLoadingLohnabrechnungen] = useState(false);
    const [previewLohnabrechnung, setPreviewLohnabrechnung] = useState<Lohnabrechnung | null>(null);
    const [lohnFilterJahr, setLohnFilterJahr] = useState('alle');
    const [lohnFilterMonat, setLohnFilterMonat] = useState('alle');

    useEffect(() => {
        loadMitarbeiter();
        loadAbteilungen();
    }, []);

    const loadMitarbeiter = async () => {
        try {
            const res = await fetch(BASE_API);
            if (res.ok) {
                const data = await res.json();
                setMitarbeiter(data);
            }
        } catch (error) {
            console.error("Error loading employees", error);
        }
    };

    const loadAbteilungen = async () => {
        try {
            const res = await fetch('/api/abteilungen');
            if (res.ok) {
                const data = await res.json();
                setAbteilungen(data);
            }
        } catch (error) {
            console.error("Error loading departments", error);
        }
    };

    const loadDokumente = async (id: number) => {
        try {
            const res = await fetch(`${BASE_API}/${id}/dokumente`);
            if (res.ok) {
                const data = await res.json();
                setDokumente(data);
            }
        } catch (error) {
            console.error("Error loading documents", error);
        }
    };

    const loadNotizen = async (id: number) => {
        try {
            const res = await fetch(`${BASE_API}/${id}/notizen`);
            if (res.ok) {
                const data = await res.json();
                setNotizen(data);
            }
        } catch (error) {
            console.error("Error loading notes", error);
        }
    };

    const loadLohnabrechnungen = async (id: number) => {
        setLoadingLohnabrechnungen(true);
        try {
            const res = await fetch(`/api/lohnabrechnungen/mitarbeiter/${id}`);
            if (res.ok) {
                const data = await res.json();
                setLohnabrechnungen(data);
            }
        } catch (error) {
            console.error("Error loading payrolls", error);
        } finally {
            setLoadingLohnabrechnungen(false);
        }
    };

    // Load lohnabrechnungen when tab is activated
    useEffect(() => {
        if (activeTab === 'lohnabrechnungen' && selectedMitarbeiter) {
            loadLohnabrechnungen(selectedMitarbeiter.id);
        }
    }, [activeTab, selectedMitarbeiter]);

    const handleCreateNotiz = async () => {
        if (!selectedMitarbeiter || !neueNotiz.trim()) return;
        try {
            const res = await fetch(`${BASE_API}/${selectedMitarbeiter.id}/notizen`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: neueNotiz.trim() // Backend expects plain string body based on controller
            });
            if (res.ok) {
                loadNotizen(selectedMitarbeiter.id);
                setNeueNotiz('');
                setShowNotizModal(false);
            }
        } catch (error) {
            console.error("Error creating note", error);
        }
    };

    const handleDeleteNotiz = async (notizId: number) => {
        if (!await confirmDialog({ title: 'Notiz löschen', message: 'Notiz wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`${BASE_API}/notizen/${notizId}`, { method: 'DELETE' });
            if (res.ok && selectedMitarbeiter) {
                loadNotizen(selectedMitarbeiter.id);
            }
        } catch (error) {
            console.error("Error deleting note", error);
        }
    };

    const handleSave = async () => {
        try {
            const method = formData.id ? 'PUT' : 'POST';
            const url = formData.id ? `${BASE_API}/${formData.id}` : BASE_API;

            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(formData)
            });

            if (res.ok) {
                const saved = await res.json();
                if (view === 'DETAIL' && selectedMitarbeiter?.id === saved.id) {
                    setSelectedMitarbeiter(saved);
                }
                loadMitarbeiter();
                setIsDialogOpen(false);
                setFormData({});
            }
        } catch (error) {
            console.error("Error saving employee", error);
        }
    };

    const handleDelete = async (id: number) => {
        if (!await confirmDialog({ title: 'Mitarbeiter löschen', message: 'Mitarbeiter wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`${BASE_API}/${id}`, { method: 'DELETE' });
            if (res.ok) {
                loadMitarbeiter();
                if (selectedMitarbeiter?.id === id) {
                    setView('LIST');
                    setSelectedMitarbeiter(null);
                }
            }
        } catch (error) {
            console.error("Error deleting employee", error);
        }
    };

    const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        if (!selectedMitarbeiter || !e.target.files?.length) return;

        const file = e.target.files[0];
        const formData = new FormData();
        formData.append('datei', file);
        formData.append('gruppe', 'DIVERSE_DOKUMENTE'); // Default group

        try {
            const res = await fetch(`${BASE_API}/${selectedMitarbeiter.id}/dokumente`, {
                method: 'POST',
                body: formData
            });
            if (res.ok) {
                loadDokumente(selectedMitarbeiter.id);
            }
        } catch (error) {
            console.error("Error uploading file", error);
        }
    };

    // QR-Code Functions
    const handleRegenerateToken = async () => {
        if (!selectedMitarbeiter) return;
        setRegenerating(true);
        try {
            const res = await fetch(`${BASE_API}/${selectedMitarbeiter.id}/regenerate-token`, {
                method: 'POST'
            });
            if (res.ok) {
                const newToken = await res.text();
                setSelectedMitarbeiter({ ...selectedMitarbeiter, loginToken: newToken });
                // Update in list too
                setMitarbeiter(prev => prev.map(m =>
                    m.id === selectedMitarbeiter.id ? { ...m, loginToken: newToken } : m
                ));
            }
        } catch (error) {
            console.error("Error regenerating token", error);
        } finally {
            setRegenerating(false);
        }
    };

    const handleDownloadQr = () => {
        if (!selectedMitarbeiter) return;
        window.open(`${BASE_API}/${selectedMitarbeiter.id}/qr-code?width=600&height=600`, '_blank');
    };

    // Sub-components
    const DetailHeader = () => (
        <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
            <div>
                <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                    Stammdaten
                </p>
                <h1 className="text-3xl font-bold text-slate-900 uppercase">
                    {selectedMitarbeiter?.nachname}, {selectedMitarbeiter?.vorname}
                </h1>
                <p className="text-slate-500 mt-1">
                    {selectedMitarbeiter?.abteilungNames || 'Keine Abteilung zugewiesen'}
                </p>
            </div>
            <div className="flex gap-2">
                {selectedMitarbeiter?.loginToken && (
                    <Button
                        variant="outline"
                        onClick={() => setIsQrModalOpen(true)}
                        className="border-rose-200 text-rose-600 hover:bg-rose-50"
                    >
                        <QrCode className="w-4 h-4 mr-2" /> QR-Code
                    </Button>
                )}
                {!selectedMitarbeiter?.loginToken && (
                    <Button
                        variant="outline"
                        onClick={handleRegenerateToken}
                        disabled={regenerating}
                        className="border-rose-200 text-rose-600 hover:bg-rose-50"
                    >
                        {regenerating ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <QrCode className="w-4 h-4 mr-2" />}
                        Token erstellen
                    </Button>
                )}
                <Button variant="outline" onClick={() => setView('LIST')}>
                    <ArrowLeft className="w-4 h-4 mr-2" /> Zurück
                </Button>
                <Button
                    className="bg-rose-600 text-white hover:bg-rose-700"
                    onClick={() => {
                        setFormData(selectedMitarbeiter || {});
                        setIsDialogOpen(true);
                    }}
                >
                    <User className="w-4 h-4 mr-2" /> Bearbeiten
                </Button>
            </div>
        </div>
    );

    const DokumenteList = () => (
        <div className="space-y-4">
            <div className="flex justify-between items-center border-b pb-4">
                <h3 className="text-lg font-semibold flex items-center gap-2">
                    <FileText className="w-5 h-5 text-rose-600" />
                    Dokumente
                </h3>
                <div className="relative">
                    <input
                        type="file"
                        id="file-upload"
                        className="hidden"
                        onChange={handleFileUpload}
                    />
                    <label
                        htmlFor="file-upload"
                        className="flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-md cursor-pointer transition-colors font-medium text-sm"
                    >
                        <Upload className="w-4 h-4" /> Datei hochladen
                    </label>
                </div>
            </div>

            {dokumente.length === 0 ? (
                <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                    <File className="w-12 h-12 mx-auto mb-3 text-slate-300" />
                    <p>Keine Dokumente vorhanden</p>
                </div>
            ) : (
                <div className="grid gap-2">
                    {dokumente.map((doc) => (
                        <div key={doc.id} className="flex items-center justify-between p-3 bg-white border border-slate-100 rounded-lg hover:border-rose-100 hover:shadow-sm transition-all group">
                            <div className="flex items-center gap-3">
                                <div className="p-2 bg-slate-100 rounded text-slate-500 group-hover:text-rose-600 group-hover:bg-rose-50 transition-colors">
                                    <File className="w-5 h-5" />
                                </div>
                                <div>
                                    <p className="font-medium text-slate-900">{doc.originalDateiname}</p>
                                    <p className="text-xs text-slate-500">
                                        {new Date(doc.uploadDatum).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })} • {(doc.dateigroesse / 1024).toFixed(0)} KB
                                    </p>
                                </div>
                            </div>
                            <div className="flex gap-2">
                                {doc.url && (
                                    <button
                                        onClick={() => setPreviewDoc(doc)}
                                        className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded transition-colors"
                                        title="Vorschau"
                                    >
                                        <Eye className="w-4 h-4" />
                                    </button>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );

    // Nachtrag Abschnitt 6 (Task 7b): jede Zeile hier ist Flex-Item einer
    // "flex items-center gap-3"-Reihe. Ohne min-w-0 behaelt das umschliessende
    // <div> min-width: auto und wird nie schmaler als sein laengstes
    // unteilbares Wort (E-Mail, Abteilungsname) -- die Reihe sprengt dann die
    // Karte, OBWOHL der Wert selbst gar nicht ueberzulaufen scheint
    // (scrollWidth == clientWidth des <p>, siehe kriterien.md "Layout: zwei
    // Fallen, die kein Test von selbst findet"). Gleiches Muster wie
    // LieferantenEditor.tsx Z. 315/324 und Kundeneditor.tsx Z. 497/509:
    // min-w-0 flex-1 am umschliessenden <div>, break-words am Wert. Auf ALLE
    // Zeilen der Spalte angewandt, nicht nur auf E-Mail (Befund von Design- und
    // Code-Reviewer aus Abschnitt 5: Abteilung war schon vorher betroffen,
    // Telefon/Festnetz/Adresse tragen dieselbe latente Luecke).
    const SideInfo = () => (
        <div className="space-y-6">
            <div>
                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Persönliche Daten</h3>
                <div className="space-y-3">
                    <div className="flex items-center gap-3">
                        <User className="w-4 h-4 text-slate-400 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <p className="text-xs text-slate-500">Voller Name</p>
                            <p className="text-sm font-medium break-words">{selectedMitarbeiter?.vorname} {selectedMitarbeiter?.nachname}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Calendar className="w-4 h-4 text-slate-400 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <p className="text-xs text-slate-500">Geburtsdatum</p>
                            <p className="text-sm font-medium break-words">
                                {selectedMitarbeiter?.geburtstag ? new Date(selectedMitarbeiter.geburtstag).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '-'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Building2 className="w-4 h-4 text-slate-400 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <p className="text-xs text-slate-500">Abteilung(en)</p>
                            <p className="text-sm font-medium break-words">{selectedMitarbeiter?.abteilungNames || '-'}</p>
                        </div>
                    </div>
                </div>
            </div>

            <div className="pt-6 border-t border-slate-100">
                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Kontakt</h3>
                <div className="space-y-3">
                    <div className="flex items-center gap-3">
                        <User className="w-4 h-4 text-slate-400 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <p className="text-xs text-slate-500">E-Mail</p>
                            <p className="text-sm font-medium break-words">{selectedMitarbeiter?.email || '-'}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Phone className="w-4 h-4 text-slate-400 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <p className="text-xs text-slate-500">Mobiltelefon</p>
                            <p className="text-sm font-medium break-words">{selectedMitarbeiter?.telefon || '-'}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Phone className="w-4 h-4 text-slate-400 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <p className="text-xs text-slate-500">Festnetz</p>
                            <p className="text-sm font-medium break-words">{selectedMitarbeiter?.festnetz || '-'}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <User className="w-4 h-4 text-slate-400 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <p className="text-xs text-slate-500">Adresse</p>
                            <p className="text-sm font-medium break-words">
                                {selectedMitarbeiter?.strasse || ''}<br />
                                {selectedMitarbeiter?.plz || ''} {selectedMitarbeiter?.ort || ''}
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            <div className="pt-6 border-t border-slate-100">
                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Qualifikation</h3>
                <div className="flex items-center gap-3">
                    <GraduationCap className="w-4 h-4 text-slate-400 shrink-0" />
                    <div className="min-w-0 flex-1">
                        <p className="text-xs text-slate-500">Stufe</p>
                        <p className="text-sm font-medium break-words">{selectedMitarbeiter?.qualifikation || '-'}</p>
                    </div>
                </div>
            </div>

            <div className="pt-6 border-t border-slate-100">
                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Konditionen</h3>
                <div className="space-y-3">
                    <div className="flex items-center gap-3">
                        <Euro className="w-4 h-4 text-slate-400 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <p className="text-xs text-slate-500">Stundenlohn</p>
                            <p className="text-sm font-medium break-words">
                                {selectedMitarbeiter?.stundenlohn ?
                                    new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(selectedMitarbeiter.stundenlohn)
                                    : '-'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Calendar className="w-4 h-4 text-slate-400 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <p className="text-xs text-slate-500">Jahresurlaub</p>
                            <p className="text-sm font-medium break-words">
                                {selectedMitarbeiter?.jahresUrlaub ? `${selectedMitarbeiter.jahresUrlaub} Tage` : '-'}
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            <div className="pt-6 border-t border-slate-100">
                <Button
                    variant="ghost"
                    className="w-full text-red-600 hover:text-red-700 hover:bg-red-50 justify-start"
                    onClick={() => selectedMitarbeiter && handleDelete(selectedMitarbeiter.id)}
                >
                    <Trash2 className="w-4 h-4 mr-2" /> Mitarbeiter löschen
                </Button>
            </div>
        </div>
    );

    const NotizenList = () => (
        <div className="space-y-4">
            <div className="flex justify-between items-center border-b pb-4">
                <h3 className="text-lg font-semibold flex items-center gap-2">
                    <StickyNote className="w-5 h-5 text-rose-600" />
                    Notizen
                </h3>
                <Button onClick={() => setShowNotizModal(true)} className="bg-rose-600 text-white hover:bg-rose-700">
                    <Plus className="w-4 h-4 mr-2" /> Neue Notiz
                </Button>
            </div>

            {notizen.length === 0 ? (
                <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                    <StickyNote className="w-12 h-12 mx-auto mb-3 text-slate-300" />
                    <p>Keine Notizen vorhanden</p>
                </div>
            ) : (
                <div className="space-y-3">
                    {notizen.map((notiz) => (
                        <div key={notiz.id} className="p-4 bg-white border border-slate-200 rounded-lg shadow-sm relative group">
                            <div className="flex justify-between items-start mb-2">
                                <p className="text-xs text-slate-500">
                                    {new Date(notiz.erstelltAm).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })} {new Date(notiz.erstelltAm).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}
                                </p>
                                <button
                                    onClick={() => handleDeleteNotiz(notiz.id)}
                                    className="text-slate-400 hover:text-rose-600 opacity-0 group-hover:opacity-100 transition-opacity"
                                >
                                    <Trash2 className="w-4 h-4" />
                                </button>
                            </div>
                            <p className="text-slate-800 whitespace-pre-wrap text-sm">{notiz.inhalt}</p>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );

    const MONATSNAMEN = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];

    const formatEuro = (betrag: number) =>
        new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(betrag);

    const LohnabrechnungenList = () => {
        // Filter nach Jahr/Monat
        const gefiltert = lohnabrechnungen.filter(la =>
            (lohnFilterJahr === 'alle' || la.jahr === Number(lohnFilterJahr)) &&
            (lohnFilterMonat === 'alle' || la.monat === Number(lohnFilterMonat))
        );

        // Gruppiere nach Jahr
        const byYear = gefiltert.reduce((acc, la) => {
            const jahr = la.jahr;
            if (!acc[jahr]) acc[jahr] = [];
            acc[jahr].push(la);
            return acc;
        }, {} as Record<number, Lohnabrechnung[]>);

        const sortedYears = Object.keys(byYear).map(Number).sort((a, b) => b - a);

        const jahrOptions = [
            { value: 'alle', label: 'Alle Jahre' },
            ...Array.from(new Set(lohnabrechnungen.map(la => la.jahr)))
                .sort((a, b) => b - a)
                .map(j => ({ value: String(j), label: String(j) }))
        ];
        const monatOptions = [
            { value: 'alle', label: 'Alle Monate' },
            ...MONATSNAMEN.map((name, i) => ({ value: String(i + 1), label: name }))
        ];

        return (
            <div className="space-y-4">
                <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-3 border-b pb-4">
                    <h3 className="text-lg font-semibold flex items-center gap-2">
                        <Receipt className="w-5 h-5 text-rose-600" />
                        Lohnabrechnungen
                    </h3>
                    <div className="flex gap-2">
                        <Select
                            options={monatOptions}
                            value={lohnFilterMonat}
                            onChange={setLohnFilterMonat}
                            className="w-40"
                        />
                        <Select
                            options={jahrOptions}
                            value={lohnFilterJahr}
                            onChange={setLohnFilterJahr}
                            className="w-32"
                        />
                    </div>
                </div>

                {loadingLohnabrechnungen ? (
                    <div className="text-center py-12 text-slate-500">
                        <Loader2 className="w-8 h-8 mx-auto mb-3 animate-spin text-rose-600" />
                        <p>Lade Lohnabrechnungen...</p>
                    </div>
                ) : gefiltert.length === 0 ? (
                    <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                        <Receipt className="w-12 h-12 mx-auto mb-3 text-slate-300" />
                        <p>{lohnabrechnungen.length === 0 ? 'Keine Lohnabrechnungen vorhanden' : 'Keine Lohnabrechnungen für diesen Filter'}</p>
                        {lohnabrechnungen.length === 0 && (
                            <p className="text-xs mt-1">Lohnabrechnungen werden automatisch aus Steuerberater-E-Mails importiert</p>
                        )}
                    </div>
                ) : (
                    <div className="space-y-6">
                        {sortedYears.map(jahr => (
                            <div key={jahr}>
                                <h4 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-3">
                                    {jahr}
                                </h4>
                                <div className="grid gap-2">
                                    {byYear[jahr].sort((a, b) => b.monat - a.monat).map(la => (
                                        <div key={la.id} className="flex items-center justify-between p-3 bg-white border border-slate-100 rounded-lg hover:border-rose-100 hover:shadow-sm transition-all group">
                                            <div className="flex items-center gap-3">
                                                <div className="p-2 bg-slate-100 rounded text-slate-500 group-hover:text-rose-600 group-hover:bg-rose-50 transition-colors">
                                                    <File className="w-5 h-5" />
                                                </div>
                                                <div>
                                                    <p className="font-medium text-slate-900">
                                                        {MONATSNAMEN[la.monat - 1]} {la.jahr}
                                                    </p>
                                                    <p className="text-xs text-slate-500">
                                                        {la.bruttolohn != null && (
                                                            <span className="font-medium text-slate-700">
                                                                Brutto: {formatEuro(la.bruttolohn)}
                                                            </span>
                                                        )}
                                                        {la.nettolohn != null && (
                                                            <span className="font-medium text-slate-700">
                                                                {la.bruttolohn != null && ' • '}Netto: {formatEuro(la.nettolohn)}
                                                            </span>
                                                        )}
                                                        {la.bruttolohn == null && la.nettolohn == null && la.originalDateiname}
                                                    </p>
                                                </div>
                                            </div>
                                            <div className="flex gap-2">
                                                <button
                                                    onClick={() => setPreviewLohnabrechnung(la)}
                                                    className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded transition-colors"
                                                    title="PDF ansehen"
                                                >
                                                    <Eye className="w-4 h-4" />
                                                </button>
                                                <a
                                                    href={la.downloadUrl}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded transition-colors"
                                                    title="PDF herunterladen"
                                                >
                                                    <Download className="w-4 h-4" />
                                                </a>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        );
    };

    const MainContent = () => (
        <>
            {/* Tab Navigation */}
            {/*
              Gemeinsame Rezeptur aus Abschnitt 3/4 (siehe Plan-Datei,
              "Gemeinsame Rezeptur fuer Kopfzeile und Reiterleiste"):
              overflow-x-auto raus, flex-wrap + min-w-0 rein -- eine versteckt
              scrollende Reiterleiste ist keine Loesung, lieber umbrechen.
              data-testid fuer eine praezise Test-Auswahl (ein Selektor ueber
              Klassen traf in anderen Specs auch fremde "border-b"-Knoepfe).
            */}
            <div data-testid="mitarbeiter-reiterleiste" className="flex flex-wrap min-w-0 gap-2 mb-6 border-b border-slate-200 pb-2">
                <button
                    onClick={() => setActiveTab('dokumente')}
                    className={`px-3 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap ${activeTab === 'dokumente'
                        ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                        : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                        }`}
                >
                    <FileText className="w-4 h-4 inline-block mr-2" />
                    Dokumente
                </button>
                <button
                    onClick={() => setActiveTab('notizen')}
                    className={`px-3 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap ${activeTab === 'notizen'
                        ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                        : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                        }`}
                >
                    <StickyNote className="w-4 h-4 inline-block mr-2" />
                    Notizen
                </button>
                <button
                    onClick={() => setActiveTab('lohnabrechnungen')}
                    className={`px-3 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap ${activeTab === 'lohnabrechnungen'
                        ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                        : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                        }`}
                >
                    <Receipt className="w-4 h-4 inline-block mr-2" />
                    Lohnabrechnungen
                </button>
                <button
                    onClick={() => setActiveTab('stundenlohn')}
                    className={`px-3 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap ${activeTab === 'stundenlohn'
                        ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                        : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                        }`}
                >
                    <Euro className="w-4 h-4 inline-block mr-2" />
                    Stundenlohn-Verlauf
                </button>
            </div>

            {activeTab === 'dokumente' && <DokumenteList />}
            {activeTab === 'notizen' && <NotizenList />}
            {activeTab === 'lohnabrechnungen' && <LohnabrechnungenList />}
            {activeTab === 'stundenlohn' && selectedMitarbeiter && (
                <StundenlohnHistorieList
                    mitarbeiterId={selectedMitarbeiter.id}
                    onChange={() => loadMitarbeiter()}
                />
            )}
        </>
    );

    return (
        <PageLayout>
            <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
                <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
                    <DialogHeader>
                        <DialogTitle className="text-xl">{formData.id ? 'Mitarbeiter bearbeiten' : 'Neuer Mitarbeiter'}</DialogTitle>
                    </DialogHeader>

                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 py-4">
                        {/* Linke Spalte - Persönliche Daten */}
                        <div className="space-y-6">
                            {/* Name */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
                                    <User className="w-4 h-4" /> Persönliche Daten
                                </h3>
                                <div className="grid grid-cols-2 gap-3">
                                    <div className="space-y-1">
                                        <Label htmlFor="vorname" className="text-xs">Vorname</Label>
                                        <Input
                                            id="vorname"
                                            value={formData.vorname || ''}
                                            onChange={e => setFormData({ ...formData, vorname: e.target.value })}
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <Label htmlFor="nachname" className="text-xs">Nachname</Label>
                                        <Input
                                            id="nachname"
                                            value={formData.nachname || ''}
                                            onChange={e => setFormData({ ...formData, nachname: e.target.value })}
                                        />
                                    </div>
                                </div>
                                <div className="space-y-1">
                                    <Label htmlFor="geburtstag" className="text-xs">Geburtstag</Label>
                                    <DatePicker
                                        value={formData.geburtstag || ''}
                                        onChange={value => setFormData({ ...formData, geburtstag: value })}
                                        placeholder="Geburtstag wählen"
                                    />
                                </div>
                            </div>

                            {/* Kontakt */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
                                    <Phone className="w-4 h-4" /> Kontakt
                                </h3>
                                <div className="space-y-1">
                                    <Label htmlFor="email" className="text-xs">E-Mail</Label>
                                    <Input
                                        id="email"
                                        type="email"
                                        value={formData.email || ''}
                                        onChange={e => setFormData({ ...formData, email: e.target.value })}
                                        placeholder="max.mustermann@firma.de"
                                    />
                                </div>
                                <div className="grid grid-cols-2 gap-3">
                                    <div className="space-y-1">
                                        <Label htmlFor="telefon" className="text-xs">Mobil</Label>
                                        <Input
                                            id="telefon"
                                            type="tel"
                                            value={formData.telefon || ''}
                                            onChange={e => setFormData({ ...formData, telefon: e.target.value })}
                                            placeholder="+49 170 123456"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <Label htmlFor="festnetz" className="text-xs">Festnetz</Label>
                                        <Input
                                            id="festnetz"
                                            type="tel"
                                            value={formData.festnetz || ''}
                                            onChange={e => setFormData({ ...formData, festnetz: e.target.value })}
                                            placeholder="+49 9721 12345"
                                        />
                                    </div>
                                </div>
                            </div>

                            {/* Adresse */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
                                    <Home className="w-4 h-4" /> Adresse
                                </h3>
                                <div className="space-y-1">
                                    <Label htmlFor="strasse" className="text-xs">Straße & Hausnummer</Label>
                                    <Input
                                        id="strasse"
                                        value={formData.strasse || ''}
                                        onChange={e => setFormData({ ...formData, strasse: e.target.value })}
                                        placeholder="Musterstraße 1"
                                    />
                                </div>
                                <div className="grid grid-cols-3 gap-3">
                                    <div className="space-y-1">
                                        <Label htmlFor="plz" className="text-xs">PLZ</Label>
                                        <Input
                                            id="plz"
                                            value={formData.plz || ''}
                                            onChange={e => setFormData({ ...formData, plz: e.target.value })}
                                            placeholder="97421"
                                        />
                                    </div>
                                    <div className="space-y-1 col-span-2">
                                        <Label htmlFor="ort" className="text-xs">Ort</Label>
                                        <Input
                                            id="ort"
                                            value={formData.ort || ''}
                                            onChange={e => setFormData({ ...formData, ort: e.target.value })}
                                            placeholder="Schweinfurt"
                                        />
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Rechte Spalte - Arbeitsdaten */}
                        <div className="space-y-6">
                            {/* Abteilungen & Qualifikation */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
                                    <Building2 className="w-4 h-4" /> Zuordnung
                                </h3>
                                <div className="space-y-1">
                                    <Label className="text-xs">Abteilung(en)</Label>
                                    <div className="flex flex-wrap gap-2 border border-slate-200 rounded-lg p-3 bg-slate-50/50">
                                        {abteilungen.map(a => (
                                            <label
                                                key={a.id}
                                                className={`flex items-center gap-2 cursor-pointer px-3 py-1.5 rounded-full border transition-all text-sm ${(formData.abteilungIds || []).includes(a.id)
                                                    ? 'bg-rose-100 border-rose-300 text-rose-700'
                                                    : 'bg-white border-slate-200 text-slate-600 hover:border-rose-200'
                                                    }`}
                                            >
                                                <input
                                                    type="checkbox"
                                                    checked={(formData.abteilungIds || []).includes(a.id)}
                                                    onChange={(e) => {
                                                        const current = formData.abteilungIds || [];
                                                        if (e.target.checked) {
                                                            setFormData({ ...formData, abteilungIds: [...current, a.id] });
                                                        } else {
                                                            setFormData({ ...formData, abteilungIds: current.filter(id => id !== a.id) });
                                                        }
                                                    }}
                                                    className="sr-only"
                                                />
                                                {a.name}
                                            </label>
                                        ))}
                                        {abteilungen.length === 0 && (
                                            <p className="text-xs text-slate-400">Keine Abteilungen verfügbar</p>
                                        )}
                                    </div>
                                </div>
                                <div className="space-y-1">
                                    <Label htmlFor="qualifikation" className="text-xs">Qualifikation</Label>
                                    <select
                                        id="qualifikation"
                                        value={formData.qualifikation || ''}
                                        onChange={e => setFormData({ ...formData, qualifikation: e.target.value })}
                                        className="w-full h-10 px-3 py-2 border border-slate-200 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent bg-white"
                                    >
                                        {QUALIFIKATIONEN.map(q => (
                                            <option key={q.value} value={q.value}>{q.label}</option>
                                        ))}
                                    </select>
                                </div>
                            </div>

                            {/* Beschäftigung */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
                                    <Calendar className="w-4 h-4" /> Beschäftigung
                                </h3>
                                <div className="grid grid-cols-2 gap-3">
                                    <div className="space-y-1">
                                        <Label htmlFor="eintrittsdatum" className="text-xs">Eintrittsdatum</Label>
                                        <DatePicker
                                            value={formData.eintrittsdatum || ''}
                                            onChange={value => setFormData({ ...formData, eintrittsdatum: value })}
                                            placeholder="Datum wählen"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <Label htmlFor="jahresUrlaub" className="text-xs">Jahresurlaub (Tage)</Label>
                                        <Input
                                            id="jahresUrlaub"
                                            type="number"
                                            value={formData.jahresUrlaub || ''}
                                            onChange={e => setFormData({ ...formData, jahresUrlaub: parseInt(e.target.value) })}
                                            placeholder="30"
                                        />
                                    </div>
                                </div>
                            </div>

                            {/* Vergütung */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
                                    <Euro className="w-4 h-4" /> Vergütung
                                </h3>
                                <div className="space-y-1">
                                    <Label htmlFor="stundenlohn" className="text-xs">
                                        {formData.id ? 'Aktueller Stundenlohn (€)' : 'Stundenlohn (€) – startet ab Eintrittsdatum'}
                                    </Label>
                                    <Input
                                        id="stundenlohn"
                                        type="number"
                                        step="0.01"
                                        value={formData.stundenlohn || ''}
                                        onChange={e => setFormData({ ...formData, stundenlohn: parseFloat(e.target.value) })}
                                        placeholder="25.00"
                                        disabled={!!formData.id}
                                    />
                                    {formData.id && (
                                        <p className="text-xs text-slate-500">
                                            Wird im Tab „Stundenlohn-Verlauf" gepflegt – auch rückwirkend für alte Zeitbuchungen.
                                        </p>
                                    )}
                                </div>
                            </div>

                            {/* Anstellungsart-Wizard (Beschäftigungsart, Krankenkasse, Kinder) */}
                            <BeschaeftigungsWizard
                                value={{
                                    beschaeftigungsart: formData.beschaeftigungsart ?? null,
                                    krankenkasseId: formData.krankenkasseId ?? null,
                                    kinderlos: formData.kinderlos ?? null,
                                }}
                                onChange={v => setFormData({
                                    ...formData,
                                    beschaeftigungsart: v.beschaeftigungsart,
                                    krankenkasseId: v.krankenkasseId,
                                    kinderlos: v.kinderlos,
                                })}
                            />

                            {/* Geschäftsführer-Lohn (für Verrechnungslohn-Rechner) */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
                                    <User className="w-4 h-4" /> Geschäftsführer
                                </h3>
                                <label className="flex items-start gap-3 cursor-pointer p-3 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors">
                                    <input
                                        type="checkbox"
                                        checked={!!formData.istGeschaeftsfuehrer}
                                        onChange={e => setFormData({
                                            ...formData,
                                            istGeschaeftsfuehrer: e.target.checked,
                                            ...(e.target.checked ? {} : {
                                                kalkulatorischerLohnMonat: null,
                                                geldwertVorteilMonat: null,
                                            }),
                                        })}
                                        className="mt-0.5 h-5 w-5 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                    />
                                    <div>
                                        <span className="text-sm font-medium text-slate-700">Ist diese Person Geschäftsführer/in?</span>
                                        <p className="text-xs text-slate-500 mt-1">
                                            Dann nehmen wir statt einem echten Lohn den Wunschlohn unten für die Kalkulation des Stundensatzes.
                                        </p>
                                    </div>
                                </label>

                                {formData.istGeschaeftsfuehrer && (
                                    <div className="space-y-3 pl-3 border-l-2 border-rose-200">
                                        <div className="space-y-1">
                                            <Label htmlFor="kalkulatorischerLohnMonat" className="text-xs">
                                                Was möchtest du dir pro Monat als Lohn rechnen? (€)
                                            </Label>
                                            <Input
                                                id="kalkulatorischerLohnMonat"
                                                type="number"
                                                step="0.01"
                                                value={formData.kalkulatorischerLohnMonat ?? ''}
                                                onChange={e => setFormData({
                                                    ...formData,
                                                    kalkulatorischerLohnMonat: e.target.value === '' ? null : parseFloat(e.target.value),
                                                })}
                                                placeholder="5000.00"
                                            />
                                            <p className="text-xs text-slate-500">
                                                Wunschlohn pro Monat – fließt 12× ins Jahr in den Stundensatz ein.
                                            </p>
                                        </div>
                                        <div className="space-y-1">
                                            <Label htmlFor="geldwertVorteilMonat" className="text-xs">
                                                Auto/Telefon/Privatanteile pro Monat (€) – optional
                                            </Label>
                                            <Input
                                                id="geldwertVorteilMonat"
                                                type="number"
                                                step="0.01"
                                                value={formData.geldwertVorteilMonat ?? ''}
                                                onChange={e => setFormData({
                                                    ...formData,
                                                    geldwertVorteilMonat: e.target.value === '' ? null : parseFloat(e.target.value),
                                                })}
                                                placeholder="500.00"
                                            />
                                            <p className="text-xs text-slate-500">
                                                Pauschal: was die Firma für dich privat trägt (z.B. Firmenwagen, Handy).
                                            </p>
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Status */}
                            <div className="space-y-3">
                                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide">Status</h3>
                                <label className="flex items-center gap-3 cursor-pointer p-3 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors">
                                    <input
                                        type="checkbox"
                                        checked={formData.aktiv ?? true}
                                        onChange={e => setFormData({ ...formData, aktiv: e.target.checked })}
                                        className="h-5 w-5 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                    />
                                    <div>
                                        <span className="text-sm font-medium text-slate-700">Aktiv</span>
                                        <p className="text-xs text-slate-500">Mitarbeiter ist im System aktiv und kann sich anmelden</p>
                                    </div>
                                </label>
                            </div>
                        </div>
                    </div>

                    <DialogFooter className="border-t pt-4">
                        <Button variant="outline" onClick={() => setIsDialogOpen(false)}>Abbrechen</Button>
                        <Button onClick={handleSave} className="bg-rose-600 text-white hover:bg-rose-700">Speichern</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* QR-Code Modal */}
            <Dialog open={isQrModalOpen} onOpenChange={setIsQrModalOpen}>
                <DialogContent className="sm:max-w-md">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <QrCode className="w-5 h-5 text-rose-600" />
                            Zeiterfassungs-QR-Code
                        </DialogTitle>
                    </DialogHeader>
                    <div className="flex flex-col items-center py-6">
                        <p className="text-sm text-slate-500 mb-4 text-center">
                            {selectedMitarbeiter?.vorname} {selectedMitarbeiter?.nachname}
                        </p>
                        {selectedMitarbeiter?.loginToken ? (
                            <div className="bg-white p-4 rounded-lg border border-slate-200 shadow-sm">
                                <img
                                    src={`${BASE_API}/${selectedMitarbeiter.id}/qr-code?width=250&height=250`}
                                    alt="QR-Code für Zeiterfassung"
                                    className="w-[250px] h-[250px]"
                                />
                            </div>
                        ) : (
                            <div className="text-center text-slate-500 py-8">
                                <QrCode className="w-16 h-16 mx-auto mb-3 text-slate-300" />
                                <p>Kein Token vorhanden</p>
                            </div>
                        )}
                        <p className="text-xs text-slate-400 mt-4 text-center max-w-sm">
                            Mit diesem QR-Code kann sich der Mitarbeiter einmalig in der Zeiterfassungs-App anmelden.
                        </p>
                    </div>
                    <DialogFooter className="flex-col sm:flex-row gap-2">
                        <Button
                            variant="outline"
                            onClick={handleRegenerateToken}
                            disabled={regenerating}
                            className="w-full sm:w-auto"
                        >
                            {regenerating ? (
                                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                            ) : (
                                <RefreshCw className="w-4 h-4 mr-2" />
                            )}
                            Neu generieren
                        </Button>
                        <Button
                            onClick={handleDownloadQr}
                            className="w-full sm:w-auto bg-rose-600 text-white hover:bg-rose-700"
                        >
                            <Download className="w-4 h-4 mr-2" />
                            Herunterladen
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Notiz Erstellen Modal */}
            <Dialog open={showNotizModal} onOpenChange={setShowNotizModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Neue Notiz erstellen</DialogTitle>
                    </DialogHeader>
                    <div className="py-4">
                        <textarea
                            value={neueNotiz}
                            onChange={e => setNeueNotiz(e.target.value)}
                            className="w-full h-32 p-3 border border-slate-200 rounded-md focus:ring-2 focus:ring-rose-500 focus:outline-none"
                            placeholder="Notiz eingeben..."
                        />
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowNotizModal(false)}>Abbrechen</Button>
                        <Button onClick={handleCreateNotiz} className="bg-rose-600 text-white hover:bg-rose-700">Speichern</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Standard ImageViewer for Images */}
            {previewDoc && previewDoc.dateityp?.startsWith('image/') && (
                <ImageViewer
                    src={previewDoc.url || ''}
                    alt={previewDoc.originalDateiname}
                    onClose={() => setPreviewDoc(null)}
                    images={dokumente
                        .filter(d => d.dateityp?.startsWith('image/') && d.url)
                        .map(d => ({ url: d.url!, name: d.originalDateiname }))}
                    startIndex={dokumente
                        .filter(d => d.dateityp?.startsWith('image/') && d.url)
                        .findIndex(d => d.id === previewDoc.id)}
                />
            )}

            {/* Dokument Vorschau Modal (Non-Images) – globale Komponente */}
            {previewDoc && !previewDoc.dateityp?.startsWith('image/') && previewDoc.url && (
                <DocumentPreviewModal
                    doc={{ url: previewDoc.url, title: previewDoc.originalDateiname }}
                    isPdf={previewDoc.dateityp === 'application/pdf' || (previewDoc.originalDateiname?.toLowerCase().endsWith('.pdf') ?? false)}
                    onClose={() => setPreviewDoc(null)}
                />
            )}

            {/* Lohnabrechnung PDF-Vorschau */}
            {previewLohnabrechnung && (
                <DocumentPreviewModal
                    doc={{
                        url: previewLohnabrechnung.downloadUrl,
                        title: `Lohnabrechnung ${MONATSNAMEN[previewLohnabrechnung.monat - 1]} ${previewLohnabrechnung.jahr}`
                    }}
                    isPdf={true}
                    onClose={() => setPreviewLohnabrechnung(null)}
                />
            )}

            {view === 'LIST' ? (
                <div className="space-y-6">
                    <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                        <div>
                            <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                                Stammdaten
                            </p>
                            <h1 className="text-3xl font-bold text-slate-900 uppercase">
                                MITARBEITER
                            </h1>
                            <p className="text-slate-500 mt-1">
                                Verwaltung der Mitarbeiter und Dokumente
                            </p>
                        </div>
                        <div className="flex gap-2">
                            <Button
                                className="bg-rose-600 text-white hover:bg-rose-700"
                                onClick={() => {
                                    setFormData({});
                                    setIsDialogOpen(true);
                                }}
                            >
                                <Plus className="w-4 h-4 mr-2" /> Neu
                            </Button>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {mitarbeiter.map((m) => (
                            <Card
                                key={m.id}
                                className="p-6 cursor-pointer hover:border-rose-200 transition-all group"
                                onClick={() => {
                                    setSelectedMitarbeiter(m);
                                    loadDokumente(m.id);
                                    loadNotizen(m.id);
                                    setView('DETAIL');
                                    setActiveTab('dokumente');
                                }}
                            >
                                <div className="flex items-start justify-between mb-4">
                                    <div className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center text-rose-600 border border-rose-200 group-hover:bg-rose-600 group-hover:text-white transition-colors">
                                        <User className="w-5 h-5" />
                                    </div>
                                </div>
                                <h3 className="text-lg font-bold text-slate-900 mb-1">
                                    {m.nachname}, {m.vorname}
                                </h3>
                                {m.abteilungNames && (
                                    <p className="text-sm text-rose-600 font-medium mb-2 flex items-center gap-1">
                                        <Building2 className="w-3 h-3" />
                                        {m.abteilungNames}
                                    </p>
                                )}
                                <div className="flex items-center gap-4 text-sm text-slate-600">
                                    {m.geburtstag && (
                                        <span className="flex items-center gap-1">
                                            <Calendar className="w-3 h-3" />
                                            {new Date(m.geburtstag).toLocaleDateString()}
                                        </span>
                                    )}
                                    {m.jahresUrlaub && (
                                        <span className="flex items-center gap-1 text-rose-600 bg-rose-50 px-2 py-0.5 rounded text-xs font-medium border border-rose-100">
                                            <Calendar className="w-3 h-3" />
                                            {m.jahresUrlaub} Tage Urlaub
                                        </span>
                                    )}
                                </div>
                            </Card>
                        ))}
                    </div>
                </div>
            ) : (
                <DetailLayout
                    header={<DetailHeader />}
                    mainContent={<MainContent />}
                    sideContent={<SideInfo />}
                />
            )}
        </PageLayout>
    );
}

