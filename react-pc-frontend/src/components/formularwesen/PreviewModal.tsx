import { useRef, useState } from 'react';
import type { FormBlock } from '../../types';
import { Dialog, DialogDescription, DialogTitle } from '../ui/dialog';
import { PAGE_FORMATS } from './constants';
import { BlockInhalt } from './BlockInhalt';
import { NormOverlay } from './NormOverlay';
import { AnschriftHinweis } from './AnschriftHinweis';
import { NormSchalter, Schalter } from './NormSchalter';
import { ZoomRegler } from './ZoomRegler';
import { useSeitenZoom } from './useSeitenZoom';
import { sichereBildQuelle } from './bildQuelle';
import type { AnschriftStatus, Briefform } from './normMasse';

interface PreviewModalProps {
    items: FormBlock[];
    backgroundImage: string | null;
    backgroundImagePage2: string | null;
    liveData: Record<string, string>;
    form: Briefform;
    onForm: (form: Briefform) => void;
    anschriftStatus: AnschriftStatus;
    onAnschriftInsFenster: () => void;
    onClose: () => void;
}

const SEITE = PAGE_FORMATS.A4;
/** Abstand zwischen den Seiten und Platz für die Seitenbeschriftung (pt). */
const ABSTAND = 40;
const BESCHRIFTUNG = 28;

/**
 * Große Vorschau mit Beispieldaten. Die Norm-Hilfen sind hier von Anfang an
 * eingeschaltet, damit man sofort sieht, ob die Anschrift im Fenster sitzt.
 */
export default function PreviewModal({
    items, backgroundImage, backgroundImagePage2, liveData,
    form, onForm, anschriftStatus, onAnschriftInsFenster, onClose,
}: PreviewModalProps) {
    const [normAn, setNormAn] = useState(true);
    const [umschlag, setUmschlag] = useState(false);
    // Form A/B ist eine Einstellung der Vorlage, keine reine Ansicht – nach dem Umschalten darauf hinweisen.
    const [formGewechselt, setFormGewechselt] = useState(false);
    const bereichRef = useRef<HTMLDivElement>(null);

    const seite1 = items.filter(i => i.page === 1).sort((a, b) => a.z - b.z);
    const seite2 = items.filter(i => i.page === 2).sort((a, b) => a.z - b.z);
    const mitFolgeseite = seite2.length > 0;
    const zoom = useSeitenZoom(bereichRef, {
        breite: mitFolgeseite ? SEITE.width * 2 + ABSTAND : SEITE.width,
        hoehe: SEITE.height + BESCHRIFTUNG,
    });
    const z = zoom.zoom;

    const seite = (bloecke: FormBlock[], briefpapier: string | null, titel: string, ersteSeite: boolean) => {
        const bild = sichereBildQuelle(briefpapier);
        return (
            <figure className="shrink-0" aria-label={titel}>
                <figcaption className="mb-2 text-center text-xs font-medium text-slate-500">{titel}</figcaption>
                <div
                    data-testid={ersteSeite ? 'vorschau-seite-1' : 'vorschau-seite-2'}
                    className="relative overflow-hidden bg-white shadow-[0_1px_3px_rgba(15,23,42,0.08),0_12px_32px_-8px_rgba(15,23,42,0.25)] ring-1 ring-slate-200"
                    style={{ width: SEITE.width * z, height: SEITE.height * z }}
                >
                    <div className="absolute left-0 top-0" style={{ width: SEITE.width, height: SEITE.height, transform: `scale(${z})`, transformOrigin: 'top left' }}>
                        {bild && <img src={bild} alt="" className="pointer-events-none absolute inset-0 h-full w-full object-cover" />}
                        {bloecke.map(item => (
                            <div key={item.id} className="absolute overflow-hidden" style={{ left: item.x, top: item.y, width: item.width, height: item.height, zIndex: item.z }}>
                                <BlockInhalt item={item} daten={liveData} />
                            </div>
                        ))}
                        {ersteSeite && (normAn || umschlag) && (
                            <NormOverlay form={form} zoom={z} status={anschriftStatus} linien={normAn} umschlag={umschlag} />
                        )}
                    </div>
                </div>
            </figure>
        );
    };

    return (
        <Dialog
            open
            onOpenChange={offen => { if (!offen) onClose(); }}
            aria-labelledby="vorschau-titel"
            className="h-[94vh] w-[min(96vw,1600px)] max-w-none gap-0 overflow-hidden p-0"
        >
            <div className="flex flex-wrap items-center gap-x-6 gap-y-3 border-b border-slate-200 py-4 pl-6 pr-16">
                <div className="mr-auto">
                    <DialogTitle id="vorschau-titel" className="text-slate-900">Vorschau</DialogTitle>
                    <DialogDescription className="mt-1">Mit Beispieldaten – so sieht ein Dokument mit dieser Vorlage aus.</DialogDescription>
                </div>
                <div>
                    <NormSchalter an={normAn} onAn={setNormAn} form={form} onForm={f => { setFormGewechselt(true); onForm(f); }} />
                    {formGewechselt && (
                        <p role="status" className="mt-1 text-xs text-slate-500">Gilt für diese Vorlage – zum Übernehmen speichern.</p>
                    )}
                </div>
                <Schalter an={umschlag} onChange={setUmschlag} beschriftung="Umschlag-Ansicht" />
                <ZoomRegler {...zoom} />
            </div>

            <div className="border-b border-slate-100 px-6 py-3">
                <AnschriftHinweis status={anschriftStatus} onInsFenster={onAnschriftInsFenster} />
            </div>

            <div ref={bereichRef} className="relative min-h-0 flex-1 overflow-auto bg-slate-100" data-testid="vorschau-bereich">
                <div className="flex min-h-full min-w-full w-max p-6">
                    <div className="m-auto flex items-start" style={{ gap: ABSTAND * z }}>
                        {seite(seite1, backgroundImage, 'Seite 1', true)}
                        {mitFolgeseite && seite(seite2, backgroundImagePage2, 'Folgeseiten', false)}
                    </div>
                </div>
            </div>
        </Dialog>
    );
}
