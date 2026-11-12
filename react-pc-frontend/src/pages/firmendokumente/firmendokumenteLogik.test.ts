import { describe, expect, it } from 'vitest';
import { baueBaum, OBERSTE_EBENE } from '../../lib/ordnerBaum';
import {
    dateityp, DOKUMENT_DATENTYP, ordnerPfadOderStrich, dokumentDarfNach, HAUPTORDNER_GESPERRT_TEXT, hauptordnerWechselGesperrt, wurzelId, downloadDateiname, faelligkeit, faelligkeitText, GESPERRTE_ENDUNGEN, istSuche, liesGezogenesDokument,
    MAX_DATEI_BYTES, nameAusDateiname, ordnerMitUeberfaelligem, pruefeDatei, pruefeName, schreibeGezogenesDokument,
    teileDateien, ziehtDateien, ziehtDokument, zieleFuerOrdner,
} from './firmendokumenteLogik';
import type { FirmenOrdner } from './typen';

const ordner = (id: number, name: string, parentId: number | null, anzahlUeberfaellig = 0): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente: 1, anzahlUeberfaellig, anzahlBaldFaellig: 0, darfAendern: true, eingeschraenkt: false,
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

describe('ordnerPfadOderStrich', () => {
    it('zeigt den vollen Pfad und einen Strich, wenn es den Ordner nicht gibt', () => {
        expect(ordnerPfadOderStrich(BAUM, 3)).toBe('Prüfungen › Tore › Halle 2');
        expect(ordnerPfadOderStrich(BAUM, 404)).toBe('–');
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
    it('schlägt den Namen aus dem Dateinamen vor – ohne Pfad und ohne Endung', () => {
        expect(nameAusDateiname('Torprüfung Halle 2.PDF')).toBe('Torprüfung Halle 2');
        expect(nameAusDateiname('C:\\Scans\\scan_0042.pdf')).toBe('scan_0042');
        expect(nameAusDateiname('ordner/bericht.pdf.pdf')).toBe('bericht.pdf');
        expect(nameAusDateiname('Baustelle Mustermann.jpeg')).toBe('Baustelle Mustermann');
        expect(nameAusDateiname('Angebot v2.1.docx')).toBe('Angebot v2.1');
        expect(nameAusDateiname('Liste ohne Endung')).toBe('Liste ohne Endung');
        expect(nameAusDateiname('.htaccess')).toBe('.htaccess');
        expect(nameAusDateiname(`${'a'.repeat(250)}.pdf`)).toHaveLength(200);
        expect(nameAusDateiname('')).toBe('');
    });

    it('baut den Download-Namen aus Dokumentname und Endung der Originaldatei', () => {
        expect(downloadDateiname('Haftpflicht 2026', 'scan_0040.docx')).toBe('Haftpflicht 2026.docx');
        expect(downloadDateiname('Torprüfung', 'scan.PDF')).toBe('Torprüfung.PDF');
        expect(downloadDateiname('Bericht.pdf', 'scan.pdf')).toBe('Bericht.pdf');
        expect(downloadDateiname('Bericht.PDF', 'scan.pdf')).toBe('Bericht.PDF');
        expect(downloadDateiname('Foto Halle', 'C:\\Bilder\\IMG_1.jpg')).toBe('Foto Halle.jpg');
        expect(downloadDateiname('Notiz', 'notiz')).toBe('Notiz');
        expect(downloadDateiname('Notiz', '.htaccess')).toBe('Notiz');
    });

    it('entschärft eine Programmendung im Namen, wenn die Originaldatei keine Endung hat', () => {
        expect(downloadDateiname('setup.exe', 'setup')).toBe('setup_exe');
        expect(downloadDateiname('Start.BAT', 'LIESMICH')).toBe('Start_BAT');
        expect(downloadDateiname('verknuepfung.appref-ms', 'x')).toBe('verknuepfung_appref-ms');
        expect(downloadDateiname('Bericht.pdf', 'bericht')).toBe('Bericht.pdf');
        expect(downloadDateiname('setup.exe', 'setup.pdf')).toBe('setup.exe.pdf');
    });
});

describe('dateityp', () => {
    const typ = (originalDateiname: string, dateiart: 'PDF' | 'BILD' | 'SONSTIGE' = 'SONSTIGE') => dateityp({ originalDateiname, dateiart });

    it('zeigt die Endung der Originaldatei in Großbuchstaben', () => {
        expect(typ('scan.pdf', 'PDF')).toBe('PDF');
        expect(typ('IMG_0042.JPG', 'BILD')).toBe('JPG');
        expect(typ('ZAUNFELD_GZ75.sza')).toBe('SZA');
        expect(typ('Angebot v2.1.docx')).toBe('DOCX');
        expect(typ('archiv.tar.gz')).toBe('GZ');
        expect(typ('verknuepfung.appref-ms')).toBe('APPREF-MS');
        expect(typ('.htaccess')).toBe('HTACCESS');
    });

    it('übergeht Punkte und Leerzeichen am Ende wie Windows', () => {
        expect(typ('bericht.pdf. ')).toBe('PDF');
        expect(typ('bericht.dwg...')).toBe('DWG');
        expect(typ('bericht.dwg\t')).toBe('DWG');
        expect(typ('bericht.dwg\u2003\u3000\u001F')).toBe('DWG');
    });

    it('zählt geschützte Leerzeichen wie der Server nicht als Leerzeichen', () => {
        expect(typ('bericht.pdf\u00A0')).toBeNull();
        expect(typ('bericht.pdf\u2007')).toBeNull();
        expect(typ('bericht.pdf\u202F')).toBeNull();
        expect(typ('bericht.pdf\uFEFF')).toBeNull();
    });

    it('hat ohne erkennbare Endung keinen Dateityp', () => {
        expect(typ('ZAUNFELD_GZ75')).toBeNull();
        expect(typ('name.')).toBeNull();
        expect(typ('x.ab cd')).toBeNull();
        expect(typ('x.-ab')).toBeNull();
        expect(typ('x.ab-')).toBeNull();
        expect(typ('x.ab--cd')).toBeNull();
        expect(typ('x.äöü')).toBeNull();
        expect(typ(`x.${'a'.repeat(21)}`)).toBeNull();
        expect(typ(`x.${'a'.repeat(20)}`)).toBe('A'.repeat(20));
        expect(typ('')).toBeNull();
        expect(typ('...')).toBeNull();
    });

    it('nimmt für Dateien der alten PDF-Ablage ohne Endung PDF an', () => {
        expect(typ('ZAUNFELD_GZ75', 'PDF')).toBe('PDF');
        expect(typ('name.', 'PDF')).toBe('PDF');
        expect(typ('ZAUNFELD_GZ75', 'BILD')).toBeNull();
        // dateiart PDF heißt: Endung auf dem Server ist „pdf“ (alte Ablage erkannte PDFs am Inhalt, egal wie der Name lautet).
        expect(typ('rechnung.jpg', 'PDF')).toBe('PDF');
    });

    it('bleibt bei sehr langen Punktfolgen schnell', () => {
        const start = performance.now();
        expect(typ(`a${'.'.repeat(50_000)}b `)).toBe('B');
        expect(typ(`a${' .'.repeat(50_000)}`)).toBeNull();
        expect(performance.now() - start).toBeLessThan(1000);
    });
});

describe('Prüfungen', () => {
    it('nimmt jede Datei an – PDF, Bild, Word, Excel, Zeichnung, ohne Endung', () => {
        expect(pruefeDatei(datei('bericht.pdf', 'application/pdf'))).toBeNull();
        expect(pruefeDatei(datei('foto.JPG', 'image/jpeg'))).toBeNull();
        expect(pruefeDatei(datei('angebot.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'))).toBeNull();
        expect(pruefeDatei(datei('liste.xlsx', ''))).toBeNull();
        expect(pruefeDatei(datei('zeichnung.dwg', ''))).toBeNull();
        expect(pruefeDatei(datei('LIESMICH', ''))).toBeNull();
    });

    it('sperrt Programmdateien – auch in Großschreibung', () => {
        const meldung = 'Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.';
        expect(pruefeDatei(datei('setup.exe', 'application/x-msdownload'))).toBe(`„setup.exe“: ${meldung}`);
        expect(pruefeDatei(datei('SETUP.EXE', ''))).toBe(`„SETUP.EXE“: ${meldung}`);
        expect(pruefeDatei(datei('start.Bat', ''))).toContain('Programmdateien');
        expect(pruefeDatei(datei('bericht.pdf.exe', 'application/pdf'))).toContain('Programmdateien');
        expect(pruefeDatei(datei('exe', ''))).toBeNull();
        expect(pruefeDatei(datei('handbuch.exe.pdf', 'application/pdf'))).toBeNull();
    });

    it('sperrt genau die Endungen aus dem Vertrag', () => {
        expect([...GESPERRTE_ENDUNGEN].sort()).toEqual([
            'app', 'application', 'appref-ms', 'bat', 'chm', 'cmd', 'com', 'command', 'cpl', 'dll', 'dmg', 'exe', 'gadget', 'hta', 'img', 'inf', 'iso',
            'jar', 'js', 'jse', 'library-ms', 'lnk', 'msc', 'msi', 'msp', 'mst', 'pif', 'pkg', 'ps1', 'ps1xml', 'psd1', 'psm1', 'reg', 'scf', 'scr',
            'settingcontent-ms', 'sh', 'url', 'vbe', 'vbs', 'vhd', 'vhdx', 'website', 'wsf', 'wsh', 'xbap',
        ]);
    });

    it('erkennt auch Endungen mit Bindestrich und lässt Office-Makrodateien zu', () => {
        expect(pruefeDatei(datei('start.appref-ms', ''))).toContain('Programmdateien');
        expect(pruefeDatei(datei('Einstellung.SettingContent-ms', ''))).toContain('Programmdateien');
        expect(pruefeDatei(datei('abbild.iso', ''))).toContain('Programmdateien');
        expect(pruefeDatei(datei('kalkulation.xlsm', ''))).toBeNull();
        expect(pruefeDatei(datei('brief.docm', ''))).toBeNull();
        expect(pruefeDatei(datei('vortrag.pptm', ''))).toBeNull();
    });

    it('lehnt leere und zu große Dateien ab', () => {
        expect(pruefeDatei(datei('leer.jpg', 'image/jpeg', 0))).toBe('„leer.jpg“ ist leer. Bitte eine andere Datei wählen.');
        expect(pruefeDatei({ name: 'riesig.mp4', type: 'video/mp4', size: MAX_DATEI_BYTES + 1 })).toBe('„riesig.mp4“ ist größer als 50 MB. Bitte eine kleinere Datei wählen.');
        expect(pruefeDatei({ name: 'genau.pdf', type: 'application/pdf', size: MAX_DATEI_BYTES })).toBeNull();
    });

    it('teilt eine Auswahl in brauchbare Dateien und Meldungen – die übrigen bleiben', () => {
        const { dateien, meldungen } = teileDateien([datei('a.pdf', 'application/pdf'), datei('virus.exe', ''), datei('foto.png', 'image/png'), datei('leer.txt', '', 0)]);
        expect(dateien.map((d) => d.name)).toEqual(['a.pdf', 'foto.png']);
        expect(meldungen).toHaveLength(2);
        expect(meldungen[0]).toContain('„virus.exe“');
        expect(meldungen[1]).toContain('„leer.txt“ ist leer');
        expect(teileDateien(null)).toEqual({ dateien: [], meldungen: [] });
    });

    it('prüft Namen von Dokumenten und Ordnern', () => {
        expect(pruefeName('  ', 'Dokument')).toBe('Bitte einen Namen für das Dokument eingeben.');
        expect(pruefeName('', 'Ordner')).toBe('Bitte einen Namen für den Ordner eingeben.');
        expect(pruefeName('x'.repeat(201), 'Ordner')).toBe('Der Name darf höchstens 200 Zeichen lang sein.');
        expect(pruefeName('Tore', 'Ordner')).toBeNull();
        expect(pruefeName('setup.exe', 'Dokument')).toBe('Der Name darf nicht auf eine Programmendung wie .exe enden.');
        expect(pruefeName('Start.BAT ', 'Dokument')).toBe('Der Name darf nicht auf eine Programmendung wie .exe enden.');
        expect(pruefeName('Prüfbericht v1.2', 'Dokument')).toBeNull();
    });

    it('sucht erst ab 2 Zeichen', () => {
        expect(istSuche(' t ')).toBe(false);
        expect(istSuche('to')).toBe(true);
    });

    it('erkennt gezogene Dateien', () => {
        expect(ziehtDateien({ dataTransfer: { types: ['Files'] } })).toBe(true);
        expect(ziehtDateien({ dataTransfer: { types: ['text/plain'] } })).toBe(false);
        expect(ziehtDateien({ dataTransfer: null })).toBe(false);
        expect(ziehtDateien({ dataTransfer: { types: [DOKUMENT_DATENTYP] } })).toBe(false);
    });

    it('erkennt ein gezogenes Dokument aus der Liste – getrennt von Dateien', () => {
        expect(ziehtDokument({ dataTransfer: { types: [DOKUMENT_DATENTYP, 'text/plain'] } })).toBe(true);
        expect(ziehtDokument({ dataTransfer: { types: ['Files'] } })).toBe(false);
        expect(ziehtDokument({ dataTransfer: null })).toBe(false);
    });
});

describe('Dokument per Ziehen verschieben', () => {
    const dok = { id: 7, ordnerId: 2, name: 'Torprüfung', faelligAm: '2027-01-31', darfAendern: true };

    it('schreibt und liest das gezogene Dokument', () => {
        const daten = new Map<string, string>();
        schreibeGezogenesDokument({ setData: (typ, wert) => { daten.set(typ, wert); } }, dok);
        // Zweiter Typ mit dem Quellordner im Namen: beim Ziehen lesbar, damit der eigene Ordner nicht aufleuchtet.
        expect([...daten.keys()]).toEqual([DOKUMENT_DATENTYP, 'application/x-firmendokument-aus-ordner-2', 'text/plain']);
        expect(daten.get('text/plain')).toBe('Torprüfung');
        expect(liesGezogenesDokument(daten.get(DOKUMENT_DATENTYP) ?? '')).toEqual({ id: 7, ordnerId: 2, name: 'Torprüfung', faelligAm: '2027-01-31' });
    });

    it('verwirft kaputte oder fremde Daten', () => {
        expect(liesGezogenesDokument('')).toBeNull();
        expect(liesGezogenesDokument('kein json')).toBeNull();
        expect(liesGezogenesDokument('{"id":"7"}')).toBeNull();
        expect(liesGezogenesDokument('{"id":7,"ordnerId":2,"name":"A","faelligAm":5}')).toBeNull();
        expect(liesGezogenesDokument('{"id":7,"ordnerId":2,"name":"A"}')).toEqual({ id: 7, ordnerId: 2, name: 'A', faelligAm: null });
    });

    it('erlaubt das Ablegen nur in einen anderen Ordner mit Recht „Ändern“', () => {
        const ziel = (id: number, darfAendern: boolean) => ({ ...ordner(id, 'Ziel', null), darfAendern });
        expect(dokumentDarfNach(dok, ziel(3, true))).toBe(true);
        expect(dokumentDarfNach(dok, ziel(2, true))).toBe(false);
        expect(dokumentDarfNach(dok, ziel(3, false))).toBe(false);
        expect(dokumentDarfNach({ ...dok, darfAendern: false }, ziel(3, true))).toBe(false);
        expect(dokumentDarfNach(dok, undefined)).toBe(false);
    });
});

// Geschäftsleitung (eingeschränkt) › Verträge › Alt, daneben Prüfungen › Tore (offen).
const MIT_RECHTEN = baueBaum([
    { ...ordner(10, 'Geschäftsleitung', null), eingeschraenkt: true },
    { ...ordner(11, 'Verträge', 10), eingeschraenkt: true },
    { ...ordner(12, 'Alt', 11), eingeschraenkt: true },
    ordner(1, 'Prüfungen', null),
    ordner(2, 'Tore', 1),
]);

describe('Eingeschränkte Zweige verlassen', () => {
    it('findet den obersten Ordner – auch bei kaputten Kreisen', () => {
        expect(wurzelId(MIT_RECHTEN, 12)).toBe(10);
        expect(wurzelId(MIT_RECHTEN, 10)).toBe(10);
        expect(wurzelId(MIT_RECHTEN, 2)).toBe(1);
        expect(wurzelId(MIT_RECHTEN, 404)).toBeNull();
        const kreis = baueBaum([ordner(1, 'A', 2), ordner(2, 'B', 1)]);
        expect([1, 2]).toContain(wurzelId(kreis, 1));
    });

    it('sperrt für Nicht-Admins den Wechsel in einen anderen Hauptordner', () => {
        expect(hauptordnerWechselGesperrt(MIT_RECHTEN, 12, 2, false)).toBe(true);
        expect(hauptordnerWechselGesperrt(MIT_RECHTEN, 12, 1, false)).toBe(true);
        // im selben Zweig und ganz nach oben bleibt erlaubt
        expect(hauptordnerWechselGesperrt(MIT_RECHTEN, 12, 10, false)).toBe(false);
        expect(hauptordnerWechselGesperrt(MIT_RECHTEN, 12, null, false)).toBe(false);
        // Admins dürfen alles, offene Zweige sind frei
        expect(hauptordnerWechselGesperrt(MIT_RECHTEN, 12, 2, true)).toBe(false);
        expect(hauptordnerWechselGesperrt(MIT_RECHTEN, 2, 11, false)).toBe(false);
    });

    it('sperrt im Verschieben-Dialog Ziele in anderen Hauptordnern mit Grund', () => {
        const grund = (istAdmin: boolean, id: number | null) => zieleFuerOrdner(MIT_RECHTEN, 11, istAdmin).find((z) => z.id === id)?.gesperrt;
        expect(grund(false, 1)).toBe('Nur ein Admin kann in einen anderen Hauptordner verschieben');
        expect(grund(false, 2)).toBe('Nur ein Admin kann in einen anderen Hauptordner verschieben');
        expect(grund(false, null)).toBeNull();
        expect(grund(true, 1)).toBeNull();
        expect(grund(true, 2)).toBeNull();
    });

    it('nennt den Grund wie der Server', () => {
        expect(HAUPTORDNER_GESPERRT_TEXT).toBe('Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.');
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
});
