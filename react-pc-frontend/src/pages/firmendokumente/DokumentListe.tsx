import type { CSSProperties, ReactNode } from 'react';
import { LadeAnzeige, VerbindungsHinweis } from '../../components/ui/lade-anzeige';
import { MehrLadenLeiste } from '../../components/ui/mehr-laden-leiste';
import { DokumentKacheln, KACHEL_GROESSE_VARIABLE } from './DokumentKacheln';
import { DokumentTabelle } from './DokumentTabelle';
import type { DokumentAktionen } from './DokumentZeilenAktionen';
import type { FirmenDokument, FirmenOrdner } from './typen';
import type { UnterordnerAktionen } from './Unterordner';
import type { Darstellung } from './useDarstellung';

const KEINE_ORDNER: readonly FirmenOrdner[] = [];

export interface DokumentListeProps {
    /** Vorlese-Name der Tabelle bzw. Kachelliste, z. B. „Dokumente in Tore“. */
    beschriftung: string;
    dokumente: FirmenDokument[];
    gesamt: number;
    laedt: boolean;
    laedtMehr: boolean;
    fehler: string | null;
    leer: ReactNode;
    /** Pfad eines Ordners – gesetzt in der Übersicht „Fällige Dokumente“ (Spalte „Ordner“). Die Suche zeigt keine Ordner-Spalte. */
    ordnerPfad?: (ordnerId: number) => string;
    aktionen: DokumentAktionen;
    onMehrLaden: () => void;
    onErneut: () => void;
    /** Liste (Standard) oder Vorschau mit Kacheln in der gewählten Größe. */
    darstellung?: Darstellung;
    /** Unterordner des gewählten Ordners – stehen vor den Dokumenten. Übersicht und Suche zeigen keine. */
    unterordner?: readonly FirmenOrdner[];
    ordnerAktionen?: UnterordnerAktionen;
}

/**
 * Inhalt eines Ordners, der Übersicht oder der Suche: Unterordner und
 * Dokumente als Tabelle oder als Kacheln, unten „Mehr laden“, solange der
 * Server mehr hat. Laden, leer und Fehler sehen verschieden aus; ein Ordner
 * nur mit Unterordnern wirkt nicht leer. Dokumente (und Ordner) mit Recht
 * „Ändern“ lassen sich auf einen Ordner ziehen, um sie zu verschieben.
 */
export function DokumentListe(props: DokumentListeProps) {
    const { beschriftung, dokumente, gesamt, laedt, laedtMehr, fehler, leer, ordnerPfad, aktionen, onMehrLaden, onErneut } = props;
    const { darstellung, ordnerAktionen } = props;
    const unterordner = ordnerAktionen ? props.unterordner ?? KEINE_ORDNER : KEINE_ORDNER;
    const keineDokumente = dokumente.length === 0;

    const zustand = !keineDokumente ? null
        : laedt ? <LadeAnzeige text="Dokumente werden geladen …" onErneut={onErneut} />
            : fehler ? <div className="py-16"><VerbindungsHinweis text={fehler} onErneut={onErneut} /></div>
                : unterordner.length === 0 ? <div className="py-16 text-center text-sm text-slate-500">{leer}</div> : null;
    if (keineDokumente && unterordner.length === 0) return zustand;

    const inhalt = darstellung?.art === 'vorschau'
        ? (
            <div data-kacheln style={{ [KACHEL_GROESSE_VARIABLE]: `${darstellung.groesse}px` } as CSSProperties}>
                <DokumentKacheln beschriftung={beschriftung} dokumente={dokumente} aktionen={aktionen} ordnerPfad={ordnerPfad}
                    unterordner={unterordner} ordnerAktionen={ordnerAktionen} />
            </div>
        )
        : (
            <DokumentTabelle beschriftung={beschriftung} dokumente={dokumente} laedt={laedt} ordnerPfad={ordnerPfad} aktionen={aktionen}
                unterordner={unterordner} ordnerAktionen={ordnerAktionen} />
        );

    return (
        <div className="space-y-3">
            {inhalt}
            {zustand}
            {!keineDokumente && <MehrLadenLeiste angezeigt={dokumente.length} gesamt={gesamt} laedtMehr={laedtMehr} onMehrLaden={onMehrLaden} />}
        </div>
    );
}
