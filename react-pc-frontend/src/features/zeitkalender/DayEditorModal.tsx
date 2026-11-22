import { useEffect, useRef, useState } from 'react';
import { Clock, LockKeyhole, LockOpen, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { ProjektKategorieTreeModal } from '../../components/ProjektKategorieTreeModal';
import { ProjektSearchModal } from '../../components/ProjektSearchModal';
import { BuchungVerlaufDialog } from '../zeiterfassung/BuchungVerlaufDialog';
import { istAbwesenheitTyp, type Arbeitsgang, type KalenderTag, type Projekt } from './kalenderTypen';
import type { TagLinkAnlass } from './tagLink';
import { useTagesBuchungen } from './useTagesBuchungen';
import { useTagSpeichern } from './useTagSpeichern';
import { useBuchungBuchenUndLoeschen } from './useBuchungBuchenUndLoeschen';
import { useTagTastenkuerzel } from './useTagTastenkuerzel';
import { AbwesenheitKarte } from './AbwesenheitKarte';
import { BuchungZeile } from './BuchungZeile';
import { TagEditorFuss } from './TagEditorFuss';
import { TagMeldungHinweis } from './TagMeldungHinweis';
import { TagNeuLeiste } from './TagNeuLeiste';
import { UrlaubBuchenModal } from './UrlaubBuchenModal';
import { ZeitausgleichBuchenModal } from './ZeitausgleichBuchenModal';

/** Buchungen, die aus einer Meldung heraus hervorgehoben werden. */
export interface TagMarkierung {
    ids: number[];
    anlass: TagLinkAnlass | null;
}

interface Props {
    tag: KalenderTag;
    mitarbeiterId: number;
    projekte: Projekt[];
    arbeitsgaenge: Arbeitsgang[];
    onClose: () => void;
    readOnly?: boolean;
    onMonatsabschlussZuruecksetzen?: () => Promise<void>;
    markierung?: TagMarkierung;
}

type Auswahl = { art: 'projekt' | 'kategorie'; buchungId: number } | { art: 'urlaub' | 'zeitausgleich' } | null;

/** Tagesansicht eines Mitarbeiters: Buchungen ansehen, ändern, ergänzen und speichern. */
export function DayEditorModal({ tag, mitarbeiterId, projekte, arbeitsgaenge, onClose, readOnly = false, onMonatsabschlussZuruecksetzen, markierung }: Props) {
    const bestaetige = useConfirm();
    const tagesBuchungen = useTagesBuchungen(tag.buchungen);
    const { buchungen, zeitEntwurf, aendern, hinzufuegen, entfernen } = tagesBuchungen;
    const { speichert, gespeichert, speichereAlle } = useTagSpeichern(tagesBuchungen, mitarbeiterId, tag.datum);
    const { abwesenheitBuchen, loeschen } = useBuchungBuchenUndLoeschen({ mitarbeiterId, tag, hinzufuegen, entfernen });
    const [fokusIndex, setFokusIndex] = useState(0);
    const zwischenablage = useTagTastenkuerzel({ buchungen, fokusIndex, setFokusIndex, zeitEntwurf, hinzufuegen, aktiv: !readOnly });
    const [auswahl, setAuswahl] = useState<Auswahl>(null);
    const [verlaufBuchung, setVerlaufBuchung] = useState<{ id: number; beschreibung: string } | null>(null);
    const listeRef = useRef<HTMLDivElement>(null);

    const datumFormatted = new Date(tag.datum).toLocaleDateString('de-DE', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
    const markierteIds = new Set(markierung?.ids ?? []);
    const auswahlBuchung = auswahl && 'buchungId' in auswahl ? buchungen.find(b => b.id === auswahl.buchungId) : undefined;

    // Aus einer Meldung geöffnet: die erste markierte Zeile in den sichtbaren Bereich holen.
    useEffect(() => {
        listeRef.current?.querySelector('[data-markiert]')?.scrollIntoView?.({ block: 'center' });
    }, []);

    /** Schließen über X oder „Schließen“: bei ungespeicherten Änderungen erst nachfragen. */
    const requestClose = async () => {
        if (!readOnly && tagesBuchungen.ungespeichert()) {
            const verwerfen = await bestaetige({
                title: 'Ungespeicherte Änderungen verwerfen?',
                message: 'Es gibt Buchungen, die noch nicht gespeichert sind. Beim Schließen gehen diese Änderungen verloren.',
                confirmLabel: 'Verwerfen',
                cancelLabel: 'Weiter bearbeiten',
                variant: 'danger',
            });
            if (!verwerfen) return;
        }
        onClose();
    };

    return (
        <>
            <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
                <div role="dialog" aria-modal="true" aria-label="Tageserfassung" className="bg-slate-50 rounded-xl shadow-2xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden">
                    <div className="bg-white p-5 border-b border-slate-200 flex justify-between items-center">
                        <div>
                            <h2 className="text-xl font-bold text-slate-800">Tageserfassung</h2>
                            <p className="text-rose-600 font-medium">{datumFormatted}</p>
                        </div>
                        <button aria-label="Tageserfassung schließen" onClick={requestClose} className="p-2 hover:bg-slate-100 rounded-full transition-colors">
                            <X className="w-6 h-6 text-slate-500" />
                        </button>
                    </div>

                    {readOnly && (
                        <div className="mx-6 mt-4 p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center justify-between gap-3 text-amber-800 text-sm">
                            <div className="flex items-center gap-2">
                                <LockKeyhole className="w-4 h-4 text-amber-600 flex-shrink-0" />
                                <span>Dieser Monat ist abgeschlossen (schreibgeschützt). Keine Buchungsänderungen möglich.</span>
                            </div>
                            {onMonatsabschlussZuruecksetzen && (
                                <Button size="sm" variant="outline" className="border-amber-300 text-amber-900 hover:bg-amber-100 flex-shrink-0" onClick={onMonatsabschlussZuruecksetzen}>
                                    <LockOpen className="w-4 h-4 mr-1.5" />
                                    Monatsabschluss zurücksetzen
                                </Button>
                            )}
                        </div>
                    )}

                    {markierung?.anlass && <TagMeldungHinweis anlass={markierung.anlass} />}

                    <div ref={listeRef} className="flex-1 overflow-y-auto p-6 space-y-4">
                        {buchungen.length === 0 ? (
                            <div className="text-center py-12 text-slate-400 bg-white rounded-lg border-2 border-dashed border-slate-200">
                                <Clock className="w-12 h-12 mx-auto mb-3 opacity-20" />
                                <p>Keine Buchungen für diesen Tag.</p>
                                <p className="text-sm">Mit „Neue Buchung“ legen Sie die erste Buchung an.</p>
                            </div>
                        ) : buchungen.map((b, index) => istAbwesenheitTyp(b.typ) ? (
                            <AbwesenheitKarte key={b.id} buchung={{ ...b, typ: b.typ }} readOnly={readOnly} onLoeschen={() => loeschen(b)} />
                        ) : (
                            <BuchungZeile
                                key={b.id}
                                buchung={b}
                                nummer={index + 1}
                                fokussiert={fokusIndex === index}
                                markiert={markierteIds.has(b.id)}
                                readOnly={readOnly}
                                projekte={projekte}
                                arbeitsgaenge={arbeitsgaenge}
                                zeitEntwurf={zeitEntwurf}
                                onFokus={() => setFokusIndex(index)}
                                onAendern={patch => aendern(b.id, patch)}
                                onProjektWaehlen={() => setAuswahl({ art: 'projekt', buchungId: b.id })}
                                onKategorieWaehlen={() => setAuswahl({ art: 'kategorie', buchungId: b.id })}
                                onVerlauf={() => setVerlaufBuchung({
                                    id: b.id,
                                    beschreibung: `${datumFormatted} · ${zeitEntwurf(b, 'startZeit') || '–'} – ${zeitEntwurf(b, 'endeZeit') || 'offen'}`,
                                })}
                                onLoeschen={() => loeschen(b)}
                            />
                        ))}

                        {!readOnly && (
                            <TagNeuLeiste
                                onBuchung={tagesBuchungen.neueBuchung}
                                onPause={tagesBuchungen.neuePause}
                                onUrlaub={() => setAuswahl({ art: 'urlaub' })}
                                onKrankheit={() => abwesenheitBuchen('KRANKHEIT')}
                                onFortbildung={() => abwesenheitBuchen('FORTBILDUNG')}
                                onZeitausgleich={() => setAuswahl({ art: 'zeitausgleich' })}
                            />
                        )}
                    </div>

                    <TagEditorFuss
                        readOnly={readOnly}
                        kopiert={!!zwischenablage}
                        speichert={speichert}
                        gespeichert={gespeichert}
                        speichernGesperrt={buchungen.length === 0}
                        onSchliessen={requestClose}
                        onSpeichern={speichereAlle}
                    />
                </div>
            </div>

            {auswahl?.art === 'kategorie' && auswahlBuchung && (
                <ProjektKategorieTreeModal
                    projektId={auswahlBuchung.projektId}
                    onSelect={(kategorieId, kategorieName) => {
                        aendern(auswahlBuchung.id, { produktkategorieId: kategorieId, produktkategorieName: kategorieName });
                        setAuswahl(null);
                    }}
                    onClose={() => setAuswahl(null)}
                />
            )}

            {auswahl?.art === 'projekt' && (
                <ProjektSearchModal
                    isOpen={true}
                    currentProjektId={auswahlBuchung?.projektId}
                    onSelect={projekt => {
                        aendern(auswahl.buchungId, { projektId: projekt.id });
                        setAuswahl(null);
                    }}
                    onClose={() => setAuswahl(null)}
                />
            )}

            <BuchungVerlaufDialog
                open={!!verlaufBuchung}
                onOpenChange={offen => { if (!offen) setVerlaufBuchung(null); }}
                buchungId={verlaufBuchung?.id ?? null}
                beschreibung={verlaufBuchung?.beschreibung}
            />

            {auswahl?.art === 'urlaub' && (
                <UrlaubBuchenModal
                    open
                    onClose={() => setAuswahl(null)}
                    datumFormatted={datumFormatted}
                    sollStunden={tag.sollStunden}
                    onConfirm={halberTag => abwesenheitBuchen('URLAUB', halberTag)}
                />
            )}

            {auswahl?.art === 'zeitausgleich' && (
                <ZeitausgleichBuchenModal
                    open
                    onClose={() => setAuswahl(null)}
                    datumFormatted={datumFormatted}
                    sollStunden={tag.sollStunden}
                    onConfirm={stunden => abwesenheitBuchen('ZEITAUSGLEICH', false, stunden)}
                />
            )}
        </>
    );
}
