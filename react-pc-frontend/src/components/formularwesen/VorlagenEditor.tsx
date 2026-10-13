import { useCallback, useMemo, useRef, useState } from 'react';
import type { FormBlock } from '../../types';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';
import { cn } from '../../lib/utils';
import EditorToolbar from './EditorToolbar';
import BlocksSidebar from './BlocksSidebar';
import EditorCanvas from './EditorCanvas';
import PropertiesSidebar from './PropertiesSidebar';
import VorlagenEinstellungen from './VorlagenEinstellungen';
import PreviewModal from './PreviewModal';
import TextbausteinDefaultsModal from './TextbausteinDefaultsModal';
import VorlageSpeichernDialog from './VorlageSpeichernDialog';
import { NormSchalter } from './NormSchalter';
import { ZoomRegler } from './ZoomRegler';
import { uid, PAGE_FORMATS } from './constants';
import { useVerlauf } from './useVerlauf';
import { useEditorTastatur } from './useEditorTastatur';
import { useSeitenZoom } from './useSeitenZoom';
import { useVorschauDaten } from './useVorschauDaten';
import { leseVorlage, serialisiereVorlage, standardBloecke, type VorlagenStand } from './vorlagenFormat';
import { neuerBaustein, type BausteinArt } from './bausteinVorlagen';
import { anschriftInsFenster, findeAnschriftBlock, pruefeAnschrift, STANDARD_BRIEFFORM, type Briefform } from './normMasse';

/** Was der Editor zum Öffnen braucht. `html: null` = neue Vorlage mit Standard-Layout. */
export interface OffeneVorlage {
    name: string | null;
    html: string | null;
    dokumenttypen: string[];
}

interface VorlagenEditorProps {
    vorlage: OffeneVorlage;
    onZurueck: () => void;
}

const NORM_SPEICHER = 'formularwesen.normHilfslinien';
const leseNormSchalter = () => {
    try { return window.localStorage.getItem(NORM_SPEICHER) !== 'aus'; } catch { return true; }
};

function startStand(vorlage: OffeneVorlage): VorlagenStand {
    if (vorlage.html === null) return { items: standardBloecke(), backgroundImage: null, backgroundImagePage2: null, briefform: null };
    return leseVorlage(vorlage.html);
}

/** Fingerabdruck des gespeicherten Stands – daran erkennt der Editor ungespeicherte Änderungen. */
const fingerabdruck = (stand: VorlagenStand, dokumenttypen: string[]) =>
    `${serialisiereVorlage(stand)}|${[...dokumenttypen].sort().join(',')}`;

/**
 * Der Vorlagen-Editor: Werkzeugleiste oben, links Bausteine und Ebenen, in der
 * Mitte die Seite, rechts die Eigenschaften. Hält den ganzen Bearbeitungsstand
 * samt Rückgängig, Speichern und Vorschau.
 */
export default function VorlagenEditor({ vorlage, onZurueck }: VorlagenEditorProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const [start] = useState(() => startStand(vorlage));

    const verlauf = useVerlauf<FormBlock[]>(start.items);
    const items = verlauf.stand;
    const [name, setName] = useState(vorlage.name);
    const [dokumenttypen, setDokumenttypen] = useState(vorlage.dokumenttypen);
    const [hintergrund1, setHintergrund1] = useState(start.backgroundImage);
    const [hintergrund2, setHintergrund2] = useState(start.backgroundImagePage2);
    const [briefform, setBriefform] = useState<Briefform | null>(start.briefform);
    const [gespeichert, setGespeichert] = useState(() => (vorlage.html === null ? '' : fingerabdruck(start, vorlage.dokumenttypen)));

    const [seite, setSeite] = useState(1);
    const [ausgewaehltId, setAusgewaehltId] = useState<string | null>(null);
    const [normAn, setNormAnRoh] = useState(leseNormSchalter);
    const [vorschau, setVorschau] = useState(false);
    const [namensDialog, setNamensDialog] = useState(false);
    const [standardTexte, setStandardTexte] = useState(false);
    const [speichert, setSpeichert] = useState(false);

    const bereichRef = useRef<HTMLDivElement>(null);
    const zoom = useSeitenZoom(bereichRef, { breite: PAGE_FORMATS.A4.width, hoehe: PAGE_FORMATS.A4.height });
    const daten = useVorschauDaten();

    const form = briefform ?? STANDARD_BRIEFFORM;
    const stand: VorlagenStand = useMemo(
        () => ({ items, backgroundImage: hintergrund1, backgroundImagePage2: hintergrund2, briefform }),
        [items, hintergrund1, hintergrund2, briefform],
    );
    const ungespeichert = useMemo(() => fingerabdruck(stand, dokumenttypen) !== gespeichert, [stand, dokumenttypen, gespeichert]);
    const anschriftStatus = useMemo(() => pruefeAnschrift(items, form), [items, form]);
    const ausgewaehlt = items.find(i => i.id === ausgewaehltId) ?? null;

    const setNormAn = (an: boolean) => {
        setNormAnRoh(an);
        try { window.localStorage.setItem(NORM_SPEICHER, an ? 'an' : 'aus'); } catch { /* ohne Speicher gilt der Schalter nur jetzt */ }
    };

    // ─── Bausteine ändern ────────────────────────────────────────────────

    const verlaufAendern = verlauf.aendern;
    const aendern = useCallback((id: string, updates: Partial<FormBlock>, gruppe?: string) => {
        verlaufAendern(prev => prev.map(i => (i.id === id ? { ...i, ...updates } : i)), gruppe);
    }, [verlaufAendern]);

    const hinzufuegen = (art: BausteinArt, mitte?: { x: number; y: number }) => {
        const block = neuerBaustein(art, { seite, items, form, mitte });
        verlauf.aendern(prev => [...prev, block]);
        setAusgewaehltId(block.id);
    };

    const loeschen = (id: string) => {
        verlauf.aendern(prev => prev.filter(i => i.id !== id));
        setAusgewaehltId(null);
    };

    const duplizieren = () => {
        if (!ausgewaehlt) return;
        const kopie: FormBlock = {
            ...ausgewaehlt,
            id: uid(),
            x: ausgewaehlt.x + 12,
            y: ausgewaehlt.y + 12,
            z: Math.max(0, ...items.map(i => i.z)) + 1,
        };
        verlauf.aendern(prev => [...prev, kopie]);
        setAusgewaehltId(kopie.id);
    };

    const ebene = (id: string, richtung: 'vorne' | 'hinten') => {
        verlauf.aendern(prev => {
            const zWerte = prev.filter(i => i.page === seite).map(i => i.z);
            const z = richtung === 'vorne' ? Math.max(...zWerte) + 1 : Math.min(...zWerte) - 1;
            return prev.map(i => (i.id === id ? { ...i, z } : i));
        });
    };

    const anschriftSetzen = () => {
        const block = findeAnschriftBlock(items);
        if (!block) return;
        // Kein Toast: der Hinweis springt sichtbar auf „Anschrift sitzt im Fenster“.
        aendern(block.id, anschriftInsFenster(form));
    };

    const seiteWechseln = (neu: number) => {
        setSeite(neu);
        setAusgewaehltId(null);
    };

    const auswaehlenAusListe = (id: string) => {
        const block = items.find(i => i.id === id);
        if (block && block.page !== seite) setSeite(block.page);
        setAusgewaehltId(id);
    };

    const rueckgaengig = () => { verlauf.rueckgaengig(); };
    const wiederholen = () => { verlauf.wiederholen(); };

    useEditorTastatur({
        hatAuswahl: ausgewaehlt !== null,
        verschieben: (dx, dy) => {
            if (!ausgewaehlt) return;
            aendern(ausgewaehlt.id, { x: ausgewaehlt.x + dx, y: ausgewaehlt.y + dy }, `pfeil-${ausgewaehlt.id}`);
        },
        loeschen: () => { if (ausgewaehlt) loeschen(ausgewaehlt.id); },
        duplizieren,
        auswahlAufheben: () => setAusgewaehltId(null),
        rueckgaengig,
        wiederholen,
    });

    const layoutZuruecksetzen = async () => {
        const ok = await confirm({
            title: 'Auf Standard-Layout zurücksetzen?',
            message: 'Alle Bausteine dieser Vorlage werden durch das Standard-Layout ersetzt. Das Briefpapier bleibt. Mit Rückgängig holen Sie den alten Stand zurück.',
            confirmLabel: 'Zurücksetzen',
            variant: 'warning',
        });
        if (!ok) return;
        verlauf.aendern(standardBloecke());
        setAusgewaehltId(null);
        setSeite(1);
    };

    // ─── Speichern, Umbenennen, Zurück ──────────────────────────────────

    const speichernUnter = async (zielName: string) => {
        setSpeichert(true);
        try {
            const res = await fetch('/api/formulare/templates', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: zielName, html: serialisiereVorlage(stand), assignedDokumenttypen: dokumenttypen }),
            });
            if (!res.ok) throw new Error();
            const antwort = await res.json().catch(() => ({}));
            const endName = (antwort as { name?: string }).name || zielName;
            setName(endName);
            setGespeichert(fingerabdruck(stand, dokumenttypen));
            setNamensDialog(false);
            toast.success(`Vorlage „${endName}“ gespeichert.`);
        } catch {
            toast.error('Vorlage konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const speichern = () => {
        if (name) void speichernUnter(name);
        else setNamensDialog(true);
    };

    const umbenennen = async (neuerName: string) => {
        // Noch nie gespeichert: Name gilt ab dem ersten Speichern.
        if (!name || gespeichert === '') {
            setName(neuerName);
            return;
        }
        try {
            const res = await fetch(`/api/formulare/templates/${encodeURIComponent(name)}/rename`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ newName: neuerName }),
            });
            if (!res.ok) throw new Error();
            setName(neuerName);
            toast.success(`Vorlage heißt jetzt „${neuerName}“.`);
        } catch {
            toast.error('Vorlage konnte nicht umbenannt werden.');
        }
    };

    const zurueck = async () => {
        if (ungespeichert) {
            const ok = await confirm({
                title: 'Änderungen verwerfen?',
                message: 'Die Vorlage hat ungespeicherte Änderungen. Wenn Sie jetzt zurückgehen, sind sie weg.',
                confirmLabel: 'Verwerfen',
                cancelLabel: 'Weiter bearbeiten',
                variant: 'danger',
            });
            if (!ok) return;
        }
        onZurueck();
    };

    // ─── Ansicht ─────────────────────────────────────────────────────────

    return (
        <div className="flex h-full min-h-[560px] flex-col gap-3" data-testid="vorlagen-editor">
            <EditorToolbar
                vorlagenName={name}
                ungespeichert={ungespeichert}
                speichert={speichert}
                onZurueck={zurueck}
                onUmbenennen={umbenennen}
                onRueckgaengig={rueckgaengig}
                onWiederholen={wiederholen}
                kannRueckgaengig={verlauf.kannRueckgaengig}
                kannWiederholen={verlauf.kannWiederholen}
                onVorschau={() => setVorschau(true)}
                onSpeichern={speichern}
                onStandardTexte={() => setStandardTexte(true)}
            />

            <div className="flex min-h-0 flex-1 gap-3">
                <div className="w-60 shrink-0">
                    <BlocksSidebar items={items} seite={seite} ausgewaehltId={ausgewaehltId} onHinzufuegen={art => hinzufuegen(art)} onAuswaehlen={auswaehlenAusListe} />
                </div>

                <section aria-label="Seite gestalten" className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                    <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b border-slate-200 px-3 py-2">
                        <div role="tablist" aria-label="Seite" className="inline-flex rounded-lg bg-slate-100 p-0.5">
                            {[1, 2].map(s => (
                                <button
                                    key={s}
                                    type="button"
                                    role="tab"
                                    aria-selected={seite === s}
                                    onClick={() => seiteWechseln(s)}
                                    className={cn(
                                        'rounded-md px-3 py-1.5 text-xs font-semibold transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                                        seite === s ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600 hover:text-slate-900',
                                    )}
                                >
                                    {s === 1 ? 'Seite 1' : 'Folgeseiten'}
                                </button>
                            ))}
                        </div>
                        <NormSchalter an={normAn} onAn={setNormAn} form={form} onForm={setBriefform} />
                        <ZoomRegler {...zoom} />
                    </div>

                    <EditorCanvas
                        items={items}
                        seite={seite}
                        ausgewaehltId={ausgewaehltId}
                        zoom={zoom.zoom}
                        bereichRef={bereichRef}
                        briefpapier={seite === 1 ? hintergrund1 : hintergrund2}
                        daten={daten}
                        normAn={normAn}
                        normForm={form}
                        anschriftStatus={anschriftStatus}
                        onAuswaehlen={setAusgewaehltId}
                        onAendern={aendern}
                        onLoeschen={loeschen}
                        onDuplizieren={duplizieren}
                        onAblegen={hinzufuegen}
                        onAnschriftInsFenster={anschriftSetzen}
                    />
                </section>

                <div className="w-72 shrink-0">
                    {ausgewaehlt ? (
                        <PropertiesSidebar
                            key={ausgewaehlt.id}
                            block={ausgewaehlt}
                            onAendern={aendern}
                            onLoeschen={loeschen}
                            onDuplizieren={duplizieren}
                            onEbene={ebene}
                            onSchliessen={() => setAusgewaehltId(null)}
                        />
                    ) : (
                        <VorlagenEinstellungen
                            briefpapier={{ seite1: hintergrund1, folgeseiten: hintergrund2 }}
                            onBriefpapier={(s, bild) => (s === 1 ? setHintergrund1(bild) : setHintergrund2(bild))}
                            dokumenttypen={dokumenttypen}
                            onDokumenttypen={setDokumenttypen}
                            onLayoutZuruecksetzen={layoutZuruecksetzen}
                        />
                    )}
                </div>
            </div>

            {vorschau && (
                <PreviewModal
                    items={items}
                    backgroundImage={hintergrund1}
                    backgroundImagePage2={hintergrund2}
                    liveData={daten}
                    form={form}
                    onForm={setBriefform}
                    anschriftStatus={anschriftStatus}
                    onAnschriftInsFenster={anschriftSetzen}
                    onClose={() => setVorschau(false)}
                />
            )}

            {namensDialog && <VorlageSpeichernDialog onSpeichern={n => void speichernUnter(n)} onAbbrechen={() => setNamensDialog(false)} />}

            {standardTexte && (
                <TextbausteinDefaultsModal
                    templateName={name}
                    assignedDokumenttypen={dokumenttypen}
                    onClose={() => setStandardTexte(false)}
                    onSaved={() => toast.success('Standard-Texte gespeichert.')}
                />
            )}
        </div>
    );
}
