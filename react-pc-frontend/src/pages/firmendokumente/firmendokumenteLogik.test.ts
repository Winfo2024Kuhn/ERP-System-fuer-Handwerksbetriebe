import { describe, expect, it } from 'vitest';
import { baueBaum, OBERSTE_EBENE } from '../../lib/ordnerBaum';
import {
    faelligkeit, faelligkeitText, istSuche, MAX_DATEI_BYTES, nameAusDateiname, ordnerMitUeberfaelligem,
    ordnerOptionen, pdfDateiname, pruefeName, pruefePdfDatei, teilePdfDateien, ziehtDateien, zieleFuerOrdner,
} from './firmendokumenteLogik';
import type { FirmenOrdner } from './typen';

const ordner = (id: number, name: string, parentId: number | null, anzahlUeberfaellig = 0): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente: 1, anzahlUeberfaellig, anzahlBaldFaellig: 0,
});

// Prüfungen › Tore › Halle 2, Prüfungen › Leitern, Arbeitsschutz
const BAUM = baueBaum([
    ordner(1, 'Prüfungen', null),
    ordner(2, 'Tore', 1),
    ordner(3, 'Halle 2', 2, 1),
    ordner(4, 'Leitern', 1),
    ordner(5, 'Arbeitsschutz', null),
]);

const datei = (name: string, type: string, size = 1000) => new File([new Uint8Array(size)], name, { type });

describe('faelligkeit', () => {
    const heute = '2026-11-02';

    it('ist überfällig vor heute, bald fällig bis 30 Tage, sonst später', () => {
        expect(faelligkeit('2026-11-01', heute)).toBe('ueberfaellig');
        expect(faelligkeit('2026-11-02', heute)).toBe('bald');
        expect(faelligkeit('2026-12-02', heute)).toBe('bald');
        expect(faelligkeit('2026-12-03', heute)).toBe('spaeter');
    });

    it('rechnet über den Jahreswechsel', () => {
        expect(faelligkeit('2027-01-30', '2026-12-31')).toBe('bald');
        expect(faelligkeit('2027-01-31', '2026-12-31')).toBe('spaeter');
    });

    it('liefert null ohne oder mit kaputtem Datum', () => {
        expect(faelligkeit(null, heute)).toBeNull();
        expect(faelligkeit(undefined, heute)).toBeNull();
        expect(faelligkeit('2026-02-31', heute)).toBeNull();
    });

    it('liefert null bei kaputtem „heute“', () => {
        expect(faelligkeit('2027-01-01', 'kaputt')).toBeNull();
    });

    it('hat einen Text je Zustand', () => {
        expect(faelligkeitText('ueberfaellig')).toBe('überfällig');
        expect(faelligkeitText('bald')).toBe('bald fällig');
        expect(faelligkeitText('spaeter')).toBe('noch Zeit');
    });
});

describe('ordnerMitUeberfaelligem', () => {
    it('markiert den Ordner und alle übergeordneten', () => {
        expect([...ordnerMitUeberfaelligem(BAUM)].sort()).toEqual([1, 2, 3]);
    });

    it('übersteht kaputte Kreise in den Daten', () => {
        const kreis = baueBaum([ordner(1, 'A', 2, 1), ordner(2, 'B', 1, 1)]);
        expect([...ordnerMitUeberfaelligem(kreis)].sort()).toEqual([1, 2]);
    });
});

describe('Formatierung', () => {
    it('schlägt den Namen aus dem Dateinamen vor', () => {
        expect(nameAusDateiname('Torprüfung Halle 2.PDF')).toBe('Torprüfung Halle 2');
        expect(nameAusDateiname('C:\\Scans\\scan_0042.pdf')).toBe('scan_0042');
        expect(nameAusDateiname('ordner/bericht.pdf.pdf')).toBe('bericht.pdf');
        expect(nameAusDateiname(`${'a'.repeat(250)}.pdf`)).toHaveLength(200);
        expect(nameAusDateiname('')).toBe('');
    });

    it('hängt beim Herunterladen genau ein „.pdf“ an', () => {
        expect(pdfDateiname('Torprüfung')).toBe('Torprüfung.pdf');
        expect(pdfDateiname('Bericht.PDF')).toBe('Bericht.PDF');
    });
});

describe('Prüfungen', () => {
    it('nimmt nur PDFs an', () => {
        expect(pruefePdfDatei(datei('bericht.pdf', 'application/pdf'))).toBeNull();
        expect(pruefePdfDatei(datei('bericht.pdf', ''))).toBeNull();
        expect(pruefePdfDatei(datei('bild.png', 'image/png'))).toBe('„bild.png“ ist kein PDF. Es können nur PDF-Dateien abgelegt werden.');
        expect(pruefePdfDatei(datei('tarnung.pdf', 'text/html'))).toContain('ist kein PDF');
        expect(pruefePdfDatei(datei('notiz.txt', ''))).toContain('ist kein PDF');
    });

    it('lehnt leere und zu große Dateien ab', () => {
        expect(pruefePdfDatei(datei('leer.pdf', 'application/pdf', 0))).toBe('„leer.pdf“ ist leer. Bitte eine andere PDF wählen.');
        expect(pruefePdfDatei({ name: 'riesig.pdf', type: 'application/pdf', size: MAX_DATEI_BYTES + 1 })).toBe('„riesig.pdf“ ist größer als 50 MB. Bitte eine kleinere PDF wählen oder die Datei verkleinern.');
    });

    it('teilt eine Auswahl in PDFs und Meldungen', () => {
        const { pdfs, meldungen } = teilePdfDateien([datei('a.pdf', 'application/pdf'), datei('b.docx', 'application/msword')]);
        expect(pdfs.map((d) => d.name)).toEqual(['a.pdf']);
        expect(meldungen).toEqual(['„b.docx“ ist kein PDF. Es können nur PDF-Dateien abgelegt werden.']);
        expect(teilePdfDateien(null)).toEqual({ pdfs: [], meldungen: [] });
    });

    it('prüft Namen von Dokumenten und Ordnern', () => {
        expect(pruefeName('  ', 'Dokument')).toBe('Bitte einen Namen für das Dokument eingeben.');
        expect(pruefeName('', 'Ordner')).toBe('Bitte einen Namen für den Ordner eingeben.');
        expect(pruefeName('x'.repeat(201), 'Ordner')).toBe('Der Name darf höchstens 200 Zeichen lang sein.');
        expect(pruefeName('Tore', 'Ordner')).toBeNull();
    });

    it('sucht erst ab 2 Zeichen', () => {
        expect(istSuche(' t ')).toBe(false);
        expect(istSuche('to')).toBe(true);
    });

    it('erkennt gezogene Dateien', () => {
        expect(ziehtDateien({ dataTransfer: { types: ['Files'] } })).toBe(true);
        expect(ziehtDateien({ dataTransfer: { types: ['text/plain'] } })).toBe(false);
        expect(ziehtDateien({ dataTransfer: null })).toBe(false);
    });
});

describe('Ordner-Ziele', () => {
    it('sperrt den Ordner selbst, seine Unterordner und den jetzigen Platz', () => {
        const ziele = zieleFuerOrdner(BAUM, 2);
        const grund = (id: number | null) => ziele.find((z) => z.id === id)?.gesperrt;
        expect(ziele[0]).toEqual({ id: null, name: OBERSTE_EBENE, ebene: 0, gesperrt: null });
        expect(grund(2)).toBe('Das ist der Ordner selbst');
        expect(grund(3)).toBe('Liegt in diesem Ordner');
        expect(grund(1)).toBe('Liegt schon hier');
        expect(grund(4)).toBeNull();
        expect(grund(5)).toBeNull();
    });

    it('sperrt die oberste Ebene für Hauptordner', () => {
        expect(zieleFuerOrdner(BAUM, 5)[0].gesperrt).toBe('Liegt schon hier');
        expect(zieleFuerOrdner(BAUM, 404)[0].gesperrt).toBe('Liegt schon hier');
    });

    it('bietet Ordner mit vollem Pfad zur Auswahl an', () => {
        expect(ordnerOptionen(BAUM)).toEqual([
            { value: '5', label: 'Arbeitsschutz' },
            { value: '1', label: 'Prüfungen' },
            { value: '4', label: 'Prüfungen › Leitern' },
            { value: '2', label: 'Prüfungen › Tore' },
            { value: '3', label: 'Prüfungen › Tore › Halle 2' },
        ]);
    });
});
