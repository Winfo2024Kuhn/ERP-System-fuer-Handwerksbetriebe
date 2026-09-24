import { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, ArrowLeft, ArrowRight, Loader2, PenLine, Sparkles, X } from 'lucide-react';
import { Button } from '../ui/button';
import { ProjektSearchModal } from '../ProjektSearchModal';
import { SchrittBilder, type GewaehltesBild } from './schritte/SchrittBilder';
import { SchrittText, type TextStand } from './schritte/SchrittText';
import { MAX_BREITE_KI, MAX_BREITE_UPLOAD } from './bildbearbeitung';
import { rendereBlob } from './bildRendern';
import {
    WebsiteApiFehler, ladeBildHoch, legeBeitragAn, setzeAltText, setzeStatus, setzeTitelbild,
} from './api';

export interface BeitragAssistentProps {
    offen: boolean;
    onAbbrechen: () => void;
    onFertig: (beitragId: number) => void;
}

type Schritt = 'projekt' | 'bilder' | 'weg' | 'text';

interface Projekt { id: number; bauvorhaben: string; auftragsnummer?: string }

const LEERER_STAND: TextStand = { titel: '', kurzbeschreibung: '', textHtml: '' };

/**
 * Fuehrt durch das Anlegen eines neuen Website-Beitrags: Projekt waehlen,
 * Bilder aussuchen und bearbeiten, Text selbst schreiben oder von der KI
 * vorschlagen lassen, speichern.
 *
 * Bilder und Text leben bis zum Speichern nur im Browser. Erst am Ende
 * schreibt der Assistent in einer festen Reihenfolge zur Website, weil dort
 * ein Beitrag existieren muss, bevor Bilder angehaengt werden koennen.
 */
export function BeitragAssistent({ offen, onAbbrechen, onFertig }: BeitragAssistentProps) {
    const [schritt, setSchritt] = useState<Schritt>('projekt');
    const [projekt, setProjekt] = useState<Projekt | null>(null);
    const [auswahl, setAuswahl] = useState<GewaehltesBild[]>([]);
    const [stand, setStand] = useState<TextStand>(LEERER_STAND);
    const [mitKi, setMitKi] = useState(false);
    const [kiBilder, setKiBilder] = useState<Blob[]>([]);
    const [speichert, setSpeichert] = useState(false);
    const [fortschritt, setFortschritt] = useState<string | null>(null);
    const [teilfehler, setTeilfehler] = useState<string | null>(null);
    // Muss im Zustand der Komponente leben, nicht in einer lokalen Variable
    // von speichern(): bricht der Bilder-Upload ab, werden die Speicher-
    // Knoepfe kurz wieder anklickbar. Ein zweiter Klick darf dann NICHT noch
    // einmal legeBeitragAn aufrufen, sonst entsteht ein zweiter Beitrag auf
    // der Website -- und die kann einen Beitrag bewusst nicht loeschen.
    const [beitragId, setBeitragId] = useState<number | null>(null);
    // Haelt SchrittText dauerhaft eingehaengt, sobald der Textschritt einmal
    // erreicht wurde. SchrittText traegt intern einen useRef, der den
    // automatischen ersten KI-Lauf nur einmal ausloesen soll; ein useRef wird
    // beim Neu-Mounten zurueckgesetzt. Wuerde die Komponente beim Wechsel
    // "Zurueck" ausgehaengt und bei "Weiter" neu gemountet, liefe die KI ein
    // zweites Mal automatisch und ueberschriebe von Hand geschriebenen Text.
    // Deshalb bleibt sie im Baum und wird nur per hidden-Attribut versteckt.
    const textJeGezeigt = useRef(false);
    // ProjektSearchModal ruft in handleSelect erst onSelect und direkt danach
    // onClose auf. Ohne diese Unterscheidung landete auch das erfolgreiche
    // Waehlen eines Projekts bei onAbbrechen -- der Assistent schloss sich
    // dabei sofort wieder und "Neuer Beitrag" sah aus, als tue der Knopf nichts.
    // Ein Ref statt State, weil onSelect und onClose im selben Klick nacheinander
    // laufen: ein setState waere hier noch nicht sichtbar.
    const projektGewaehlt = useRef(false);

    // Zuruecksetzen, sobald der Assistent neu geoeffnet wird.
    useEffect(() => {
        if (offen) {
            setSchritt('projekt');
            setProjekt(null);
            setAuswahl([]);
            setStand(LEERER_STAND);
            setMitKi(false);
            setKiBilder([]);
            setTeilfehler(null);
            setFortschritt(null);
            setBeitragId(null);
            textJeGezeigt.current = false;
            projektGewaehlt.current = false;
        }
    }, [offen]);

    // Kleine Fassungen fuer die KI vorbereiten, sobald der Textschritt ansteht.
    const bereiteKiBilderVor = useCallback(async (gewaehlt: GewaehltesBild[]) => {
        const blobs: Blob[] = [];
        for (const eintrag of gewaehlt) {
            try {
                blobs.push(await rendereBlob(eintrag.bild.url, eintrag.bearbeitung, MAX_BREITE_KI, 0.8));
            } catch {
                // Ein einzelnes Bild weniger im Kontext ist kein Grund abzubrechen.
            }
        }
        setKiBilder(blobs);
    }, []);

    const speichern = async (veroeffentlichen: boolean) => {
        // beitragId aus dem Zustand: existiert schon einer (Teilabbruch bei
        // einem frueheren Versuch), bricht dieser Aufruf sofort ab, statt
        // ueber legeBeitragAn einen zweiten Beitrag anzulegen. Die Knoepfe
        // sind in diesem Fall zwar schon ausgeblendet (siehe istTeilabbruch
        // unten), diese Sperre bleibt aber als zweite Absicherung stehen.
        if (!projekt || beitragId !== null) return;
        setSpeichert(true);
        setTeilfehler(null);
        let angelegteId: number | null = null;
        let uebertragen = 0;

        try {
            setFortschritt('Beitrag wird angelegt...');
            const angelegt = await legeBeitragAn({
                title: stand.titel.trim(),
                excerpt: stand.kurzbeschreibung.trim(),
                content: stand.textHtml,
            });
            angelegteId = angelegt.id;
            // Sofort in den Zustand spiegeln: von hier an ueberlebt die
            // Kennung auch einen Abbruch weiter unten im Bilder-Upload.
            setBeitragId(angelegteId);

            // Nacheinander, nicht parallel: die Website rechnet jedes Bild um.
            let letzterStand = angelegt;
            for (let i = 0; i < auswahl.length; i++) {
                setFortschritt(`Bild ${i + 1} von ${auswahl.length} wird übertragen...`);
                const eintrag = auswahl[i];
                const blob = await rendereBlob(eintrag.bild.url, eintrag.bearbeitung, MAX_BREITE_UPLOAD);
                letzterStand = await ladeBildHoch(angelegteId, blob, eintrag.bild.originalDateiname);
                uebertragen += 1;

                // Alt-Text braucht einen zweiten Aufruf, weil der Upload ihn
                // nicht mitschickt (BeitraegeWebsiteClient.bildHinzufuegen).
                const neuestes = letzterStand.images.at(-1);
                if (neuestes && stand.titel.trim()) {
                    letzterStand = await setzeAltText(angelegteId, neuestes.id, stand.titel.trim());
                }
            }

            const titelbild = letzterStand.images[0];
            if (titelbild) {
                setFortschritt('Titelbild wird gesetzt...');
                await setzeTitelbild(angelegteId, titelbild.id);
            }

            if (veroeffentlichen) {
                setFortschritt('Beitrag wird veröffentlicht...');
                await setzeStatus(angelegteId, 'published');
            }

            onFertig(angelegteId);
        } catch (e) {
            if (angelegteId !== null) {
                // Kein Zuruecksetzen von beitragId: die Website-API kann
                // einen Beitrag bewusst nicht loeschen. Also ehrlich sagen,
                // was steht, und die Kennung fuer den Rest der Sitzung
                // gesperrt halten (siehe istTeilabbruch weiter unten).
                setTeilfehler(
                    `Der Beitrag wurde als Entwurf angelegt, aber nicht alles ging durch. `
                    + `${uebertragen} von ${auswahl.length} ${auswahl.length === 1 ? 'Bild' : 'Bildern'} `
                    + `wurde übertragen. Du kannst im Editor weitermachen. `
                    + `(${fehlertext(e)})`);
            } else {
                setTeilfehler(`Der Beitrag konnte nicht angelegt werden. ${fehlertext(e)}`);
            }
        } finally {
            setSpeichert(false);
            setFortschritt(null);
        }
    };

    if (!offen) return null;

    if (schritt === 'text') textJeGezeigt.current = true;

    const kannSpeichern = Boolean(
        stand.titel.trim() && stand.kurzbeschreibung.trim() && stand.textHtml.trim());
    // Beitrag steht schon (als Entwurf) auf der Website, aber nicht alles
    // ging durch. Ab hier sind beide Speicher-Knoepfe tabu -- siehe speichern().
    const istTeilabbruch = beitragId !== null && teilfehler !== null;

    // Schliesst den Assistenten so oder so. Nach einem Teilabbruch aber wie
    // bei Erfolg ueber onFertig, egal ob per X oben oder per Knopf unten:
    // sonst laedt die Beitragsliste nicht neu, der neue Entwurf bleibt
    // unsichtbar, und der Nutzer legt ihn im Glauben, nichts sei passiert,
    // versehentlich noch einmal an.
    const schliessen = () => {
        if (beitragId !== null && teilfehler !== null) {
            onFertig(beitragId);
        } else {
            onAbbrechen();
        }
    };

    return (
        <div className="fixed inset-0 bg-white z-[65] flex flex-col">
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Neuer Beitrag</p>
                    <h2 className="text-xl font-bold text-slate-900">
                        {projekt ? projekt.bauvorhaben : 'Projekt auswählen'}
                    </h2>
                </div>
                <div className="flex items-center gap-4">
                    <Schrittleiste aktiv={schritt} />
                    <button onClick={schliessen} aria-label="Assistent schließen"
                        className="p-1.5 hover:bg-slate-100 rounded-full">
                        <X className="w-5 h-5 text-slate-500" />
                    </button>
                </div>
            </div>

            <div className="flex-1 overflow-y-auto p-6">
                {teilfehler && (
                    <div className="mb-4 flex items-start gap-3 p-4 bg-amber-50 border border-amber-200 rounded-lg text-amber-900">
                        <AlertTriangle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                        <p>{teilfehler}</p>
                    </div>
                )}

                <ProjektSearchModal
                    isOpen={schritt === 'projekt'}
                    onClose={() => { if (!projektGewaehlt.current) onAbbrechen(); }}
                    onSelect={(gewaehlt) => {
                        projektGewaehlt.current = true;
                        setProjekt(gewaehlt as Projekt);
                        setSchritt('bilder');
                    }}
                />

                {schritt === 'bilder' && projekt && (
                    <SchrittBilder
                        projektId={projekt.id}
                        auswahl={auswahl}
                        onAuswahlAendern={setAuswahl}
                    />
                )}

                {schritt === 'weg' && (
                    <div className="max-w-2xl mx-auto py-12 grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <button
                            onClick={() => { setMitKi(false); setSchritt('text'); }}
                            aria-label="Selbst schreiben"
                            className="p-6 border-2 border-slate-200 rounded-xl text-left hover:border-rose-300 transition-colors"
                        >
                            <PenLine className="w-6 h-6 text-slate-500 mb-3" />
                            <p className="font-semibold text-slate-900">Selbst schreiben</p>
                            <p className="text-sm text-slate-500 mt-1">
                                Du schreibst den Text. Die KI kannst du danach trotzdem noch fragen.
                            </p>
                        </button>
                        <button
                            onClick={() => { setMitKi(true); setSchritt('text'); }}
                            aria-label="Von der KI vorschlagen lassen"
                            className="p-6 border-2 border-rose-200 bg-rose-50 rounded-xl text-left hover:border-rose-400 transition-colors"
                        >
                            <Sparkles className="w-6 h-6 text-rose-600 mb-3" />
                            <p className="font-semibold text-slate-900">Von der KI vorschlagen lassen</p>
                            <p className="text-sm text-slate-500 mt-1">
                                Die KI schaut sich die Bilder und die Leistungen an und schreibt einen Vorschlag.
                            </p>
                        </button>
                    </div>
                )}

                {textJeGezeigt.current && projekt && (
                    <div hidden={schritt !== 'text'}>
                        <SchrittText
                            projektId={projekt.id}
                            kiBilder={kiBilder}
                            vorschauBilder={auswahl.map(a => ({
                                url: a.bild.url,
                                altText: a.bild.originalDateiname,
                            }))}
                            stand={stand}
                            onStandAendern={setStand}
                            mitKi={mitKi}
                        />
                    </div>
                )}
            </div>

            <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200">
                {istTeilabbruch ? (
                    <div className="flex w-full justify-end">
                        <Button
                            size="sm"
                            onClick={schliessen}
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        >
                            Im Editor weitermachen
                            <ArrowRight className="w-4 h-4" />
                        </Button>
                    </div>
                ) : (
                    <>
                        <Button
                            size="sm"
                            disabled={schritt === 'projekt' || speichert}
                            onClick={() => setSchritt(schritt === 'text' ? 'weg' : 'bilder')}
                            className="border border-slate-300 text-slate-600 hover:bg-slate-100"
                        >
                            <ArrowLeft className="w-4 h-4" />
                            Zurück
                        </Button>

                        {fortschritt && (
                            <span className="flex items-center gap-2 text-sm text-slate-500">
                                <Loader2 className="w-4 h-4 animate-spin" />
                                {fortschritt}
                            </span>
                        )}

                        {schritt === 'bilder' && (
                            <Button
                                size="sm"
                                onClick={() => { void bereiteKiBilderVor(auswahl); setSchritt('weg'); }}
                                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            >
                                Weiter
                                <ArrowRight className="w-4 h-4" />
                            </Button>
                        )}

                        {schritt === 'text' && (
                            <div className="flex gap-2">
                                <Button
                                    size="sm"
                                    disabled={!kannSpeichern || speichert}
                                    onClick={() => void speichern(false)}
                                    className="border border-rose-300 text-rose-700 hover:bg-rose-50"
                                >
                                    Als Entwurf speichern
                                </Button>
                                <Button
                                    size="sm"
                                    disabled={!kannSpeichern || speichert}
                                    onClick={() => void speichern(true)}
                                    className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                >
                                    Veröffentlichen
                                </Button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}

function Schrittleiste({ aktiv }: { aktiv: Schritt }) {
    const schritte: { name: Schritt; label: string }[] = [
        { name: 'projekt', label: 'Projekt' },
        { name: 'bilder', label: 'Bilder' },
        { name: 'weg', label: 'Weg' },
        { name: 'text', label: 'Text' },
    ];
    return (
        <ol className="hidden md:flex items-center gap-2 text-sm">
            {schritte.map((s, i) => (
                <li key={s.name} className="flex items-center gap-2">
                    <span className={aktiv === s.name ? 'text-rose-700 font-medium' : 'text-slate-400'}>
                        {s.label}
                    </span>
                    {i < schritte.length - 1 && <span className="text-slate-300">/</span>}
                </li>
            ))}
        </ol>
    );
}

function fehlertext(e: unknown): string {
    if (e instanceof WebsiteApiFehler) {
        if (e.status === 502 || e.status === 0) return 'Die Website war nicht erreichbar.';
        return e.message;
    }
    return e instanceof Error ? e.message : 'Unbekannter Fehler.';
}
