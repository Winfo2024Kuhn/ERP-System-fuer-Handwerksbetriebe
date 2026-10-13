import type { CSSProperties } from 'react';
import { ImageOff } from 'lucide-react';
import { useState } from 'react';
import type { FormBlock } from '../../types';
import { sichereBildQuelle } from './bildQuelle';
import { replacePlaceholders } from './constants';
import { pdfStil } from './pdfStil';
// Schriften wie im PDF (Open Sans für Brieftext, Montserrat für den Betreff) –
// sonst brechen Zeilen im Editor anders um als im Druck.
import '@fontsource/open-sans/latin-400.css';
import '@fontsource/open-sans/latin-700.css';
import '@fontsource/montserrat/latin-600.css';

/** Zeilenabstand wie im PDF-Dienst (Schriftgröße × 1,4). */
const ZEILENABSTAND = 1.4;
const SCHRIFT = "'Open Sans', Arial, sans-serif";
const BETREFF_SCHRIFT = "'Montserrat', Arial, sans-serif";

/** Felder, die das Programm beim Erstellen eines Dokuments füllt. */
const FELDER: Partial<Record<FormBlock['type'], { schluessel: string; beschriftung: string }>> = {
    doknr: { schluessel: 'dokumentnummer', beschriftung: 'Dokumentnr.' },
    projektnr: { schluessel: 'projektnummer', beschriftung: 'Projektnr.' },
    kundennummer: { schluessel: 'kundennummer', beschriftung: 'Kundennr.' },
    kunde: { schluessel: 'kundenname', beschriftung: 'Kunde' },
    adresse: { schluessel: 'kundenadresse', beschriftung: 'Anschrift' },
    dokumenttyp: { schluessel: 'dokumenttyp', beschriftung: 'Dokumenttyp' },
    datum: { schluessel: 'datum', beschriftung: 'Datum' },
    seitenzahl: { schluessel: 'seitenzahl', beschriftung: 'Seitenzahl' },
};

const BEISPIEL_POSITIONEN = [
    ['1.1', '12', 'm²', 'Trockenbauarbeiten nach DIN 18181', '45,00 €', '540,00 €'],
    ['1.2', '8', 'Stk', 'Gipskartonplatten 12,5 mm', '18,50 €', '148,00 €'],
    ['2.1', '6', 'Std', 'Montagearbeiten Facharbeiter', '58,00 €', '348,00 €'],
];

interface BlockInhaltProps {
    item: FormBlock;
    daten: Record<string, string>;
    /** Im Editor: leere Texte als Platzhalter zeigen, Tabellenbereich kennzeichnen. */
    imEditor?: boolean;
}

/**
 * Inhalt eines Bausteins – gemeinsam für Editor-Seite und Vorschau, damit
 * beide gleich aussehen. Text steht oben bündig wie im PDF.
 */
export function BlockInhalt({ item, daten, imEditor = false }: BlockInhaltProps) {
    const stil = pdfStil(item);
    const textStil: CSSProperties = {
        fontFamily: stil.betreff ? BETREFF_SCHRIFT : SCHRIFT,
        fontSize: stil.schriftgroesse,
        lineHeight: ZEILENABSTAND,
        color: stil.farbe,
        fontWeight: stil.betreff ? 600 : stil.fett ? 700 : 400,
        whiteSpace: 'pre-line',
        overflowWrap: 'anywhere',
    };

    if (item.type === 'logo') return <LogoBild quelle={item.content || '/image001.png'} />;

    if (item.type === 'table') {
        return (
            <div className="relative h-full w-full">
                <table className="w-full border-collapse text-[9px]" style={{ fontFamily: SCHRIFT }}>
                    <thead>
                        <tr className="border-b border-slate-300 text-slate-700">
                            {['Pos.', 'Menge', 'ME', 'Bezeichnung', 'EP', 'GP'].map((t, i) => (
                                <th key={t} className={`px-1 py-1 font-semibold ${i >= 4 ? 'text-right' : i === 3 ? 'text-left' : 'text-center'}`}>{t}</th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {BEISPIEL_POSITIONEN.map(zeile => (
                            <tr key={zeile[0]} className="border-b border-slate-100 text-slate-700">
                                {zeile.map((zelle, i) => (
                                    <td key={i} className={`px-1 py-1 ${i >= 4 ? 'text-right tabular-nums' : i === 3 ? 'text-left' : 'text-center'}`}>{zelle}</td>
                                ))}
                            </tr>
                        ))}
                        <tr className="font-semibold text-slate-900">
                            <td className="px-1 py-1" colSpan={5}>Gesamt netto</td>
                            <td className="px-1 py-1 text-right tabular-nums">1.036,00 €</td>
                        </tr>
                    </tbody>
                </table>
                {imEditor && (
                    <p className="absolute bottom-1 left-1 right-1 text-center text-[9px] font-medium text-slate-400">
                        Bereich für die Positionen
                    </p>
                )}
            </div>
        );
    }

    const feld = FELDER[item.type];
    if (feld) {
        // Felder füllt das Programm; ein eigener Text am Feld hat Vorrang.
        const wert = item.content ? replacePlaceholders(item.content, daten) : (daten[feld.schluessel] ?? '');
        return (
            <div style={textStil}>
                {imEditor && <span className="block text-[8px] font-medium uppercase tracking-wide text-slate-400" style={{ lineHeight: 1.2 }}>{feld.beschriftung}</span>}
                {wert}
            </div>
        );
    }

    const roh = item.content ?? '';
    if (!roh.trim()) {
        return imEditor ? <div style={{ ...textStil, color: '#94a3b8' }}>Text eingeben …</div> : null;
    }
    return <div style={textStil}>{replacePlaceholders(roh, daten)}</div>;
}

function LogoBild({ quelle }: { quelle: string }) {
    const [fehler, setFehler] = useState(false);
    const sicher = sichereBildQuelle(quelle);
    if (!sicher || fehler) {
        return (
            <div role="img" aria-label="Bild nicht gefunden" className="flex h-full w-full items-center justify-center rounded bg-slate-100">
                <ImageOff aria-hidden="true" className="h-6 w-6 text-slate-400" />
            </div>
        );
    }
    return <img src={sicher} alt="Logo" draggable={false} onError={() => setFehler(true)} className="h-full w-full object-contain" />;
}
