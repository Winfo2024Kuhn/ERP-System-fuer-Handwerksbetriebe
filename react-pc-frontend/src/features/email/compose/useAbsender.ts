import { useEffect, useMemo, useState } from 'react';
import { nurWaehlbareAbsender, waehleStandardAbsender } from '../postfach';
import { useAbsenderPostfaecher } from '../useAbsenderPostfaecher';

/**
 * „Senden von“: Bei neuen Mails frei wählbar (eigenes Postfach vorbelegt), bei
 * Antwort/Weiterleitung fest (Postfach der Original-Mail), bei Geschäftsdokumenten
 * fest über das dafür eingerichtete Postfach.
 */
export function useAbsender({ geschaeftsdokument, absenderFest }: { geschaeftsdokument: boolean; absenderFest: boolean }) {
    /** Postfächer für „Senden von“ (eigenes zuerst). */
    const absenderPostfaecher = useAbsenderPostfaecher();
    const [postfachId, setPostfachId] = useState<number | null>(null);
    /** Adresse des eigenen Postfachs für Geschäftsdokumente; leer = es gibt keins. */
    const [dokumentAbsender, setDokumentAbsender] = useState('');
    const modus: 'auswahl' | 'fest' = absenderFest || geschaeftsdokument ? 'fest' : 'auswahl';

    /** Ohne das Postfach für Rechnungen & Mahnungen – das nutzt nur der Dokument-Editor. */
    const waehlbare = useMemo(() => nurWaehlbareAbsender(absenderPostfaecher.postfaecher), [absenderPostfaecher.postfaecher]);
    const gewaehltes = waehlbare.find(p => p.id === postfachId) ?? null;
    const hauptpostfach = absenderPostfaecher.postfaecher.find(p => p.hauptpostfach) ?? null;

    // Vorbelegung: eigenes Postfach, sonst Hauptpostfach, sonst das erste. Ein gespeicherter
    // Entwurf behält sein Postfach, solange es noch wählbar ist. (Zustand beim Rendern anpassen.)
    if (!absenderPostfaecher.laedt && !(postfachId != null && waehlbare.some(p => p.id === postfachId))) {
        const standard = waehleStandardAbsender(waehlbare)?.id ?? null;
        if (standard !== postfachId) setPostfachId(standard);
    }

    // Beim Versand eines Geschäftsdokuments entscheidet das dafür eingerichtete Postfach.
    // Ein wiederhergestellter Entwurf kann das erst nach dem Laden einschalten.
    useEffect(() => {
        if (!geschaeftsdokument) return;
        fetch('/api/email/dokument-absender')
            .then(res => (res.ok ? res.json() : null))
            .then(data => {
                if (data?.aktiv && typeof data.address === 'string' && data.address.trim()) {
                    setDokumentAbsender(data.address.trim());
                }
            })
            .catch(err => {
                // Kein harter Fehler: Dann steht das Hauptpostfach da, der Versand
                // läuft serverseitig trotzdem über das richtige Postfach.
                console.error('Absender für Geschäftsdokumente konnte nicht geladen werden:', err);
            });
    }, [geschaeftsdokument]);

    return { absenderPostfaecher, waehlbare, postfachId, setPostfachId, gewaehltes, hauptpostfach, dokumentAbsender, modus };
}

export type Absender = ReturnType<typeof useAbsender>;
