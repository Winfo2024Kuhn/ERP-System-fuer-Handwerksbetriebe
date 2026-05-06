import { AlertTriangle, X, Mail, Phone, MapPin, User } from 'lucide-react';
import { Button } from './ui/button';
import type { KundeDuplikatTreffer } from './KundeDuplikatHinweis';

interface Props {
    isOpen: boolean;
    duplikate: KundeDuplikatTreffer[];
    /** User bestätigt: trotzdem neu anlegen (POST mit X-Duplikat-Bestaetigt-Header). */
    onTrotzdemAnlegen: () => void;
    /** User wählt einen bestehenden Kunden aus der Liste. */
    onBestehendenWaehlen: (treffer: KundeDuplikatTreffer) => void;
    /** Modal schließen ohne Aktion. */
    onAbbrechen: () => void;
}

const GRUND_LABELS: Record<string, string> = {
    EMAIL_GLEICH: 'Gleiche E-Mail-Adresse',
    TELEFON_GLEICH: 'Gleiche Telefonnummer',
    MOBILTELEFON_GLEICH: 'Gleiche Mobilnummer',
    NAME_PLZ_GLEICH: 'Gleicher Name und PLZ',
    NAME_STRASSE_GLEICH: 'Gleicher Name und Straße',
};

/**
 * Bestätigungs-Modal das nach einem 409-Response von POST /api/kunden geöffnet wird.
 * Zeigt die gefundenen Treffer mit Match-Gründen, und gibt dem User drei Optionen:
 * bestehenden Kunden öffnen, Anlage trotzdem fortsetzen, oder abbrechen.
 */
export function KundeDuplikatBestaetigungModal({
    isOpen, duplikate, onTrotzdemAnlegen, onBestehendenWaehlen, onAbbrechen,
}: Props) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/60 z-[70] flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[85vh] flex flex-col overflow-hidden">
                {/* Header */}
                <div className="px-6 py-4 border-b border-amber-200 bg-amber-50 flex items-start justify-between gap-4">
                    <div className="flex items-start gap-3">
                        <div className="w-10 h-10 rounded-full bg-amber-100 flex items-center justify-center flex-shrink-0">
                            <AlertTriangle className="w-5 h-5 text-amber-600" />
                        </div>
                        <div>
                            <h2 className="text-lg font-bold text-amber-900">Möglicher Doppel-Eintrag</h2>
                            <p className="text-sm text-amber-800 mt-0.5">
                                Es gibt bereits {duplikate.length === 1 ? 'einen Kunden' : `${duplikate.length} Kunden`} mit
                                ähnlichen Daten. Möchten Sie den bestehenden Kunden nutzen?
                            </p>
                        </div>
                    </div>
                    <button onClick={onAbbrechen} className="p-1 hover:bg-amber-100 rounded-full">
                        <X className="w-5 h-5 text-amber-700" />
                    </button>
                </div>

                {/* Treffer-Liste */}
                <div className="flex-1 overflow-y-auto p-6 space-y-3">
                    {duplikate.map(t => (
                        <div
                            key={t.id}
                            className="border-2 border-slate-200 hover:border-rose-300 rounded-xl p-4 transition-colors"
                        >
                            <div className="flex items-start justify-between gap-3 mb-3">
                                <div className="min-w-0 flex-1">
                                    <p className="text-base font-semibold text-slate-900 truncate">
                                        {t.name}
                                        {t.kundennummer && (
                                            <span className="ml-2 text-xs font-mono bg-slate-100 px-1.5 py-0.5 rounded">
                                                {t.kundennummer}
                                            </span>
                                        )}
                                    </p>
                                    {t.ansprechspartner && (
                                        <p className="text-sm text-slate-500 flex items-center gap-1.5 mt-0.5">
                                            <User className="w-3.5 h-3.5" />{t.ansprechspartner}
                                        </p>
                                    )}
                                </div>
                                <Button
                                    size="sm"
                                    onClick={() => onBestehendenWaehlen(t)}
                                    className="bg-rose-600 text-white hover:bg-rose-700 flex-shrink-0"
                                >
                                    Diesen verwenden
                                </Button>
                            </div>

                            {/* Match-Gründe */}
                            <div className="flex flex-wrap gap-1.5 mb-3">
                                {t.gruende.map(g => (
                                    <span
                                        key={g}
                                        className="inline-flex items-center gap-1 text-xs bg-rose-100 text-rose-800 px-2 py-0.5 rounded-full font-medium"
                                    >
                                        <AlertTriangle className="w-3 h-3" />
                                        {GRUND_LABELS[g] || g}
                                    </span>
                                ))}
                            </div>

                            {/* Kontakt-Infos */}
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm text-slate-600">
                                {(t.strasse || t.plz || t.ort) && (
                                    <p className="flex items-center gap-1.5">
                                        <MapPin className="w-3.5 h-3.5 text-slate-400" />
                                        <span className="truncate">
                                            {[t.strasse, [t.plz, t.ort].filter(Boolean).join(' ')].filter(Boolean).join(', ')}
                                        </span>
                                    </p>
                                )}
                                {t.telefon && (
                                    <p className="flex items-center gap-1.5">
                                        <Phone className="w-3.5 h-3.5 text-slate-400" />
                                        <span className="truncate">{t.telefon}</span>
                                    </p>
                                )}
                                {t.mobiltelefon && (
                                    <p className="flex items-center gap-1.5">
                                        <Phone className="w-3.5 h-3.5 text-slate-400" />
                                        <span className="truncate">{t.mobiltelefon}</span>
                                    </p>
                                )}
                                {t.kundenEmails && t.kundenEmails.length > 0 && (
                                    <p className="flex items-center gap-1.5">
                                        <Mail className="w-3.5 h-3.5 text-slate-400" />
                                        <span className="truncate">{t.kundenEmails[0]}</span>
                                    </p>
                                )}
                            </div>
                        </div>
                    ))}
                </div>

                {/* Footer */}
                <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex justify-between items-center gap-3">
                    <Button variant="outline" onClick={onAbbrechen}>
                        Abbrechen
                    </Button>
                    <Button
                        variant="outline"
                        onClick={onTrotzdemAnlegen}
                        className="border-amber-300 text-amber-800 hover:bg-amber-50"
                    >
                        Trotzdem neu anlegen
                    </Button>
                </div>
            </div>
        </div>
    );
}
