import { useId, useState } from 'react';
import { AlertTriangle, History, PencilLine } from 'lucide-react';
import { Button } from '../../ui/button';
import { MehrLadenLeiste } from '../../ui/mehr-laden-leiste';
import DocumentPreviewModal from '../../DocumentPreviewModal';
import { StandKarte } from './StandKarte';
import { StaendeInfoButton, WiederherstellenInfoButton } from './StaendeInfo';
import { useDokumentStaende } from './useDokumentStaende';
import { useStandWiederherstellen } from './useStandWiederherstellen';
import { standPdfUrl, wiederherstellenSperrgrund } from './standFormat';
import type { DokumentBezugFuerStaende, StandEintrag } from './staendeTypen';

interface Props {
    dokumentId: number;
    dokumentNummer?: string;
    dokument?: DokumentBezugFuerStaende | null;
    /** Im Dokumenteditor geöffnet: Der Editor lädt danach neu und meldet das selbst. */
    imEditor?: boolean;
    /** Nach erfolgreichem Wiederherstellen, z. B. damit die Projektseite ihre Dokumentliste neu lädt. */
    onWiederhergestellt?: () => void;
}

function LadePlatzhalter() {
    return (
        <div className="space-y-3" aria-label="Stände werden geladen" role="status">
            {[0, 1, 2].map((i) => (
                <div key={i} className="rounded-lg border border-slate-200 bg-white p-4">
                    <div className="h-4 w-24 rounded bg-slate-200 motion-safe:animate-pulse" />
                    <div className="mt-2 h-3 w-64 rounded bg-slate-100 motion-safe:animate-pulse" />
                    <div className="mt-3 h-3 w-80 rounded bg-slate-100 motion-safe:animate-pulse" />
                </div>
            ))}
        </div>
    );
}

function OffeneAenderungen({ saetze }: { saetze: string[] }) {
    const titelId = useId();
    return (
        <section aria-labelledby={titelId} className="rounded-lg border border-amber-200 bg-amber-50 p-3">
            <h3 id={titelId} className="flex items-center gap-2 text-sm font-semibold text-amber-900">
                <PencilLine className="w-4 h-4" aria-hidden="true" />
                Seit dem letzten Export geändert
            </h3>
            <p className="mt-0.5 text-xs text-amber-800">Noch nicht verschickt oder gedruckt.</p>
            <ul className="mt-2 space-y-1 text-sm text-slate-800">
                {saetze.map((satz, i) => (
                    <li key={i} className="flex gap-2">
                        <span className="mt-2 h-1 w-1 shrink-0 rounded-full bg-amber-500" aria-hidden="true" />
                        <span className="break-words">{satz}</span>
                    </li>
                ))}
            </ul>
        </section>
    );
}

/** Reiter „Stände“ im Verlauf-Dialog: jeder Export als Karte, neueste zuerst. */
export function StaendeListe({ dokumentId, dokumentNummer, dokument, imEditor = false, onWiederhergestellt }: Props) {
    const liste = useDokumentStaende(dokumentId);
    const { wiederherstellen, laeuftFuer } = useStandWiederherstellen({ dokumentId, dokument, erfolgMelden: !imEditor });
    const [pdfStand, setPdfStand] = useState<StandEintrag | null>(null);

    const beimWiederherstellen = async (stand: StandEintrag) => {
        if (!(await wiederherstellen(stand, liste.offeneAenderungen ?? null))) return;
        liste.neuLaden();
        onWiederhergestellt?.();
    };

    const ersteLadung = liste.laedt && liste.eintraege.length === 0;
    const keinStand = !liste.laedt && !liste.fehler && liste.eintraege.length === 0;

    return (
        <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-600">
                <span className="inline-flex items-center gap-1">
                    Ein Stand entsteht bei jedem Drucken, PDF-Speichern und E-Mail-Versand.
                    <StaendeInfoButton />
                </span>
                <span className="inline-flex items-center gap-1">
                    Wiederherstellen
                    <WiederherstellenInfoButton />
                </span>
            </div>

            {ersteLadung ? (
                <LadePlatzhalter />
            ) : liste.fehler && liste.eintraege.length === 0 ? (
                <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                    <span className="flex items-center gap-2">
                        <AlertTriangle className="w-4 h-4 shrink-0 text-red-600" aria-hidden="true" />
                        {liste.fehler}
                    </span>
                    <Button variant="outline" size="sm" onClick={liste.neuLaden}>Erneut versuchen</Button>
                </div>
            ) : (
                <>
                    {liste.offeneAenderungen && liste.offeneAenderungen.length > 0 && (
                        <OffeneAenderungen saetze={liste.offeneAenderungen} />
                    )}
                    {keinStand ? (
                        <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center text-sm text-slate-600">
                            <History className="mx-auto mb-2 w-6 h-6 text-slate-400" aria-hidden="true" />
                            Noch kein Stand. Ein Stand entsteht, sobald Sie das Dokument drucken, als PDF speichern oder per E-Mail verschicken.
                        </div>
                    ) : (
                        <div className="space-y-3">
                            {liste.eintraege.map((stand) => (
                                <StandKarte
                                    key={stand.id}
                                    dokumentId={dokumentId}
                                    stand={stand}
                                    sperrgrund={wiederherstellenSperrgrund(liste.dokumentBearbeitbar, stand)}
                                    laeuft={laeuftFuer === stand.id}
                                    onPdf={() => setPdfStand(stand)}
                                    onWiederherstellen={() => void beimWiederherstellen(stand)}
                                />
                            ))}
                            <MehrLadenLeiste
                                angezeigt={liste.eintraege.length}
                                gesamt={liste.gesamt}
                                laedtMehr={liste.laedtMehr}
                                onMehrLaden={liste.mehrLaden}
                                zaehlerText={`${liste.eintraege.length} von ${liste.gesamt} Ständen angezeigt`}
                            />
                        </div>
                    )}
                </>
            )}

            {pdfStand && (
                <DocumentPreviewModal
                    isPdf
                    doc={{
                        url: standPdfUrl(dokumentId, pdfStand.id),
                        title: `${dokumentNummer ? `${dokumentNummer} – ` : ''}Stand ${pdfStand.nummer}`,
                    }}
                    onClose={() => setPdfStand(null)}
                />
            )}
        </div>
    );
}
