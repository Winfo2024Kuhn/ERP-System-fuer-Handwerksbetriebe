import { useEffect, useRef, useState } from 'react';

interface ZahlungszielTageEingabeProps {
    /** Geltendes Zahlungsziel in Tagen. */
    tage: number;
    /**
     * Wird aufgerufen, wenn die Eingabe abgeschlossen ist (Enter oder Feld
     * verlassen). Liefert zurueck, ob der Wert uebernommen wurde — bei `false`
     * (abgelehnt oder Rueckfrage abgebrochen) faellt das Feld auf `tage` zurueck.
     */
    onUebernehmen: (tage: number) => Promise<boolean>;
    /** Klassen fuer das Eingabefeld — die Groesse unterscheidet sich je Einsatzort. */
    className?: string;
    autoFocus?: boolean;
}

/**
 * Eingabefeld fuer das Zahlungsziel in Tagen: Summenzeile und Popover am
 * Zahlungsziel-Chip nutzen dasselbe Verhalten.
 *
 * Bewusst mit Entwurf statt sofortiger Uebernahme. Der Editor fragt bei langen
 * Zahlungszielen nach (siehe `handleZahlungszielChange`), und eine Rueckfrage
 * pro Tastendruck waere unbenutzbar. Waehrend des Tippens darf deshalb auch ein
 * unfertiger Zwischenstand stehen bleiben (Vorgabe aus FRONTEND_UI.md), geprueft
 * wird erst beim Abschluss.
 *
 * Kein `type="number"`: der native Zahlen-Spinner ist im Projekt nicht erwuenscht.
 *
 * Bewusst nicht `components/ui/decimal-input.tsx`: der prueft beim Tippen und
 * meldet inline, waehrend hier erst der Abschluss zaehlt (sonst stuende die
 * Rueckfrage nach jedem Tastendruck im Weg) und die Meldung ueber den
 * gemeinsamen Toast des Editors laeuft.
 */
export function ZahlungszielTageEingabe({ tage, onUebernehmen, className, autoFocus }: ZahlungszielTageEingabeProps) {
    const [entwurf, setEntwurf] = useState(String(tage));
    const feldRef = useRef<HTMLInputElement>(null);
    // Spiegel des Entwurfs. `onBlur` kann im SELBEN React-Ereignis feuern, in dem
    // der Entwurf gerade gesetzt wurde (Escape ruft `blur()` direkt nach
    // `setEntwurf`) — der State waere dort noch der alte, und Escape haette den
    // verworfenen Wert uebernommen.
    const entwurfRef = useRef(entwurf);
    // Nur was der Nutzer selbst getippt hat, wird gemeldet. Ohne das meldet ein
    // blosses Hinein- und Wieder-Hinausklicken den Wert, den das Feld beim
    // Fokussieren zufaellig zeigte — z.B. den Standard, waehrend das Dokument
    // noch laedt.
    const getipptRef = useRef(false);
    // Verhindert eine zweite Uebernahme, solange die erste laeuft: die
    // Rueckfrage nimmt den Fokus, wodurch `onBlur` waehrend des offenen Dialogs
    // feuert — ohne diesen Riegel stuende die Frage zweimal da.
    const laeuftRef = useRef(false);
    // Der Aussenwert kann sich aendern, waehrend die Rueckfrage offen steht —
    // nach dem Warten zaehlt der neue, nicht der aus der alten Render-Closure.
    const tageRef = useRef(tage);
    useEffect(() => { tageRef.current = tage; }, [tage]);

    const setzeEntwurf = (wert: string, getippt: boolean) => {
        entwurfRef.current = wert;
        getipptRef.current = getippt;
        setEntwurf(wert);
    };

    // Ein von aussen geaenderter Wert (Laden, Speichern, die jeweils andere
    // Eingabestelle) gewinnt — nur nicht gegen etwas, das der Nutzer gerade
    // selbst getippt hat.
    useEffect(() => {
        if (!getipptRef.current) setzeEntwurf(String(tage), false);
    }, [tage]);

    const uebernehmen = async () => {
        if (laeuftRef.current) return;
        if (!getipptRef.current) return;
        const entwurfJetzt = entwurfRef.current;
        if (entwurfJetzt === String(tage)) {
            setzeEntwurf(entwurfJetzt, false);
            return;
        }
        laeuftRef.current = true;
        try {
            // Nur reine Ziffern sind eine Tageszahl. "30abc" oder "3,5" wuerde
            // `parseInt` stillschweigend zu 30 bzw. 3 machen; als NaN laeuft es
            // stattdessen in die Meldung des Editors.
            const gewuenscht = /^\d+$/.test(entwurfJetzt) ? Number.parseInt(entwurfJetzt, 10) : NaN;
            const uebernommen = await onUebernehmen(gewuenscht);
            // Nach der Uebernahme die kanonische Schreibweise zeigen ("030" → "30"),
            // sonst meldet das anschliessende Verlassen des Feldes denselben Wert
            // noch einmal an — samt erneuter Rueckfrage.
            setzeEntwurf(String(uebernommen ? gewuenscht : tageRef.current), false);
            // Die Rueckfrage nimmt den Fokus und gibt ihn nicht zurueck. Fuer die
            // Tastaturbedienung geht er zurueck ins Feld.
            if (document.activeElement === document.body) feldRef.current?.focus();
        } finally {
            laeuftRef.current = false;
        }
    };

    return (
        <input
            ref={feldRef}
            type="text"
            inputMode="numeric"
            value={entwurf}
            onChange={(e) => setzeEntwurf(e.target.value, true)}
            onBlur={() => { void uebernehmen(); }}
            onKeyDown={(e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    void uebernehmen();
                }
                if (e.key === 'Escape') {
                    e.preventDefault();
                    // Kein `blur()`: der Fokus darf bleiben, und der spaetere
                    // echte Blur ist nach dem Zuruecksetzen ein No-op.
                    setzeEntwurf(String(tage), false);
                }
            }}
            title="Zahlungsziel in Tagen"
            aria-label="Zahlungsziel in Tagen"
            className={className}
            autoFocus={autoFocus}
        />
    );
}
