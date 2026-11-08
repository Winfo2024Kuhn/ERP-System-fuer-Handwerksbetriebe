import { Card } from '../../components/ui/card';
import { DecimalInput } from '../../components/ui/decimal-input';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { FirmaSpeichernKnopf } from './FirmaSpeichernKnopf';
import { formatiereBetrag } from './firmaHilfen';
import type { FirmaAendern, FirmenEntwurf, GewerkOption } from './firmaTypen';

interface Props {
    firma: FirmenEntwurf;
    gewerke: GewerkOption[];
    aendern: FirmaAendern;
    speichert: boolean;
    onSpeichern: () => void;
}

/** Reiter „Unfallversicherung“: Gewerk wählen, BG und Beitragssatz. */
export function UnfallversicherungReiter({ firma, gewerke, aendern, speichert, onSpeichern }: Props) {
    const vorschlag = firma.bgSatzVorschlag != null ? formatiereBetrag(Number(firma.bgSatzVorschlag)) : null;
    const gewerkWaehlen = (v: string) => {
        const id = v ? Number(v) : null;
        const g = gewerke.find(x => x.id === id);
        aendern({
            gewerkId: id,
            gewerkName: g?.name ?? null,
            bgName: g?.bgName ?? null,
            bgSatzVorschlag: g?.bgSatzProzent ?? null,
        });
    };
    return (
        <Card className="p-6 space-y-4">
            <div>
                <h3 className="text-lg font-semibold text-slate-900">Gewerk &amp; Unfallversicherung</h3>
                <p className="text-sm text-slate-500 mt-1">
                    Wähle dein Gewerk – wir schlagen dir die passende Berufsgenossenschaft (BG) und einen
                    Standard-Beitragssatz vor. Wenn du den genauen Satz aus deinem Beitragsbescheid kennst,
                    trägst du ihn unten ein.
                </p>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                    <Label>Gewerk</Label>
                    <Select
                        value={firma.gewerkId ? String(firma.gewerkId) : ''}
                        onChange={gewerkWaehlen}
                        options={[
                            { value: '', label: '— Bitte wählen —' },
                            ...gewerke.map(g => ({ value: String(g.id), label: `${g.name} (${g.bgName})` })),
                        ]}
                    />
                </div>
                <div>
                    <Label>Berufsgenossenschaft</Label>
                    <Input value={firma.bgName ?? ''} disabled placeholder="— wird durch Gewerk gesetzt —" />
                </div>
                <div>
                    <Label>BG-Satz (Vorschlag)</Label>
                    <Input
                        value={vorschlag != null ? `${vorschlag} %` : ''}
                        disabled
                        placeholder="—"
                    />
                </div>
                <div>
                    <Label>Tatsächlicher BG-Satz (aus Bescheid)</Label>
                    <DecimalInput
                        aria-label="Tatsächlicher BG-Satz (aus Bescheid)"
                        min={0}
                        max={100}
                        value={firma.bgSatzOverride ?? ''}
                        onChange={value => aendern({ bgSatzOverride: value })}
                        placeholder={vorschlag != null ? `Standard ${vorschlag} %` : 'optional'}
                    />
                    <p className="text-xs text-slate-500 mt-1">Leer lassen, um den Standard-Satz zu nutzen.</p>
                </div>
            </div>
            <div className="pt-4 border-t flex justify-end">
                <FirmaSpeichernKnopf speichert={speichert} onSpeichern={onSpeichern} ladeAnzeige="text" />
            </div>
        </Card>
    );
}
