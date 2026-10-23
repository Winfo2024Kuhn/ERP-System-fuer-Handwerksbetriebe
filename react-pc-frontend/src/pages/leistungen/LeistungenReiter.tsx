import { useEffect, useMemo, useState } from 'react';
import { FilePlus, FileText, FolderInput, Info, Loader2, Plus, Save, Trash2, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { DecimalInput } from '../../components/ui/decimal-input';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { TiptapEditor } from '../../components/TiptapEditor';
import { useToast } from '../../components/ui/toast';
import { htmlZuEinzeiler } from '../../lib/htmlSanitizer';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { cn } from '../../lib/utils';
import { VERRECHNUNGSEINHEITEN } from '../../types';
import {
    beschreibungsVorschlag, einheitKurz, formatPreis, hatUnterordner, kinderVon, leistungPasst, leistungenImOrdner,
    pfadText, relativerPfad, type Baum,
} from './ordnerLogik';
import type { Leistung, Ordner, ZiehObjekt } from './typen';

export interface LeistungenReiterProps {
    ordner: Ordner;
    baum: Baum;
    leistungen: Leistung[];
    /** Suchtext aus dem Baum – filtert auch die Liste hier. */
    suchtext: string;
    onSucheLeeren: () => void;
    /** Leistung, die sofort im Formular geöffnet werden soll (z. B. aus der Suche). */
    oeffnen: { id: number; zaehler: number } | null;
    /** Wirft bei einem Fehler – die Meldung erscheint am Formular. */
    onSpeichern: (leistung: Omit<Leistung, 'id'> & { id?: number }) => Promise<Leistung>;
    onLoeschen: (leistung: Leistung) => void;
    onVerschieben: (leistung: Leistung) => void;
    onZiehenStart: (objekt: ZiehObjekt) => void;
    onZiehenEnde: () => void;
    onUngespeichert: (ungespeichert: boolean) => void;
}

interface Entwurf {
    id?: number;
    name: string;
    beschreibung: string;
    preis: string;
    einheit: string;
    ordnerId: string;
}

const EINHEITEN = VERRECHNUNGSEINHEITEN.map((e) => ({ value: e.name, label: `${e.anzeigename} (${einheitKurz(e.name)})` }));

function entwurfAus(leistung: Leistung): Entwurf {
    return {
        id: leistung.id,
        name: leistung.name,
        beschreibung: leistung.beschreibung,
        preis: formatDecimalInput(leistung.preis),
        einheit: leistung.einheit,
        ordnerId: leistung.ordnerId === null ? '' : String(leistung.ordnerId),
    };
}

const gleich = (a: Entwurf | null, b: Entwurf | null) => JSON.stringify(a) === JSON.stringify(b);

/**
 * Reiter „Leistungen“: Liste links (inkl. Unterordner mit Ordnerpfad), Formular
 * rechts. Neue Leistungen gehen nur in Ordnern ohne Unterordner – sonst ist
 * der Knopf gesperrt und der Grund steht sichtbar daneben. Leistungen lassen
 * sich aus der Liste auf einen Ordner im Baum ziehen.
 */
export function LeistungenReiter(props: LeistungenReiterProps) {
    const { ordner, baum, leistungen, suchtext, oeffnen, onUngespeichert } = props;
    const toast = useToast();
    const [entwurf, setEntwurf] = useState<Entwurf | null>(null);
    const [ausgang, setAusgang] = useState<Entwurf | null>(null);
    const [fehler, setFehler] = useState<string | null>(null);
    const [preisFehler, setPreisFehler] = useState<string | undefined>(undefined);
    const [sendet, setSendet] = useState(false);

    const istBlatt = !hatUnterordner(baum, ordner.id);
    const alle = useMemo(() => leistungenImOrdner(baum, leistungen, ordner.id), [baum, leistungen, ordner.id]);
    const liste = useMemo(() => (suchtext.trim() ? alle.filter((l) => leistungPasst(l, suchtext)) : alle), [alle, suchtext]);
    const ungespeichert = entwurf !== null && !gleich(entwurf, ausgang);

    useEffect(() => { onUngespeichert(ungespeichert); }, [ungespeichert, onUngespeichert]);
    useEffect(() => () => onUngespeichert(false), [onUngespeichert]);

    const blattOrdner = useMemo(
        () => [...baum.byId.values()]
            .filter((o) => !hatUnterordner(baum, o.id))
            .map((o) => ({ value: String(o.id), label: pfadText(baum, o.id) }))
            .sort((a, b) => a.label.localeCompare(b.label, 'de')),
        [baum],
    );

    const bearbeiten = (leistung: Leistung) => {
        const neu = entwurfAus(leistung);
        setEntwurf(neu);
        setAusgang(neu);
        setFehler(null);
        setPreisFehler(undefined);
    };

    // Aus der Suche geöffnete Leistung übernehmen.
    useEffect(() => {
        if (!oeffnen) return;
        const leistung = leistungen.find((l) => l.id === oeffnen.id);
        if (leistung) bearbeiten(leistung);
        // Nur auf neue Öffnen-Wünsche reagieren, nicht auf jede Listenänderung.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [oeffnen]);

    // Wird die bearbeitete Leistung gelöscht (oder ausgeblendet), schließt das Formular.
    const bearbeitetVorhanden = entwurf?.id === undefined || leistungen.some((l) => l.id === entwurf.id);
    const sichtbarerEntwurf = bearbeitetVorhanden ? entwurf : null;

    const neueLeistung = () => {
        const neu: Entwurf = {
            name: '',
            beschreibung: beschreibungsVorschlag(baum, ordner.id),
            preis: '',
            einheit: ordner.einheit ?? 'STUECK',
            ordnerId: String(ordner.id),
        };
        setEntwurf(neu);
        setAusgang(neu);
        setFehler(null);
        setPreisFehler(undefined);
    };

    const schliessen = () => { setEntwurf(null); setAusgang(null); setFehler(null); setPreisFehler(undefined); };

    const speichern = async () => {
        if (!entwurf) return;
        const preis = validateDecimalInput(entwurf.preis, { label: 'den Preis', required: true, min: 0 });
        const meldungen: string[] = [];
        if (!entwurf.name.trim()) meldungen.push('Bitte geben Sie der Leistung einen Namen.');
        if (!htmlZuEinzeiler(entwurf.beschreibung)) meldungen.push('Bitte beschreiben Sie die Leistung – der Text steht später im Angebot.');
        if (!entwurf.ordnerId) meldungen.push('Bitte wählen Sie einen Ordner.');
        setPreisFehler(preis.valid ? undefined : preis.message);
        if (!preis.valid) meldungen.push(preis.message);
        if (meldungen.length) {
            setFehler(meldungen.join(' '));
            toast.error(meldungen[0]);
            return;
        }
        setSendet(true);
        setFehler(null);
        try {
            const gespeichert = await props.onSpeichern({
                id: entwurf.id,
                name: entwurf.name.trim(),
                beschreibung: entwurf.beschreibung,
                preis: preis.valid ? preis.value ?? 0 : 0,
                einheit: entwurf.einheit,
                ordnerId: Number(entwurf.ordnerId),
            });
            toast.success(entwurf.id === undefined ? `Leistung „${gespeichert.name}“ angelegt.` : 'Leistung gespeichert.');
            bearbeiten(gespeichert);
        } catch (err) {
            const meldung = err instanceof Error ? err.message : 'Die Leistung konnte nicht gespeichert werden.';
            setFehler(meldung);
            toast.error(meldung);
        } finally {
            setSendet(false);
        }
    };

    const ersterUnterordner = kinderVon(baum, ordner.id)[0];
    const setze = (teil: Partial<Entwurf>) => setEntwurf((alt) => (alt ? { ...alt, ...teil } : alt));

    return (
        <div className="grid min-h-0 gap-5 lg:grid-cols-[minmax(0,320px)_minmax(0,1fr)]">
            {/* Liste */}
            <section aria-label={`Leistungen in „${ordner.name}“`} className="min-w-0 space-y-3">
                <div className="space-y-2">
                    <Button size="sm" onClick={neueLeistung} disabled={!istBlatt} aria-describedby={!istBlatt ? 'neue-leistung-grund' : undefined}
                        className="w-full">
                        <Plus className="h-4 w-4" aria-hidden="true" /> Neue Leistung
                    </Button>
                    {!istBlatt && (
                        <p id="neue-leistung-grund" className="flex items-start gap-1.5 text-xs text-slate-500">
                            <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                            <span>
                                Neue Leistungen kommen in den untersten Ordner
                                {ersterUnterordner ? <> – z. B. in „{ersterUnterordner.name}“.</> : '.'}
                            </span>
                        </p>
                    )}
                </div>

                {suchtext.trim() && (
                    <p className="flex items-center justify-between gap-2 rounded-md bg-slate-50 px-2 py-1 text-xs text-slate-600">
                        <span className="truncate">Gefiltert nach „{suchtext.trim()}“</span>
                        <button type="button" onClick={props.onSucheLeeren}
                            className="shrink-0 rounded px-1 font-medium text-rose-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                            Filter löschen
                        </button>
                    </p>
                )}

                {liste.length === 0 ? (
                    <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center">
                        <FilePlus className="mx-auto mb-2 h-8 w-8 text-slate-300" aria-hidden="true" />
                        <p className="text-sm text-slate-500">
                            {suchtext.trim() ? 'Keine Leistung in diesem Ordner passt zur Suche.'
                                : istBlatt ? 'In diesem Ordner gibt es noch keine Leistungen.' : 'In den Unterordnern gibt es noch keine Leistungen.'}
                        </p>
                    </div>
                ) : (
                    <ul className="-m-1 max-h-[calc(100vh-24rem)] space-y-1 overflow-y-auto p-1" aria-label="Leistungen">
                        {liste.map((l) => {
                            const offen = sichtbarerEntwurf?.id === l.id;
                            const unterpfad = l.ordnerId !== null && l.ordnerId !== ordner.id ? relativerPfad(baum, ordner.id, l.ordnerId) : '';
                            return (
                                <li
                                    key={l.id}
                                    draggable
                                    onDragStart={(e) => {
                                        e.dataTransfer.effectAllowed = 'move';
                                        e.dataTransfer.setData('text/plain', l.name);
                                        props.onZiehenStart({ art: 'leistung', id: l.id });
                                    }}
                                    onDragEnd={props.onZiehenEnde}
                                    className={cn(
                                        'group/leistung flex items-center gap-2 rounded-lg border bg-white p-2 transition-colors',
                                        offen ? 'border-rose-300 bg-rose-50' : 'border-slate-200 hover:border-rose-200',
                                    )}
                                >
                                    <button type="button" onClick={() => bearbeiten(l)} aria-current={offen ? 'true' : undefined}
                                        className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                                        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-rose-50" aria-hidden="true">
                                            <FileText className="h-4 w-4 text-rose-600" />
                                        </span>
                                        <span className="min-w-0">
                                            <span className="block truncate text-sm font-medium text-slate-900" title={l.name} data-kuerzung-erlaubt>{l.name}</span>
                                            <span className="block truncate text-xs text-slate-500" data-kuerzung-erlaubt>
                                                {formatPreis(l.preis)} / {einheitKurz(l.einheit) || l.einheit}
                                                {unterpfad && <> · {unterpfad}</>}
                                            </span>
                                        </span>
                                    </button>
                                    <span className={cn('shrink-0 items-center gap-0.5', offen ? 'flex' : 'hidden group-hover/leistung:flex group-focus-within/leistung:flex')}>
                                        <button type="button" onClick={() => props.onVerschieben(l)} aria-label={`„${l.name}“ verschieben nach …`} title="Verschieben nach …"
                                            className="rounded p-1 text-slate-500 hover:bg-slate-100 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                                            <FolderInput className="h-4 w-4" aria-hidden="true" />
                                        </button>
                                        <button type="button" onClick={() => props.onLoeschen(l)} aria-label={`„${l.name}“ löschen`} title="Löschen"
                                            className="rounded p-1 text-slate-500 hover:bg-red-50 hover:text-red-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                                            <Trash2 className="h-4 w-4" aria-hidden="true" />
                                        </button>
                                    </span>
                                </li>
                            );
                        })}
                    </ul>
                )}
                {liste.length > 0 && (
                    <p className="text-xs text-slate-500">Tipp: Leistungen lassen sich auf einen Ordner links ziehen.</p>
                )}
            </section>

            {/* Formular */}
            <section aria-label="Leistung bearbeiten" className="min-w-0">
                {!sichtbarerEntwurf ? (
                    <div className="flex h-full min-h-[16rem] flex-col items-center justify-center rounded-lg border border-dashed border-slate-200 p-8 text-center">
                        <FilePlus className="mb-3 h-10 w-10 text-slate-300" aria-hidden="true" />
                        <p className="text-sm text-slate-500">Wählen Sie links eine Leistung aus{istBlatt ? ' oder legen Sie eine neue an' : ''}.</p>
                    </div>
                ) : (
                    <div className="space-y-4 rounded-lg border border-slate-200 p-4">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                            <div className="min-w-0 flex-1 basis-48">
                                <p className="text-xs font-medium text-slate-500">{sichtbarerEntwurf.id === undefined ? 'Neue Leistung' : 'Leistung bearbeiten'}</p>
                                <h3 className="text-lg font-semibold text-slate-900 [overflow-wrap:anywhere]">{sichtbarerEntwurf.name.trim() || 'Ohne Namen'}</h3>
                            </div>
                            <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">
                                {ungespeichert && <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">Nicht gespeichert</span>}
                                <Button size="sm" variant="outline" onClick={schliessen} disabled={sendet}>
                                    <X className="h-4 w-4" aria-hidden="true" /> {ungespeichert ? 'Verwerfen' : 'Schließen'}
                                </Button>
                                <Button size="sm" onClick={() => void speichern()} disabled={sendet}>
                                    {sendet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Save className="h-4 w-4" aria-hidden="true" />}
                                    {sichtbarerEntwurf.id === undefined ? 'Leistung anlegen' : 'Speichern'}
                                </Button>
                            </div>
                        </div>

                        {fehler && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{fehler}</p>}

                        <div className="grid gap-4 xl:grid-cols-2">
                            <div>
                                <Label htmlFor="leistung-name">Name *</Label>
                                <Input id="leistung-name" value={sichtbarerEntwurf.name} onChange={(e) => setze({ name: e.target.value })}
                                    placeholder="z. B. Geländer Edelstahl, Rundrohr" maxLength={255} />
                            </div>
                            <div>
                                <Label htmlFor="leistung-ordner">Ordner *</Label>
                                <Select id="leistung-ordner" value={sichtbarerEntwurf.ordnerId} onChange={(v) => setze({ ordnerId: v })}
                                    options={blattOrdner} placeholder="Ordner wählen" aria-label="Ordner" />
                            </div>
                            <DecimalInput label="Preis in € *" value={sichtbarerEntwurf.preis} onChange={(v) => setze({ preis: v })}
                                min={0} error={preisFehler} placeholder="z. B. 189,50" />
                            <div>
                                <Label htmlFor="leistung-einheit">Einheit</Label>
                                <Select id="leistung-einheit" value={sichtbarerEntwurf.einheit} onChange={(v) => setze({ einheit: v })}
                                    options={EINHEITEN} aria-label="Einheit" />
                            </div>
                        </div>

                        <div className="space-y-1">
                            <Label>Beschreibung * <span className="font-normal text-slate-500">– steht so im Angebot</span></Label>
                            <TiptapEditor value={sichtbarerEntwurf.beschreibung} onChange={(v) => setze({ beschreibung: v })} />
                        </div>

                        {sichtbarerEntwurf.id !== undefined && (
                            <div className="flex justify-end border-t border-slate-100 pt-3">
                                <Button size="sm" variant="ghost" className="text-red-700 hover:bg-red-50"
                                    onClick={() => { const l = leistungen.find((x) => x.id === sichtbarerEntwurf.id); if (l) props.onLoeschen(l); }}>
                                    <Trash2 className="h-4 w-4" aria-hidden="true" /> Leistung löschen …
                                </Button>
                            </div>
                        )}
                    </div>
                )}
            </section>
        </div>
    );
}
