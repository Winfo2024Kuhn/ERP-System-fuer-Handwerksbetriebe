import { useCallback, useEffect, useMemo, useState } from 'react';
import { Check, ImageOff, Loader2, Pencil, X } from 'lucide-react';
import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import { ladeProjektBilder } from '../api';
import { BildEditorModal } from '../BildEditorModal';
import { BildUploadFeld } from '../BildUploadFeld';
import { eintragAusDatei } from '../bildUpload';
import { STANDARD_BEARBEITUNG, type Bildbearbeitung } from '../bildbearbeitung';
import type { ProjektBild } from '../typen';

export interface GewaehltesBild {
    bild: ProjektBild;
    bearbeitung: Bildbearbeitung;
}

export interface SchrittBilderProps {
    projektId: number;
    auswahl: GewaehltesBild[];
    onAuswahlAendern: (auswahl: GewaehltesBild[]) => void;
}

/**
 * Zweiter Schritt des Assistenten. Zeigt alle Bilder des Projekts aus
 * Bautagebuch und Projektdokumenten und laesst mehrere auswaehlen. Zusaetzlich
 * koennen Bilder von der Festplatte hochgeladen werden; sie landen in
 * derselben Auswahl und lassen sich genauso bearbeiten.
 *
 * Die Reihenfolge der Auswahl ist zugleich die Reihenfolge auf der Website:
 * die Website vergibt beim Hochladen sortOrder = images.length, und der
 * Assistent uebertraegt die Bilder in dieser Reihenfolge nacheinander.
 */
export function SchrittBilder({ projektId, auswahl, onAuswahlAendern }: SchrittBilderProps) {
    const [bilder, setBilder] = useState<ProjektBild[]>([]);
    const [laedt, setLaedt] = useState(true);
    const [fehler, setFehler] = useState<string | null>(null);
    const [inBearbeitung, setInBearbeitung] = useState<GewaehltesBild | null>(null);

    useEffect(() => {
        let abgebrochen = false;
        // Bewusst synchron im Effekt: setzt "laedt" beim Wechsel von projektId
        // zurueck auf true, damit sofort der Spinner statt der Bilder des alten
        // Projekts erscheint (der Aufruf beim allerersten Rendern ist wirkungslos,
        // da laedt dann schon true ist).
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLaedt(true);
        ladeProjektBilder(projektId)
            .then(gefunden => { if (!abgebrochen) setBilder(gefunden); })
            .catch(() => { if (!abgebrochen) setFehler('Die Bilder konnten nicht geladen werden.'); })
            .finally(() => { if (!abgebrochen) setLaedt(false); });
        return () => { abgebrochen = true; };
    }, [projektId]);

    const gewaehlteSchluessel = useMemo(
        () => new Set(auswahl.map(a => a.bild.schluessel)), [auswahl]);

    const umschalten = useCallback((bild: ProjektBild) => {
        if (gewaehlteSchluessel.has(bild.schluessel)) {
            onAuswahlAendern(auswahl.filter(a => a.bild.schluessel !== bild.schluessel));
        } else {
            onAuswahlAendern([...auswahl, { bild, bearbeitung: STANDARD_BEARBEITUNG }]);
        }
    }, [auswahl, gewaehlteSchluessel, onAuswahlAendern]);

    const dateienHinzufuegen = (dateien: File[]) => {
        onAuswahlAendern([...auswahl, ...dateien.map(eintragAusDatei)]);
    };

    const uploadEntfernen = (eintrag: GewaehltesBild) => {
        URL.revokeObjectURL(eintrag.bild.url);
        onAuswahlAendern(auswahl.filter(a => a.bild.schluessel !== eintrag.bild.schluessel));
    };

    const bearbeitungUebernehmen = (bearbeitung: Bildbearbeitung) => {
        if (!inBearbeitung) return;
        onAuswahlAendern(auswahl.map(a =>
            a.bild.schluessel === inBearbeitung.bild.schluessel ? { ...a, bearbeitung } : a));
        setInBearbeitung(null);
    };

    const ausBautagebuch = bilder.filter(b => b.quelle === 'bautagebuch');
    const ausDokumenten = bilder.filter(b => b.quelle === 'dokument');
    const eigene = auswahl.filter(a => a.bild.quelle === 'upload');

    // Die Ablagefläche bleibt in jedem Zustand sichtbar: auch ohne Projektbilder
    // oder bei einem Ladefehler kann man Bilder vom Computer nehmen.
    return (
        <div className="space-y-6">
            <BildUploadFeld onDateien={dateienHinzufuegen} kompakt={eigene.length > 0} />

            <p className="text-sm text-slate-600">
                {auswahl.length === 0
                    ? 'Noch kein Bild ausgewählt.'
                    : `${auswahl.length} ${auswahl.length === 1 ? 'Bild' : 'Bilder'} ausgewählt. Die Reihenfolge der Auswahl ist die Reihenfolge auf der Website.`}
            </p>

            {eigene.length > 0 && (
                <div>
                    <h3 className="font-semibold text-slate-900 mb-3">
                        Vom Computer <span className="font-normal text-slate-500">({eigene.length})</span>
                    </h3>
                    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                        {eigene.map(eintrag => (
                            <div key={eintrag.bild.schluessel} className="relative">
                                <img
                                    src={toSafeResourceUrl(eintrag.bild.url)}
                                    alt={eintrag.bild.originalDateiname}
                                    className="w-full h-28 object-cover rounded-lg border-2 border-rose-600 bg-slate-100"
                                />
                                <button
                                    type="button"
                                    aria-label={`${eintrag.bild.originalDateiname} bearbeiten`}
                                    title="Bild bearbeiten"
                                    onClick={() => setInBearbeitung(eintrag)}
                                    className="absolute top-1.5 right-9 w-6 h-6 rounded-full bg-white text-slate-700 shadow flex items-center justify-center hover:bg-slate-100 cursor-pointer"
                                >
                                    <Pencil className="w-3.5 h-3.5" />
                                </button>
                                <button
                                    type="button"
                                    aria-label={`${eintrag.bild.originalDateiname} entfernen`}
                                    title="Entfernen"
                                    onClick={() => uploadEntfernen(eintrag)}
                                    className="absolute top-1.5 right-1.5 w-6 h-6 rounded-full bg-white text-slate-700 shadow flex items-center justify-center hover:bg-rose-50 hover:text-rose-700 cursor-pointer"
                                >
                                    <X className="w-3.5 h-3.5" />
                                </button>
                                <p className="mt-1 text-xs text-slate-500 truncate" title={eintrag.bild.originalDateiname}>
                                    {eintrag.bild.originalDateiname}
                                </p>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {laedt ? (
                <div className="flex items-center justify-center py-10 text-slate-500">
                    <Loader2 className="w-6 h-6 animate-spin mr-2" />
                    Bilder werden geladen...
                </div>
            ) : fehler ? (
                <p className="text-rose-700 py-4">{fehler}</p>
            ) : bilder.length === 0 ? (
                <div className="text-center py-8 text-slate-500">
                    <ImageOff className="w-10 h-10 mx-auto mb-3 opacity-40" />
                    <p className="text-slate-700 font-medium">Zu diesem Projekt gibt es noch keine Bilder.</p>
                    <p className="mt-1 text-sm">
                        Bilder entstehen im Bautagebuch des Projekts oder werden dort unter Dateien hochgeladen.
                    </p>
                </div>
            ) : (
                <>
            <Gruppe
                titel="Aus dem Bautagebuch"
                bilder={ausBautagebuch}
                auswahl={auswahl}
                gewaehlteSchluessel={gewaehlteSchluessel}
                onUmschalten={umschalten}
                onBearbeiten={setInBearbeitung}
            />
            <Gruppe
                titel="Aus den Projektdokumenten"
                bilder={ausDokumenten}
                auswahl={auswahl}
                gewaehlteSchluessel={gewaehlteSchluessel}
                onUmschalten={umschalten}
                onBearbeiten={setInBearbeitung}
            />
                </>
            )}

            <BildEditorModal
                offen={inBearbeitung !== null}
                bildUrl={inBearbeitung?.bild.url ?? ''}
                startBearbeitung={inBearbeitung?.bearbeitung}
                onAbbrechen={() => setInBearbeitung(null)}
                onUebernehmen={bearbeitungUebernehmen}
            />
        </div>
    );
}

function Gruppe({ titel, bilder, auswahl, gewaehlteSchluessel, onUmschalten, onBearbeiten }: {
    titel: string;
    bilder: ProjektBild[];
    auswahl: GewaehltesBild[];
    gewaehlteSchluessel: Set<string>;
    onUmschalten: (bild: ProjektBild) => void;
    onBearbeiten: (gewaehlt: GewaehltesBild) => void;
}) {
    return (
        <div>
            <h3 className="font-semibold text-slate-900 mb-3">
                {titel} <span className="font-normal text-slate-400">({bilder.length})</span>
            </h3>
            {bilder.length === 0 ? (
                <p className="text-sm text-slate-400">Hier liegt nichts.</p>
            ) : (
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                    {bilder.map(bild => {
                        const gewaehlt = gewaehlteSchluessel.has(bild.schluessel);
                        const eintrag = auswahl.find(a => a.bild.schluessel === bild.schluessel);
                        return (
                            <div key={bild.schluessel} className="relative group">
                                <button
                                    type="button"
                                    onClick={() => onUmschalten(bild)}
                                    className={`block w-full rounded-lg overflow-hidden border-2 transition-colors cursor-pointer
                                        ${gewaehlt ? 'border-rose-600' : 'border-transparent hover:border-slate-300'}`}
                                >
                                    <img
                                        src={bild.thumbnailUrl || bild.url}
                                        alt={bild.originalDateiname}
                                        className="w-full h-28 object-cover bg-slate-100"
                                        loading="lazy"
                                    />
                                </button>

                                {gewaehlt && (
                                    <>
                                        <span className="absolute top-1.5 left-1.5 w-6 h-6 rounded-full bg-rose-600 text-white flex items-center justify-center">
                                            <Check className="w-4 h-4" />
                                        </span>
                                        <button
                                            type="button"
                                            aria-label={`${bild.originalDateiname} bearbeiten`}
                                            title="Bild bearbeiten"
                                            onClick={() => eintrag && onBearbeiten(eintrag)}
                                            className="absolute top-1.5 right-1.5 w-6 h-6 rounded-full bg-white/90 text-slate-700 flex items-center justify-center hover:bg-white cursor-pointer"
                                        >
                                            <Pencil className="w-3.5 h-3.5" />
                                        </button>
                                    </>
                                )}

                                {bild.hinweis && (
                                    <p className="mt-1 text-xs text-slate-500 line-clamp-2" title={bild.hinweis}>
                                        {bild.hinweis}
                                    </p>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
