import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { BookmarkPlus, FolderCheck, Loader2, TriangleAlert } from 'lucide-react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { useToast } from '../ui/toast';
import { htmlZuEinzeiler } from '../../lib/htmlSanitizer';
import { cn } from '../../lib/utils';
import type { ProduktkategorieDto } from '../../types';
import { KategorieTreeNode, type KategorieNode } from './Modals';
import { einheitZuLeistungsEinheit, formatCurrency } from './helpers';
import type { DocBlock, LeistungApiDto } from './types';

/** Gleiche Grenze wie im Backend (Spalte `bezeichnung`, 255 Zeichen). */
export const MAX_NAME_LAENGE = 255;
/** Schutz gegen kaputte parentId-Ketten (Zyklus, Altdaten). */
const MAX_PFAD_TIEFE = 10;
const ALLGEMEINER_FEHLER = 'Die Leistung konnte nicht gespeichert werden.';
const HINWEIS_UNTERSTE_EBENE = 'Bitte wählen Sie einen Ordner der untersten Ebene.';

export interface GewaehlterOrdner {
    id: number;
    /** Lesbarer Pfad, z. B. „Geländer › Edelstahl“. */
    pfad: string;
}

export interface LeistungNeuSpeichernDialogProps {
    /** Die geaenderte Leistungsposition aus dem Dokument. */
    block: DocBlock;
    /** Die gespeicherte Leistung, aus der die Position eingefuegt wurde. Bleibt unveraendert. */
    ursprungsLeistung: LeistungApiDto;
    onGespeichert: (neu: LeistungApiDto, ordner: GewaehlterOrdner) => void;
    onClose: () => void;
}

/**
 * Ordner, wie ihn der Dialog kennt. `ausBaum`: stammt aus dem Ordnerbaum
 * (haupt/unterkategorien) – nur dann ist `isLeaf` verlaesslich. Ordner aus der
 * Pfad-Ermittlung liefern nur Name und Elternteil.
 */
interface BekannterOrdner extends KategorieNode {
    ausBaum: boolean;
}

function zuKnoten(dto: ProduktkategorieDto, parentId: number | null): KategorieNode {
    return {
        id: Number(dto.id),
        bezeichnung: dto.bezeichnung || dto.pfad || 'Kategorie',
        isLeaf: dto.leaf ?? true,
        parentId,
    };
}

/**
 * Ermittelt den Ordner der Ursprungs-Leistung samt aller Vorfahren
 * (unterster Ordner zuerst). Bricht still ab bei Fehler, fehlendem Elternteil
 * oder zu tiefer Kette – dann wird eben weniger aufgeklappt.
 */
async function ladeOrdnerKette(startId: number): Promise<KategorieNode[]> {
    const kette: KategorieNode[] = [];
    const gesehen = new Set<number>();
    let id: number | null = startId;
    while (id != null && kette.length < MAX_PFAD_TIEFE && !gesehen.has(id)) {
        gesehen.add(id);
        try {
            // light=true: ohne die teure Projektzaehlung. `leaf` ist dort nicht
            // verlaesslich – das kommt weiter aus dem Baum (siehe BekannterOrdner).
            const res = await fetch(`/api/produktkategorien/${encodeURIComponent(String(id))}?light=true`);
            if (!res.ok) break;
            const dto = await res.json() as ProduktkategorieDto;
            const parentId = dto.parentId == null ? null : Number(dto.parentId);
            kette.push(zuKnoten(dto, parentId));
            id = parentId;
        } catch {
            break;
        }
    }
    return kette;
}

/** Liest `message` aus einer 400-Antwort; sonst die allgemeine Meldung. */
async function fehlermeldungAus(res: Response): Promise<string> {
    if (res.status !== 400) return ALLGEMEINER_FEHLER;
    try {
        const daten = await res.json() as { message?: unknown };
        return typeof daten?.message === 'string' && daten.message.trim() ? daten.message : ALLGEMEINER_FEHLER;
    } catch {
        return ALLGEMEINER_FEHLER;
    }
}

/**
 * „Als neue Leistung speichern“: legt aus einer geaenderten Position eine
 * zusaetzliche Leistung in einem Ordner der untersten Ebene an. Vorausgewaehlt
 * und aufgeklappt ist der Ordner der Ursprungs-Leistung.
 */
export function LeistungNeuSpeichernDialog({ block, ursprungsLeistung, onGespeichert, onClose }: LeistungNeuSpeichernDialogProps) {
    const toast = useToast();
    const titelId = useId();
    const nameId = useId();

    const [name, setName] = useState(block.title ?? '');
    const [wurzeln, setWurzeln] = useState<KategorieNode[]>([]);
    const [ladeWurzeln, setLadeWurzeln] = useState(true);
    const [bekannte, setBekannte] = useState<ReadonlyMap<number, BekannterOrdner>>(() => new Map());
    const [aufgeklappteIds, setAufgeklappteIds] = useState<ReadonlySet<number>>(() => new Set());
    const [vorgemerkteId, setVorgemerkteId] = useState<number | null>(null);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);
    // Doppelklick-Schutz unabhaengig vom naechsten Render.
    const laeuftRef = useRef(false);

    const merke = useCallback((knoten: KategorieNode[], ausBaum: boolean) => {
        if (knoten.length === 0) return;
        setBekannte(vorher => {
            const neu = new Map(vorher);
            for (const k of knoten) {
                const alt = neu.get(k.id);
                if (!alt) {
                    neu.set(k.id, { ...k, ausBaum });
                } else if (ausBaum || !alt.ausBaum) {
                    // Baum-Wissen gewinnt; ein bekannter Elternteil bleibt erhalten.
                    neu.set(k.id, { ...alt, ...k, parentId: k.parentId ?? alt.parentId, ausBaum: ausBaum || alt.ausBaum });
                } else {
                    // Pfad-Info nach Baum-Info: nur Elternteil ergaenzen, leaf nicht anfassen.
                    neu.set(k.id, { ...alt, parentId: alt.parentId ?? k.parentId });
                }
            }
            return neu;
        });
    }, []);
    const merkeKnoten = useCallback((knoten: KategorieNode[]) => merke(knoten, true), [merke]);

    // Wurzel-Ordner laden
    useEffect(() => {
        let abgebrochen = false;
        fetch('/api/produktkategorien/haupt?light=true')
            .then(r => r.ok ? r.json() : [])
            .then((daten: ProduktkategorieDto[]) => {
                if (abgebrochen) return;
                const knoten = (Array.isArray(daten) ? daten : []).map(d => zuKnoten(d, null));
                setWurzeln(knoten);
                merkeKnoten(knoten);
            })
            .catch(() => { /* leerer Baum zeigt den Hinweis */ })
            .finally(() => { if (!abgebrochen) setLadeWurzeln(false); });
        return () => { abgebrochen = true; };
    }, [merkeKnoten]);

    // Ordner der Ursprungs-Leistung: Pfad aufklappen, Blatt vorauswaehlen
    const startOrdnerId = ursprungsLeistung.folderId ?? block.kategorieId ?? null;
    useEffect(() => {
        if (startOrdnerId == null) return;
        let abgebrochen = false;
        void ladeOrdnerKette(startOrdnerId).then(kette => {
            if (abgebrochen || kette.length === 0) return;
            merke(kette, false);
            // Alle aufklappen – ein Ordner der untersten Ebene klappt ohnehin nicht auf.
            setAufgeklappteIds(new Set(kette.map(k => k.id)));
            // Hat der Bediener schon selbst gewaehlt, bleibt seine Wahl. Ob der
            // Ordner ueberhaupt waehlbar ist, entscheidet der Baum (siehe gewaehlteId unten).
            setVorgemerkteId(vorher => vorher ?? kette[0].id);
        });
        return () => { abgebrochen = true; };
    }, [startOrdnerId, merke]);

    const pfadVon = useCallback((id: number): string => {
        const teile: string[] = [];
        let aktuell: KategorieNode | undefined = bekannte.get(id);
        for (let i = 0; aktuell && i <= MAX_PFAD_TIEFE; i++) {
            teile.unshift(aktuell.bezeichnung);
            aktuell = aktuell.parentId == null ? undefined : bekannte.get(aktuell.parentId);
        }
        return teile.join(' › ');
    }, [bekannte]);

    // Altdaten: Liegt die Ursprungs-Leistung in einem Ordner mit Unterordnern,
    // wird er nur aufgeklappt, aber nicht als Ziel vorausgewaehlt.
    const vorgemerkt = vorgemerkteId == null ? undefined : bekannte.get(vorgemerkteId);
    const gewaehlteId = vorgemerkt?.ausBaum && !vorgemerkt.isLeaf ? null : vorgemerkteId;
    const gewaehlterKnoten = gewaehlteId == null ? undefined : bekannte.get(gewaehlteId);
    const ordnerOk = gewaehlterKnoten != null && gewaehlterKnoten.ausBaum && gewaehlterKnoten.isLeaf;
    const nameBereinigt = name.trim();
    const nameOk = nameBereinigt.length > 0 && nameBereinigt.length <= MAX_NAME_LAENGE;
    const kannSpeichern = nameOk && ordnerOk && !speichert;

    const sperrGrund = !nameOk
        ? 'Bitte geben Sie der Leistung einen Namen.'
        : !ordnerOk ? HINWEIS_UNTERSTE_EBENE : undefined;

    const textVorschau = useMemo(() => htmlZuEinzeiler(block.description), [block.description]);
    const einheit = (block.unit || 'Stk').trim();

    const speichern = async () => {
        if (!kannSpeichern || laeuftRef.current || gewaehlteId == null) return;
        laeuftRef.current = true;
        setSpeichert(true);
        setFehler(null);
        const ordner: GewaehlterOrdner = { id: gewaehlteId, pfad: pfadVon(gewaehlteId) };
        try {
            const res = await fetch('/api/leistungen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: nameBereinigt,
                    description: htmlZuEinzeiler(block.description) ? block.description ?? null : null,
                    price: typeof block.price === 'number' && Number.isFinite(block.price) ? block.price : null,
                    unit: einheitZuLeistungsEinheit(block.unit),
                    folderId: gewaehlteId,
                }),
            });
            if (!res.ok) {
                const meldung = await fehlermeldungAus(res);
                setFehler(meldung);
                toast.error(meldung);
                return;
            }
            const neu = await res.json() as LeistungApiDto;
            onGespeichert(neu, ordner);
        } catch {
            setFehler(ALLGEMEINER_FEHLER);
            toast.error(ALLGEMEINER_FEHLER);
        } finally {
            laeuftRef.current = false;
            setSpeichert(false);
        }
    };

    return (
        <Dialog
            open
            onOpenChange={(offen) => { if (!offen && !laeuftRef.current) onClose(); }}
            className="w-full max-w-2xl"
            aria-labelledby={titelId}
        >
            <DialogContent>
                <DialogHeader className="pr-8">
                    <div className="flex items-center gap-2.5">
                        <div className="p-2 bg-rose-50 rounded-xl flex-shrink-0">
                            <BookmarkPlus className="w-4 h-4 text-rose-600" aria-hidden="true" />
                        </div>
                        <DialogTitle id={titelId} className="text-base font-bold text-slate-900">
                            Als neue Leistung speichern
                        </DialogTitle>
                        <InfoButton titel="Als neue Leistung speichern">
                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                <p>
                                    Aus dieser Position wird eine zusätzliche Leistung in Ihren Stammdaten angelegt –
                                    mit Name, Text, Preis und Einheit, so wie sie jetzt in der Position stehen. Sie liegt
                                    danach im gewählten Ordner und kann in jedem Dokument wieder eingefügt werden.
                                </p>
                            </InfoAbschnitt>
                            <InfoAbschnitt titel="Was bewirkt das?">
                                <p>
                                    Die ursprüngliche Leistung bleibt unverändert. Diese Position ist danach mit der neuen
                                    Leistung verbunden. Bereits verschickte Angebote und Rechnungen ändern sich nicht.
                                </p>
                            </InfoAbschnitt>
                        </InfoButton>
                    </div>
                    <DialogDescription>
                        „{ursprungsLeistung.name}“ bleibt, wie sie ist. Die geänderte Position kommt als weitere Leistung dazu.
                    </DialogDescription>
                </DialogHeader>

                <div className="flex flex-col gap-4 min-h-0 overflow-y-auto -m-1 p-1">
                    <div>
                        <Label htmlFor={nameId} className="font-medium">
                            Name der Leistung <span className="text-rose-600" aria-hidden="true">*</span>
                        </Label>
                        <Input
                            id={nameId}
                            value={name}
                            onChange={(e) => { setName(e.target.value); setFehler(null); }}
                            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); void speichern(); } }}
                            maxLength={MAX_NAME_LAENGE}
                            required
                            aria-invalid={!nameOk}
                            placeholder="z. B. Geländer montieren"
                            autoFocus
                            className="rounded-lg"
                        />
                        {!nameBereinigt && (
                            <p className="mt-1 text-xs text-amber-700">Bitte geben Sie der Leistung einen Namen.</p>
                        )}
                    </div>

                    <div className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2 text-xs text-slate-600 space-y-0.5">
                        <p>
                            <span className="font-semibold text-slate-500">Preis: </span>
                            <span className="tabular-nums text-slate-800">{formatCurrency(block.price ?? 0)} €</span>
                            <span> je {einheit}</span>
                        </p>
                        <p className="truncate" title={textVorschau || undefined} data-kuerzung-erlaubt="true">
                            <span className="font-semibold text-slate-500">Text: </span>
                            {textVorschau || <span className="italic text-slate-400">kein Text</span>}
                        </p>
                    </div>

                    <div className="min-h-0">
                        <p className="text-sm font-medium text-slate-800 mb-1.5">Ordner</p>
                        <div
                            className="max-h-72 overflow-y-auto rounded-xl border border-slate-200 bg-white p-2 space-y-1"
                            aria-label="Ordner wählen"
                            role="group"
                        >
                            {ladeWurzeln ? (
                                <div className="flex items-center gap-2 px-2 py-3 text-xs text-slate-400">
                                    <Loader2 className="w-3.5 h-3.5 motion-safe:animate-spin" aria-hidden="true" /> Ordner werden geladen…
                                </div>
                            ) : wurzeln.length === 0 ? (
                                <p className="px-2 py-3 text-xs text-slate-400">Keine Ordner vorhanden.</p>
                            ) : wurzeln.map(wurzel => (
                                <KategorieTreeNode
                                    key={wurzel.id}
                                    node={wurzel}
                                    selectedId={gewaehlteId}
                                    onSelect={(id) => { setVorgemerkteId(id); setFehler(null); }}
                                    onChildrenLoaded={merkeKnoten}
                                    aufgeklappteIds={aufgeklappteIds}
                                    nurBlaetterWaehlbar
                                />
                            ))}
                        </div>
                    </div>

                    {fehler && (
                        <p role="alert" className="flex items-start gap-1.5 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
                            <TriangleAlert className="w-3.5 h-3.5 mt-0.5 flex-shrink-0" aria-hidden="true" />
                            {fehler}
                        </p>
                    )}
                </div>

                <DialogFooter className="items-center gap-2 sm:space-x-0 border-t border-slate-100 pt-4">
                    <p
                        className={cn(
                            'flex items-center gap-1.5 text-xs min-w-0 sm:mr-auto',
                            ordnerOk ? 'text-slate-600' : 'text-amber-700'
                        )}
                        data-testid="speicherziel"
                    >
                        {ordnerOk ? (
                            <>
                                <FolderCheck className="w-3.5 h-3.5 text-rose-600 flex-shrink-0" aria-hidden="true" />
                                <span className="truncate" title={gewaehlteId != null ? pfadVon(gewaehlteId) : undefined} data-kuerzung-erlaubt="true">
                                    Wird gespeichert in: <span className="font-semibold text-slate-900">{gewaehlteId != null && pfadVon(gewaehlteId)}</span>
                                </span>
                            </>
                        ) : HINWEIS_UNTERSTE_EBENE}
                    </p>
                    <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={speichert}>
                        Abbrechen
                    </Button>
                    <span title={sperrGrund}>
                        <Button type="button" size="sm" onClick={() => void speichern()} disabled={!kannSpeichern}>
                            {speichert
                                ? <Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                                : <BookmarkPlus className="w-4 h-4" aria-hidden="true" />}
                            {speichert ? 'Wird gespeichert…' : 'Speichern'}
                        </Button>
                    </span>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
