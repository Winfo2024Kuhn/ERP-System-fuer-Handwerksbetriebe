import { X } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { EmailEntityDocumentPicker } from '../../../components/EmailEntityDocumentPicker';
import type { ProjektDokument } from '../../../types';

interface VorgangDateienDialogProps {
    anfrage: boolean;
    dokumente: ProjektDokument[];
    bautagebuch: ProjektDokument[];
    laedt: boolean;
    gewaehlteIds: Set<number>;
    ladendeIds: Set<number>;
    onUmschalten: (dokument: ProjektDokument) => void;
    onSchliessen: () => void;
}

/** Auswahl der Dateien aus Projekt bzw. Anfrage als Anhang. */
export function VorgangDateienDialog(p: VorgangDateienDialogProps) {
    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-4">
            <div className="flex h-[82vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl">
                <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
                    <div>
                        <h3 className="font-semibold text-slate-900">{p.anfrage ? 'Anfrage-Dateien' : 'Projekt-Dateien'}</h3>
                        <p className="mt-1 text-xs text-slate-500">Dateien anklicken, um sie als E-Mail-Anhang auszuwählen.</p>
                    </div>
                    <Button type="button" variant="ghost" size="sm" onClick={p.onSchliessen} aria-label="Dateiauswahl schließen">
                        <X className="h-5 w-5" />
                    </Button>
                </div>
                <div className="flex-1 overflow-y-auto p-4">
                    <EmailEntityDocumentPicker
                        documents={p.dokumente}
                        loading={p.laedt}
                        selectedIds={p.gewaehlteIds}
                        loadingIds={p.ladendeIds}
                        onToggle={p.onUmschalten}
                        bautagebuchDocuments={p.bautagebuch}
                    />
                </div>
                <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-5 py-3">
                    <span className="text-xs text-slate-500">{p.gewaehlteIds.size} Datei(en) ausgewählt</span>
                    <Button type="button" onClick={p.onSchliessen}>Auswahl übernehmen</Button>
                </div>
            </div>
        </div>
    );
}
