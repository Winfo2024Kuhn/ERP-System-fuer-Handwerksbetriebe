import { useState, useEffect, useLayoutEffect, useCallback, useRef, useMemo, forwardRef, useImperativeHandle } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { Pencil, Plus, Check, X } from 'lucide-react';
import { useEditor } from '@tiptap/react';
import { TiptapToolbar } from '../TiptapEditor';
import type { TiptapAenderungsArt } from '../tiptapVerlauf';
import { useDokumentVerlauf, type DokumentStand, type VerlaufsZiel, type VerlaufsMeldung } from './useDokumentVerlauf';
import { useVerlaufTastatur } from './useVerlaufTastatur';
import {
    type AusgangsGeschaeftsDokument,
    type AusgangsGeschaeftsDokumentTyp,
    type AusgangsGeschaeftsDokumentErstellen,
    type AbrechnungspositionDto,
    type AbrechnungsverlaufDto,
    AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN,
    type FormBlock,
    type FormBlockType
} from '../../types';
import { notifyDokumentChanged } from '../../lib/dokumentChannel';
import { parseHtmlInert, sanitizeHtml } from '../../lib/htmlSanitizer';
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

import type { DocBlock, DocumentEditorProps, DocumentEditorHandle, KontextDaten, TextbausteinApiDto, LeistungApiDto, ArbeitszeitartApiDto, EditorInstance } from './types';
import {
    CLOSURE_BLOCK_ID,
    syncClosureBlock,
    insertAtAnchor,
    insertBeforeNachtexte,
    insertIntoSection,
    insertBlocksBeforeClosure,
    validateRootReorder,
    gruppiereAlsAlternativen,
    gruppenNameVergeben,
    loeseAlternativGruppeAuf,
    normalisiereAlternativGruppen,
    ohneAlternativGruppe,
} from './blockOps';

/**
 * Zielposition fuer das Einfuegen eines neuen Blocks. Wird vor dem Oeffnen
 * eines Pickers gesetzt; nach dem Insert auf den Default zurueckgesetzt.
 *  - 'between-nach': zwischen Vor- und Nachtexte (Standard fuer Header-Icons)
 *  - 'after':        direkt unterhalb des angegebenen Anker-Blocks
 *  - 'in-section':   als letztes Kind im angegebenen Bauabschnitt
 */
type InsertAnchor =
    | { kind: 'between-nach' }
    | { kind: 'after'; id: string }
    | { kind: 'in-section'; sectionId: string };

const DEFAULT_ANCHOR: InsertAnchor = { kind: 'between-nach' };

function applyInsert(prev: DocBlock[], block: DocBlock, anchor: InsertAnchor): DocBlock[] {
    switch (anchor.kind) {
        case 'between-nach':
            return insertBeforeNachtexte(prev, block);
        case 'after':
            return insertAtAnchor(prev, block, anchor.id);
        case 'in-section':
            return insertIntoSection(prev, block, anchor.sectionId);
    }
}

/** Anzeigename fuer den Verlauf: "Position/Textbaustein/Bauabschnitt/Trennlinie/Abschluss". */
function blockName(block: DocBlock | undefined): string {
    switch (block?.type) {
        case 'TEXT': return 'Textbaustein';
        case 'SECTION_HEADER': return 'Bauabschnitt';
        case 'SEPARATOR': return 'Trennlinie';
        case 'CLOSURE': return 'Abschluss';
        case 'SERVICE':
        default:
            return 'Position';
    }
}

/** Welches Feld von `DocBlock` diese Aktualisierung betrifft -- die Block-Komponenten
 *  schicken pro Aufruf immer genau eines (Task-2-Vertrag). */
function verlaufsFeldVon(updates: Partial<DocBlock>): VerlaufsZiel['feld'] {
    if (updates.title !== undefined) return 'title';
    if (updates.quantity !== undefined) return 'quantity';
    if (updates.unit !== undefined) return 'unit';
    if (updates.price !== undefined) return 'price';
    if (updates.content !== undefined) return 'content';
    if (updates.description !== undefined) return 'description';
    if (updates.sectionLabel !== undefined) return 'sectionLabel';
    return undefined;
}

/** Wortliste (Plan Task 4): Bezeichnung eines Inhalts-Schritts in Handwerker-Sprache. */
function bezeichnungFuerAenderung(updates: Partial<DocBlock>, art?: TiptapAenderungsArt): string {
    const feld = verlaufsFeldVon(updates);
    switch (feld) {
        case 'title': return 'Titel geändert';
        case 'quantity': return 'Menge geändert';
        case 'unit': return 'Einheit geändert';
        case 'price': return 'Preis geändert';
        case 'content':
        case 'description':
            return art === 'tippen' ? 'Text geändert' : 'Formatierung geändert';
        case 'sectionLabel': return 'Bauabschnitt umbenannt';
        default: return 'Änderung';
    }
}

/**
 * Buendel-Schluessel fuer Inhalts-Aenderungen: gleicher Block + gleiches Feld
 * buendelt, solange getippt wird. Formatierung, Einfuegen per Zwischenablage
 * und Bilder (`art !== 'tippen'`) sind laut Spec immer ein eigener Schritt.
 */
function buendelSchluesselFuer(id: string, updates: Partial<DocBlock>, art?: TiptapAenderungsArt): string | null {
    const feld = verlaufsFeldVon(updates);
    if (!feld) return null;
    if ((feld === 'content' || feld === 'description') && art !== 'tippen') return null;
    return `block:${id}:${feld}`;
}
// Dokumentschriften wie im PDF (Open Sans fuer den Brieftext, Montserrat fuer den Betreff);
// selbst gehostet ueber @fontsource, nur im Dokument-Editor geladen.
import '@fontsource/open-sans/latin-400.css';
import '@fontsource/open-sans/latin-400-italic.css';
import '@fontsource/open-sans/latin-700.css';
import '@fontsource/open-sans/latin-700-italic.css';
import '@fontsource/montserrat/latin-600.css';
import { blocksToHtml, calculateNetto, calculateNettoNachRabatt, extractBoldFromHtml, getAllServiceBlocks, findBlockContainer, flattenBlocksForPdf, buildPositionMap, gruppiereFuerAnzeige, computeClosureSummary, zahlungszielPlaceholderToChipHtml, chipHtmlToZahlungszielPlaceholder, berechneZahlungszielDatum, DEFAULT_ZAHLUNGSZIEL_TAGE, MIN_ZAHLUNGSZIEL_TAGE, MAX_ZAHLUNGSZIEL_TAGE, ZAHLUNGSZIEL_NACHFRAGE_AB_TAGEN, defaultsLabelKandidaten, mussAufBezugsdokumentWarten, mussAufKontextWarten, repariereLeeresBezugsdatumInStandardtext, parseBlocksAusPositionenJson, baueDokumentSignatur, weichtVonLeistungAb, anzeigeEinheitDerLeistung } from './helpers';
import { schliesstEinfacheRechnungAuftragAb } from '../../lib/abrechnungsverlauf';
import { AlternativGruppeBox } from './AlternativGruppeBox';
import { AlternativGruppeDialog } from './AlternativGruppeDialog';
import { DocumentEditorHeader } from './DocumentEditorHeader';
import { ArtikelAuswahlDialog, type ArtikelAuswahl } from '../artikel/ArtikelAuswahlDialog';
import { ShopWarenkorbDialog } from '../ids/ShopWarenkorbDialog';
import { useIdsVerfuegbar } from '../ids/useIdsVerfuegbar';
import { ServiceBlock } from './ServiceBlock';
import { TextBlock } from './TextBlock';
import { ClosureBlock } from './ClosureBlock';
import { abschlussZeileAuftrag, useAbschlussZeilen } from './abschluss/useAbschlussZeilen';
import { abschlussZeilenSpeicherwert, normalisiereAbschlussZeilen, type AbschlussZeilen } from './abschluss/abschlussZeilen';
import { baueAbrechnungsverlaufRequest, brauchtAbrechnungsverlauf, ladeAbrechnungsverlaufFuerPdf, type PdfAbrechnungsverlauf } from './abschluss/abrechnungsverlaufRequest';
import { useDifferenzHinweis } from './abschluss/useDifferenzHinweis';
import { useVorschauMarker } from './vorschau/useVorschauMarker';
import { SeparatorBlock } from './SeparatorBlock';
import { SectionHeaderBlock } from './SectionHeaderBlock';
import { SortableBlock } from './SortableBlock';
import { SummenFooter } from './SummenFooter';
import { ZahlungszielTageEingabe } from './ZahlungszielTageEingabe';
import { LivePreviewPanel } from './LivePreviewPanel';
import { UnsavedChangesModal, TextbausteinPickerModal, LeistungPickerModal, StundensatzPickerModal, AddTypeDialog } from './Modals';
import { LeistungNeuSpeichernDialog, type GewaehlterOrdner } from './LeistungNeuSpeichernDialog';
import { RabattDialog } from './RabattDialog';
import { KategorieBestaetigenDialog } from './KategorieBestaetigenDialog';
import { loesePlatzhalterAuf } from './platzhalterAufloesen';
import { useDokumentKontext } from './useDokumentKontext';
import { useDokumentVersand } from './versand/useDokumentVersand';
import { VersandDialoge } from './versand/VersandDialoge';
import { formatDatum, heuteIso } from '../../lib/datum';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';
import { TabSchliessenHinweis } from '../lock/TabSchliessenHinweis';
import { useKonfliktMeldung } from '../lock/useKonfliktMeldung';
import { scrolleMittigInDenBlick } from '../../lib/scrollInDenBlick';
import { useEinmalTimer } from '../../hooks/useEinmalTimer';
import { ueberlagereDokumentMitStand } from './standAnsicht';
import { EditorVerlaufKnopf } from './EditorVerlaufKnopf';

interface ImportedGaebBlock {
    type: string;
    quantity?: number | string;
    price?: number | string;
    content?: string;
    sectionLabel?: string;
    children?: ImportedGaebBlock[];
    [key: string]: unknown;
}

type PreviewLayoutBlock = FormBlock | (Omit<FormBlock, 'type'> & { type: 'watermark' });

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
                <label
                    htmlFor="rechnungsadresse-eingabe"
                    className="block text-[10px] font-semibold text-rose-500 uppercase tracking-wider mb-1"
                >
                    Rechnungsadresse bearbeiten
                </label>
                <textarea
                    id="rechnungsadresse-eingabe"
                    ref={textareaRef}
                    value={draft}
                    data-eigenes-rueckgaengig="true"
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

const DocumentEditor = forwardRef<DocumentEditorHandle, DocumentEditorProps>(function DocumentEditor(
    { projektId, anfrageId, dokumentId, initialDokumentTyp, onClose, readOnly = false, onLockFreigeben, standAnsicht },
    ref
) {
    const toast = useToast();
    // Rueckfrage bei langen Zahlungszielen, siehe handleZahlungszielChange.
    const confirm = useConfirm();
    // Fuer das Zurueckschreiben der beim Anlegen vergebenen Id in die URL
    // (syncDocumentIdInUrl) -- MUSS ueber den Router laufen, nicht per
    // window.history.replaceState, sonst bekommt die Seite (liest dokumentId
    // aus genau diesen searchParams) die neue Id nie mit und ihr Lock-Hook
    // bleibt auf 'idle' stehen (Nachbesserung 1, siehe Kontext-Log).
    const [searchParams, setSearchParams] = useSearchParams();
    // Fuer den Pfad-Guard in syncDocumentIdInUrl: MUSS die Router-Location
    // sein, nicht window.location -- ein MemoryRouter (z.B. in Tests, aber
    // auch technisch denkbar bei zukuenftiger Einbettung) ruehrt
    // window.location ueberhaupt nicht an.
    const location = useLocation();
    // Versionskonflikt (409 mit JSON-Body vom RestExceptionHandler) bekommt
    // die geteilte Neu-laden-Meldung statt eines rohen JSON-Textes im Toast.
    const konfliktMeldung = useKonfliktMeldung('Dokument');
    // --- State ---
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [dokumentTyp, setDokumentTyp] = useState<AusgangsGeschaeftsDokumentTyp>(initialDokumentTyp || 'ANGEBOT');
    const [dokumentNummer, setDokumentNummer] = useState<string>('');
    const [betreff, setBetreff] = useState('');
    const [datum, setDatum] = useState(heuteIso);
    // Synchroner Spiegel von `datum` für stale-closure-freie Lesezugriffe (z.B.
    // wenn wir das Datum direkt vor einem Versand bumpen und SOFORT in handleSave
    // / createPdfRequest verwenden, bevor der nächste React-Render passiert).
    const datumRef = useRef(datum);
    useEffect(() => { datumRef.current = datum; }, [datum]);
    const [blocks, setBlocks] = useState<DocBlock[]>([]);
    // Synchroner Spiegel + Ref-first-Setter (Vorbild datumRef/kontextDatenRef
    // unten): der Dokumentverlauf liest den Stand synchron aus Refs, bevor der
    // naechste Render passiert. Ab hier schreibt niemand mehr direkt setBlocks --
    // entweder ueber verlauf.aendern (Nutzeraktionen) oder ueber setzeBlocks mit
    // einem aus blocksRef.current berechneten Wert (automatische Aenderungen).
    const blocksRef = useRef(blocks);
    useLayoutEffect(() => { blocksRef.current = blocks; }, [blocks]);
    const setzeBlocks = useCallback((neu: DocBlock[]) => {
        blocksRef.current = neu;
        setBlocks(neu);
    }, []);
    // Kunden-, Projekt- und Bezugsdaten samt Laden (siehe useDokumentKontext).
    const {
        kontextDaten, kontextDatenRef, setzeKontextDaten,
        adresseUserEditedRef, zahlungszielUserEditedRef, kontextGeladen, ladeFuerDokument: ladeKontextFuerDokument,
    } = useDokumentKontext({ dokumentId, projektId, anfrageId, dokumentTyp, setzeBetreff: setBetreff });
    const [dokument, setDokument] = useState<AusgangsGeschaeftsDokument | null>(null);
    const [showUnsavedWarning, setShowUnsavedWarning] = useState(false);
    const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
    const [saveSuccess, setSaveSuccess] = useState(false);
    const saveSuccessTimer = useEinmalTimer();
    // Setzt nach dem Laden den gespeicherten Stand zurueck (siehe Lade-Effekt).
    const geladenTimer = useEinmalTimer();
    const lastSavedStateRef = useRef<string>('');
    // Die Rechnungsadresse liegt in `kontextDaten` und damit ausserhalb der
    // State-Signatur, gegen die `lastSavedStateRef` vergleicht. Sie kann dort
    // auch nicht aufgenommen werden: die Signatur wird beim Laden aus dem
    // Server-Payload gebaut, die angezeigte Adresse ist bei Dokumenten ohne
    // `rechnungsadresseOverride` aber ein abgeleiteter Wert (buildAdresse aus
    // den Kundenstammdaten), der im Payload gar nicht vorkommt. Die Signatur
    // wuerde also dauerhaft abweichen und jedes solche Dokument schon beim
    // Oeffnen als geaendert melden. Stattdessen ein eigenes Flag, das additiv
    // in Dirty-Check und Auto-Save einfliesst und beim Speichern zuruecksetzt.
    const [adresseGeaendert, setAdresseGeaendert] = useState(false);
    // Gleicher Grund beim Zahlungsziel: ohne eigenen Wert im Dokument zeigt der
    // Editor den Kunden-Standard bzw. DEFAULT_ZAHLUNGSZIEL_TAGE an, und diese
    // Werte kommen erst nach dem Laden asynchron an. In der Signatur wuerden
    // frisch geladene und neue Dokumente sofort als geaendert gelten.
    const [zahlungszielGeaendert, setZahlungszielGeaendert] = useState(false);
    // Bezugswert dafuer: das zuletzt geladene bzw. gespeicherte Zahlungsziel.
    // Solange nichts als geaendert markiert ist, ist der angezeigte Wert genau
    // dieser Stand; danach friert er ein, bis wieder gespeichert wurde. Ohne
    // ihn bliebe ein Hin-und-Zurueck-Tippen im Feld (14 -> 1 -> 14) als
    // Aenderung stehen -- samt Auto-Save, der ein noch nie gespeichertes
    // Dokument allein deswegen anlegen wuerde.
    const gespeichertesZahlungszielRef = useRef<number | undefined>(undefined);
    useEffect(() => {
        if (!zahlungszielGeaendert) gespeichertesZahlungszielRef.current = kontextDaten.zahlungsziel;
    }, [kontextDaten.zahlungsziel, zahlungszielGeaendert]);

    // Vorlagen
    const [textbausteine, setTextbausteine] = useState<TextbausteinApiDto[]>([]);
    const [leistungen, setLeistungen] = useState<LeistungApiDto[]>([]);
    const [arbeitszeitarten, setArbeitszeitarten] = useState<ArbeitszeitartApiDto[]>([]);

    // Preview State
    const [showPreview] = useState(true);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    // Startet mit true: die Vorschau ist von Anfang an offen und wird beim Öffnen sofort erzeugt.
    const [previewLoading, setPreviewLoading] = useState(showPreview);
    const [previewStale, setPreviewStale] = useState(false);
    // Wird nach jedem erfolgreichen Speichern hochgezaehlt und stoesst ein
    // sofortiges Neu-Rendern der Vorschau an. Noetig fuer Kopfdaten wie die
    // Rechnungsadresse: sie stecken in `kontextDaten`, nicht in `blocks`, und
    // werden vom Debounce-Effect auf den Bloecken daher nicht erfasst.
    const [previewRefreshToken, setPreviewRefreshToken] = useState(0);
    // Laufende Nummer der jeweils juengsten Preview-Anfrage (siehe handlePreview).
    const previewRequestIdRef = useRef(0);
    // Spiegelt die aktuell angezeigte Blob-URL, damit sie beim Schliessen des
    // Editors freigegeben werden kann, ohne den Effect an `previewUrl` zu binden.
    const previewUrlRef = useRef<string | null>(null);

    // UI Layout State


    // Picker Modal State
    const [showTextbausteinPicker, setShowTextbausteinPicker] = useState(false);
    const [showLeistungPicker, setShowLeistungPicker] = useState(false);
    // Block-id der Position, die gerade als neue Leistung gespeichert wird (Dialog offen).
    const [neueLeistungBlockId, setNeueLeistungBlockId] = useState<string | null>(null);
    const [showStundensatzPicker, setShowStundensatzPicker] = useState(false);
    // Vollbild-Auswahlfenster fuer Material aus den Artikel-Stammdaten.
    // Wird UNCONDITIONAL gemountet (siehe Rendering weiter unten) - der Dialog
    // setzt seine eigene Auswahl beim Uebergang false -> true selbst zurueck.
    const [materialDialogOffen, setMaterialDialogOffen] = useState(false);
    // Warenkorb aus dem Lieferanten-Shop (IDS). Ohne freigeschaltete Shops bleibt der Knopf weg.
    const [shopDialogOffen, setShopDialogOffen] = useState(false);
    const idsLieferanten = useIdsVerfuegbar();
    const idsLieferantenNamen = useMemo(() => idsLieferanten.map((l) => l.lieferantName), [idsLieferanten]);
    // Auswahl-Dialog "Was hinzufuegen?" (Leistung / Stundensatz / Textbaustein),
    // ausgeloest ueber das "+"-Symbol unter einer Karte oder im Bauabschnitt.
    const [showAddTypeDialog, setShowAddTypeDialog] = useState(false);
    // Position fuer den naechsten Insert. Wird ueberschrieben, sobald ein "+"
    // (Karte/Section) den Picker bzw. AddTypeDialog oeffnet. Header-Icons
    // resetten bewusst auf 'between-nach'.
    const pendingAnchorRef = useRef<InsertAnchor>(DEFAULT_ANCHOR);

    // Global Toolbar State
    const [activeEditor, setActiveEditor] = useState<ReturnType<typeof useEditor> | null>(null);
    const [activeEditorId, setActiveEditorId] = useState<string | null>(null);
    const editorRefs = useRef<Record<string, EditorInstance | null>>({});
    const setEditorRef = useCallback((editorKey: string, editor: EditorInstance | null) => {
        editorRefs.current[editorKey] = editor;
    }, []);
    // Editor-Instanzen, die der Nutzer wirklich fokussiert hat (echte
    // Interaktion) -- siehe istPhantomTiptapAenderung weiter unten. Ein
    // WeakSet haengt an der Editor-INSTANZ (Objekt-Identitaet), nicht an
    // einem Key, weil derselbe Aufruf (onEditorFocus) fuer content- und
    // description-Editoren identisch aussieht und nur die Instanz sie
    // unterscheidet. Deklaration hier (vor isLocked), Verwendung erst nach
    // dessen Berechnung -- siehe markiereEditorFokussiert dort.
    const fokussierteEditorenRef = useRef<WeakSet<NonNullable<EditorInstance>>>(new WeakSet());

    // Global Rabatt
    const [globalRabatt, setGlobalRabatt] = useState<number>(0);
    const globalRabattRef = useRef(globalRabatt);
    useLayoutEffect(() => { globalRabattRef.current = globalRabatt; }, [globalRabatt]);
    const setzeGlobalRabatt = useCallback((neu: number) => {
        globalRabattRef.current = neu;
        setGlobalRabatt(neu);
    }, []);
    const [showRabattDialog, setShowRabattDialog] = useState(false);

    // Id der Leistung, von der aus der Alternativ-Dialog geoeffnet wurde (null = zu).
    const [alternativDialogAnker, setAlternativDialogAnker] = useState<string | null>(null);

    // Kategorie-Bestätigung beim Einfügen einer Leistung
    const [pendingLeistungInsert, setPendingLeistungInsert] = useState<{
        block: DocBlock;
        leistungName: string;
        kategorieId: number;
        kategoriePfad: string;
        /** Anker-Position zum Zeitpunkt des Picker-Klicks. */
        anchor: InsertAnchor;
    } | null>(null);

    // Abrechnungsverlauf: already-billed amount by other invoices (for Schlussrechnung etc.)
    const [bereitsAbgerechnetDurchAndere, setBereitsAbgerechnetDurchAndere] = useState<number | null>(null);
    // Basisdokument-Nettobetrag (Gesamtauftragssumme aus AB/Anfrage)
    const [basisdokumentBetragNetto, setBasisdokumentBetragNetto] = useState<number | null>(null);
    /**
     * Bloecke des Basisdokuments (Angebot/AB). Nur zum Benennen der entfallenen
     * bzw. zusaetzlichen Leistungen in der Differenzzeile der Schlussrechnung —
     * der Differenzbetrag selbst wird nicht daraus gerechnet.
     */
    const [basisdokumentBlocks, setBasisdokumentBlocks] = useState<DocBlock[]>([]);
    // Anzahl anderer aktiver Rechnungen am Basisdokument, nur fuer die einfache
    // Rechnung: entscheidet, ob sie den Auftrag abschliesst (Differenzzeile).
    // null = nicht geladen, dann gibt es auch keinen Positionsvergleich.
    const [andereAktiveRechnungen, setAndereAktiveRechnungen] = useState<number | null>(null);
    // Kennzeichen "schliesst Auftrag ab" aus dem positionenJson. Zaehlt nur bei
    // gesperrten Rechnungen (Nachdruck nach dem Buchen); fehlt es, bleibt eine
    // Bestandsrechnung wie bisher.
    const [gespeichertSchliesstAuftragAb, setGespeichertSchliesstAuftragAbState] = useState(false);
    // Ref-Spiegel fuer bucheDokument: Export, Druck und Mail speichern im selben
    // Klick direkt vorher. Der State aus dieser Closure waere dann noch der alte
    // und loeste ein zweites, ueberfluessiges Speichern aus.
    const gespeichertSchliesstAuftragAbRef = useRef(false);
    const setGespeichertSchliesstAuftragAb = useCallback((wert: boolean) => {
        gespeichertSchliesstAuftragAbRef.current = wert;
        setGespeichertSchliesstAuftragAbState(wert);
    }, []);
    /**
     * Fortschrittsbalken im Abrechnungsstand auf der Rechnung zeigen. Liegt in
     * positionenJson, Standard an — Bestandsrechnungen sehen aus wie bisher.
     */
    const [balkenAnzeigen, setBalkenAnzeigen] = useState<boolean>(true);
    const balkenAnzeigenRef = useRef(balkenAnzeigen);
    useLayoutEffect(() => { balkenAnzeigenRef.current = balkenAnzeigen; }, [balkenAnzeigen]);
    const setzeBalkenAnzeigen = useCallback((neu: boolean) => {
        balkenAnzeigenRef.current = neu;
        setBalkenAnzeigen(neu);
    }, []);
    // Eigene Texte bzw. ausgeblendete Zeilen im Abrechnungsstand (positionenJson.abschlussZeilen).
    const { abschlussZeilen, abschlussZeilenRef, setzeAbschlussZeilen } = useAbschlussZeilen();
    // Detaillierte Abrechnungspositionen für die ClosureBlock-Anzeige
    const [abrechnungsPositionen, setAbrechnungsPositionen] = useState<Array<{
        dokumentNummer: string;
        typ: string;
        datum: string;
        betragNetto: number;
        abschlagsNummer?: number;
    }>>([]);

    // Firmenfarbe aus den Firmeninformationen — Akzent im Abrechnungsstand.
    // null = noch nicht geladen oder nicht hinterlegt, dann greift die Standardfarbe.
    const [firmenfarbe, setFirmenfarbe] = useState<string | null>(null);

    useEffect(() => {
        let abgebrochen = false;
        fetch('/api/firma')
            .then(res => (res.ok ? res.json() : null))
            .then(daten => {
                if (!abgebrochen && daten?.firmenfarbe) {
                    setFirmenfarbe(daten.firmenfarbe);
                }
            })
            .catch(() => {
                // Ohne Farbe zeichnet der Abrechnungsstand in der Standardfarbe.
            });
        return () => { abgebrochen = true; };
    }, []);

    // Abschlagsrechnung: user-defined installment amount
    const [abschlagBetragNetto, setAbschlagBetragNetto] = useState<number | null>(null);
    // Abschlagsrechnung: Eingabemodus und Originalwert
    const [abschlagInfo, setAbschlagInfo] = useState<{ modus: string; eingabeWert: number } | null>(null);

    // GAEB Import
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [importToast, setImportToast] = useState<{ type: 'success' | 'error'; message: string; details?: string } | null>(null);
    const importToastTimer = useEinmalTimer();

    /** Alle Rechnungstypen die nach GoBD beim Export/Druck gesperrt werden müssen */
    const invoiceTypes: AusgangsGeschaeftsDokumentTyp[] = [
        'RECHNUNG', 'TEILRECHNUNG', 'ABSCHLAGSRECHNUNG', 'SCHLUSSRECHNUNG', 'GUTSCHRIFT', 'STORNO'
    ];
    // Spiegelt AusgangsGeschaeftsDokument#istBearbeitbar() im Backend:
    // storniert / digital angenommen (Angebot, AB) / gebuchte Rechnung → gesperrt.
    // Wichtig für Auto-Save: ohne digitalAngenommen-Check feuert der 10s-Interval
    // bei angenommenen Angeboten/ABs endlos Server-Fehler-Alerts.
    // Eigene Variable statt inline in isLocked (Design-Review Abschnitt 7-2,
    // Befund 1): DocumentEditorHeader zeigte das "Gebucht"-Badge bisher an
    // isLocked, das auch bei Fremdsperre/eigenem "Fertig"/Sperrfehler true
    // ist -- "Gebucht" heisst in diesem Produkt "in der Buchhaltung erfasst"
    // und stand damit faelschlich auf Dokumenten mit gebucht=false. Diese
    // Variable ist die einzig korrekte Bedingung fuer das Badge und wird als
    // eigener Prop an den Header gegeben (siehe unten); isLocked bleibt
    // unveraendert fuers Deaktivieren der Bearbeitungs-Aktionen.
    const istGebuchteRechnung = !!(dokument?.gebucht && dokument?.typ && invoiceTypes.includes(dokument.typ));
    // `readOnly` kommt zusaetzlich von der Seite: haelt sie gerade kein Lock
    // (z.B. ein Kollege bearbeitet den Datensatz), darf hier trotzdem nur
    // gelesen werden -- der Editor haelt selbst kein Lock mehr, siehe unten.
    const isLocked = !!(
        readOnly ||
        dokument?.storniert ||
        dokument?.digitalAngenommen ||
        istGebuchteRechnung
    );
    const currentDokumentTyp = dokument?.typ ?? dokumentTyp;
    const showFinalizationPrompt = invoiceTypes.includes(currentDokumentTyp);

    // --- Rückgängig & Wiederholen ---
    const leseStand = useCallback((): DokumentStand => ({
        blocks: blocksRef.current,
        globalRabatt: globalRabattRef.current,
        datum: datumRef.current,
        zahlungsziel: kontextDatenRef.current.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE,
        rechnungsadresse: kontextDatenRef.current.rechnungsadresse ?? '',
        balkenAnzeigen: balkenAnzeigenRef.current,
        abschlussZeilen: abschlussZeilenRef.current,
    }), [kontextDatenRef, abschlussZeilenRef]);

    const schreibeStand = useCallback((werte: Partial<DokumentStand>) => {
        if (werte.blocks !== undefined) setzeBlocks(werte.blocks);
        if (werte.globalRabatt !== undefined) setzeGlobalRabatt(werte.globalRabatt);
        if (werte.balkenAnzeigen !== undefined) setzeBalkenAnzeigen(werte.balkenAnzeigen);
        if (werte.abschlussZeilen !== undefined) setzeAbschlussZeilen(werte.abschlussZeilen);
        if (werte.datum !== undefined) {
            datumRef.current = werte.datum;
            setDatum(werte.datum);
        }
        if (werte.zahlungsziel !== undefined || werte.rechnungsadresse !== undefined) {
            const naechste: KontextDaten = { ...kontextDatenRef.current };
            if (werte.zahlungsziel !== undefined) {
                naechste.zahlungsziel = werte.zahlungsziel;
                // Sticky wie bei der Adresse: ein spaeter eintreffender
                // Kontext-Load darf ein per Rueckgaengig/Wiederholen gesetztes
                // Zahlungsziel nicht mehr ueberschreiben. Vergleich gegen den
                // zuletzt GESPEICHERTEN Wert (nicht unconditional true) --
                // dieselbe Formel wie in handleZahlungszielChange, damit ein
                // Rueckgaengig, das exakt auf dem gespeicherten Stand landet,
                // "Ungespeichert" korrekt wieder loescht (und ein zweites
                // Rueckgaengig, das DAVON abweicht, es korrekt wieder setzt --
                // sonst wuerde die zurueckgenommene Aenderung nie gespeichert).
                zahlungszielUserEditedRef.current = true;
                setZahlungszielGeaendert(werte.zahlungsziel !== (gespeichertesZahlungszielRef.current ?? DEFAULT_ZAHLUNGSZIEL_TAGE));
            }
            if (werte.rechnungsadresse !== undefined) {
                naechste.rechnungsadresse = werte.rechnungsadresse;
                // Bewusste Spec-Entscheidung (Abschnitt "Rechnungsadresse"):
                // gleicher sichtbarer Stand, kein Sonderpfad zum Zuruecksetzen
                // des Overrides -- unconditional true, wie beim normalen Edit.
                adresseUserEditedRef.current = true;
                setAdresseGeaendert(true);
            }
            setzeKontextDaten(naechste);
        }
    }, [setzeBlocks, setzeGlobalRabatt, setzeBalkenAnzeigen, setzeAbschlussZeilen, setzeKontextDaten, kontextDatenRef, adresseUserEditedRef, zahlungszielUserEditedRef]);

    const verlauf = useDokumentVerlauf({ leseStand, schreibeStand, gesperrt: isLocked });

    /**
     * Ersetzt die bisherigen Inline-`onEditorFocus`-Callbacks: markiert die
     * fokussierte Editor-Instanz als "vom Nutzer beruehrt" (siehe
     * istPhantomTiptapAenderung) und spiegelt sie weiterhin in die globale
     * Toolbar (activeEditor).
     */
    const markiereEditorFokussiert = useCallback((editor: EditorInstance | null) => {
        if (editor) fokussierteEditorenRef.current.add(editor);
        setActiveEditor(isLocked ? null : editor);
    }, [isLocked]);

    /**
     * Befund aus dem Abschnitt-1-Review: der setEditable-Effekt in
     * TiptapEditor.tsx feuert beim Mounten EIN onChange mit Tiptaps
     * normalisiertem HTML (z.B. '' -> '<p></p>'), bevor der Nutzer irgendetwas
     * getan hat. Ohne dieses Gate waere das schon beim blossen Oeffnen eines
     * Dokuments ein Verlaufsschritt, den niemand ausgeloest hat. Gate ueber
     * den Fokus: nur eine Editor-Instanz, die der Nutzer nachweislich
     * fokussiert hat (siehe markiereEditorFokussiert), darf einen Schritt
     * erzeugen -- alles andere (Mount-Aufruf, ein spaeterer erneuter
     * setEditable-Trigger vor dem ersten Fokus) laeuft automatisch durch.
     * Betrifft nur content/description (die einzigen Tiptap-Felder); alle
     * anderen Felder (title/quantity/...) sind normale kontrollierte Inputs
     * ohne dieses Mount-Verhalten.
     */
    const istPhantomTiptapAenderung = useCallback((id: string, updates: Partial<DocBlock>): boolean => {
        const feld = updates.content !== undefined ? 'content' : updates.description !== undefined ? 'description' : null;
        if (!feld) return false;
        const editorKey = feld === 'content' ? id : `${id}-desc`;
        const editor = editorRefs.current[editorKey];
        return !editor || !fokussierteEditorenRef.current.has(editor);
    }, []);

    // --- Placeholders ---
    // keepZahlungszielPlatzhalter: laesst {{ZAHLUNGSZIEL}} und {{ZAHLUNGSZIEL_TAGE}}
    // als Platzhalter stehen (Editor-Chips); PDF/Vorschau loesen beide auf.
    // `datum` wird über `datumRef.current` gelesen (synchron gebumptes
    // Versanddatum), bleibt aber in den Deps, damit Vorschau und PDF bei
    // DatePicker-Änderungen neu erzeugt werden.
    const replacePlaceholders = useCallback((text: string, isPreview: boolean = true, keepZahlungszielPlatzhalter: boolean = false): string =>
        loesePlatzhalterAuf(
            text,
            { kontext: kontextDaten, dokumentTyp, dokumentNummer, datumIso: datumRef.current, betreff },
            { vorschau: isPreview, zahlungszielPlatzhalterBehalten: keepZahlungszielPlatzhalter },
        ),
        // eslint-disable-next-line react-hooks/exhaustive-deps
    [kontextDaten, dokumentTyp, dokumentNummer, datum, betreff]);

    // --- Zahlungsziel: geschuetzter Chip in Textbausteinen ---
    // {{ZAHLUNGSZIEL}} bleibt in block.content erhalten (friert nicht als
    // Klartext ein) und wird im Editor als nicht editierbarer Chip angezeigt.
    // Einzige Wahrheit ist kontextDaten.zahlungsziel — daraus folgen Chip-Text,
    // PDF-Text und das beim Buchen gespeicherte Faelligkeitsdatum.
    const zahlungszielTage = kontextDaten.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE;
    const zahlungszielDatumDisplay = useMemo(
        () => berechneZahlungszielDatum(datum, zahlungszielTage),
        [datum, zahlungszielTage]
    );

    /**
     * block.content → Editor-HTML: erst alle uebrigen Platzhalter aufloesen
     * (Zahlungsziel-Platzhalter bleiben stehen), dann beide Zahlungsziel-
     * Platzhalter in geschuetzte Chips wandeln. Die Reihenfolge ist wichtig:
     * replacePlaceholders normalisiert Chip-HTML defensiv zurueck zum
     * Platzhalter — liefe es NACH der Chip-Erzeugung, wuerde es den frisch
     * erzeugten Chip sofort wieder zerstoeren (Bug: Zahlungsziel stand als
     * Klartext im Editor statt als Chip).
     */
    const prepareEditorContent = useCallback(
        (text: string) => zahlungszielPlaceholderToChipHtml(
            replacePlaceholders(text, true, true),
            zahlungszielDatumDisplay,
            String(zahlungszielTage)
        ),
        [replacePlaceholders, zahlungszielDatumDisplay, zahlungszielTage]
    );

    /** Editor-HTML → block.content: Chip wieder als Platzhalter serialisieren. */
    const serializeEditorContent = useCallback(
        (html: string) => chipHtmlToZahlungszielPlaceholder(html),
        []
    );

    /**
     * Uebernimmt ein neues Zahlungsziel, wenn die Eingabe abgeschlossen ist
     * (Feld verlassen oder Enter) — nicht bei jedem Tastendruck, sonst stuende
     * die Rueckfrage unten mitten im Tippen im Weg.
     *
     * Liefert zurueck, ob der Wert uebernommen wurde: die Eingabefelder setzen
     * ihren Entwurf sonst wieder auf den geltenden Wert.
     */
    const handleZahlungszielChange = useCallback(async (tage: number): Promise<boolean> => {
        // Verteidigungslinie ohne Meldung: bei gesperrtem Dokument gibt es gar
        // kein Eingabefeld (die Summenzeile bekommt `undefined`, der Chip
        // reagiert nicht), hier kann also niemand ankommen.
        if (isLocked) return false;
        // `min`/`max` am Feld halten eine getippte Zahl nicht auf. Ungeprueft
        // ginge sie jetzt in die Datenbank und von dort in die Faelligkeit:
        // negativ hiesse "ab sofort ueberfaellig", Mahnwesen inklusive.
        if (!Number.isInteger(tage) || tage < MIN_ZAHLUNGSZIEL_TAGE || tage > MAX_ZAHLUNGSZIEL_TAGE) {
            toast.error(`Bitte ein Zahlungsziel zwischen ${MIN_ZAHLUNGSZIEL_TAGE} und ${MAX_ZAHLUNGSZIEL_TAGE} Tagen eingeben.`);
            return false;
        }
        // Laenger als der Standard: nachfragen (Nutzervorgabe 17.09.2026).
        // Ein langes Zahlungsziel ist erlaubt, soll aber eine bewusste
        // Entscheidung sein — es heisst spaeter an das eigene Geld kommen.
        if (tage > ZAHLUNGSZIEL_NACHFRAGE_AB_TAGEN) {
            const bestaetigt = await confirm({
                title: 'Langes Zahlungsziel',
                message: `Bei ${tage} Tagen Zahlungsziel ist die Rechnung erst am ${berechneZahlungszielDatum(datumRef.current, tage)} fällig. Üblich sind ${ZAHLUNGSZIEL_NACHFRAGE_AB_TAGEN} Tage. Wirklich ${tage} Tage eintragen?`,
                confirmLabel: `Ja, ${tage} Tage`,
                cancelLabel: 'Abbrechen',
                variant: 'warning',
            });
            if (!bestaetigt) return false;
        }
        // schreibeStand setzt zahlungszielUserEditedRef/zahlungszielGeaendert
        // (Zurueck auf den gespeicherten Stand ist keine Aenderung mehr: sonst
        // bliebe "Ungespeichert" stehen und der Auto-Save legte ein noch nie
        // gespeichertes Dokument allein deswegen an). Kein Bündeln über
        // schnelle Tastendrücke hinweg, da erst beim Abschluss übernommen wird
        // (siehe ZahlungszielTageEingabe) -- der Bündel-Schlüssel greift nur,
        // wenn dieselbe Feld-Änderung kurz hintereinander mehrfach eintrifft.
        verlauf.aendern({
            bezeichnung: 'Zahlungsziel geändert',
            berechne: () => ({ zahlungsziel: tage }),
            buendelSchluessel: 'kopf:zahlungsziel',
            ziel: null,
        });
        return true;
    }, [isLocked, toast, confirm, verlauf]);

    /** Position des Chip-Bearbeitungs-Popovers (null = geschlossen). */
    const [zahlungszielPopover, setZahlungszielPopover] = useState<{ top: number; left: number } | null>(null);
    const zahlungszielPopoverRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!zahlungszielPopover) return;
        const handler = (e: MouseEvent) => {
            const ziel = e.target as HTMLElement;
            // Ein Chip zaehlt nicht als "draussen": dasselbe mousedown oeffnet
            // das Popover gerade und laeuft danach bis hierher weiter (es wuerde
            // sich sonst im selben Moment wieder schliessen), und ein zweiter
            // Chip im selben Textbaustein setzt es nur um.
            if (ziel.closest?.('[data-zahlungsziel-chip]')) return;
            if (zahlungszielPopoverRef.current && !zahlungszielPopoverRef.current.contains(ziel)) {
                setZahlungszielPopover(null);
            }
        };
        // Escape schliesst das Popover; der Entwurf im Feld wird davon unabhaengig
        // verworfen (siehe ZahlungszielTageEingabe).
        const escHandler = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setZahlungszielPopover(null);
        };
        document.addEventListener('mousedown', handler);
        document.addEventListener('keydown', escHandler);
        return () => {
            document.removeEventListener('mousedown', handler);
            document.removeEventListener('keydown', escHandler);
        };
    }, [zahlungszielPopover]);

    const handleZahlungszielChipClick = useCallback((anchor: DOMRect) => {
        setZahlungszielPopover({
            top: anchor.bottom + 8,
            left: Math.max(8, Math.min(anchor.left, window.innerWidth - 280)),
        });
    }, []);

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
                const newBlocks: ImportedGaebBlock[] = await res.json();

                const mapBlock = (b: ImportedGaebBlock): DocBlock => ({
                    ...b,
                    id: crypto.randomUUID(),
                    type: b.type === 'TEXT' ? 'TEXT' : (b.type === 'SECTION_HEADER' ? 'SECTION_HEADER' : 'SERVICE'),
                    quantity: b.type === 'TEXT' || b.type === 'SECTION_HEADER' ? undefined : (Number(b.quantity) || 1),
                    price: b.type === 'TEXT' || b.type === 'SECTION_HEADER' ? undefined : (Number(b.price) || 0),
                    content: b.type === 'TEXT' ? (b.content || '') : undefined,
                    sectionLabel: b.type === 'SECTION_HEADER' ? (b.sectionLabel || 'Bauabschnitt') : undefined,
                    children: b.type === 'SECTION_HEADER' && Array.isArray(b.children)
                        ? b.children.map((child) => mapBlock(child))
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

                verlauf.aendern({
                    bezeichnung: 'GAEB-Positionen eingefügt',
                    berechne: stand => ({ blocks: insertBlocksBeforeClosure(stand.blocks, mappedBlocks) }),
                    buendelSchluessel: null,
                    ziel: mappedBlocks[0] ? { blockId: mappedBlocks[0].id } : null,
                });
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
        setImportToast({ type, message, details });
        importToastTimer.starte(() => setImportToast(null), 5000);
    };


    // Frisch umgewandeltes Dokument: Standard-Textbausteine des neuen Typs einmalig
    // nachladen (Erklärung bei „Auto-Load Standard-Textbausteine je Dokumenttyp“).
    // Vor „Load Document“ deklariert, weil das Laden das Flag setzt.
    const [standardTexteErneuern, setStandardTexteErneuern] = useState(false);

    // --- Load Document ---
    useEffect(() => {
        const loadDokument = async () => {
            if (!dokumentId) {
                setLoading(false);
                return;
            }
            // Nachbesserung 1: dokumentId wechselt jetzt auch dann, wenn WIR
            // selbst gerade erst angelegt haben (syncDocumentIdInUrl schreibt
            // die neue Id ueber den Router in die URL, die Seite reicht sie
            // als Prop zurueck). dokument.id ist dann bereits exakt diese Id
            // (setDokument(created) lief im selben handleSave-Aufruf vorher).
            // Ein erneutes Fetch wuerde hier zwischenzeitliche, noch nicht
            // gespeicherte Tipp-Aenderungen des Nutzers stillschweigend mit
            // dem gerade erst gespeicherten Stand ueberschreiben -- also
            // ueberspringen, wenn wir dieses Dokument schon vollstaendig im
            // State haben.
            if (dokument?.id === dokumentId) {
                setLoading(false);
                return;
            }
            try {
                const res = await fetch(`/api/ausgangs-dokumente/${dokumentId}`);
                if (res.ok) {
                    const data = ueberlagereDokumentMitStand(await res.json() as AusgangsGeschaeftsDokument, standAnsicht);
                    setDokument(data);
                    setDokumentNummer(data.dokumentNummer);
                    setDokumentTyp(data.typ);
                    setBetreff(data.betreff || '');
                    // Gespeichertes Datum zeigen, nicht still auf heute stellen: Welches Datum oben
                    // steht, fragt der erste Dialog beim endgültigen Versand (DokumentDatumWahl).
                    setDatum(data.datum);
                    datumRef.current = data.datum;
                    // Kunde/Projekt/Anfrage und Vorgaenger: abwarten, damit umgewandelte
                    // Standardtexte nicht mit leerem {{BEZUGSDOKUMENTDATUM}} entstehen.
                    await ladeKontextFuerDokument(data);

                    let loadedBlocks: DocBlock[] = [];
                    let loadedGlobalRabatt = 0;
                    // Ladebaseline fuer baueDokumentSignatur unten -- die State-Variable
                    // balkenAnzeigen ist zu diesem Zeitpunkt noch nicht aktualisiert.
                    let geladenerBalken = true;
                    let geladeneAbschlussZeilen: AbschlussZeilen = {};
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
                                // Fehlendes Flag = an: Bestandsrechnungen behalten ihren Balken.
                                geladenerBalken = parsed.abrechnungsstandBalkenAnzeigen !== false;
                                setzeBalkenAnzeigen(geladenerBalken);
                                // Fehlender Schluessel = alles automatisch (Bestandsrechnungen).
                                geladeneAbschlussZeilen = normalisiereAbschlussZeilen(parsed.abschlussZeilen);
                                setzeAbschlussZeilen(geladeneAbschlussZeilen);
                                setGespeichertSchliesstAuftragAb(parsed.schliesstAuftragAb === true);
                                if (parsed.abschlagInfo) {
                                    setAbschlagInfo(parsed.abschlagInfo);
                                }
                                // Vom Backend beim Umwandeln gesetzt: alte Standard-
                                // Textbausteine wurden entfernt, die fuer den neuen Typ
                                // konfigurierten sollen einmalig nachgeladen werden.
                                // Das Flag verschwindet beim ersten Speichern automatisch,
                                // weil der Save-Payload nur {blocks, globalRabatt,
                                // abschlagInfo} neu aufbaut.
                                if (parsed.standardTextbausteineErneuern === true) {
                                    setStandardTexteErneuern(true);
                                }
                            }
                            // Automatische Aenderung (Laden), kein Nutzer-Schritt: ref-first
                            // setzen, nicht ueber verlauf.aendern.
                            setzeBlocks(loadedBlocks.filter(b => b.type !== 'CLOSURE'));
                            setzeGlobalRabatt(loadedGlobalRabatt);
                        } catch (e) {
                            console.error('Fehler beim Parsen der Positionen:', e);
                        }
                    }

                    // Abschlagsbetrag aus Dokument laden
                    if (data.typ === 'ABSCHLAGSRECHNUNG' && data.betragNetto != null) {
                        setAbschlagBetragNetto(data.betragNetto);
                    }

                    // Reset saved-state ref so loaded data doesn't count as "unsaved".
                    // CLOSURE-Marker ausschliessen, da er per useEffect zur Laufzeit
                    // eingefuegt wird und nicht Teil der persistierten Daten ist.
                    geladenTimer.starte(() => {
                        const persistedLoaded = loadedBlocks.filter(b => b.id !== CLOSURE_BLOCK_ID && b.type !== 'CLOSURE');
                        lastSavedStateRef.current = baueDokumentSignatur({
                            blocks: persistedLoaded,
                            datum: data.datum,
                            betreff: data.betreff || '',
                            dokumentTyp: data.typ,
                            globalRabatt: loadedGlobalRabatt,
                            balkenAnzeigen: geladenerBalken,
                            abschlussZeilen: geladeneAbschlussZeilen,
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
                                    (sum: number, pos: AbrechnungspositionDto) => {
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
                                // Bloecke der AB fuer den Positionsvergleich der Differenzzeile
                                setBasisdokumentBlocks(parseBlocksAusPositionenJson(verlauf.basisdokumentPositionenJson));
                                // Detaillierte Positionen speichern für ClosureBlock-Anzeige
                                const anderePosDetails = (verlauf.positionen || [])
                                    .filter((pos: AbrechnungspositionDto) => pos.id !== data.id && !pos.storniert)
                                    .map((pos: AbrechnungspositionDto) => ({
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
                    } else if (data.vorgaengerId && data.typ === 'RECHNUNG') {
                        // Einfache Rechnung: nur Basisbetrag und Basis-Bloecke fuer den
                        // Positionsvergleich der Differenzzeile. Bewusst KEIN
                        // bereitsAbgerechnetDurchAndere — das zieht bei der
                        // Schlussrechnung Betraege ab und kippt istRestbetrag; die
                        // einfache Rechnung zeigt ihre Nettosumme unveraendert.
                        try {
                            const verlaufRes = await fetch(`/api/ausgangs-dokumente/${data.vorgaengerId}/abrechnungsverlauf`);
                            if (verlaufRes.ok) {
                                const verlauf: AbrechnungsverlaufDto = await verlaufRes.json();
                                if (verlauf.basisdokumentBetragNetto != null) {
                                    setBasisdokumentBetragNetto(verlauf.basisdokumentBetragNetto);
                                }
                                setBasisdokumentBlocks(parseBlocksAusPositionenJson(verlauf.basisdokumentPositionenJson));
                                setAndereAktiveRechnungen((verlauf.positionen || [])
                                    .filter((pos: AbrechnungspositionDto) => pos.id !== data.id && !pos.storniert)
                                    .length);
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
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

    // --- Auto-Load Standard-Textbausteine je Dokumenttyp ---
    // Vor-/Nachtexte werden beim erstmaligen Anlegen eines Dokuments aus der
    // Formular-Vorlage geladen (dokumentId noch nicht gesetzt). Sobald ein
    // Dokument einmal gespeichert wurde, ist der User Herr ueber den Inhalt:
    // nichts wird beim erneuten Oeffnen automatisch hinzugefuegt oder ersetzt –
    // selbst wenn der User Vor-/Nachtexte komplett geloescht hat.
    // EINZIGE AUSNAHME: frisch umgewandelte Dokumente (z.B. AB -> Abschlags-
    // rechnung). Das Backend entfernt beim Typwechsel die alten Standard-
    // Textbausteine und setzt das Flag "standardTextbausteineErneuern" ins
    // positionenJson — dann (und nur dann) laedt der Editor die fuer den neuen
    // Typ konfigurierten Defaults einmalig nach. Beim ersten Speichern wird das
    // Flag automatisch verworfen (Save-Payload baut das JSON neu auf).
    // (State dazu: standardTexteErneuern, deklariert vor „Load Document“, das ihn setzt.)
    const lastAppliedDefaultsTypRef = useRef<string | null>(null);
    /** Typ, fuer den gerade ein Defaults-Fetch laeuft (verhindert Doppel-Fetches). */
    const defaultsInFlightTypRef = useRef<string | null>(null);
    useEffect(() => {
        if (loading) return;
        if (!dokumentTyp) return;
        // Gebuchte oder digital angenommene Dokumente sind unveraenderlich —
        // auch das Umwandlungs-Flag darf deren Inhalt nicht mehr anfassen
        // (z.B. Auto-AB nach digitaler Angebotsannahme).
        if (dokument?.gebucht || dokument?.digitalAngenommen) return;

        // Warten, bis der Kontext (Kunde / Projekt) geladen ist, damit
        // {{KUNDENNAME}}, {{BAUVORHABEN}} etc. korrekt aufgeloest werden —
        // aber nur, solange der Kontext-Load noch laeuft (siehe helpers.ts).
        if (mussAufKontextWarten(kontextDaten, kontextGeladen, !!(projektId || anfrageId))) return;

        // Das Kontext-Update aus dem Vorgaenger-Request (useDokumentKontext) ist asynchron. Deshalb
        // reicht es nicht, den Request in loadDokument nur abzuwarten: Der
        // Defaults-Effekt kann vorher noch einen Zwischenrender mit leerem
        // Bezugsdatum sehen und den Platzhalter dauerhaft zu "" aufloesen.
        if (mussAufBezugsdokumentWarten(dokument?.vorgaengerId, kontextDaten)) return;

        if (lastAppliedDefaultsTypRef.current === dokumentTyp) return;

        // Bestehende Dokumente erhalten keine neuen Defaults. Sie durchlaufen
        // den Fetch nur fuer die eng begrenzte Reparatur des frueher zu ""
        // aufgeloesten {{BEZUGSDOKUMENTDATUM}}-Platzhalters.
        const nurLeeresBezugsdatumReparieren = !!dokumentId && !standardTexteErneuern;
        if (nurLeeresBezugsdatumReparieren && !dokument?.vorgaengerId) {
            lastAppliedDefaultsTypRef.current = dokumentTyp;
            return;
        }

        const typLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp;

        // Laeuft fuer diesen Typ schon ein Fetch? Dann nichts doppelt starten.
        // WICHTIG: Der "applied"-Marker wird erst NACH erfolgreichem setBlocks
        // gesetzt (nicht optimistisch vorab). Die fruehere Variante markierte
        // vor dem Fetch und brach bei jedem Effekt-Re-Run (z.B. Kontext-Update
        // waehrend des Fetches) ohne Revert ab — die Vor-/Nachtexte blieben
        // dann dauerhaft aus.
        if (defaultsInFlightTypRef.current === dokumentTyp) return;
        defaultsInFlightTypRef.current = dokumentTyp;

        (async () => {
            try {
                type DefaultsResponse = {
                    vortexte: Array<{ id: number; name: string; html?: string; beschreibung?: string }>;
                    nachtexte: Array<{ id: number; name: string; html?: string; beschreibung?: string }>;
                };

                // Fallback-Reihenfolge der Dokumenttyp-Labels (Nachtragsangebot
                // faellt auf die Angebots-Defaults zurueck, siehe helpers.ts).
                const labelKandidaten = defaultsLabelKandidaten(dokumentTyp, typLabel);

                let data: DefaultsResponse | null = null;
                for (const label of labelKandidaten) {
                    const tplRes = await fetch(
                        `/api/formulare/templates/selection?dokumenttyp=${encodeURIComponent(label)}`,
                    );
                    if (tplRes.status !== 200) continue;
                    const templateName = (await tplRes.text()).trim();
                    if (!templateName) continue;

                    const dfRes = await fetch(
                        `/api/formulare/templates/${encodeURIComponent(templateName)}/textbaustein-defaults/resolve?dokumenttyp=${encodeURIComponent(label)}`,
                    );
                    if (!dfRes.ok) continue;
                    const kandidat: DefaultsResponse = await dfRes.json();
                    data = kandidat;
                    if ((kandidat.vortexte?.length || 0) > 0 || (kandidat.nachtexte?.length || 0) > 0) break;
                }

                if (!data) return;
                if (nurLeeresBezugsdatumReparieren) {
                    const defaultsByKey = new Map<string, { html?: string; beschreibung?: string }>();
                    for (const item of data.vortexte || []) defaultsByKey.set(`VOR:${item.id}`, item);
                    for (const item of data.nachtexte || []) defaultsByKey.set(`NACH:${item.id}`, item);

                    setzeBlocks(blocksRef.current.map(block => {
                        if (!block.textbausteinRolle || block.textbausteinId == null) return block;
                        const item = defaultsByKey.get(`${block.textbausteinRolle}:${block.textbausteinId}`);
                        const rawHtml = item?.html || item?.beschreibung || '';
                        const reparierterInhalt = repariereLeeresBezugsdatumInStandardtext(
                            block.content || '',
                            rawHtml,
                            kontextDaten.bezugsdokumentDatum || '',
                            html => replacePlaceholders(html, false, true),
                        );
                        if (reparierterInhalt === (block.content || '')) return block;

                        return {
                            ...block,
                            content: reparierterInhalt,
                        };
                    }));
                    // Automatische Aenderung, kein Nutzer-Schritt -- trifft dieser
                    // Fetch erst ein, nachdem der Nutzer schon etwas geaendert hat,
                    // beginnt der Verlauf laut Spec neu (Randfall).
                    verlauf.leeren();
                    lastAppliedDefaultsTypRef.current = dokumentTyp;
                    return;
                }

                const buildBlock = (item: { id: number; html?: string; beschreibung?: string }, rolle: 'VOR' | 'NACH'): DocBlock => {
                    const rawHtml = item.html || item.beschreibung || '';
                    // Zahlungsziel-Platzhalter NICHT aufloesen: sie bleiben in
                    // block.content erhalten und werden im Editor als geschuetzte
                    // Chips gerendert (sonst friert "8 Tage" als Klartext ein).
                    const resolvedHtml = replacePlaceholders(rawHtml, false, true);
                    return {
                        id: crypto.randomUUID(),
                        type: 'TEXT',
                        content: resolvedHtml,
                        fontSize: 10,
                        fett: false,
                        textbausteinRolle: rolle,
                        textbausteinId: item.id,
                        textbausteinDokumenttyp: dokumentTyp,
                    };
                };

                const vorBlocks = (data.vortexte || []).map(v => buildBlock(v, 'VOR'));
                const nachBlocks = (data.nachtexte || []).map(n => buildBlock(n, 'NACH'));

                // Vorhandene Default-Bloecke entfernen, manuell eingefuegte Texte bleiben
                const cleaned = blocksRef.current.filter(b => b.textbausteinRolle == null);
                const firstLeistungIdx = cleaned.findIndex(
                    b => b.type === 'SERVICE' || b.type === 'SECTION_HEADER',
                );
                const naechsteBlocks = vorBlocks.length === 0 && nachBlocks.length === 0
                    ? cleaned
                    : firstLeistungIdx === -1
                        // Noch keine Leistungen: Vor- und Nachtexte einfach anhaengen
                        ? [...cleaned, ...vorBlocks, ...nachBlocks]
                        : [
                            ...cleaned.slice(0, firstLeistungIdx),
                            ...vorBlocks,
                            ...cleaned.slice(firstLeistungIdx),
                            ...nachBlocks,
                        ];
                setzeBlocks(naechsteBlocks);
                // Automatische Aenderung, kein Nutzer-Schritt -- trifft dieser Fetch
                // erst ein, nachdem der Nutzer schon etwas geaendert hat, beginnt der
                // Verlauf laut Spec neu (Randfall).
                verlauf.leeren();
                // Erfolg: erst jetzt als "angewendet" markieren und das
                // Umwandlungs-Flag verbrauchen — die Defaults des neuen Typs sind drin.
                lastAppliedDefaultsTypRef.current = dokumentTyp;
                setStandardTexteErneuern(false);
            } catch {
                // Stumm: fehlende Defaults sind kein Fehler. Der Marker wurde noch
                // nicht gesetzt, ein spaeterer Re-Render (z.B. nach Reconnect)
                // versucht das Laden automatisch erneut.
            } finally {
                if (defaultsInFlightTypRef.current === dokumentTyp) {
                    defaultsInFlightTypRef.current = null;
                }
            }
        })();
    // verlauf.leeren ist ueber seinen eigenen useCallback stabil (siehe
    // useDokumentVerlauf.ts); das umschliessende `verlauf`-Objekt selbst ist
    // das NICHT (frisches Objekt-Literal bei jedem Render) -- es in die
    // Dep-Liste aufzunehmen wuerde diesen schweren, mehrstufigen Fetch-Effekt
    // bei jedem Render neu anstossen.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [loading, dokumentId, dokumentTyp, replacePlaceholders, kontextDaten, projektId, anfrageId, dokument, standardTexteErneuern, kontextGeladen, setzeBlocks, verlauf.leeren]);

    // Nachbesserung 1 (Kontext-Log): frueher per window.history.replaceState
    // geschrieben -- das aendert die URL, ohne dass react-router (und damit
    // der ueber useSearchParams lesende Seiten-Lock-Hook) je davon erfaehrt.
    // Ein frisch angelegtes Dokument blieb dadurch ohne Seiten-Heartbeat und
    // verlor sein Soft-Lock nach STALE_AFTER (90s) -- der naechste Autosave
    // scheiterte dann mit 409, ohne dass der Editor das noch reparieren
    // konnte (das alte tryAcquireLock-Retry ist ja weg). Jetzt ueber
    // setSearchParams, damit die Seite (und ihr Lock-Hook) die neue Id
    // tatsaechlich mitbekommt.
    const syncDocumentIdInUrl = useCallback((savedDocumentId?: number) => {
        if (!savedDocumentId) return;
        if (!location.pathname.endsWith('/dokument-editor')) return;

        const idAsString = String(savedDocumentId);
        if (searchParams.get('dokumentId') === idAsString) return;

        const naechste = new URLSearchParams(searchParams);
        naechste.set('dokumentId', idAsString);
        setSearchParams(naechste, { replace: true });
    }, [searchParams, setSearchParams, location]);

    // Das Lock selbst haelt jetzt ausschliesslich die Seite (useDatensatzLock,
    // anderer Task) -- der Editor holt und pingt keins mehr. Frueher gab es
    // hier zusaetzlich einen eigenen, nie gestoppten Heartbeat (alle 30s auf
    // dem alten, mittlerweile abgeloesten Sperr-Endpunkt) UND ein eigenes
    // Acquire (tryAcquireLock, als 409-Retry in handleSave). Beides geriet mit dem
    // Seiten-Hook aus dem Takt und ist raus (Issue #82, Abschnitt 6a): ein
    // 409 beim Speichern bedeutet jetzt entweder eine echte Fremdsperre oder
    // eine abgelaufene Seiten-Sperre -- in beiden Faellen kann der Editor
    // hier nichts reparieren, siehe handleSave unten.

    // --- Save ---
    const speicherKonfliktRef = useRef(false);
    const handleSave = useCallback(async (): Promise<AusgangsGeschaeftsDokument | null> => {
        if (isLocked) return null;
        setSaving(true);
        // Adresse EINMAL zum Start festhalten: sie geht so in den Request und
        // dient nach der Antwort als Vergleichswert. Aendert der User waehrend
        // des Roundtrips erneut, bleibt das Dirty-Flag stehen, statt die zweite
        // Aenderung stillschweigend zu verwerfen (Lost Update).
        const gesendeteAdresse = kontextDatenRef.current.rechnungsadresse;
        const adresseUnveraendert = () => kontextDatenRef.current.rechnungsadresse === gesendeteAdresse;
        // Zahlungsziel genauso festhalten. Immer den angezeigten Wert schicken,
        // damit Datenbank (Offene Posten, Mahnwesen) und PDF uebereinstimmen.
        // `undefined` (Kontext nicht geladen) faellt aus dem JSON heraus und das
        // Backend behaelt seinen Wert — nie still DEFAULT_ZAHLUNGSZIEL_TAGE speichern.
        // Bewusste Nebenwirkung: ein Dokument ohne eigenen Wert folgte bisher
        // dauerhaft dem Kundenstamm; mit dem ersten Speichern friert der
        // angezeigte Wert fest ein. Fuer Rechnungen ist genau das richtig — im
        // PDF steht dann derselbe Wert wie in der Faelligkeit.
        const gesendetesZahlungsziel = kontextDatenRef.current.zahlungsziel;
        const zahlungszielUnveraendert = () => kontextDatenRef.current.zahlungsziel === gesendetesZahlungsziel;
        try {
            // CLOSURE-Marker wird NICHT persistiert: er ist ein UI-Konstrukt, der per
            // useEffect bei Bedarf wieder eingefuegt wird. Damit bleibt positionenJson
            // bytegenau kompatibel mit bestehenden Dokumenten.
            const persistedBlocks = blocks.filter(b => b.id !== CLOSURE_BLOCK_ID);
            const htmlInhalt = blocksToHtml(persistedBlocks);
            // Pauschalrabatt MUSS hier einfliessen: `betragNetto` ist der Wert, der
            // persistiert wird und aus dem das Backend den Bruttobetrag ableitet.
            // Projekt-Dokumentliste, Offene Posten, Projekt-Bruttopreis und die
            // Rechnungs-E-Mail lesen ausschliesslich diesen Wert — ein nur in
            // positionenJson abgelegter globalRabatt kaeme dort nie an.
            const blockNetto = calculateNettoNachRabatt(persistedBlocks, globalRabatt);
            // Für Schlussrechnung: effektiven Restbetrag verwenden (Blocksumme minus bereits abgerechnete)
            let betragNetto = blockNetto;
            if (dokumentTyp === 'SCHLUSSRECHNUNG' && bereitsAbgerechnetDurchAndere !== null) {
                betragNetto = blockNetto - bereitsAbgerechnetDurchAndere;
            } else if (dokumentTyp === 'ABSCHLAGSRECHNUNG' && abschlagBetragNetto !== null) {
                // Abschlagsrechnung: benutzerdefinierten Abschlagsbetrag verwenden
                betragNetto = abschlagBetragNetto;
            }
            // Abgeleitetes Kennzeichen mitspeichern, damit die Rechnung auch nach
            // dem Buchen (gesperrt) weiter als abschliessend druckt. Unbekannte
            // Zahl anderer Rechnungen (Verlauf nicht geladen) = weglassen statt raten.
            const schliesstAuftragAbSpeichern = !isLocked && andereAktiveRechnungen !== null
                && schliesstEinfacheRechnungAuftragAb(dokumentTyp, andereAktiveRechnungen, false);
            // Nur Abweichungen vom Automatischen speichern (fehlt = alles automatisch).
            const abschlussZeilenSpeichern = abschlussZeilenSpeicherwert(abschlussZeilen);
            const positionenData = JSON.stringify({
                blocks: persistedBlocks,
                globalRabatt,
                abrechnungsstandBalkenAnzeigen: balkenAnzeigen,
                ...(abschlussZeilenSpeichern ? { abschlussZeilen: abschlussZeilenSpeichern } : {}),
                ...(schliesstAuftragAbSpeichern ? { schliesstAuftragAb: true } : {}),
                ...(abschlagInfo ? { abschlagInfo } : {})
            });

            if (dokument?.id) {
                const id = dokument.id;
                const body = JSON.stringify({
                    datum: datumRef.current,
                    betreff,
                    betragNetto,
                    htmlInhalt,
                    positionenJson: positionenData,
                    zahlungszielTage: gesendetesZahlungsziel,
                    wiederherstellungsZaehler: dokument.wiederherstellungsZaehler,
                    // Nur mitschicken wenn User die Adresse bewusst geändert hat.
                    // Ueber das Ref lesen, nicht ueber den State — siehe kontextDatenRef.
                    ...(adresseUserEditedRef.current
                        ? { rechnungsadresseOverride: gesendeteAdresse ?? null }
                        : {})
                });
                const doPut = () => fetch(`/api/ausgangs-dokumente/${id}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body,
                });
                const res = await doPut();
                // Frueher wurde hier bei 409 automatisch neu ge-acquired
                // (tryAcquireLock) -- das gehoert jetzt der Seite, die ueber
                // useDatensatzLock den Heartbeat durchgehend am Laufen haelt.
                // Ein 409 hier bedeutet also entweder eine echte Fremdsperre
                // oder eine tatsaechlich abgelaufene Seiten-Sperre; beides
                // kann der Editor selbst nicht reparieren.
                //
                // Zwei GRUNDVERSCHIEDENE Ursachen stecken hinter demselben
                // Statuscode (Nachbesserung 1, Kontext-Log): der Lock-Check
                // in update() liefert einen reinen Text-Body ("Dokument wird
                // gerade von ... bearbeitet"), ein echter Versionskonflikt
                // (RestExceptionHandler.handleOptimisticLockingFailure) einen
                // JSON-Body. Ohne diese Unterscheidung landete beim
                // Versionskonflikt der rohe JSON-Text im Toast. Der
                // Content-Type verraet zuverlaessig, welcher Fall vorliegt.
                if (res.status === 409) {
                    const istJson = (res.headers.get('content-type') || '').includes('application/json');
                    if (istJson) {
                        // Meldung des Servers zeigen (z. B. inzwischen auf älteren Stand zurückgesetzt), danach kein Autosave mehr.
                        speicherKonfliktRef.current = true;
                        await konfliktMeldung.pruefeAntwort(res, { serverMeldung: true });
                    } else {
                        const konfliktText = await res.text().catch(() => '');
                        toast.warning(konfliktText || 'Dieses Dokument wird gerade von einem anderen Benutzer bearbeitet. Bitte kurz warten und es erneut versuchen.');
                    }
                    return null;
                }
                if (res.ok) {
                    const updated = await res.json();
                    setDokument(updated);
                    setDokumentNummer(updated.dokumentNummer);
                    syncDocumentIdInUrl(updated.id);
                    const currentState = baueDokumentSignatur({ blocks: persistedBlocks, datum: datumRef.current, betreff, dokumentTyp, globalRabatt, balkenAnzeigen, abschlussZeilen });
                    lastSavedStateRef.current = currentState;
                    setHasUnsavedChanges(false);
                    // Gespeicherten Stand spiegeln: wird direkt danach gebucht, stimmt
                    // der Nachdruck im gesperrten Zustand ohne Neuladen.
                    setGespeichertSchliesstAuftragAb(schliesstAuftragAbSpeichern);
                    setSaveSuccess(true);
                    saveSuccessTimer.starte(() => setSaveSuccess(false), 2000);
                    if (adresseUnveraendert()) setAdresseGeaendert(false);
                    // Bezugswert auf den tatsaechlich persistierten Wert setzen --
                    // auch wenn der Nutzer waehrend des Roundtrips erneut geaendert
                    // hat. Sonst haelt die Ref den Stand von VOR dem Speichern, und
                    // ein Zuruecktippen auf diesen alten Wert loeschte das
                    // Dirty-Flag, obwohl in der Datenbank etwas anderes steht.
                    gespeichertesZahlungszielRef.current = gesendetesZahlungsziel;
                    if (zahlungszielUnveraendert()) setZahlungszielGeaendert(false);
                    setPreviewRefreshToken(t => t + 1);
                    notifyDokumentChanged({ projektId, anfrageId, dokumentId: updated.id });
                    return updated;
                } else {
                    const errorText = await res.text().catch(() => '');
                    console.error('Fehler beim Speichern:', res.status, errorText);
                    toast.error(`Speichern fehlgeschlagen: ${errorText || res.statusText}`);
                }
            } else {
                const dto: AusgangsGeschaeftsDokumentErstellen = {
                    typ: dokumentTyp,
                    datum: datumRef.current,
                    betreff,
                    betragNetto,
                    htmlInhalt,
                    positionenJson: positionenData,
                    zahlungszielTage: gesendetesZahlungsziel,
                    projektId,
                    anfrageId,
                    // Hat der User die Adresse schon vor dem ersten Speichern bearbeitet,
                    // gilt seine Fassung — sonst erbt das Backend die zuletzt geänderte
                    // Rechnungsanschrift des Vorgangs.
                    // Wie im PUT-Pfad an `adresseUserEditedRef` gekoppelt statt an
                    // die Truthiness der Adresse. Hinweis: eine bewusst geleerte
                    // Adresse laesst sich beim ANLEGEN trotzdem nicht ausdruecken —
                    // das Backend wertet leer als "erbe vom Vorgang" (siehe
                    // AusgangsGeschaeftsDokumentService#erstellen). Beim Speichern
                    // eines bestehenden Dokuments greift der Reset dagegen.
                    ...(adresseUserEditedRef.current
                        ? { rechnungsadresseOverride: gesendeteAdresse ?? undefined }
                        : {})
                };
                const res = await fetch('/api/ausgangs-dokumente', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(dto)
                });
                if (res.ok) {
                    const created = await res.json();
                    // Reihenfolge nicht tauschen: setDokument(created) muss VOR
                    // syncDocumentIdInUrl(created.id) stehen und beide im selben React-Batch
                    // landen. Der Lade-Effekt prueft dokument?.id === dokumentId, um beim
                    // Prop-Wechsel undefined -> neue Id NICHT neu zu laden (sonst Zustand weg,
                    // siehe 6a Nachbesserung 1). Steht die URL-Id vor dem Dokument im State,
                    // greift der Guard nicht (Code-Review 6, 2. Durchgang).
                    setDokument(created);
                    setDokumentNummer(created.dokumentNummer);
                    syncDocumentIdInUrl(created.id);
                    const currentState = baueDokumentSignatur({ blocks: persistedBlocks, datum: datumRef.current, betreff, dokumentTyp, globalRabatt, balkenAnzeigen, abschlussZeilen });
                    lastSavedStateRef.current = currentState;
                    setHasUnsavedChanges(false);
                    // Gespeicherten Stand spiegeln: wird direkt danach gebucht, stimmt
                    // der Nachdruck im gesperrten Zustand ohne Neuladen.
                    setGespeichertSchliesstAuftragAb(schliesstAuftragAbSpeichern);
                    setSaveSuccess(true);
                    saveSuccessTimer.starte(() => setSaveSuccess(false), 2000);
                    if (adresseUnveraendert()) setAdresseGeaendert(false);
                    // Bezugswert auf den tatsaechlich persistierten Wert setzen --
                    // auch wenn der Nutzer waehrend des Roundtrips erneut geaendert
                    // hat. Sonst haelt die Ref den Stand von VOR dem Speichern, und
                    // ein Zuruecktippen auf diesen alten Wert loeschte das
                    // Dirty-Flag, obwohl in der Datenbank etwas anderes steht.
                    gespeichertesZahlungszielRef.current = gesendetesZahlungsziel;
                    if (zahlungszielUnveraendert()) setZahlungszielGeaendert(false);
                    setPreviewRefreshToken(t => t + 1);
                    notifyDokumentChanged({ projektId, anfrageId, dokumentId: created.id });
                    return created;
                } else {
                    const errorText = await res.text().catch(() => '');
                    console.error('Fehler beim Erstellen:', res.status, errorText);
                    toast.error(`Speichern fehlgeschlagen: ${errorText || res.statusText}`);
                }
            }
        } catch (err) {
            console.error('Fehler beim Speichern:', err);
            toast.error('Speichern fehlgeschlagen — Verbindung pruefen und erneut versuchen.');
        } finally {
            setSaving(false);
        }
        return null;
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [dokument, dokumentTyp, datum, betreff, blocks, projektId, anfrageId, isLocked, syncDocumentIdInUrl, konfliktMeldung, bereitsAbgerechnetDurchAndere, globalRabatt, balkenAnzeigen, abschlussZeilen, andereAktiveRechnungen]);

    // Imperatives Handle fuer die Seite (Task 7a, Issue #82): der
    // Untaetigkeits-Timer der Seite (useIdleTimer + useDatensatzLock) darf
    // die Sperre erst freigeben, NACHDEM ungespeicherte Aenderungen
    // gespeichert wurden -- "niemals freigeben, solange ungespeicherte
    // Aenderungen im Editor liegen" (Spec). Ohne ungespeicherte Aenderungen
    // ist es bewusst ein No-op (kein Leerlauf-Request bei jedem Idle-Ablauf
    // eines rein lesend offenen Dokuments). `isLocked` zusaetzlich geprueft,
    // weil `handleSave` selbst bei isLocked ohnehin `null` liefert (siehe
    // oben) -- der fruehe Ausstieg hier spart den Aufruf ganz.
    useImperativeHandle(ref, () => ({
        hatUngespeicherteAenderungen: () => hasUnsavedChanges && !isLocked,
        speichernFuerFreigabe: async () => {
            if (isLocked || !hasUnsavedChanges) return;
            await handleSave();
        },
    }), [isLocked, hasUnsavedChanges, handleSave]);

    // --- Change Detection ---
    // CLOSURE-Marker beim State-Vergleich ausblenden, damit das automatische
    // Einfuegen/Entfernen des Markers nicht als "ungespeicherte Aenderung" zaehlt.
    useEffect(() => {
        const persistedBlocks = blocks.filter(b => b.id !== CLOSURE_BLOCK_ID);
        const currentState = baueDokumentSignatur({ blocks: persistedBlocks, datum, betreff, dokumentTyp, globalRabatt, balkenAnzeigen, abschlussZeilen });
        if (lastSavedStateRef.current === '') {
            lastSavedStateRef.current = currentState;
            return;
        }
        // `adresseGeaendert` und `zahlungszielGeaendert` additiv, sonst wuerde
        // ein Undo, das die Bloecke exakt auf den gespeicherten Stand
        // zuruecksetzt, das Dirty-Flag loeschen und die geaenderte Adresse
        // bzw. das geaenderte Zahlungsziel ginge beim Schliessen verloren.
        setHasUnsavedChanges(currentState !== lastSavedStateRef.current || adresseGeaendert || zahlungszielGeaendert);
    }, [blocks, datum, betreff, dokumentTyp, globalRabatt, balkenAnzeigen, abschlussZeilen, adresseGeaendert, zahlungszielGeaendert]);

    // --- Auto-Save ---
    useEffect(() => {
        if (isLocked) return;
        const intervalId = setInterval(() => {
            const persistedBlocks = blocks.filter(b => b.id !== CLOSURE_BLOCK_ID);
            const currentState = baueDokumentSignatur({ blocks: persistedBlocks, datum, betreff, dokumentTyp, globalRabatt, balkenAnzeigen, abschlussZeilen });
            if ((currentState !== lastSavedStateRef.current || adresseGeaendert || zahlungszielGeaendert) && !saving && !speicherKonfliktRef.current) {
                handleSave();
            }
        }, 10000);
        return () => clearInterval(intervalId);
    }, [blocks, datum, betreff, dokumentTyp, globalRabatt, balkenAnzeigen, abschlussZeilen, saving, isLocked, handleSave, adresseGeaendert, zahlungszielGeaendert]);

    // --- Tab schliessen (X-Button-Ablauf, Issue #82) ---
    // Feste Reihenfolge laut Spec: (1) Warnung bei ungespeicherten Aenderungen
    // (siehe handleClose/UnsavedChangesModal), (2) speichern, (3) Sperre der
    // Seite aktiv freigeben, (4) Tab schliessen versuchen, (5) existiert der
    // Tab danach noch (z.B. macOS Safari blockiert window.close() aus
    // Skripten): ruhige Vollbild-Seite statt des Editors zeigen.
    // `onLockFreigeben` kommt von der Seite (useDatensatzLock, anderer Task)
    // und ist optional: ohne ihn (Aufrufer, die das neue Lock-Fundament noch
    // nicht verdrahtet haben) bleibt das bisherige Verhalten unveraendert --
    // die Seite entscheidet ueber `onClose`, ob sie per `navigate(-1)`
    // zurueckgeht oder den Tab schliesst (siehe DocumentEditorProps).
    const [zeigeTabSchliessenHinweis, setZeigeTabSchliessenHinweis] = useState(false);
    const mountedRef = useRef(true);
    // Haelt die Timer-Id des 150ms-Waits unten fest, damit sie beim Unmount
    // (z.B. weil ein Elternteil den Editor doch noch wegnimmt, bevor der
    // Timer feuert) sauber abgeraeumt wird -- sonst laeuft ein verwaister
    // Timer weiter und versucht spaeter, State auf einer laengst
    // ungemounteten Komponente zu setzen.
    const tabSchliessenTimerRef = useRef<ReturnType<typeof window.setTimeout> | null>(null);
    useEffect(() => {
        mountedRef.current = true;
        return () => {
            mountedRef.current = false;
            if (tabSchliessenTimerRef.current != null) {
                window.clearTimeout(tabSchliessenTimerRef.current);
                tabSchliessenTimerRef.current = null;
            }
        };
    }, []);

    const tabSchliessen = useCallback(async () => {
        if (!onLockFreigeben) {
            onClose();
            return;
        }
        try {
            await onLockFreigeben();
        } catch (err) {
            console.error('Sperre konnte nicht freigegeben werden:', err);
            toast.error('Sperre konnte nicht freigegeben werden. Bitte Seite neu laden.');
            return;
        }
        window.close();
        // window.close() wird vom Browser nicht ueberall zuverlaessig
        // ausgefuehrt (z.B. macOS Safari, wenn das Skript den Tab nicht
        // zweifelsfrei selbst geoeffnet hat). Existiert die Komponente nach
        // kurzer Wartezeit noch, zeigen wir statt stumm nichts zu tun eine
        // Bestaetigung, dass Speichern und Freigeben trotzdem geklappt haben.
        tabSchliessenTimerRef.current = window.setTimeout(() => {
            tabSchliessenTimerRef.current = null;
            if (mountedRef.current) setZeigeTabSchliessenHinweis(true);
        }, 150);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [onClose, onLockFreigeben]);

    // --- Close Handler ---
    const handleClose = useCallback(() => {
        if (hasUnsavedChanges) {
            setShowUnsavedWarning(true);
        } else {
            void tabSchliessen();
        }
    }, [hasUnsavedChanges, tabSchliessen]);

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

    // Gesperrt: offene Editoren, Picker und Dialoge beim Rendern schließen.
    const [warGesperrt, setWarGesperrt] = useState(isLocked);
    if (isLocked !== warGesperrt) {
        setWarGesperrt(isLocked);
        if (isLocked) {
            setActiveEditor(null);
            setShowTextbausteinPicker(false);
            setShowLeistungPicker(false);
            setShowStundensatzPicker(false);
            setShowAddTypeDialog(false);
            setNeueLeistungBlockId(null);
        }
    }

    useEffect(() => {
        if (!isLocked) return;
        // Defense-in-Depth: bei Lock-Uebernahme alle UI-Anker zuruecksetzen,
        // damit beim Entsperren kein stale Anker aus einem abgebrochenen
        // Picker-Flow im Ref haengen bleibt.
        pendingAnchorRef.current = DEFAULT_ANCHOR;
        // Verlauf leeren: auch bei einem reinen Soft-Lock-Wechsel (Fremdsperre,
        // eigenes "Fertig", Untaetigkeit), weil der Stand danach fremd
        // veraendert sein kann -- ein "Rueckgaengig" auf einen dann veralteten
        // Schritt waere falsch.
        verlauf.leeren();
    // Gleicher Grund wie beim Standard-Textbausteine-Effekt oben: verlauf.leeren
    // ist stabil, das `verlauf`-Objekt selbst nicht.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isLocked, verlauf.leeren]);

    // --- Rückgängig & Wiederholen: Tastatur + "Stelle zeigen" ---
    // Wurzel-Element des Editor-Bereichs (Bloecke, Rechnungsadresse) --
    // ausserhalb (Kopf-/Fusszeile, Dialoge) bleibt das normale Verhalten des
    // fokussierten Elements unangetastet.
    const editorWurzelRef = useRef<HTMLDivElement>(null);

    const [sprung, setSprung] = useState<{ text: string; ziel: VerlaufsZiel | null; nummer: number } | null>(null);
    const sprungZaehlerRef = useRef(0);

    /** Meldet einen Rückgängig-/Wiederholen-Sprung fuer Ansage + "Stelle zeigen". */
    const zeigeSprung = useCallback((richtung: 'Rückgängig' | 'Wiederholen', meldung: VerlaufsMeldung | null) => {
        if (!meldung) return;
        sprungZaehlerRef.current += 1;
        setSprung({ text: `${richtung}: ${meldung.bezeichnung}`, ziel: meldung.ziel, nummer: sprungZaehlerRef.current });
    }, []);

    // Die meisten Dialoge des Editors tragen kein role="dialog"/aria-modal --
    // deshalb hier explizit gebuendelt statt ueber darfVerlaufTasteGreifens
    // eigene [aria-modal]-Suche (die deckt nur AddTypeDialog,
    // AlternativGruppeDialog, ArtikelAuswahlDialog und den globalen
    // Bestaetigungsdialog ab).
    // --- Als neue Leistung speichern ---
    const leistungenNachId = useMemo(() => new Map(leistungen.map(l => [l.id, l])), [leistungen]);

    /** Knopf nur, wenn die Position aus einer (noch vorhandenen) Leistung stammt und davon abweicht. */
    const kannAlsNeueLeistungSpeichern = useCallback((b: DocBlock): boolean => {
        if (isLocked || b.leistungId == null) return false;
        return weichtVonLeistungAb(b, leistungenNachId.get(b.leistungId));
    }, [isLocked, leistungenNachId]);

    /** Sucht eine Position auf oberster Ebene oder in einem Bauabschnitt. */
    const findePosition = (id: string): { block: DocBlock; sectionId?: string } | null => {
        for (const b of blocks) {
            if (b.id === id) return { block: b };
            const kind = b.type === 'SECTION_HEADER' ? b.children?.find(c => c.id === id) : undefined;
            if (kind) return { block: kind, sectionId: b.id };
        }
        return null;
    };

    const neueLeistungPosition = neueLeistungBlockId ? findePosition(neueLeistungBlockId) : null;
    const neueLeistungUrsprung = neueLeistungPosition?.block.leistungId != null
        ? leistungenNachId.get(neueLeistungPosition.block.leistungId)
        : undefined;
    /** Tatsaechliche Render-Bedingung des Dialogs – Grundlage fuer einDialogOffen. */
    const neueLeistungDialogOffen = !!neueLeistungPosition && !!neueLeistungUrsprung && !isLocked;

    // Verschwindet die Position (Rueckgaengig, Loeschen) oder die Ursprungs-
    // Leistung, waehrend die Id gesetzt ist, die Id verwerfen. Sonst bliebe ein
    // unsichtbarer "offener Dialog" haengen und spaeteres Wiederauftauchen
    // oeffnete das Fenster ungefragt. (Zustand beim Rendern anpassen.)
    if (neueLeistungBlockId !== null && !neueLeistungDialogOffen) setNeueLeistungBlockId(null);


    const einDialogOffenOhneVersand = showUnsavedWarning || showAddTypeDialog || showTextbausteinPicker
        || showLeistungPicker || showStundensatzPicker || showRabattDialog || neueLeistungDialogOffen
        || alternativDialogAnker !== null || pendingLeistungInsert !== null
        || materialDialogOffen || shopDialogOffen;

    // "Stelle zeigen": Karte in den sichtbaren Bereich scrollen, kurz
    // hervorheben und bei Inhalts-Feldern den Cursor dorthin setzen.
    useEffect(() => {
        if (!sprung || !sprung.ziel) return;
        const wurzel = editorWurzelRef.current;
        if (!wurzel) return;
        const ziel = sprung.ziel;

        const karten = Array.from(wurzel.querySelectorAll<HTMLElement>('[data-block-id]'));
        const karte = karten.find(el => el.dataset.blockId === ziel.blockId)
            ?? (ziel.sectionId ? karten.find(el => el.dataset.blockId === ziel.sectionId) : undefined);
        if (!karte) return; // Stelle existiert nicht mehr (z.B. Einfuegen zurueckgenommen) -- nicht scrollen.

        scrolleMittigInDenBlick(karte);
        karte.classList.add('verlauf-hervorgehoben');
        const timer = window.setTimeout(() => karte.classList.remove('verlauf-hervorgehoben'), 1200);

        if (ziel.feld === 'content' || ziel.feld === 'description') {
            const editorKey = ziel.feld === 'content' ? ziel.blockId : `${ziel.blockId}-desc`;
            const ed = editorRefs.current[editorKey];
            if (ed && !ed.isDestroyed) ed.commands.focus();
        } else if (ziel.feld) {
            karte.querySelector<HTMLInputElement>(`[data-verlauf-feld="${ziel.feld}"]`)?.focus();
        }

        return () => window.clearTimeout(timer);
    }, [sprung]);

    /** Props fuer die Verlauf-Knoepfe in der Kopfleiste. */
    const verlaufKnoepfeProps = useMemo(() => ({
        kannRueckgaengig: verlauf.kannRueckgaengig,
        kannWiederholen: verlauf.kannWiederholen,
        naechstesRueckgaengig: verlauf.naechstesRueckgaengig,
        naechstesWiederholen: verlauf.naechstesWiederholen,
        schritte: verlauf.schritte,
        onRueckgaengig: (anzahl: number) => zeigeSprung('Rückgängig', verlauf.rueckgaengig(anzahl)),
        onWiederholen: () => zeigeSprung('Wiederholen', verlauf.wiederholen()),
    }), [verlauf, zeigeSprung]);

    /**
     * Oeffnet das "+"-Auswahl-Dialog (Was hinzufuegen?) mit der angegebenen
     * Zielposition. Wird vom Plus-Symbol unter Karten und im Bauabschnitt
     * verwendet.
     */
    const openAddTypeDialogAt = useCallback((anchor: InsertAnchor) => {
        if (isLocked) return;
        pendingAnchorRef.current = anchor;
        setShowAddTypeDialog(true);
    }, [isLocked]);

    /** Schliesst das AddType-Dialog und setzt den Anker zurueck. */
    const closeAddTypeDialog = useCallback(() => {
        pendingAnchorRef.current = DEFAULT_ANCHOR;
        setShowAddTypeDialog(false);
    }, []);

    /**
     * Wechselt vom AddType-Dialog zum konkreten Picker, ohne den vorher
     * gesetzten pendingAnchorRef zu verlieren. Wichtig: Wir resetten den
     * Anker NICHT in closeAddTypeDialog, weil der Picker ihn noch braucht.
     */
    const openPickerForType = useCallback((picker: 'TEXTBAUSTEIN' | 'LEISTUNG' | 'STUNDENSATZ') => {
        setShowAddTypeDialog(false);
        if (picker === 'TEXTBAUSTEIN') setShowTextbausteinPicker(true);
        else if (picker === 'LEISTUNG') setShowLeistungPicker(true);
        else setShowStundensatzPicker(true);
    }, []);

    /** Plus-Symbol unter einer Karte (Textbaustein/Leistung) auf Root oder im Bauabschnitt. */
    const handleAddBelow = useCallback((anchorId: string) => {
        openAddTypeDialogAt({ kind: 'after', id: anchorId });
    }, [openAddTypeDialogAt]);

    /** Plus-Symbol auf dem Bauabschnitt selbst (fuegt am Ende der Section ein). */
    const handleAddIntoSection = useCallback((sectionId: string) => {
        openAddTypeDialogAt({ kind: 'in-section', sectionId });
    }, [openAddTypeDialogAt]);

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
        if (activeContainer !== overContainer) return; // Cross-container drag handled by drop zones, not by sorting overlap

        // Verschieben ist eine Nutzeraktion: Stand ref-first lesen, Ergebnis
        // (inkl. Validierung) VOR dem Schreiben ausrechnen. Ungueltig ⇒ nur
        // Toast, kein Schritt ("Aktion, die den Stand nicht veraendert").
        // toast.warning() bewusst AUSSERHALB eines State-Updaters (StrictMode
        // wuerde ihn sonst doppelt zeigen).
        const aktuelleBlocks = blocksRef.current;

        if (activeContainer === 'root') {
            // Root-level reorder mit CLOSURE-Constraint:
            //  - Leistungen (SERVICE) und Bauabschnitte (SECTION_HEADER) muessen
            //    vor dem CLOSURE-Block bleiben.
            //  - CLOSURE selbst darf nicht vor die letzte Leistung/Bauabschnitt wandern.
            //  - Textbausteine, Separatoren etc. duerfen frei umsortiert werden.
            // Validation in blockOps.validateRootReorder (testabar mit Vitest).
            const oldIndex = aktuelleBlocks.findIndex((item) => item.id === activeId);
            const newIndex = aktuelleBlocks.findIndex((item) => item.id === overId);
            if (oldIndex === -1 || newIndex === -1) return;
            const newOrder = arrayMove(aktuelleBlocks, oldIndex, newIndex);

            const validation = validateRootReorder(newOrder, activeId);
            if (!validation.ok) {
                if (validation.reason === 'SERVICE_AFTER_CLOSURE') {
                    toast.warning('Leistungen und Bauabschnitte müssen vor dem Abschluss bleiben.');
                } else {
                    toast.warning('Der Abschluss muss nach allen Leistungen und Bauabschnitten stehen.');
                }
                return;
            }
            const bewegterBlock = aktuelleBlocks.find(b => b.id === activeId);
            // Varianten einer Auswahl muessen nebeneinander bleiben — wurde
            // etwas dazwischen gezogen, ruecken sie wieder zusammen.
            const naechsteBlocks = normalisiereAlternativGruppen(newOrder);
            verlauf.aendern({
                bezeichnung: `${blockName(bewegterBlock)} verschoben`,
                berechne: () => ({ blocks: naechsteBlocks }),
                buendelSchluessel: null,
                ziel: { blockId: activeId },
            });
        } else {
            // Within a section: reorder children
            const section = aktuelleBlocks.find(b => b.id === activeContainer);
            const bewegterBlock = section?.children?.find(c => c.id === activeId);
            if (!section?.children) return;
            const oldIndex = section.children.findIndex(c => c.id === activeId);
            const newIndex = section.children.findIndex(c => c.id === overId);
            if (oldIndex === -1 || newIndex === -1) return;
            const naechsteBlocks = normalisiereAlternativGruppen(aktuelleBlocks.map(b => (
                b.id === activeContainer && b.children
                    ? { ...b, children: arrayMove(b.children, oldIndex, newIndex) }
                    : b
            )));
            verlauf.aendern({
                bezeichnung: `${blockName(bewegterBlock)} verschoben`,
                berechne: () => ({ blocks: naechsteBlocks }),
                buendelSchluessel: null,
                ziel: { blockId: activeId, sectionId: activeContainer },
            });
        }
    }

    /** Move a SERVICE block from wherever it is into a target section */
    const moveServiceToSection = useCallback((serviceId: string, sectionId: string) => {
        // Find the service block (fuer die Bezeichnung und um fruehzeitig
        // auszusteigen, wenn es ihn gar nicht mehr gibt).
        const aktuelleBlocks = blocksRef.current;
        let serviceBlock: DocBlock | null = null;
        const rootItem = aktuelleBlocks.find(b => b.id === serviceId);
        if (rootItem && rootItem.type === 'SERVICE') {
            serviceBlock = rootItem;
        }
        if (!serviceBlock) {
            for (const b of aktuelleBlocks) {
                if (b.type === 'SECTION_HEADER' && b.children) {
                    const child = b.children.find(c => c.id === serviceId);
                    if (child) {
                        serviceBlock = child;
                        break;
                    }
                }
            }
        }
        if (!serviceBlock) return;

        verlauf.aendern({
            bezeichnung: 'Position in Bauabschnitt verschoben',
            berechne: stand => {
                // Remove from current location
                let newBlocks = stand.blocks
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

                // Container-Wechsel: die Variante verlaesst ihre Gruppe, im Ziel steht
                // sie zunaechst allein. Beides regelt die Invariante.
                return { blocks: normalisiereAlternativGruppen(newBlocks) };
            },
            buendelSchluessel: null,
            ziel: { blockId: serviceId, sectionId },
        });
    }, [verlauf]);

    /** Remove a SERVICE from a section back to root level (placed right after the section) */
    const ejectChildFromSection = useCallback((sectionId: string, childId: string) => {
        const section = blocksRef.current.find(b => b.id === sectionId);
        const child = section?.children?.find(c => c.id === childId);
        if (!child) return;

        verlauf.aendern({
            bezeichnung: `${blockName(child)} aus Bauabschnitt geholt`,
            berechne: stand => {
                // Remove from section
                const newBlocks = stand.blocks.map(b => {
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

                // Die herausgeworfene Variante wechselt den Container: im Bauabschnitt
                // bleibt evtl. nur eine zurueck, auf Root-Ebene steht sie allein.
                return { blocks: normalisiereAlternativGruppen(newBlocks) };
            },
            buendelSchluessel: null,
            ziel: { blockId: childId },
        });
    }, [verlauf]);

    // Hinweis: insertBeforeNachtexte und insertAtAnchor sind nach blockOps.ts ausgelagert,
    // damit sie in der Vitest-Suite (blockOps.test.ts) direkt mit 100% Branch-Coverage
    // getestet werden koennen.

    // CLOSURE-Sync: fuegt den virtuellen Abschluss-Block automatisch nach der
    // letzten Leistung/Section ein, sobald mindestens eine Leistung vorhanden ist.
    // Entfernt ihn wieder, wenn keine Leistung mehr existiert ODER wenn die
    // berechnete Nettosumme 0 ist (nur optionale Leistungen / alle Preise 0€).
    // Wichtig: Reihenfolge bestehender Bloecke wird NICHT veraendert – nur das
    // CLOSURE-Marker-Element wird ein-/ausgeblendet (Identity-preserved return
    // wenn nichts zu tun -> kein Re-Render-Loop).
    useEffect(() => {
        if (loading) return;
        // Automatische Aenderung, kein Nutzer-Schritt: liest blocksRef.current
        // statt `prev` (Ref-first) und setzt nur, wenn sich die Referenz
        // tatsaechlich aendert -- sonst haengt jeder Rueckgaengig/Wiederholen-
        // Sprung eine unsichtbare, identische Extra-Runde an.
        const prev = blocksRef.current;
        // Wenn gesamtNetto === 0, soll KEIN CLOSURE im Array sein – sonst
        // hinge ein leerer SortableBlock-Wrapper inkl. Drag-Handle im DOM.
        // computeClosureSummary direkt hier (noch keine Component-Variable,
        // closureSummary wird erst weiter unten gebildet).
        const gesamtNetto = computeClosureSummary(prev).gesamtNetto;
        const naechste = gesamtNetto <= 0
            ? (prev.some(b => b.id === CLOSURE_BLOCK_ID) ? prev.filter(b => b.id !== CLOSURE_BLOCK_ID) : prev)
            : syncClosureBlock(prev);
        if (naechste !== prev) setzeBlocks(naechste);
    }, [blocks, loading, setzeBlocks]);

    // --- Block Actions ---
    /** Default-Bezeichnung je Typ (Wortliste) -- `bezeichnung` ueberschreibt sie (Stundensatz vs. Leistung, beide type SERVICE). */
    const addBlock = (type: DocBlock['type'], payload?: Partial<DocBlock>, bezeichnung?: string) => {
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

        // Anker fuer die Einfuegeposition. Wird vom Aufrufer ueber
        // pendingAnchorRef gesetzt:
        //  - Header-Icons:          { kind: 'between-nach' }
        //  - Karten-Plus-Button:    { kind: 'after', id }
        //  - Bauabschnitt-Plus:     { kind: 'in-section', sectionId }
        const anchor = pendingAnchorRef.current;
        // Nach dem Lesen sofort zuruecksetzen, damit der naechste Aufruf
        // (z.B. Header-Icon ohne expliziten Set) wieder den Default trifft.
        pendingAnchorRef.current = DEFAULT_ANCHOR;

        // Wenn eine Leistung mit Produktkategorie in ein Projekt eingefügt wird,
        // den User fragen ob die Kategorie dem Projekt zugeordnet werden soll
        if (type === 'SERVICE' && newBlock.leistungId && newBlock.kategorieId && projektId) {
            const leistung = leistungen.find(l => l.id === newBlock.leistungId);
            setPendingLeistungInsert({
                block: newBlock,
                leistungName: leistung?.name || newBlock.title || 'Leistung',
                kategorieId: newBlock.kategorieId,
                kategoriePfad: leistung?.kategoriePfad || `Kategorie #${newBlock.kategorieId}`,
                anchor,
            });
            return;
        }

        // SUBTOTAL/CLOSURE entstehen nie ueber addBlock (synthetisch bzw.
        // CLOSURE-Sync) -- der Fallback deckt sie nur type-sicher ab.
        const label = bezeichnung ?? `${blockName({ ...newBlock, type })} eingefügt`;
        verlauf.aendern({
            bezeichnung: label,
            berechne: stand => ({ blocks: applyInsert(stand.blocks, newBlock, anchor) }),
            buendelSchluessel: null,
            ziel: anchor.kind === 'in-section'
                ? { blockId: newBlock.id, sectionId: anchor.sectionId }
                : { blockId: newBlock.id },
        });
    };

    /**
     * Uebernimmt die Auswahl aus dem Materialfenster als SERVICE-Bloecke.
     *
     * Bewusst NICHT ueber addBlock: addBlock fragt bei leistungId+kategorieId
     * nach der Projektkategorie zurueck (siehe oben) - fuer Material gibt es
     * diese Rueckfrage nicht. Ausserdem koennte addBlock nur einen Block pro
     * Aufruf einfuegen; bei mehreren ausgewaehlten Artikeln wuerde ein
     * wiederholter Aufruf jedes Mal auf einem veralteten blocks-State rechnen
     * und die Positionsnummern durcheinanderbringen. Deshalb werden alle
     * Bloecke in einem einzigen Zustandswechsel eingefuegt.
     */
    const uebernehmeMaterial = (auswahl: ArtikelAuswahl[]) => {
        if (isLocked || auswahl.length === 0) return;
        const neueBloecke: DocBlock[] = auswahl.map((a) => ({
            id: crypto.randomUUID(),
            type: 'SERVICE',
            title: a.titel,
            description: a.beschreibungHtml,
            quantity: a.menge,
            unit: a.einheit,
            price: a.einzelpreis,
            fontSize: 10,
            fett: false,
            optional: false,
            artikelId: a.artikelId,
        }));
        verlauf.aendern({
            bezeichnung: 'Material eingefügt',
            berechne: stand => ({ blocks: insertBlocksBeforeClosure(stand.blocks, neueBloecke) }),
            buendelSchluessel: null,
            ziel: { blockId: neueBloecke[0].id },
        });
        setMaterialDialogOffen(false);
    };

    /**
     * Uebernimmt die Positionen aus dem Lieferanten-Shop -- wie
     * uebernehmeMaterial in einem einzigen Verlaufsschritt. Der Dialog bleibt
     * offen: danach fragt er noch nach dem Artikelstamm.
     */
    const uebernehmeShopArtikel = (bloecke: DocBlock[]) => {
        if (isLocked || bloecke.length === 0) return;
        verlauf.aendern({
            bezeichnung: 'Shop-Artikel eingefügt',
            berechne: stand => ({ blocks: insertBlocksBeforeClosure(stand.blocks, bloecke) }),
            buendelSchluessel: null,
            ziel: { blockId: bloecke[0].id },
        });
    };

    const handleKategorieBestaetigt = async (kategorieId: number) => {
        if (!pendingLeistungInsert || !projektId) return;
        const finalBlock = { ...pendingLeistungInsert.block, kategorieId };
        const anchor = pendingLeistungInsert.anchor;
        verlauf.aendern({
            bezeichnung: 'Leistung eingefügt',
            berechne: stand => ({ blocks: applyInsert(stand.blocks, finalBlock, anchor) }),
            buendelSchluessel: null,
            ziel: anchor.kind === 'in-section'
                ? { blockId: finalBlock.id, sectionId: anchor.sectionId }
                : { blockId: finalBlock.id },
        });
        setPendingLeistungInsert(null);
        try {
            const res = await fetch(`/api/projekte/${projektId}/produktkategorien`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify([{ produktkategorieID: kategorieId, menge: 0 }]),
            });
            if (res.ok) {
                toast.success('Kategorie dem Projekt zugeordnet');
            } else if (res.status === 409) {
                // Kategorie war bereits zugeordnet – kein Problem
                toast.success('Kategorie ist bereits im Projekt vorhanden');
            } else {
                const text = await res.text();
                toast.error(text || 'Fehler beim Zuordnen der Kategorie');
            }
        } catch {
            toast.error('Fehler beim Zuordnen der Kategorie');
        }
    };

    const handleKategorieUeberspringen = () => {
        if (!pendingLeistungInsert) return;
        const name = pendingLeistungInsert.leistungName;
        const anchor = pendingLeistungInsert.anchor;
        const block = pendingLeistungInsert.block;
        verlauf.aendern({
            bezeichnung: 'Leistung eingefügt',
            berechne: stand => ({ blocks: applyInsert(stand.blocks, block, anchor) }),
            buendelSchluessel: null,
            ziel: anchor.kind === 'in-section'
                ? { blockId: block.id, sectionId: anchor.sectionId }
                : { blockId: block.id },
        });
        setPendingLeistungInsert(null);
        toast.info(`Leistung „${name}“ ohne Kategorie eingefügt`);
    };

    /**
     * Uebernimmt eine Feldaenderung (Titel/Menge/Einheit/Preis/Beschreibung/
     * Inhalt). `art` kommt nur bei Content-/Description-Aenderungen aus Tiptap
     * (verlaufsModus) -- siehe istPhantomTiptapAenderung fuer den Mount-Guard.
     */
    const updateBlock = (id: string, updates: Partial<DocBlock>, art?: TiptapAenderungsArt) => {
        if (isLocked) return;
        if (istPhantomTiptapAenderung(id, updates)) {
            setzeBlocks(blocksRef.current.map(b => b.id === id ? { ...b, ...updates } : b));
            return;
        }
        verlauf.aendern({
            bezeichnung: bezeichnungFuerAenderung(updates, art),
            berechne: stand => ({ blocks: stand.blocks.map(b => b.id === id ? { ...b, ...updates } : b) }),
            buendelSchluessel: buendelSchluesselFuer(id, updates, art),
            ziel: { blockId: id, feld: verlaufsFeldVon(updates) },
        });
    };

    const removeBlock = (id: string) => {
        if (isLocked) return;
        const block = blocksRef.current.find(b => b.id === id);
        verlauf.aendern({
            bezeichnung: `${blockName(block)} gelöscht`,
            // When removing a SECTION_HEADER, eject its children back to root
            berechne: stand => {
                const aktuellerBlock = stand.blocks.find(b => b.id === id);
                if (aktuellerBlock?.type === 'SECTION_HEADER' && aktuellerBlock.children && aktuellerBlock.children.length > 0) {
                    const idx = stand.blocks.findIndex(b => b.id === id);
                    const newBlocks = [...stand.blocks];
                    newBlocks.splice(idx, 1, ...aktuellerBlock.children);
                    // Aus einem aufgeloesten Bauabschnitt gekippte Varianten landen auf
                    // Root-Ebene — dort gilt die Gruppen-Invariante erneut.
                    return { blocks: normalisiereAlternativGruppen(newBlocks) };
                }
                // Bleibt nach dem Loeschen nur noch eine Variante uebrig, verliert sie
                // die Gruppe und wird wieder eine normale Zusatzposition.
                return { blocks: normalisiereAlternativGruppen(stand.blocks.filter(b => b.id !== id)) };
            },
            buendelSchluessel: null,
            ziel: { blockId: id },
        });
    };

    /** Update a child block within a section */
    const updateSectionChild = (sectionId: string, childId: string, updates: Partial<DocBlock>, art?: TiptapAenderungsArt) => {
        if (isLocked) return;
        if (istPhantomTiptapAenderung(childId, updates)) {
            setzeBlocks(blocksRef.current.map(b => (b.id === sectionId && b.children)
                ? { ...b, children: b.children.map(c => c.id === childId ? { ...c, ...updates } : c) }
                : b));
            return;
        }
        verlauf.aendern({
            bezeichnung: bezeichnungFuerAenderung(updates, art),
            berechne: stand => ({
                blocks: stand.blocks.map(b => (b.id === sectionId && b.children)
                    ? { ...b, children: b.children.map(c => c.id === childId ? { ...c, ...updates } : c) }
                    : b),
            }),
            buendelSchluessel: buendelSchluesselFuer(childId, updates, art),
            ziel: { blockId: childId, sectionId, feld: verlaufsFeldVon(updates) },
        });
    };

    /** Remove a child from a section (delete it entirely) */
    const removeSectionChild = (sectionId: string, childId: string) => {
        if (isLocked) return;
        verlauf.aendern({
            bezeichnung: 'Position gelöscht',
            berechne: stand => ({
                blocks: normalisiereAlternativGruppen(stand.blocks.map(b => (b.id === sectionId && b.children)
                    ? { ...b, children: b.children.filter(c => c.id !== childId) }
                    : b)),
            }),
            buendelSchluessel: null,
            ziel: { blockId: childId, sectionId },
        });
    };

    /**
     * Setzt eine Leistung auf "fest beauftragt" oder "optional" — der dritte
     * Zustand (Variante einer Auswahl) laeuft ueber den Dialog.
     *
     * Beide Ziele verlassen eine bestehende Gruppe, deshalb laeuft das bewusst
     * nicht ueber `updateBlock`: bleibt die Gruppe mit nur einer Variante zurueck,
     * muss `normalisiereAlternativGruppen` sie aufloesen.
     */
    const setzeWahlmodus = (block: DocBlock, modus: 'fest' | 'optional'): DocBlock =>
        ({ ...ohneAlternativGruppe(block), optional: modus === 'optional' });

    const childModusWechsel = (sectionId: string, childId: string, modus: 'fest' | 'optional') => {
        if (isLocked) return;
        verlauf.aendern({
            bezeichnung: modus === 'optional' ? 'Position auf Optional gestellt' : 'Position auf Fest beauftragt gestellt',
            berechne: stand => ({
                blocks: syncClosureBlock(normalisiereAlternativGruppen(stand.blocks.map(b =>
                    b.id === sectionId && b.children
                        ? { ...b, children: b.children.map(c => c.id === childId ? setzeWahlmodus(c, modus) : c) }
                        : b
                ))),
            }),
            buendelSchluessel: null,
            ziel: { blockId: childId, sectionId },
        });
    };

    const modusWechsel = (id: string, modus: 'fest' | 'optional') => {
        if (isLocked) return;
        verlauf.aendern({
            bezeichnung: modus === 'optional' ? 'Position auf Optional gestellt' : 'Position auf Fest beauftragt gestellt',
            berechne: stand => ({
                blocks: syncClosureBlock(normalisiereAlternativGruppen(
                    stand.blocks.map(b => b.id === id ? setzeWahlmodus(b, modus) : b)
                )),
            }),
            buendelSchluessel: null,
            ziel: { blockId: id },
        });
    };

    /**
     * Uebernimmt die im Dialog zusammengestellte Auswahl. `blockIds` ist die
     * vollstaendige Mitgliederliste — abgewaehlte Varianten fallen dadurch
     * automatisch aus der Gruppe zurueck auf "optional".
     */
    const alternativGruppeSpeichern = (blockIds: string[], name: string, bisherigeGruppe: string | null) => {
        if (isLocked) return;
        verlauf.aendern({
            bezeichnung: 'Auswahl gespeichert',
            berechne: stand => ({ blocks: syncClosureBlock(gruppiereAlsAlternativen(stand.blocks, blockIds, name, bisherigeGruppe)) }),
            buendelSchluessel: null,
            ziel: blockIds[0] ? { blockId: blockIds[0] } : null,
        });
    };

    const gruppeAufloesen = (name: string) => {
        if (isLocked) return;
        verlauf.aendern({
            bezeichnung: 'Auswahl aufgelöst',
            berechne: stand => ({ blocks: syncClosureBlock(loeseAlternativGruppeAuf(stand.blocks, name)) }),
            buendelSchluessel: null,
            ziel: null,
        });
    };

    const gruppeUmbenennen = (alt: string, neu: string) => {
        if (isLocked) return;
        // Ohne diesen Guard verschmelzen zwei gleichnamige Gruppen dokumentweit:
        // der Editor zeigt weiter zwei Kaesten, das Backend sieht eine Gruppe —
        // der Kunde waehlt zweimal und kann das Angebot danach nicht annehmen.
        if (gruppenNameVergeben(blocksRef.current, neu, alt)) {
            toast.error(`„${neu}“ ist in diesem Dokument schon vergeben. Bitte einen anderen Namen wählen.`);
            return;
        }
        verlauf.aendern({
            bezeichnung: 'Auswahl umbenannt',
            berechne: stand => ({
                blocks: normalisiereAlternativGruppen(stand.blocks.map(b => {
                    const um = (x: DocBlock) => x.alternativGruppe === alt ? { ...x, alternativGruppe: neu } : x;
                    return b.type === 'SECTION_HEADER' && b.children
                        ? { ...b, children: b.children.map(um) }
                        : um(b);
                })),
            }),
            buendelSchluessel: null,
            ziel: null,
        });
    };

    /**
     * Nach erfolgreichem Anlegen: neue Leistung in die Liste, Position mit ihr
     * verknuepfen. Damit weicht nichts mehr ab und der Knopf verschwindet.
     */
    const alsNeueLeistungGespeichert = (neu: LeistungApiDto, ordner: GewaehlterOrdner) => {
        const id = neueLeistungBlockId;
        setNeueLeistungBlockId(null);
        setLeistungen(prev => [...prev.filter(l => l.id !== neu.id), neu]);
        if (id && !isLocked) {
            const sectionId = findePosition(id)?.sectionId;
            const updates: Partial<DocBlock> = { leistungId: neu.id, kategorieId: ordner.id };
            verlauf.aendern({
                bezeichnung: 'Als neue Leistung gespeichert',
                berechne: stand => ({
                    blocks: stand.blocks.map(b => {
                        if (b.id === id) return { ...b, ...updates };
                        if (b.type === 'SECTION_HEADER' && b.children?.some(c => c.id === id)) {
                            return { ...b, children: b.children.map(c => c.id === id ? { ...c, ...updates } : c) };
                        }
                        return b;
                    }),
                }),
                buendelSchluessel: null,
                ziel: sectionId ? { blockId: id, sectionId } : { blockId: id },
            });
        }
        toast.success(`Neue Leistung „${neu.name}“ im Ordner „${ordner.pfad}“ gespeichert`);
    };

    /** Dokument buchen und State sofort aktualisieren (GoBD); liefert den gebuchten Stand oder null bei Fehler. */
    const bucheDokument = async (): Promise<AusgangsGeschaeftsDokument | null> => {
        if (!dokument?.id) return null;
        if (dokument.gebucht || dokument.storniert) return dokument; // bereits gesperrt
        try {
            // Erst unsaved changes speichern, bevor das Dokument gebucht wird.
            // Schlaegt das fehl (Lock-Konflikt, Netz, Server), darf NICHT gebucht
            // werden: das Dokument waere sonst GoBD-gesperrt mit einem Stand, den
            // der User so nie gesehen hat — etwa der alten Rechnungsadresse.
            // Auch ohne Aenderung speichern, wenn das Kennzeichen "schliesst
            // Auftrag ab" noch fehlt (z. B. vor diesem Stand angelegte Rechnung):
            // nach dem Buchen zaehlt nur noch der gespeicherte Wert.
            const kennzeichenNachtragen = andereAktiveRechnungen !== null
                && schliesstEinfacheRechnungAuftragAb(dokumentTyp, andereAktiveRechnungen, false)
                && !gespeichertSchliesstAuftragAbRef.current;
            if (hasUnsavedChanges || kennzeichenNachtragen) {
                const gespeichert = await handleSave();
                if (!gespeichert) return null;
            }
            const bookRes = await fetch(`/api/ausgangs-dokumente/${dokument.id}/buchen`, { method: 'POST' });
            if (!bookRes.ok) {
                const errText = await bookRes.text();
                throw new Error(errText || 'Buchung fehlgeschlagen');
            }
            const updated = await bookRes.json();
            setDokument(updated); // State sofort aktualisieren → UI sperrt instant
            // Saved-State synchronisieren, damit Auto-Save nicht mehr feuert.
            // Signatur exakt wie in handleSave bilden: CLOSURE-Marker raus und
            // Datum aus dem Ref. Sonst meldet die Change-Detection direkt nach
            // dem Buchen eine Abweichung, die niemand mehr speichern kann.
            const persistedBlocks = blocks.filter(b => b.id !== CLOSURE_BLOCK_ID);
            const currentState = baueDokumentSignatur({ blocks: persistedBlocks, datum: datumRef.current, betreff, dokumentTyp, globalRabatt, balkenAnzeigen, abschlussZeilen });
            lastSavedStateRef.current = currentState;
            setHasUnsavedChanges(false);
            // Muessen mit zurueck: nach dem Buchen ist das Dokument gesperrt und
            // handleSave steigt sofort aus. Ein stehengebliebenes Flag waere
            // dann nicht mehr loeschbar und meldete dauerhaft "ungespeichert".
            setAdresseGeaendert(false);
            setZahlungszielGeaendert(false);
            notifyDokumentChanged({ projektId, anfrageId, dokumentId: updated.id });
            return updated as AusgangsGeschaeftsDokument;
        } catch (err) {
            console.error('Fehler beim Buchen:', err);
            toast.error('Dokument konnte nicht gebucht werden\n' + (err instanceof Error ? 'Antwort vom Server ' + err.message : ''));
            return null;
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
        const wrapper = parseHtmlInert(html || '');
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
            // Nur in der Entwicklung: laeuft bei offener Vorschau alle zwei Sekunden.
            if (import.meta.env.DEV) {
                console.debug('[DocumentEditor] Template HTML length:', html.length);
                console.debug('[DocumentEditor] Parsed blocks from template:', parsedBlocks.map(b => ({
                    id: b.id, type: b.type, page: b.page, x: b.x, y: b.y,
                    width: b.width, height: b.height,
                    content: b.content ? b.content.substring(0, 50) : '(empty)',
                    hasStyles: !!b.styles
                })));
            }
            const layoutBlocks = parsedBlocks.map(b => ({
                id: b.id,
                type: b.type,
                page: b.page,
                x: b.x,
                y: b.y,
                z: b.z,
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

    const { einfacheRechnungSchliesstAb, differenzHinweis } = useDifferenzHinweis({
        dokumentTyp, isLocked, andereAktiveRechnungen, gespeichertSchliesstAuftragAb,
        basisdokumentBlocks, basisdokumentBetragNetto, blocks, globalRabatt,
    });

    const createPdfRequest = async (isPreview: boolean, withDraftWatermark: boolean = false) => {
        const { layoutBlocks, backgroundImage, backgroundImagePage2 } = await fetchTemplateData(dokumentTyp);

        const filledLayoutBlocks: PreviewLayoutBlock[] = layoutBlocks.map(b => {
            const copy = { ...b };
            switch (copy.type) {
                case 'dokumenttyp':
                    copy.content = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp;
                    break;
                case 'doknr':
                    copy.content = dokumentNummer || (isPreview ? 'VORSCHAU' : 'ENTWURF');
                    break;
                case 'datum':
                    copy.content = formatDatum(datumRef.current || heuteIso());
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

        // Diagnose-Log fuer die Layout-Positionierung. Nur in der Entwicklung,
        // weil es bei offener Vorschau alle zwei Sekunden feuert. Bewusst OHNE
        // Inhalte: die gefuellten Bloecke tragen Kundenname und Rechnungs-
        // adresse, also personenbezogene Daten.
        if (import.meta.env.DEV) {
            console.debug('[DocumentEditor] Filled layout blocks for PDF:', filledLayoutBlocks.map(b => ({
                id: b.id, type: b.type, page: b.page,
                x: b.x, y: b.y, width: b.width, height: b.height,
                hasContent: Boolean(b.content)
            })));
        }

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
            });
        }

        // Entwurfs-Versand: Watermark "ENTWURF" einbauen (auf allen Seiten – der
        // PDF-Service rendert Watermark-Blöcke seitenübergreifend).
        if (withDraftWatermark) {
            filledLayoutBlocks.push({
                id: 'watermark',
                type: 'watermark',
                page: 1,
                x: 100,
                y: 300,
                width: 400,
                height: 200,
                z: 1000,
                content: 'ENTWURF',
                styles: {
                    fontSize: 72,
                    fontWeight: 'bold',
                    color: '#e2e8f0',
                    textAlign: 'center'
                }
            });
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
            // TEXT and SERVICE blocks: Always use neutral defaults (10pt, not bold).
            // All formatting (bold, italic, colors) is embedded as inline
            // HTML from TiptapEditor and parsed by the backend HTML parser.
            // Using extractBoldFromHtml as block-level default
            // is wrong because it would make the ENTIRE block bold/large if ANY single
            // word was bold/large (the backend uses defaultBold/defaultFontSize for every
            // text chunk outside of explicit <strong>/<span> tags).
            fontSize: b.type === 'TEXT' || b.type === 'SERVICE' ? 10 : (b.fontSize || 10),
            fett: b.type === 'TEXT' || b.type === 'SERVICE' ? false : (b.fett || false),
            optional: b.optional || false,
            alternativGruppe: b.alternativGruppe || null,
            sectionLabel: b.sectionLabel || '',
            discount: b.discount || 0
        }));

        const kopfdaten = {
            dokumentnummer: dokumentNummer || (isPreview ? 'VORSCHAU' : 'ENTWURF'),
            rechnungsDatum: datumRef.current,
            leistungsDatum: datumRef.current,
            kundenName: kontextDaten.kundenName || 'Musterkunde',
            kundenAdresse: kontextDaten.rechnungsadresse || '',
            betreff: betreff,
            kundennummer: kontextDaten.kundennummer || '',
            bezugsdokument: kontextDaten.bezugsdokument || '',
            projektnummer: kontextDaten.projektnummer || '',
            bauvorhaben: kontextDaten.projektBauvorhaben || betreff || '',
            bezugsdokumentTyp: kontextDaten.bezugsdokumentTyp || '',
            bezugsdokumentDatum: kontextDaten.bezugsdokumentDatum || '',
            zahlungszielTage: kontextDaten.zahlungsziel ?? null
        };

        const schlusstext = '';
        let abrechnungsverlauf: PdfAbrechnungsverlauf | null = null;
        if (dokument?.vorgaengerId && brauchtAbrechnungsverlauf(dokumentTyp, dokument.vorgaengerId)) {
            const verlauf = await ladeAbrechnungsverlaufFuerPdf(dokument.vorgaengerId);
            if (verlauf) {
                abrechnungsverlauf = baueAbrechnungsverlaufRequest({
                    verlauf,
                    dokumentId: dokument.id,
                    dokumentTyp,
                    isLocked,
                    gespeichertSchliesstAuftragAb,
                    balkenAnzeigen,
                    differenzHinweis,
                    abschlussZeilen,
                    positionenNetto: computeClosureSummary(blocks).gesamtNetto,
                    abschlagBetragNetto,
                });
            }
        }

        const expliziterNettoBetrag = dokumentTyp === 'ABSCHLAGSRECHNUNG' && abschlagBetragNetto !== null
            ? abschlagBetragNetto
            : undefined;

        return {
            dokumentTyp,
            templateName: isPreview ? 'preview' : 'default',
            kopfdaten,
            layoutBlocks: filledLayoutBlocks,
            contentBlocks,
            schlusstext,
            backgroundImagePage1: backgroundImage,
            backgroundImagePage2: backgroundImagePage2,
            // Ein expliziter betragNetto (Abschlagsrechnung) ist bereits der Endbetrag.
            // Dann darf der Prozentsatz NICHT mitgehen, sonst zieht
            // RechnungPdfService#addSummenBlock den Rabatt ein zweites Mal ab.
            // Gleiche Weiche wie in AutoAuftragsbestaetigungVersandService#buildPdfBytes.
            globalRabattProzent: (globalRabatt > 0 && expliziterNettoBetrag === undefined) ? globalRabatt : null,
            abrechnungsverlauf,
            betragNetto: expliziterNettoBetrag,
            abschlagInfo: dokumentTyp === 'ABSCHLAGSRECHNUNG' && abschlagInfo ? abschlagInfo : undefined,
        };
    };

    // Mark preview as stale when content changes.
    // `kontextDaten` steht als ganzes Objekt drin, nicht nur die Adresse: auch
    // Zahlungsziel & Co. landen ueber `createPdfRequest` im PDF und gehoeren
    // damit zum Vorschau-Inhalt. Die Objekt-Identitaet aendert sich nur bei
    // einem neuen Kontext-Stand (useDokumentKontext), loest also keine Endlosschleife aus.
    // `dokumentNummer` ebenfalls: sie wird erst beim ersten Speichern vom
    // Server vergeben, vorher steht "VORSCHAU" im PDF.
    // `balkenAnzeigen` und `abschlussZeilen` aendern nur den Abrechnungsstand im PDF;
    // Rabatt, Abschlag und Sperre liest createPdfRequest ebenfalls.
    // (Zustand beim Rendern anpassen: so sieht der Token-Effekt unten den Marker
    // schon im selben Commit.)
    const { schluessel: vorschauInhalt, geaendert: vorschauInhaltGeaendert } = useVorschauMarker(
        {
            blocks, datum, betreff, dokumentTyp, kontextDaten, dokumentNummer, balkenAnzeigen, abschlussZeilen,
            globalRabatt, abschlagBetragNetto, abschlagInfo, isLocked,
        },
        () => { if (showPreview && previewUrl) setPreviewStale(true); },
    );

    // Vorschau-Block steht hinter createPdfRequest, weil handlePreview es aufruft.
    // --- Preview & Export ---
    // Erzeugt die Vorschau. Die Ladeanzeige zu Beginn setzt der Aufrufer
    // (handlePreview, Startwert beim Öffnen, Render-Anpassung nach dem Speichern),
    // damit in den Effekten unten kein setState synchron läuft.
    const erzeugeVorschau = useCallback(() => {
        // Laufende Nummer je Anfrage: bei zwei ueberlappenden Renderings darf
        // nicht die zuerst gestartete, aber spaeter eintreffende Antwort
        // gewinnen — sonst springt die Vorschau auf einen aelteren Stand
        // zurueck (z.B. auf die Adresse von vor dem Speichern).
        const requestId = ++previewRequestIdRef.current;
        return createPdfRequest(true)
            .then(request => fetch('/api/dokument-generator/preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            }))
            .then(async response => {
                if (!response.ok) return;
                const blob = await response.blob();
                if (requestId !== previewRequestIdRef.current) return;
                const url = URL.createObjectURL(blob);
                // Vorgaenger ueber das Ref freigeben statt ueber den State: der
                // Wert aus der Closure koennte veraltet sein, und ein State-
                // Updater mit Seiteneffekt liefe im StrictMode doppelt.
                const alteUrl = previewUrlRef.current;
                previewUrlRef.current = url;
                setPreviewUrl(url);
                if (alteUrl) URL.revokeObjectURL(alteUrl);
                setPreviewStale(false);
            })
            .catch(error => {
                console.error('Fehler bei Vorschau:', error);
            })
            .finally(() => {
                if (requestId === previewRequestIdRef.current) setPreviewLoading(false);
            });
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [vorschauInhalt]);

    /** Vorschau neu erzeugen, mit Ladeanzeige (Knopf, Debounce-Timer). */
    const handlePreview = useCallback(() => {
        setPreviewLoading(true);
        return erzeugeVorschau();
    }, [erzeugeVorschau]);

    // Nach dem Speichern (Token, siehe Effekt unten) die Ladeanzeige beim Rendern
    // einschalten – unter derselben Bedingung, unter der der Effekt neu rendert.
    // Ein in diesem Durchlauf gerade gesetzter Stale-Marker zählt mit.
    const vorschauVeraltet = previewStale || (vorschauInhaltGeaendert && showPreview && !!previewUrl);
    const [refreshTokenVorher, setRefreshTokenVorher] = useState(previewRefreshToken);
    if (previewRefreshToken !== refreshTokenVorher) {
        setRefreshTokenVorher(previewRefreshToken);
        if (previewRefreshToken !== 0 && vorschauVeraltet) setPreviewLoading(true);
    }

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
    // Muss dieselben Abhaengigkeiten haben wie der Stale-Marker oben, sonst
    // bliebe die Vorschau nach einer Adressaenderung dauerhaft als veraltet
    // markiert (und damit hinter dem Lade-Skelett verborgen).
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [vorschauInhalt, showPreview]);

    // Nach jedem erfolgreichen Speichern die Vorschau sofort neu rendern.
    // Damit landet z.B. eine geaenderte Rechnungsadresse direkt im Vorschau-PDF,
    // ohne dass der User zusaetzlich einen Block anfassen muss.
    useEffect(() => {
        // Token 0 = Erstmontage, noch kein Speichern passiert. Der Guard haelt
        // ausserdem den StrictMode-Doppelmount raus — bitte nicht "aufraeumen".
        if (previewRefreshToken === 0) return;
        // Nur rendern, wenn die Vorschau den gespeicherten Stand noch nicht
        // zeigt. `handleSave` haengt an neun Aufrufern (Auto-Save, Export,
        // Druck, Buchen, Mail); ohne diesen Guard liefe parallel zu jedem
        // davon eine zweite, ueberfluessige PDF-Generierung.
        // Der Stale-Marker oben wird beim Rendern gesetzt und ist deshalb hier
        // schon sichtbar, auch wenn das erste Speichern gleichzeitig die
        // Dokumentnummer setzt.
        if (!previewStale) return;
        // Ein laufender Debounce-Timer wuerde sonst kurz darauf ein zweites,
        // identisches Preview-Rendering ausloesen.
        if (previewTimerRef.current) {
            clearTimeout(previewTimerRef.current);
            previewTimerRef.current = null;
        }
        void erzeugeVorschau();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [previewRefreshToken]);

    // Letzte Blob-URL beim Schliessen des Editors freigeben, sonst haelt der
    // Browser das komplette Vorschau-PDF bis zum Reload im Speicher.
    useEffect(() => {
        previewUrlRef.current = previewUrl;
    }, [previewUrl]);
    useEffect(() => () => {
        // Hochzaehlen entwertet eine noch laufende Anfrage: ihre Antwort legt
        // sonst nach dem Unmount eine Blob-URL an, die niemand mehr freigibt.
        previewRequestIdRef.current++;
        if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
    }, []);

    // When preview panel is shown for the first time, trigger an immediate preview
    const prevShowPreview = useRef(false);
    useEffect(() => {
        if (showPreview && !prevShowPreview.current) {
            // Ladeanzeige: Startwert von previewLoading (Vorschau ist von Anfang an offen).
            void erzeugeVorschau();
        }
        prevShowPreview.current = showPreview;
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [showPreview]);

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

    // Hinweis: Bis 2026-05 wurde der ClosureBlock als externer Render-Block nach
    // einem berechneten Index eingefuegt. Seit dem DnD-Refactor lebt CLOSURE als
    // echter Block im blocks-Array (id=CLOSURE_BLOCK_ID) und wird direkt im Map
    // gerendert -> kein separater closureInsertAfterIdx mehr noetig.

    const getPositionString = (block: DocBlock): string => {
        return positionMap.get(block.id) || '';
    };

    /**
     * Rendert einen Root-Block samt Drag-Handle. Ausgelagert, weil ein Block
     * entweder direkt in der Liste steht oder — als Variante einer Auswahl —
     * innerhalb der AlternativGruppeBox. Die SortableContext-Reihenfolge bleibt
     * davon unberuehrt: gruppiert wird nur die Darstellung, nicht das Array.
     */
    const renderRootBlock = (block: DocBlock) => (
        <SortableBlock key={block.id} block={block} isLocked={isLocked}>
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
                    onChildModusWechsel={childModusWechsel}
                    onAlternativOeffnen={setAlternativDialogAnker}
                    onFocus={(id) => setActiveEditorId(id)}
                    onEditorFocus={markiereEditorFokussiert}
                    getPositionString={getPositionString}
                    sectionPosition={getPositionString(block)}
                    onAddBelow={handleAddBelow}
                    onAddIntoSection={handleAddIntoSection}
                    onGruppeUmbenennen={gruppeUmbenennen}
                    onGruppeAufloesen={gruppeAufloesen}
                    verlaufsModus
                    kannAlsNeueLeistungSpeichern={kannAlsNeueLeistungSpeichern}
                    onAlsNeueLeistungSpeichern={setNeueLeistungBlockId}
                />
            )}
            {block.type === 'TEXT' && (
                <TextBlock
                    block={block}
                    isLocked={isLocked}
                    isActive={activeEditorId === block.id}
                    editorRefs={editorRefs}
                    onEditorReady={setEditorRef}
                    onUpdate={updateBlock}
                    onRemove={removeBlock}
                    onFocus={(id) => setActiveEditorId(id)}
                    onEditorFocus={markiereEditorFokussiert}
                    prepareContent={prepareEditorContent}
                    serializeContent={serializeEditorContent}
                    onZahlungszielChipClick={handleZahlungszielChipClick}
                    onAddBelow={handleAddBelow}
                    verlaufsModus
                />
            )}
            {block.type === 'SERVICE' && (
                <ServiceBlock
                    block={block}
                    positionNumber={getPositionString(block)}
                    isLocked={isLocked}
                    isActive={activeEditorId === block.id}
                    editorRefs={editorRefs}
                    onEditorReady={setEditorRef}
                    onUpdate={updateBlock}
                    onRemove={removeBlock}
                    onModusWechsel={modusWechsel}
                    onAlternativOeffnen={setAlternativDialogAnker}
                    onFocus={(id) => setActiveEditorId(id)}
                    onEditorFocus={markiereEditorFokussiert}
                    onAddBelow={handleAddBelow}
                    verlaufsModus
                    kannAlsNeueLeistungSpeichern={kannAlsNeueLeistungSpeichern(block)}
                    onAlsNeueLeistungSpeichern={setNeueLeistungBlockId}
                />
            )}
            {block.type === 'CLOSURE' && closureSummary.gesamtNetto > 0 && (
                <ClosureBlock
                    summary={closureSummary}
                    dokumentTyp={dokumentTyp}
                    abschlagBetragNetto={abschlagBetragNetto}
                    abschlagInfo={abschlagInfo}
                    bereitsAbgerechnetDurchAndere={bereitsAbgerechnetDurchAndere}
                    abrechnungsPositionen={abrechnungsPositionen}
                    basisdokumentBetragNetto={basisdokumentBetragNetto}
                    globalRabatt={globalRabatt}
                    firmenfarbe={firmenfarbe}
                    differenzHinweis={differenzHinweis}
                    schliesstAuftragAb={einfacheRechnungSchliesstAb}
                    abschlussZeilen={abschlussZeilen}
                    onAbschlussZeileAendern={isLocked ? undefined : (schluessel, neu) => {
                        verlauf.aendern(abschlussZeileAuftrag(abschlussZeilenRef.current, schluessel, neu));
                    }}
                    balkenAnzeigen={balkenAnzeigen}
                    onBalkenAnzeigenChange={isLocked ? undefined : (anzeigen) => {
                        verlauf.aendern({
                            bezeichnung: anzeigen ? 'Balken eingeblendet' : 'Balken ausgeblendet',
                            berechne: () => ({ balkenAnzeigen: anzeigen }),
                            buendelSchluessel: null,
                            ziel: { blockId: CLOSURE_BLOCK_ID },
                        });
                    }}
                />
            )}
        </SortableBlock>
    );

    // Drucken, PDF herunterladen, per E-Mail senden (samt Datum oben auf dem Dokument).
    const versand = useDokumentVersand({
        datumRef, setDatum, isLocked, zahlungszielTage,
        dokument, setDokument, dokumentTyp, dokumentNummer, betreff, kontextDaten, nettosumme, projektId, anfrageId,
        hasUnsavedChanges, istRechnung: showFinalizationPrompt, handleSave, createPdfRequest, bucheDokument,
        verlaufLeeren: verlauf.leeren, standAnsicht: !!standAnsicht,
    });

    useVerlaufTastatur({
        aktiv: !isLocked && !einDialogOffenOhneVersand && !versand.dialogOffen,
        wurzelRef: editorWurzelRef,
        onRueckgaengig: () => zeigeSprung('Rückgängig', verlauf.rueckgaengig()),
        onWiederholen: () => zeigeSprung('Wiederholen', verlauf.wiederholen()),
    });

    // --- Loading state ---
    if (loading) {
        return (
            <div className="fixed inset-x-0 bottom-0 top-[var(--lock-leiste-hoehe,0px)] z-50 bg-white flex items-center justify-center">
                <div className="flex flex-col items-center gap-3">
                    <div className="animate-spin rounded-full h-8 w-8 border-2 border-rose-200 border-t-rose-600" />
                    <p className="text-sm text-slate-400">Wird geladen…</p>
                </div>
            </div>
        );
    }

    // Tab-Schliess-Ablauf abgeschlossen (gespeichert + Sperre freigegeben),
    // aber window.close() hat nicht gegriffen: statt des Editors nur noch
    // die Bestaetigung zeigen, siehe tabSchliessen() oben.
    if (zeigeTabSchliessenHinweis) {
        return <TabSchliessenHinweis />;
    }

    // --- Render ---
    // `top-[var(--lock-leiste-hoehe,0px)]` statt `inset-0` (Design-Review
    // Abschnitt 7-2, Befund 2): die Seite (DocumentEditorPage.tsx) setzt
    // diese Custom Property auf die gemessene Hoehe ihrer eigenen Bearbeiten-
    // Leiste, damit dieser Bereich direkt darunter beginnt -- ohne die
    // fruehere `transform`-Huelle der Seite, die (per CSS-Spezifikation) ein
    // eigenes "containing block" fuer ALLE `position:fixed`-Nachfahren
    // erzeugte und damit auch jeden Vollbild-Dialog dieses Editors auf den
    // Bereich UNTER der Leiste einschraenkte -- ein offenes Modal deckte die
    // Leiste dadurch nicht mehr ab, ein Klick auf "Fertig"/"Bearbeiten" ging
    // durch den (optisch als Overlay wirkenden, tatsaechlich aber verkleinert
    // liegenden) Backdrop hindurch. Ohne `transform`-Huelle binden sich
    // `position:fixed`-Nachfahren wieder an den echten Viewport -- Dialoge
    // (`z-[70]`+) liegen wieder ueber allem, inklusive der Leiste. Ohne
    // gesetzte Custom Property (jeder andere Verwender/Test) faellt der
    // Fallback `0px` auf exakt das bisherige `inset-0`-Verhalten zurueck.
    return (
        <div className="fixed inset-x-0 bottom-0 top-[var(--lock-leiste-hoehe,0px)] z-50 bg-slate-50 flex flex-col">
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
                istGebucht={istGebuchteRechnung}
                saving={saving}
                saveSuccess={saveSuccess}
                hasUnsavedChanges={hasUnsavedChanges}
                previewLoading={previewLoading}
                dokument={dokument}
                emailLoading={versand.email.laedt}
                onClose={handleClose}
                onSave={handleSave}
                onOpenTextbausteinPicker={() => {
                    pendingAnchorRef.current = DEFAULT_ANCHOR;
                    setShowTextbausteinPicker(true);
                }}
                onOpenLeistungPicker={() => {
                    pendingAnchorRef.current = DEFAULT_ANCHOR;
                    setShowLeistungPicker(true);
                }}
                onOpenStundensatzPicker={() => {
                    pendingAnchorRef.current = DEFAULT_ANCHOR;
                    setShowStundensatzPicker(true);
                }}
                onOpenMaterialPicker={() => setMaterialDialogOffen(true)}
                onOpenShopWarenkorb={idsLieferanten.length > 0 ? () => setShopDialogOffen(true) : undefined}
                shopLieferantenNamen={idsLieferantenNamen}
                onAddSeparator={() => {
                    pendingAnchorRef.current = DEFAULT_ANCHOR;
                    addBlock('SEPARATOR');
                }}
                onAddSectionHeader={() => {
                    pendingAnchorRef.current = DEFAULT_ANCHOR;
                    addBlock('SECTION_HEADER');
                }}
                onOpenRabattDialog={() => setShowRabattDialog(true)}
                onExport={() => { void versand.pdfExport.oeffnen(); }}
                onPrint={() => { void versand.druck.oeffnen(); }}
                onSendEmail={() => versand.email.starte(false)}
                onSendDraft={() => versand.email.starte(true)}
                onGaebImport={handleGaebImportClick}
                fileInputRef={fileInputRef}
                onFileChange={handleFileChange}
                verlauf={verlaufKnoepfeProps}
                nurAnsehen={!!standAnsicht}
                verlaufKnopf={dokument?.id && !standAnsicht ? (
                    <EditorVerlaufKnopf dokument={dokument} vorOeffnen={async () => { if (!isLocked && hasUnsavedChanges) await handleSave(); }} />
                ) : undefined}
            />

            {/* ===== Main Split Pane ===== */}
            <div className="flex-1 flex overflow-hidden relative">
                {/* Editor Area */}
                <div ref={editorWurzelRef} className="flex-1 flex flex-col overflow-hidden min-w-0 transition-all duration-500 ease-in-out">
                    {/* Ansage fuer Screenreader nach Rückgängig/Wiederholen. Bewusst OHNE
                        role="status": e2e/hilfen/design.ts sammelt [role="status"] fuer die
                        Ueberschneidungs-Pruefung ein, ein sr-only-Element wuerde dort als
                        (scheinbar) ueberlappendes Bedienelement auftauchen. */}
                    <p aria-live="polite" aria-atomic="true" className="sr-only" data-testid="verlauf-ansage">
                        {sprung?.text ?? ''}
                    </p>
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
                                onChange={(newAddr) => {
                                    // "Uebernehmen" ohne echte Aenderung darf nichts ausloesen:
                                    // `adresseUserEditedRef` schaltet das Dokument dauerhaft auf
                                    // eine eigene Adresse um und kappt damit die Vererbung vom
                                    // Vorgang — das soll keine Nebenwirkung eines No-ops sein.
                                    if (newAddr === (kontextDaten.rechnungsadresse ?? '')) return;
                                    // schreibeStand setzt adresseUserEditedRef/adresseGeaendert.
                                    verlauf.aendern({
                                        bezeichnung: 'Rechnungsadresse geändert',
                                        berechne: () => ({ rechnungsadresse: newAddr }),
                                        buendelSchluessel: null,
                                        ziel: null,
                                    });
                                }}
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
                                    {gruppiereFuerAnzeige(blocks).map(eintrag => (
                                        eintrag.art === 'gruppe' ? (
                                            <AlternativGruppeBox
                                                key={eintrag.positionen[0].id}
                                                name={eintrag.name}
                                                isLocked={isLocked}
                                                onUmbenennen={gruppeUmbenennen}
                                                onAufloesen={gruppeAufloesen}
                                            >
                                                {eintrag.positionen.map(v => renderRootBlock(v))}
                                            </AlternativGruppeBox>
                                        ) : renderRootBlock(eintrag.block)
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
                                                    <div className="text-xs text-slate-600 line-clamp-2 rich-text-einheitlich" dangerouslySetInnerHTML={{ __html: sanitizeHtml(activeDragBlock.content) }} />
                                                </div>
                                            )}
                                            {activeDragBlock.type === 'SERVICE' && (
                                                <div className="bg-white rounded-lg border border-slate-200 p-3">
                                                    <div className="text-[10px] font-semibold text-emerald-500 uppercase tracking-wider mb-1">Leistung</div>
                                                    <div className="text-sm font-medium text-slate-800 line-clamp-1">{activeDragBlock.title || 'Ohne Titel'}</div>
                                                </div>
                                            )}
                                            {activeDragBlock.type === 'CLOSURE' && (
                                                <div className="bg-slate-50 rounded-lg border border-slate-200 p-3">
                                                    <div className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-1">Abschluss</div>
                                                    <div className="text-sm font-medium text-slate-800 line-clamp-1">Zwischensumme &amp; Schluss</div>
                                                </div>
                                            )}
                                        </SortableBlock>
                                    ) : null}
                                </DragOverlay>
                            </DndContext>

                            {/* CLOSURE wird inline im map oben gerendert (Block-Type 'CLOSURE'),
                                eingefuegt durch den CLOSURE-Sync useEffect. */}

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
                    // "Datum wählen" ist laut Spec eine Einzelaktion -- nie gebuendelt.
                    verlauf.aendern({
                        bezeichnung: 'Datum geändert',
                        berechne: () => ({ datum: value }),
                        buendelSchluessel: null,
                        ziel: null,
                    });
                }}
                globalRabatt={globalRabatt}
                istRestbetrag={bereitsAbgerechnetDurchAndere !== null && bereitsAbgerechnetDurchAndere > 0}
                istGutschrift={dokumentTyp === 'GUTSCHRIFT'}
                zahlungsziel={zahlungszielTage}
                zahlungszielDatum={zahlungszielDatumDisplay}
                onZahlungszielChange={isLocked ? undefined : handleZahlungszielChange}
            />

            {/* Zahlungsziel-Chip Popover */}
            {zahlungszielPopover && (
                <div
                    ref={zahlungszielPopoverRef}
                    style={{ position: 'fixed', top: zahlungszielPopover.top, left: zahlungszielPopover.left, zIndex: 200 }}
                    className="w-64 rounded-lg border border-rose-200 bg-white p-3 shadow-xl"
                    role="dialog"
                    aria-labelledby="zahlungsziel-popover-titel"
                >
                    <p id="zahlungsziel-popover-titel" className="text-xs font-semibold text-slate-700 mb-2">Zahlungsziel</p>
                    <div className="flex items-center gap-2">
                        <ZahlungszielTageEingabe
                            tage={zahlungszielTage}
                            onUebernehmen={handleZahlungszielChange}
                            className="w-16 rounded border border-slate-300 px-2 py-1 text-sm text-center focus:border-rose-400 focus:outline-none"
                            autoFocus
                        />
                        <span className="text-xs text-slate-500">Tage</span>
                        <span className="text-xs text-slate-400">=&nbsp;fällig {zahlungszielDatumDisplay}</span>
                    </div>
                </div>
            )}

            {/* Modals */}
            <VersandDialoge
                versand={versand}
                dokumentTyp={dokumentTyp}
                istRechnung={showFinalizationPrompt}
                kontextDaten={kontextDaten}
                betreff={betreff}
                emailBetreff={emailSubject}
                projektId={projektId}
                anfrageId={anfrageId}
            />
            {showUnsavedWarning && (
                <UnsavedChangesModal
                    onCancel={() => setShowUnsavedWarning(false)}
                    onDiscard={() => {
                        setShowUnsavedWarning(false);
                        void tabSchliessen();
                    }}
                    onSaveAndClose={async () => {
                        // Nur schliessen, wenn das Speichern wirklich geklappt
                        // hat. Sonst waere die Aenderung weg und der Fehler-Toast
                        // verschwaende mit dem Editor -- kein onLockFreigeben,
                        // kein Tab-Schliess-Versuch.
                        const gespeichert = await handleSave();
                        if (!gespeichert) return;
                        setShowUnsavedWarning(false);
                        void tabSchliessen();
                    }}
                />
            )}
            {showAddTypeDialog && (
                <AddTypeDialog
                    onPick={openPickerForType}
                    onClose={closeAddTypeDialog}
                />
            )}
            {showTextbausteinPicker && (
                <TextbausteinPickerModal
                    textbausteine={textbausteine}
                    dokumentTyp={dokumentTyp}
                    onSelect={(tb) => {
                        const htmlContent = tb.html || tb.beschreibung || '';
                        // Zahlungsziel-Platzhalter bleiben erhalten → geschuetzter Chip im Editor.
                        const resolvedContent = replacePlaceholders(htmlContent, true, true);
                        addBlock('TEXT', {
                            content: resolvedContent,
                            fett: extractBoldFromHtml(resolvedContent) || extractBoldFromHtml(htmlContent),
                        });
                        setShowTextbausteinPicker(false);
                        toast.success(`Textbaustein „${tb.name}“ eingefügt`);
                    }}
                    onClose={() => {
                        pendingAnchorRef.current = DEFAULT_ANCHOR;
                        setShowTextbausteinPicker(false);
                    }}
                />
            )}
            {showLeistungPicker && (
                <LeistungPickerModal
                    leistungen={leistungen}
                    onSelect={(l) => {
                        const descHtml = l.description || '';
                        const hasKategorie = l.folderId != null && projektId != null;
                        addBlock('SERVICE', {
                            title: l.name,
                            description: descHtml,
                            quantity: 1,
                            unit: anzeigeEinheitDerLeistung(l),
                            price: l.price,
                            fett: extractBoldFromHtml(descHtml),
                            leistungId: l.id,
                            kategorieId: l.folderId ?? undefined,
                        }, 'Leistung eingefügt');
                        setShowLeistungPicker(false);
                        if (!hasKategorie) {
                            toast.success(`Leistung „${l.name}“ eingefügt`);
                        }
                    }}
                    onClose={() => {
                        pendingAnchorRef.current = DEFAULT_ANCHOR;
                        setShowLeistungPicker(false);
                    }}
                />
            )}
            {neueLeistungDialogOffen && neueLeistungPosition && neueLeistungUrsprung && (
                <LeistungNeuSpeichernDialog
                    block={neueLeistungPosition.block}
                    ursprungsLeistung={neueLeistungUrsprung}
                    onGespeichert={alsNeueLeistungGespeichert}
                    onClose={() => setNeueLeistungBlockId(null)}
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
                            fett: extractBoldFromHtml(descHtml),
                        }, 'Stundensatz eingefügt');
                        setShowStundensatzPicker(false);
                        toast.success(`Stundensatz „${az.bezeichnung}“ eingefügt`);
                    }}
                    onClose={() => {
                        pendingAnchorRef.current = DEFAULT_ANCHOR;
                        setShowStundensatzPicker(false);
                    }}
                />
            )}
            {showRabattDialog && (
                <RabattDialog
                    blocks={blocks}
                    globalRabatt={globalRabatt}
                    onApply={(positionDiscounts, newGlobalRabatt) => {
                        // Positionsrabatte + Pauschalrabatt sind EIN Schritt (Wortliste).
                        // Die Change-Detection erkennt globalRabatt jetzt selbst
                        // (baueDokumentSignatur) -- kein setHasUnsavedChanges(true) mehr noetig.
                        verlauf.aendern({
                            bezeichnung: 'Rabatt geändert',
                            berechne: stand => ({
                                blocks: stand.blocks.map(b => (b.type === 'SERVICE' && b.id in positionDiscounts)
                                    ? { ...b, discount: positionDiscounts[b.id] }
                                    : b),
                                globalRabatt: newGlobalRabatt,
                            }),
                            buendelSchluessel: null,
                            ziel: null,
                        });
                        setShowRabattDialog(false);
                    }}
                    onClose={() => setShowRabattDialog(false)}
                />
            )}
            {alternativDialogAnker && (
                <AlternativGruppeDialog
                    blocks={blocks}
                    ankerId={alternativDialogAnker}
                    onSpeichern={alternativGruppeSpeichern}
                    onAufloesen={gruppeAufloesen}
                    onClose={() => setAlternativDialogAnker(null)}
                />
            )}

            {/* Kategorie-Bestätigung beim Leistungseinfügen */}
            {pendingLeistungInsert && (
                <KategorieBestaetigenDialog
                    leistungName={pendingLeistungInsert.leistungName}
                    vorgeschlageneKategorieId={pendingLeistungInsert.kategorieId}
                    vorgeschlageneKategoriePfad={pendingLeistungInsert.kategoriePfad}
                    onBestaetigen={handleKategorieBestaetigt}
                    onUeberspringen={handleKategorieUeberspringen}
                />
            )}

            {/* Material-Auswahl aus den Artikel-Stammdaten. Unconditional gemountet
                (siehe materialDialogOffen weiter oben). */}
            <ArtikelAuswahlDialog
                offen={materialDialogOffen}
                onSchliessen={() => setMaterialDialogOffen(false)}
                onUebernehmen={uebernehmeMaterial}
            />

            {/* Warenkorb aus dem Lieferanten-Shop (IDS). Unconditional gemountet wie das Materialfenster. */}
            <ShopWarenkorbDialog
                offen={shopDialogOffen && !isLocked}
                lieferanten={idsLieferanten}
                onSchliessen={() => setShopDialogOffen(false)}
                onEinfuegen={uebernehmeShopArtikel}
            />
        </div>
    );
});

export default DocumentEditor;
