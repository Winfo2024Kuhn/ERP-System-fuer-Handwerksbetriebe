import { Plus, RefreshCw, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { cn } from '../../lib/utils';

interface Props {
    logoSrc: string | null;
    hatLogo: boolean;
    laedtHoch: boolean;
    onHochladen: (file: File) => void;
    onLoeschen: () => void;
}

/** Abschnitt „Firmenlogo“: Vorschau, Hochladen/Ersetzen und Entfernen. */
export function FirmenLogoKarte({ logoSrc, hatLogo, laedtHoch, onHochladen, onLoeschen }: Props) {
    return (
        <div className="mb-8 pb-6 border-b">
            <h3 className="text-lg font-semibold text-slate-900 mb-4">Firmenlogo</h3>
            <div className="flex items-start gap-6">
                <div className="w-40 h-40 rounded-lg border-2 border-dashed border-slate-200 bg-slate-50 flex items-center justify-center overflow-hidden">
                    {logoSrc ? (
                        <img
                            src={logoSrc}
                            alt="Firmenlogo"
                            className="max-w-full max-h-full object-contain"
                        />
                    ) : (
                        <span className="text-xs text-slate-400 text-center px-4">
                            Kein Logo hinterlegt
                        </span>
                    )}
                </div>
                <div className="flex-1 space-y-3">
                    <p className="text-sm text-slate-600">
                        Das hier hochgeladene Logo erscheint auf allen generierten PDFs
                        (Angebote, Rechnungen, Bestellungen, Preisanfragen). Formate:
                        PNG, JPEG oder WebP, maximal 2&nbsp;MB.
                    </p>
                    <div className="flex items-center gap-2">
                        <label className={cn(
                            "inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg cursor-pointer transition",
                            laedtHoch
                                ? "bg-slate-200 text-slate-500 cursor-not-allowed"
                                : "bg-rose-600 text-white hover:bg-rose-700"
                        )}>
                            {laedtHoch ? (
                                <RefreshCw className="w-4 h-4 animate-spin" />
                            ) : (
                                <Plus className="w-4 h-4" />
                            )}
                            {hatLogo ? 'Logo ersetzen' : 'Logo hochladen'}
                            <input
                                type="file"
                                accept="image/png,image/jpeg,image/webp"
                                className="hidden"
                                disabled={laedtHoch}
                                onChange={e => {
                                    const file = e.target.files?.[0];
                                    if (file) onHochladen(file);
                                    e.target.value = '';
                                }}
                            />
                        </label>
                        {hatLogo && (
                            <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={onLoeschen}
                                disabled={laedtHoch}
                            >
                                <Trash2 className="w-4 h-4 mr-1" />
                                Entfernen
                            </Button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
