import React, { useState, useEffect, useRef } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { PdfCanvasViewer } from '../components/ui/PdfCanvasViewer';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { DatePicker } from '../components/ui/datepicker';
import { DetailLayout } from '../components/DetailLayout';
import {
    Plus, User, Trash2, ArrowLeft,
    FileText, Upload, Calendar, Euro, File, Building2, QrCode, RefreshCw, Download, Loader2, Eye, X, Phone, GraduationCap, Home, StickyNote, Receipt, Shield, Award, FileBadge
} from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "../components/ui/dialog";
import { ImageViewer } from '../components/ui/image-viewer';
import { useConfirm } from '../components/ui/confirm-dialog';
import { useFeatures } from '../hooks/useFeatures';

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
    abteilungIds: number[] | null;
    abteilungNames: string | null;
    loginToken: string | null;
    jahresUrlaub: number | null;
    en1090RolleIds: number[] | null;
    en1090RolleNames: string | null;
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

interface En1090RolleOption {
    id: number;
    kurztext: string;
    aktiv: boolean;
}

interface MitarbeiterQualifikation {
    id: number;
    bezeichnung: string;
    beschreibung: string | null;
    datum: string | null;
    dokumentId: number | null;
    dokumentAnzeigename: string | null;
    dokumentGespeicherterName: string | null;
    dokumentDateityp: string | null;
    dokumentUploadDatum: string | null;
    erstelltAm: string;
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
    const [activeTab, setActiveTab] = useState<'dokumente' | 'notizen' | 'lohnabrechnungen' | 'en1090'>('dokumente');
    const [notizen, setNotizen] = useState<MitarbeiterNotiz[]>([]);
    const [showNotizModal, setShowNotizModal] = useState(false);
    const [neueNotiz, setNeueNotiz] = useState('');

    // Lohnabrechnungen States
    const [lohnabrechnungen, setLohnabrechnungen] = useState<Lohnabrechnung[]>([]);
    const [loadingLohnabrechnungen, setLoadingLohnabrechnungen] = useState(false);

    // EN 1090 States
    const features = useFeatures();
    const [en1090RolleOptionen, setEn1090RolleOptionen] = useState<En1090RolleOption[]>([]);
    const [selectedRolleIds, setSelectedRolleIds] = useState<number[]>([]);
    const [savingRollen, setSavingRollen] = useState(false);
    const [qualifikationen, setQualifikationen] = useState<MitarbeiterQualifikation[]>([]);
    const [showQualModal, setShowQualModal] = useState(false);
    const [editingQual, setEditingQual] = useState<Partial<MitarbeiterQualifikation> & { dateiFile?: File | null }>({});
    const [savingQual, setSavingQual] = useState(false);
    const [isDraggingQual, setIsDraggingQual] = useState(false);
    const qualFileInputRef = useRef<HTMLInputElement>(null);

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
        if (activeTab === 'en1090' && selectedMitarbeiter && features.en1090) {
            loadEn1090Rollen();
            loadQualifikationen(selectedMitarbeiter.id);
        }
    }, [activeTab, selectedMitarbeiter, features.en1090]);

    const loadEn1090Rollen = async () => {
        try {
            const res = await fetch('/api/en1090/rollen');
            if (res.ok) setEn1090RolleOptionen(await res.json());
        } catch (e) { console.error(e); }
    };

    const loadQualifikationen = async (id: number) => {
        try {
            const res = await fetch(`${BASE_API}/${id}/qualifikationen`);
            if (res.ok) setQualifikationen(await res.json());
        } catch (e) { console.error(e); }
    };

    const saveRollen = async () => {
        if (!selectedMitarbeiter) return;
        setSavingRollen(true);
        try {
            await fetch(`${BASE_API}/${selectedMitarbeiter.id}/en1090-rollen`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(selectedRolleIds)
            });
            const updRes = await fetch(`${BASE_API}/${selectedMitarbeiter.id}`);
            if (updRes.ok) setSelectedMitarbeiter(await updRes.json());
        } catch (e) { console.error(e); } finally { setSavingRollen(false); }
    };

    const saveQualifikation = async () => {
        if (!selectedMitarbeiter || !editingQual.bezeichnung?.trim()) return;
        setSavingQual(true);
        try {
            const fd = new FormData();
            fd.append('bezeichnung', editingQual.bezeichnung.trim());
            if (editingQual.beschreibung) fd.append('beschreibung', editingQual.beschreibung);
            if (editingQual.datum) fd.append('datum', editingQual.datum);
            if (editingQual.dateiFile) fd.append('datei', editingQual.dateiFile);
            const isEdit = !!editingQual.id;
            const url = isEdit
                ? `${BASE_API}/${selectedMitarbeiter.id}/qualifikationen/${editingQual.id}`
                : `${BASE_API}/${selectedMitarbeiter.id}/qualifikationen`;
            const res = await fetch(url, { method: isEdit ? 'PUT' : 'POST', body: fd });
            if (res.ok) {
                await loadQualifikationen(selectedMitarbeiter.id);
                setShowQualModal(false);
                setEditingQual({});
            }
        } catch (e) { console.error(e); } finally { setSavingQual(false); }
    };

    const deleteQualifikation = async (qualId: number) => {
        if (!selectedMitarbeiter) return;
        if (!await confirmDialog({ title: 'Qualifikation l\u00f6schen', message: 'Nachweis wirklich l\u00f6schen?', variant: 'danger', confirmLabel: 'L\u00f6schen' })) return;
        try {
            await fetch(`${BASE_API}/${selectedMitarbeiter.id}/qualifikationen/${qualId}`, { method: 'DELETE' });
            await loadQualifikationen(selectedMitarbeiter.id);
        } catch (e) { console.error(e); }
    };

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

    const SideInfo = () => (
        <div className="space-y-6">
            <div>
                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Persönliche Daten</h3>
                <div className="space-y-3">
                    <div className="flex items-center gap-3">
                        <User className="w-4 h-4 text-slate-400" />
                        <div>
                            <p className="text-xs text-slate-500">Voller Name</p>
                            <p className="text-sm font-medium">{selectedMitarbeiter?.vorname} {selectedMitarbeiter?.nachname}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Calendar className="w-4 h-4 text-slate-400" />
                        <div>
                            <p className="text-xs text-slate-500">Geburtsdatum</p>
                            <p className="text-sm font-medium">
                                {selectedMitarbeiter?.geburtstag ? new Date(selectedMitarbeiter.geburtstag).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '-'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Building2 className="w-4 h-4 text-slate-400" />
                        <div>
                            <p className="text-xs text-slate-500">Abteilung(en)</p>
                            <p className="text-sm font-medium">{selectedMitarbeiter?.abteilungNames || '-'}</p>
                        </div>
                    </div>
                </div>
            </div>

            <div className="pt-6 border-t border-slate-100">
                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Kontakt</h3>
                <div className="space-y-3">
                    <div className="flex items-center gap-3">
                        <User className="w-4 h-4 text-slate-400" />
                        <div>
                            <p className="text-xs text-slate-500">E-Mail</p>
                            <p className="text-sm font-medium">{selectedMitarbeiter?.email || '-'}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Phone className="w-4 h-4 text-slate-400" />
                        <div>
                            <p className="text-xs text-slate-500">Mobiltelefon</p>
                            <p className="text-sm font-medium">{selectedMitarbeiter?.telefon || '-'}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Phone className="w-4 h-4 text-slate-400" />
                        <div>
                            <p className="text-xs text-slate-500">Festnetz</p>
                            <p className="text-sm font-medium">{selectedMitarbeiter?.festnetz || '-'}</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <User className="w-4 h-4 text-slate-400" />
                        <div>
                            <p className="text-xs text-slate-500">Adresse</p>
                            <p className="text-sm font-medium">
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
                    <GraduationCap className="w-4 h-4 text-slate-400" />
                    <div>
                        <p className="text-xs text-slate-500">Stufe</p>
                        <p className="text-sm font-medium">{selectedMitarbeiter?.qualifikation || '-'}</p>
                    </div>
                </div>
            </div>

            <div className="pt-6 border-t border-slate-100">
                <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Konditionen</h3>
                <div className="space-y-3">
                    <div className="flex items-center gap-3">
                        <Euro className="w-4 h-4 text-slate-400" />
                        <div>
                            <p className="text-xs text-slate-500">Stundenlohn</p>
                            <p className="text-sm font-medium">
                                {selectedMitarbeiter?.stundenlohn ?
                                    new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(selectedMitarbeiter.stundenlohn)
                                    : '-'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <Calendar className="w-4 h-4 text-slate-400" />
                        <div>
                            <p className="text-xs text-slate-500">Jahresurlaub</p>
                            <p className="text-sm font-medium">
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

    const LohnabrechnungenList = () => {
        // Gruppiere nach Jahr
        const byYear = lohnabrechnungen.reduce((acc, la) => {
            const jahr = la.jahr;
            if (!acc[jahr]) acc[jahr] = [];
            acc[jahr].push(la);
            return acc;
        }, {} as Record<number, Lohnabrechnung[]>);

        const sortedYears = Object.keys(byYear).map(Number).sort((a, b) => b - a);

        return (
            <div className="space-y-4">
                <div className="flex justify-between items-center border-b pb-4">
                    <h3 className="text-lg font-semibold flex items-center gap-2">
                        <Receipt className="w-5 h-5 text-rose-600" />
                        Lohnabrechnungen
                    </h3>
                </div>

                {loadingLohnabrechnungen ? (
                    <div className="text-center py-12 text-slate-500">
                        <Loader2 className="w-8 h-8 mx-auto mb-3 animate-spin text-rose-600" />
                        <p>Lade Lohnabrechnungen...</p>
                    </div>
                ) : lohnabrechnungen.length === 0 ? (
                    <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                        <Receipt className="w-12 h-12 mx-auto mb-3 text-slate-300" />
                        <p>Keine Lohnabrechnungen vorhanden</p>
                        <p className="text-xs mt-1">Lohnabrechnungen werden automatisch aus Steuerberater-E-Mails importiert</p>
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
                                                        {la.originalDateiname}
                                                        {la.bruttolohn && la.nettolohn && (
                                                            <span className="ml-2">
                                                                • Brutto: {new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(la.bruttolohn)}
                                                                • Netto: {new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(la.nettolohn)}
                                                            </span>
                                                        )}
                                                    </p>
                                                </div>
                                            </div>
                                            <div className="flex gap-2">
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
            <div className="flex gap-2 mb-6 border-b border-slate-200 pb-2 overflow-x-auto">
                <button
                    onClick={() => setActiveTab('dokumente')}
                    className={`px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap ${activeTab === 'dokumente'
                        ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                        : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                        }`}
                >
                    <FileText className="w-4 h-4 inline-block mr-2" />
                    Dokumente
                </button>
                <button
                    onClick={() => setActiveTab('notizen')}
                    className={`px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap ${activeTab === 'notizen'
                        ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                        : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                        }`}
                >
                    <StickyNote className="w-4 h-4 inline-block mr-2" />
                    Notizen
                </button>
                <button
                    onClick={() => setActiveTab('lohnabrechnungen')}
                    className={`px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap ${activeTab === 'lohnabrechnungen'
                        ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                        : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                        }`}
                >
                    <Receipt className="w-4 h-4 inline-block mr-2" />
                    Lohnabrechnungen
                </button>
                {features.en1090 && (
                    <button
                        onClick={() => setActiveTab('en1090')}
                        className={`px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap ${activeTab === 'en1090'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                            }`}
                    >
                        <Shield className="w-4 h-4 inline-block mr-2" />
                        EN&nbsp;1090
                    </button>
                )}
            </div>

            {activeTab === 'dokumente' && <DokumenteList />}
            {activeTab === 'notizen' && <NotizenList />}
            {activeTab === 'lohnabrechnungen' && <LohnabrechnungenList />}
            {activeTab === 'en1090' && features.en1090 && (
                <div className="space-y-6">
                    {/* Rollen-Bereich */}
                    <Card className="p-5 space-y-4">
                        <h3 className="text-sm font-semibold text-slate-700 flex items-center gap-2">
                            <Shield className="w-4 h-4 text-rose-600" /> Zugewiesene Rollen
                        </h3>
                        <div className="flex flex-wrap gap-2">
                            {en1090RolleOptionen.filter(r => r.aktiv).map(rolle => (
                                <label key={rolle.id} className={`flex items-center gap-2 px-3 py-1.5 rounded-full border cursor-pointer text-sm transition-colors ${
                                    selectedRolleIds.includes(rolle.id)
                                        ? 'bg-rose-600 text-white border-rose-600'
                                        : 'bg-white text-slate-700 border-slate-200 hover:border-rose-300'
                                }`}>
                                    <input
                                        type="checkbox"
                                        className="hidden"
                                        checked={selectedRolleIds.includes(rolle.id)}
                                        onChange={e => setSelectedRolleIds(prev =>
                                            e.target.checked ? [...prev, rolle.id] : prev.filter(id => id !== rolle.id)
                                        )}
                                    />
                                    {rolle.kurztext}
                                </label>
                            ))}
                            {en1090RolleOptionen.filter(r => r.aktiv).length === 0 && (
                                <p className="text-sm text-slate-400">Keine Rollen verfügbar. Bitte zuerst in den Firmaeinstellungen anlegen.</p>
                            )}
                        </div>
                        <Button
                            size="sm"
                            className="bg-rose-600 text-white hover:bg-rose-700"
                            onClick={saveRollen}
                            disabled={savingRollen}
                        >
                            {savingRollen ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
                            Rollen speichern
                        </Button>
                    </Card>

                    {/* Qualifikationen-Bereich */}
                    <Card className="p-5 space-y-4">
                        <div className="flex justify-between items-center">
                            <h3 className="text-sm font-semibold text-slate-700 flex items-center gap-2">
                                <FileBadge className="w-4 h-4 text-rose-600" /> Qualifikations-Nachweise
                            </h3>
                            <Button
                                size="sm"
                                className="bg-rose-600 text-white hover:bg-rose-700"
                                onClick={() => { setEditingQual({}); setShowQualModal(true); }}
                            >
                                <Plus className="w-4 h-4 mr-2" /> Neuer Nachweis
                            </Button>
                        </div>

                        {qualifikationen.length === 0 ? (
                            <div className="text-center py-10 text-slate-400 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                                <Award className="w-8 h-8 mx-auto mb-2 text-slate-300" />
                                <p className="text-sm">Noch keine Nachweise eingetragen</p>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {qualifikationen.map(q => (
                                    <div key={q.id} className="flex items-start justify-between gap-3 p-3 rounded-lg border border-slate-100 bg-slate-50">
                                        <div className="flex-1 min-w-0">
                                            <p className="font-medium text-slate-900 text-sm">{q.bezeichnung}</p>
                                            {q.beschreibung && <p className="text-xs text-slate-500 mt-0.5">{q.beschreibung}</p>}
                                            <div className="flex flex-wrap gap-3 mt-1 text-xs text-slate-400">
                                                {q.datum && <span className="flex items-center gap-1"><Calendar className="w-3 h-3" />{q.datum}</span>}
                                                {q.dokumentAnzeigename && (
                                                    <a
                                                        href={`/api/dokumente/${q.dokumentGespeicherterName}`}
                                                        target="_blank"
                                                        rel="noreferrer"
                                                        className="flex items-center gap-1 text-rose-600 hover:underline"
                                                    >
                                                        <FileText className="w-3 h-3" />{q.dokumentAnzeigename}
                                                    </a>
                                                )}
                                            </div>
                                        </div>
                                        <div className="flex gap-1 shrink-0">
                                            <Button size="sm" variant="ghost" className="text-slate-500 hover:text-slate-700"
                                                onClick={() => { setEditingQual({ ...q, dateiFile: null }); setShowQualModal(true); }}>
                                                <Eye className="w-4 h-4" />
                                            </Button>
                                            <Button size="sm" variant="ghost" className="text-rose-600 hover:bg-rose-50"
                                                onClick={() => deleteQualifikation(q.id)}>
                                                <Trash2 className="w-4 h-4" />
                                            </Button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </Card>
                </div>
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
                                    <Label htmlFor="stundenlohn" className="text-xs">Stundenlohn (€)</Label>
                                    <Input
                                        id="stundenlohn"
                                        type="number"
                                        step="0.01"
                                        value={formData.stundenlohn || ''}
                                        onChange={e => setFormData({ ...formData, stundenlohn: parseFloat(e.target.value) })}
                                        placeholder="25.00"
                                    />
                                </div>
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

            {/* Qualifikation Modal */}
            <Dialog open={showQualModal} onOpenChange={open => { if (!open) { setShowQualModal(false); setEditingQual({}); } }} className="w-[700px] max-w-[95vw]">
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{editingQual.id ? 'Nachweis bearbeiten' : 'Neuer Qualifikations-Nachweis'}</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <div>
                            <Label>Bezeichnung *</Label>
                            <Input
                                placeholder="z.B. Schweißer-Prüfungszeugnis nach EN ISO 9606-1"
                                value={editingQual.bezeichnung || ''}
                                onChange={e => setEditingQual(prev => ({ ...prev, bezeichnung: e.target.value }))}
                            />
                        </div>
                        <div>
                            <Label>Beschreibung</Label>
                            <textarea
                                className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 min-h-[80px] resize-y"
                                placeholder="Weitere Details zum Nachweis..."
                                value={editingQual.beschreibung || ''}
                                onChange={e => setEditingQual(prev => ({ ...prev, beschreibung: e.target.value }))}
                            />
                        </div>
                        <div>
                            <Label>Datum (z.B. Ausstellungsdatum)</Label>
                            <Input
                                type="date"
                                value={editingQual.datum || ''}
                                onChange={e => setEditingQual(prev => ({ ...prev, datum: e.target.value }))}
                            />
                        </div>
                        <div>
                            <Label>Dokument hochladen (optional)</Label>
                            {editingQual.dokumentAnzeigename && !editingQual.dateiFile && (
                                <p className="text-xs text-slate-500 mb-1 flex items-center gap-1">
                                    <FileText className="w-3 h-3" /> Aktuell: {editingQual.dokumentAnzeigename}
                                </p>
                            )}
                            <div
                                className={`mt-1 border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-colors ${
                                    isDraggingQual
                                        ? 'border-rose-500 bg-rose-50'
                                        : editingQual.dateiFile
                                        ? 'border-rose-400 bg-rose-50'
                                        : 'border-slate-300 hover:border-rose-400 hover:bg-slate-50'
                                }`}
                                onClick={() => qualFileInputRef.current?.click()}
                                onDragOver={e => { e.preventDefault(); setIsDraggingQual(true); }}
                                onDragLeave={() => setIsDraggingQual(false)}
                                onDrop={e => {
                                    e.preventDefault();
                                    setIsDraggingQual(false);
                                    const file = e.dataTransfer.files?.[0];
                                    if (file) setEditingQual(prev => ({ ...prev, dateiFile: file }));
                                }}
                            >
                                {editingQual.dateiFile ? (
                                    <>
                                        <FileText className="w-8 h-8 mx-auto mb-2 text-rose-500" />
                                        <p className="text-sm font-medium text-rose-700">{editingQual.dateiFile.name}</p>
                                        <button
                                            type="button"
                                            className="mt-2 text-xs text-slate-400 hover:text-rose-500 underline"
                                            onClick={e => { e.stopPropagation(); setEditingQual(prev => ({ ...prev, dateiFile: null })); }}
                                        >
                                            Datei entfernen
                                        </button>
                                    </>
                                ) : (
                                    <>
                                        <Upload className="w-8 h-8 mx-auto mb-2 text-slate-400" />
                                        <p className="text-sm font-medium text-slate-700">Datei hier ablegen oder klicken</p>
                                        <p className="text-xs text-slate-400 mt-1">PDF, JPG, PNG oder andere Dokumente</p>
                                    </>
                                )}
                                <input
                                    ref={qualFileInputRef}
                                    type="file"
                                    className="hidden"
                                    onChange={e => setEditingQual(prev => ({ ...prev, dateiFile: e.target.files?.[0] ?? null }))}
                                />
                            </div>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => { setShowQualModal(false); setEditingQual({}); }}>
                            <X className="w-4 h-4 mr-2" /> Abbrechen
                        </Button>
                        <Button
                            onClick={saveQualifikation}
                            disabled={savingQual || !editingQual.bezeichnung?.trim()}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {savingQual ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
                            Speichern
                        </Button>
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

            {/* Dokument Vorschau Modal (Non-Images) */}
            {previewDoc && !previewDoc.dateityp?.startsWith('image/') && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setPreviewDoc(null)}>
                    <div className="relative bg-white rounded-lg shadow-xl max-w-4xl max-h-[90vh] w-full m-4 overflow-hidden" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between p-4 border-b">
                            <h3 className="text-lg font-semibold text-slate-900 flex items-center gap-2">
                                <Eye className="w-5 h-5 text-rose-600" />
                                {previewDoc.originalDateiname}
                            </h3>
                            <button
                                onClick={() => setPreviewDoc(null)}
                                className="p-2 hover:bg-slate-100 rounded-full transition-colors"
                            >
                                <X className="w-5 h-5 text-slate-500" />
                            </button>
                        </div>
                        <div className="p-4 overflow-auto max-h-[calc(90vh-80px)]">
                            {(previewDoc.dateityp === 'application/pdf' || previewDoc.originalDateiname?.endsWith('.pdf')) && previewDoc.url ? (
                                <PdfCanvasViewer
                                    url={previewDoc.url}
                                    className="w-full h-[70vh] rounded-lg overflow-y-auto overflow-x-hidden"
                                />
                            ) : (
                                <div className="text-center py-12">
                                    <File className="w-16 h-16 mx-auto mb-4 text-slate-300" />
                                    <p className="text-slate-600 mb-4">Vorschau für diesen Dateityp nicht verfügbar</p>
                                    <Button
                                        onClick={() => window.open(previewDoc.url, '_blank')}
                                        className="bg-rose-600 text-white hover:bg-rose-700"
                                    >
                                        <Download className="w-4 h-4 mr-2" />
                                        Datei herunterladen
                                    </Button>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
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
                                    setSelectedRolleIds(m.en1090RolleIds ?? []);
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

