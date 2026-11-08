import { Download, RefreshCw } from 'lucide-react';
import { Card } from '../../components/ui/card';
import { UnterlagenFilter } from './UnterlagenFilter';
import { formatiereBetrag, trefferText } from './firmaHilfen';
import type { BwaUploadDto } from './firmaTypen';
import type { useSteuerberaterUnterlagen } from './useSteuerberaterUnterlagen';

interface Props {
    unterlagen: ReturnType<typeof useSteuerberaterUnterlagen>;
}

/** Unterreiter „BWA / Auswertungen“. */
export function BwaListe({ unterlagen }: Props) {
    const { bwaListe } = unterlagen;
    return (
        <div className="space-y-4">
            <UnterlagenFilter
                jahr={unterlagen.jahr}
                verfuegbareJahre={unterlagen.verfuegbareJahre}
                onJahr={unterlagen.setJahr}
                anzahlText={trefferText(bwaListe.length, 'Auswertung', 'Auswertungen')}
            />

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {bwaListe.map(bwa => <BwaKarte key={bwa.id} bwa={bwa} />)}
                {bwaListe.length === 0 && (
                    <div className="col-span-full text-center py-12 text-slate-400 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                        Keine BWA-Dokumente für {unterlagen.jahr} gefunden
                    </div>
                )}
            </div>
        </div>
    );
}

function BwaKarte({ bwa }: { bwa: BwaUploadDto }) {
    return (
        <Card className="p-4 flex flex-col gap-3 hover:border-rose-200 transition">
            <div className="flex justify-between items-start">
                <div>
                    <h4 className="font-semibold text-slate-900">
                        {bwa.typ === 'MONATLICH' ? `BWA ${bwa.monat}/${bwa.jahr}` : `Jahresabschluss ${bwa.jahr}`}
                    </h4>
                    <p className="text-xs text-slate-500">
                        {bwa.steuerberaterName || 'Unbekannter Steuerberater'}
                    </p>
                </div>
                <a
                    href={bwa.pdfUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-rose-600 hover:bg-rose-50 p-1.5 rounded-full"
                    title="PDF anzeigen"
                >
                    <Download className="w-4 h-4" />
                </a>
            </div>

            <div className="space-y-1 text-sm border-t pt-2 mt-auto">
                <div className="flex justify-between">
                    <span className="text-slate-500">Gemeinkosten:</span>
                    <span className="font-medium text-slate-900">
                        {formatiereBetrag(bwa.gesamtGemeinkosten || 0)} €
                    </span>
                </div>
            </div>

            <div className="flex gap-2 text-xs pt-1">
                {bwa.analysiert ? (
                    <span className="bg-green-100 text-green-700 px-2 py-0.5 rounded flex items-center">
                        <RefreshCw className="w-3 h-3 mr-1" />
                        Analysiert
                    </span>
                ) : (
                    <span className="bg-yellow-100 text-yellow-700 px-2 py-0.5 rounded">
                        Ausstehend
                    </span>
                )}
                {bwa.freigegeben && (
                    <span className="bg-blue-100 text-blue-700 px-2 py-0.5 rounded">
                        Freigegeben
                    </span>
                )}
            </div>
        </Card>
    );
}
