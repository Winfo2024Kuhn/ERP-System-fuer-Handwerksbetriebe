import { useState, useEffect, useCallback, useRef, useMemo, Fragment } from 'react';
import { Pencil, Plus, Check, X } from 'lucide-react';
import { useEditor } from '@tiptap/react';
import { TiptapToolbar } from '../TiptapEditor';
import {
    type AusgangsGeschaeftsDokument,
    type AusgangsGeschaeftsDokumentTyp,
    type AusgangsGeschaeftsDokumentErstellen,
    AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN,
    type FormBlock,
    type FormBlockType
} from '../../types';
import { notifyDokumentChanged } from '../../lib/dokumentChannel';
import {
    DndContext,
    DragOverlay,
    closestCenter,
    pointerWithin,
    KeyboardSensor,
    PointerSensor,
    useSensor,
    useSensors,
} from '@dnd-kit/core';
import type { DragEndEvent, DragStartEvent, CollisionDetection } from '@dnd-kit/core';
import {
    arrayMove,
    SortableContext,
    sortableKeyboardCoordinates,
    verticalListSortingStrategy,
} from '@dnd-kit/sortable';

import type { DocBlock, DocumentEditorProps, KontextDaten, TextbausteinApiDto, LeistungApiDto, ArbeitszeitartApiDto, EditorInstance } from './types';
import { buildAdresse, buildAdresseFromAngebot, blocksToHtml, calculateNetto, extractFontSizeFromHtml, extractBoldFromHtml, unitMap, getAllServiceBlocks, findBlockContainer, flattenBlocksForPdf, buildPositionMap, computeClosureSummary } from './helpers';
import { DocumentEditorHeader } from './DocumentEditorHeader';
import { ServiceBlock } from './ServiceBlock';
import { TextBlock } from './TextBlock';
import { ClosureBlock } from './ClosureBlock';
import { SeparatorBlock } from './SeparatorBlock';
import { SectionHeaderBlock } from './SectionHeaderBlock';
import { SortableBlock } from './SortableBlock';
import { SummenFooter } from './SummenFooter';
import { LivePreviewPanel } from './LivePreviewPanel';
import { ExportWarningModal, UnsavedChangesModal, PrintOptionsModal, TextbausteinPickerModal, LeistungPickerModal, StundensatzPickerModal } from './Modals';
import { RabattDialog } from './RabattDialog';
import { EmailComposeModal } from '../EmailComposeModal';
import { anredeEnumToText } from '../EmailComposeForm';
import { EmailFormatDialog, type PdfFormat } from './EmailFormatDialog';
import { useToast } from '../ui/toast';

/** Inline-editable Rechnungsadresse – changes only the document, not the customer table */
function RechnungsadresseBlock({
    value,
    isLocked,
    onChange,
}: {
    value: string;
    isLocked: boolean;
    onChange: (newValue: string) => void;
}) {
    const [editing, setEditing] = useState(false);
    const [draft, setDraft] = useState(value);
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    useEffect(() => {
        if (editing && textareaRef.current) {
            textareaRef.current.focus();
            // auto-resize
            textareaRef.current.style.height = 'auto';
            textareaRef.current.style.height = textareaRef.current.scrollHeight + 'px';
        }
    }, [editing]);

    const startEdit = () => {
        if (isLocked) return;
        setDraft(value);
        setEditing(true);
    };

    const confirmEdit = () => {
        onChange(draft);
        setEditing(false);
    };

    const cancelEdit = () => {
        setDraft(value);
        setEditing(false);
    };

    if (editing) {
        return (
            <div className="bg-white rounded-lg border-2 border-rose-300 p-3">
                <label className="block text-[10px] font-semibold text-rose-500 uppercase tracking-wider mb-1">
                    Rechnungsadresse bearbeiten
                </label>
                <textarea
                    ref={textareaRef}
                    value={draft}
                    onChange={(e) => {
                        setDraft(e.target.value);
                        e.target.style.height = 'auto';
                        e.target.style.height = e.target.scrollHeight + 'px';
                    }}
                    onKeyDown={(e) => {
                        if (e.key === 'Escape') cancelEdit();
                        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) confirmEdit();
                    }}
                    className="w-full text-sm text-slate-700 leading-relaxed border border-slate-200 rounded-md px-2 py-1.5 resize-none focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300"
                    rows={3}
                    placeholder="Adresse eingeben…"
                />
                <p className="text-[10px] text-slate-400 mt-1">Strg+Enter zum Bestätigen, Escape zum Abbrechen</p>
                <div className="flex gap-1.5 mt-2">
                    <button
                        onClick={confirmEdit}
                        className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-medium bg-rose-600 text-white rounded-md hover:bg-rose-700 transition-colors"
                    >
                        <Check className="w-3 h-3" /> Übernehmen
                    </button>
                    <button
                        onClick={cancelEdit}
                        className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-medium text-slate-600 bg-slate-100 rounded-md hover:bg-slate-200 transition-colors"
                    >
                        <X className="w-3 h-3" /> Abbrechen
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="bg-white rounded-lg border border-slate-200 p-3 group">
            <div className="flex items-center justify-between mb-1">
                <label className="block text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
                    Rechnungsadresse
                </label>
                {!isLocked && (
                    <button
                        onClick={startEdit}
                        className="opacity-0 group-hover:opacity-100 transition-opacity p-1 hover:bg-slate-100 rounded-md"
                        title="Rechnungsadresse für dieses Dokument bearbeiten"
                    >
                        <Pencil className="w-3.5 h-3.5 text-slate-400 hover:text-rose-600" />
                    </button>
                )}
            </div>
            <div className="text-sm text-slate-700 whitespace-pre-line leading-relaxed">
                {value || 'Keine Adresse verfügbar'}
            </div>
        </div>
    );
}

export default function DocumentEditor({ projektId, angebotId, dokumentId, initialDokumentTyp, onClose }: DocumentEditorProps) {
    const toast = useToast();
    // --- State ---
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [dokumentTyp, setDokumentTyp] = useState<AusgangsGeschaeftsDokumentTyp>(initialDokumentTyp || 'ANGEBOT');
    const [dokumentNummer, setDokumentNummer] = useState<string>('');
    const [betreff, setBetreff] = useState('');
    const [datum, setDatum] = useState(new Date().toISOString().split('T')[0]);
    const [blocks, setBlocks] = useState<DocBlock[]>([]);
    const [kontextDaten, setKontextDaten] = useState<KontextDaten>({});
    const [dokument, setDokument] = useState<AusgangsGeschaeftsDokument | null>(null);
    const [showExportWarning, setShowExportWarning] = useState(false);
    const [showExportFormatDialog, setShowExportFormatDialog] = useState(false);
    const [exportLoading, setExportLoading] = useState(false);
    const [showPrintOptions, setShowPrintOptions] = useState(false);
    const [showUnsavedWarning, setShowUnsavedWarning] = useState(false);
    const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
    const [saveSuccess, setSaveSuccess] = useState(false);
    const lastSavedStateRef = useRef<string>('');

    // Vorlagen
    const [textbausteine, setTextbausteine] = useState<TextbausteinApiDto[]>([]);
    const [leistungen, setLeistungen] = useState<LeistungApiDto[]>([]);
    const [arbeitszeitarten, setArbeitszeitarten] = useState<ArbeitszeitartApiDto[]>([]);

    // Preview State
    const [showPreview] = useState(true);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [previewLoading, setPreviewLoading] = useState(false);
    const [previewStale, setPreviewStale] = useState(false);

    // UI Layout State


    // Picker Modal State
    const [showTextbausteinPicker, setShowTextbausteinPicker] = useState(false);
    const [showLeistungPicker, setShowLeistungPicker] = useState(false);
    const [showStundensatzPicker, setShowStundensatzPicker] = useState(false);

    // Global Toolbar State
    const [activeEditor, setActiveEditor] = useState<ReturnType<typeof useEditor> | null>(null);
    const [activeEditorId, setActiveEditorId] = useState<string | null>(null);
    const editorRefs = useRef<Record<string, EditorInstance | null>>({});

    // Global Rabatt
    const [globalRabatt, setGlobalRabatt] = useState<number>(0);
    const [showRabattDialog, setShowRabattDialog] = useState(false);

    // Abrechnungsverlauf: already-billed amount by other invoices (for Schlussrechnung etc.)
    const [bereitsAbgerechnetDurchAndere, setBereitsAbgerechnetDurchAndere] = useState<number | null>(null);
    // Basisdokument-Nettobetrag (Gesamtauftragssumme aus AB/Angebot)
    const [basisdokumentBetragNetto, setBasisdokumentBetragNetto] = useState<number | null>(null);
    // Detaillierte Abrechnungspositionen für die ClosureBlock-Anzeige
    const [abrechnungsPositionen, setAbrechnungsPositionen] = useState<Array<{
        dokumentNummer: string;
        typ: string;
        datum: string;
        betragNetto: number;
        abschlagsNummer?: number;
    }>>([]);

    // Abschlagsrechnung: user-defined installment amount
    const [abschlagBetragNetto, setAbschlagBetragNetto] = useState<number | null>(null);
    // Abschlagsrechnung: Eingabemodus und Originalwert
    const [abschlagInfo, setAbschlagInfo] = useState<{ modus: string; eingabeWert: number } | null>(null);

    // Email Versand State
    const [showEmailModal, setShowEmailModal] = useState(false);
    const [showFormatDialog, setShowFormatDialog] = useState(false);
    const [emailLoading, setEmailLoading] = useState(false);
    const [emailAttachments, setEmailAttachments] = useState<File[]>([]);
    const [emailBody, setEmailBody] = useState<string>('');

    // GAEB Import
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [importToast, setImportToast] = useState<{ type: 'success' | 'error'; message: string; details?: string } | null>(null);
    const importToastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    /** Alle Rechnungstypen die nach GoBD beim Export/Druck gesperrt werden müssen */
    const invoiceTypes: AusgangsGeschaeftsDokumentTyp[] = [
        'RECHNUNG', 'TEILRECHNUNG', 'ABSCHLAGSRECHNUNG', 'SCHLUSSRECHNUNG', 'GUTSCHRIFT', 'STORNO'
    ];
    const isLocked = !!(
        (dokument?.gebucht || dokument?.storniert) &&
        dokument?.typ &&
        invoiceTypes.includes(dokument.typ)
    );
    const currentDokumentTyp = dokument?.typ ?? dokumentTyp;
    const showFinalizationPrompt = invoiceTypes.includes(currentDokumentTyp);

    // --- Placeholders ---
    const replacePlaceholders = useCallback((text: string, isPreview: boolean = true): string => {
        if (!text) return text;
        const dokumentTypLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp;
        const anredeMap: Record<string, string> = {
            'HERR': 'Sehr geehrter Herr',
            'FRAU': 'Sehr geehrte Frau',
            'DIVERS': 'Sehr geehrte Damen und Herren',
            'FIRMA': 'Sehr geehrte Damen und Herren',
            'HERR_FRAU': 'Sehr geehrte Frau, sehr geehrter Herr'
        };
        const anredeValue = kontextDaten.anrede || '';
        const anredeDisplay = anredeMap[anredeValue.toUpperCase()] ?? anredeValue;

        const dokumentnummerValue = dokumentNummer || (isPreview ? 'VORSCHAU' : 'ENTWURF');
        const dataMap: Record<string, string> = {
            'KUNDENNAME': kontextDaten.kundenName || '',
            'KUNDENADRESSE': kontextDaten.rechnungsadresse || '',
            'KUNDENNUMMER': kontextDaten.kundennummer || '',
            'PROJEKTNUMMER': kontextDaten.projektnummer || '',
            'DOKUMENTNUMMER': dokumentnummerValue,
            'RECHNUNGSNUMMER': dokumentnummerValue, // Alias für DOKUMENTNUMMER
            'DATUM': datum ? new Date(datum).toLocaleDateString('de-DE') : new Date().toLocaleDateString('de-DE'),
            'BETREFF': betreff || '',
            'ANREDE': anredeDisplay,
            'ANSPRECHPARTNER': kontextDaten.ansprechpartner || '',
            'DOKUMENTTYP': dokumentTypLabel,
            'BAUVORHABEN': kontextDaten.projektBauvorhaben || betreff || '',
            'BEZUGSDOKUMENT': kontextDaten.bezugsdokument || '',
            'BEZUGSDOKUMENTNUMMER': kontextDaten.bezugsdokument || '',
            'BEZUGSDOKUMENTTYP': kontextDaten.bezugsdokumentTyp || '',
            'ZAHLUNGSZIEL': (() => {
                const days = kontextDaten.zahlungsziel ?? 8;
                const d = datum ? new Date(datum) : new Date();
                d.setDate(d.getDate() + days);
                return d.toLocaleDateString('de-DE');
            })()
        };

        return text.replace(/\{\{\s*([a-zA-Z0-9_äöüÄÖÜß]+)\s*\}\}/g, (match, key) => {
            const upperKey = key.toUpperCase();
            return dataMap[upperKey] !== undefined ? dataMap[upperKey] : match;
        });
    }, [kontextDaten, dokumentTyp, dokumentNummer, datum, betreff]);

    // --- GAEB Import ---
    const handleGaebImportClick = () => {
        if (isLocked) return;
        fileInputRef.current?.click();
    };

    const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
        if (isLocked) return;
        const file = event.target.files?.[0];
        if (!file) return;
        if (event.target.value) event.target.value = '';

        const formData = new FormData();
        formData.append('file', file);
        setLoading(true);

        try {
            const res = await fetch('/api/import/gaeb', {
                method: 'POST',
                body: formData
            });

            if (res.ok) {
                const newBlocks: DocBlock[] = await res.json();

                const mapBlock = (b: any): DocBlock => ({
                    ...b,
                    id: crypto.randomUUID(),
                    type: b.type === 'TEXT' ? 'TEXT' : (b.type === 'SECTION_HEADER' ? 'SECTION_HEADER' : 'SERVICE'),
                    quantity: b.type === 'TEXT' || b.type === 'SECTION_HEADER' ? undefined : (Number(b.quantity) || 1),
                    price: b.type === 'TEXT' || b.type === 'SECTION_HEADER' ? undefined : (Number(b.price) || 0),
                    content: b.type === 'TEXT' ? (b.content || '') : undefined,
                    sectionLabel: b.type === 'SECTION_HEADER' ? (b.sectionLabel || 'Bauabschnitt') : undefined,
                    children: b.type === 'SECTION_HEADER' && Array.isArray(b.children)
                        ? b.children.map((child: any) => mapBlock(child))
                        : undefined,
                    fontSize: b.type === 'SECTION_HEADER' ? undefined : 10,
                    fett: b.type === 'SECTION_HEADER' ? undefined : false
                });

                const mappedBlocks: DocBlock[] = newBlocks.map(mapBlock);

                // Count total positions (including children in sections)
                let totalCount = 0;
                for (const b of mappedBlocks) {
                    if (b.type === 'SECTION_HEADER' && b.children) {
                        totalCount += b.children.length;
                    } else {
                        totalCount++;
                    }
                }

                setBlocks(current => [...current, ...mappedBlocks]);
                const sectionCount = mappedBlocks.filter(b => b.type === 'SECTION_HEADER').length;
                showImportToast('success',
                    'GAEB Import erfolgreich',
                    sectionCount > 0
                        ? `${totalCount} Positionen in ${sectionCount} Bauabschnitt${sectionCount !== 1 ? 'en' : ''} importiert.`
                        : `${totalCount} Positionen importiert.`
                );
            } else {
                throw new Error('Import fehlgeschlagen');
            }
        } catch (error) {
            console.error('GAEB Import Error:', error);
            showImportToast('error', 'GAEB Import fehlgeschlagen', 'Die Datei konnte nicht verarbeitet werden.');
        } finally {
            setLoading(false);
        }
    };

    // --- Import Toast Helper ---
    const showImportToast = (type: 'success' | 'error', message: string, details?: string) => {
        if (importToastTimer.current) clearTimeout(importToastTimer.current);
        setImportToast({ type, message, details });
        importToastTimer.current = setTimeout(() => setImportToast(null), 5000);
    };

    // --- Load Kontext ---
    useEffect(() => {
        const loadKontext = async () => {
            try {
                if (projektId) {
                    const res = await fetch(`/api/projekte/${projektId}`);
                    if (res.ok) {
                        const projekt = await res.json();
                        const emails: string[] = [];
                        if (projekt.kundenEmails) projekt.kundenEmails.forEach((e: string) => { if (e && !emails.includes(e)) emails.push(e); });
                        if (projekt.kundeDto?.kundenEmails) projekt.kundeDto.kundenEmails.forEach((e: string) => { if (e && !emails.includes(e)) emails.push(e); });
                        setKontextDaten({
                            projektnummer: projekt.auftragsnummer,
                            projektBauvorhaben: projekt.bauvorhaben,
                            kundennummer: projekt.kundennummer,
                            kundenName: projekt.kunde,
                            kundeId: projekt.kundenId,
                            rechnungsadresse: buildAdresse(projekt.kundeDto),
                            anrede: projekt.kundeDto?.anrede,
                            ansprechpartner: projekt.kundeDto?.ansprechspartner || projekt.kundeDto?.ansprechpartner,
                            kundenEmails: emails,
                            zahlungsziel: projekt.kundeDto?.zahlungsziel ?? 8,
                        });
                        setBetreff(projekt.bauvorhaben || '');
                    }
                } else if (angebotId) {
                    const res = await fetch(`/api/angebote/${angebotId}`);
                    if (res.ok) {
                        const angebot = await res.json();
                        const isFollowUp = dokumentTyp !== 'ANGEBOT';
                        const emails: string[] = [];
                        if (angebot.kundenEmails) angebot.kundenEmails.forEach((e: string) => { if (e && !emails.includes(e)) emails.push(e); });
                        setKontextDaten({
                            kundennummer: angebot.kundennummer,
                            kundenName: angebot.kundenName,
                            kundeId: angebot.kundenId,
                            rechnungsadresse: buildAdresseFromAngebot(angebot),
                            anrede: angebot.kundenAnrede || angebot.anrede,
                            ansprechpartner: angebot.kundenAnsprechpartner || angebot.kundenAnsprechspartner,
                            kundenEmails: emails,
                            zahlungsziel: angebot.zahlungsziel ?? 8,
                            ...(isFollowUp ? { bezugsdokument: angebot.angebotsnummer, bezugsdokumentTyp: 'Angebot' } : {})
                        });
                        setBetreff(angebot.bauvorhaben || '');
                    }
                }
            } catch (err) {
                console.error('Fehler beim Laden der Kontext-Daten:', err);
            }
        };
        loadKontext();
    }, [projektId, angebotId]);

    // --- Load Document ---
    useEffect(() => {
        const loadDokument = async () => {
            if (!dokumentId) {
                setLoading(false);
                return;
            }
            try {
                const res = await fetch(`/api/ausgangs-dokumente/${dokumentId}`);
                if (res.ok) {
                    const data: AusgangsGeschaeftsDokument = await res.json();
                    setDokument(data);
                    setDokumentNummer(data.dokumentNummer);
                    setDokumentTyp(data.typ);
                    setBetreff(data.betreff || '');
                    setDatum(data.datum);

                    if (data.gebucht) {
                        setKontextDaten({
                            kundennummer: data.kundennummer,
                            kundenName: data.kundenName,
                            rechnungsadresse: data.rechnungsadresse,
                            projektnummer: data.projektnummer,
                            projektBauvorhaben: data.projektBauvorhaben,
                            bezugsdokument: data.vorgaengerNummer,
                            zahlungsziel: data.zahlungszielTage ?? 8
                        });
                    } else {
                        if (data.projektId) {
                            const projRes = await fetch(`/api/projekte/${data.projektId}`);
                            if (projRes.ok) {
                                const projekt = await projRes.json();
                                setKontextDaten({
                                    projektnummer: projekt.auftragsnummer,
                                    projektBauvorhaben: projekt.bauvorhaben,
                                    kundennummer: projekt.kundennummer,
                                    kundenName: projekt.kunde,
                                    kundeId: projekt.kundenId,
                                    rechnungsadresse: buildAdresse(projekt.kundeDto),
                                    anrede: projekt.kundeDto?.anrede,
                                    ansprechpartner: projekt.kundeDto?.ansprechspartner || projekt.kundeDto?.ansprechpartner,
                                    bezugsdokument: data.vorgaengerNummer,
                                    zahlungsziel: data.zahlungszielTage ?? projekt.kundeDto?.zahlungsziel ?? 8
                                });
                            }
                        } else if (data.angebotId) {
                            const angRes = await fetch(`/api/angebote/${data.angebotId}`);
                            if (angRes.ok) {
                                const angebot = await angRes.json();
                                const isFollowUp = data.typ !== 'ANGEBOT';
                                setKontextDaten({
                                    kundennummer: angebot.kundennummer,
                                    kundenName: angebot.kundenName,
                                    kundeId: angebot.kundenId,
                                    rechnungsadresse: buildAdresseFromAngebot(angebot),
                                    anrede: angebot.kundenAnrede || angebot.anrede,
                                    ansprechpartner: angebot.kundenAnsprechpartner || angebot.kundenAnsprechspartner,
                                    zahlungsziel: data.zahlungszielTage ?? angebot.zahlungsziel ?? 8,
                                    ...(isFollowUp ? { bezugsdokument: angebot.angebotsnummer, bezugsdokumentTyp: 'Angebot' } : {})
                                });
                            }
                        } else if (data.kundeId) {
                            const kundeRes = await fetch(`/api/kunden/${data.kundeId}`);
                            if (kundeRes.ok) {
                                const kunde = await kundeRes.json();
                                setKontextDaten({
                                    kundennummer: kunde.kundennummer || data.kundennummer,
                                    kundenName: kunde.name || `${kunde.vorname || ''} ${kunde.nachname || ''}`.trim(),
                                    kundeId: data.kundeId,
                                    rechnungsadresse: buildAdresse(kunde),
                                    anrede: kunde.anrede,
                                    ansprechpartner: kunde.ansprechspartner || kunde.ansprechpartner,
                                    bezugsdokument: data.vorgaengerNummer,
                                    zahlungsziel: data.zahlungszielTage ?? kunde.zahlungsziel ?? 8
                                });
                            }
                        } else {
                            setKontextDaten({
                                kundennummer: data.kundennummer,
                                kundenName: data.kundenName,
                                rechnungsadresse: data.rechnungsadresse,
                                projektnummer: data.projektnummer,
                                projektBauvorhaben: data.projektBauvorhaben,
                                bezugsdokument: data.vorgaengerNummer,
                                zahlungsziel: data.zahlungszielTage ?? 8
                            });
                        }
                    }

                    // Fetch predecessor type
                    if (data.angebotId && !data.gebucht) {
                        // Already loaded above
                    } else if (data.angebotId && data.typ !== 'ANGEBOT') {
                        setKontextDaten(prev => ({ ...prev, bezugsdokumentTyp: 'Angebot' }));
                    } else if (data.vorgaengerId) {
                        fetch(`/api/ausgangs-dokumente/${data.vorgaengerId}`)
                            .then(r => r.ok ? r.json() : null)
                            .then(vorgaenger => {
                                if (vorgaenger) {
                                    const typLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === vorgaenger.typ)?.label || vorgaenger.typ;
                                    setKontextDaten(prev => ({
                                        ...prev,
                                        bezugsdokumentTyp: typLabel,
                                        bezugsdokument: prev.bezugsdokument || vorgaenger.dokumentNummer
                                    }));
                                }
                            })
                            .catch(console.error);
                    }

                    let loadedBlocks: DocBlock[] = [];
                    let loadedGlobalRabatt = 0;
                    if (data.positionenJson) {
                        try {
                            const parsed = JSON.parse(data.positionenJson);
                            if (Array.isArray(parsed)) {
                                // Legacy format: plain array of blocks
                                loadedBlocks = parsed;
                            } else if (parsed && Array.isArray(parsed.blocks)) {
                                // New format: { blocks, globalRabatt, abschlagInfo }
                                loadedBlocks = parsed.blocks;
                                loadedGlobalRabatt = parsed.globalRabatt || 0;
                                if (parsed.abschlagInfo) {
                                    setAbschlagInfo(parsed.abschlagInfo);
                                }
                            }
                            setBlocks(loadedBlocks.filter(b => b.type !== 'CLOSURE'));
                            setGlobalRabatt(loadedGlobalRabatt);
                        } catch (e) {
                            console.error('Fehler beim Parsen der Positionen:', e);
                        }
                    }

                    // Abschlagsbetrag aus Dokument laden
                    if (data.typ === 'ABSCHLAGSRECHNUNG' && data.betragNetto != null) {
                        setAbschlagBetragNetto(data.betragNetto);
                    }

                    // Reset saved-state ref so loaded data doesn't count as "unsaved"
                    setTimeout(() => {
                        lastSavedStateRef.current = JSON.stringify({
                            blocks: loadedBlocks,
                            datum: data.datum,
                            betreff: data.betreff || '',
                            dokumentTyp: data.typ
                        });
                        setHasUnsavedChanges(false);
                    }, 0);

                    // Abrechnungsverlauf laden für alle Rechnungstypen mit Basisdokument
                    const rechnungsTypenMitAbzug: AusgangsGeschaeftsDokumentTyp[] = ['SCHLUSSRECHNUNG', 'ABSCHLAGSRECHNUNG', 'TEILRECHNUNG'];
                    if (data.vorgaengerId && rechnungsTypenMitAbzug.includes(data.typ)) {
                        try {
                            const verlaufRes = await fetch(`/api/ausgangs-dokumente/${data.vorgaengerId}/abrechnungsverlauf`);
                            if (verlaufRes.ok) {
                                const verlauf = await verlaufRes.json();
                                // Sum of all other non-stornierte invoices (excluding current document)
                                const andereAbgerechnet = (verlauf.positionen || []).reduce(
                                    (sum: number, pos: any) => {
                                        if (pos.id !== data.id && !pos.storniert) {
                                            return sum + (pos.betragNetto || 0);
                                        }
                                        return sum;
                                    }, 0
                                );
                                setBereitsAbgerechnetDurchAndere(andereAbgerechnet);
                                // Basisdokument-Betrag speichern
                                if (verlauf.basisdokumentBetragNetto != null) {
                                    setBasisdokumentBetragNetto(verlauf.basisdokumentBetragNetto);
                                }
                                // Detaillierte Positionen speichern für ClosureBlock-Anzeige
                                const anderePosDetails = (verlauf.positionen || [])
                                    .filter((pos: any) => pos.id !== data.id && !pos.storniert)
                                    .map((pos: any) => ({
                                        dokumentNummer: pos.dokumentNummer,
                                        typ: pos.typ,
                                        datum: pos.datum,
                                        betragNetto: pos.betragNetto || 0,
                                        abschlagsNummer: pos.abschlagsNummer,
                                    }));
                                setAbrechnungsPositionen(anderePosDetails);
                            }
                        } catch (e) {
                            console.error('Abrechnungsverlauf konnte nicht geladen werden:', e);
                        }
                    }
                }
            } catch (err) {
                console.error('Fehler beim Laden des Dokuments:', err);
            } finally {
                setLoading(false);
            }
        };
        loadDokument();
    }, [dokumentId]);

    // --- Load Vorlagen ---
    useEffect(() => {
        const loadVorlagen = async () => {
            try {
                const [tbRes, lRes, azRes] = await Promise.all([
                    fetch('/api/textbausteine'),
                    fetch('/api/leistungen'),
                    fetch('/api/arbeitszeitarten')
                ]);
                if (tbRes.ok) setTextbausteine(await tbRes.json());
                if (lRes.ok) setLeistungen(await lRes.json());
                if (azRes.ok) setArbeitszeitarten(await azRes.json());
            } catch (err) {
                console.error('Fehler beim Laden der Vorlagen:', err);
            } finally {
                setLoading(false);
            }
        };
        loadVorlagen();
    }, []);

    // Textbausteine are NOT auto-inserted for new documents.
    // Users add them manually via the "Textbaustein" picker button in the toolbar.

    const syncDocumentIdInUrl = useCallback((savedDocumentId?: number) => {
        if (!savedDocumentId) return;
        try {
            const url = new URL(window.location.href);
            if (!url.pathname.endsWith('/dokument-editor')) return;

            const idAsString = String(savedDocumentId);
            if (url.searchParams.get('dokumentId') === idAsString) return;

            url.searchParams.set('dokumentId', idAsString);
            window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
        } catch (err) {
            console.warn('Konnte dokumentId nicht in URL synchronisieren:', err);
        }
    }, []);

    // --- Save ---
    const handleSave = useCallback(async () => {
        if (isLocked) return;
        setSaving(true);
        try {
            const htmlInhalt = blocksToHtml(blocks);
            const blockNetto = calculateNetto(blocks);
            // Für Schlussrechnung: effektiven Restbetrag verwenden (Blocksumme minus bereits abgerechnete)
            let betragNetto = blockNetto;
            if (dokumentTyp === 'SCHLUSSRECHNUNG' && bereitsAbgerechnetDurchAndere !== null) {
                betragNetto = blockNetto - bereitsAbgerechnetDurchAndere;
            } else if (dokumentTyp === 'ABSCHLAGSRECHNUNG' && abschlagBetragNetto !== null) {
                // Abschlagsrechnung: benutzerdefinierten Abschlagsbetrag verwenden
                betragNetto = abschlagBetragNetto;
            }
            const positionenData = JSON.stringify({
                blocks,
                globalRabatt,
                ...(abschlagInfo ? { abschlagInfo } : {})
            });

            if (dokument?.id) {
                const res = await fetch(`/api/ausgangs-dokumente/${dokument.id}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        datum,
                        betreff,
                        betragNetto,
                        htmlInhalt,
                        positionenJson: positionenData
                    })
                });
                if (res.ok) {
                    const updated = await res.json();
                    setDokument(updated);
                    setDokumentNummer(updated.dokumentNummer);
                    syncDocumentIdInUrl(updated.id);
                    const currentState = JSON.stringify({ blocks, datum, betreff, dokumentTyp });
                    lastSavedStateRef.current = currentState;
                    setHasUnsavedChanges(false);
                    setSaveSuccess(true);
                    setTimeout(() => setSaveSuccess(false), 2000);
                    notifyDokumentChanged({ projektId, angebotId, dokumentId: updated.id });
                } else {
                    const errorText = await res.text();
                    console.error('Fehler beim Speichern:', res.status, errorText);
                    alert(`Fehler beim Speichern: ${errorText || res.statusText}`);
                }
            } else {
                const dto: AusgangsGeschaeftsDokumentErstellen = {
                    typ: dokumentTyp,
                    datum,
                    betreff,
                    betragNetto,
                    htmlInhalt,
                    positionenJson: positionenData,
                    projektId,
                    angebotId
                };
                const res = await fetch('/api/ausgangs-dokumente', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(dto)
                });
                if (res.ok) {
                    const created = await res.json();
                    setDokument(created);
                    setDokumentNummer(created.dokumentNummer);
                    syncDocumentIdInUrl(created.id);
                    const currentState = JSON.stringify({ blocks, datum, betreff, dokumentTyp });
                    lastSavedStateRef.current = currentState;
                    setHasUnsavedChanges(false);
                    setSaveSuccess(true);
                    setTimeout(() => setSaveSuccess(false), 2000);
                    notifyDokumentChanged({ projektId, angebotId, dokumentId: created.id });
                }
            }
        } catch (err) {
            console.error('Fehler beim Speichern:', err);
        } finally {
            setSaving(false);
        }
    }, [dokument, dokumentTyp, datum, betreff, blocks, projektId, angebotId, isLocked, syncDocumentIdInUrl, bereitsAbgerechnetDurchAndere, globalRabatt]);

    // --- Change Detection ---
    useEffect(() => {
        const currentState = JSON.stringify({ blocks, datum, betreff, dokumentTyp });
        if (lastSavedStateRef.current === '') {
            lastSavedStateRef.current = currentState;
            return;
        }
        setHasUnsavedChanges(currentState !== lastSavedStateRef.current);
    }, [blocks, datum, betreff, dokumentTyp]);

    // --- Auto-Save ---
    useEffect(() => {
        if (isLocked) return;
        const intervalId = setInterval(() => {
            const currentState = JSON.stringify({ blocks, datum, betreff, dokumentTyp });
            if (currentState !== lastSavedStateRef.current && !saving) {
                console.log('Auto-Save: Speichere Änderungen...');
                handleSave();
            }
        }, 10000);
        return () => clearInterval(intervalId);
    }, [blocks, datum, betreff, dokumentTyp, saving, isLocked, handleSave]);

    // --- Close Handler ---
    const handleClose = useCallback(() => {
        if (hasUnsavedChanges) {
            setShowUnsavedWarning(true);
        } else {
            onClose();
        }
    }, [hasUnsavedChanges, onClose]);

    // --- Keyboard Shortcut ---
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 's') {
                e.preventDefault();
                if (!isLocked && !saving) handleSave();
            }

        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleSave, isLocked, saving]);

    useEffect(() => {
        if (!isLocked) return;
        setActiveEditor(null);
        setShowTextbausteinPicker(false);
        setShowLeistungPicker(false);
        setShowStundensatzPicker(false);
    }, [isLocked]);

    // --- DnD ---
    const [activeDragId, setActiveDragId] = useState<string | null>(null);
    const activeDragBlock = activeDragId ? blocks.find(b => b.id === activeDragId) ?? null : null;

    const sensors = useSensors(
        useSensor(PointerSensor, {
            activationConstraint: {
                distance: 8,
            },
        }),
        useSensor(KeyboardSensor, {
            coordinateGetter: sortableKeyboardCoordinates,
        })
    );

    function handleDragStart(event: DragStartEvent) {
        setActiveDragId(String(event.active.id));
    }

    function handleDragCancel() {
        setActiveDragId(null);
    }

    // Custom collision detection: prioritize section drop zones over sortable items
    const sectionAwareCollision: CollisionDetection = useCallback((args) => {
        // First check if pointer is within a section drop zone
        const pointerCollisions = pointerWithin(args);
        const sectionDrops = pointerCollisions.filter(c =>
            String(c.id).startsWith('section-drop-')
        );
        if (sectionDrops.length > 0) {
            // Only allow SERVICE blocks to be dropped into sections
            const activeId = String(args.active.id);
            const activeBlock = blocks.find(b => b.id === activeId);
            if (activeBlock?.type === 'SERVICE') {
                return sectionDrops;
            }
        }
        // Otherwise use closestCenter for normal reordering
        return closestCenter(args);
    }, [blocks]);

    function handleDragEnd(event: DragEndEvent) {
        if (isLocked) return;
        const { active, over } = event;
        if (!over) return;

        const activeId = String(active.id);
        const overId = String(over.id);

        // CASE 1: Dropped on a section drop zone → move SERVICE into section
        if (overId.startsWith('section-drop-')) {
            const targetSectionId = overId.replace('section-drop-', '');
            moveServiceToSection(activeId, targetSectionId);
            return;
        }

        // CASE 2: Same item, no-op
        if (activeId === overId) return;

        // CASE 3: Find containers for both items
        const activeContainer = findBlockContainer(blocks, activeId);
        const overContainer = findBlockContainer(blocks, overId);

        if (!activeContainer || !overContainer) return;

        if (activeContainer === overContainer) {
            // Same container: reorder
            if (activeContainer === 'root') {
                // Root-level reorder
                setBlocks((items) => {
                    const oldIndex = items.findIndex((item) => item.id === activeId);
                    const newIndex = items.findIndex((item) => item.id === overId);
                    if (oldIndex === -1 || newIndex === -1) return items;
                    const newOrder = arrayMove(items, oldIndex, newIndex);
                    return newOrder;
                });
            } else {
                // Within a section: reorder children
                setBlocks(prev => prev.map(b => {
                    if (b.id === activeContainer && b.children) {
                        const oldIndex = b.children.findIndex(c => c.id === activeId);
                        const newIndex = b.children.findIndex(c => c.id === overId);
                        if (oldIndex === -1 || newIndex === -1) return b;
                        return { ...b, children: arrayMove(b.children, oldIndex, newIndex) };
                    }
                    return b;
                }));
            }
        }
        // Cross-container drag handled by drop zones, not by sorting overlap
    }

    /** Move a SERVICE block from wherever it is into a target section */
    const moveServiceToSection = useCallback((serviceId: string, sectionId: string) => {
        setBlocks(prev => {
            // Find the service block
            let serviceBlock: DocBlock | null = null;

            // Check root level
            const rootItem = prev.find(b => b.id === serviceId);
            if (rootItem && rootItem.type === 'SERVICE') {
                serviceBlock = rootItem;
            }

            // Check inside other sections
            if (!serviceBlock) {
                for (const b of prev) {
                    if (b.type === 'SECTION_HEADER' && b.children) {
                        const child = b.children.find(c => c.id === serviceId);
                        if (child) {
                            serviceBlock = child;
                            break;
                        }
                    }
                }
            }

            if (!serviceBlock) return prev;

            // Remove from current location
            let newBlocks = prev
                .filter(b => b.id !== serviceId)
                .map(b => {
                    if (b.type === 'SECTION_HEADER' && b.children) {
                        return { ...b, children: b.children.filter(c => c.id !== serviceId) };
                    }
                    return b;
                });

            // Add to target section's children
            newBlocks = newBlocks.map(b => {
                if (b.id === sectionId && b.type === 'SECTION_HEADER') {
                    return { ...b, children: [...(b.children || []), serviceBlock!] };
                }
                return b;
            });

            return newBlocks;
        });
    }, []);

    /** Remove a SERVICE from a section back to root level (placed right after the section) */
    const ejectChildFromSection = useCallback((sectionId: string, childId: string) => {
        setBlocks(prev => {
            const section = prev.find(b => b.id === sectionId);
            const child = section?.children?.find(c => c.id === childId);
            if (!child) return prev;

            // Remove from section
            let newBlocks = prev.map(b => {
                if (b.id === sectionId && b.children) {
                    return { ...b, children: b.children.filter(c => c.id !== childId) };
                }
                return b;
            });

            // Insert right after the section
            const sectionIndex = newBlocks.findIndex(b => b.id === sectionId);
            if (sectionIndex !== -1) {
                newBlocks.splice(sectionIndex + 1, 0, child);
            } else {
                newBlocks.push(child);
            }

            return newBlocks;
        });
    }, []);

    // --- Block Actions ---
    const addBlock = (type: any, payload?: Partial<DocBlock>) => {
        if (isLocked) return;

        const allServices = getAllServiceBlocks(blocks);
        // Build the block – spread payload LAST so caller values win,
        // but guard against undefined fontSize overwriting the default.
        const rawBlock: DocBlock = {
            id: crypto.randomUUID(),
            type: type as DocBlock['type'],
            content: payload?.content || '',
            pos: type === 'SERVICE' ? (allServices.length + 1).toString() : undefined,
            quantity: type === 'SERVICE' ? 1 : undefined,
            unit: type === 'SERVICE' ? 'Stk' : undefined,
            price: type === 'SERVICE' ? 0 : undefined,
            fontSize: 10,
            sectionLabel: type === 'SECTION_HEADER' ? (payload?.sectionLabel || '') : undefined,
            children: type === 'SECTION_HEADER' ? [] : undefined,
            ...payload
        };
        // Ensure fontSize is never undefined (important for PDF generation)
        const newBlock: DocBlock = {
            ...rawBlock,
            fontSize: rawBlock.fontSize ?? 10,
        };

        setBlocks([...blocks, newBlock]);
    };

    const updateBlock = (id: string, updates: Partial<DocBlock>) => {
        if (isLocked) return;
        setBlocks(prev => prev.map(b => b.id === id ? { ...b, ...updates } : b));
    };

    const removeBlock = (id: string) => {
        if (isLocked) return;
        // When removing a SECTION_HEADER, eject its children back to root
        setBlocks(prev => {
            const block = prev.find(b => b.id === id);
            if (block?.type === 'SECTION_HEADER' && block.children && block.children.length > 0) {
                const idx = prev.findIndex(b => b.id === id);
                const newBlocks = [...prev];
                newBlocks.splice(idx, 1, ...block.children);
                return newBlocks;
            }
            return prev.filter(b => b.id !== id);
        });
    };

    /** Update a child block within a section */
    const updateSectionChild = (sectionId: string, childId: string, updates: Partial<DocBlock>) => {
        if (isLocked) return;
        setBlocks(prev => prev.map(b => {
            if (b.id === sectionId && b.children) {
                return {
                    ...b,
                    children: b.children.map(c => c.id === childId ? { ...c, ...updates } : c)
                };
            }
            return b;
        }));
    };

    /** Remove a child from a section (delete it entirely) */
    const removeSectionChild = (sectionId: string, childId: string) => {
        if (isLocked) return;
        setBlocks(prev => prev.map(b => {
            if (b.id === sectionId && b.children) {
                return { ...b, children: b.children.filter(c => c.id !== childId) };
            }
            return b;
        }));
    };

    /** Toggle optional on a section child */
    const toggleSectionChildOptional = (sectionId: string, childId: string, current: boolean | undefined) => {
        updateSectionChild(sectionId, childId, { optional: !current });
    };

    const toggleOptional = (id: string, currentOptional: boolean | undefined) => {
        updateBlock(id, { optional: !currentOptional });
    };

    // --- Preview & Export ---
    const handlePreview = useCallback(async () => {
        setPreviewLoading(true);
        try {
            const request = await createPdfRequest(true);
            const response = await fetch('/api/dokument-generator/preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });
            if (response.ok) {
                const blob = await response.blob();
                if (previewUrl) URL.revokeObjectURL(previewUrl);
                const url = URL.createObjectURL(blob);
                setPreviewUrl(url);
                setPreviewStale(false);
            }
        } catch (error) {
            console.error('Fehler bei Vorschau:', error);
        } finally {
            setPreviewLoading(false);
        }
    }, [blocks, datum, betreff, dokumentTyp, kontextDaten, dokumentNummer, previewUrl]);

    // Mark preview as stale when content changes
    useEffect(() => {
        if (showPreview && previewUrl) {
            setPreviewStale(true);
        }
    }, [blocks, datum, betreff, dokumentTyp]);

    // Debounced auto-preview: refresh 2s after last change when preview panel is open
    const previewTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    useEffect(() => {
        if (!showPreview) return;
        if (previewTimerRef.current) clearTimeout(previewTimerRef.current);
        previewTimerRef.current = setTimeout(() => {
            handlePreview();
        }, 2000);
        return () => {
            if (previewTimerRef.current) clearTimeout(previewTimerRef.current);
        };
    }, [blocks, datum, betreff, dokumentTyp, showPreview]);

    // When preview panel is shown for the first time, trigger an immediate preview
    const prevShowPreview = useRef(false);
    useEffect(() => {
        if (showPreview && !prevShowPreview.current) {
            handlePreview();
        }
        prevShowPreview.current = showPreview;
    }, [showPreview]);

    /** Hilfsfunktion: Dokument buchen und State sofort aktualisieren (GoBD). */
    const buchenUndSperren = async (): Promise<boolean> => {
        if (!dokument?.id) return false;
        if (dokument.gebucht || dokument.storniert) return true; // bereits gesperrt
        try {
            // Erst unsaved changes speichern, bevor das Dokument gebucht wird
            if (hasUnsavedChanges) {
                await handleSave();
            }
            const bookRes = await fetch(`/api/ausgangs-dokumente/${dokument.id}/buchen`, { method: 'POST' });
            if (!bookRes.ok) {
                const errText = await bookRes.text();
                throw new Error(errText || 'Buchung fehlgeschlagen');
            }
            const updated = await bookRes.json();
            setDokument(updated); // State sofort aktualisieren → UI sperrt instant
            // Saved-State synchronisieren, damit Auto-Save nicht mehr feuert
            const currentState = JSON.stringify({ blocks, datum, betreff, dokumentTyp });
            lastSavedStateRef.current = currentState;
            setHasUnsavedChanges(false);
            notifyDokumentChanged({ projektId, angebotId, dokumentId: updated.id });
            return true;
        } catch (err) {
            console.error('Fehler beim Buchen:', err);
            toast.error('Dokument konnte nicht gebucht werden\n' + (err instanceof Error ? 'Antwort vom Server ' + err.message : ''));
            return false;
        }
    };

    const handleExport = async () => {
        if (!dokument?.id) await handleSave();
        setShowExportFormatDialog(true);
    };

    const handleExportFormatSelected = async (format: PdfFormat) => {
        setExportLoading(true);
        try {
            const shouldBook = showFinalizationPrompt;
            await confirmExport(shouldBook, format);
        } finally {
            setExportLoading(false);
            setShowExportFormatDialog(false);
        }
    };

    const handlePrint = async () => {
        if (!dokument?.id) await handleSave();
        setShowPrintOptions(true);
    };

    const executePrint = async (options: { withBackground: boolean; isFinal: boolean }) => {
        setShowPrintOptions(false);
        try {
            // Rechnungstypen: IMMER buchen & sperren beim Drucken (instant lock)
            const shouldBook = options.isFinal || showFinalizationPrompt;
            if (shouldBook) {
                if (!dokument?.id) throw new Error('Dokument muss zuerst gespeichert werden');
                const booked = await buchenUndSperren();
                if (!booked) throw new Error('Buchung fehlgeschlagen');
            }

            const request = await createPdfRequest(!shouldBook);

            // Remove background images if not requested
            if (!options.withBackground) {
                request.backgroundImagePage1 = null;
                request.backgroundImagePage2 = null;
            }

            // Remove watermark for final/booked print
            if (shouldBook) {
                request.layoutBlocks = request.layoutBlocks.filter((b: any) => b.type !== 'watermark');
            }

            const response = await fetch('/api/dokument-generator/preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });
            if (!response.ok) throw new Error('PDF-Generierung fehlgeschlagen');

            const blob = await response.blob();

            // PDF serverseitig speichern für Offene Posten Ansicht (nur bei finalem Druck)
            if (shouldBook && dokument?.id) {
                try {
                    const arrayBuffer = await blob.arrayBuffer();
                    await fetch(`/api/ausgangs-dokumente/${dokument.id}/pdf-speichern`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/pdf' },
                        body: arrayBuffer
                    });
                } catch (e) {
                    console.warn('PDF konnte nicht serverseitig gespeichert werden:', e);
                }
            }

            const url = URL.createObjectURL(blob);

            // Vorheriges Print-Iframe aufräumen
            const oldIframe = document.getElementById('print-iframe');
            if (oldIframe) {
                document.body.removeChild(oldIframe);
            }

            const iframe = document.createElement('iframe');
            iframe.id = 'print-iframe';
            iframe.style.position = 'fixed';
            iframe.style.left = '-9999px';
            iframe.style.width = '0';
            iframe.style.height = '0';
            iframe.src = url;
            document.body.appendChild(iframe);
            iframe.addEventListener('load', () => {
                iframe.contentWindow?.focus();
                iframe.contentWindow?.print();
            });
        } catch (err) {
            console.error('Fehler beim Drucken:', err);
            toast.error('Drucken fehlgeschlagen: ' + (err instanceof Error ? err.message : ''));
        }
    };

    const confirmExport = async (shouldBook: boolean = true, format: PdfFormat = 'pdf') => {
        setShowExportWarning(false);
        if (!dokument?.id) return;

        try {
            // Sofort buchen & sperren (instant lock, kein nachträgliches Refresh nötig)
            if (shouldBook) {
                const booked = await buchenUndSperren();
                if (!booked) throw new Error('Buchung fehlgeschlagen');
            }

            const request = await createPdfRequest(false);
            if (!request) throw new Error('Konnte PDF-Anfrage nicht erstellen');

            const endpoint = format === 'zugferd'
                ? '/api/dokument-generator/zugferd-pdf'
                : '/api/dokument-generator/pdf';

            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });
            if (!response.ok) throw new Error('PDF-Generierung fehlgeschlagen');

            const blob = await response.blob();

            // PDF serverseitig speichern für Offene Posten Ansicht
            if (shouldBook && dokument?.id) {
                try {
                    const arrayBuffer = await blob.arrayBuffer();
                    await fetch(`/api/ausgangs-dokumente/${dokument.id}/pdf-speichern`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/pdf' },
                        body: arrayBuffer
                    });
                } catch (e) {
                    console.warn('PDF konnte nicht serverseitig gespeichert werden:', e);
                }
            }

            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            const prefix = format === 'zugferd' ? 'zugferd_' : '';
            a.download = `${prefix}${dokumentTyp.toLowerCase()}_${dokumentNummer || 'neu'}.pdf`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error('Fehler beim Export:', err);
            toast.error('Export fehlgeschlagen: ' + (err instanceof Error ? err.message : ''));
        }
    };

    // --- E-Mail Versand ---
    const handleSendEmail = () => {
        setShowFormatDialog(true);
    };

    const handleFormatSelected = async (format: PdfFormat) => {
        setEmailLoading(true);
        try {
            // 1. Auto-Save wenn nötig
            if (!dokument?.id || hasUnsavedChanges) {
                await handleSave();
            }

            // 2. Rechnungstypen: sofort buchen & sperren (instant lock vor E-Mail)
            if (showFinalizationPrompt) {
                const booked = await buchenUndSperren();
                if (!booked) throw new Error('Buchung fehlgeschlagen');
            }

            // 2. PDF generieren (normal oder ZUGFeRD)
            const request = await createPdfRequest(false);
            if (!request) throw new Error('Konnte PDF-Anfrage nicht erstellen');

            const endpoint = format === 'zugferd'
                ? '/api/dokument-generator/zugferd-pdf'
                : '/api/dokument-generator/pdf';

            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });
            if (!response.ok) throw new Error('PDF-Generierung fehlgeschlagen');

            const blob = await response.blob();

            // PDF serverseitig speichern für Offene Posten Ansicht
            if (showFinalizationPrompt && dokument?.id) {
                try {
                    const arrayBuffer = await blob.arrayBuffer();
                    await fetch(`/api/ausgangs-dokumente/${dokument.id}/pdf-speichern`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/pdf' },
                        body: arrayBuffer
                    });
                } catch (e) {
                    console.warn('PDF konnte nicht serverseitig gespeichert werden:', e);
                }
            }

            const dokumentTypLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp;
            const prefix = format === 'zugferd' ? 'ZUGFeRD_' : '';
            const fileName = `${prefix}${dokumentTypLabel}_${dokumentNummer || 'Entwurf'}.pdf`;
            const pdfFile = new File([blob], fileName, { type: 'application/pdf' });

            // 3. E-Mail-Body vom Backend generieren lassen
            let generatedBody = '';
            try {
                const templateRes = await fetch('/api/email/template', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        dokumentTyp: dokumentTyp,
                        anrede: anredeEnumToText(kontextDaten.anrede),
                        kundenName: kontextDaten.ansprechpartner || '',
                        bauvorhaben: kontextDaten.projektBauvorhaben || betreff || '',
                        projektnummer: kontextDaten.projektnummer || '',
                        dokumentnummer: dokumentNummer || '',
                        rechnungsdatum: datum || new Date().toISOString().split('T')[0],
                        betrag: nettosumme ? `${(nettosumme * 1.19).toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €` : undefined,
                    })
                });
                if (templateRes.ok) {
                    const template = await templateRes.json();
                    generatedBody = template.body || '';
                }
            } catch (e) {
                console.warn('E-Mail-Template konnte nicht geladen werden:', e);
            }

            // 4. Email-Modal öffnen
            setEmailAttachments([pdfFile]);
            setEmailBody(generatedBody);
            setShowFormatDialog(false);
            setShowEmailModal(true);
        } catch (err) {
            console.error('Fehler beim E-Mail-Versand vorbereiten:', err);
            toast.error('E-Mail konnte nicht vorbereitet werden: ' + (err instanceof Error ? err.message : ''));
        } finally {
            setEmailLoading(false);
        }
    };

    // Berechneter E-Mail-Betreff basierend auf Dokumenttyp
    const emailSubject = useMemo(() => {
        const typLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp;
        const nr = dokumentNummer || '';
        const bv = kontextDaten.projektBauvorhaben || betreff || '';
        const parts = [typLabel];
        if (nr) parts.push(nr);
        if (bv) parts.push(`- ${bv}`);
        return parts.join(' ');
    }, [dokumentTyp, dokumentNummer, kontextDaten.projektBauvorhaben, betreff]);

    // --- Template / PDF Logic ---
    const deserializeTemplate = (html: string): FormBlock[] => {
        const wrapper = document.createElement('div');
        wrapper.innerHTML = html || '';
        const found = wrapper.querySelectorAll('[data-block-type]');
        return Array.from(found).map((el, idx) => {
            const dataset = (el as HTMLElement).dataset;
            return {
                id: `bg-${idx}`,
                type: dataset.blockType as FormBlockType,
                page: Number(dataset.page || 1),
                x: Number(dataset.x || 32),
                y: Number(dataset.y || 32),
                z: Number(dataset.z || idx + 1),
                width: Number(dataset.width) || 200,
                height: Number(dataset.height) || 80,
                content: dataset.content ? decodeURIComponent(dataset.content) : undefined,
                styles: dataset.style ? JSON.parse(decodeURIComponent(dataset.style)) : undefined
            } as FormBlock;
        });
    };

    const fetchTemplateData = async (type: string) => {
        try {
            // Convert enum value (e.g. 'AUFTRAGSBESTAETIGUNG') to German label (e.g. 'Auftragsbestätigung')
            // because the backend stores labels in the dokumenttyp table
            const typeLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === type)?.label || type;
            const selectionRes = await fetch(`/api/formulare/templates/selection?dokumenttyp=${encodeURIComponent(typeLabel)}`);
            let html = '';
            let backgroundImage: string | null = null;
            let backgroundImagePage2: string | null = null;

            if (selectionRes.ok && selectionRes.status !== 204) {
                const templateName = await selectionRes.text();
                if (templateName) {
                    const templateRes = await fetch(`/api/formulare/templates/${encodeURIComponent(templateName)}`);
                    if (templateRes.ok) {
                        const templateData = await templateRes.json();
                        html = templateData.html || '';
                        const bgMatch1 = html.match(/<meta\s+name="background-image"\s+content="([^"]+)"/);
                        if (bgMatch1 && bgMatch1[1]) backgroundImage = decodeURIComponent(bgMatch1[1]);
                        const bgMatch2 = html.match(/<meta\s+name="background-image-page2"\s+content="([^"]+)"/);
                        if (bgMatch2 && bgMatch2[1]) backgroundImagePage2 = decodeURIComponent(bgMatch2[1]);
                    }
                }
            }
            if (!html) {
                html = `
                    <div data-block-type="heading" data-x="40" data-y="40" data-width="300" data-height="60" data-content="${encodeURIComponent('Dokument')}"></div>
                    <div data-block-type="dokumenttyp" data-x="40" data-y="100" data-width="200" data-height="40" data-content="${encodeURIComponent(type)}"></div>
                    <div data-block-type="table" data-x="40" data-y="160" data-width="515" data-height="500"></div>
                `;
            }
            const parsedBlocks = deserializeTemplate(html);
            console.log('[DocumentEditor] Template HTML length:', html.length);
            console.log('[DocumentEditor] Parsed blocks from template:', parsedBlocks.map(b => ({
                id: b.id, type: b.type, page: b.page, x: b.x, y: b.y,
                width: b.width, height: b.height,
                content: b.content ? b.content.substring(0, 50) : '(empty)',
                hasStyles: !!b.styles
            })));
            const layoutBlocks = parsedBlocks.map(b => ({
                id: b.id,
                type: b.type,
                page: b.page,
                x: b.x,
                y: b.y,
                width: b.width,
                height: b.height,
                content: b.content || '',
                styles: b.styles
            }));
            return { layoutBlocks, backgroundImage, backgroundImagePage2 };
        } catch (error) {
            console.error('Error fetching template:', error);
            return { layoutBlocks: [], backgroundImage: null, backgroundImagePage2: null };
        }
    };

    const createPdfRequest = async (isPreview: boolean) => {
        const { layoutBlocks, backgroundImage, backgroundImagePage2 } = await fetchTemplateData(dokumentTyp);

        const filledLayoutBlocks = layoutBlocks.map(b => {
            const copy = { ...b };
            switch (copy.type) {
                case 'dokumenttyp':
                    copy.content = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp;
                    break;
                case 'doknr':
                    copy.content = dokumentNummer || (isPreview ? 'VORSCHAU' : 'ENTWURF');
                    break;
                case 'datum':
                    copy.content = datum ? new Date(datum).toLocaleDateString('de-DE') : new Date().toLocaleDateString('de-DE');
                    break;
                case 'projektnr':
                    copy.content = kontextDaten.projektnummer || '';
                    break;
                case 'kundennummer':
                    copy.content = kontextDaten.kundennummer || '';
                    break;
                case 'kunde':
                    copy.content = kontextDaten.kundenName || '';
                    break;
                case 'adresse':
                    copy.content = kontextDaten.rechnungsadresse || '';
                    break;
                case 'table':
                    copy.content = '';
                    break;
            }
            if (copy.content) {
                copy.content = replacePlaceholders(copy.content);
            }
            return copy;
        });

        console.log('[DocumentEditor] Filled layout blocks for PDF:', filledLayoutBlocks.map(b => ({
            id: b.id, type: b.type, page: b.page,
            x: b.x, y: b.y, width: b.width, height: b.height,
            content: b.content ? (b.content.length > 80 ? b.content.substring(0, 80) + '...' : b.content) : '(empty)'
        })));
        console.log('[DocumentEditor] kontextDaten:', {
            kundennummer: kontextDaten.kundennummer,
            projektnummer: kontextDaten.projektnummer,
            kundenName: kontextDaten.kundenName,
            rechnungsadresse: kontextDaten.rechnungsadresse ? kontextDaten.rechnungsadresse.substring(0, 50) : '(empty)'
        });

        if (isPreview && showFinalizationPrompt) {
            filledLayoutBlocks.push({
                id: 'watermark',
                type: 'watermark',
                page: 1,
                x: 100,
                y: 300,
                width: 400,
                height: 200,
                z: 1000,
                content: 'VORSCHAU / ENTWURF\nKein Beleg',
                styles: {
                    fontSize: 48,
                    fontWeight: 'bold',
                    color: '#e2e8f0',
                    textAlign: 'center'
                }
            } as any);
        }

        const contentBlocks = flattenBlocksForPdf(blocks).map(b => ({
            id: b.id,
            type: b.type,
            content: replacePlaceholders(b.content || ''),
            pos: b.pos || '',
            title: b.title || '',
            quantity: b.quantity || 0,
            unit: b.unit || 'Stk',
            price: b.price || 0,
            description: replacePlaceholders(b.description || ''),
            // TEXT blocks: Always use base defaults (10pt, not bold).
            // All formatting (bold, italic, font-size, colors) is embedded as inline
            // HTML from TiptapEditor and parsed by the backend HTML parser.
            // Using extractBoldFromHtml/extractFontSizeFromHtml as defaults was wrong
            // because it would make ALL text bold/large if ANY part was bold/large.
            fontSize: b.type === 'TEXT' ? 10 : (b.fontSize || 10),
            fett: b.type === 'TEXT' ? false : (b.fett || false),
            optional: b.optional || false,
            sectionLabel: b.sectionLabel || '',
            discount: b.discount || 0
        }));

        const kopfdaten = {
            dokumentnummer: dokumentNummer || (isPreview ? 'VORSCHAU' : 'ENTWURF'),
            rechnungsDatum: datum,
            leistungsDatum: datum,
            kundenName: kontextDaten.kundenName || 'Musterkunde',
            kundenAdresse: kontextDaten.rechnungsadresse || '',
            betreff: betreff,
            kundennummer: kontextDaten.kundennummer || '',
            bezugsdokument: kontextDaten.bezugsdokument || '',
            projektnummer: kontextDaten.projektnummer || '',
            bauvorhaben: kontextDaten.projektBauvorhaben || betreff || ''
        };

        // Abrechnungsverlauf für Rechnungstypen mit Basisdokument laden
        let schlusstext = '';
        let abrechnungsverlauf: any = null;
        const rechnungsTypen: AusgangsGeschaeftsDokumentTyp[] = ['RECHNUNG', 'TEILRECHNUNG', 'ABSCHLAGSRECHNUNG', 'SCHLUSSRECHNUNG'];
        if (dokument?.vorgaengerId && rechnungsTypen.includes(dokumentTyp)) {
            try {
                const verlaufRes = await fetch(`/api/ausgangs-dokumente/${dokument.vorgaengerId}/abrechnungsverlauf`);
                if (verlaufRes.ok) {
                    const verlauf = await verlaufRes.json();
                    // Filter out the current document from the Abrechnungsverlauf positions
                    const otherPositions = (verlauf.positionen || []).filter(
                        (pos: any) => pos.id !== dokument.id && !pos.storniert
                    );
                    // Always send abrechnungsverlauf when basisdokument exists
                    // (even with 0 other positions, to show Gesamtauftragssumme on first Abschlagsrechnung)
                    if (verlauf.basisdokumentBetragNetto != null && verlauf.basisdokumentBetragNetto > 0) {
                        abrechnungsverlauf = {
                            basisdokumentNummer: verlauf.basisdokumentNummer,
                            basisdokumentTyp: verlauf.basisdokumentTyp,
                            basisdokumentDatum: verlauf.basisdokumentDatum,
                            basisdokumentBetragNetto: verlauf.basisdokumentBetragNetto,
                            positionen: otherPositions.map((pos: any) => ({
                                dokumentNummer: pos.dokumentNummer,
                                typ: pos.typ,
                                datum: pos.datum,
                                betragNetto: pos.betragNetto,
                                abschlagsNummer: pos.abschlagsNummer,
                            })),
                        };
                    }
                }
            } catch (e) {
                console.error('Abrechnungsverlauf konnte nicht geladen werden:', e);
            }
        }

        return {
            dokumentTyp,
            templateName: isPreview ? 'preview' : 'default',
            kopfdaten,
            layoutBlocks: filledLayoutBlocks,
            contentBlocks,
            schlusstext,
            backgroundImagePage1: backgroundImage,
            backgroundImagePage2: backgroundImagePage2,
            globalRabattProzent: globalRabatt > 0 ? globalRabatt : null,
            abrechnungsverlauf,
            betragNetto: dokumentTyp === 'ABSCHLAGSRECHNUNG' && abschlagBetragNetto !== null ? abschlagBetragNetto : undefined,
            abschlagInfo: dokumentTyp === 'ABSCHLAGSRECHNUNG' && abschlagInfo ? abschlagInfo : undefined,
        };
    };

    // --- Computed values ---
    const blockNettosumme = calculateNetto(blocks);
    // Effektive Nettosumme: für Schlussrechnung = Blocksumme minus bereits abgerechnete
    const nettosumme = (dokumentTyp === 'SCHLUSSRECHNUNG' && bereitsAbgerechnetDurchAndere !== null)
        ? blockNettosumme - bereitsAbgerechnetDurchAndere
        : (dokumentTyp === 'ABSCHLAGSRECHNUNG' && abschlagBetragNetto !== null)
            ? abschlagBetragNetto
            : blockNettosumme;
    const dokumentTypLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp;

    // --- Position map & closure summary (computed from blocks) ---
    const positionMap = buildPositionMap(blocks);
    const closureSummary = computeClosureSummary(blocks);

    // Index des letzten SERVICE/SECTION_HEADER/SUBTOTAL/SEPARATOR Blocks
    // Der Abschluss wird direkt danach eingefügt (vor nachfolgenden TEXT-Blöcken)
    const closureInsertAfterIdx = useMemo(() => {
        for (let i = blocks.length - 1; i >= 0; i--) {
            const t = blocks[i].type;
            if (t === 'SERVICE' || t === 'SECTION_HEADER' || t === 'SUBTOTAL' || t === 'SEPARATOR') {
                return i;
            }
        }
        return blocks.length - 1;
    }, [blocks]);

    const getPositionString = (block: DocBlock): string => {
        return positionMap.get(block.id) || '';
    };

    // --- Loading state ---
    if (loading) {
        return (
            <div className="fixed inset-0 z-50 bg-white flex items-center justify-center">
                <div className="flex flex-col items-center gap-3">
                    <div className="animate-spin rounded-full h-8 w-8 border-2 border-rose-200 border-t-rose-600" />
                    <p className="text-sm text-slate-400">Wird geladen…</p>
                </div>
            </div>
        );
    }

    // --- Render ---
    return (
        <div className="fixed inset-0 z-50 bg-slate-50 flex flex-col">
            {/* GAEB Import Toast */}
            {importToast && (
                <div className="fixed top-5 left-1/2 -translate-x-1/2 z-[100] animate-in fade-in slide-in-from-top-4 duration-300">
                    <div className={`flex items-start gap-3 px-5 py-3.5 rounded-xl shadow-xl border backdrop-blur-sm min-w-[320px] max-w-[480px] ${
                        importToast.type === 'success'
                            ? 'bg-emerald-50/95 border-emerald-200 text-emerald-800'
                            : 'bg-red-50/95 border-red-200 text-red-800'
                    }`}>
                        <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5 ${
                            importToast.type === 'success' ? 'bg-emerald-100' : 'bg-red-100'
                        }`}>
                            {importToast.type === 'success' ? (
                                <svg className="w-4.5 h-4.5 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                </svg>
                            ) : (
                                <svg className="w-4.5 h-4.5 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            )}
                        </div>
                        <div className="flex-1 min-w-0">
                            <p className="text-sm font-semibold">{importToast.message}</p>
                            {importToast.details && (
                                <p className={`text-xs mt-0.5 ${
                                    importToast.type === 'success' ? 'text-emerald-600' : 'text-red-600'
                                }`}>{importToast.details}</p>
                            )}
                        </div>
                        <button
                            onClick={() => setImportToast(null)}
                            className={`flex-shrink-0 p-1 rounded-md transition-colors ${
                                importToast.type === 'success'
                                    ? 'hover:bg-emerald-200/60 text-emerald-500'
                                    : 'hover:bg-red-200/60 text-red-500'
                            }`}
                        >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                            </svg>
                        </button>
                    </div>
                </div>
            )}
            {/* ===== Compact Header ===== */}
            <DocumentEditorHeader
                dokumentNummer={dokumentNummer}
                kontextInfo={kontextDaten.projektBauvorhaben || kontextDaten.kundenName || ''}
                isLocked={isLocked}
                saving={saving}
                saveSuccess={saveSuccess}
                hasUnsavedChanges={hasUnsavedChanges}
                previewLoading={previewLoading}
                dokument={dokument}
                emailLoading={emailLoading}
                onClose={handleClose}
                onSave={handleSave}
                onOpenTextbausteinPicker={() => setShowTextbausteinPicker(true)}
                onOpenLeistungPicker={() => setShowLeistungPicker(true)}
                onOpenStundensatzPicker={() => setShowStundensatzPicker(true)}
                onAddSeparator={() => addBlock('SEPARATOR')}
                onAddSectionHeader={() => addBlock('SECTION_HEADER')}
                onOpenRabattDialog={() => setShowRabattDialog(true)}
                onExport={handleExport}
                onPrint={handlePrint}
                onSendEmail={handleSendEmail}
                onGaebImport={handleGaebImportClick}
                fileInputRef={fileInputRef}
                onFileChange={handleFileChange}
            />

            {/* ===== Main Split Pane ===== */}
            <div className="flex-1 flex overflow-hidden relative">
                {/* Editor Area */}
                <div className="flex-1 flex flex-col overflow-hidden min-w-0 transition-all duration-500 ease-in-out">
                    {/* Global Sticky Toolbar */}
                    {!isLocked && (
                        <div className="sticky top-0 z-20 bg-slate-50/90 backdrop-blur-sm px-4 py-1.5 border-b border-slate-100">
                            <TiptapToolbar editor={activeEditor} />
                        </div>
                    )}

                    {/* Content Editor - scrollable */}
                    <div className="flex-1 overflow-y-auto">
                        <div className="mx-auto px-4 py-4 space-y-2.5 max-w-4xl">
                            {/* Rechnungsadresse – editierbar pro Dokument */}
                            <RechnungsadresseBlock
                                value={kontextDaten.rechnungsadresse || ''}
                                isLocked={isLocked}
                                onChange={(newAddr) => setKontextDaten(prev => ({ ...prev, rechnungsadresse: newAddr }))}
                            />

                            {/* Blocks */}
                            <DndContext
                                sensors={sensors}
                                collisionDetection={sectionAwareCollision}
                                onDragStart={handleDragStart}
                                onDragEnd={(event) => { handleDragEnd(event); setActiveDragId(null); }}
                                onDragCancel={handleDragCancel}
                            >
                                <SortableContext
                                    items={blocks.map(b => b.id)}
                                    strategy={verticalListSortingStrategy}
                                >
                                    {blocks.map((block, blockIndex) => (
                                        <Fragment key={block.id}>
                                        <SortableBlock block={block} isLocked={isLocked}>
                                            {block.type === 'SEPARATOR' && (
                                                <SeparatorBlock
                                                    blockId={block.id}
                                                    isLocked={isLocked}
                                                    onRemove={removeBlock}
                                                />
                                            )}
                                            {block.type === 'SECTION_HEADER' && (
                                                <SectionHeaderBlock
                                                    block={block}
                                                    isLocked={isLocked}
                                                    isActive={activeEditorId === block.id}
                                                    activeEditorId={activeEditorId}
                                                    editorRefs={editorRefs}
                                                    onUpdate={updateBlock}
                                                    onUpdateChild={updateSectionChild}
                                                    onRemove={removeBlock}
                                                    onRemoveChild={removeSectionChild}
                                                    onEjectChild={ejectChildFromSection}
                                                    onToggleChildOptional={toggleSectionChildOptional}
                                                    onFocus={(id) => setActiveEditorId(id)}
                                                    onEditorFocus={(editor) => setActiveEditor(isLocked ? null : editor)}
                                                    getPositionString={getPositionString}
                                                    sectionPosition={getPositionString(block)}
                                                />
                                            )}
                                            {block.type === 'TEXT' && (
                                                <TextBlock
                                                    block={block}
                                                    isLocked={isLocked}
                                                    isActive={activeEditorId === block.id}
                                                    editorRefs={editorRefs}
                                                    onUpdate={updateBlock}
                                                    onRemove={removeBlock}
                                                    onFocus={(id) => setActiveEditorId(id)}
                                                    onEditorFocus={(editor) => setActiveEditor(isLocked ? null : editor)}
                                                    replacePlaceholders={replacePlaceholders}
                                                />
                                            )}
                                            {block.type === 'SERVICE' && (
                                                <ServiceBlock
                                                    block={block}
                                                    positionNumber={getPositionString(block)}
                                                    isLocked={isLocked}
                                                    isActive={activeEditorId === block.id}
                                                    editorRefs={editorRefs}
                                                    onUpdate={updateBlock}
                                                    onRemove={removeBlock}
                                                    onToggleOptional={toggleOptional}
                                                    onFocus={(id) => setActiveEditorId(id)}
                                                    onEditorFocus={(editor) => setActiveEditor(isLocked ? null : editor)}
                                                />
                                            )}
                                        </SortableBlock>
                                        {blockIndex === closureInsertAfterIdx && closureSummary.gesamtNetto > 0 && (
                                            <ClosureBlock
                                                summary={closureSummary}
                                                dokumentTyp={dokumentTyp}
                                                abschlagBetragNetto={abschlagBetragNetto}
                                                bereitsAbgerechnetDurchAndere={bereitsAbgerechnetDurchAndere}
                                                abrechnungsPositionen={abrechnungsPositionen}
                                                basisdokumentBetragNetto={basisdokumentBetragNetto}
                                            />
                                        )}
                                        </Fragment>
                                    ))}
                                </SortableContext>
                                <DragOverlay dropAnimation={{
                                    duration: 200,
                                    easing: 'cubic-bezier(0.25, 1, 0.5, 1)',
                                }}>
                                    {activeDragBlock ? (
                                        <SortableBlock block={activeDragBlock} isLocked={isLocked} isDragOverlay>
                                            {activeDragBlock.type === 'SEPARATOR' && (
                                                <SeparatorBlock
                                                    blockId={activeDragBlock.id}
                                                    isLocked={true}
                                                    onRemove={() => {}}
                                                />
                                            )}
                                            {activeDragBlock.type === 'SECTION_HEADER' && (
                                                <div className="bg-slate-800 px-4 py-3 rounded-lg">
                                                    <span className="text-white text-sm font-bold">
                                                        Bauabschnitt: {activeDragBlock.sectionLabel || 'Ohne Bezeichnung'}
                                                    </span>
                                                    {activeDragBlock.children && activeDragBlock.children.length > 0 && (
                                                        <span className="text-slate-400 text-xs ml-2">
                                                            ({activeDragBlock.children.length} Pos.)
                                                        </span>
                                                    )}
                                                </div>
                                            )}
                                            {activeDragBlock.type === 'TEXT' && (
                                                <div className="bg-white rounded-lg border border-slate-200 p-3">
                                                    <div className="text-[10px] font-semibold text-rose-400 uppercase tracking-wider mb-1">Textbaustein</div>
                                                    <div className="text-xs text-slate-600 line-clamp-2" dangerouslySetInnerHTML={{ __html: activeDragBlock.content || '' }} />
                                                </div>
                                            )}
                                            {activeDragBlock.type === 'SERVICE' && (
                                                <div className="bg-white rounded-lg border border-slate-200 p-3">
                                                    <div className="text-[10px] font-semibold text-emerald-500 uppercase tracking-wider mb-1">Leistung</div>
                                                    <div className="text-sm font-medium text-slate-800 line-clamp-1">{activeDragBlock.title || 'Ohne Titel'}</div>
                                                </div>
                                            )}
                                        </SortableBlock>
                                    ) : null}
                                </DragOverlay>
                            </DndContext>

                            {/* Fallback: Abschluss nach allen Blöcken (wenn kein SERVICE/SECTION_HEADER vorhanden) */}
                            {closureInsertAfterIdx < 0 && closureSummary.gesamtNetto > 0 && (
                                <ClosureBlock
                                    summary={closureSummary}
                                    dokumentTyp={dokumentTyp}
                                    abschlagBetragNetto={abschlagBetragNetto}
                                    bereitsAbgerechnetDurchAndere={bereitsAbgerechnetDurchAndere}
                                    abrechnungsPositionen={abrechnungsPositionen}
                                    basisdokumentBetragNetto={basisdokumentBetragNetto}
                                />
                            )}

                            {/* Empty State */}
                            {blocks.length === 0 && (
                                <div className="bg-white rounded-xl border-2 border-dashed border-slate-200 p-12 text-center">
                                    <div className="w-14 h-14 bg-slate-100 rounded-xl flex items-center justify-center mx-auto mb-3">
                                        <Plus className="w-7 h-7 text-slate-300" />
                                    </div>
                                    <p className="text-sm font-medium text-slate-500">
                                        Nutzen Sie die Buttons oben um Textbausteine, Leistungen oder Stundensätze hinzuzufügen
                                    </p>
                                    <p className="text-xs text-slate-400 mt-1">
                                        oder importieren Sie Positionen über GAEB
                                    </p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* ===== Live Preview Panel ===== */}
                <LivePreviewPanel
                    previewUrl={previewUrl}
                    loading={previewLoading}
                    stale={previewStale}
                    onRefresh={handlePreview}
                    isOpen={true}
                />
            </div>

            {/* ===== Footer Status Bar ===== */}
            <SummenFooter
                nettosumme={nettosumme}
                blockCount={blocks.length}
                dokumentTypLabel={dokumentTypLabel}
                datum={datum}
                kundennummer={kontextDaten.kundennummer}
                projektnummer={kontextDaten.projektnummer}
                betreff={betreff}
                isLocked={isLocked}
                onDatumChange={(value) => {
                    if (isLocked) return;
                    setDatum(value);
                }}
                globalRabatt={globalRabatt}
                istRestbetrag={bereitsAbgerechnetDurchAndere !== null && bereitsAbgerechnetDurchAndere > 0}
            />

            {/* Modals */}
            {showExportWarning && (
                <ExportWarningModal
                    onCancel={() => setShowExportWarning(false)}
                    onConfirm={() => confirmExport(true)}
                />
            )}

            {/* Export Format Dialog (PDF / ZUGFeRD) */}
            <EmailFormatDialog
                isOpen={showExportFormatDialog}
                onClose={() => { setShowExportFormatDialog(false); setExportLoading(false); }}
                onSelect={handleExportFormatSelected}
                loading={exportLoading}
                title="PDF exportieren"
                description="Wählen Sie das Format für den Download:"
                pdfLabel="Standard PDF-Datei herunterladen"
                zugferdLabel="PDF mit maschinenlesbaren Rechnungsdaten (EN 16931)"
                loadingText="PDF wird exportiert…"
            />
            {showPrintOptions && (
                <PrintOptionsModal
                    onCancel={() => setShowPrintOptions(false)}
                    onConfirm={executePrint}
                    allowFinalization={showFinalizationPrompt}
                />
            )}
            {showUnsavedWarning && (
                <UnsavedChangesModal
                    onCancel={() => setShowUnsavedWarning(false)}
                    onDiscard={() => {
                        setShowUnsavedWarning(false);
                        onClose();
                    }}
                    onSaveAndClose={async () => {
                        await handleSave();
                        setShowUnsavedWarning(false);
                        onClose();
                    }}
                />
            )}
            {showTextbausteinPicker && (
                <TextbausteinPickerModal
                    textbausteine={textbausteine}
                    dokumentTyp={dokumentTyp}
                    onSelect={(tb) => {
                        const htmlContent = tb.html || tb.beschreibung || '';
                        const resolvedContent = replacePlaceholders(htmlContent);
                        addBlock('TEXT', {
                            content: resolvedContent,
                            fontSize: extractFontSizeFromHtml(resolvedContent) || extractFontSizeFromHtml(htmlContent),
                            fett: extractBoldFromHtml(resolvedContent) || extractBoldFromHtml(htmlContent),
                        });
                    }}
                    onClose={() => setShowTextbausteinPicker(false)}
                />
            )}
            {showLeistungPicker && (
                <LeistungPickerModal
                    leistungen={leistungen}
                    onSelect={(l) => {
                        const descHtml = l.description || '';
                        addBlock('SERVICE', {
                            title: l.name,
                            description: descHtml,
                            quantity: 1,
                            unit: unitMap[l.unit?.name || 'STUECK'] || 'Stk',
                            price: l.price,
                            fontSize: extractFontSizeFromHtml(descHtml),
                            fett: extractBoldFromHtml(descHtml),
                            leistungId: l.id,
                        });
                    }}
                    onClose={() => setShowLeistungPicker(false)}
                />
            )}
            {showStundensatzPicker && (
                <StundensatzPickerModal
                    arbeitszeitarten={arbeitszeitarten}
                    onSelect={(az) => {
                        const descHtml = az.beschreibung || '';
                        addBlock('SERVICE', {
                            title: az.bezeichnung,
                            description: descHtml,
                            quantity: 1,
                            unit: 'h',
                            price: az.stundensatz,
                            fontSize: extractFontSizeFromHtml(descHtml),
                            fett: extractBoldFromHtml(descHtml),
                        });
                    }}
                    onClose={() => setShowStundensatzPicker(false)}
                />
            )}
            {showRabattDialog && (
                <RabattDialog
                    blocks={blocks}
                    globalRabatt={globalRabatt}
                    onApply={(positionDiscounts, newGlobalRabatt) => {
                        setBlocks(prev => prev.map(b => {
                            if (b.type === 'SERVICE' && b.id in positionDiscounts) {
                                return { ...b, discount: positionDiscounts[b.id] };
                            }
                            return b;
                        }));
                        setGlobalRabatt(newGlobalRabatt);
                        setHasUnsavedChanges(true);
                        setShowRabattDialog(false);
                    }}
                    onClose={() => setShowRabattDialog(false)}
                />
            )}

            {/* PDF-Format Auswahl Dialog */}
            <EmailFormatDialog
                isOpen={showFormatDialog}
                onClose={() => { setShowFormatDialog(false); setEmailLoading(false); }}
                onSelect={handleFormatSelected}
                loading={emailLoading}
            />

            {/* E-Mail Versand Modal */}
            <EmailComposeModal
                isOpen={showEmailModal}
                onClose={() => {
                    setShowEmailModal(false);
                    setEmailAttachments([]);
                    setEmailBody('');
                }}
                projektId={projektId}
                angebotId={angebotId}
                kundeId={kontextDaten.kundeId}
                angebot={{
                    bauvorhaben: kontextDaten.projektBauvorhaben || betreff || '',
                    kundenName: kontextDaten.kundenName,
                    kundenEmails: kontextDaten.kundenEmails,
                    kundenAnrede: kontextDaten.anrede,
                    kundenAnsprechpartner: kontextDaten.ansprechpartner,
                }}
                initialAttachments={emailAttachments}
                initialRecipient={kontextDaten.kundenEmails?.[0]}
                initialSubject={emailSubject}
                initialBody={emailBody}
                onSuccess={async () => {
                    // Versanddatum setzen (Buchung erfolgte bereits vor E-Mail-Vorbereitung)
                    if (dokument?.id) {
                        try {
                            const res = await fetch(`/api/ausgangs-dokumente/${dokument.id}/email-versendet`, {
                                method: 'POST',
                            });
                            if (res.ok) {
                                const updated = await res.json();
                                setDokument(updated);
                                notifyDokumentChanged({ projektId, angebotId, dokumentId: updated.id });
                            }
                        } catch (err) {
                            console.error('Fehler beim Setzen des Versanddatums:', err);
                        }
                    }
                    setShowEmailModal(false);
                    setEmailAttachments([]);
                    setEmailBody('');
                }}
            />
        </div>
    );
}
