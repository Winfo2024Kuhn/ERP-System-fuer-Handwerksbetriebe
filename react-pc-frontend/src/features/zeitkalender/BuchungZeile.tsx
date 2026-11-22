import { BellRing, Clock, Folder, History, Search, Trash2 } from 'lucide-react';
import { TimeInput } from '../../components/ui/time-input';
import { Select } from '../../components/ui/select-custom';
import { cn } from '../../lib/utils';
import type { Arbeitsgang, Buchung, Projekt } from './kalenderTypen';
import { dauerAnzeige, endeVorBeginn, type ZeitEntwurf } from './buchungZeiten';
import { loeschKnopfName } from './buchungLoeschen';

const FELD = 'w-full border border-slate-300 rounded-md px-3 py-1.5 focus:ring-2 focus:ring-rose-500 focus:border-rose-500 disabled:bg-slate-100 disabled:text-slate-500';
const BESCHRIFTUNG = 'block text-xs font-semibold text-slate-500 mb-1';

interface Props {
    buchung: Buchung;
    nummer: number;
    fokussiert: boolean;
    /** Aus einer Meldung („Zeiten prüfen“) hervorgehoben. */
    markiert: boolean;
    readOnly: boolean;
    projekte: Projekt[];
    arbeitsgaenge: Arbeitsgang[];
    zeitEntwurf: ZeitEntwurf;
    onFokus: () => void;
    onAendern: (patch: Partial<Buchung>) => void;
    onProjektWaehlen: () => void;
    onKategorieWaehlen: () => void;
    onVerlauf: () => void;
    onLoeschen: () => void;
}

function projektText(buchung: Buchung, projekte: Projekt[]) {
    if (!buchung.projektId) return <span className="text-slate-400">Projekt wählen…</span>;
    const p = projekte.find(pr => pr.id === buchung.projektId);
    return p ? `${p.auftragsnummer || ''} - ${p.bauvorhaben}${p.kunde ? ` (${p.kunde})` : ''}` : (buchung.projektName || 'Projekt wählen…');
}

/** Eine Arbeits- oder Pausenzeile der Tagesansicht. */
export function BuchungZeile(props: Props) {
    const { buchung: b, nummer, fokussiert, markiert, readOnly, zeitEntwurf, onAendern } = props;
    const zeitFehler = endeVorBeginn(b, zeitEntwurf);
    return (
        <div
            onClick={props.onFokus}
            data-ende-vor-beginn={zeitFehler || undefined}
            data-markiert={markiert || undefined}
            className={cn(
                'rounded-lg border shadow-sm p-4 cursor-pointer transition-colors',
                zeitFehler ? 'border-red-400 ring-2 ring-red-100'
                    : fokussiert ? 'border-rose-400 ring-2 ring-rose-100'
                        : markiert ? 'border-amber-300' : 'border-slate-200 hover:border-slate-300',
                // Seitenrand nach der Randfarbe, sonst überschreibt tailwind-merge ihn.
                markiert ? 'bg-amber-50 border-l-4 border-l-amber-500' : 'bg-white',
            )}
        >
            <div className="grid grid-cols-12 gap-4 items-start">
                <div className="col-span-1 pt-2">
                    <div className="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center text-sm font-bold text-slate-500">
                        {nummer}
                    </div>
                </div>

                <div className="col-span-11 md:col-span-10 grid grid-cols-2 gap-4">
                    {markiert && (
                        <p className="col-span-2 -mb-2">
                            <span className="inline-flex items-center gap-1 rounded bg-amber-100 px-1.5 py-0.5 text-xs font-medium text-amber-900">
                                <BellRing className="h-3 w-3" aria-hidden="true" />
                                Aus Meldung
                            </span>
                        </p>
                    )}
                    <div className="col-span-2 flex items-center gap-4">
                        <div className="flex-1">
                            <label className={BESCHRIFTUNG}>Von</label>
                            <TimeInput required aria-label={`Von Buchung ${nummer}`} className={FELD}
                                value={zeitEntwurf(b, 'startZeit')}
                                onChange={value => onAendern({ startZeit: value })}
                                disabled={readOnly}
                            />
                        </div>
                        <div className="flex-1">
                            <label className={BESCHRIFTUNG}>Bis</label>
                            <TimeInput aria-label={`Bis Buchung ${nummer}`} aria-invalid={zeitFehler || undefined} className={FELD}
                                value={zeitEntwurf(b, 'endeZeit')}
                                onChange={value => onAendern({ endeZeit: value })}
                                disabled={readOnly}
                            />
                        </div>
                        <div className="flex-1">
                            <label className="block text-xs font-semibold text-slate-400 mb-1">Dauer</label>
                            <div className={`px-3 py-1.5 border rounded-md text-sm ${zeitFehler ? 'bg-red-50 border-red-200 text-red-700 font-medium' : 'bg-slate-50 border-slate-200 text-slate-600'}`}>
                                {dauerAnzeige(b, zeitEntwurf)}
                            </div>
                        </div>
                    </div>

                    {b.typ !== 'PAUSE' ? (
                        <>
                            <div className="col-span-2 md:col-span-1">
                                <label className={BESCHRIFTUNG}>Projekt / Auftrag</label>
                                <button
                                    type="button"
                                    onClick={props.onProjektWaehlen}
                                    disabled={readOnly}
                                    className="w-full flex items-center gap-2 border border-slate-300 rounded-md px-3 py-1.5 text-left hover:border-rose-400 hover:bg-rose-50 transition-colors group disabled:opacity-60 disabled:hover:bg-white disabled:hover:border-slate-300"
                                >
                                    <Search className="w-4 h-4 text-slate-400 group-hover:text-rose-500 flex-shrink-0" />
                                    <span className="flex-1 truncate text-sm">{projektText(b, props.projekte)}</span>
                                </button>
                            </div>
                            <div className="col-span-2 md:col-span-1">
                                <label className={BESCHRIFTUNG}>Tätigkeit</label>
                                <Select
                                    aria-label={`Tätigkeit Buchung ${nummer}`}
                                    value={b.arbeitsgangId ? b.arbeitsgangId.toString() : ''}
                                    onChange={val => onAendern({ arbeitsgangId: Number(val) })}
                                    options={props.arbeitsgaenge.map(a => ({ value: a.id.toString(), label: a.beschreibung }))}
                                    placeholder="Tätigkeit wählen…"
                                    className="w-full"
                                    disabled={readOnly}
                                />
                            </div>
                            <div className="col-span-2">
                                <label className={BESCHRIFTUNG}>Produktkategorie (optional)</label>
                                <button
                                    type="button"
                                    onClick={props.onKategorieWaehlen}
                                    disabled={readOnly}
                                    className="w-full flex items-center gap-2 px-3 py-1.5 border border-slate-300 rounded-md text-left text-sm hover:bg-slate-50 focus:ring-2 focus:ring-rose-500 focus:border-rose-500 disabled:opacity-60 disabled:hover:bg-white"
                                >
                                    <Folder className="w-4 h-4 text-rose-500 flex-shrink-0" />
                                    <span className={b.produktkategorieName ? 'text-slate-800' : 'text-slate-400'}>
                                        {b.produktkategorieName || 'Kategorie wählen...'}
                                    </span>
                                </button>
                            </div>
                        </>
                    ) : (
                        <div className="col-span-2">
                            <div className="flex items-center gap-2 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg text-amber-700">
                                <Clock className="w-4 h-4" />
                                <span className="font-medium">Pausenzeit</span>
                                <span className="text-xs text-amber-600 ml-auto">wird nicht zur Arbeitszeit gezählt</span>
                            </div>
                        </div>
                    )}

                    <div className="col-span-2">
                        <label className={BESCHRIFTUNG}>Bemerkung</label>
                        <input
                            type="text"
                            placeholder="Optionale Notiz zur Tätigkeit..."
                            className={FELD}
                            value={b.notiz || ''}
                            onChange={e => onAendern({ notiz: e.target.value })}
                            disabled={readOnly}
                        />
                    </div>
                </div>

                <div className="col-span-1 flex flex-col gap-2 pt-6">
                    {b.id > 0 && (
                        <button
                            type="button"
                            onClick={props.onVerlauf}
                            className="p-2 bg-slate-50 text-slate-400 rounded hover:bg-rose-50 hover:text-rose-600 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                            title="Verlauf"
                            aria-label={`Verlauf Buchung ${nummer}`}
                        >
                            <History className="w-5 h-5" />
                        </button>
                    )}
                    {!readOnly && (
                        <button
                            type="button"
                            onClick={props.onLoeschen}
                            className="p-2 bg-slate-50 text-slate-400 rounded hover:bg-slate-100 hover:text-red-500 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                            title="Löschen"
                            aria-label={loeschKnopfName(b)}
                        >
                            <Trash2 className="w-5 h-5" />
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}
