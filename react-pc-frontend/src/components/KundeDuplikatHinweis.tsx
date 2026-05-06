import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, ChevronRight } from 'lucide-react';

export interface KundeDuplikatTreffer {
    id: number;
    kundennummer?: string;
    name: string;
    ansprechspartner?: string;
    strasse?: string;
    plz?: string;
    ort?: string;
    telefon?: string;
    mobiltelefon?: string;
    kundenEmails?: string[];
    score: number;
    gruende: string[];
}

interface KundeDuplikatHinweisProps {
    /** Aktuelle Eingabewerte – das Banner sucht debounced nach Duplikaten. */
    email?: string;
    telefon?: string;
    mobiltelefon?: string;
    name?: string;
    plz?: string;
    strasse?: string;
    /** Klick auf einen Treffer (z.B. um den bestehenden Kunden zu öffnen). */
    onTrefferKlick?: (treffer: KundeDuplikatTreffer) => void;
}

const GRUND_LABELS: Record<string, string> = {
    EMAIL_GLEICH: 'gleiche E-Mail',
    TELEFON_GLEICH: 'gleiche Telefonnummer',
    MOBILTELEFON_GLEICH: 'gleiche Mobilnummer',
    NAME_PLZ_GLEICH: 'gleicher Name und PLZ',
    NAME_STRASSE_GLEICH: 'gleicher Name und Straße',
};

/**
 * Live-Hinweis unter dem Kunden-Anlegen-Formular: ruft debounced
 * /api/kunden/duplikat-check und zeigt mögliche Duplikate als gelbe Warnung.
 *
 * Greift nicht ein – der User kann jederzeit weitermachen. Der harte Stop
 * passiert erst beim POST (siehe KundeDuplikatBestaetigungModal).
 */
export function KundeDuplikatHinweis({
    email, telefon, mobiltelefon, name, plz, strasse, onTrefferKlick,
}: KundeDuplikatHinweisProps) {
    const [treffer, setTreffer] = useState<KundeDuplikatTreffer[]>([]);
    const [harterTreffer, setHarterTreffer] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    useEffect(() => {
        if (debounceRef.current) clearTimeout(debounceRef.current);
        // Mindestens E-Mail oder Telefon oder (Name + PLZ/Straße) müssen da sein,
        // sonst lohnt sich die Anfrage nicht.
        const hatRelevanteEingabe = !!(email || telefon || mobiltelefon
            || (name && (plz || strasse)));
        if (!hatRelevanteEingabe) {
            // Reset asynchron, damit kein setState-in-effect-Warning auftritt.
            const id = setTimeout(() => {
                setTreffer([]);
                setHarterTreffer(false);
            }, 0);
            return () => clearTimeout(id);
        }
        debounceRef.current = setTimeout(() => {
            if (abortRef.current) abortRef.current.abort();
            const ctrl = new AbortController();
            abortRef.current = ctrl;
            const params = new URLSearchParams();
            if (email) params.set('email', email);
            if (telefon) params.set('telefon', telefon);
            if (mobiltelefon) params.set('mobiltelefon', mobiltelefon);
            if (name) params.set('name', name);
            if (plz) params.set('plz', plz);
            if (strasse) params.set('strasse', strasse);
            fetch(`/api/kunden/duplikat-check?${params.toString()}`, { signal: ctrl.signal })
                .then(res => res.ok ? res.json() : null)
                .then(data => {
                    if (!data) return;
                    setTreffer(data.duplikate || []);
                    setHarterTreffer(!!data.harterTreffer);
                })
                .catch(err => {
                    if (!(err instanceof DOMException && err.name === 'AbortError')) {
                        console.error('Duplikat-Check fehlgeschlagen:', err);
                    }
                });
        }, 400);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [email, telefon, mobiltelefon, name, plz, strasse]);

    if (treffer.length === 0) return null;

    const farbe = harterTreffer
        ? 'bg-rose-50 border-rose-300 text-rose-900'
        : 'bg-amber-50 border-amber-300 text-amber-900';
    const iconFarbe = harterTreffer ? 'text-rose-600' : 'text-amber-600';
    const headline = harterTreffer
        ? `Es gibt bereits ${treffer.length === 1 ? 'einen Kunden' : `${treffer.length} Kunden`} mit gleichen Daten:`
        : `${treffer.length === 1 ? 'Ein ähnlicher Kunde gefunden' : `${treffer.length} ähnliche Kunden gefunden`}:`;

    return (
        <div className={`p-3 rounded-lg border-2 ${farbe}`}>
            <div className="flex items-start gap-2">
                <AlertTriangle className={`w-5 h-5 mt-0.5 flex-shrink-0 ${iconFarbe}`} />
                <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold mb-2">{headline}</p>
                    <div className="space-y-1.5">
                        {treffer.map(t => (
                            <button
                                key={t.id}
                                type="button"
                                onClick={() => onTrefferKlick?.(t)}
                                className="w-full text-left bg-white/60 hover:bg-white border border-current/20 rounded-md px-2.5 py-2 transition-colors group"
                            >
                                <div className="flex items-center justify-between gap-2">
                                    <div className="min-w-0 flex-1">
                                        <p className="text-sm font-medium truncate">
                                            {t.name}
                                            {t.kundennummer && (
                                                <span className="ml-1.5 text-xs font-mono opacity-70">({t.kundennummer})</span>
                                            )}
                                        </p>
                                        <p className="text-xs opacity-80 truncate">
                                            {t.gruende.map(g => GRUND_LABELS[g] || g).join(' · ')}
                                            {(t.plz || t.ort) && (
                                                <span className="opacity-60"> – {[t.plz, t.ort].filter(Boolean).join(' ')}</span>
                                            )}
                                        </p>
                                    </div>
                                    <ChevronRight className="w-4 h-4 opacity-40 group-hover:opacity-80 flex-shrink-0" />
                                </div>
                            </button>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}
