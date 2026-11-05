import { useCallback, useEffect, useMemo, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { ladeStandardSignatur } from '../email/mailEditorHtml';
import { monatsname } from './zeitraum';

export interface SteuerberaterAnsprechpartner {
    id: number;
    anrede: string | null;
    vorname: string;
    nachname: string;
    email: string;
    telefon: string;
    istLohnAnsprechpartner: boolean;
}

export interface SteuerberaterKontakt {
    id: number;
    name: string;
    email: string;
    telefon: string;
    weitereEmails: string[];
    ansprechpartnerListe: SteuerberaterAnsprechpartner[];
}

export interface Option {
    value: string;
    label: string;
}

const FRONTEND_USER_STORAGE_KEY = 'frontendUserSelection';

/** Am Rechner gewähltes Profil (für Signatur und Absender) – `null`, wenn keins gewählt ist. */
export function aktuellesFrontendProfil(): { id: number; displayName: string } | null {
    try {
        const raw = localStorage.getItem(FRONTEND_USER_STORAGE_KEY);
        const parsed: unknown = raw ? JSON.parse(raw) : null;
        if (parsed && typeof parsed === 'object' && typeof (parsed as { id?: unknown }).id === 'number') {
            return parsed as { id: number; displayName: string };
        }
    } catch (err) {
        console.warn('Frontend-Profil konnte nicht gelesen werden:', err);
    }
    return null;
}

/** Lohn-Ansprechpartner eines Steuerberaters, sonst der erste, sonst `null`. */
export function lohnAnsprechpartner(sb: SteuerberaterKontakt | null): SteuerberaterAnsprechpartner | null {
    if (!sb) return null;
    const liste = sb.ansprechpartnerListe || [];
    return liste.find(ap => ap.istLohnAnsprechpartner) || liste[0] || null;
}

/** Alle Adressen eines Steuerberaters ohne Doppelte (Kanzlei, weitere, Ansprechpartner). */
export function adressenVon(sb: SteuerberaterKontakt | null): string[] {
    if (!sb) return [];
    const alle = [sb.email, ...(sb.weitereEmails || []), ...(sb.ansprechpartnerListe || []).map(ap => ap.email)];
    return Array.from(new Set(alle.filter(Boolean)));
}

function ansprechpartnerOption(ap: SteuerberaterAnsprechpartner): Option {
    const name = [ap.vorname, ap.nachname].filter(Boolean).join(' ') || '(ohne Namen)';
    return { value: String(ap.id), label: ap.istLohnAnsprechpartner ? `${name} · Löhne` : name };
}

interface Geladen {
    liste: SteuerberaterKontakt[];
    signatur: string;
}

/**
 * Empfänger der Stundenübermittlung: lädt beim Öffnen Steuerberater,
 * Firmenname (für den Betreff) und Signatur, wählt den ersten Steuerberater
 * mit seinem Lohn-Ansprechpartner vor und leitet Empfänger und Auswahllisten ab.
 * Geschlossen wird alles geleert.
 */
export function useSteuerberaterEmpfaenger(offen: boolean, monat: number, jahr: number) {
    const toast = useToast();
    const [geladen, setGeladen] = useState<Geladen | null>(null);
    const [steuerberaterId, setSteuerberaterId] = useState<number | null>(null);
    const [ansprechpartnerId, setAnsprechpartnerId] = useState<number | null>(null);
    const [empfaenger, setEmpfaenger] = useState('');
    const [betreff, setBetreff] = useState('');

    // Geschlossen oder anderer Monat: Auswahl zurücksetzen, damit sofort „wird geladen“ erscheint.
    const fuer = offen ? `${monat}|${jahr}` : null;
    const [geladenFuer, setGeladenFuer] = useState<string | null>(null);
    if (fuer !== geladenFuer) {
        setGeladenFuer(fuer);
        setGeladen(null);
        setSteuerberaterId(null);
        setAnsprechpartnerId(null);
        setEmpfaenger('');
        setBetreff('');
    }

    const waehleSteuerberater = useCallback((id: number | null, liste: SteuerberaterKontakt[]) => {
        const sb = liste.find(s => s.id === id) ?? null;
        const ap = lohnAnsprechpartner(sb);
        setSteuerberaterId(sb?.id ?? null);
        setAnsprechpartnerId(ap?.id ?? null);
        setEmpfaenger(ap?.email || sb?.email || '');
    }, []);

    useEffect(() => {
        if (!offen) return;
        let abgebrochen = false;
        Promise.all([
            fetch('/api/firma/steuerberater').then(r => (r.ok ? r.json() : [])),
            fetch('/api/firma').then(r => (r.ok ? r.json() : null)).catch(() => null),
            ladeStandardSignatur(aktuellesFrontendProfil()?.id),
        ]).then(([liste, firma, signatur]) => {
            if (abgebrochen) return;
            const sbListe: SteuerberaterKontakt[] = Array.isArray(liste) ? liste : [];
            const firmenname = typeof firma?.firmenname === 'string' ? firma.firmenname.trim() : '';
            const titel = `Stundenaufstellung ${monatsname(monat)} ${jahr}`;
            setBetreff(firmenname ? `${titel} - ${firmenname}` : titel);
            setGeladen({ liste: sbListe, signatur });
            waehleSteuerberater(sbListe[0]?.id ?? null, sbListe);
        }).catch(err => {
            if (abgebrochen) return;
            console.error('Steuerberater konnten nicht geladen werden:', err);
            toast.error('Die Steuerberater konnten nicht geladen werden.');
            setGeladen({ liste: [], signatur: '' });
        });
        return () => { abgebrochen = true; };
    }, [offen, monat, jahr, toast, waehleSteuerberater]);

    const liste = useMemo(() => geladen?.liste ?? [], [geladen]);
    const steuerberater = liste.find(sb => sb.id === steuerberaterId) ?? null;
    const ansprechpartner = steuerberater?.ansprechpartnerListe.find(ap => ap.id === ansprechpartnerId) ?? null;

    return {
        laedt: offen && geladen === null,
        liste,
        signatur: geladen?.signatur ?? '',
        steuerberaterId,
        ansprechpartnerId,
        ansprechpartner,
        empfaenger,
        setEmpfaenger,
        betreff,
        setBetreff,
        waehleSteuerberater: (id: number | null) => waehleSteuerberater(id, liste),
        waehleAnsprechpartner: (id: number | null) => {
            setAnsprechpartnerId(id);
            const email = steuerberater?.ansprechpartnerListe.find(ap => ap.id === id)?.email;
            if (email) setEmpfaenger(email);
        },
        optionen: {
            steuerberater: liste.map((sb): Option => ({ value: String(sb.id), label: sb.name })),
            ansprechpartner: (steuerberater?.ansprechpartnerListe || []).map(ansprechpartnerOption),
            empfaenger: adressenVon(steuerberater).map((e): Option => ({ value: e, label: e })),
        },
    };
}

export type SteuerberaterEmpfaenger = ReturnType<typeof useSteuerberaterEmpfaenger>;
