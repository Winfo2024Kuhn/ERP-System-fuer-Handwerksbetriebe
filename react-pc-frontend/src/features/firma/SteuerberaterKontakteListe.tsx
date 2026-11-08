import { Edit2, Plus, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { formatDatum } from '../../lib/datum';
import type { SteuerberaterKontakt } from './firmaTypen';

interface Props {
    steuerberater: SteuerberaterKontakt[];
    onNeu: () => void;
    onBearbeiten: (sb: SteuerberaterKontakt) => void;
    onLoeschen: (sb: SteuerberaterKontakt) => void;
}

/** Unterreiter „Kontakte“: Steuerberater als Karten mit Bearbeiten/Löschen. */
export function SteuerberaterKontakteListe({ steuerberater, onNeu, onBearbeiten, onLoeschen }: Props) {
    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <p className="text-slate-500 text-sm">
                    Steuerberater-Kontakte für die automatische Erkennung von BWA und Lohnabrechnungen
                </p>
                <Button onClick={onNeu}>
                    <Plus aria-hidden="true" className="w-4 h-4 mr-2" />
                    Neuer Steuerberater
                </Button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {steuerberater.map(sb => (
                    <SteuerberaterKarte key={sb.id} sb={sb} onBearbeiten={onBearbeiten} onLoeschen={onLoeschen} />
                ))}
                {steuerberater.length === 0 && (
                    <div className="col-span-2 text-center py-8 text-slate-400">
                        Noch keine Steuerberater angelegt
                    </div>
                )}
            </div>
        </div>
    );
}

interface KarteProps {
    sb: SteuerberaterKontakt;
    onBearbeiten: (sb: SteuerberaterKontakt) => void;
    onLoeschen: (sb: SteuerberaterKontakt) => void;
}

function SteuerberaterKarte({ sb, onBearbeiten, onLoeschen }: KarteProps) {
    return (
        <Card className="p-4">
            <div className="flex justify-between items-start">
                <div>
                    <h4 className="font-semibold text-slate-900">{sb.name}</h4>
                    <p className="text-sm text-slate-500">{sb.email}</p>
                    {sb.ansprechpartner && (
                        <p className="text-sm text-slate-400">Ansprechpartner: {sb.ansprechpartner}</p>
                    )}
                    <div className="flex gap-4 mt-2 text-xs text-slate-500">
                        <div>
                            <span className="font-medium">Von:</span> {formatDatum(sb.gueltigAb) || 'Offen'}
                        </div>
                        <div>
                            <span className="font-medium">Bis:</span> {formatDatum(sb.gueltigBis) || 'Offen'}
                        </div>
                    </div>
                    {sb.weitereEmails && sb.weitereEmails.length > 0 && (
                        <div className="mt-2 text-xs text-slate-500">
                            <span className="font-medium">Weitere E-Mails:</span> {sb.weitereEmails.join(', ')}
                        </div>
                    )}
                </div>
                <div className="flex gap-1">
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onBearbeiten(sb)}
                        aria-label={`${sb.name} bearbeiten`}
                        title="Bearbeiten"
                    >
                        <Edit2 aria-hidden="true" className="w-4 h-4" />
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onLoeschen(sb)}
                        aria-label={`${sb.name} löschen`}
                        title="Löschen"
                        className="text-red-600 hover:text-red-700"
                    >
                        <Trash2 aria-hidden="true" className="w-4 h-4" />
                    </Button>
                </div>
            </div>
            <div className="flex gap-2 mt-3">
                {sb.autoProcessEmails && (
                    <span className="text-xs bg-slate-100 text-slate-700 px-2 py-0.5 rounded">
                        Ganze Kanzlei wird erkannt
                    </span>
                )}
            </div>
        </Card>
    );
}
