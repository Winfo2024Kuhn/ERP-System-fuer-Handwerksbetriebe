import type { ComponentType, ReactNode } from 'react';
import { Building2, Mail, MapPin, Package, Phone, Wallet } from 'lucide-react';
import GoogleMapsEmbed from '../../components/GoogleMapsEmbed';
import { WeitereRufnummern } from '../telefon/WeitereRufnummern';
import type { LieferantDetail } from '../../types';

interface Props {
    lieferant: LieferantDetail;
    darfTelefon: boolean;
}

/**
 * Eine Zeile der Kontaktdaten. Rezeptur gegen Ueberlauf (Task 11/12): Icon
 * shrink-0, Textblock min-w-0 flex-1, Werte break-words statt truncate.
 */
function KontaktZeile({ symbol: Symbol, titel, children }: { symbol: ComponentType<{ className?: string }>; titel: string; children: ReactNode }) {
    return (
        <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
            <div className="p-2 bg-white rounded-md shadow-sm text-slate-400 shrink-0">
                <Symbol className="w-4 h-4" />
            </div>
            <div className="min-w-0 flex-1">
                <p className="text-xs text-slate-500">{titel}</p>
                {children}
            </div>
        </div>
    );
}

/** Rechte Spalte der Lieferanten-Detailansicht: Kontaktdaten und Karte. */
export function LieferantKontaktSpalte({ lieferant, darfTelefon }: Props) {
    return (
        <>
            <h2 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                <Phone className="w-5 h-5 text-rose-500" />
                Kontaktdaten
            </h2>
            <div className="space-y-4">
                <KontaktZeile symbol={Phone} titel="Telefon">
                    <p className="font-medium text-slate-900 break-words">{lieferant.telefon || '-'}</p>
                </KontaktZeile>
                <KontaktZeile symbol={Building2} titel="Mobil / Fax">
                    <p className="font-medium text-slate-900 break-words">{lieferant.mobiltelefon || '-'}</p>
                </KontaktZeile>
                {/* Beim Zuordnen von Anrufen gemerkte Nummern (erscheint nur, wenn es welche gibt). */}
                {lieferant.id && <WeitereRufnummern typ="LIEFERANT" kontaktId={Number(lieferant.id)} darfLoeschen={darfTelefon} />}
                <KontaktZeile symbol={Mail} titel="E-Mail">
                    <div className="flex flex-col">
                        {lieferant.kundenEmails && lieferant.kundenEmails.length > 0 ? (
                            lieferant.kundenEmails.map(email => (
                                // break-words statt truncate: eine Firmen-E-Mail-Adresse darf man
                                // in der schmalen Spalte nicht abschneiden.
                                <a key={email} href={`mailto:${email}`} className="font-medium text-rose-600 hover:underline break-words block">{email}</a>
                            ))
                        ) : (
                            <span className="text-slate-400">-</span>
                        )}
                    </div>
                </KontaktZeile>
                <KontaktZeile symbol={Package} titel="Standard-Kostenstelle">
                    <p className="font-medium text-slate-900 break-words">
                        {lieferant.standardKostenstelleName || <span className="text-slate-400">Keine zugewiesen</span>}
                    </p>
                </KontaktZeile>
                <KontaktZeile symbol={Wallet} titel="Bezahlung">
                    <p className="font-medium text-slate-900 break-words">
                        {lieferant.vorauskasse
                            ? "Im Voraus — Rechnungen sind beim Eintreffen schon bezahlt"
                            : "Auf Rechnung — Rechnungen landen in den Offenen Posten"}
                    </p>
                </KontaktZeile>
            </div>

            <div className="mt-8 pt-6 border-t border-slate-100 flex-1 flex flex-col min-h-0">
                <h3 className="text-sm font-medium text-slate-900 mb-3 flex items-center gap-2 shrink-0">
                    <MapPin className="w-4 h-4 text-slate-400" /> Standort
                </h3>
                <div className="flex-1 min-h-[200px] rounded-lg overflow-hidden border border-slate-200">
                    <GoogleMapsEmbed
                        strasse={lieferant.strasse}
                        plz={lieferant.plz}
                        ort={lieferant.ort}
                        className="w-full h-full"
                    />
                </div>
            </div>
        </>
    );
}
