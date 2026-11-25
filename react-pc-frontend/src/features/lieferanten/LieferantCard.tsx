import { Edit2, MapPin, Phone, Users, Wallet } from 'lucide-react';
import { Card } from '../../components/ui/card';
import { LIEFERANT_ROLLEN, type Lieferant } from '../../types';

interface Props {
    lieferant: Lieferant;
    onClick: () => void;
    onEdit: (e: React.MouseEvent) => void;
}

/** Karte eines Lieferanten in der Übersicht. Klick öffnet, der Stift bearbeitet. */
export function LieferantCard({ lieferant, onClick, onEdit }: Props) {
    const adresse = [lieferant.strasse, [lieferant.plz, lieferant.ort].filter(Boolean).join(" ")].filter(Boolean).join(", ");
    const kontakt = [lieferant.telefon, lieferant.mobiltelefon].filter(Boolean).join(" / ");

    return (
        <Card
            className="group relative cursor-pointer hover:shadow-md transition-shadow border-slate-200 bg-white overflow-hidden h-full flex flex-col"
            onClick={onClick}
        >
            {/* Gemeinsame Rezeptur (Abschnitt 4 im Plan): line-clamp-2 statt truncate
                haelt den vollen Namen im title-Attribut lesbar, kein min-h-[3rem] --
                das riss bei kurzen Namen eine 24px-Luecke zum Meta-Block (Design-
                Review Abschnitt 3). Gleich hohe Karten stattdessen ueber h-full
                flex-col an der Karte und mt-auto am Meta-Block (Kontaktblock unten). */}
            <div className="p-4 flex flex-col h-full gap-3">
                <div className="min-w-0">
                    <div className="flex flex-wrap gap-1">
                        {lieferant.rollen && lieferant.rollen.length > 0 ? (
                            lieferant.rollen.map(rolle => (
                                <span key={rolle} className="text-xs font-semibold tracking-wider text-rose-600 uppercase bg-rose-50 px-2 py-0.5 rounded-full">
                                    {LIEFERANT_ROLLEN.find(r => r.value === rolle)?.label || rolle}
                                </span>
                            ))
                        ) : (
                            <span className="text-xs font-semibold tracking-wider text-rose-600 uppercase bg-rose-50 px-2 py-0.5 rounded-full">
                                {lieferant.lieferantenTyp || "Ohne Rolle"}
                            </span>
                        )}
                        {lieferant.vorauskasse && (
                            <span
                                className="inline-flex items-center gap-1 text-xs font-semibold tracking-wider text-amber-800 uppercase bg-amber-50 px-2 py-0.5 rounded-full"
                                title="Rechnungen dieser Firma gelten sofort als bezahlt und stehen nicht unter Offene Posten."
                            >
                                <Wallet className="w-3 h-3" />
                                Vorkasse
                            </span>
                        )}
                    </div>
                    <h3 className="font-semibold text-slate-900 mt-2 line-clamp-2 text-base" title={lieferant.lieferantenname} data-kuerzung-erlaubt>
                        {lieferant.lieferantenname || "Unbenannt"}
                    </h3>
                    {lieferant.aliasName && (
                        <p className="text-sm text-slate-600 truncate" title={lieferant.aliasName}>
                            auch: {lieferant.aliasName}
                        </p>
                    )}
                    <p className="text-sm text-slate-500 truncate">{lieferant.ort || "Kein Ort"}</p>
                </div>

                <div className="space-y-1 pt-2 border-t border-slate-50 mt-auto">
                    <div className="flex items-start gap-2 text-sm text-slate-600">
                        <MapPin className="w-4 h-4 mt-0.5 text-slate-400 shrink-0" />
                        <span className="line-clamp-2">{adresse || "-"}</span>
                    </div>
                    {(lieferant.telefon || lieferant.mobiltelefon) && (
                        <div className="flex items-center gap-2 text-sm text-slate-600">
                            <Phone className="w-4 h-4 text-slate-400 shrink-0" />
                            <span className="truncate">{kontakt}</span>
                        </div>
                    )}
                    {lieferant.ansprechpartnerHinweis && (
                        <div className="flex items-center gap-2 text-sm text-slate-600" title={lieferant.ansprechpartnerHinweis}>
                            <Users className="w-4 h-4 text-slate-400 shrink-0" />
                            <span className="sr-only">Ansprechpartner: </span>
                            <span className="truncate">{lieferant.ansprechpartnerHinweis}</span>
                        </div>
                    )}
                </div>
            </div>

            <button
                onClick={onEdit}
                className="absolute top-3 right-3 p-1.5 rounded-full bg-white/90 text-slate-400 opacity-0 group-hover:opacity-100 hover:text-rose-600 hover:bg-rose-50 transition-[opacity,color,background-color,border-color] shadow-sm border border-transparent hover:border-rose-100"
                title="Bearbeiten"
            >
                <Edit2 className="w-4 h-4" />
            </button>
        </Card>
    );
}
