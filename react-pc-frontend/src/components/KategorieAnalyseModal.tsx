import { useState } from 'react';
import { BarChart3, X } from 'lucide-react';
import { Button } from './ui/button';
import { Select } from './ui/select-custom';
import { KategorieAnalyseInhalt } from './KategorieAnalyseInhalt';
import type { Produktkategorie } from '../types';

interface KategorieAnalyseModalProps {
    kategorie: Produktkategorie;
    onClose: () => void;
}

const aktuellesJahr = new Date().getFullYear();
const JAHRE = [
    { value: '', label: 'Alle Jahre' },
    ...Array.from({ length: 10 }, (_, i) => ({ value: String(aktuellesJahr - i), label: String(aktuellesJahr - i) })),
];

/**
 * Analyse-Fenster aus dem Angebots-Editor (ServiceBlock). Der Inhalt steckt in
 * {@link KategorieAnalyseInhalt} – derselbe Baustein steht im Reiter
 * „Auswertung“ der Seite „Leistungen & Kategorien“.
 */
export const KategorieAnalyseModal: React.FC<KategorieAnalyseModalProps> = ({ kategorie, onClose }) => {
    const [jahr, setJahr] = useState('');

    return (
        <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4" onClick={onClose}>
            <div
                role="dialog"
                aria-modal="true"
                aria-label={`Analyse: ${kategorie.bezeichnung}`}
                className="bg-white rounded-2xl shadow-2xl w-full max-w-6xl flex flex-col max-h-[92vh]"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex items-center justify-between gap-4 px-6 py-4 border-b border-slate-100 rounded-t-2xl">
                    <div className="flex items-center gap-3 min-w-0">
                        <div className="w-10 h-10 rounded-xl bg-rose-100 flex items-center justify-center flex-shrink-0">
                            <BarChart3 className="w-5 h-5 text-rose-600" aria-hidden="true" />
                        </div>
                        <div className="min-w-0">
                            <p className="text-xs uppercase tracking-wide text-slate-500 font-medium">Auswertung</p>
                            <h2 className="text-lg font-bold text-slate-900 leading-tight truncate">{kategorie.bezeichnung}</h2>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <div className="w-40">
                            <Select value={jahr} onChange={setJahr} options={JAHRE} aria-label="Abschlussjahr" />
                        </div>
                        <Button variant="ghost" size="sm" onClick={onClose} aria-label="Fenster schließen"
                            className="text-slate-500 hover:text-slate-700 hover:bg-slate-100">
                            <X className="w-5 h-5" aria-hidden="true" />
                        </Button>
                    </div>
                </div>
                <div className="overflow-y-auto flex-1 p-6">
                    <KategorieAnalyseInhalt kategorieId={kategorie.id} jahr={jahr} />
                </div>
            </div>
        </div>
    );
};
