import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import {
    ArrowLeft,
    Briefcase,
    Calendar,
    Check,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Clock,
    CornerDownRight,
    Edit2,
    Euro,
    File,
    FileText,
    FolderOpen,
    Hammer,
    Lock,
    Mail,
    MapPin,
    Plus,
    Package,
    Receipt,
    RefreshCw,
    Search,
    StickyNote,
    Ban,
    Building2,
    ExternalLink,
    Trash2,
    Upload,
    User,
    X,
} from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { cn } from "../lib/utils";
import type { Projekt, ProjektDetail, AusgangsGeschaeftsDokument, AusgangsGeschaeftsDokumentTyp, AbrechnungsverlaufDto, AbrechnungspositionDto, Artikel } from "../types";
import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN } from "../types";
import { DetailLayout } from "../components/DetailLayout";
import { ProjektErstellenModal } from "../components/ProjektErstellenModal";
import { DocumentManager } from "../components/DocumentManager";
import { EmailsTab } from "../components/EmailsTab";
import { Select } from "../components/ui/select-custom";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "../components/ui/dialog";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import GoogleMapsEmbed from "../components/GoogleMapsEmbed";
import { PageLayout } from "../components/layout/PageLayout";

import { ImageViewer } from "../components/ui/image-viewer";
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import type { DocBlock } from '../components/document-editor/types';
import { TeilrechnungPositionRow, getAllServiceBlocks, zeroOutUnselectedBlocks } from '../components/TeilrechnungPositionRow';
import { onDokumentChanged } from '../lib/dokumentChannel';
import { appendBildToNotiz, removeBildFromNotiz } from '../lib/optimisticUploads';
import DocumentPreviewModal from '../components/DocumentPreviewModal';
import { ZuordnungModal } from '../components/ZuordnungModal';

interface Supplier {
    id: number;
    lieferantenname: string;
}

const PAGE_SIZE = 12;
const LAGER_ARTIKEL_PAGE_SIZE = 15;

// ==================== DOCUMENT TREE HELPERS ====================

interface DokumentTreeNode {
    dokument: AusgangsGeschaeftsDokument;
    children: DokumentTreeNode[];
    depth: number;
}

/**
 * Baut eine Baumstruktur aus der flachen Dokumentenliste.
 * Basisdokumente (ohne vorgaengerId) sind Wurzeln,
 * Nachfolger werden als Kinder eingehängt.
 */
function buildDokumentTree(dokumente: AusgangsGeschaeftsDokument[]): DokumentTreeNode[] {
    const byId = new Map<number, AusgangsGeschaeftsDokument>();
    const childrenMap = new Map<number, AusgangsGeschaeftsDokument[]>();

    for (const dok of dokumente) {
        byId.set(dok.id, dok);
        if (dok.vorgaengerId) {
            const existing = childrenMap.get(dok.vorgaengerId) || [];
            existing.push(dok);
            childrenMap.set(dok.vorgaengerId, existing);
        }
    }

    function buildNode(dok: AusgangsGeschaeftsDokument, depth: number): DokumentTreeNode {
        const kids = (childrenMap.get(dok.id) || [])
            .sort((a, b) => new Date(a.datum).getTime() - new Date(b.datum).getTime());
        return {
            dokument: dok,
            children: kids.map(k => buildNode(k, depth + 1)),
            depth,
        };
    }

    // Wurzeln = Dokumente ohne vorgaengerId ODER deren Vorgänger nicht in dieser Liste ist
    const roots = dokumente
        .filter(d => !d.vorgaengerId || !byId.has(d.vorgaengerId))
        .sort((a, b) => new Date(a.datum).getTime() - new Date(b.datum).getTime());

    return roots.map(r => buildNode(r, 0));
}

/** Flacht den Baum für die sequenzielle Darstellung ab */
function flattenTree(nodes: DokumentTreeNode[]): DokumentTreeNode[] {
    const result: DokumentTreeNode[] = [];
    for (const node of nodes) {
        result.push(node);
        if (node.children.length > 0) {
            result.push(...flattenTree(node.children));
        }
    }
    return result;
}

// ==================== TYPE HELPERS ====================

const TYP_COLORS: Record<string, string> = {
    'ANGEBOT': 'bg-blue-50 text-blue-700 border-blue-200',
    'AUFTRAGSBESTAETIGUNG': 'bg-purple-50 text-purple-700 border-purple-200',
    'RECHNUNG': 'bg-rose-50 text-rose-700 border-rose-200',
    'TEILRECHNUNG': 'bg-rose-50 text-rose-600 border-rose-200',
    'ABSCHLAGSRECHNUNG': 'bg-orange-50 text-orange-700 border-orange-200',
    'SCHLUSSRECHNUNG': 'bg-rose-100 text-rose-800 border-rose-300',
    'GUTSCHRIFT': 'bg-green-50 text-green-700 border-green-200',
    'STORNO': 'bg-red-50 text-red-700 border-red-200',
};





// ==================== DETAIL VIEW ====================

interface ProjektDetailViewProps {
    projekt: ProjektDetail;
    onBack: () => void;
    onEdit: () => void;
    onRefresh: () => Promise<void>;
    initialTab?: 'zeiten' | 'materialkosten' | 'emails' | 'geschaeftsdokumente' | 'dokumente' | 'beschreibung' | 'notizen';
}

const ProjektDetailView: React.FC<ProjektDetailViewProps> = ({ projekt, onBack, onEdit, onRefresh, initialTab }) => {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState<'zeiten' | 'materialkosten' | 'emails' | 'geschaeftsdokumente' | 'dokumente' | 'beschreibung' | 'notizen'>(initialTab || 'zeiten');
    const [kurzbeschreibung, setKurzbeschreibung] = useState(projekt.kurzbeschreibung || '');
    const [savingDesc, setSavingDesc] = useState(false);

    useEffect(() => {
        setKurzbeschreibung(projekt.kurzbeschreibung || '');
    }, [projekt.kurzbeschreibung]);

    const handleSaveDescription = async () => {
        setSavingDesc(true);
        try {
            const response = await fetch(`/api/projekte/${projekt.id}/kurzbeschreibung`, {
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
    const [showMaterialModal, setShowMaterialModal] = useState(false);
    const [showLagerArtikelModal, setShowLagerArtikelModal] = useState(false);
    const [newMaterial, setNewMaterial] = useState<{ beschreibung: string, betrag: string, lieferantId?: string, rechnungsnummer: string }>({ beschreibung: '', betrag: '', rechnungsnummer: '' });
    const [savingMaterial, setSavingMaterial] = useState(false);
    const [lagerArtikel, setLagerArtikel] = useState<Artikel[]>([]);
    const [loadingLagerArtikel, setLoadingLagerArtikel] = useState(false);
    const [savingLagerArtikel, setSavingLagerArtikel] = useState(false);
    const [lagerArtikelError, setLagerArtikelError] = useState<string | null>(null);
    const [lagerArtikelSearch, setLagerArtikelSearch] = useState('');
    const [lagerArtikelPage, setLagerArtikelPage] = useState(0);
    const [lagerArtikelGesamt, setLagerArtikelGesamt] = useState(0);
    const [selectedLagerArtikelKeys, setSelectedLagerArtikelKeys] = useState<Set<string>>(new Set());
    const [selectedLagerArtikelData, setSelectedLagerArtikelData] = useState<Record<string, Artikel>>({});
    const [lagerArtikelMengen, setLagerArtikelMengen] = useState<Record<string, string>>({});
    const [lagerArtikelBeschaffung, setLagerArtikelBeschaffung] = useState<Record<string, 'lager' | 'bestellen'>>({});
    const [suppliers, setSuppliers] = useState<Supplier[]>([]);
    const [showSupplierPicker, setShowSupplierPicker] = useState(false);
    const [supplierSearchQuery, setSupplierSearchQuery] = useState('');

    const [showDokumentTypDialog, setShowDokumentTypDialog] = useState(false);
    const lagerArtikelFetchSeq = useRef(0);

    // Rechnungserstellung Dialog State
    const [showRechnungDialog, setShowRechnungDialog] = useState(false);
    const [rechnungBasisDok, setRechnungBasisDok] = useState<AusgangsGeschaeftsDokument | null>(null);
    const [abrechnungsverlauf, setAbrechnungsverlauf] = useState<AbrechnungsverlaufDto | null>(null);
    const [rechnungTyp, setRechnungTyp] = useState<AusgangsGeschaeftsDokumentTyp>('TEILRECHNUNG');
    const [abschlagsBetrag, setAbschlagsBetrag] = useState<string>('');
    const [rechnungLoading, setRechnungLoading] = useState(false);

    // Teilrechnung Positions-Auswahl
    const [basisDokBlocks, setBasisDokBlocks] = useState<DocBlock[]>([]);
    const [selectedBlockIds, setSelectedBlockIds] = useState<Set<string>>(new Set());
    const [expandedBlockIds, setExpandedBlockIds] = useState<Set<string>>(new Set());
    const [bereitsAbgerechneteBlockIds, setBereitsAbgerechneteBlockIds] = useState<Set<string>>(new Set());

    // Abschlagsrechnung Eingabemodus
    const [abschlagsEingabeModus, setAbschlagsEingabeModus] = useState<'netto' | 'brutto' | 'prozent'>('netto');

    // Abschlagsrechnung: berechneter Nettobetrag
    const berechneterAbschlagNetto = useMemo(() => {
        const val = parseFloat(abschlagsBetrag);
        if (isNaN(val) || val <= 0) return 0;
        if (abschlagsEingabeModus === 'netto') return val;
        if (abschlagsEingabeModus === 'brutto') return val / 1.19;
        // prozent
        const basis = abrechnungsverlauf?.basisdokumentBetragNetto ?? rechnungBasisDok?.betragNetto ?? 0;
        return basis * (val / 100);
    }, [abschlagsBetrag, abschlagsEingabeModus, abrechnungsverlauf, rechnungBasisDok]);

    // Teilrechnung: Summe der gewählten Positionen
    const teilrechnungSelectedSum = useMemo(() => {
        return getAllServiceBlocks(basisDokBlocks)
            .filter(b => selectedBlockIds.has(b.id))
            .reduce((sum, b) => sum + (b.quantity || 0) * (b.price || 0), 0);
    }, [basisDokBlocks, selectedBlockIds]);

    // Teilrechnung: Prüfung ob Gesamtbetrag (gewählte Positionen + bereits abgerechnet) den Basisbetrag überschreitet
    const teilrechnungExceedsRestbetrag = useMemo(() => {
        if (!abrechnungsverlauf || teilrechnungSelectedSum <= 0) return false;
        // Toleranz von 0.01 für Rundungsdifferenzen
        return teilrechnungSelectedSum > abrechnungsverlauf.restbetrag + 0.01;
    }, [abrechnungsverlauf, teilrechnungSelectedSum]);

    // Lösch-Dialog State
    const [showDeleteDialog, setShowDeleteDialog] = useState(false);
    const [deleteDokument, setDeleteDokument] = useState<AusgangsGeschaeftsDokument | null>(null);
    const [deleteBegruendung, setDeleteBegruendung] = useState('');
    const [deleteLoading, setDeleteLoading] = useState(false);

    // PDF Preview Modal State
    const [pdfPreviewDoc, setPdfPreviewDoc] = useState<{ url: string; title: string } | null>(null);
    const [zuordnungBearbeitenEr, setZuordnungBearbeitenEr] = useState<Eingangsrechnung | null>(null);

    // Eingangsrechnungen State
    interface EingangsrechnungAnteil {
        projektId?: number;
        projektName?: string;
        projektNummer?: string;
        kostenstelleId?: number;
        kostenstelleName?: string;
        prozent?: number;
        berechneterBetrag?: number;
        beschreibung?: string;
        zugeordnetVonName?: string;
        zugeordnetAm?: string;
    }
    interface DokumentKetteRef {
        id: number;
        typ: string;
        dokumentNummer?: string;
        dokumentDatum?: string;
        betragNetto?: number;
        pdfUrl?: string;
    }
    interface Eingangsrechnung {
        id: number;
        dokumentId: number;
        geschaeftsdokumentId: number;
        dokumentNummer: string;
        dateiname: string;
        dokumentDatum: string;
        gesamtbetrag: number;
        prozent: number;
        berechneterBetrag: number;
        beschreibung: string;
        lieferantId: number;
        lieferantName: string;
        pdfUrl: string;
        zugeordnetVonName?: string;
        zugeordnetAm?: string;
        alleZuordnungen?: EingangsrechnungAnteil[];
        dokumentenKette?: DokumentKetteRef[];
    }
    const [eingangsrechnungen, setEingangsrechnungen] = useState<Eingangsrechnung[]>([]);

    // Eingangsrechnungen laden
    const loadEingangsrechnungen = useCallback(async () => {
        if (projekt.id) {
            try {
                const res = await fetch(`/api/projekte/${projekt.id}/eingangsrechnungen`);
                if (res.ok) {
                    const data = await res.json();
                    setEingangsrechnungen(data);
                } else {
                    setEingangsrechnungen([]);
                }
            } catch {
                setEingangsrechnungen([]);
            }
        }
    }, [projekt.id]);

    useEffect(() => {
        loadEingangsrechnungen();
    }, [loadEingangsrechnungen]);

    // Projekt-Notizen State
    interface ProjektNotizBild {
        id: number;
        originalDateiname: string;
        url: string;
        erstelltAm: string;
    }
    interface ProjektNotiz {
        id: number;
        notiz: string;
        erstelltAm: string;
        mitarbeiterId: number;
        mitarbeiterVorname: string;
        mitarbeiterNachname: string;
        mobileSichtbar: boolean;
        nurFuerErsteller: boolean;
        canEdit?: boolean;
        bilder?: ProjektNotizBild[];
    }
    const [notizen, setNotizen] = useState<ProjektNotiz[]>([]);
    const [showNotizModal, setShowNotizModal] = useState(false);
    const [neueNotiz, setNeueNotiz] = useState('');
    const [mobileSichtbar, setMobileSichtbar] = useState(true);
    const [nurFuerErsteller, setNurFuerErsteller] = useState(false);
    const [editingNotiz, setEditingNotiz] = useState<ProjektNotiz | null>(null);
    const [savingNotiz, setSavingNotiz] = useState(false);
    const [uploadingNotizBildId, setUploadingNotizBildId] = useState<number | null>(null);
    const [notizBildViewer, setNotizBildViewer] = useState<{ images: { url: string; name?: string }[]; startIndex: number } | null>(null);

    // Ausgangs-Geschäftsdokumente State
    const [ausgangsDokumente, setAusgangsDokumente] = useState<AusgangsGeschaeftsDokument[]>([]);
    const [actionMenuDokument, setActionMenuDokument] = useState<AusgangsGeschaeftsDokument | null>(null);

    // Dateien (Dokumente) Anzahl
    const [dokumenteCount, setDokumenteCount] = useState(0);

    // Notizen laden
    const loadNotizen = useCallback(async () => {
        if (projekt.id) {
            try {
                const res = await fetch(`/api/projekte/${projekt.id}/notizen`);
                if (res.ok) {
                    setNotizen(await res.json());
                }
            } catch {
                setNotizen([]);
            }
        }
    }, [projekt.id]);

    useEffect(() => {
        loadNotizen();
    }, [loadNotizen]);

    // Ausgangs-Geschäftsdokumente laden
    const loadAusgangsDokumente = useCallback(async () => {
        if (projekt.id) {
            try {
                const res = await fetch(`/api/ausgangs-dokumente/projekt/${projekt.id}`);
                if (res.ok) {
                    setAusgangsDokumente(await res.json());
                }
            } catch {
                setAusgangsDokumente([]);
            }
        }
    }, [projekt.id]);

    useEffect(() => {
        loadAusgangsDokumente();
    }, [loadAusgangsDokumente]);

    // Dateien-Anzahl laden
    useEffect(() => {
        if (projekt.id) {
            fetch(`/api/projekte/${projekt.id}/dokumente`)
                .then(res => res.ok ? res.json() : [])
                .then((data: unknown[]) => setDokumenteCount(data.length))
                .catch(() => setDokumenteCount(0));
        }
    }, [projekt.id]);

    // Cross-tab: Geschäftsdokumente automatisch aktualisieren wenn im DocumentEditor gespeichert wird
    useEffect(() => {
        return onDokumentChanged((event) => {
            if (event.projektId === projekt.id) {
                loadAusgangsDokumente();
            }
        });
    }, [projekt.id, loadAusgangsDokumente]);

    // Notiz speichern (Neu oder Bearbeiten)
    const handleSaveNotiz = async () => {
        if (!neueNotiz.trim()) return;
        setSavingNotiz(true);
        try {
            const userProfileId = localStorage.getItem('frontendUserSelection');
            const profileData = userProfileId ? JSON.parse(userProfileId) : null;

            const url = editingNotiz
                ? `/api/projekte/${projekt.id}/notizen/${editingNotiz.id}`
                : `/api/projekte/${projekt.id}/notizen`;

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
                    nurFuerErsteller: nurFuerErsteller
                })
            });
            if (res.ok) {
                setNeueNotiz('');
                setMobileSichtbar(true);
                setNurFuerErsteller(false);
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

            const res = await fetch(`/api/projekte/${projekt.id}/notizen/${notizId}`, {
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
        setNurFuerErsteller(false);
        setEditingNotiz(null);
        setShowNotizModal(true);
    };

    const openEditNotizModal = (notiz: ProjektNotiz) => {
        setNeueNotiz(notiz.notiz);
        setMobileSichtbar(notiz.mobileSichtbar);
        setNurFuerErsteller(notiz.nurFuerErsteller);
        setEditingNotiz(notiz);
        setShowNotizModal(true);
    };

    // Notiz Bild Upload Handler
    const handleNotizBildUpload = async (notizId: number, file: File) => {
        setUploadingNotizBildId(notizId);
        try {
            const userProfileId = localStorage.getItem('frontendUserSelection');
            const profileData = userProfileId ? JSON.parse(userProfileId) : null;

            const formData = new FormData();
            formData.append('datei', file);

            const res = await fetch(`/api/projekte/${projekt.id}/notizen/${notizId}/bilder`, {
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

    // Notiz Bild Delete Handler
    const handleNotizBildDelete = async (notizId: number, bildId: number) => {
        if (!await confirmDialog({ title: 'Bild löschen', message: 'Bild wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const userProfileId = localStorage.getItem('frontendUserSelection');
            const profileData = userProfileId ? JSON.parse(userProfileId) : null;

            const res = await fetch(`/api/projekte/${projekt.id}/notizen/${notizId}/bilder/${bildId}`, {
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

    useEffect(() => {
        if (!showMaterialModal && !showSupplierPicker) return;

        const fetchSuppliers = async () => {
            try {
                const params = new URLSearchParams();
                params.set('size', '50');
                if (supplierSearchQuery) params.set('q', supplierSearchQuery);

                const res = await fetch(`/api/lieferanten?${params.toString()}`);
                const data = await res.json();

                // Fix: Backend returns { lieferanten: [...] }, not content
                const list = data.lieferanten || data.content || (Array.isArray(data) ? data : []);
                setSuppliers(list);
            } catch (err) {
                console.error("Failed to load suppliers", err);
            }
        };

        const timer = setTimeout(fetchSuppliers, 300);
        return () => clearTimeout(timer);
    }, [showMaterialModal, showSupplierPicker, supplierSearchQuery]);



    const formatCurrency = (val?: number) =>
        new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val || 0);

    const formatDate = (dateStr?: string) => {
        if (!dateStr) return '-';
        return new Date(dateStr).toLocaleDateString('de-DE');
    };

    const getLagerArtikelKey = (artikel: Artikel) => `${artikel.id}-${artikel.lieferantId ?? 'none'}`;

    const getVerrechnungseinheitName = (einheit?: Artikel['verrechnungseinheit']) => {
        if (!einheit) return 'STUECK';
        return typeof einheit === 'string' ? einheit : einheit.name;
    };

    const getVerrechnungseinheitLabel = (einheit?: Artikel['verrechnungseinheit']) => {
        if (!einheit) return 'Stück';
        if (typeof einheit === 'object') return einheit.anzeigename || einheit.name;
        if (einheit === 'STUECK') return 'Stück';
        if (einheit === 'LAUFENDE_METER') return 'Laufende Meter';
        if (einheit === 'QUADRATMETER') return 'Quadratmeter';
        if (einheit === 'KILOGRAMM') return 'Kilogramm';
        return einheit;
    };

    const mapEinheitForBackend = (einheit?: Artikel['verrechnungseinheit']) => {
        const name = getVerrechnungseinheitName(einheit);
        if (name === 'LAUFENDE_METER') return 'METER';
        return name;
    };

    const resetLagerArtikelSelection = () => {
        setSelectedLagerArtikelKeys(new Set());
        setSelectedLagerArtikelData({});
        setLagerArtikelMengen({});
        setLagerArtikelBeschaffung({});
        setLagerArtikelSearch('');
        setLagerArtikelPage(0);
        setLagerArtikelGesamt(0);
        setLagerArtikelError(null);
    };

    const loadLagerArtikel = useCallback(async ({ page, query }: { page: number; query: string }) => {
        const fetchId = ++lagerArtikelFetchSeq.current;

        setLoadingLagerArtikel(true);

        setLagerArtikelError(null);
        try {
            const params = new URLSearchParams();
            params.set('page', String(page));
            params.set('size', String(LAGER_ARTIKEL_PAGE_SIZE));
            params.set('sort', 'produktname');
            params.set('dir', 'asc');
            params.set('nurMitLieferantenpreis', 'true');
            if (query) {
                params.set('q', query);
            }

            const res = await fetch(`/api/artikel?${params.toString()}`);
            if (!res.ok) throw new Error('Artikel konnten nicht geladen werden');

            const data = await res.json();
            if (fetchId !== lagerArtikelFetchSeq.current) {
                return;
            }

            const list: Artikel[] = Array.isArray(data?.artikel) ? data.artikel : [];
            setLagerArtikel(list);

            const gesamt = typeof data?.gesamt === 'number' ? data.gesamt : 0;
            const aktuelleSeite = typeof data?.seite === 'number' ? data.seite : page;

            setLagerArtikelPage(aktuelleSeite);
            setLagerArtikelGesamt(gesamt);
        } catch (error) {
            console.error('Fehler beim Laden der Lagerartikel:', error);
            setLagerArtikel([]);
            setLagerArtikelGesamt(0);
            setLagerArtikelError('Lagerartikel konnten nicht geladen werden.');
        } finally {
            if (fetchId === lagerArtikelFetchSeq.current) {
                setLoadingLagerArtikel(false);
            }
        }
    }, []);

    useEffect(() => {
        if (!showLagerArtikelModal) return;
        setLagerArtikelPage(0);
    }, [showLagerArtikelModal, lagerArtikelSearch]);

    useEffect(() => {
        if (!showLagerArtikelModal) return;
        const query = lagerArtikelSearch.trim();
        const timer = setTimeout(() => {
            loadLagerArtikel({ page: lagerArtikelPage, query });
        }, 300);

        return () => clearTimeout(timer);
    }, [showLagerArtikelModal, lagerArtikelSearch, lagerArtikelPage, loadLagerArtikel]);

    const lagerArtikelTotalPages = Math.max(1, Math.ceil(lagerArtikelGesamt / LAGER_ARTIKEL_PAGE_SIZE));

    const lagerArtikelStatusText = useMemo(() => {
        if (loadingLagerArtikel) return 'Artikel werden geladen...';
        if (lagerArtikelGesamt === 0) return 'Keine Artikel gefunden.';

        const start = lagerArtikelPage * LAGER_ARTIKEL_PAGE_SIZE + 1;
        const end = Math.min(start + lagerArtikel.length - 1, lagerArtikelGesamt);
        return `Zeige ${start}-${end} von ${lagerArtikelGesamt} Artikeln`;
    }, [loadingLagerArtikel, lagerArtikelGesamt, lagerArtikelPage, lagerArtikel.length]);

    const handleToggleLagerArtikel = (artikel: Artikel, checked: boolean) => {
        const key = getLagerArtikelKey(artikel);
        setSelectedLagerArtikelKeys(prev => {
            const next = new Set(prev);
            if (checked) {
                next.add(key);
            } else {
                next.delete(key);
            }
            return next;
        });

        if (checked) {
            setLagerArtikelMengen(prev => {
                if (prev[key]) return prev;
                return { ...prev, [key]: '1' };
            });
            setSelectedLagerArtikelData(prev => ({ ...prev, [key]: artikel }));
            setLagerArtikelBeschaffung(prev => {
                if (prev[key]) return prev;
                return { ...prev, [key]: 'lager' };
            });
        } else {
            setSelectedLagerArtikelData(prev => {
                if (!prev[key]) return prev;
                const next = { ...prev };
                delete next[key];
                return next;
            });
            setLagerArtikelBeschaffung(prev => {
                if (!prev[key]) return prev;
                const next = { ...prev };
                delete next[key];
                return next;
            });
        }
    };

    const handleLagerMengeChange = (artikel: Artikel, value: string) => {
        const key = getLagerArtikelKey(artikel);
        setLagerArtikelMengen(prev => ({ ...prev, [key]: value }));

        const parsed = parseFloat(value.replace(',', '.'));
        setSelectedLagerArtikelKeys(prev => {
            const next = new Set(prev);
            if (!Number.isNaN(parsed) && parsed > 0) {
                next.add(key);
            } else {
                next.delete(key);
            }
            return next;
        });

        setSelectedLagerArtikelData(prev => {
            const next = { ...prev };
            if (!Number.isNaN(parsed) && parsed > 0) {
                next[key] = artikel;
            } else {
                delete next[key];
            }
            return next;
        });

        setLagerArtikelBeschaffung(prev => {
            if (!Number.isNaN(parsed) && parsed > 0) {
                if (prev[key]) return prev;
                return { ...prev, [key]: 'lager' };
            }
            if (!prev[key]) return prev;
            const next = { ...prev };
            delete next[key];
            return next;
        });
    };

    const handleLagerBeschaffungChange = (artikel: Artikel, value: string) => {
        const key = getLagerArtikelKey(artikel);
        const beschaffung = value === 'bestellen' ? 'bestellen' : 'lager';
        setLagerArtikelBeschaffung(prev => ({ ...prev, [key]: beschaffung }));
    };

    const handleSaveLagerArtikel = async () => {
        if (selectedLagerArtikelKeys.size === 0) {
            toast.error('Bitte wählen Sie mindestens einen Artikel aus.');
            return;
        }

        const payload = Array.from(selectedLagerArtikelKeys)
            .map((key) => {
                const artikel = selectedLagerArtikelData[key];
                if (!artikel) return null;
                const rawMenge = (lagerArtikelMengen[key] || '1').replace(',', '.');
                const menge = parseFloat(rawMenge);
                return {
                    artikelId: artikel.id,
                    lieferantId: artikel.lieferantId,
                    preis: artikel.preis,
                    menge,
                    einheit: mapEinheitForBackend(artikel.verrechnungseinheit),
                    ausLager: (lagerArtikelBeschaffung[key] ?? 'lager') === 'lager',
                };
            })
            .filter((item): item is NonNullable<typeof item> => item !== null);

        if (payload.length === 0) {
            toast.error('Die gewählten Artikel sind nicht mehr verfügbar. Bitte erneut auswählen.');
            return;
        }

        if (payload.some(p => Number.isNaN(p.menge) || p.menge <= 0)) {
            toast.error('Bitte geben Sie für alle gewählten Artikel eine Menge größer als 0 ein.');
            return;
        }

        setSavingLagerArtikel(true);
        try {
            const res = await fetch(`/api/projekte/${projekt.id}/materialkosten/artikel`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            if (res.ok) {
                toast.success(`${payload.length} Artikel zum Projekt hinzugefügt.`);
                setShowLagerArtikelModal(false);
                resetLagerArtikelSelection();
                await onRefresh();
            } else {
                toast.error('Artikel konnten nicht hinzugefügt werden.');
            }
        } catch (error) {
            console.error('Fehler beim Hinzufügen von Artikeln:', error);
            toast.error('Fehler beim Hinzufügen von Artikeln.');
        } finally {
            setSavingLagerArtikel(false);
        }
    };

    const handleSaveMaterial = async () => {
        if (!newMaterial.beschreibung || !newMaterial.betrag) return;
        setSavingMaterial(true);
        try {
            const betrag = parseFloat(newMaterial.betrag.replace(',', '.'));
            const payload = [{
                beschreibung: newMaterial.beschreibung,
                betrag: betrag,
                monat: new Date().getMonth() + 1, // Default to current month
                lieferantId: newMaterial.lieferantId ? parseInt(newMaterial.lieferantId) : null,
                rechnungsnummer: newMaterial.rechnungsnummer
            }];

            const res = await fetch(`/api/projekte/${projekt.id}/materialkosten`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                setShowMaterialModal(false);
                setNewMaterial({ beschreibung: '', betrag: '', rechnungsnummer: '' });
                // Trigger refresh if possible, or just notify user
                // Ideally, we callback to parent to reload project
                onBack(); // Simple workaround: go back to list to force reload on re-entry (or we could implement a reload callback)
                // Better: onEdit() effectively reloads? No. 
                // We'll modify the props to accept onRefresh?
                // For now, reload via window.location.reload() is too harsh.
                // Let's rely on the user navigating or implementing a proper refresh later.
                // Or simply: 
                window.location.reload();
            }
        } catch (error) {
            console.error(error);
        } finally {
            setSavingMaterial(false);
        }
    };

    // Calculate totals
    const arbeitskosten = useMemo(() => {
        if (!projekt.zeiten) return 0;
        return projekt.zeiten.reduce((sum, z) => sum + ((z.anzahlInStunden ?? 0) * (z.stundensatz ?? 0)), 0);
    }, [projekt.zeiten]);

    const materialkostenSum = useMemo(() => {
        if (!projekt.materialkosten) return 0;
        return projekt.materialkosten.reduce((sum, m) => sum + (m.betrag ?? 0), 0);
    }, [projekt.materialkosten]);

    const artikelkosten = useMemo(() => {
        if (!projekt.artikel) return 0;
        return projekt.artikel.reduce((sum, a) => sum + (a.gesamtpreis || 0), 0);
    }, [projekt.artikel]);

    // Eingangsrechnungen-Summe (zugeordnete Lieferantenrechnungen)
    const eingangsrechnungenSum = useMemo(() => {
        return eingangsrechnungen.reduce((sum, er) => sum + (er.berechneterBetrag ?? 0), 0);
    }, [eingangsrechnungen]);

    // Gesamte Materialkosten inkl. Eingangsrechnungen
    const gesamtMaterialkosten = materialkostenSum + artikelkosten + eingangsrechnungenSum;

    const nettoPreis = useMemo(() => {
        return (projekt.bruttoPreis || 0) / 1.19;
    }, [projekt.bruttoPreis]);

    // Gewinn = Nettopreis - Arbeitskosten - Materialkosten/Artikel/Eingangsrechnungen
    const gewinn = useMemo(() => {
        return nettoPreis - arbeitskosten - gesamtMaterialkosten;
    }, [nettoPreis, arbeitskosten, gesamtMaterialkosten]);

    const kundeDto = projekt.kundeDto;
    const kundenEmails = (() => {
        const emails: string[] = [];
        if (projekt.kundenEmails) projekt.kundenEmails.forEach(e => { if (e && !emails.includes(e)) emails.push(e); });
        if (kundeDto?.kundenEmails) kundeDto.kundenEmails.forEach(e => { if (e && !emails.includes(e)) emails.push(e); });
        return emails;
    })();
    const adresse = [projekt.strasse || kundeDto?.strasse, projekt.plz || kundeDto?.plz, projekt.ort || kundeDto?.ort]
        .filter(Boolean).join(', ');

    const header = (
        <Card className="p-6">
            <div className="flex flex-col xl:flex-row gap-8 justify-between">
                <div className="flex items-start gap-4">
                    <Button variant="ghost" size="sm" onClick={onBack} className="-ml-2 h-auto py-1 self-start">
                        <ArrowLeft className="w-5 h-5" />
                    </Button>
                    <div className="w-16 h-16 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center text-xl font-bold shrink-0">
                        <Briefcase className="w-8 h-8" />
                    </div>
                    <div>
                        <div className="flex items-center gap-3 flex-wrap">
                            <h1 className="text-2xl font-bold text-slate-900">{projekt.bauvorhaben}</h1>
                            <span className={cn(
                                "px-2.5 py-0.5 rounded-full text-xs font-medium border",
                                projekt.bezahlt
                                    ? "bg-green-50 text-green-700 border-green-200"
                                    : "bg-amber-50 text-amber-700 border-amber-200"
                            )}>
                                {projekt.bezahlt ? 'Bezahlt' : 'Offen'}
                            </span>
                        </div>
                        <div className="mt-1 text-slate-500 space-y-0.5">
                            {projekt.kunde && <p className="flex items-center gap-2"><User className="w-4 h-4" /> {projekt.kunde}</p>}
                            {adresse && <p className="flex items-center gap-2"><MapPin className="w-4 h-4" /> {adresse}</p>}
                            {projekt.auftragsnummer && <p className="flex items-center gap-2"><FileText className="w-4 h-4" /> {projekt.auftragsnummer}</p>}
                        </div>
                    </div>
                </div>

                {/* Stats Row */}
                <div className="flex items-center gap-6 flex-1 max-w-4xl">
                    <div className="flex flex-col items-center px-4 py-2 border-r border-slate-200 last:border-r-0">
                        <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Brutto</p>
                        <p className="text-base font-semibold text-slate-800">{formatCurrency(projekt.bruttoPreis)}</p>
                    </div>
                    <div className="flex flex-col items-center px-4 py-2 border-r border-slate-200 last:border-r-0">
                        <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Netto</p>
                        <p className="text-base font-semibold text-slate-800">{formatCurrency(nettoPreis)}</p>
                    </div>
                    <div className="flex flex-col items-center px-4 py-2 border-r border-slate-200 last:border-r-0">
                        <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Arbeitskosten</p>
                        <p className="text-base font-semibold text-slate-800">{formatCurrency(arbeitskosten)}</p>
                    </div>
                    <div className="flex flex-col items-center px-4 py-2 border-r border-slate-200 last:border-r-0">
                        <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Material</p>
                        <p className="text-base font-semibold text-slate-800">{formatCurrency(gesamtMaterialkosten)}</p>
                    </div>
                    <div className="flex flex-col items-center px-4 py-2">
                        <p className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Gewinn</p>
                        <p className={cn("text-base font-semibold", gewinn >= 0 ? 'text-green-600' : 'text-red-600')}>{formatCurrency(gewinn)}</p>
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

    // Supplier Picker Dialog
    const supplierPickerDialog = (
        <Dialog open={showSupplierPicker} onOpenChange={setShowSupplierPicker}>
            <DialogContent className="sm:max-w-md">
                <DialogHeader>
                    <DialogTitle>Lieferant auswählen</DialogTitle>
                </DialogHeader>
                <div className="space-y-4 py-4">
                    <div className="relative">
                        <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-500" />
                        <Input
                            placeholder="Suchen..."
                            className="pl-9"
                            value={supplierSearchQuery}
                            onChange={e => setSupplierSearchQuery(e.target.value)}
                            autoFocus
                        />
                    </div>
                    <div className="h-[300px] overflow-y-auto border rounded-md divide-y divide-slate-100">
                        {suppliers.length === 0 ? (
                            <div className="p-4 text-center text-slate-500 text-sm">Keine Lieferanten gefunden.</div>
                        ) : (
                            suppliers
                                .map(s => (
                                    <div
                                        key={s.id}
                                        className="p-3 hover:bg-rose-50 cursor-pointer transition-colors text-sm"
                                        onClick={() => {
                                            setNewMaterial(prev => ({ ...prev, lieferantId: s.id.toString() }));
                                            setShowSupplierPicker(false);
                                        }}
                                    >
                                        {s.lieferantenname}
                                    </div>
                                ))
                        )}
                    </div>
                </div>
                <DialogFooter>
                    <Button variant="ghost" onClick={() => setNewMaterial(prev => ({ ...prev, lieferantId: undefined }))} className="mr-auto text-rose-600 hover:text-rose-700 hover:bg-rose-50">
                        Auswahl entfernen
                    </Button>
                    <Button variant="outline" onClick={() => setShowSupplierPicker(false)}>Abbrechen</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );

    const mainContent = (
        <>
            {/* Tab Navigation */}
            <div className="flex gap-2 mb-6 border-b border-slate-200 pb-2 overflow-x-auto">
                <button
                    onClick={() => setActiveTab('zeiten')}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap",
                        activeTab === 'zeiten'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <Clock className="w-4 h-4 inline-block mr-2" />
                    Zeiten ({projekt.zeiten?.length || 0})
                </button>
                <button
                    onClick={() => setActiveTab('materialkosten')}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition whitespace-nowrap",
                        activeTab === 'materialkosten'
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <Euro className="w-4 h-4 inline-block mr-2" />
                    Materialkosten ({projekt.materialkosten?.length || 0})
                </button>
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
                    E-Mails ({projekt.emails?.length || 0})
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
                    Ein-/ Ausgangsgeschäftsdokumente ({ausgangsDokumente.length + eingangsrechnungen.length})
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
                    Dateien ({dokumenteCount})
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

            {/* Tab Content */}
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
                                                        <span className="text-[10px] bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded border border-slate-200" title="Auf Mobile App nicht sichtbar">
                                                            Mobile ausgeblendet
                                                        </span>
                                                    )}
                                                </div>
                                                <p className="text-xs text-slate-500">
                                                    {new Date(n.erstelltAm).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })} {new Date(n.erstelltAm).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}
                                                </p>
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
                                                <RefreshCw className="w-4 h-4 animate-spin" />
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

            {/* Notiz Bild Viewer Modal */}
            {/* Notiz Bild Viewer Modal */}
            <ImageViewer
                src={notizBildViewer ? notizBildViewer.images[notizBildViewer.startIndex]?.url : null}
                onClose={() => setNotizBildViewer(null)}
                alt="Notiz-Bild"
                images={notizBildViewer?.images}
                startIndex={notizBildViewer?.startIndex}
            />

            {activeTab === 'beschreibung' && (
                <div className="space-y-4">
                    <textarea
                        className="flex min-h-[300px] w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-600 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                        placeholder="Kurzbeschreibung des Projekts..."
                        value={kurzbeschreibung}
                        onChange={(e) => setKurzbeschreibung(e.target.value)}
                    />
                    <div className="flex justify-end">
                        <Button
                            onClick={handleSaveDescription}
                            disabled={savingDesc || kurzbeschreibung === (projekt.kurzbeschreibung || '')}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {savingDesc ? <RefreshCw className="w-4 h-4 animate-spin mr-2" /> : <FileText className="w-4 h-4 mr-2" />}
                            Speichern
                        </Button>
                    </div>
                </div>
            )}

            {activeTab === 'zeiten' && (
                <div className="space-y-3">
                    {projekt.zeiten && projekt.zeiten.length > 0 ? (
                        (() => {
                            // Helper Types for nested structure
                            type EmployeeGroup = { name: string; hours: number; cost: number };
                            type ActivityGroup = { name: string; employees: EmployeeGroup[]; totalHours: number; totalCost: number };
                            type CategoryGroup = { name: string; activities: ActivityGroup[]; totalHours: number; totalCost: number };

                            const categoryMap = new Map<string, {
                                name: string;
                                activities: Map<string, {
                                    name: string;
                                    employees: Map<string, EmployeeGroup>
                                }>
                            }>();

                            // 1. Group Data
                            projekt.zeiten.forEach(z => {
                                const catName = z.produktkategorie?.bezeichnung || 'Ohne Kategorie';
                                const actName = z.arbeitsgangBeschreibung || 'Sonstige';
                                const mitarbeiter = z.mitarbeiterVorname && z.mitarbeiterNachname
                                    ? `${z.mitarbeiterVorname} ${z.mitarbeiterNachname}`
                                    : 'Unbekannt';

                                if (!categoryMap.has(catName)) {
                                    categoryMap.set(catName, { name: catName, activities: new Map() });
                                }
                                const cat = categoryMap.get(catName)!;

                                if (!cat.activities.has(actName)) {
                                    cat.activities.set(actName, { name: actName, employees: new Map() });
                                }
                                const act = cat.activities.get(actName)!;

                                if (!act.employees.has(mitarbeiter)) {
                                    act.employees.set(mitarbeiter, { name: mitarbeiter, hours: 0, cost: 0 });
                                }
                                const emp = act.employees.get(mitarbeiter)!;

                                emp.hours += (z.anzahlInStunden ?? 0);
                                emp.cost += ((z.anzahlInStunden ?? 0) * (z.stundensatz ?? 0));
                            });

                            // 2. Convert to Sorted Array with Totals
                            const categories: CategoryGroup[] = Array.from(categoryMap.values()).map(cat => {
                                const activities: ActivityGroup[] = Array.from(cat.activities.values()).map(act => {
                                    const employees = Array.from(act.employees.values()).sort((a, b) => a.name.localeCompare(b.name));
                                    const totalHours = employees.reduce((sum, e) => sum + e.hours, 0);
                                    const totalCost = employees.reduce((sum, e) => sum + e.cost, 0);
                                    return {
                                        name: act.name,
                                        employees,
                                        totalHours,
                                        totalCost
                                    };
                                }).sort((a, b) => a.name.localeCompare(b.name));

                                const totalHours = activities.reduce((sum, a) => sum + a.totalHours, 0);
                                const totalCost = activities.reduce((sum, a) => sum + a.totalCost, 0);

                                return {
                                    name: cat.name,
                                    activities,
                                    totalHours,
                                    totalCost
                                };
                            }).sort((a, b) => {
                                if (a.name === 'Ohne Kategorie') return 1;
                                if (b.name === 'Ohne Kategorie') return -1;
                                return a.name.localeCompare(b.name);
                            });

                            // 3. Render
                            return (
                                <div className="space-y-4">
                                    {categories.map((cat, catIdx) => (
                                        <div key={catIdx} className="border border-slate-200 rounded-lg overflow-hidden">
                                            {/* Level 1: Product Category */}
                                            <div className="bg-slate-50 p-3 border-b border-slate-200 flex justify-between items-center">
                                                <div className="flex items-center gap-2">
                                                    <FolderOpen className="w-4 h-4 text-slate-400" />
                                                    <span className="font-semibold text-slate-900">{cat.name}</span>
                                                </div>
                                                <div className="text-right text-sm">
                                                    <span className="font-medium text-slate-900 mx-3">{cat.totalHours.toFixed(2)} h</span>
                                                    <span className="text-slate-500">{formatCurrency(cat.totalCost)}</span>
                                                </div>
                                            </div>

                                            <div className="divide-y divide-slate-100 bg-white">
                                                {cat.activities.map((act, actIdx) => (
                                                    <div key={actIdx} className="p-3 pl-8">
                                                        {/* Level 2: Activity */}
                                                        <div className="flex justify-between items-center mb-2">
                                                            <div className="flex items-center gap-2">
                                                                <Hammer className="w-3_5 h-3_5 text-rose-500" /> {/* Using Hammer as icon for activity */}
                                                                <span className="font-medium text-slate-800">{act.name}</span>
                                                            </div>
                                                            <div className="text-right text-xs text-slate-500">
                                                                <span className="font-medium mx-3">{act.totalHours.toFixed(2)} h</span>
                                                                <span>{formatCurrency(act.totalCost)}</span>
                                                            </div>
                                                        </div>

                                                        {/* Level 3: Employees */}
                                                        <div className="space-y-1 pl-6 border-l-2 border-slate-100 ml-1.5">
                                                            {act.employees.map((emp, empIdx) => (
                                                                <div key={empIdx} className="flex justify-between items-center text-sm py-0.5">
                                                                    <div className="flex items-center gap-2 text-slate-600">
                                                                        <User className="w-3 h-3 text-slate-400" />
                                                                        <span>{emp.name}</span>
                                                                    </div>
                                                                    <div className="text-right text-slate-600">
                                                                        <span className="font-medium mx-3">{emp.hours.toFixed(2)} h</span>
                                                                        <span className="text-slate-400 text-xs">{formatCurrency(emp.cost)}</span>
                                                                    </div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            );
                        })()
                    ) : (
                        <p className="text-slate-500 text-center py-6">Keine Zeiten erfasst.</p>
                    )}
                </div>
            )}

            {activeTab === 'materialkosten' && (
                <div className="space-y-6">
                    {/* Manuell erfasste Materialkosten */}
                    <div className="space-y-3">
                        <div className="flex justify-between items-center mb-2">
                            <h4 className="text-sm font-medium text-slate-500 uppercase tracking-wide">Manuell erfasste Materialkosten</h4>
                            <div className="flex items-center gap-2">
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => setShowLagerArtikelModal(true)}
                                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                >
                                    <Package className="w-4 h-4 mr-2" /> Artikel aus Lager
                                </Button>
                                <Button size="sm" onClick={() => setShowMaterialModal(true)} className="bg-rose-600 text-white hover:bg-rose-700">
                                    <Plus className="w-4 h-4 mr-2" /> Kosten erfassen
                                </Button>
                            </div>
                        </div>

                        {projekt.materialkosten && projekt.materialkosten.length > 0 ? (
                            projekt.materialkosten.map((m) => (
                                <div key={m.id} className="flex items-center justify-between p-3 bg-white rounded-lg border border-slate-100">
                                    <div>
                                        <p className="font-medium text-slate-900">{m.beschreibung}</p>
                                        {m.rechnungsnummer && <p className="text-xs text-slate-500">Rech-Nr: {m.rechnungsnummer}</p>}
                                    </div>
                                    <p className="font-semibold text-slate-900">{formatCurrency(m.betrag)}</p>
                                </div>
                            ))
                        ) : (
                            <p className="text-slate-500 text-center py-4">Keine manuell erfassten Materialkosten.</p>
                        )}
                    </div>

                    <div className="space-y-3">
                        <h4 className="text-sm font-medium text-slate-500 uppercase tracking-wide">Artikel aus Lager</h4>
                        {projekt.artikel && projekt.artikel.length > 0 ? (
                            projekt.artikel.map((a) => (
                                <div key={a.id} className="flex items-center justify-between p-3 bg-white rounded-lg border border-slate-100">
                                    <div>
                                        <p className="font-medium text-slate-900">{a.produktname || a.beschreibung || 'Artikel'}</p>
                                        <p className="text-xs text-slate-500 mt-0.5">
                                            {a.externeArtikelnummer ? `Nr. ${a.externeArtikelnummer} · ` : ''}
                                            {a.lieferantName ? `${a.lieferantName} · ` : ''}
                                            {a.stueckzahl ? `${a.stueckzahl} Stück` : a.meter ? `${a.meter} m` : a.kilogramm ? `${a.kilogramm} kg` : '-'}
                                        </p>
                                    </div>
                                    <p className="font-semibold text-slate-900">{formatCurrency(a.preisProStueck ?? a.gesamtpreis ?? 0)}</p>
                                </div>
                            ))
                        ) : (
                            <p className="text-slate-500 text-center py-4">Keine Artikel aus dem Lager im Projekt.</p>
                        )}
                    </div>

                    {/* Hinweis: Eingangsrechnungen-Summe fließt weiterhin in die Nachkalkulation ein (siehe gesamtMaterialkosten) */}
                    {eingangsrechnungen.length > 0 && (
                        <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm text-amber-800">
                            <span className="font-medium">Hinweis:</span> {eingangsrechnungen.length} Eingangsrechnung{eingangsrechnungen.length !== 1 ? 'en' : ''} ({formatCurrency(eingangsrechnungenSum)}) — siehe Tab &quot;Ein-/ Ausgangsgeschäftsdokumente&quot;
                        </div>
                    )}
                </div>
            )}

            {activeTab === 'emails' && (
                <EmailsTab
                    emails={(projekt.emails || []).map(e => ({
                        ...e,
                        parentEmailId: e.parentEmailId ?? e.parentId,
                    }))}
                    projektId={projekt.id}
                    entityName={projekt.bauvorhaben}
                    kundenEmail={projekt.kundeDto?.kundenEmails?.[0]}
                    projekt={projekt}
                    onEmailSent={() => onRefresh()}
                />
            )}

            {activeTab === 'geschaeftsdokumente' && (
                <div className="space-y-6">
                    {/* Ausgangs-Geschäftsdokumente */}
                    <div>
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="text-lg font-medium text-slate-900">Ausgangsgeschäftsdokumente</h3>
                            <div className="flex items-center gap-3">
                                <span className="text-sm text-slate-500">{ausgangsDokumente.length} Dokument{ausgangsDokumente.length !== 1 ? 'e' : ''}</span>
                                <Button
                                    size="sm"
                                    className="bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50 disabled:cursor-not-allowed"
                                    onClick={() => setShowDokumentTypDialog(true)}
                                    disabled={ausgangsDokumente.some(d => !d.vorgaengerId)}
                                    title={ausgangsDokumente.some(d => !d.vorgaengerId) ? 'Es existiert bereits ein Basisdokument' : undefined}
                                >
                                    <Plus className="w-4 h-4 mr-1" /> Dokument erstellen
                                </Button>
                            </div>
                        </div>
                        {ausgangsDokumente.length > 0 ? (
                            <div className="space-y-1">
                                {flattenTree(buildDokumentTree(ausgangsDokumente)).map((node) => {
                                    const dok = node.dokument;
                                    const typConfig = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dok.typ);
                                    const hasChildren = node.children.length > 0;
                                    const isChild = node.depth > 0;

                                    return (
                                        <div
                                            key={dok.id}
                                            className="relative"
                                            style={{ paddingLeft: node.depth * 32 }}
                                        >
                                            {/* Einrückungs-Linie für Kinder */}
                                            {isChild && (
                                                <div className="absolute left-0 top-0 bottom-0 flex items-center" style={{ left: (node.depth - 1) * 32 + 14 }}>
                                                    <CornerDownRight className="w-4 h-4 text-slate-300" />
                                                </div>
                                            )}

                                            <div
                                                className={cn(
                                                    "bg-white rounded-xl border p-4 hover:shadow-md transition-all cursor-pointer group relative",
                                                    isChild ? "border-slate-200 hover:border-slate-300" : "border-slate-200 hover:border-rose-300",
                                                    dok.storniert && "opacity-50"
                                                )}
                                                onClick={() => setActionMenuDokument(actionMenuDokument?.id === dok.id ? null : dok)}
                                                onDoubleClick={() => window.open(`/dokument-editor?projektId=${projekt.id}&dokumentId=${dok.id}`, '_blank')}
                                                title="Klick für Aktionen, Doppelklick zum Öffnen"
                                            >
                                                <div className="flex items-start justify-between gap-4">
                                                    <div className="flex-1 min-w-0">
                                                        <div className="flex items-center gap-2 mb-1 flex-wrap">
                                                            <span className={cn(
                                                                "text-xs font-medium px-2 py-0.5 rounded-full border",
                                                                TYP_COLORS[dok.typ] || 'bg-slate-50 text-slate-700 border-slate-200'
                                                            )}>
                                                                {typConfig?.label || dok.typ}
                                                            </span>
                                                            {dok.storniert && (
                                                                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-red-100 text-red-700 border border-red-200">
                                                                    Storniert
                                                                </span>
                                                            )}
                                                            {!dok.storniert && dok.gebucht && (
                                                                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-green-100 text-green-700 border border-green-200">
                                                                    <Lock className="w-3 h-3 inline-block mr-1" />
                                                                    Gebucht
                                                                </span>
                                                            )}
                                                            {!dok.storniert && !dok.gebucht && (
                                                                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 border border-amber-200">
                                                                    Entwurf
                                                                </span>
                                                            )}
                                                            {isChild && dok.vorgaengerNummer && (
                                                                <span className="text-[10px] text-slate-400">
                                                                    aus {dok.vorgaengerNummer}
                                                                </span>
                                                            )}
                                                            {hasChildren && (
                                                                <span className="text-[10px] text-slate-400 flex items-center gap-0.5">
                                                                    <ChevronDown className="w-3 h-3" />
                                                                    {node.children.length} Folgedokument{node.children.length > 1 ? 'e' : ''}
                                                                </span>
                                                            )}
                                                        </div>
                                                        <p className="font-semibold text-slate-900 group-hover:text-rose-700 transition-colors">
                                                            {dok.dokumentNummer}
                                                        </p>
                                                        {dok.betreff && (
                                                            <p className="text-sm text-slate-600 truncate mt-0.5">{dok.betreff}</p>
                                                        )}
                                                        <div className="flex items-center gap-4 mt-2 text-xs text-slate-400">
                                                            <span>
                                                                <Calendar className="w-3 h-3 inline-block mr-1" />
                                                                {new Date(dok.datum).toLocaleDateString('de-DE')}
                                                            </span>
                                                            {dok.kundenName && (
                                                                <span>
                                                                    <User className="w-3 h-3 inline-block mr-1" />
                                                                    {dok.kundenName}
                                                                </span>
                                                            )}
                                                            {dok.erstelltVonName && (
                                                                <span title="Erstellt von">
                                                                    <Edit2 className="w-3 h-3 inline-block mr-1" />
                                                                    {dok.erstelltVonName}
                                                                </span>
                                                            )}
                                                        </div>
                                                    </div>
                                                    <div className="text-right shrink-0">
                                                        {dok.betragNetto !== undefined && dok.betragNetto !== null ? (
                                                            <>
                                                                <p className="text-lg font-bold text-slate-900">
                                                                    {formatCurrency(dok.betragNetto)}
                                                                </p>
                                                                <p className="text-xs text-slate-400">netto</p>
                                                                {dok.betragBrutto !== undefined && dok.betragBrutto !== null && (
                                                                    <>
                                                                        <p className="text-sm font-semibold text-slate-600 mt-0.5">
                                                                            {formatCurrency(dok.betragBrutto)}
                                                                        </p>
                                                                        <p className="text-xs text-slate-400">brutto</p>
                                                                    </>
                                                                )}
                                                            </>
                                                        ) : null}
                                                    </div>
                                                </div>

                                                {/* Aktionsmenü bei Klick */}
                                                {actionMenuDokument?.id === dok.id && (
                                                    <div
                                                        className="absolute right-4 top-4 bg-white rounded-lg shadow-xl border border-slate-200 py-2 z-20 min-w-[220px]"
                                                        onClick={(e) => e.stopPropagation()}
                                                    >
                                                        <button
                                                            className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-rose-50 hover:text-rose-700 flex items-center gap-2"
                                                            onClick={() => {
                                                                window.open(`/dokument-editor?projektId=${projekt.id}&dokumentId=${dok.id}`, '_blank');
                                                                setActionMenuDokument(null);
                                                            }}
                                                        >
                                                            <FileText className="w-4 h-4" />
                                                            Öffnen (neuer Tab)
                                                        </button>

                                                        {/* Umwandeln - nur für bestimmte Typen */}
                                                        {(dok.typ === 'ANGEBOT' || dok.typ === 'AUFTRAGSBESTAETIGUNG') && (
                                                            <>
                                                                <hr className="my-1 border-slate-100" />
                                                                <p className="px-4 py-1 text-xs text-slate-400 font-medium">Folgedokument erstellen:</p>
                                                                {dok.typ === 'ANGEBOT' && (
                                                                    <button
                                                                        className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-purple-50 hover:text-purple-700 flex items-center gap-2"
                                                                        onClick={async () => {
                                                                            if (await confirmDialog({ title: "Auftragsbestätigung erstellen", message: `Auftragsbestätigung basierend auf ${dok.dokumentNummer} erstellen?`, variant: "info", confirmLabel: "Erstellen" })) {
                                                                                try {
                                                                                    const response = await fetch('/api/ausgangs-dokumente', {
                                                                                        method: 'POST',
                                                                                        headers: { 'Content-Type': 'application/json' },
                                                                                        body: JSON.stringify({
                                                                                            typ: 'AUFTRAGSBESTAETIGUNG',
                                                                                            projektId: projekt.id,
                                                                                            vorgaengerId: dok.id,
                                                                                            betreff: dok.betreff,
                                                                                            betragNetto: dok.betragNetto
                                                                                        })
                                                                                    });
                                                                                    if (response.ok) {
                                                                                        const newDoc = await response.json();
                                                                                        loadAusgangsDokumente();
                                                                                        window.open(`/dokument-editor?projektId=${projekt.id}&dokumentId=${newDoc.id}`, '_blank');
                                                                                    }
                                                                                } catch (e) {
                                                                                    console.error(e);
                                                                                }
                                                                            }
                                                                            setActionMenuDokument(null);
                                                                        }}
                                                                    >
                                                                        <FileText className="w-4 h-4" />
                                                                        Auftragsbestätigung
                                                                    </button>
                                                                )}
                                                                <button
                                                                    className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-rose-50 hover:text-rose-700 flex items-center gap-2"
                                                                    onClick={async () => {
                                                                        // Menü sofort schließen
                                                                        setActionMenuDokument(null);
                                                                        // Abrechnungsverlauf und Positionen laden
                                                                        setRechnungBasisDok(dok);
                                                                        setRechnungTyp('RECHNUNG');
                                                                        setAbschlagsBetrag('');
                                                                        setAbschlagsEingabeModus('netto');
                                                                        setRechnungLoading(true);
                                                                        setShowRechnungDialog(true);
                                                                        setBasisDokBlocks([]);
                                                                        setSelectedBlockIds(new Set());
                                                                        setExpandedBlockIds(new Set());
                                                                        setBereitsAbgerechneteBlockIds(new Set());
                                                                        try {
                                                                            const [verlaufRes, dokRes] = await Promise.all([
                                                                                fetch(`/api/ausgangs-dokumente/${dok.id}/abrechnungsverlauf`),
                                                                                fetch(`/api/ausgangs-dokumente/${dok.id}`)
                                                                            ]);
                                                                            if (verlaufRes.ok) {
                                                                                const verlaufData = await verlaufRes.json();
                                                                                setAbrechnungsverlauf(verlaufData);
                                                                                if (verlaufData.bereitsAbgerechneteBlockIds) {
                                                                                    setBereitsAbgerechneteBlockIds(new Set(verlaufData.bereitsAbgerechneteBlockIds));
                                                                                }
                                                                                // Bereits abgerechnete Block-IDs als Set für schnelle Prüfung
                                                                                const abgerechneteIds = new Set<string>(verlaufData.bereitsAbgerechneteBlockIds || []);

                                                                                if (dokRes.ok) {
                                                                                    const dokData = await dokRes.json();
                                                                                    if (dokData.positionenJson) {
                                                                                        try {
                                                                                            const parsed = JSON.parse(dokData.positionenJson);
                                                                                            const blocks: DocBlock[] = Array.isArray(parsed) ? parsed : (parsed.blocks || []);
                                                                                            setBasisDokBlocks(blocks);
                                                                                            // Nur noch nicht abgerechnete Positionen vorauswählen
                                                                                            const allIds = new Set<string>();
                                                                                            for (const b of blocks) {
                                                                                                if (b.type === 'SERVICE' && !abgerechneteIds.has(b.id)) allIds.add(b.id);
                                                                                                if (b.type === 'SECTION_HEADER' && b.children) {
                                                                                                    for (const c of b.children) {
                                                                                                        if (c.type === 'SERVICE' && !abgerechneteIds.has(c.id)) allIds.add(c.id);
                                                                                                    }
                                                                                                }
                                                                                            }
                                                                                            setSelectedBlockIds(allIds);
                                                                                        } catch { /* ignore parse error */ }
                                                                                    }
                                                                                }
                                                                            } else {
                                                                                setAbrechnungsverlauf(null);
                                                                                if (dokRes.ok) {
                                                                                    const dokData = await dokRes.json();
                                                                                    if (dokData.positionenJson) {
                                                                                        try {
                                                                                            const parsed = JSON.parse(dokData.positionenJson);
                                                                                            const blocks: DocBlock[] = Array.isArray(parsed) ? parsed : (parsed.blocks || []);
                                                                                            setBasisDokBlocks(blocks);
                                                                                            const allIds = new Set<string>();
                                                                                            for (const b of blocks) {
                                                                                                if (b.type === 'SERVICE') allIds.add(b.id);
                                                                                                if (b.type === 'SECTION_HEADER' && b.children) {
                                                                                                    for (const c of b.children) {
                                                                                                        if (c.type === 'SERVICE') allIds.add(c.id);
                                                                                                    }
                                                                                                }
                                                                                            }
                                                                                            setSelectedBlockIds(allIds);
                                                                                        } catch { /* ignore parse error */ }
                                                                                    }
                                                                                }
                                                                            }
                                                                        } catch {
                                                                            setAbrechnungsverlauf(null);
                                                                        } finally {
                                                                            setRechnungLoading(false);
                                                                        }
                                                                    }}
                                                                >
                                                                    <Receipt className="w-4 h-4" />
                                                                    Rechnung erstellen
                                                                </button>
                                                            </>
                                                        )}

                                                        {/* Stornieren - nur für gebuchte Rechnungstypen die nicht schon storniert sind */}
                                                        {dok.gebucht && !dok.storniert && ['RECHNUNG', 'TEILRECHNUNG', 'ABSCHLAGSRECHNUNG', 'SCHLUSSRECHNUNG'].includes(dok.typ) && (
                                                            <>
                                                                <hr className="my-1 border-slate-100" />
                                                                <button
                                                                    className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center gap-2"
                                                                    onClick={async () => {
                                                                        setActionMenuDokument(null);
                                                                        if (await confirmDialog({
                                                                            title: 'Rechnung stornieren',
                                                                            message: `Möchten Sie ${dok.dokumentNummer} wirklich stornieren? Es wird automatisch eine Stornorechnung erstellt. Dieser Vorgang kann nicht rückgängig gemacht werden.`,
                                                                            variant: 'danger',
                                                                            confirmLabel: 'Stornieren'
                                                                        })) {
                                                                            try {
                                                                                const response = await fetch(`/api/ausgangs-dokumente/${dok.id}/storno`, {
                                                                                    method: 'POST'
                                                                                });
                                                                                if (response.ok) {
                                                                                    const stornoDok = await response.json();
                                                                                    toast.success(`Stornorechnung ${stornoDok.dokumentNummer} wurde erstellt`);
                                                                                    loadAusgangsDokumente();
                                                                                } else {
                                                                                    const errText = await response.text();
                                                                                    toast.error('Stornierung fehlgeschlagen: ' + errText);
                                                                                }
                                                                            } catch (e) {
                                                                                console.error('Storno error:', e);
                                                                                toast.error('Stornierung fehlgeschlagen');
                                                                            }
                                                                        }
                                                                    }}
                                                                >
                                                                    <Ban className="w-4 h-4" />
                                                                    Stornieren
                                                                </button>
                                                            </>
                                                        )}

                                                        {/* Löschen - nur Entwürfe (GoBD: nicht gebucht, nicht versandt, nicht storniert, kein STORNO) */}
                                                        {!dok.gebucht && !dok.versandDatum && !dok.storniert && dok.typ !== 'STORNO' && (
                                                            <>
                                                                <hr className="my-1 border-slate-100" />
                                                                <button
                                                                    className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center gap-2"
                                                                    onClick={() => {
                                                                        setDeleteDokument(dok);
                                                                        setDeleteBegruendung('');
                                                                        setShowDeleteDialog(true);
                                                                        setActionMenuDokument(null);
                                                                    }}
                                                                >
                                                                    <Trash2 className="w-4 h-4" />
                                                                    Löschen
                                                                </button>
                                                            </>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <div className="flex flex-col items-center justify-center py-8 text-slate-500 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                                <FileText className="w-10 h-10 text-slate-300 mb-2" />
                                <p className="text-sm">Keine Ausgangsgeschäftsdokumente vorhanden.</p>
                                <p className="text-xs text-slate-400 mt-1">Klicken Sie oben auf &quot;Dokument erstellen&quot;</p>
                            </div>
                        )}
                    </div>

                    {/* Eingangsrechnungen */}
                    <div>
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="text-lg font-medium text-slate-900">Eingangsrechnungen</h3>
                            <span className="text-sm text-slate-500">{eingangsrechnungen.length} Rechnung{eingangsrechnungen.length !== 1 ? 'en' : ''}{eingangsrechnungen.length > 0 ? ` — ${formatCurrency(eingangsrechnungenSum)}` : ''}</span>
                        </div>
                        {eingangsrechnungen.length > 0 ? (
                            <div className="space-y-4">
                                {eingangsrechnungen.map((er) => {
                                    const ketteItems: DokumentKetteRef[] = er.dokumentenKette && er.dokumentenKette.length > 0
                                        ? er.dokumentenKette
                                        : er.pdfUrl
                                            ? [{ id: er.dokumentId ?? er.id, typ: 'RECHNUNG', dokumentNummer: er.dokumentNummer ?? null, dokumentDatum: er.dokumentDatum ?? null, betragNetto: er.gesamtbetrag ?? null, pdfUrl: er.pdfUrl }]
                                            : [];
                                    const hasKette = ketteItems.length > 0;
                                    const alleZuordnungen = er.alleZuordnungen || [];
                                    const andereZuordnungen = alleZuordnungen.filter(z => z.projektId !== projekt.id);
                                    
                                    return (
                                        <div key={er.id} className="bg-white rounded-xl border border-slate-200 hover:shadow-md transition-shadow overflow-hidden">
                                            {/* Dokumentenkette */}
                                            {hasKette && (
                                                <div className="px-4 pt-3 pb-2 bg-slate-50 border-b border-slate-100">
                                                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-2">Dokumentenkette</p>
                                                    <div className="flex items-center gap-1 flex-wrap">
                                                        {ketteItems.map((kd, idx) => {
                                                            const typConfig: Record<string, { label: string; color: string; bg: string; border: string }> = {
                                                                ANGEBOT: { label: 'Angebot', color: 'text-blue-700', bg: 'bg-blue-50', border: 'border-blue-200' },
                                                                AUFTRAGSBESTAETIGUNG: { label: 'AB', color: 'text-purple-700', bg: 'bg-purple-50', border: 'border-purple-200' },
                                                                LIEFERSCHEIN: { label: 'Lieferschein', color: 'text-amber-700', bg: 'bg-amber-50', border: 'border-amber-200' },
                                                                RECHNUNG: { label: 'Rechnung', color: 'text-rose-700', bg: 'bg-rose-50', border: 'border-rose-200' },
                                                                GUTSCHRIFT: { label: 'Gutschrift', color: 'text-green-700', bg: 'bg-green-50', border: 'border-green-200' },
                                                                SONSTIG: { label: 'Sonstiges', color: 'text-slate-700', bg: 'bg-slate-50', border: 'border-slate-200' },
                                                            };
                                                            const cfg = typConfig[kd.typ] || { label: kd.typ, color: 'text-slate-700', bg: 'bg-slate-50', border: 'border-slate-200' };
                                                            return (
                                                                <React.Fragment key={kd.id}>
                                                                    {idx > 0 && <ChevronRight className="w-3 h-3 text-slate-300 shrink-0" />}
                                                                    <button
                                                                        onClick={() => kd.pdfUrl && setPdfPreviewDoc({ url: kd.pdfUrl, title: kd.dokumentNummer || cfg.label })}
                                                                        className={`inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-md border ${cfg.bg} ${cfg.color} ${cfg.border} hover:opacity-80 transition-opacity`}
                                                                        title={kd.dokumentNummer ? `${cfg.label} ${kd.dokumentNummer}` : cfg.label}
                                                                    >
                                                                        <File className="w-3 h-3" />
                                                                        <span className="font-medium">{cfg.label}</span>
                                                                        {kd.dokumentNummer && <span className="opacity-70">#{kd.dokumentNummer}</span>}
                                                                        {kd.betragNetto != null && (
                                                                            <span className="opacity-70">{formatCurrency(kd.betragNetto)}</span>
                                                                        )}
                                                                    </button>
                                                                </React.Fragment>
                                                            );
                                                        })}
                                                    </div>
                                                </div>
                                            )}
                                            
                                            {/* Hauptinhalt */}
                                            <div className="p-4">
                                                <div className="flex items-start justify-between gap-4">
                                                    <div className="flex-1 min-w-0">
                                                        <div className="flex items-center gap-2 mb-1">
                                                            <span className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded border bg-emerald-50 text-emerald-700 border-emerald-200">
                                                                EINGANGSRECHNUNG
                                                            </span>
                                                            <span className="font-semibold text-slate-900 truncate">
                                                                {er.lieferantName || 'Unbekannter Lieferant'}
                                                            </span>
                                                            {er.dokumentNummer && (
                                                                <span className="text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded">
                                                                    {er.dokumentNummer}
                                                                </span>
                                                            )}
                                                        </div>
                                                        <p className="text-sm text-slate-500 truncate">{er.dateiname}</p>
                                                        {er.beschreibung && (
                                                            <p className="text-sm text-slate-600 mt-1">{er.beschreibung}</p>
                                                        )}
                                                        <div className="flex items-center gap-4 mt-2 text-xs text-slate-400">
                                                            {er.dokumentDatum && (
                                                                <span>Datum: {new Date(er.dokumentDatum).toLocaleDateString('de-DE')}</span>
                                                            )}
                                                            {er.prozent < 100 ? (
                                                                <span className="bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded">
                                                                    {er.prozent}% Anteil
                                                                </span>
                                                            ) : (
                                                                <span className="bg-green-100 text-green-700 px-1.5 py-0.5 rounded">
                                                                    100% zugewiesen
                                                                </span>
                                                            )}
                                                        </div>
                                                    </div>
                                                    <div className="text-right shrink-0">
                                                        <p className="text-lg font-bold text-slate-900">
                                                            {formatCurrency(er.berechneterBetrag)}
                                                        </p>
                                                        {er.gesamtbetrag && er.prozent < 100 && (
                                                            <p className="text-xs text-slate-400">
                                                                von {formatCurrency(er.gesamtbetrag)}
                                                            </p>
                                                        )}
                                                        <div className="flex items-center gap-2 justify-end mt-2">
                                                            {er.geschaeftsdokumentId && (
                                                                <button
                                                                    onClick={() => setZuordnungBearbeitenEr(er)}
                                                                    className="inline-flex items-center gap-1 text-xs text-rose-600 hover:text-rose-700"
                                                                    title="Zuordnung bearbeiten"
                                                                >
                                                                    <Edit2 className="w-3 h-3" />
                                                                    Bearbeiten
                                                                </button>
                                                            )}
                                                        </div>
                                                    </div>
                                                </div>
                                                
                                                {/* Zuordnungs-Info: Wer hat zugeordnet + Aufteilung */}
                                                {(er.zugeordnetVonName || andereZuordnungen.length > 0) && (
                                                    <div className="mt-3 pt-3 border-t border-slate-100">
                                                        {/* Zugeordnet von */}
                                                        {er.zugeordnetVonName && (
                                                            <div className="flex items-center gap-1.5 text-xs text-slate-500 mb-2">
                                                                <User className="w-3 h-3" />
                                                                <span>Zugeordnet von <span className="font-medium text-slate-700">{er.zugeordnetVonName}</span></span>
                                                                {er.zugeordnetAm && (
                                                                    <span className="text-slate-400">
                                                                        am {new Date(er.zugeordnetAm).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })}
                                                                    </span>
                                                                )}
                                                            </div>
                                                        )}
                                                        
                                                        {/* Aufteilung auf andere Projekte/Kostenstellen */}
                                                        {andereZuordnungen.length > 0 && (
                                                            <div className="space-y-1">
                                                                <p className="text-xs font-medium text-slate-500 mb-1">Weitere Zuordnungen:</p>
                                                                {andereZuordnungen.map((z, idx) => (
                                                                    <div key={idx} className="flex items-center gap-2 text-xs">
                                                                        {z.projektId ? (
                                                                            <button
                                                                                onClick={() => {
                                                                                    navigate(`/projekte?projektId=${z.projektId}`);
                                                                                }}
                                                                                className="inline-flex items-center gap-1 text-rose-600 hover:text-rose-700 hover:underline"
                                                                            >
                                                                                <Briefcase className="w-3 h-3" />
                                                                                {z.projektName || 'Projekt'}
                                                                                {z.projektNummer && <span className="text-slate-400">({z.projektNummer})</span>}
                                                                                <ExternalLink className="w-2.5 h-2.5" />
                                                                            </button>
                                                                        ) : z.kostenstelleId ? (
                                                                            <span
                                                                                className="inline-flex items-center gap-1 text-slate-600"
                                                                            >
                                                                                <Building2 className="w-3 h-3" />
                                                                                {z.kostenstelleName || 'Kostenstelle'}
                                                                            </span>
                                                                        ) : null}
                                                                        <span className="text-slate-400">
                                                                            {z.prozent != null && `${z.prozent}%`}
                                                                            {z.berechneterBetrag != null && ` · ${formatCurrency(z.berechneterBetrag)}`}
                                                                        </span>
                                                                        {z.beschreibung && (
                                                                            <span className="text-slate-500 italic truncate max-w-[200px]" title={z.beschreibung}>
                                                                                „{z.beschreibung}"
                                                                            </span>
                                                                        )}
                                                                        {z.zugeordnetVonName && (
                                                                            <span className="text-slate-400">
                                                                                (von {z.zugeordnetVonName})
                                                                            </span>
                                                                        )}
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <div className="flex flex-col items-center justify-center py-8 text-slate-500 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                                <File className="w-10 h-10 text-slate-300 mb-2" />
                                <p className="text-sm">Keine Eingangsrechnungen zugeordnet.</p>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Rechnungserstellung Dialog */}
            <Dialog open={showRechnungDialog} onOpenChange={(open) => {
                if (!open) {
                    setShowRechnungDialog(false);
                    setRechnungBasisDok(null);
                    setAbrechnungsverlauf(null);
                }
            }} className="w-full max-w-3xl">
                <DialogContent className="overflow-hidden">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900 flex items-center gap-2">
                            <Receipt className="w-5 h-5 text-rose-600" />
                            Rechnung erstellen
                        </DialogTitle>
                        {rechnungBasisDok && (
                            <p className="text-sm text-slate-500">
                                Basierend auf: <span className="font-medium text-slate-700">{rechnungBasisDok.dokumentNummer}</span>
                                {rechnungBasisDok.betreff && <span> &ndash; {rechnungBasisDok.betreff}</span>}
                            </p>
                        )}
                    </DialogHeader>

                    {rechnungLoading ? (
                        <div className="flex items-center justify-center py-12">
                            <RefreshCw className="w-6 h-6 text-rose-500 animate-spin" />
                            <span className="ml-3 text-slate-500">Abrechnungsverlauf wird geladen...</span>
                        </div>
                    ) : (
                        <div className="space-y-6 py-2 overflow-y-auto min-h-0 flex-1 pr-1">
                            {/* Abrechnungsverlauf Übersicht */}
                            {abrechnungsverlauf && (
                                <div className="space-y-3">
                                    <h4 className="text-sm font-semibold text-slate-700 uppercase tracking-wide">Abrechnungsverlauf</h4>

                                    {/* Basisdokument Betrag */}
                                    <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
                                        <div className="flex justify-between items-center">
                                            <span className="text-sm text-slate-600">Basisdokument ({abrechnungsverlauf.basisdokumentNummer})</span>
                                            <span className="text-base font-bold text-slate-900">
                                                {formatCurrency(abrechnungsverlauf.basisdokumentBetragNetto)}
                                            </span>
                                        </div>
                                    </div>

                                    {/* Bisherige Abrechnungen */}
                                    {abrechnungsverlauf.positionen.length > 0 && (
                                        <div className="space-y-1">
                                            {abrechnungsverlauf.positionen.map((pos) => {
                                                const posTypConfig = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === pos.typ);
                                                return (
                                                    <div
                                                        key={pos.id}
                                                        className={cn(
                                                            "flex items-center justify-between px-3 py-2 rounded-lg text-sm",
                                                            pos.storniert ? "bg-red-50 line-through" : "bg-white border border-slate-100"
                                                        )}
                                                    >
                                                        <div className="flex items-center gap-2">
                                                            <span className={cn(
                                                                "text-[10px] font-medium px-1.5 py-0.5 rounded border",
                                                                TYP_COLORS[pos.typ] || 'bg-slate-50 text-slate-600 border-slate-200'
                                                            )}>
                                                                {posTypConfig?.label || pos.typ}
                                                                {pos.abschlagsNummer ? ` #${pos.abschlagsNummer}` : ''}
                                                            </span>
                                                            <span className="text-slate-600">{pos.dokumentNummer}</span>
                                                            <span className="text-slate-400">{new Date(pos.datum).toLocaleDateString('de-DE')}</span>
                                                        </div>
                                                        <span className={cn("font-medium", pos.storniert ? "text-red-400" : "text-slate-900")}>
                                                            {pos.storniert ? 'storniert' : formatCurrency(pos.betragNetto)}
                                                        </span>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    )}

                                    {/* Summenzeile */}
                                    <div className="flex justify-between items-center gap-4 pt-2 border-t border-slate-200">
                                        <div className="text-sm text-slate-500">
                                            Bereits abgerechnet: <span className="font-semibold text-slate-700">{formatCurrency(abrechnungsverlauf.bereitsAbgerechnet)}</span>
                                        </div>
                                        <div className={cn(
                                            "text-base font-bold px-3 py-1 rounded-lg",
                                            abrechnungsverlauf.restbetrag > 0 ? "bg-green-50 text-green-700" : "bg-red-50 text-red-700"
                                        )}>
                                            Restbetrag: {formatCurrency(abrechnungsverlauf.restbetrag)}
                                        </div>
                                    </div>

                                    {abrechnungsverlauf.restbetrag <= 0 && (
                                        <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
                                            Der Basisbetrag ist vollständig abgerechnet. Es kann keine weitere Rechnung erstellt werden.
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Rechnungstyp-Auswahl */}
                            {(!abrechnungsverlauf || abrechnungsverlauf.restbetrag > 0) && (
                                <div className="space-y-4">
                                    <h4 className="text-sm font-semibold text-slate-700 uppercase tracking-wide">Rechnungstyp wählen</h4>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                        {/* Teilrechnung – nur wenn mindestens 2 Leistungspositionen */}
                                        {(() => {
                                            const hatGenugPositionen = getAllServiceBlocks(basisDokBlocks).length >= 2;
                                            return (
                                                <button
                                                    onClick={() => hatGenugPositionen && setRechnungTyp('TEILRECHNUNG')}
                                                    disabled={!hatGenugPositionen}
                                                    className={cn(
                                                        "p-4 rounded-xl border-2 text-left transition-all",
                                                        !hatGenugPositionen
                                                            ? "border-slate-200 bg-slate-50 opacity-50 cursor-not-allowed"
                                                            : rechnungTyp === 'TEILRECHNUNG'
                                                                ? "border-rose-500 bg-rose-50 ring-1 ring-rose-200"
                                                                : "border-slate-200 hover:border-slate-300 hover:bg-slate-50"
                                                    )}
                                                    title={!hatGenugPositionen ? 'Teilrechnung ist nur bei mindestens 2 Leistungspositionen möglich' : undefined}
                                                >
                                                    <p className={cn("font-semibold", !hatGenugPositionen ? "text-slate-400" : "text-slate-900")}>Teilrechnung</p>
                                                    <p className="text-xs text-slate-500 mt-1">
                                                        Einzelne Leistungspositionen nach Fertigstellung abrechnen
                                                    </p>
                                                </button>
                                            );
                                        })()}

                                        {/* Abschlagsrechnung */}
                                        <button
                                            onClick={() => setRechnungTyp('ABSCHLAGSRECHNUNG')}
                                            className={cn(
                                                "p-4 rounded-xl border-2 text-left transition-all",
                                                rechnungTyp === 'ABSCHLAGSRECHNUNG'
                                                    ? "border-rose-500 bg-rose-50 ring-1 ring-rose-200"
                                                    : "border-slate-200 hover:border-slate-300 hover:bg-slate-50"
                                            )}
                                        >
                                            <p className="font-semibold text-slate-900">Abschlagsrechnung</p>
                                            <p className="text-xs text-slate-500 mt-1">
                                                Pauschaler Abschlag auf den Gesamtbetrag
                                            </p>
                                        </button>

                                        {/* Schlussrechnung – nur wenn zuvor Teil-/Abschlagsrechnung existiert */}
                                        {(() => {
                                            const hatVorherigeAbrechnung = abrechnungsverlauf?.positionen.some(
                                                p => !p.storniert && (p.typ === 'TEILRECHNUNG' || p.typ === 'ABSCHLAGSRECHNUNG')
                                            );
                                            return (
                                                <button
                                                    onClick={() => hatVorherigeAbrechnung && setRechnungTyp('SCHLUSSRECHNUNG')}
                                                    disabled={!hatVorherigeAbrechnung}
                                                    className={cn(
                                                        "p-4 rounded-xl border-2 text-left transition-all",
                                                        !hatVorherigeAbrechnung
                                                            ? "border-slate-200 bg-slate-50 opacity-50 cursor-not-allowed"
                                                            : rechnungTyp === 'SCHLUSSRECHNUNG'
                                                                ? "border-rose-700 bg-rose-50 ring-1 ring-rose-300"
                                                                : "border-slate-200 hover:border-slate-300 hover:bg-slate-50"
                                                    )}
                                                    title={!hatVorherigeAbrechnung ? 'Schlussrechnung ist erst nach einer Teilrechnung oder Abschlagsrechnung möglich' : undefined}
                                                >
                                                    <p className={cn("font-semibold", !hatVorherigeAbrechnung ? "text-slate-400" : "text-slate-900")}>Schlussrechnung</p>
                                                    <p className="text-xs text-slate-500 mt-1">
                                                        Verbleibenden Restbetrag ({abrechnungsverlauf ? formatCurrency(abrechnungsverlauf.restbetrag) : '–'}) abrechnen
                                                    </p>
                                                </button>
                                            );
                                        })()}

                                        {/* Einfache Rechnung (bei erstem Mal) */}
                                        {abrechnungsverlauf && abrechnungsverlauf.positionen.length === 0 && (
                                            <button
                                                onClick={() => setRechnungTyp('RECHNUNG')}
                                                className={cn(
                                                    "p-4 rounded-xl border-2 text-left transition-all",
                                                    rechnungTyp === 'RECHNUNG'
                                                        ? "border-rose-500 bg-rose-50 ring-1 ring-rose-200"
                                                        : "border-slate-200 hover:border-slate-300 hover:bg-slate-50"
                                                )}
                                            >
                                                <p className="font-semibold text-slate-900">Einfache Rechnung</p>
                                                <p className="text-xs text-slate-500 mt-1">
                                                    Gesamtbetrag in einer Rechnung abrechnen
                                                </p>
                                            </button>
                                        )}
                                    </div>

                                    {/* Betrag Eingabe für Abschlagsrechnung */}
                                    {rechnungTyp === 'ABSCHLAGSRECHNUNG' && (
                                        <div className="space-y-3 p-4 bg-slate-50 rounded-xl border border-slate-200">
                                            <Label className="text-sm font-semibold text-slate-700 uppercase tracking-wide">
                                                Abschlagsbetrag
                                            </Label>
                                            <div className="grid grid-cols-3 gap-2">
                                                {([
                                                    { value: 'netto' as const, label: 'Netto absolut' },
                                                    { value: 'brutto' as const, label: 'Brutto absolut' },
                                                    { value: 'prozent' as const, label: 'Prozentual' },
                                                ]).map(m => (
                                                    <button
                                                        key={m.value}
                                                        onClick={() => { setAbschlagsEingabeModus(m.value); setAbschlagsBetrag(''); }}
                                                        className={cn(
                                                            "px-3 py-2 rounded-lg border text-sm font-medium transition-all",
                                                            abschlagsEingabeModus === m.value
                                                                ? "border-rose-400 bg-rose-50 text-rose-700"
                                                                : "border-slate-200 text-slate-600 hover:border-slate-300"
                                                        )}
                                                    >
                                                        {m.label}
                                                    </button>
                                                ))}
                                            </div>
                                            <div className="relative">
                                                <Input
                                                    type="number"
                                                    step={abschlagsEingabeModus === 'prozent' ? '1' : '0.01'}
                                                    min="0"
                                                    max={abschlagsEingabeModus === 'prozent' ? '100' : undefined}
                                                    value={abschlagsBetrag}
                                                    onChange={e => setAbschlagsBetrag(e.target.value)}
                                                    placeholder={abschlagsEingabeModus === 'prozent' ? '0' : '0,00'}
                                                    className="pr-8 rounded-lg bg-white"
                                                />
                                                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 text-sm">
                                                    {abschlagsEingabeModus === 'prozent' ? '%' : '€'}
                                                </span>
                                            </div>
                                            {berechneterAbschlagNetto > 0 && abschlagsEingabeModus !== 'netto' && (
                                                <p className="text-xs text-slate-500">
                                                    = {formatCurrency(berechneterAbschlagNetto)} netto
                                                </p>
                                            )}
                                            {abrechnungsverlauf && berechneterAbschlagNetto > abrechnungsverlauf.restbetrag && (
                                                <p className="text-xs text-red-600">
                                                    Betrag übersteigt den verfügbaren Restbetrag ({formatCurrency(abrechnungsverlauf.restbetrag)})
                                                </p>
                                            )}
                                        </div>
                                    )}

                                    {/* Schlussrechnung Info */}
                                    {rechnungTyp === 'SCHLUSSRECHNUNG' && abrechnungsverlauf && (
                                        <div className="p-4 bg-rose-50 rounded-xl border border-rose-200">
                                            <p className="text-sm text-rose-700">
                                                Die Schlussrechnung wird automatisch über den verbleibenden Restbetrag von{' '}
                                                <strong>{formatCurrency(abrechnungsverlauf.restbetrag)}</strong> erstellt.
                                            </p>
                                        </div>
                                    )}

                                    {/* Teilrechnung Positions-Auswahl */}
                                    {rechnungTyp === 'TEILRECHNUNG' && (
                                        <div className="space-y-3 flex flex-col min-h-0">
                                            <div className="flex items-center justify-between flex-shrink-0">
                                                <Label className="text-sm font-semibold text-slate-700">
                                                    Leistungen auswählen
                                                </Label>
                                                {getAllServiceBlocks(basisDokBlocks).length > 0 && (
                                                    <button
                                                        className="text-xs text-rose-600 hover:text-rose-700 font-medium"
                                                        onClick={() => {
                                                            const allIds = new Set<string>();
                                                            for (const b of basisDokBlocks) {
                                                                if (b.type === 'SERVICE' && !bereitsAbgerechneteBlockIds.has(b.id)) allIds.add(b.id);
                                                                if (b.type === 'SECTION_HEADER' && b.children) {
                                                                    for (const c of b.children) {
                                                                        if (c.type === 'SERVICE' && !bereitsAbgerechneteBlockIds.has(c.id)) allIds.add(c.id);
                                                                    }
                                                                }
                                                            }
                                                            setSelectedBlockIds(prev =>
                                                                prev.size === allIds.size ? new Set() : allIds
                                                            );
                                                        }}
                                                    >
                                                        {selectedBlockIds.size === getAllServiceBlocks(basisDokBlocks).filter(b => !bereitsAbgerechneteBlockIds.has(b.id)).length ? 'Alle abwählen' : 'Alle auswählen'}
                                                    </button>
                                                )}
                                            </div>
                                            {basisDokBlocks.length === 0 ? (
                                                <div className="p-4 bg-slate-50 rounded-xl border border-slate-200 text-sm text-slate-500 text-center">
                                                    Keine Positionen im Basisdokument vorhanden.
                                                </div>
                                            ) : (
                                                <div className="max-h-[40vh] overflow-y-auto overflow-x-hidden space-y-1 border border-slate-200 rounded-xl p-2">
                                                    {basisDokBlocks.map((block) => {
                                                        if (block.type === 'SECTION_HEADER') {
                                                            const sectionServices = (block.children || []).filter(c => c.type === 'SERVICE');
                                                            const selectableServices = sectionServices.filter(c => !bereitsAbgerechneteBlockIds.has(c.id));
                                                            const allSectionDisabled = selectableServices.length === 0;
                                                            const allSelected = selectableServices.length > 0 && selectableServices.every(c => selectedBlockIds.has(c.id));
                                                            const someSelected = selectableServices.some(c => selectedBlockIds.has(c.id));
                                                            return (
                                                                <div key={block.id} className="mb-2">
                                                                    <div
                                                                        className={cn(
                                                                            "flex items-center gap-2 px-3 py-2 bg-slate-100 rounded-lg",
                                                                            allSectionDisabled ? "opacity-50 cursor-default" : "cursor-pointer"
                                                                        )}
                                                                        onClick={allSectionDisabled ? undefined : () => {
                                                                            setSelectedBlockIds(prev => {
                                                                                const next = new Set(prev);
                                                                                if (allSelected) {
                                                                                    selectableServices.forEach(c => next.delete(c.id));
                                                                                } else {
                                                                                    selectableServices.forEach(c => next.add(c.id));
                                                                                }
                                                                                return next;
                                                                            });
                                                                        }}
                                                                    >
                                                                        <div className={cn(
                                                                            "w-4 h-4 rounded border flex items-center justify-center flex-shrink-0",
                                                                            allSectionDisabled ? "bg-slate-300 border-slate-300" : allSelected ? "bg-rose-600 border-rose-600" : someSelected ? "bg-rose-200 border-rose-400" : "border-slate-300"
                                                                        )}>
                                                                            {(allSelected || someSelected || allSectionDisabled) && <Check className="w-3 h-3 text-white" />}
                                                                        </div>
                                                                        <span className={cn("text-sm font-semibold", allSectionDisabled ? "text-slate-400" : "text-slate-700")}>
                                                                            {block.sectionLabel || 'Bauabschnitt'}
                                                                        </span>
                                                                    </div>
                                                                    <div className="ml-6 space-y-1 mt-1">
                                                                        {sectionServices.map(service => (
                                                                            <TeilrechnungPositionRow
                                                                                key={service.id}
                                                                                block={service}
                                                                                selected={selectedBlockIds.has(service.id)}
                                                                                expanded={expandedBlockIds.has(service.id)}
                                                                                disabled={bereitsAbgerechneteBlockIds.has(service.id)}
                                                                                onToggleSelect={() => {
                                                                                    if (bereitsAbgerechneteBlockIds.has(service.id)) return;
                                                                                    setSelectedBlockIds(prev => {
                                                                                        const next = new Set(prev);
                                                                                        if (next.has(service.id)) next.delete(service.id);
                                                                                        else next.add(service.id);
                                                                                        return next;
                                                                                    });
                                                                                }}
                                                                                onToggleExpand={() => {
                                                                                    setExpandedBlockIds(prev => {
                                                                                        const next = new Set(prev);
                                                                                        if (next.has(service.id)) next.delete(service.id);
                                                                                        else next.add(service.id);
                                                                                        return next;
                                                                                    });
                                                                                }}
                                                                            />
                                                                        ))}
                                                                    </div>
                                                                </div>
                                                            );
                                                        }
                                                        if (block.type === 'SERVICE') {
                                                            return (
                                                                <TeilrechnungPositionRow
                                                                    key={block.id}
                                                                    block={block}
                                                                    selected={selectedBlockIds.has(block.id)}
                                                                    expanded={expandedBlockIds.has(block.id)}
                                                                    disabled={bereitsAbgerechneteBlockIds.has(block.id)}
                                                                    onToggleSelect={() => {
                                                                        if (bereitsAbgerechneteBlockIds.has(block.id)) return;
                                                                        setSelectedBlockIds(prev => {
                                                                            const next = new Set(prev);
                                                                            if (next.has(block.id)) next.delete(block.id);
                                                                            else next.add(block.id);
                                                                            return next;
                                                                        });
                                                                    }}
                                                                    onToggleExpand={() => {
                                                                        setExpandedBlockIds(prev => {
                                                                            const next = new Set(prev);
                                                                            if (next.has(block.id)) next.delete(block.id);
                                                                            else next.add(block.id);
                                                                            return next;
                                                                        });
                                                                    }}
                                                                />
                                                            );
                                                        }
                                                        return null;
                                                    })}
                                                </div>
                                            )}
                                            {/* Summe der gewählten Positionen + Budget-Übersicht */}
                                            {getAllServiceBlocks(basisDokBlocks).length > 0 && (
                                                <div className="space-y-2">
                                                    {/* Gewählte Positionen Summe */}
                                                    <div className="flex justify-between items-center p-3 bg-rose-50 rounded-lg border border-rose-200">
                                                        <span className="text-sm font-medium text-rose-700">
                                                            Summe ({selectedBlockIds.size} von {getAllServiceBlocks(basisDokBlocks).length} Positionen)
                                                        </span>
                                                        <span className="text-base font-bold text-rose-700">
                                                            {formatCurrency(teilrechnungSelectedSum)}
                                                        </span>
                                                    </div>

                                                    {/* Budget-Übersicht wenn bereits abgerechnet */}
                                                    {abrechnungsverlauf && abrechnungsverlauf.bereitsAbgerechnet > 0 && (
                                                        <div className="p-3 bg-slate-50 rounded-lg border border-slate-200 space-y-2">
                                                            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Abrechnungsübersicht</p>
                                                            <div className="space-y-1 text-sm">
                                                                <div className="flex justify-between text-slate-600">
                                                                    <span>Gesamtauftragssumme (netto)</span>
                                                                    <span className="font-medium">{formatCurrency(abrechnungsverlauf.basisdokumentBetragNetto)}</span>
                                                                </div>
                                                                {abrechnungsverlauf.positionen.filter((p: AbrechnungspositionDto) => !p.storniert).map((pos: AbrechnungspositionDto) => {
                                                                    const posTypConfig = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === pos.typ);
                                                                    return (
                                                                        <div key={pos.id} className="flex justify-between text-slate-500">
                                                                            <span className="flex items-center gap-1.5">
                                                                                <span className="text-[10px] font-medium px-1 py-0.5 rounded border bg-amber-50 text-amber-700 border-amber-200">
                                                                                    {posTypConfig?.label || pos.typ}
                                                                                    {pos.abschlagsNummer ? ` #${pos.abschlagsNummer}` : ''}
                                                                                </span>
                                                                                {pos.dokumentNummer}
                                                                            </span>
                                                                            <span className="font-medium text-amber-700">− {formatCurrency(pos.betragNetto)}</span>
                                                                        </div>
                                                                    );
                                                                })}
                                                                <div className="border-t border-slate-200 pt-1 flex justify-between font-semibold text-slate-700">
                                                                    <span>Verfügbarer Restbetrag</span>
                                                                    <span className={cn(
                                                                        abrechnungsverlauf.restbetrag > 0 ? "text-green-700" : "text-red-600"
                                                                    )}>
                                                                        {formatCurrency(abrechnungsverlauf.restbetrag)}
                                                                    </span>
                                                                </div>
                                                                {teilrechnungSelectedSum > 0 && (
                                                                    <div className="flex justify-between text-slate-600">
                                                                        <span>Diese Teilrechnung</span>
                                                                        <span className="font-medium">− {formatCurrency(teilrechnungSelectedSum)}</span>
                                                                    </div>
                                                                )}
                                                                {teilrechnungSelectedSum > 0 && (
                                                                    <div className="border-t border-slate-200 pt-1 flex justify-between font-bold">
                                                                        <span className="text-slate-700">Verbleibend nach dieser Rechnung</span>
                                                                        <span className={cn(
                                                                            (abrechnungsverlauf.restbetrag - teilrechnungSelectedSum) >= -0.01 ? "text-green-700" : "text-red-600"
                                                                        )}>
                                                                            {formatCurrency(Math.max(0, abrechnungsverlauf.restbetrag - teilrechnungSelectedSum))}
                                                                        </span>
                                                                    </div>
                                                                )}
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Warnung bei Überschreitung */}
                                                    {teilrechnungExceedsRestbetrag && (
                                                        <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700 flex items-start gap-2">
                                                            <span className="text-red-500 mt-0.5 flex-shrink-0">⚠</span>
                                                            <div>
                                                                <p className="font-semibold">Betrag übersteigt den Restbetrag!</p>
                                                                <p className="text-xs mt-0.5">
                                                                    Die gewählten Positionen ({formatCurrency(teilrechnungSelectedSum)}) übersteigen den verfügbaren Restbetrag
                                                                    ({formatCurrency(abrechnungsverlauf?.restbetrag ?? 0)}). Bitte wählen Sie weniger Positionen aus.
                                                                </p>
                                                            </div>
                                                        </div>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    )}

                    <DialogFooter className="gap-2">
                        <Button variant="outline" onClick={() => {
                            setShowRechnungDialog(false);
                            setRechnungBasisDok(null);
                            setAbrechnungsverlauf(null);
                        }}>
                            Abbrechen
                        </Button>
                        {(!abrechnungsverlauf || abrechnungsverlauf.restbetrag > 0) && (
                            <Button
                                className="bg-rose-600 text-white hover:bg-rose-700"
                                disabled={
                                    rechnungLoading ||
                                    (rechnungTyp === 'ABSCHLAGSRECHNUNG' && berechneterAbschlagNetto <= 0) ||
                                    (rechnungTyp === 'ABSCHLAGSRECHNUNG' && !!abrechnungsverlauf && berechneterAbschlagNetto > abrechnungsverlauf.restbetrag) ||
                                    (rechnungTyp === 'SCHLUSSRECHNUNG' && abrechnungsverlauf != null && abrechnungsverlauf.restbetrag <= 0) ||
                                    (rechnungTyp === 'TEILRECHNUNG' && selectedBlockIds.size === 0) ||
                                    (rechnungTyp === 'TEILRECHNUNG' && teilrechnungExceedsRestbetrag)
                                }
                                onClick={async () => {
                                    if (!rechnungBasisDok) return;

                                    let betrag: number | undefined;
                                    let positionenJson: string | undefined;

                                    if (rechnungTyp === 'ABSCHLAGSRECHNUNG') {
                                        betrag = berechneterAbschlagNetto;
                                        if (!betrag || betrag <= 0) {
                                            toast.error('Bitte geben Sie einen gültigen Betrag ein.');
                                            return;
                                        }
                                        // Abschlag-Eingabemodus und Originalwert speichern, Blöcke vom Basisdokument übernehmen
                                        positionenJson = JSON.stringify({
                                            blocks: basisDokBlocks,
                                            globalRabatt: 0,
                                            abschlagInfo: {
                                                modus: abschlagsEingabeModus,
                                                eingabeWert: parseFloat(abschlagsBetrag)
                                            }
                                        });
                                    } else if (rechnungTyp === 'SCHLUSSRECHNUNG' && abrechnungsverlauf) {
                                        betrag = abrechnungsverlauf.restbetrag;
                                    } else if (rechnungTyp === 'RECHNUNG' && rechnungBasisDok.betragNetto) {
                                        betrag = rechnungBasisDok.betragNetto;
                                    } else if (rechnungTyp === 'TEILRECHNUNG') {
                                        if (selectedBlockIds.size === 0) {
                                            toast.error('Bitte wählen Sie mindestens eine Leistung aus.');
                                            return;
                                        }
                                        const allBlocksWithZeros = zeroOutUnselectedBlocks(basisDokBlocks, selectedBlockIds);
                                        positionenJson = JSON.stringify({ blocks: allBlocksWithZeros, globalRabatt: 0 });
                                        // Betrag aus gewählten Positionen berechnen für Abrechnungsverlauf
                                        betrag = getAllServiceBlocks(basisDokBlocks)
                                            .filter(b => selectedBlockIds.has(b.id))
                                            .reduce((sum, b) => sum + (b.quantity || 0) * (b.price || 0), 0);
                                    }

                                    try {
                                        const response = await fetch('/api/ausgangs-dokumente', {
                                            method: 'POST',
                                            headers: { 'Content-Type': 'application/json' },
                                            body: JSON.stringify({
                                                typ: rechnungTyp,
                                                projektId: projekt.id,
                                                vorgaengerId: rechnungBasisDok.id,
                                                betreff: rechnungBasisDok.betreff,
                                                betragNetto: betrag,
                                                ...(positionenJson ? { positionenJson } : {}),
                                            })
                                        });
                                        if (response.ok) {
                                            const newDoc = await response.json();
                                            loadAusgangsDokumente();
                                            setShowRechnungDialog(false);
                                            setRechnungBasisDok(null);
                                            setAbrechnungsverlauf(null);
                                            window.open(`/dokument-editor?projektId=${projekt.id}&dokumentId=${newDoc.id}`, '_blank');
                                        } else {
                                            const errorText = await response.text();
                                            toast.error(errorText || 'Fehler beim Erstellen der Rechnung');
                                        }
                                    } catch (e) {
                                        console.error(e);
                                        toast.error('Fehler beim Erstellen der Rechnung');
                                    }
                                }}
                            >
                                <Receipt className="w-4 h-4 mr-2" />
                                {rechnungTyp === 'TEILRECHNUNG' ? 'Erstellen & im Editor bearbeiten' : 'Rechnung erstellen'}
                            </Button>
                        )}
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Lösch-Dialog mit Begründung */}
            <Dialog open={showDeleteDialog} onOpenChange={(open) => {
                if (!open) {
                    setShowDeleteDialog(false);
                    setDeleteDokument(null);
                    setDeleteBegruendung('');
                }
            }}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-red-600">Dokument löschen</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-2">
                        <p className="text-sm text-slate-600">
                            Möchten Sie <span className="font-semibold">{deleteDokument?.dokumentNummer}</span> wirklich löschen?
                        </p>
                        <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs text-slate-500">
                            Gemäß GoBD dürfen nur Entwürfe gelöscht werden. Gebuchte oder versandte Dokumente müssen storniert werden.
                        </div>
                        <div className="space-y-2">
                            <Label className="text-sm font-medium text-slate-700">Begründung *</Label>
                            <textarea
                                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500 min-h-[80px] resize-none"
                                placeholder="Bitte geben Sie eine Begründung für das Löschen an..."
                                value={deleteBegruendung}
                                onChange={(e) => setDeleteBegruendung(e.target.value)}
                            />
                        </div>
                    </div>
                    <DialogFooter className="gap-2">
                        <Button variant="outline" onClick={() => {
                            setShowDeleteDialog(false);
                            setDeleteDokument(null);
                            setDeleteBegruendung('');
                        }}>
                            Abbrechen
                        </Button>
                        <Button
                            className="bg-red-600 text-white hover:bg-red-700"
                            disabled={deleteLoading || !deleteBegruendung.trim()}
                            onClick={async () => {
                                if (!deleteDokument) return;
                                setDeleteLoading(true);
                                try {
                                    const response = await fetch(
                                        `/api/ausgangs-dokumente/${deleteDokument.id}?begruendung=${encodeURIComponent(deleteBegruendung.trim())}`,
                                        { method: 'DELETE' }
                                    );
                                    if (response.ok) {
                                        toast.success(`Dokument ${deleteDokument.dokumentNummer} gelöscht`);
                                        loadAusgangsDokumente();
                                        setShowDeleteDialog(false);
                                        setDeleteDokument(null);
                                        setDeleteBegruendung('');
                                    } else {
                                        const error = await response.text();
                                        toast.error(error || 'Löschen fehlgeschlagen');
                                    }
                                } catch (e) {
                                    console.error(e);
                                    toast.error('Fehler beim Löschen');
                                } finally {
                                    setDeleteLoading(false);
                                }
                            }}
                        >
                            <Trash2 className="w-4 h-4 mr-2" />
                            Löschen
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {activeTab === 'dokumente' && (
                <div className="space-y-6">
                    {/* Projekt-Dateien (DocumentManager) */}
                    <div>
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="text-lg font-medium text-slate-900">Projekt-Dateien</h3>
                        </div>
                        <DocumentManager projektId={projekt.id} />
                    </div>
                </div>
            )}

            {/* DocumentEditor wird jetzt immer in neuem Tab geöffnet über /dokument-editor Route */}

            {/* PDF Preview Modal */}
            {pdfPreviewDoc && (
                <DocumentPreviewModal
                    doc={pdfPreviewDoc}
                    onClose={() => setPdfPreviewDoc(null)}
                />
            )}

            {/* Zuordnung Bearbeiten Modal */}
            {zuordnungBearbeitenEr && (
                <ZuordnungModal
                    geschaeftsdokumentId={zuordnungBearbeitenEr.geschaeftsdokumentId}
                    dokumentNummer={zuordnungBearbeitenEr.dokumentNummer}
                    lieferantName={zuordnungBearbeitenEr.lieferantName}
                    pdfUrl={zuordnungBearbeitenEr.pdfUrl}
                    onClose={() => setZuordnungBearbeitenEr(null)}
                    onSuccess={async () => {
                        setZuordnungBearbeitenEr(null);
                        await loadEingangsrechnungen();
                        await onRefresh();
                    }}
                />
            )}

            {/* Material Modal */}
            <Dialog open={showMaterialModal} onOpenChange={setShowMaterialModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Materialkosten erfassen</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <div className="space-y-2">
                            <Label>Beschreibung</Label>
                            <Input
                                placeholder="z.B. Kleinmaterial"
                                value={newMaterial.beschreibung}
                                onChange={e => setNewMaterial(prev => ({ ...prev, beschreibung: e.target.value }))}
                            />
                        </div>
                        <div className="space-y-2">
                            <Label>Rechnungsnummer (Optional)</Label>
                            <Input
                                placeholder="z.B. RE-2023-001"
                                value={newMaterial.rechnungsnummer}
                                onChange={e => setNewMaterial(prev => ({ ...prev, rechnungsnummer: e.target.value }))}
                            />
                        </div>
                        <div className="space-y-2">
                            <Label>Betrag (€)</Label>
                            <Input
                                type="number"
                                placeholder="0.00"
                                value={newMaterial.betrag}
                                onChange={e => setNewMaterial(prev => ({ ...prev, betrag: e.target.value }))}
                            />
                        </div>
                        <div className="space-y-2">
                            <Label>Lieferant (Optional)</Label>
                            <div className="flex gap-2">
                                <Input
                                    readOnly
                                    value={suppliers.find(s => s.id.toString() === newMaterial.lieferantId)?.lieferantenname || ''}
                                    placeholder="Kein Lieferant ausgewählt"
                                    className="bg-slate-50"
                                />
                                <Button variant="outline" onClick={() => setShowSupplierPicker(true)} title="Lieferant suchen">
                                    <Search className="w-4 h-4" />
                                </Button>
                            </div>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowMaterialModal(false)}>Abbrechen</Button>
                        <Button onClick={handleSaveMaterial} disabled={savingMaterial || !newMaterial.beschreibung || !newMaterial.betrag}>
                            {savingMaterial ? 'Speichern...' : 'Speichern'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Lagerartikel Modal */}
            <Dialog
                open={showLagerArtikelModal}
                onOpenChange={(open) => {
                    setShowLagerArtikelModal(open);
                    if (!open) {
                        resetLagerArtikelSelection();
                    }
                }}
                className="w-[96vw] h-[92vh] max-w-none"
            >
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Artikel aus Lager hinzufügen</DialogTitle>
                        <p className="text-sm text-slate-500">
                            Wählen Sie einen oder mehrere Artikel mit hinterlegtem Lieferantenpreis und legen Sie pro Artikel fest, ob er aus Lager kommt oder bestellt werden muss.
                        </p>
                    </DialogHeader>

                    <div className="flex flex-col sm:flex-row gap-2 sm:items-center">
                        <div className="relative flex-1">
                            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-500" />
                            <Input
                                placeholder="Artikel, Nummer, Lieferant oder Werkstoff suchen..."
                                className="pl-9"
                                value={lagerArtikelSearch}
                                onChange={(e) => setLagerArtikelSearch(e.target.value)}
                            />
                        </div>
                        <Button
                            variant="outline"
                            onClick={() => loadLagerArtikel({ page: lagerArtikelPage, query: lagerArtikelSearch.trim() })}
                            disabled={loadingLagerArtikel || savingLagerArtikel}
                        >
                            <RefreshCw className={cn('w-4 h-4 mr-2', loadingLagerArtikel && 'animate-spin')} /> Neu laden
                        </Button>
                    </div>

                    <div className="text-xs text-slate-500">{lagerArtikelStatusText}</div>

                    {lagerArtikelError && (
                        <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
                            {lagerArtikelError}
                        </div>
                    )}

                    <div className="flex-1 min-h-0 border border-slate-200 rounded-lg overflow-hidden">
                        <div className="h-full overflow-auto">
                            <table className="min-w-full text-sm">
                                <thead className="bg-slate-50 border-b border-slate-200 sticky top-0 z-10">
                                    <tr>
                                        <th className="px-3 py-2 text-left font-medium text-slate-600 w-[90px]">Auswahl</th>
                                        <th className="px-3 py-2 text-left font-medium text-slate-600">Artikel</th>
                                        <th className="px-3 py-2 text-left font-medium text-slate-600">Lieferant</th>
                                        <th className="px-3 py-2 text-left font-medium text-slate-600">Einheit</th>
                                        <th className="px-3 py-2 text-right font-medium text-slate-600">Preis</th>
                                        <th className="px-3 py-2 text-left font-medium text-slate-600 w-[140px]">Menge</th>
                                        <th className="px-3 py-2 text-left font-medium text-slate-600 w-[190px]">Bezug</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {lagerArtikel.map((artikel) => {
                                        const key = getLagerArtikelKey(artikel);
                                        const checked = selectedLagerArtikelKeys.has(key);
                                        const beschaffung = lagerArtikelBeschaffung[key] || 'lager';
                                        return (
                                            <tr key={key} className={cn('border-b border-slate-100', checked && 'bg-rose-50/50')}>
                                                <td className="px-3 py-2 align-top">
                                                    <input
                                                        type="checkbox"
                                                        checked={checked}
                                                        onChange={(e) => handleToggleLagerArtikel(artikel, e.target.checked)}
                                                        title={`Artikel ${artikel.produktname || ''} auswählen`}
                                                        aria-label={`Artikel ${artikel.produktname || ''} auswählen`}
                                                        className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                                    />
                                                </td>
                                                <td className="px-3 py-2 align-top">
                                                    <div className="font-medium text-slate-900">{artikel.produktname || '-'}</div>
                                                    <div className="text-xs text-slate-500 mt-0.5">
                                                        {artikel.externeArtikelnummer ? `Nr. ${artikel.externeArtikelnummer}` : 'Ohne Artikelnummer'}
                                                        {artikel.werkstoffName ? ` · ${artikel.werkstoffName}` : ''}
                                                    </div>
                                                    {artikel.produkttext && (
                                                        <div className="text-xs text-slate-400 mt-1 line-clamp-2">{artikel.produkttext}</div>
                                                    )}
                                                </td>
                                                <td className="px-3 py-2 align-top text-slate-700">{artikel.lieferantenname || '-'}</td>
                                                <td className="px-3 py-2 align-top text-slate-700">{getVerrechnungseinheitLabel(artikel.verrechnungseinheit)}</td>
                                                <td className="px-3 py-2 align-top text-right font-medium text-slate-900">{formatCurrency(artikel.preis)}</td>
                                                <td className="px-3 py-2 align-top">
                                                    <Input
                                                        type="number"
                                                        min="0.01"
                                                        step="0.01"
                                                        value={lagerArtikelMengen[key] || ''}
                                                        onChange={(e) => handleLagerMengeChange(artikel, e.target.value)}
                                                        placeholder="z.B. 5"
                                                    />
                                                </td>
                                                <td className="px-3 py-2 align-top">
                                                    <Select
                                                        value={beschaffung}
                                                        onChange={(value) => handleLagerBeschaffungChange(artikel, value)}
                                                        disabled={!checked}
                                                        options={[
                                                            { value: 'lager', label: 'Aus Lager' },
                                                            { value: 'bestellen', label: 'Bestellen' },
                                                        ]}
                                                        placeholder="Bezug wählen"
                                                    />
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>

                            {!loadingLagerArtikel && lagerArtikel.length === 0 && (
                                <div className="text-center text-slate-500 py-10">
                                    Keine Artikel mit Lieferantenpreis gefunden.
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="flex items-center justify-between gap-3">
                        <p className="text-xs text-slate-500">Seite {lagerArtikelPage + 1} von {lagerArtikelTotalPages}</p>
                        <div className="flex gap-2 justify-end">
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={loadingLagerArtikel || lagerArtikelPage === 0}
                                onClick={() => setLagerArtikelPage((p) => Math.max(0, p - 1))}
                            >
                                <ChevronLeft className="w-4 h-4 mr-1" /> zurück
                            </Button>
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={loadingLagerArtikel || lagerArtikelPage >= lagerArtikelTotalPages - 1}
                                onClick={() => setLagerArtikelPage((p) => p + 1)}
                            >
                                Weiter <ChevronRight className="w-4 h-4 ml-1" />
                            </Button>
                        </div>
                    </div>

                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowLagerArtikelModal(false)} disabled={savingLagerArtikel}>
                            Abbrechen
                        </Button>
                        <Button
                            onClick={handleSaveLagerArtikel}
                            disabled={savingLagerArtikel || selectedLagerArtikelKeys.size === 0}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {savingLagerArtikel ? 'Übernehme...' : `Abschließen (${selectedLagerArtikelKeys.size})`}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
            {supplierPickerDialog}

            {/* Dokumenttyp Auswahl Dialog */}
            <Dialog open={showDokumentTypDialog} onOpenChange={setShowDokumentTypDialog}>
                <DialogContent className="sm:max-w-lg">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900">Dokument erstellen</DialogTitle>
                        <p className="text-sm text-slate-500">Welche Art von Dokument möchten Sie erstellen?</p>
                    </DialogHeader>
                    <div className="grid grid-cols-2 gap-3 py-4">
                        {AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN
                            .filter((typ) => ['ANGEBOT', 'RECHNUNG', 'AUFTRAGSBESTAETIGUNG'].includes(typ.value))
                            .map((typ) => {
                                const isBaseType = typ.value === 'ANGEBOT' || typ.value === 'AUFTRAGSBESTAETIGUNG';
                                const hasBasisdokument = ausgangsDokumente.some(d => !d.vorgaengerId);
                                const disabled = isBaseType && hasBasisdokument;
                                return (
                                    <button
                                        key={typ.value}
                                        onClick={() => {
                                            if (disabled) return;
                                            setShowDokumentTypDialog(false);
                                            window.open(`/dokument-editor?projektId=${projekt.id}&dokumentTyp=${typ.value}`, '_blank');
                                        }}
                                        disabled={disabled}
                                        className={`flex items-center gap-3 p-4 rounded-xl border transition-all text-left group ${
                                            disabled
                                                ? 'border-slate-100 bg-slate-50 opacity-50 cursor-not-allowed'
                                                : 'border-slate-200 hover:border-rose-300 hover:bg-rose-50'
                                        }`}
                                        title={disabled ? 'Es existiert bereits ein Basisdokument' : undefined}
                                    >
                                        <div className={`flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center transition-colors ${
                                            disabled ? 'bg-slate-100 text-slate-400' : 'bg-rose-100 text-rose-600 group-hover:bg-rose-200'
                                        }`}>
                                            <FileText className="w-5 h-5" />
                                        </div>
                                        <span className={`font-medium transition-colors ${
                                            disabled ? 'text-slate-400' : 'text-slate-700 group-hover:text-rose-700'
                                        }`}>{typ.label}</span>
                                    </button>
                                );
                            })}
                    </div>
                </DialogContent>
            </Dialog>
        </>
    );

    const sideContent = (
        <>
            <h2 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                <User className="w-5 h-5 text-rose-500" />
                Projektdaten
            </h2>
            <div className="space-y-4">
                <div className="p-3 bg-slate-50 rounded-lg">
                    <p className="text-xs text-slate-500">Kunde</p>
                    <p className="font-medium text-slate-900">{projekt.kunde || '-'}</p>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg">
                    <p className="text-xs text-slate-500">Kundennummer</p>
                    <p className="font-medium text-slate-900">{projekt.kundennummer || kundeDto?.kundennummer || '-'}</p>
                </div>
                {kundeDto?.ansprechspartner && (
                    <div className="p-3 bg-slate-50 rounded-lg">
                        <p className="text-xs text-slate-500">Ansprechpartner</p>
                        <p className="font-medium text-slate-900">{kundeDto.ansprechspartner}</p>
                    </div>
                )}
                <div className="p-3 bg-slate-50 rounded-lg">
                    <p className="text-xs text-slate-500">Auftragsnummer</p>
                    <p className="font-medium text-slate-900">{projekt.auftragsnummer || '-'}</p>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg">
                    <p className="text-xs text-slate-500">Anlagedatum</p>
                    <p className="font-medium text-slate-900">{formatDate(projekt.anlegedatum)}</p>
                </div>
                {projekt.abschlussdatum && (
                    <div className="p-3 bg-slate-50 rounded-lg">
                        <p className="text-xs text-slate-500">Abschlussdatum</p>
                        <p className="font-medium text-slate-900">{formatDate(projekt.abschlussdatum)}</p>
                    </div>
                )}
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
            </div>

            {/* Kategorien */}
            {projekt.produktkategorien && projekt.produktkategorien.length > 0 && (
                <div className="mt-6 pt-6 border-t border-slate-100">
                    <h3 className="text-sm font-medium text-slate-900 mb-3">Kategorien</h3>
                    <div className="space-y-2">
                        {projekt.produktkategorien.map((k, index) => {
                            const kategorie = k.produktkategorie;
                            const verrechnungseinheit = typeof kategorie?.verrechnungseinheit === 'object'
                                ? kategorie.verrechnungseinheit?.anzeigename || kategorie.verrechnungseinheit?.name
                                : kategorie?.verrechnungseinheit;
                            return (
                                <div key={kategorie?.id || index} className="p-2 bg-rose-50 rounded-lg text-sm space-y-1">
                                    <div className="text-slate-900">{kategorie?.pfad || kategorie?.bezeichnung || 'Kategorie'}</div>
                                    <div className="text-rose-700 font-medium">{k.menge} {verrechnungseinheit || ''}</div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* Projektadresse mit Karte */}
            {(projekt.strasse || projekt.plz || projekt.ort || kundeDto?.strasse || kundeDto?.plz || kundeDto?.ort) && (
                <div className="mt-6 pt-6 border-t border-slate-100">
                    <h3 className="text-sm font-medium text-slate-900 mb-3 flex items-center gap-2">
                        <MapPin className="w-4 h-4 text-rose-500" />
                        Projektadresse
                    </h3>
                    <div className="p-3 bg-slate-50 rounded-lg mb-3">
                        <p className="font-medium text-slate-900">{projekt.strasse || kundeDto?.strasse || '-'}</p>
                        <p className="text-sm text-slate-600">
                            {projekt.plz || kundeDto?.plz} {projekt.ort || kundeDto?.ort}
                        </p>
                    </div>
                    <GoogleMapsEmbed
                        strasse={projekt.strasse || kundeDto?.strasse}
                        plz={projekt.plz || kundeDto?.plz}
                        ort={projekt.ort || kundeDto?.ort}
                        className="h-48"
                    />
                </div>
            )}

            {/* Notiz hinzufügen Modal */}
            <Dialog open={showNotizModal} onOpenChange={setShowNotizModal}>
                <DialogContent className="sm:max-w-md">
                    <DialogHeader>
                        <DialogTitle>{editingNotiz ? 'Notiz bearbeiten' : 'Neue Notiz'}</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <textarea
                            className="flex min-h-[120px] w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-600 focus-visible:ring-offset-2"
                            placeholder="Notiz eingeben..."
                            value={neueNotiz}
                            onChange={(e) => setNeueNotiz(e.target.value)}
                            autoFocus
                        />
                        <div className="space-y-3">
                            <label className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 bg-slate-50 cursor-pointer hover:bg-slate-100 transition-colors">
                                <input
                                    type="checkbox"
                                    id="mobileSichtbar"
                                    checked={mobileSichtbar}
                                    onChange={(e) => setMobileSichtbar(e.target.checked)}
                                    className="w-4 h-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
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
                        <Button
                            onClick={handleSaveNotiz}
                            disabled={savingNotiz || !neueNotiz.trim()}
                            className="bg-rose-600 hover:bg-rose-700 text-white"
                        >
                            {savingNotiz ? 'Speichern...' : 'Speichern'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
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

export default function ProjektEditor() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [viewMode, setViewMode] = useState<'list' | 'detail'>('list');
    const [projekte, setProjekte] = useState<Projekt[]>([]);
    const [selectedProjekt, setSelectedProjekt] = useState<ProjektDetail | null>(null);
    const [loading, setLoading] = useState(false);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showEditModal, setShowEditModal] = useState(false);

    // Filters
    const [filters, setFilters] = useState({
        q: "",
        kunde: "",
        status: "", // "bezahlt", "offen", ""
    });

    // Fetch List
    const loadProjekte = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", String(PAGE_SIZE));
            if (filters.q) params.set("q", filters.q);
            if (filters.kunde) params.set("kunde", filters.kunde);
            if (filters.status === 'bezahlt') params.set("bezahlt", "true");
            if (filters.status === 'offen') params.set("bezahlt", "false");

            const res = await fetch(`/api/projekte?${params.toString()}`);
            if (!res.ok) throw new Error("Fehler beim Laden");
            const data = await res.json();

            // Projekte sortieren: offene (nicht abgeschlossene) zuerst
            const sortedProjekte = Array.isArray(data.projekte) ? data.projekte : [];
            sortedProjekte.sort((a: Projekt, b: Projekt) => {
                // Offene (abgeschlossen=false) zuerst
                if (a.abgeschlossen === b.abgeschlossen) return 0;
                return a.abgeschlossen ? 1 : -1;
            });

            setProjekte(sortedProjekte);
            setTotal(typeof data.gesamt === "number" ? data.gesamt : 0);
        } catch (err) {
            console.error(err);
            setProjekte([]);
            setTotal(0);
        } finally {
            setLoading(false);
        }
    }, [page, filters]);

    useEffect(() => {
        if (viewMode === 'list') {
            loadProjekte();
        }
    }, [loadProjekte, viewMode]);

    // Deep-link: auto-open project from URL param ?projektId=123&tab=notizen
    const [deepLinkTab, setDeepLinkTab] = useState<ProjektDetailViewProps['initialTab']>(undefined);
    const lastProcessedProjektId = useRef<string | null>(null);
    useEffect(() => {
        const projektIdParam = searchParams.get('projektId');
        const tabParam = searchParams.get('tab') as ProjektDetailViewProps['initialTab'];
        if (!projektIdParam && !tabParam) return;
        // Skip if we already processed this exact projektId
        if (projektIdParam && lastProcessedProjektId.current === projektIdParam) return;
        const projektId = projektIdParam ? Number(projektIdParam) : null;
        if (projektIdParam && (isNaN(projektId!) || !projektId)) return;
        if (tabParam) setDeepLinkTab(tabParam);
        if (projektIdParam) lastProcessedProjektId.current = projektIdParam;
        if (projektId) {
            (async () => {
                try {
                    setLoading(true);
                    const res = await fetch(`/api/projekte/${projektId}`);
                    if (!res.ok) throw new Error('Fehler beim Laden der Details');
                    const data: ProjektDetail = await res.json();
                    setSelectedProjekt(data);
                    setViewMode('detail');
                } catch (err) {
                    console.error('Deep-link load error', err);
                } finally {
                    setLoading(false);
                }
            })();
        }
    }, [searchParams]);

    // Handlers
    const handleFilterChange = (key: string, value: string) => {
        setFilters((prev) => ({ ...prev, [key]: value }));
    };

    const handleFilterSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        loadProjekte();
    };

    const handleResetFilters = () => {
        setFilters({ q: "", kunde: "", status: "" });
        setPage(0);
    };

    const handleDetail = async (projekt: Projekt) => {
        try {
            setLoading(true);
            const res = await fetch(`/api/projekte/${projekt.id}`);
            if (!res.ok) throw new Error("Fehler beim Laden der Details");
            const data: ProjektDetail = await res.json();

            setSelectedProjekt(data);
            setViewMode('detail');
            setSearchParams({ projektId: String(data.id) }, { replace: true });
        } catch (err) {
            console.error("Detail load error", err);
            setSelectedProjekt(projekt as ProjektDetail);
            setViewMode('detail');
            setSearchParams({ projektId: String(projekt.id) }, { replace: true });
        } finally {
            setLoading(false);
        }
    };

    const handleEdit = () => {
        if (selectedProjekt) {
            setShowEditModal(true);
        }
    };

    // Toggle abgeschlossen status directly from card
    const handleToggleAbgeschlossen = async (projektId: number, abgeschlossen: boolean) => {
        try {
            // Find projekt to get required data
            const projekt = projekte.find(p => p.id === projektId);
            if (!projekt) return;

            const res = await fetch(`/api/projekte/${projektId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    bauvorhaben: projekt.bauvorhaben,
                    kunde: projekt.kunde,
                    kundennummer: projekt.kundennummer,
                    kundenId: projekt.kundenId,
                    auftragsnummer: projekt.auftragsnummer,
                    bruttoPreis: projekt.bruttoPreis,
                    strasse: projekt.strasse,
                    plz: projekt.plz,
                    ort: projekt.ort,
                    abgeschlossen: abgeschlossen
                })
            });

            if (res.ok) {
                // Update local state optimistically
                setProjekte(prev => prev.map(p =>
                    p.id === projektId ? { ...p, abgeschlossen } : p
                ));
            }
        } catch (err) {
            console.error('Fehler beim Aktualisieren:', err);
        }
    };

    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

    const statusText = useMemo(() => {
        if (loading) return 'Projekte werden geladen...';
        if (total === 0) return 'Keine Projekte gefunden.';
        const start = page * PAGE_SIZE + 1;
        const end = Math.min(start + projekte.length - 1, total);
        return `Zeige ${start}-${end} von ${total} Projekten`;
    }, [loading, total, page, projekte.length]);

    if (viewMode === 'detail' && selectedProjekt) {
        return (
            <>
                <ProjektDetailView
                    projekt={selectedProjekt}
                    initialTab={deepLinkTab}
                    onBack={() => {
                        setSelectedProjekt(null);
                        setViewMode('list');
                        setDeepLinkTab(undefined);
                        setSearchParams({}, { replace: true });
                    }}
                    onEdit={handleEdit}
                    onRefresh={async () => {
                        // Reload project details to stay in detail view
                        try {
                            const res = await fetch(`/api/projekte/${selectedProjekt.id}`);
                            if (res.ok) {
                                const updated = await res.json();
                                setSelectedProjekt(updated);
                            }
                        } catch (err) {
                            console.error('Projekt konnte nicht neu geladen werden:', err);
                        }
                    }}
                />

                {/* Edit Project Modal - auch in Detail-View */}
                <ProjektErstellenModal
                    isOpen={showEditModal}
                    onClose={() => setShowEditModal(false)}
                    onSuccess={async (projektId) => {
                        // Projekt-Details neu laden nach Bearbeitung
                        try {
                            const res = await fetch(`/api/projekte/${projektId}`);
                            if (res.ok) {
                                const updated = await res.json();
                                setSelectedProjekt(updated);
                            }
                        } catch (err) {
                            console.error('Projekt konnte nicht neu geladen werden:', err);
                        }
                    }}
                    editProjekt={{
                        id: selectedProjekt.id,
                        bauvorhaben: selectedProjekt.bauvorhaben || '',
                        kunde: selectedProjekt.kunde || '',
                        kundennummer: selectedProjekt.kundennummer || '',
                        kundenId: selectedProjekt.kundeDto?.id,
                        auftragsnummer: selectedProjekt.auftragsnummer || '',
                        bruttoPreis: selectedProjekt.bruttoPreis,
                        strasse: selectedProjekt.strasse,
                        plz: selectedProjekt.plz,
                        ort: selectedProjekt.ort,
                        abgeschlossen: selectedProjekt.abgeschlossen,
                        kundeDto: selectedProjekt.kundeDto,
                        produktkategorien: selectedProjekt.produktkategorien,
                        kundenEmails: selectedProjekt.kundenEmails,
                    }}
                />

            </>
        );
    }

    // ==================== LIST VIEW ====================
    return (
        <PageLayout
            ribbonCategory="Projektmanagement"
            title="Projektübersicht"
            subtitle="Übersicht und Verwaltung Ihrer Projekte."
            actions={
                <>
                    <Button size="sm" onClick={() => setShowCreateModal(true)} className="bg-rose-600 text-white hover:bg-rose-700">
                        <Plus className="w-4 h-4 mr-2" />
                        Neues Projekt
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => loadProjekte()}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >
            {/* Filter - volle Breite */}
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
                        <label className="block text-sm font-medium text-gray-700">Kunde</label>
                        <input
                            type="text"
                            className="filter-input w-full mt-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                            placeholder="Kundenname"
                            value={filters.kunde}
                            onChange={e => handleFilterChange('kunde', e.target.value)}
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Status</label>
                        <Select
                            className="mt-1"
                            options={[
                                { value: "", label: "Alle" },
                                { value: "offen", label: "Offen" },
                                { value: "bezahlt", label: "Bezahlt" }
                            ]}
                            value={filters.status}
                            onChange={(val) => handleFilterChange("status", val)}
                            placeholder="Alle"
                        />
                    </div>
                    <div className="flex items-end gap-3">
                        <Button type="submit" className="flex-1 bg-rose-600 text-white hover:bg-rose-700">Filtern</Button>
                        <Button type="button" variant="outline" className="flex-1" onClick={handleResetFilters}>Reset</Button>
                    </div>
                </form>
                <p className="text-xs text-gray-500 mt-3">Für Performance werden immer nur {PAGE_SIZE} Einträge auf einmal geladen.</p>
            </div>

            {/* Grid Content */}
            {loading ? (
                <div className="text-center py-8 text-slate-500">Projekte werden geladen...</div>
            ) : projekte.length === 0 ? (
                <div className="bg-white p-8 rounded-2xl text-center text-slate-500 border-dashed border-2">
                    <Briefcase className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                    Keine Projekte gefunden.
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {projekte.map((projekt) => (
                        <ProjektCard
                            key={projekt.id}
                            projekt={projekt}
                            onClick={() => handleDetail(projekt)}
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

            {/* Create Project Modal */}
            <ProjektErstellenModal
                isOpen={showCreateModal}
                onClose={() => setShowCreateModal(false)}
                onSuccess={() => {
                    loadProjekte();
                }}
            />

            {/* Edit Project Modal */}
            {selectedProjekt && (
                <ProjektErstellenModal
                    isOpen={showEditModal}
                    onClose={() => setShowEditModal(false)}
                    onSuccess={async (projektId) => {
                        // Projekt-Details neu laden nach Bearbeitung
                        try {
                            const res = await fetch(`/api/projekte/${projektId}`);
                            if (res.ok) {
                                const updated = await res.json();
                                setSelectedProjekt(updated);
                            }
                        } catch (err) {
                            console.error('Projekt konnte nicht neu geladen werden:', err);
                        }
                        loadProjekte();
                    }}
                    editProjekt={{
                        id: selectedProjekt.id,
                        bauvorhaben: selectedProjekt.bauvorhaben || '',
                        kunde: selectedProjekt.kunde || '',
                        kundennummer: selectedProjekt.kundennummer || '',
                        kundenId: selectedProjekt.kundeDto?.id,
                        auftragsnummer: selectedProjekt.auftragsnummer || '',
                        bruttoPreis: selectedProjekt.bruttoPreis,
                        strasse: selectedProjekt.strasse,
                        plz: selectedProjekt.plz,
                        ort: selectedProjekt.ort,
                        abgeschlossen: selectedProjekt.abgeschlossen,
                        kundeDto: selectedProjekt.kundeDto,
                        produktkategorien: selectedProjekt.produktkategorien,
                        kundenEmails: selectedProjekt.kundenEmails,
                    }}
                />
            )}
        </PageLayout>
    );
}

function ProjektCard({ projekt, onClick, onToggleAbgeschlossen }: {
    projekt: Projekt;
    onClick: () => void;
    onToggleAbgeschlossen?: (projektId: number, abgeschlossen: boolean) => void;
}) {
    const formatCurrency = (val?: number) =>
        new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val || 0);

    const formatDate = (dateStr?: string) => {
        if (!dateStr) return '-';
        return new Date(dateStr).toLocaleDateString('de-DE');
    };

    const handleCheckboxClick = (e: React.MouseEvent) => {
        e.stopPropagation(); // Prevent card click
    };

    const handleCheckboxChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        e.stopPropagation();
        if (onToggleAbgeschlossen) {
            onToggleAbgeschlossen(projekt.id, e.target.checked);
        }
    };

    return (
        <Card
            className={cn(
                "group relative cursor-pointer hover:shadow-md transition-all border-slate-200 bg-white overflow-hidden",
                projekt.abgeschlossen && "opacity-60 bg-slate-50"
            )}
            onClick={onClick}
        >
            <div className="p-4 space-y-3">
                <div className="flex items-start justify-between">
                    <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                            <span className={cn(
                                "text-xs font-semibold tracking-wider uppercase px-2 py-0.5 rounded-full",
                                projekt.bezahlt
                                    ? "bg-green-50 text-green-700"
                                    : "bg-amber-50 text-amber-700"
                            )}>
                                {projekt.bezahlt ? 'Bezahlt' : 'Offen'}
                            </span>
                            {projekt.abgeschlossen && (
                                <span className="text-xs font-semibold tracking-wider uppercase px-2 py-0.5 rounded-full bg-slate-200 text-slate-600">
                                    Beendet
                                </span>
                            )}
                        </div>
                        <h3 className="font-semibold text-slate-900 mt-2 truncate text-base" title={projekt.bauvorhaben}>
                            {projekt.bauvorhaben || "Unbenannt"}
                        </h3>
                        <p className="text-sm text-slate-500 truncate">{projekt.kunde || "Kein Kunde"}</p>
                    </div>
                    {/* Checkbox zum Beenden */}
                    <div
                        className="shrink-0 ml-2"
                        onClick={handleCheckboxClick}
                        title={projekt.abgeschlossen ? "Projekt ist beendet" : "Als beendet markieren"}
                    >
                        <label className="flex items-center gap-1.5 cursor-pointer p-1.5 rounded-lg hover:bg-slate-100 transition-colors">
                            <input
                                type="checkbox"
                                checked={projekt.abgeschlossen || false}
                                onChange={handleCheckboxChange}
                                className="w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                            />
                            <span className="text-xs text-slate-500 hidden sm:inline">Beendet</span>
                        </label>
                    </div>
                </div>

                <div className="space-y-1 pt-2 border-t border-slate-50">
                    {projekt.auftragsnummer && (
                        <div className="flex items-center gap-2 text-sm text-slate-600">
                            <FileText className="w-4 h-4 text-slate-400 shrink-0" />
                            <span className="truncate">{projekt.auftragsnummer}</span>
                        </div>
                    )}
                    <div className="flex items-center gap-2 text-sm text-slate-600">
                        <Calendar className="w-4 h-4 text-slate-400 shrink-0" />
                        <span>{formatDate(projekt.anlegedatum)}</span>
                    </div>
                    <div className="flex items-center gap-2 text-sm font-medium text-rose-600">
                        <Euro className="w-4 h-4 shrink-0" />
                        <span>{formatCurrency(projekt.bruttoPreis)}</span>
                    </div>
                </div>
            </div>
        </Card>
    );
}

export { ProjektEditor };
