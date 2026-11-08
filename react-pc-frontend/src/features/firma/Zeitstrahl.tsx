import type { LucideIcon } from 'lucide-react';
import { DecimalInput } from '../../components/ui/decimal-input';

// Bausteine des Mahnverfahren-Zeitstrahls (nur auf der Firma-Seite verwendet).

/** Eine Station auf dem Zeitstrahl: Kreis-Marker mit Icon, Label daneben (mobil) bzw. darunter (Desktop). */
export function ZeitstrahlStation({ icon: Icon, label }: { icon: LucideIcon; label: string }) {
    return (
        <div className="flex shrink-0 items-center gap-3 md:w-32 md:flex-col md:gap-2">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border-2 border-rose-300 bg-rose-50 text-rose-600">
                <Icon className="h-5 w-5" />
            </div>
            <span className="text-sm font-medium text-slate-700 md:text-center">{label}</span>
        </div>
    );
}

/** Verbindung zwischen zwei Stationen mit Tage-Eingabefeld: mobil vertikal, ab md horizontal. */
export function ZeitstrahlAbstand({ value, onChange, beschriftung }: { value: string; onChange: (tage: string) => void; beschriftung: string }) {
    return (
        <div className="flex md:flex-1 md:flex-col">
            {/* Verbindungslinie: mobil senkrecht unter dem Kreis, ab md waagerecht auf Kreis-Mitte */}
            <div className="flex w-10 shrink-0 justify-center self-stretch md:h-5 md:w-full md:items-end md:self-auto">
                <div className="min-h-[3.5rem] w-px bg-rose-200 md:h-px md:min-h-0 md:w-full" />
            </div>
            <div className="flex items-center gap-2 py-3 md:mt-2 md:flex-col md:gap-1 md:px-2 md:py-0">
                <DecimalInput
                    aria-label={beschriftung}
                    required integer
                    min={1}
                    value={value}
                    onChange={onChange}
                    className="w-20 text-center"
                />
                <span className="text-xs text-slate-500 md:text-center">{beschriftung}</span>
            </div>
        </div>
    );
}
