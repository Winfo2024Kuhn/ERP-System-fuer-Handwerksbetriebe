// EN1090 picker adapted to the current Schnittbilder catalog (form, image, angles).
import { useEffect, useState } from 'react';
import { Check, Loader2, Ruler, Scissors, X } from 'lucide-react';
import { Button } from './ui/button';
import { Dialog } from './ui/dialog';
import { DecimalInput } from './ui/decimal-input';
import { useToast } from './ui/toast';
import { cn } from '../lib/utils';
import { toSafeResourceUrl } from '../lib/htmlSanitizer';
import { validateNumberDrafts } from '../lib/numberDrafts';
import { formatDecimalInput } from '../lib/numberInput';
import { einkaufApi } from '../features/einkauf/api';
interface Schnittbild { id: number; bildUrlSchnittbild: string; form: string }
export interface SonderzuschnittAuswahl { schnittForm: string; schnittbildBildUrl: string; anschnittWinkelLinks: number; anschnittWinkelRechts: number }
interface Props { isOpen: boolean; onClose: () => void; onSubmit: (value: SonderzuschnittAuswahl) => void; kategorieId?: number | null; artikelId?: number | null; initial?: Partial<SonderzuschnittAuswahl> | null }
export function SonderzuschnittPicker(props: Props) {
    return props.isOpen ? <SonderzuschnittDialog {...props} /> : null;
}
function SonderzuschnittDialog({ isOpen, onClose, onSubmit, kategorieId, artikelId, initial }: Props) {
    const toast = useToast();
    const [schnittbilder, setSchnittbilder] = useState<Schnittbild[]>([]);
    const [selectedForm, setSelectedForm] = useState(initial?.schnittForm ?? '');
    const [winkelLinks, setWinkelLinks] = useState(initial?.anschnittWinkelLinks == null ? '' : formatDecimalInput(initial.anschnittWinkelLinks));
    const [winkelRechts, setWinkelRechts] = useState(initial?.anschnittWinkelRechts == null ? '' : formatDecimalInput(initial.anschnittWinkelRechts));
    const [loadingSchnitte, setLoadingSchnitte] = useState(true);
    const [fehler, setFehler] = useState('');
    useEffect(() => {
        let aktuell = true;
        const params = new URLSearchParams();
        if (artikelId) params.set('artikelId', String(artikelId));
        else if (kategorieId) params.set('subKategorieId', String(kategorieId));
        void einkaufApi.get<Schnittbild[]>(`/api/schnittbilder?${params}`).then(data => { if (aktuell) setSchnittbilder(data); })
            .catch(() => { if (aktuell) { setFehler('Schnittbilder konnten nicht geladen werden.'); toast.error('Schnittbilder konnten nicht geladen werden.'); } })
            .finally(() => { if (aktuell) setLoadingSchnitte(false); });
        return () => { aktuell = false; };
    }, [isOpen, artikelId, kategorieId, toast]);
    const selectedSchnittbild = schnittbilder.find(s => s.form === selectedForm);
    const canSubmit = !!selectedSchnittbild;
    const handleSubmit = () => {
        if (!selectedSchnittbild) return;
        const result = validateNumberDrafts({ links: winkelLinks, rechts: winkelRechts }, {
            links: { label: 'Winkel links', min: -360, max: 360, maxDecimalPlaces: 2 }, rechts: { label: 'Winkel rechts', min: -360, max: 360, maxDecimalPlaces: 2 },
        });
        if (!result.valid) { setFehler(result.message); toast.error(result.message); return; }
        onSubmit({ schnittForm: selectedSchnittbild.form, schnittbildBildUrl: selectedSchnittbild.bildUrlSchnittbild, anschnittWinkelLinks: result.values.links ?? 90, anschnittWinkelRechts: result.values.rechts ?? 90 });
        onClose();
    };
    if (!isOpen) return null;
    return (
        <Dialog open={isOpen} onOpenChange={open => { if (!open) onClose(); }} className="w-[min(48rem,calc(100vw-2rem))] p-0 overflow-hidden" aria-labelledby="sonderzuschnitt-title">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-gradient-to-r from-rose-50 to-white rounded-t-2xl">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-rose-100 flex items-center justify-center">
                            <Scissors className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500 font-medium">
                                Sonderzuschnitt
                            </p>
                            <h2 id="sonderzuschnitt-title" className="text-lg font-bold text-slate-900 leading-tight">
                                Schnittbild und Winkel wählen
                            </h2>
                        </div>
                    </div>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onClose}
                        className="text-slate-500 hover:text-slate-700 hover:bg-slate-100"
                        aria-label="Schließen"
                    >
                        <X className="w-5 h-5" />
                    </Button>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {/* Schritt 2: Schnittbild */}
                    <section>
                        <div className="flex items-center gap-2 mb-3">
                            <span
                                className={cn(
                                    'inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold',
                                    'bg-rose-600 text-white',
                                )}
                            >
                                2
                            </span>
                            <h3 className="font-semibold text-slate-900">Schnittbild wählen</h3>
                        </div>
                        {loadingSchnitte ? (
                            <div className="text-center text-slate-500 py-6">
                                <Loader2 className="w-5 h-5 mx-auto mb-2 animate-spin text-rose-400" />
                                Schnittbilder werden geladen…
                            </div>
                        ) : schnittbilder.length === 0 ? (
                            <div className="text-center text-slate-500 py-6 border-2 border-dashed border-slate-200 rounded-xl">
                                <p className="text-sm">
                                    Für diese Warengruppe sind keine Schnittbilder hinterlegt. Bitte zuerst einen Artikel oder eine Warengruppe wählen.
                                </p>
                            </div>
                        ) : (
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                                {schnittbilder.map((sb) => {
                                    const active = sb.form === selectedForm;
                                    return (
                                        <button
                                            key={sb.id}
                                            type="button"
                                            onClick={() => setSelectedForm(sb.form)}
                                            className={cn(
                                                'relative flex flex-col items-center justify-center gap-2 p-3 rounded-xl border-2 transition-colors cursor-pointer',
                                                active
                                                    ? 'border-rose-500 bg-rose-50'
                                                    : 'border-slate-200 bg-white hover:border-rose-300 hover:bg-rose-50/40',
                                            )}
                                        >
                                            <img
                                                src={toSafeResourceUrl(sb.bildUrlSchnittbild) ?? undefined}
                                                alt={`Schnittbild ${sb.id}`}
                                                className="w-full h-10 object-contain"
                                                onError={(e) => {
                                                    (e.currentTarget as HTMLImageElement).style.opacity = '0.3';
                                                }}
                                            />
                                            <span className="text-xs text-slate-600">Schnitt #{sb.id}</span>
                                            {active && (
                                                <span className="absolute top-1 right-1 w-5 h-5 rounded-full bg-rose-600 text-white flex items-center justify-center">
                                                    <Check className="w-3 h-3" />
                                                </span>
                                            )}
                                        </button>
                                    );
                                })}
                            </div>
                        )}
                    </section>

                    {/* Schritt 3: Winkel */}
                    <section className={cn(!selectedForm && 'opacity-40 pointer-events-none')}>
                        <div className="flex items-center gap-2 mb-3">
                            <span
                                className={cn(
                                    'inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold',
                                    selectedForm ? 'bg-rose-600 text-white' : 'bg-slate-200 text-slate-500',
                                )}
                            >
                                3
                            </span>
                            <h3 className="font-semibold text-slate-900">Winkel links / rechts</h3>
                            <span className="text-xs text-slate-500 ml-2">(leer = 90°)</span>
                        </div>
                        <div className="grid grid-cols-2 gap-3 max-w-md">
                            <div>
                                <label className="block text-xs font-medium text-slate-500 mb-1 flex items-center gap-1">
                                    <Ruler className="w-3 h-3" /> Winkel links
                                </label>
                                <DecimalInput
                                    value={winkelLinks}
                                    aria-label="Winkel links"
                                    onChange={setWinkelLinks}
                                    placeholder="90"

                                />
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-slate-500 mb-1 flex items-center gap-1">
                                    <Ruler className="w-3 h-3" /> Winkel rechts
                                </label>
                                <DecimalInput
                                    value={winkelRechts}
                                    aria-label="Winkel rechts"
                                    onChange={setWinkelRechts}
                                    placeholder="90"

                                />
                            </div>
                        </div>
                    </section>
                </div>

                {fehler && <p role="alert" className="px-6 text-sm text-rose-700">{fehler}</p>}
                {/* Footer */}
                <div className="px-6 py-3 border-t border-slate-100 flex justify-end gap-2">
                    <Button variant="ghost" onClick={onClose}>
                        Abbrechen
                    </Button>
                    <Button
                        onClick={handleSubmit}
                        disabled={!canSubmit}
                        className="bg-rose-600 text-white hover:bg-rose-700"
                    >
                        <Check className="w-4 h-4" />
                        Übernehmen
                    </Button>
                </div>
        </Dialog>
    );
}
