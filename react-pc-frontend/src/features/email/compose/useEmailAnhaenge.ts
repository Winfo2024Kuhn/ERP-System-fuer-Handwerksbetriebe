import { useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { useToast } from '../../../components/ui/toast';
import type { ProjektDokument } from '../../../types';
import { komprimiereBildFuerEmail, komprimiereBilderFuerEmail } from '../../../lib/bildKomprimierung';
import { ANHANG_ZU_GROSS, MAX_ATTACHMENT_BYTES, formatFileSize, neuerAnhang, type UploadedFile } from './anhangGrenze';

interface AnhangOptionen {
    uploadedFiles: UploadedFile[];
    setUploadedFiles: Dispatch<SetStateAction<UploadedFile[]>>;
    /** Größe der mitgesendeten Anhänge der Original-Mail (Weiterleitung). */
    weiterleitungsBytes: number;
    markDirty: () => void;
    setError: (meldung: string | null) => void;
}

const summe = (dateien: UploadedFile[], start: number) => dateien.reduce((s, eintrag) => s + eintrag.file.size, start);

/**
 * Eigene Anhänge des Formulars: hochladen (Fotos vorher verkleinern), Dateien
 * aus Projekt/Anfrage an- und abwählen, entfernen, Größengrenze für T-Online.
 * Die Liste selbst liegt beim Formular, weil auch Entwurf und Versand sie brauchen.
 */
export function useEmailAnhaenge({ uploadedFiles, setUploadedFiles, weiterleitungsBytes, markDirty, setError }: AnhangOptionen) {
    // Verkleinern großer Fotos dauert je nach Anzahl eine Sekunde. Ohne Rückmeldung
    // wirkt das Formular in der Zeit, als wäre der Anhang verschluckt worden.
    const [komprimiere, setKomprimiere] = useState(false);
    const [ladendeDokumentIds, setLadendeDokumentIds] = useState<Set<number>>(new Set());
    const toast = useToast();
    const listeRef = useRef(uploadedFiles);
    useEffect(() => { listeRef.current = uploadedFiles; }, [uploadedFiles]);
    /** Fehler am Formular und als Toast. */
    const melde = (meldung: string) => {
        setError(meldung);
        toast.error(meldung);
    };

    // Von außen mitgegebene Anhänge ebenfalls verkleinern: Die Regel „Fotos gehen nicht
    // im Original raus“ gilt so für das Formular als Ganzes. Bereits verkleinerte
    // Dateien erkennt komprimiereBilderFuerEmail und lässt sie in Ruhe.
    useEffect(() => {
        let abgebrochen = false;
        const originale = uploadedFiles.map(eintrag => eintrag.file);
        if (originale.length === 0) return;
        void komprimiereBilderFuerEmail(originale).then(verkleinert => {
            const hatSichGeaendert = verkleinert.some((datei, i) => datei !== originale[i]);
            if (abgebrochen || !hatSichGeaendert) return;
            setUploadedFiles(vorher => vorher.map((eintrag, i) => (
                originale[i] === eintrag.file ? { ...eintrag, file: verkleinert[i] } : eintrag
            )));
        });
        return () => { abgebrochen = true; };
        // Absichtlich nur beim Öffnen: Später hinzugefügte Anhänge sind an ihrer Quelle
        // schon verkleinert, ein Lauf bei jeder Änderung würde sich selbst wieder auslösen.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const bytes = useMemo(() => summe(uploadedFiles, weiterleitungsBytes), [uploadedFiles, weiterleitungsBytes]);
    const gewaehlteDokumentIds = useMemo(
        () => new Set(uploadedFiles.flatMap(eintrag => (eintrag.sourceDokumentId ? [eintrag.sourceDokumentId] : []))),
        [uploadedFiles],
    );

    const hochladen = async (dateien: FileList | null) => {
        if (!dateien || dateien.length === 0) return;
        setKomprimiere(true);
        let verkleinert: File[];
        try {
            verkleinert = await komprimiereBilderFuerEmail(Array.from(dateien));
        } finally {
            setKomprimiere(false);
        }
        // Grenze gegen die aktuelle Liste prüfen (nach dem Verkleinern kann sie sich geändert haben).
        const angenommen: UploadedFile[] = [];
        let groesse = summe(listeRef.current, weiterleitungsBytes);
        let zuGross = false;
        for (const file of verkleinert) {
            if (groesse + file.size > MAX_ATTACHMENT_BYTES) {
                zuGross = true;
                continue;
            }
            angenommen.push(neuerAnhang(file));
            groesse += file.size;
        }
        if (zuGross) melde(ANHANG_ZU_GROSS);
        if (angenommen.length === 0) return;
        if (!zuGross) setError(null);
        markDirty();
        setUploadedFiles(vorher => [...vorher, ...angenommen]);
    };

    const ladeDokument = async (dokument: ProjektDokument) => {
        const downloadUrl = dokument.url + (dokument.url.includes('?') ? '&' : '?') + 'download=true';
        const response = await fetch(downloadUrl);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const blob = await response.blob();
        return komprimiereBildFuerEmail(new File(
            [blob], dokument.originalDateiname, { type: blob.type || dokument.dateityp || 'application/octet-stream' },
        ));
    };

    const dokumentUmschalten = async (dokument: ProjektDokument) => {
        if (gewaehlteDokumentIds.has(dokument.id)) {
            setUploadedFiles(vorher => vorher.filter(eintrag => eintrag.sourceDokumentId !== dokument.id));
            markDirty();
            return;
        }
        setLadendeDokumentIds(vorher => new Set(vorher).add(dokument.id));
        setError(null);
        try {
            const file = await ladeDokument(dokument);
            if (summe(listeRef.current, weiterleitungsBytes) + file.size > MAX_ATTACHMENT_BYTES) {
                melde(`"${dokument.originalDateiname}" passt nicht mehr in das Anhangslimit von ${formatFileSize(MAX_ATTACHMENT_BYTES)}.`);
                return;
            }
            markDirty();
            setUploadedFiles(vorher => [...vorher, neuerAnhang(file, dokument.id)]);
        } catch (err) {
            console.error('Datei konnte nicht als E-Mail-Anhang geladen werden:', err);
            melde(`"${dokument.originalDateiname}" konnte nicht angehängt werden.`);
        } finally {
            setLadendeDokumentIds(vorher => {
                const naechste = new Set(vorher);
                naechste.delete(dokument.id);
                return naechste;
            });
        }
    };

    const entfernen = (id: string) => {
        setUploadedFiles(vorher => vorher.filter(eintrag => eintrag.id !== id));
        markDirty();
    };

    return {
        komprimiere, ladendeDokumentIds, gewaehlteDokumentIds, bytes,
        limitUeberschritten: bytes > MAX_ATTACHMENT_BYTES,
        hochladen, dokumentUmschalten, entfernen,
    };
}

export type EmailAnhaenge = ReturnType<typeof useEmailAnhaenge>;
