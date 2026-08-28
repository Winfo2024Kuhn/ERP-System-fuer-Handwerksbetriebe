import { useCallback, useEffect, useState } from 'react';
import {
    AlertTriangle, Eye, FileText, Globe, Image as ImageIcon, Loader2,
    Pencil, Plus, Star, Trash2, X,
} from 'lucide-react';
import { Button } from '../ui/button';
import { useConfirm } from '../ui/confirm-dialog';
import { useToast } from '../ui/toast';
import { ProjektSearchModal } from '../ProjektSearchModal';
import { BeitragVorschau } from './BeitragVorschau';
import { BeitragRichtextEditor } from './BeitragRichtextEditor';
import { leiteKurzbeschreibungAb } from './textumwandlung';
import { SchrittBilder, type GewaehltesBild } from './schritte/SchrittBilder';
import { rendereBlob } from './bildRendern';
import { MAX_BREITE_UPLOAD } from './bildbearbeitung';
import {
    WebsiteApiFehler,
    aktualisiereBeitrag,
    ladeBeitrag,
    ladeBeitraege,
    ladeBildHoch,
    loescheBild,
    setzeAltText,
    setzeStatus,
    setzeTitelbild,
} from './api';
import type { BeitragDetail, BeitragSummary } from './typen';

export interface BeitraegeTabProps {
    /**
     * Task 16 haengt hier den Assistenten ein. Absichtlich weiterhin
     * optional: WebsiteEditor uebergibt die echte Funktion, andere
     * Einbettungen (und Tests) duerfen den Knopf ungenutzt lassen -- dann
     * bleibt er sichtbar gesperrt, siehe neuerBeitragGesperrt unten.
     */
    onNeuerBeitrag?: () => void;
    /** Hochzaehlen laesst die Liste neu laden, z.B. nach dem Assistenten. */
    neuLadenSignal?: number;
}

/**
 * Nur die Felder, die der Bild-Dialog unten braucht. Ein Beitrag speichert
 * selbst keinen Projektbezug (BeitragDetailDto hat kein Projektfeld), darum
 * fragt der Dialog bei jedem Bild erneut ueber ProjektSearchModal danach.
 */
interface Projekt {
    id: number;
    bauvorhaben: string;
}

/**
 * Linke Spalte: alle Beitraege der Website. Rechte Spalte: der gewaehlte
 * Beitrag zum Bearbeiten, umschaltbar auf die Vorschau.
 *
 * Alle Daten kommen ueber ./api vom ERP-Backend, das seinerseits die
 * Website anspricht. Faellt die Website aus, kommt hier ein HTTP 502 an.
 */
export function BeitraegeTab({ onNeuerBeitrag, neuLadenSignal = 0 }: BeitraegeTabProps) {
    const confirm = useConfirm();
    const toast = useToast();
    const [liste, setListe] = useState<BeitragSummary[]>([]);
    const [gewaehlt, setGewaehlt] = useState<BeitragDetail | null>(null);
    const [titel, setTitel] = useState('');
    const [kurzbeschreibung, setKurzbeschreibung] = useState('');
    const [text, setText] = useState('');
    const [ansicht, setAnsicht] = useState<'bearbeiten' | 'vorschau'>('bearbeiten');
    const [laedtListe, setLaedtListe] = useState(true);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);

    // Bild-hinzufuegen-Dialog: erst Projekt suchen (ein Beitrag speichert
    // keinen Projektbezug), dann Bilder aus dessen Bautagebuch und
    // Projektdokumenten auswaehlen und bearbeiten, wie im Assistenten.
    const [bildSchritt, setBildSchritt] = useState<'projekt' | 'bilder' | null>(null);
    const [bildProjekt, setBildProjekt] = useState<Projekt | null>(null);
    const [bildAuswahl, setBildAuswahl] = useState<GewaehltesBild[]>([]);
    const [bilderWerdenHochgeladen, setBilderWerdenHochgeladen] = useState(false);
    const [bildFortschritt, setBildFortschritt] = useState<string | null>(null);

    const ladeListe = useCallback(async () => {
        setLaedtListe(true);
        setFehler(null);
        try {
            setListe(await ladeBeitraege());
        } catch (e) {
            setFehler(fehlertext(e));
        } finally {
            setLaedtListe(false);
        }
    }, []);

    useEffect(() => { void ladeListe(); }, [ladeListe, neuLadenSignal]);

    /** Uebernimmt einen frisch geladenen oder zurueckgegebenen Beitrag in die Felder. */
    const uebernehmen = useCallback((beitrag: BeitragDetail) => {
        setGewaehlt(beitrag);
        setTitel(beitrag.title);
        setKurzbeschreibung(beitrag.excerpt);
        setText(beitrag.content);
    }, []);

    const oeffne = async (id: number) => {
        setFehler(null);
        try {
            uebernehmen(await ladeBeitrag(id));
            setAnsicht('bearbeiten');
        } catch (e) {
            setFehler(fehlertext(e));
        }
    };

    const speichern = async () => {
        if (!gewaehlt) return;
        if (!titel.trim() || !kurzbeschreibung.trim() || !text.trim()) {
            toast.error('Titel, Kurzbeschreibung und Text dürfen nicht leer sein.');
            return;
        }
        setSpeichert(true);
        try {
            const neu = await aktualisiereBeitrag(gewaehlt.id, {
                title: titel.trim(),
                excerpt: kurzbeschreibung.trim(),
                content: text,
            });
            uebernehmen(neu);
            await ladeListe();
            toast.success('Beitrag gespeichert.');
        } catch (e) {
            toast.error(fehlertext(e));
        } finally {
            setSpeichert(false);
        }
    };

    const statusUmschalten = async () => {
        if (!gewaehlt) return;
        const veroeffentlichen = gewaehlt.status === 'draft';
        const bestaetigt = await confirm({
            title: veroeffentlichen ? 'Beitrag veröffentlichen' : 'Beitrag zurückziehen',
            message: veroeffentlichen
                ? 'Diesen Beitrag wirklich veröffentlichen? Er ist danach für alle auf der Website sichtbar.'
                : 'Diesen Beitrag von der Website zurückziehen? Er bleibt als Entwurf erhalten.',
            confirmLabel: veroeffentlichen ? 'Veröffentlichen' : 'Zurückziehen',
        });
        if (!bestaetigt) return;
        try {
            uebernehmen(await setzeStatus(gewaehlt.id, veroeffentlichen ? 'published' : 'draft'));
            await ladeListe();
        } catch (e) {
            toast.error(fehlertext(e));
        }
    };

    const bildLoeschen = async (bildId: number) => {
        if (!gewaehlt) return;
        const bestaetigt = await confirm({
            title: 'Bild löschen',
            message: 'Dieses Bild vom Beitrag entfernen?',
            variant: 'danger',
            confirmLabel: 'Löschen',
        });
        if (!bestaetigt) return;
        try {
            uebernehmen(await loescheBild(gewaehlt.id, bildId));
        } catch (e) {
            toast.error(fehlertext(e));
        }
    };

    const titelbildSetzen = async (bildId: number) => {
        if (!gewaehlt) return;
        try {
            uebernehmen(await setzeTitelbild(gewaehlt.id, bildId));
        } catch (e) {
            toast.error(fehlertext(e));
        }
    };

    const altTextSpeichern = async (bildId: number, wert: string) => {
        if (!gewaehlt) return;
        if (!wert.trim()) {
            toast.error('Die Bildbeschreibung darf nicht leer sein.');
            return;
        }
        try {
            uebernehmen(await setzeAltText(gewaehlt.id, bildId, wert.trim()));
        } catch (e) {
            toast.error(fehlertext(e));
        }
    };

    const bildDialogSchliessen = () => {
        setBildSchritt(null);
        setBildProjekt(null);
        setBildAuswahl([]);
        setBildFortschritt(null);
    };

    /**
     * Laedt die ausgewaehlten Bilder nacheinander hoch, genau wie der
     * Assistent (BeitragAssistent.speichern): die Website rechnet jedes Bild
     * einzeln um, parallel liefe hier also nichts schneller.
     *
     * Bricht die Uebertragung mittendrin ab, bleiben die schon hochgeladenen
     * Bilder im Beitrag -- die Ansicht wird sofort aktualisiert --, und nur
     * die restlichen bleiben im Dialog ausgewaehlt, damit ein zweiter Versuch
     * sie nicht doppelt hochlaedt.
     */
    const bilderHochladen = async () => {
        if (!gewaehlt || bildAuswahl.length === 0) return;
        setBilderWerdenHochgeladen(true);
        let letzterStand = gewaehlt;
        let uebertragen = 0;
        try {
            for (let i = 0; i < bildAuswahl.length; i++) {
                setBildFortschritt(`Bild ${i + 1} von ${bildAuswahl.length} wird hochgeladen...`);
                const eintrag = bildAuswahl[i];
                const blob = await rendereBlob(eintrag.bild.url, eintrag.bearbeitung, MAX_BREITE_UPLOAD);
                letzterStand = await ladeBildHoch(gewaehlt.id, blob, eintrag.bild.originalDateiname);
                uebertragen += 1;
            }
            uebernehmen(letzterStand);
            toast.success(uebertragen === 1 ? 'Bild hinzugefügt.' : `${uebertragen} Bilder hinzugefügt.`);
            bildDialogSchliessen();
        } catch (e) {
            if (uebertragen > 0) {
                uebernehmen(letzterStand);
                setBildAuswahl(vorher => vorher.slice(uebertragen));
                toast.error(
                    `${uebertragen} von ${bildAuswahl.length} Bildern `
                    + `${uebertragen === 1 ? 'wurde' : 'wurden'} hinzugefügt, `
                    + `danach brach die Übertragung ab. ${fehlertext(e)}`);
            } else {
                toast.error(fehlertext(e));
            }
        } finally {
            setBilderWerdenHochgeladen(false);
            setBildFortschritt(null);
        }
    };

    // Der Assistent hinter dem Knopf kommt erst in einer späteren Aufgabe.
    // Bis dahin lieber sichtbar sperren statt eines Knopfs, der ins Leere geht.
    const neuerBeitragGesperrt = !onNeuerBeitrag;

    if (fehler && liste.length === 0) {
        return (
            <div className="flex flex-col items-start gap-3 p-4 bg-rose-50 border border-rose-200 rounded-lg text-rose-800">
                <div className="flex items-start gap-3">
                    <AlertTriangle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                    <p>{fehler}</p>
                </div>
                <Button size="sm" onClick={() => void ladeListe()}
                    className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700">
                    Erneut versuchen
                </Button>
            </div>
        );
    }

    return (
        <div className="grid grid-cols-1 lg:grid-cols-[320px_1fr] gap-6">
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden flex flex-col">
                <div className="p-3 border-b border-slate-200">
                    <Button
                        size="sm"
                        onClick={onNeuerBeitrag}
                        disabled={neuerBeitragGesperrt}
                        title={neuerBeitragGesperrt ? 'Diese Funktion ist noch nicht fertig.' : undefined}
                        className={`w-full ${neuerBeitragGesperrt
                            ? 'bg-slate-100 text-slate-400 border border-slate-200'
                            : 'bg-rose-600 text-white border border-rose-600 hover:bg-rose-700'}`}
                    >
                        <Plus className="w-4 h-4" />
                        Neuer Beitrag
                    </Button>
                </div>
                <div className="flex-1 overflow-y-auto divide-y divide-slate-100 max-h-[70vh]">
                    {laedtListe ? (
                        <div className="p-8 text-center text-slate-400">
                            <Loader2 className="w-6 h-6 mx-auto animate-spin" />
                        </div>
                    ) : liste.length === 0 ? (
                        <div className="p-8 text-center text-slate-400">
                            <FileText className="w-10 h-10 mx-auto mb-2 opacity-30" />
                            <p>Noch kein Beitrag angelegt.</p>
                        </div>
                    ) : liste.map(beitrag => (
                        <button
                            key={beitrag.id}
                            onClick={() => void oeffne(beitrag.id)}
                            className={`w-full flex items-start gap-3 p-3 text-left transition-colors
                                ${gewaehlt?.id === beitrag.id ? 'bg-rose-50' : 'hover:bg-slate-50'}`}
                        >
                            <div className="w-12 h-12 rounded-lg bg-slate-100 flex items-center justify-center flex-shrink-0 overflow-hidden">
                                {beitrag.coverImagePath
                                    ? <ImageIcon className="w-5 h-5 text-slate-400" />
                                    : <FileText className="w-5 h-5 text-slate-400" />}
                            </div>
                            <div className="min-w-0 flex-1">
                                <p className="font-medium text-slate-900 truncate">{beitrag.title}</p>
                                <div className="flex items-center gap-2 mt-1">
                                    <StatusChip status={beitrag.status} />
                                    {beitrag.publishedAt && (
                                        <span className="text-xs text-slate-400">
                                            {beitrag.publishedAt.slice(0, 10)}
                                        </span>
                                    )}
                                </div>
                            </div>
                        </button>
                    ))}
                </div>
            </div>

            {!gewaehlt ? (
                <div className="bg-white border border-slate-200 rounded-xl flex items-center justify-center text-slate-400 min-h-[400px]">
                    <div className="text-center">
                        <Globe className="w-10 h-10 mx-auto mb-2 opacity-30" />
                        <p>Links einen Beitrag wählen oder einen neuen anlegen.</p>
                    </div>
                </div>
            ) : (
                <div className="bg-white border border-slate-200 rounded-xl p-5 space-y-4">
                    <div className="flex items-center justify-between gap-2 flex-wrap">
                        <div className="flex gap-1">
                            <Button
                                size="sm"
                                onClick={() => setAnsicht('bearbeiten')}
                                className={ansicht === 'bearbeiten'
                                    ? 'bg-rose-600 text-white border border-rose-600 hover:bg-rose-700'
                                    : 'border border-rose-300 text-rose-700 hover:bg-rose-50'}
                            >
                                <Pencil className="w-4 h-4" />
                                Bearbeiten
                            </Button>
                            <Button
                                size="sm"
                                onClick={() => setAnsicht('vorschau')}
                                className={ansicht === 'vorschau'
                                    ? 'bg-rose-600 text-white border border-rose-600 hover:bg-rose-700'
                                    : 'border border-rose-300 text-rose-700 hover:bg-rose-50'}
                            >
                                <Eye className="w-4 h-4" />
                                Vorschau
                            </Button>
                        </div>
                        <div className="flex gap-2">
                            <Button
                                size="sm"
                                onClick={() => void statusUmschalten()}
                                className="border border-rose-300 text-rose-700 hover:bg-rose-50"
                            >
                                {gewaehlt.status === 'draft' ? 'Veröffentlichen' : 'Zurückziehen'}
                            </Button>
                            <Button
                                size="sm"
                                disabled={speichert}
                                onClick={() => void speichern()}
                                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            >
                                {speichert && <Loader2 className="w-4 h-4 animate-spin" />}
                                Speichern
                            </Button>
                        </div>
                    </div>

                    {ansicht === 'vorschau' ? (
                        <div className="max-h-[70vh] overflow-y-auto">
                            <BeitragVorschau
                                titel={titel}
                                textHtml={text}
                                bildUrls={gewaehlt.images.map(b => ({
                                    url: bildAdresse(b.path),
                                    altText: b.altText ?? '',
                                }))}
                                veroeffentlichtAm={gewaehlt.publishedAt}
                            />
                        </div>
                    ) : (
                        <>
                            <div>
                                <label htmlFor="beitrag-titel" className="block text-sm font-medium text-slate-700 mb-1">
                                    Titel
                                </label>
                                <input
                                    id="beitrag-titel"
                                    type="text"
                                    value={titel}
                                    onChange={e => setTitel(e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                                />
                                <p className="text-xs text-slate-400 mt-1">
                                    Die Adresse des Beitrags entsteht aus dem ersten Titel und ändert sich später nicht mehr.
                                </p>
                            </div>

                            <div>
                                <div className="flex items-center justify-between mb-1">
                                    <label htmlFor="beitrag-kurz" className="block text-sm font-medium text-slate-700">
                                        Kurzbeschreibung
                                    </label>
                                    <button
                                        type="button"
                                        onClick={() => setKurzbeschreibung(leiteKurzbeschreibungAb(text))}
                                        className="text-sm text-rose-700 hover:underline"
                                    >
                                        Aus dem Text übernehmen
                                    </button>
                                </div>
                                <textarea
                                    id="beitrag-kurz"
                                    rows={2}
                                    value={kurzbeschreibung}
                                    onChange={e => setKurzbeschreibung(e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                                />
                                <p className="text-xs text-slate-400 mt-1">
                                    Das ist der kurze Text, der auf der Übersichtsseite unter dem Titel steht. {kurzbeschreibung.length} Zeichen.
                                </p>
                            </div>

                            <div>
                                <span className="block text-sm font-medium text-slate-700 mb-1">Text</span>
                                <BeitragRichtextEditor html={text} onChange={setText} />
                            </div>

                            <div>
                                <div className="flex items-center justify-between gap-2 mb-2">
                                    <span className="block text-sm font-medium text-slate-700">
                                        Bilder ({gewaehlt.images.length})
                                    </span>
                                    <Button
                                        size="sm"
                                        onClick={() => setBildSchritt('projekt')}
                                        className="border border-rose-300 text-rose-700 hover:bg-rose-50"
                                    >
                                        <Plus className="w-4 h-4" />
                                        Bild hinzufügen
                                    </Button>
                                </div>
                                {gewaehlt.images.length === 0 ? (
                                    <p className="text-sm text-slate-400">Dieser Beitrag hat noch keine Bilder.</p>
                                ) : (
                                    <div className="grid grid-cols-2 xl:grid-cols-3 gap-3">
                                        {gewaehlt.images.map((bild, index) => (
                                            <div key={bild.id} className="border border-slate-200 rounded-lg overflow-hidden">
                                                <img
                                                    src={bildAdresse(bild.path)}
                                                    alt={bild.altText ?? ''}
                                                    className="w-full h-28 object-cover"
                                                />
                                                <div className="p-2 space-y-2">
                                                    <input
                                                        type="text"
                                                        aria-label={`Bildbeschreibung für Bild ${index + 1} von ${gewaehlt.images.length}`}
                                                        defaultValue={bild.altText ?? ''}
                                                        placeholder="Was ist zu sehen?"
                                                        onBlur={e => void altTextSpeichern(bild.id, e.target.value)}
                                                        className="w-full px-2 py-1 text-sm border border-slate-200 rounded"
                                                    />
                                                    <div className="flex gap-1">
                                                        <button
                                                            type="button"
                                                            aria-label={bild.isCover ? 'Titelbild' : 'Als Titelbild setzen'}
                                                            title={bild.isCover ? 'Titelbild' : 'Als Titelbild setzen'}
                                                            disabled={bild.isCover}
                                                            onClick={() => void titelbildSetzen(bild.id)}
                                                            className={`flex-1 flex items-center justify-center gap-1 py-1 rounded text-xs
                                                                ${bild.isCover
                                                                    ? 'bg-rose-100 text-rose-700'
                                                                    : 'text-slate-600 hover:bg-slate-100'}`}
                                                        >
                                                            <Star className="w-3.5 h-3.5" />
                                                            Titelbild
                                                        </button>
                                                        <button
                                                            type="button"
                                                            aria-label={`Bild ${index + 1} von ${gewaehlt.images.length} löschen`}
                                                            onClick={() => void bildLoeschen(bild.id)}
                                                            className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded"
                                                        >
                                                            <Trash2 className="w-3.5 h-3.5" />
                                                        </button>
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </>
                    )}
                </div>
            )}

            <ProjektSearchModal
                isOpen={bildSchritt === 'projekt'}
                onClose={bildDialogSchliessen}
                onSelect={projekt => {
                    setBildProjekt(projekt as Projekt);
                    setBildAuswahl([]);
                    setBildSchritt('bilder');
                }}
            />

            {bildSchritt === 'bilder' && bildProjekt && (
                <div className="fixed inset-0 bg-black/60 z-[65] flex items-center justify-center p-4">
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden">
                        <div className="flex items-center justify-between p-4 border-b border-slate-200">
                            <div>
                                <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Bild hinzufügen</p>
                                <h2 className="text-lg font-bold text-slate-900">{bildProjekt.bauvorhaben}</h2>
                            </div>
                            <button
                                type="button"
                                onClick={bildDialogSchliessen}
                                disabled={bilderWerdenHochgeladen}
                                aria-label="Bildauswahl schließen"
                                title="Schließen"
                                className="p-1.5 hover:bg-slate-100 rounded-full"
                            >
                                <X className="w-5 h-5 text-slate-500" />
                            </button>
                        </div>

                        <div className="flex-1 overflow-y-auto p-4">
                            <SchrittBilder
                                projektId={bildProjekt.id}
                                auswahl={bildAuswahl}
                                onAuswahlAendern={setBildAuswahl}
                            />
                        </div>

                        <div className="flex items-center justify-between gap-3 p-4 border-t border-slate-200">
                            <Button
                                size="sm"
                                disabled={bilderWerdenHochgeladen}
                                onClick={bildDialogSchliessen}
                                className="border border-slate-300 text-slate-600 hover:bg-slate-100"
                            >
                                Abbrechen
                            </Button>

                            {bildFortschritt && (
                                <span className="flex items-center gap-2 text-sm text-slate-500">
                                    <Loader2 className="w-4 h-4 animate-spin" />
                                    {bildFortschritt}
                                </span>
                            )}

                            <Button
                                size="sm"
                                disabled={bildAuswahl.length === 0 || bilderWerdenHochgeladen}
                                onClick={() => void bilderHochladen()}
                                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            >
                                {bilderWerdenHochgeladen && <Loader2 className="w-4 h-4 animate-spin" />}
                                Hinzufügen{bildAuswahl.length > 0 ? ` (${bildAuswahl.length})` : ''}
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

function StatusChip({ status }: { status: 'draft' | 'published' }) {
    return status === 'published' ? (
        <span className="text-xs bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded">Veröffentlicht</span>
    ) : (
        <span className="text-xs bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded">Entwurf</span>
    );
}

/**
 * `BeitragBildDto.path` enthaelt nur den Dateinamen. Die Datei selbst liegt
 * auf der Website unter /uploads/aktuelles/<name>/ und ist vom Browser im
 * Buero nicht zwingend erreichbar. Deshalb geht die Anzeige ueber die
 * Durchreiche des ERP aus Task 9.
 */
function bildAdresse(pfad: string): string {
    return `/api/beitraege/bild/${encodeURIComponent(pfad)}`;
}

function fehlertext(e: unknown): string {
    if (e instanceof WebsiteApiFehler) {
        if (e.status === 502 || e.status === 0) {
            return 'Die Website ist gerade nicht erreichbar. Bitte später noch einmal versuchen.';
        }
        return e.message;
    }
    return e instanceof Error ? e.message : 'Unbekannter Fehler.';
}
