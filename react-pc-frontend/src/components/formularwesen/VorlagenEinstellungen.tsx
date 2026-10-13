import { useRef } from 'react';
import { Check, ImagePlus, MousePointerClick, RotateCcw, Trash2 } from 'lucide-react';
import { Button } from '../ui/button';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { useToast } from '../ui/toast';
import { cn } from '../../lib/utils';
import { DOCUMENT_TYPES } from './constants';
import { Gruppe } from './Gruppe';
import { sichereBildQuelle } from './bildQuelle';

interface VorlagenEinstellungenProps {
    briefpapier: { seite1: string | null; folgeseiten: string | null };
    onBriefpapier: (seite: 1 | 2, bild: string | null) => void;
    dokumenttypen: string[];
    onDokumenttypen: (typen: string[]) => void;
    onLayoutZuruecksetzen: () => void;
}

const KUERZEL: [string, string][] = [
    ['Pfeiltasten', 'verschieben (mit ⇧ in 10er-Schritten)'],
    ['Entf', 'löschen'],
    ['Strg + D', 'duplizieren'],
    ['Strg + Z / Y', 'rückgängig / wiederholen'],
    ['Esc', 'Auswahl aufheben'],
];

/** Rechte Spalte ohne Auswahl: kurze Hilfe und alles, was für die ganze Vorlage gilt. */
export default function VorlagenEinstellungen({ briefpapier, onBriefpapier, dokumenttypen, onDokumenttypen, onLayoutZuruecksetzen }: VorlagenEinstellungenProps) {
    return (
        <aside aria-label="Einstellungen der Vorlage" className="flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-100 px-4 py-3">
                <h2 className="text-sm font-semibold text-slate-900">Vorlage</h2>
                <p className="mt-0.5 text-xs text-slate-500">Gilt für alle Seiten dieser Vorlage</p>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto">
                <section className="px-4 py-4">
                    <div className="flex gap-3 rounded-lg bg-slate-50 p-3">
                        <MousePointerClick aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-rose-600" />
                        <div className="text-xs text-slate-600">
                            <p className="font-semibold text-slate-800">So geht&apos;s</p>
                            <p className="mt-1">Baustein auf der Seite anklicken, um ihn zu bearbeiten. Ziehen verschiebt ihn, die Griffe ändern die Größe.</p>
                            <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-2 gap-y-1">
                                {KUERZEL.map(([taste, wirkung]) => (
                                    <div key={taste} className="contents">
                                        <dt><kbd className="rounded border border-slate-200 bg-white px-1 py-px font-sans text-[10px] font-medium text-slate-700">{taste}</kbd></dt>
                                        <dd>{wirkung}</dd>
                                    </div>
                                ))}
                            </dl>
                        </div>
                    </div>
                </section>

                <Gruppe
                    titel="Briefpapier"
                    zusatz={(
                        <InfoButton titel="Briefpapier">
                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                <p>Das Bild liegt als Briefpapier hinter allen Bausteinen – Seite 1 und Folgeseiten getrennt.</p>
                            </InfoAbschnitt>
                            <InfoAbschnitt titel="Was bewirkt das?">
                                <p>Alle Dokumente mit dieser Vorlage bekommen dieses Briefpapier. Schon verschickte PDFs ändern sich nicht.</p>
                            </InfoAbschnitt>
                        </InfoButton>
                    )}
                >
                    <div className="space-y-3">
                        <BriefpapierFeld titel="Seite 1" bild={briefpapier.seite1} onBild={bild => onBriefpapier(1, bild)} />
                        <BriefpapierFeld titel="Folgeseiten" bild={briefpapier.folgeseiten} onBild={bild => onBriefpapier(2, bild)} />
                    </div>
                </Gruppe>

                <Gruppe titel="Verwenden für">
                    <ul className="space-y-0.5">
                        {DOCUMENT_TYPES.map(typ => {
                            const an = dokumenttypen.includes(typ);
                            return (
                                <li key={typ}>
                                    <button
                                        type="button"
                                        role="checkbox"
                                        aria-checked={an}
                                        onClick={() => onDokumenttypen(an ? dokumenttypen.filter(d => d !== typ) : [...dokumenttypen, typ])}
                                        className={cn(
                                            'flex w-full items-center gap-2.5 rounded-md px-2 py-1.5 text-left text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                                            an ? 'bg-rose-50 font-medium text-rose-700' : 'text-slate-600 hover:bg-slate-50',
                                        )}
                                    >
                                        <span className={cn('flex h-4 w-4 shrink-0 items-center justify-center rounded border', an ? 'border-rose-600 bg-rose-600 text-white' : 'border-slate-300 bg-white')}>
                                            {an && <Check aria-hidden="true" className="h-3 w-3" />}
                                        </span>
                                        {typ}
                                    </button>
                                </li>
                            );
                        })}
                    </ul>
                </Gruppe>

                <Gruppe titel="Layout">
                    <Button size="sm" variant="outline" className="w-full" onClick={onLayoutZuruecksetzen}>
                        <RotateCcw aria-hidden="true" className="mr-1.5 h-4 w-4" />Auf Standard-Layout zurücksetzen
                    </Button>
                </Gruppe>
            </div>
        </aside>
    );
}

/**
 * Das Backend nimmt eine Vorlage bis 5 MB an. Das Bild steckt als Base64 darin
 * (ein Drittel größer), deshalb höchstens 3,5 MB je Bild.
 */
const MAX_BRIEFPAPIER_BYTES = 3.5 * 1024 * 1024;

function BriefpapierFeld({ titel, bild, onBild }: { titel: string; bild: string | null; onBild: (bild: string | null) => void }) {
    const toast = useToast();
    const eingabe = useRef<HTMLInputElement>(null);
    const quelle = sichereBildQuelle(bild);

    const laden = (datei: File | undefined) => {
        if (!datei) return;
        if (datei.size > MAX_BRIEFPAPIER_BYTES) {
            const mb = (datei.size / 1024 / 1024).toLocaleString('de-DE', { maximumFractionDigits: 1 });
            toast.error(`Das Bild ist zu groß (${mb} MB). Bitte ein Bild unter 3,5 MB wählen.`);
            return;
        }
        const leser = new FileReader();
        leser.onload = () => {
            const ergebnis = typeof leser.result === 'string' ? leser.result : '';
            if (!sichereBildQuelle(ergebnis)) {
                toast.error('Das Bild konnte nicht übernommen werden. Bitte ein PNG-, JPG-, GIF- oder WebP-Bild wählen.');
                return;
            }
            onBild(ergebnis);
        };
        leser.onerror = () => toast.error('Das Bild konnte nicht gelesen werden.');
        leser.readAsDataURL(datei);
    };

    return (
        <div className="flex items-center gap-3">
            <input
                ref={eingabe}
                type="file"
                accept="image/png,image/jpeg,image/gif,image/webp"
                className="hidden"
                aria-label={`Briefpapier ${titel} hochladen`}
                onChange={e => { laden(e.target.files?.[0]); e.target.value = ''; }}
            />
            <button
                type="button"
                onClick={() => eingabe.current?.click()}
                tabIndex={-1}
                aria-hidden="true"
                className="relative flex h-[4.5rem] w-[3.2rem] shrink-0 items-center justify-center overflow-hidden rounded border border-dashed border-slate-300 bg-slate-50 text-slate-400 transition-colors hover:border-rose-300 hover:bg-rose-50 hover:text-rose-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
            >
                {quelle
                    ? <img src={quelle} alt="" className="absolute inset-0 h-full w-full bg-white object-cover" />
                    : <ImagePlus aria-hidden="true" className="h-5 w-5" />}
            </button>
            <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-slate-800">{titel}</p>
                <p className="text-xs text-slate-500">{quelle ? 'Bild hinterlegt' : 'Kein Briefpapier'}</p>
                <div className="mt-1 flex items-center gap-1">
                    <Button
                        size="sm"
                        variant="outline"
                        className="h-7 px-2 text-xs"
                        aria-label={quelle ? `Briefpapier ${titel} ändern` : `Briefpapier ${titel} wählen`}
                        onClick={() => eingabe.current?.click()}
                    >
                        {quelle ? 'Ändern' : 'Bild wählen'}
                    </Button>
                    {quelle && (
                        <button
                            type="button"
                            onClick={() => onBild(null)}
                            aria-label={`Briefpapier ${titel} entfernen`}
                            title="Entfernen"
                            className="inline-flex h-7 w-7 items-center justify-center rounded text-slate-400 hover:bg-red-50 hover:text-red-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                        >
                            <Trash2 aria-hidden="true" className="h-4 w-4" />
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}
