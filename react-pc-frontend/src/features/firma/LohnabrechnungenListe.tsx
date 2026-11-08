import { Calendar, Download, FileText } from 'lucide-react';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { UnterlagenFilter } from './UnterlagenFilter';
import { formatiereBetrag } from './firmaHilfen';
import type { LohnabrechnungDto, SteuerberaterKontakt } from './firmaTypen';
import type { useSteuerberaterUnterlagen } from './useSteuerberaterUnterlagen';

interface Props {
    unterlagen: ReturnType<typeof useSteuerberaterUnterlagen>;
    steuerberater: SteuerberaterKontakt[];
}

/** Unterreiter „Lohnabrechnungen im System“. */
export function LohnabrechnungenListe({ unterlagen, steuerberater }: Props) {
    const { lohnabrechnungen } = unterlagen;
    return (
        <div className="space-y-4">
            <UnterlagenFilter
                jahr={unterlagen.jahr}
                verfuegbareJahre={unterlagen.verfuegbareJahre}
                onJahr={unterlagen.setJahr}
                anzahlText={`${lohnabrechnungen.length} Dokumente gefunden`}
            >
                <div className="w-64">
                    <Label>Steuerberater Filter</Label>
                    <Select
                        value={unterlagen.steuerberaterFilter}
                        onChange={unterlagen.setSteuerberaterFilter}
                        options={[
                            { value: 'ALL', label: 'Alle anzeigen' },
                            ...steuerberater.map(sb => ({ value: sb.id.toString(), label: sb.name }))
                        ]}
                    />
                </div>
            </UnterlagenFilter>

            <div className="grid grid-cols-1 gap-2">
                {lohnabrechnungen.map(la => <LohnabrechnungZeile key={la.id} la={la} />)}
                {lohnabrechnungen.length === 0 && (
                    <div className="text-center py-12 text-slate-400 bg-slate-50 rounded-lg border border-dashed border-slate-200">
                        Keine Lohnabrechnungen für diesen Zeitraum gefunden
                    </div>
                )}
            </div>
        </div>
    );
}

function LohnabrechnungZeile({ la }: { la: LohnabrechnungDto }) {
    return (
        <div className="flex items-center justify-between p-3 bg-white border border-slate-100 rounded-lg hover:border-rose-200 transition shadow-sm">
            <div className="flex items-center gap-4">
                <div className="h-10 w-10 bg-rose-50 rounded-full flex items-center justify-center text-rose-600">
                    <FileText className="w-5 h-5" />
                </div>
                <div>
                    <p className="font-medium text-slate-900">{la.mitarbeiterName}</p>
                    <div className="flex gap-2 text-xs text-slate-500">
                        <span className="flex items-center">
                            <Calendar className="w-3 h-3 mr-1" />
                            {la.monat}/{la.jahr}
                        </span>
                        {la.bruttolohn && (
                            <span>• Brutto: {formatiereBetrag(la.bruttolohn)} €</span>
                        )}
                        {la.nettolohn && (
                            <span>• Netto: {formatiereBetrag(la.nettolohn)} €</span>
                        )}
                    </div>
                </div>
            </div>
            <div className="flex items-center gap-4">
                {la.status === 'NEU' && (
                    <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded">Neu</span>
                )}
                <a
                    href={la.downloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-rose-600 hover:text-rose-700 hover:bg-rose-50 p-2 rounded-full transition"
                    title="PDF öffnen"
                >
                    <Download className="w-4 h-4" />
                </a>
            </div>
        </div>
    );
}
