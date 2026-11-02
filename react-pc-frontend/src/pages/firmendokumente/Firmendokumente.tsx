import { useRef, useState } from 'react';
import { Check, Search, Upload, X } from 'lucide-react';
import DocumentPreviewModal from '../../components/DocumentPreviewModal';
import { PageLayout } from '../../components/layout/PageLayout';
import { Input } from '../../components/ui/input';
import { LadeAnzeige, VerbindungsHinweis } from '../../components/ui/lade-anzeige';
import { useToast } from '../../components/ui/toast';
import { toSafeResourceUrl } from '../../lib/htmlSanitizer';
import { dateiUrl, ladeHoch, speichereDokument } from './api';
import { DokumentDialog } from './DokumentDialog';
import { DokumentOrdnerBaum } from './DokumentOrdnerBaum';
import { MAX_SUCHE, MIN_SUCHE, nameAusDateiname, ordnerOptionen } from './firmendokumenteLogik';
import { SeitenInfo } from './InfoTexte';
import { OrdnerDialoge, type OrdnerDialog } from './OrdnerDialoge';
import { KeineOrdner, RechterBereich } from './RechterBereich';
import { useAnsicht } from './useAnsicht';
import { useDokumentAktionen } from './useDokumentAktionen';
import { useFirmenOrdner } from './useFirmenOrdner';
import { useHochladen } from './useHochladen';
import { useOrdnerLoeschen } from './useOrdnerLoeschen';

/**
 * Seite „Firmendokumente“: Firmen-PDFs (Prüfungen, Arbeitsschutz, Versicherungen …)
 * in Ordnern ablegen, wiederfinden, ansehen und drucken. Für jeden PC-Nutzer.
 */
export default function Firmendokumente() {
    const toast = useToast();
    const ordnerStand = useFirmenOrdner(toast.error);
    const { baum } = ordnerStand;
    const ansichtStand = useAnsicht(baum, ordnerStand.ordner !== null, toast.error);
    const { ansicht, gewaehlt, liste, waehle } = ansichtStand;
    const [ordnerDialog, setOrdnerDialog] = useState<OrdnerDialog | null>(null);
    const dateiAuswahl = useRef<HTMLInputElement>(null);
    const hochladen = useHochladen(toast.error);
    const dokAktionen = useDokumentAktionen({
        // Neu laden statt nur lokal herausnehmen: sonst fehlt beim späteren „Mehr laden“ ein Dokument.
        onGeloescht: () => { liste.neuLaden(); ordnerStand.neuLaden(); },
        imOrdnerZeigen: ansicht.art === 'ordner' ? undefined : (dok) => waehle(dok.ordnerId),
    });
    const ordnerLoeschen = useOrdnerLoeschen({ baum, gewaehlt, onGeloescht: (zurueck) => { ordnerStand.neuLaden(); waehle(zurueck); } });
    const optionen = ordnerOptionen(baum);
    const ordnerFehlen = ordnerStand.ordner !== null && ordnerStand.ordner.length === 0;
    const upload = hochladen.aktuelle;
    const bearbeitet = dokAktionen.bearbeiten;
    const vorschau = dokAktionen.vorschau;

    const nachAenderung = () => {
        ordnerStand.neuLaden();
        liste.neuLaden();
    };

    return (
        <PageLayout
            ribbonCategory="Stammdaten"
            title="Firmendokumente"
            titelInfo={<SeitenInfo />}
            subtitle="Prüfberichte, Gefährdungsbeurteilungen, Versicherungen und andere Firmen-PDFs an einem Ort."
            actions={<SuchFeld wert={ansichtStand.suchtext} onWert={ansichtStand.setSuchtext} />}
        >
            {ordnerStand.ordner === null ? (
                ordnerStand.fehler
                    ? <div className="py-16"><VerbindungsHinweis text={ordnerStand.fehler} onErneut={ordnerStand.neuLaden} /></div>
                    : <LadeAnzeige text="Ordner werden geladen …" onErneut={ordnerStand.neuLaden} />
            ) : (
                <div className="grid gap-6 lg:grid-cols-[18rem_minmax(0,1fr)]">
                    <div className="self-start rounded-lg border border-slate-200 bg-white shadow-sm lg:sticky lg:top-4 lg:flex lg:max-h-[calc(100vh-12rem)] lg:flex-col">
                        <DokumentOrdnerBaum
                            baum={baum}
                            gewaehlt={gewaehlt}
                            ueberfaellig={ordnerStand.ueberfaellig}
                            onWaehlen={waehle}
                            onNeuerOrdner={() => setOrdnerDialog({ art: 'anlegen', eltern: null })}
                            onAktion={(aktion, ordner) => {
                                if (aktion === 'loeschen') ordnerLoeschen(ordner);
                                else setOrdnerDialog(aktion === 'unterordner' ? { art: 'anlegen', eltern: ordner } : { art: aktion, ordner });
                            }}
                            onDateienAbgelegt={(ordnerId, dateien) => hochladen.annehmen(dateien, ordnerId)}
                        />
                    </div>
                    <div className="min-w-0 rounded-lg border border-slate-200 bg-white p-4 shadow-sm md:p-6">
                        {ordnerFehlen && ansicht.art !== 'suche'
                            ? <KeineOrdner onNeuerOrdner={() => setOrdnerDialog({ art: 'anlegen', eltern: null })} />
                            : (
                                <RechterBereich
                                    ansicht={ansicht}
                                    baum={baum}
                                    liste={liste}
                                    aktionen={dokAktionen.aktionen}
                                    onHochladen={() => dateiAuswahl.current?.click()}
                                    onDateienAbgelegt={(ordnerId, dateien) => hochladen.annehmen(dateien, ordnerId)}
                                    onOrdnerWaehlen={waehle}
                                />
                            )}
                    </div>
                </div>
            )}

            <input
                ref={dateiAuswahl}
                type="file"
                accept=".pdf,application/pdf"
                multiple
                hidden
                aria-label="PDF-Dateien auswählen"
                onChange={(e) => {
                    if (gewaehlt !== null) hochladen.annehmen(e.target.files, gewaehlt);
                    e.target.value = '';
                }}
            />

            {upload && (
                <DokumentDialog
                    key={`${upload.nummer}-${upload.datei.name}`}
                    titel={upload.anzahl > 1 ? `PDF hochladen (${upload.nummer} von ${upload.anzahl})` : 'PDF hochladen'}
                    beschreibung={`Datei: ${upload.datei.name}`}
                    start={{ name: nameAusDateiname(upload.datei.name), ordnerId: upload.ordnerId, faelligAm: null }}
                    ordnerOptionen={optionen}
                    knopfText="Hochladen"
                    knopfSymbol={<Upload className="h-4 w-4" aria-hidden="true" />}
                    onAbbrechen={hochladen.abbrechen}
                    onSpeichern={(angaben) => ladeHoch(upload.datei, angaben).then((dok) => {
                        toast.success(`„${dok.name}“ abgelegt.`);
                        nachAenderung();
                        hochladen.weiter(dok.ordnerId);
                    })}
                />
            )}

            {bearbeitet && (
                <DokumentDialog
                    titel="Dokument bearbeiten"
                    beschreibung={`Datei: ${bearbeitet.originalDateiname}`}
                    start={{ name: bearbeitet.name, ordnerId: bearbeitet.ordnerId, faelligAm: bearbeitet.faelligAm }}
                    ordnerOptionen={optionen}
                    knopfText="Speichern"
                    knopfSymbol={<Check className="h-4 w-4" aria-hidden="true" />}
                    onAbbrechen={dokAktionen.schliesseBearbeiten}
                    onSpeichern={(angaben) => speichereDokument(bearbeitet.id, angaben).then((dok) => {
                        toast.success(`„${dok.name}“ gespeichert.`);
                        dokAktionen.schliesseBearbeiten();
                        nachAenderung();
                    })}
                />
            )}

            {vorschau && (
                <DocumentPreviewModal
                    doc={{ url: toSafeResourceUrl(dateiUrl(vorschau.id)) ?? '', title: vorschau.name }}
                    isPdf
                    onClose={dokAktionen.schliesseVorschau}
                />
            )}

            <OrdnerDialoge
                dialog={ordnerDialog}
                baum={baum}
                onSchliessen={() => setOrdnerDialog(null)}
                onFertig={(ordner, meldung) => {
                    toast.success(meldung);
                    setOrdnerDialog(null);
                    ordnerStand.neuLaden();
                    if (ordnerDialog?.art === 'anlegen') waehle(ordner.id);
                }}
            />
        </PageLayout>
    );
}

/** Suche über alle Ordner (ab 2 Zeichen). */
function SuchFeld({ wert, onWert }: { wert: string; onWert: (text: string) => void }) {
    const zuKurz = wert.trim().length > 0 && wert.trim().length < MIN_SUCHE;
    return (
        <div className="relative w-full md:w-80">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <Input
                type="search"
                value={wert}
                maxLength={MAX_SUCHE}
                onChange={(e) => onWert(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Escape' && wert) { e.preventDefault(); onWert(''); } }}
                placeholder="Dokumente suchen …"
                aria-label="Dokumente in allen Ordnern suchen"
                aria-describedby={zuKurz ? 'firmendokumente-suche-hinweis' : undefined}
                className="rounded-lg py-2 pl-8 pr-8 [&::-webkit-search-cancel-button]:hidden"
            />
            {wert && (
                <button type="button" onClick={() => onWert('')} aria-label="Suche leeren" title="Suche leeren"
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-0.5 text-slate-400 hover:text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                    <X className="h-4 w-4" aria-hidden="true" />
                </button>
            )}
            {zuKurz && <p id="firmendokumente-suche-hinweis" className="absolute mt-1 text-xs text-slate-500">Bitte mindestens 2 Zeichen eingeben.</p>}
        </div>
    );
}
