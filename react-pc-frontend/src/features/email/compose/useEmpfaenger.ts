import { useMemo, useState } from 'react';
import { EINZELVERSAND_MAX_EMPFAENGER, einzelneEmpfaenger, type EinzelversandErgebnis } from '../einzelversand';
import { fuegeEmpfaengerHinzu } from '../empfaengerListe';

interface EmpfaengerOptionen {
    initialRecipient: string;
    initialCc?: string[];
    /** Antwort: Empfänger steht fest, Einzelversand gibt es nicht. */
    replyEmailId?: number;
    /** Ist eine Antwort schon beim Öffnen bekannt? Dann wird nichts automatisch vorgeschlagen. */
    istAntwortBeimOeffnen: boolean;
    /** Kunden-Adressen des verknüpften Vorgangs (gleiche Liste = gleiches Objekt). */
    kundenEmails: string[];
    markDirty: () => void;
}

/**
 * Empfänger des Formulars: An, CC und Einzelversand („jeder bekommt eine eigene
 * Mail“). Solange der Empfänger nur vorgeschlagen ist, zieht ein Wechsel der
 * Zuordnung ihn mit; sobald der Nutzer tippt, bleibt seine Eingabe stehen.
 */
export function useEmpfaenger({ initialRecipient, initialCc, replyEmailId, istAntwortBeimOeffnen, kundenEmails, markDirty }: EmpfaengerOptionen) {
    const [recipient, setRecipient] = useState<string>(initialRecipient || '');
    const [ccRecipients, setCcRecipients] = useState<string[]>(() => initialCc?.filter(c => c.trim()) ?? []);
    const [showCc, setShowCc] = useState(() => (initialCc?.some(c => c.trim()) ?? false));
    const [empfaengerAutomatisch, setEmpfaengerAutomatisch] = useState<boolean>(!initialRecipient && !istAntwortBeimOeffnen);
    /** Im An-Feld ist eine Adresse getippt, aber noch kein Kärtchen (wird es beim Klick auf Senden). */
    const [anEntwurfVorhanden, setAnEntwurfVorhanden] = useState(false);
    /** `null` = automatisch (an ab zwei Empfängern ohne CC), sonst die bewusste Wahl des Anwenders. */
    const [einzelversandWahl, setEinzelversandWahl] = useState<boolean | null>(null);
    /** Ergebnis des letzten Einzelversands mit Teil- oder Totalfehler. */
    const [einzelversandErgebnis, setEinzelversandErgebnis] = useState<EinzelversandErgebnis | null>(null);

    // Ohne Doppelte gezählt – so wie die Kärtchen sie zeigen und das Backend sie verschickt.
    const anEmpfaenger = useMemo(() => fuegeEmpfaengerHinzu([], einzelneEmpfaenger(recipient)), [recipient]);
    const ccEingetragen = ccRecipients.some(c => c.trim());
    // Einzelversand gibt es nicht bei Antworten – die gehen an genau den Verlauf.
    const einzelversandMoeglich = !replyEmailId;
    // Mehrere Empfänger sollen sich nicht versehentlich gegenseitig sehen (Datenschutz).
    // Wer CC einträgt, will eine gemeinsame Mail – dann schaltet sich nichts von selbst ein.
    const einzelversand = einzelversandWahl ?? (anEmpfaenger.length >= 2 && !ccEingetragen);
    const einzelversandAktiv = einzelversandMoeglich && einzelversand;
    const zuVieleEmpfaenger = einzelversandAktiv && anEmpfaenger.length > EINZELVERSAND_MAX_EMPFAENGER;

    // Empfänger aus dem verknüpften Vorgang vorschlagen – auch nach einem Wechsel der
    // Zuordnung, damit die Mail nicht an den Kunden des vorherigen Projekts geht.
    const [vorgeschlageneVorher, setVorgeschlageneVorher] = useState<string[] | null>(null);
    if (kundenEmails !== vorgeschlageneVorher) {
        setVorgeschlageneVorher(kundenEmails);
        if (empfaengerAutomatisch && kundenEmails.length > 0 && recipient !== kundenEmails[0]) {
            setRecipient(kundenEmails[0]);
        }
    }

    const empfaengerAendern = (wert: string) => {
        markDirty();
        setEmpfaengerAutomatisch(false);
        setRecipient(wert);
    };

    const ccAus = () => { setShowCc(false); setCcRecipients([]); };

    const einzelversandUmschalten = (an: boolean) => {
        markDirty();
        setEinzelversandWahl(an);
        setEinzelversandErgebnis(null);
        // Beim Einzelversand gibt es keine Kopie-Empfänger.
        if (an) ccAus();
    };

    return {
        recipient, setRecipient, empfaengerAendern, setEmpfaengerAutomatisch,
        ccRecipients, setCcRecipients, showCc, setShowCc, ccAus,
        anEntwurfVorhanden, setAnEntwurfVorhanden, anEmpfaenger,
        einzelversand, einzelversandMoeglich, einzelversandAktiv, zuVieleEmpfaenger,
        setEinzelversandWahl, einzelversandUmschalten, einzelversandErgebnis, setEinzelversandErgebnis,
    };
}

export type Empfaenger = ReturnType<typeof useEmpfaenger>;
