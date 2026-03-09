import { Select } from '../ui/select-custom';
import { DatePicker } from '../ui/datepicker';
import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN } from '../../types';
import type { AusgangsGeschaeftsDokumentTyp, KontextDaten } from './types';

interface DocumentEditorKopfdatenProps {
    dokumentTyp: AusgangsGeschaeftsDokumentTyp;
    datum: string;
    betreff: string;
    kontextDaten: KontextDaten;
    isLocked: boolean;
    isExpanded: boolean;
    onDokumentTypChange: (val: AusgangsGeschaeftsDokumentTyp) => void;
    onDatumChange: (val: string) => void;
    onBetreffChange: (val: string) => void;
}

export function DocumentEditorKopfdaten({
    dokumentTyp,
    datum,
    betreff,
    kontextDaten,
    isLocked,
    isExpanded,
    onDokumentTypChange,
    onDatumChange,
    onBetreffChange,
}: DocumentEditorKopfdatenProps) {
    const typLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp;
    const formattedDatum = datum ? new Date(datum).toLocaleDateString('de-DE') : '–';

    // Collapsed: show a compact inline summary
    if (!isExpanded) {
        return (
            <div className="bg-white border-b border-slate-200 px-4 py-1.5 flex items-center gap-3 text-[11px] flex-shrink-0">
                <span className="font-semibold text-rose-600 bg-rose-50 px-2 py-0.5 rounded">
                    {typLabel}
                </span>
                <span className="text-slate-400">·</span>
                <span className="text-slate-600">{formattedDatum}</span>
                {kontextDaten.kundennummer && (
                    <>
                        <span className="text-slate-400">·</span>
                        <span className="text-slate-500">KD {kontextDaten.kundennummer}</span>
                    </>
                )}
                {kontextDaten.projektnummer && (
                    <>
                        <span className="text-slate-400">·</span>
                        <span className="text-slate-500">PRJ {kontextDaten.projektnummer}</span>
                    </>
                )}
                {betreff && (
                    <>
                        <span className="text-slate-400">·</span>
                        <span className="text-slate-600 truncate max-w-xs font-medium">{betreff}</span>
                    </>
                )}
            </div>
        );
    }

    // Expanded: full form
    return (
        <div className="bg-white border-b border-slate-200 flex-shrink-0">
            <div className="px-4 py-3">
                {/* Row 1: Compact fields */}
                <div className="grid grid-cols-4 gap-3">
                    <div>
                        <label className="block text-[10px] font-semibold text-slate-400 uppercase tracking-wider mb-1">
                            Dokumenttyp
                        </label>
                        <Select
                            value={dokumentTyp}
                            onChange={(v) => onDokumentTypChange(v as AusgangsGeschaeftsDokumentTyp)}
                            options={AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN}
                            disabled={isLocked}
                        />
                    </div>
                    <div>
                        <label className="block text-[10px] font-semibold text-slate-400 uppercase tracking-wider mb-1">
                            Datum
                        </label>
                        <DatePicker
                            value={datum}
                            onChange={onDatumChange}
                            disabled={isLocked}
                        />
                    </div>
                    <div>
                        <label className="block text-[10px] font-semibold text-slate-400 uppercase tracking-wider mb-1">
                            Kundennummer
                        </label>
                        <div className="px-3 py-2 bg-slate-50 border border-slate-100 rounded-lg text-sm text-slate-600">
                            {kontextDaten.kundennummer || '–'}
                        </div>
                    </div>
                    <div>
                        <label className="block text-[10px] font-semibold text-slate-400 uppercase tracking-wider mb-1">
                            Projektnummer
                        </label>
                        <div className="px-3 py-2 bg-slate-50 border border-slate-100 rounded-lg text-sm text-slate-600">
                            {kontextDaten.projektnummer || '–'}
                        </div>
                    </div>
                </div>

                {/* Row 2: Betreff (full width) */}
                <div className="mt-3">
                    <label className="block text-[10px] font-semibold text-slate-400 uppercase tracking-wider mb-1">
                        Betreff / Bauvorhaben
                    </label>
                    <input
                        type="text"
                        value={betreff}
                        onChange={(e) => onBetreffChange(e.target.value)}
                        disabled={isLocked}
                        placeholder="Betreff eingeben…"
                        className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 disabled:bg-slate-50 disabled:text-slate-400 placeholder:text-slate-300 transition-all"
                    />
                </div>
            </div>
        </div>
    );
}
