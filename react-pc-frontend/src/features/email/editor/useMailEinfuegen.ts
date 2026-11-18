import { useCallback, useRef, type ClipboardEvent, type DragEvent, type RefObject } from 'react';
import { useToast } from '../../../components/ui/toast';
import { fuegeHtmlEin, stelleAmPunkt } from './mailEditorDom';
import {
    KEINE_DATEIEN_MELDUNG, anhangMeldung, benenneDateien, bereiteEinfuegenVor, hatSichtbarenInhalt, nichtUebernommenMeldungen,
} from './mailEinfuegen';
import type { MailAuswahl } from './useMailAuswahl';

/**
 * Nimmt eingefügte bzw. hineingezogene Dateien an. Liefert die Zahl der
 * angenommenen Dateien (Grenzen prüft der Aufrufer), sonst gelten alle als angenommen.
 */
export type MailDateienAnnehmen = (dateien: File[]) => unknown;

interface Optionen {
    editorRef: RefObject<HTMLDivElement | null>;
    auswahl: MailAuswahl;
    meldeAenderung: () => void;
    /** Ohne: Dateien und Bilder werden abgelehnt (Hinweis), Text kommt trotzdem ins Feld. */
    onDateien?: MailDateienAnnehmen;
    /** Bisheriges Einfügen einer Adresse als Link (`useMailLinks`). */
    adresseEinfuegen: (event: ClipboardEvent<HTMLDivElement>) => void;
}

function dateienAus(daten: DataTransfer): File[] {
    const dateien = Array.from(daten.files ?? []);
    if (dateien.length > 0) return dateien;
    return Array.from(daten.items ?? [])
        .filter(eintrag => eintrag.kind === 'file')
        .map(eintrag => eintrag.getAsFile())
        .filter((datei): datei is File => datei !== null);
}

/**
 * Einfügen und Ablegen im Mail-Textfeld: Dateien und Bilder kommen nie in den
 * Text, sondern über `onDateien` in die Anhangsliste. Eingefügtes HTML (Word,
 * Excel, Webseiten) wird bereinigt eingesetzt, Tabellen und Formatierung bleiben.
 * Reiner Text und eingefügte Adressen laufen wie bisher.
 */
export function useMailEinfuegen({ editorRef, auswahl, meldeAenderung, onDateien, adresseEinfuegen }: Optionen) {
    const toast = useToast();
    const nummer = useRef(0);
    /** Ziehen innerhalb des Felds (Text verschieben) bleibt beim Browser. */
    const ziehtImFeld = useRef(false);
    const vergibNummer = useCallback(() => { nummer.current += 1; return nummer.current; }, []);

    const haengeAn = useCallback(async (dateien: File[]) => {
        if (dateien.length === 0) return;
        if (!onDateien) {
            toast.warning(KEINE_DATEIEN_MELDUNG);
            return;
        }
        try {
            const angenommen = await onDateien(dateien);
            const anzahl = typeof angenommen === 'number' ? angenommen : dateien.length;
            if (anzahl > 0) toast.success(anhangMeldung(dateien.slice(0, anzahl)));
        } catch (err) {
            console.error('Eingefügte Dateien konnten nicht angehängt werden:', err);
            toast.error('Die Dateien konnten nicht angehängt werden.');
        }
    }, [onDateien, toast]);

    /** Verarbeitet Zwischenablage bzw. abgelegte Daten. `true`, wenn der Browser nichts mehr tun soll. */
    const verarbeite = useCallback((daten: DataTransfer, stelle: Range | null): boolean => {
        const editor = editorRef.current;
        if (!editor) return false;
        const dateien = benenneDateien(dateienAus(daten), vergibNummer);
        const html = daten.getData('text/html');
        if (dateien.length === 0 && !html) return false;
        const bilder = dateien.filter(datei => datei.type.startsWith('image/')).length;
        const ergebnis = html ? bereiteEinfuegenVor(html, { vergibNummer, gedeckteBilder: bilder }) : null;
        const sichtbar = !!ergebnis && hatSichtbarenInhalt(ergebnis.html);
        const hinweise = ergebnis ? nichtUebernommenMeldungen(ergebnis.nichtUebernommen) : [];
        const anhaenge = [...dateien, ...(ergebnis?.dateien ?? [])];
        // Nichts zu tun (leeres HTML ohne Bilder): normales Einfügen.
        if (!sichtbar && anhaenge.length === 0 && hinweise.length === 0) return false;
        const ziel = stelle ?? auswahl.aktuelle();
        if (ergebnis && sichtbar && ziel) {
            fuegeHtmlEin(editor, ziel, ergebnis.html);
            auswahl.merken();
            meldeAenderung();
        }
        void haengeAn(anhaenge);
        hinweise.forEach(meldung => toast.warning(meldung));
        return true;
    }, [editorRef, vergibNummer, auswahl, meldeAenderung, haengeAn, toast]);

    const beimEinfuegen = useCallback((event: ClipboardEvent<HTMLDivElement>) => {
        const daten = event.clipboardData;
        if (!daten) return;
        if (dateienAus(daten).length === 0) {
            adresseEinfuegen(event);
            if (event.defaultPrevented) return;
        }
        if (verarbeite(daten, null)) event.preventDefault();
    }, [adresseEinfuegen, verarbeite]);

    const beimZiehenUeber = useCallback((event: DragEvent<HTMLDivElement>) => {
        if (ziehtImFeld.current || !Array.from(event.dataTransfer?.types ?? []).includes('Files')) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = 'copy';
    }, []);

    const beimAblegen = useCallback((event: DragEvent<HTMLDivElement>) => {
        const editor = editorRef.current;
        if (ziehtImFeld.current || !editor || !event.dataTransfer) {
            ziehtImFeld.current = false;
            return;
        }
        if (verarbeite(event.dataTransfer, stelleAmPunkt(editor, event.clientX, event.clientY))) event.preventDefault();
    }, [editorRef, verarbeite]);

    return {
        onPaste: beimEinfuegen,
        onDragOver: beimZiehenUeber,
        onDrop: beimAblegen,
        onDragStart: () => { ziehtImFeld.current = true; },
        onDragEnd: () => { ziehtImFeld.current = false; },
    };
}
