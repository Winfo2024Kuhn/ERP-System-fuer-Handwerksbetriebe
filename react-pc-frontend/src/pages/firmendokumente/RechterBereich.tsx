import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { ChevronRight, Eye, FolderPlus, Upload } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { kinderVon, ordnerKette, type Baum } from '../../lib/ordnerBaum';
import { cn } from '../../lib/utils';
import { AnsichtLeiste } from './AnsichtLeiste';
import { DokumentListe } from './DokumentListe';
import type { DokumentAktionen } from './DokumentZeilenAktionen';
import { ordnerPfadOderStrich, ziehtDateien } from './firmendokumenteLogik';
import { HochladenFortschritt } from './HochladenFortschritt';
import { HochladenInfo } from './InfoTexte';
import type { FirmenDokument, FirmenOrdner } from './typen';
import type { AblageRegeln } from './useAblageZiel';
import { useDarstellung } from './useDarstellung';
import type { StapelStand } from './useStapelHochladen';

/** Was rechts angezeigt wird. */
export type Ansicht =
    | { art: 'uebersicht' }
    | { art: 'ordner'; ordnerId: number }
    | { art: 'suche'; suchbegriff: string };

export interface ListenStand {
    eintraege: FirmenDokument[];
    gesamt: number;
    laedt: boolean;
    laedtMehr: boolean;
    fehler: string | null;
    mehrLaden: () => void;
    neuLaden: () => void;
}

export interface RechterBereichProps {
    ansicht: Ansicht;
    baum: Baum<FirmenOrdner>;
    liste: ListenStand;
    aktionen: DokumentAktionen;
    onHochladen: () => void;
    onOrdnerWaehlen: (id: number | null) => void;
    /** Ablegen auf dem Ordner und seinen Unterordnern – dieselben Regeln wie im Baum. */
    ablage: AblageRegeln;
    /** Fortschritt, solange mehrere Dateien ohne Dialog hochgehen. */
    stapel: StapelStand | null;
    /** „Abbrechen“ in der Fortschrittsleiste. */
    onStapelAbbrechen: () => void;
}

const WARTEN_TEXT = 'Bitte warten, bis die laufenden Dateien hochgeladen sind.';

/**
 * Rechte Seite: fällige Dokumente (Übersicht), Inhalt eines Ordners (erst
 * Unterordner, dann Dokumente) oder Suchtreffer – als Liste oder Vorschau.
 * Die Darstellung liegt hier und nicht in der Seite: Beim Ziehen am Regler
 * rendert nur dieser Bereich neu.
 */
export function RechterBereich({ ansicht, baum, liste, aktionen, onHochladen, onOrdnerWaehlen, ablage, stapel, onStapelAbbrechen }: RechterBereichProps) {
    const darstellung = useDarstellung();
    const pfad = useCallback((id: number) => ordnerPfadOderStrich(baum, id), [baum]);
    const ordnerId = ansicht.art === 'ordner' ? ansicht.ordnerId : null;
    // Gleiche Liste solange Baum und Ordner gleich sind – auch bei Blattordnern (sonst je Render ein neues []).
    const unterordner = useMemo(() => (ordnerId === null ? undefined : kinderVon(baum, ordnerId)), [baum, ordnerId]);
    const ordnerAktionen = useMemo(() => ({ onOeffnen: onOrdnerWaehlen, ablage }), [onOrdnerWaehlen, ablage]);
    const gemeinsam = {
        dokumente: liste.eintraege, gesamt: liste.gesamt, laedt: liste.laedt, laedtMehr: liste.laedtMehr, fehler: liste.fehler,
        onMehrLaden: liste.mehrLaden, onErneut: liste.neuLaden, aktionen,
        darstellung: { art: darstellung.art, groesse: darstellung.groesse },
    };
    const leiste = (
        <AnsichtLeiste art={darstellung.art} groesse={darstellung.groesse} onArt={darstellung.setArt}
            onGroesse={darstellung.setGroesse} onGroesseFertig={darstellung.groesseMerken} />
    );
    const fortschritt = stapel && <HochladenFortschritt stand={stapel} onAbbrechen={onStapelAbbrechen} />;

    if (ansicht.art === 'suche') {
        return (
            <Bereich titel={`Suche nach „${ansicht.suchbegriff}“`} untertitel="Treffer in allen Ordnern, nach Name sortiert." aktion={leiste} hinweis={fortschritt}>
                <DokumentListe {...gemeinsam} beschriftung="Suchtreffer" leer={`Kein Dokument passt zu „${ansicht.suchbegriff}“.`} />
            </Bereich>
        );
    }

    if (ansicht.art === 'uebersicht') {
        return (
            <Bereich titel="Fällige Dokumente" untertitel="Überfällig oder in den nächsten 30 Tagen fällig, das früheste Datum zuerst." aktion={leiste} hinweis={fortschritt}>
                <DokumentListe {...gemeinsam} beschriftung="Fällige Dokumente" ordnerPfad={pfad}
                    leer="Nichts fällig. Dokumente mit einem Datum unter „Fällig am“ erscheinen hier rechtzeitig vorher." />
            </Bereich>
        );
    }

    const ordner = baum.byId.get(ansicht.ordnerId);
    const kette = ordnerKette(baum, ansicht.ordnerId);
    const darfAendern = ordner?.darfAendern ?? false;
    return (
        <Ablage onDateien={darfAendern ? (dateien) => ablage.onDateien(ansicht.ordnerId, dateien) : undefined}>
            <Bereich
                titel={ordner?.name ?? ''}
                kopfzeile={<Brotkrumen kette={kette} onWaehlen={onOrdnerWaehlen} />}
                untertitel={darfAendern ? 'Dateien hierher ziehen oder über den Knopf auswählen.' : <NurAnsehenHinweis />}
                aktion={<>{leiste}{darfAendern && <HochladenKnopf gesperrt={stapel !== null} onHochladen={onHochladen} />}</>}
                hinweis={fortschritt}
            >
                <DokumentListe {...gemeinsam} beschriftung={`Dokumente in ${ordner?.name ?? 'diesem Ordner'}`}
                    unterordner={unterordner} ordnerAktionen={ordnerAktionen}
                    leer={darfAendern ? 'In diesem Ordner liegt noch kein Dokument. Über „Datei hochladen“ oder per Ziehen ablegen.' : 'In diesem Ordner liegt noch kein Dokument.'} />
            </Bereich>
        </Ablage>
    );
}

/** „Datei hochladen“ mit Info; gesperrt (mit Grund), solange ein Stapel hochgeht. */
function HochladenKnopf({ gesperrt, onHochladen }: { gesperrt: boolean; onHochladen: () => void }) {
    return (
        <div className="flex items-center gap-1">
            <span title={gesperrt ? WARTEN_TEXT : undefined}>
                <Button onClick={onHochladen} disabled={gesperrt}><Upload className="h-4 w-4" aria-hidden="true" />Datei hochladen</Button>
            </span>
            <HochladenInfo />
        </div>
    );
}

/** Ruhiger Hinweis statt Hochladen, wenn der Angemeldete den Ordner nur ansehen darf. */
function NurAnsehenHinweis() {
    return (
        <span className="inline-flex items-center gap-1.5">
            <Eye className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
            Dieser Ordner lässt sich nur ansehen.
        </span>
    );
}

/** Leer-Zustand, solange es gar keine Ordner gibt. */
export function KeineOrdner({ onNeuerOrdner }: { onNeuerOrdner: () => void }) {
    return (
        <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
            <FolderPlus className="h-6 w-6 text-slate-400" aria-hidden="true" />
            <p className="font-medium text-slate-900">Noch keine Ordner.</p>
            <p className="max-w-sm text-sm text-slate-500">Zum Beispiel „Prüfungen“ oder „Arbeitsschutz“. Darin werden die Dokumente abgelegt.</p>
            <Button onClick={onNeuerOrdner}><FolderPlus className="h-4 w-4" aria-hidden="true" />Ersten Ordner anlegen</Button>
        </div>
    );
}

interface BereichProps {
    titel: string;
    untertitel: ReactNode;
    kopfzeile?: ReactNode;
    aktion?: ReactNode;
    /** Zeile unter dem Kopf, z. B. der Fortschritt beim Hochladen. */
    hinweis?: ReactNode;
    children: ReactNode;
}

function Bereich({ titel, untertitel, kopfzeile, aktion, hinweis, children }: BereichProps) {
    return (
        <section aria-label={titel} className="space-y-4">
            <div className="flex flex-wrap items-end justify-between gap-3">
                <div className="min-w-0">
                    {kopfzeile}
                    <h2 className="break-words text-lg font-semibold text-slate-900">{titel}</h2>
                    <p className="text-sm text-slate-500">{untertitel}</p>
                </div>
                <div className="flex flex-wrap items-center justify-end gap-x-3 gap-y-2">{aktion}</div>
            </div>
            {hinweis}
            {children}
        </section>
    );
}

function Brotkrumen({ kette, onWaehlen }: { kette: FirmenOrdner[]; onWaehlen: (id: number | null) => void }) {
    return (
        <nav aria-label="Ordnerpfad" className="mb-0.5">
            <ol className="flex flex-wrap items-center gap-1 text-xs text-slate-500">
                <li><button type="button" onClick={() => onWaehlen(null)} className="rounded hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">Übersicht</button></li>
                {kette.slice(0, -1).map((o) => (
                    <li key={o.id} className="flex items-center gap-1">
                        <ChevronRight className="h-3 w-3" aria-hidden="true" />
                        <button type="button" onClick={() => onWaehlen(o.id)} className="rounded hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">{o.name}</button>
                    </li>
                ))}
            </ol>
        </nav>
    );
}

/**
 * Fläche, auf die man Dateien aus dem Explorer/Finder ziehen kann. Ohne `onDateien` (kein Recht „Ändern“) nimmt sie nichts an.
 * Hat ein Unterordner darin das Ziehen schon angenommen (`defaultPrevented`), hält sie sich heraus.
 */
function Ablage({ onDateien, children }: { onDateien?: (dateien: FileList) => void; children: ReactNode }) {
    const [aktiv, setAktiv] = useState(false);
    return (
        <div
            onDragOver={(e) => {
                if (e.defaultPrevented) { setAktiv(false); return; }
                if (!onDateien || !ziehtDateien(e)) return;
                e.preventDefault();
                e.dataTransfer.dropEffect = 'copy';
                setAktiv(true);
            }}
            onDragLeave={(e) => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setAktiv(false); }}
            onDrop={(e) => {
                if (e.defaultPrevented || !onDateien || !ziehtDateien(e)) return;
                e.preventDefault();
                setAktiv(false);
                onDateien(e.dataTransfer.files);
            }}
            className={cn('-m-2 rounded-lg p-2', aktiv && 'bg-rose-50 ring-2 ring-inset ring-rose-300')}
        >
            {children}
        </div>
    );
}
