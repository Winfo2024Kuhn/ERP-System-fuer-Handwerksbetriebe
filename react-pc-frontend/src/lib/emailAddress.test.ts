import { describe, it, expect } from 'vitest';
import {
    extractEmailAddress,
    extractDisplayName,
    formatRecipient,
    isSingleEmailAddress,
    infoAdresseZuDomain,
    istInfoAdresse,
    waehleInfoEmpfaenger,
    parseRecipientList,
    formatRecipientList,
    escapeHtml,
} from './emailAddress';

describe('extractEmailAddress', () => {
    it('liefert reine Adresse unverändert zurück', () => {
        expect(extractEmailAddress('max.mustermann@example.com')).toBe('max.mustermann@example.com');
    });

    it('holt die Adresse aus "Name" <adresse> heraus', () => {
        expect(extractEmailAddress('"Max Mustermann" <max.mustermann@example.com>'))
            .toBe('max.mustermann@example.com');
    });

    it('holt die Adresse auch ohne Anführungszeichen heraus', () => {
        expect(extractEmailAddress('Max Mustermann <max.mustermann@example.com>'))
            .toBe('max.mustermann@example.com');
    });

    it('holt die Adresse heraus, wenn Name und Adresse identisch sind', () => {
        expect(extractEmailAddress('"max@example.com" <max@example.com>')).toBe('max@example.com');
    });

    it('liefert leeren String bei fehlender Eingabe', () => {
        expect(extractEmailAddress(undefined)).toBe('');
        expect(extractEmailAddress('   ')).toBe('');
    });
});

describe('extractDisplayName', () => {
    it('liefert den Anzeigenamen', () => {
        expect(extractDisplayName('"Max Mustermann" <max@example.com>')).toBe('Max Mustermann');
    });

    it('liefert die Adresse, wenn kein Anzeigename vorhanden ist', () => {
        expect(extractDisplayName('max@example.com')).toBe('max@example.com');
        expect(extractDisplayName('<max@example.com>')).toBe('max@example.com');
    });

    it('liefert Unbekannt ohne Eingabe', () => {
        expect(extractDisplayName(undefined)).toBe('Unbekannt');
        expect(extractDisplayName('   ')).toBe('Unbekannt');
    });

    // Bewusste Entscheidung: Bei einem Sammel-String mit Anzeigenamen wird die
    // letzte Rohadresse angezeigt statt des ersten Namens. Das betrifft nur
    // ausgehende Mails, in die jemand mehrere Empfänger in ein Feld getippt hat –
    // importierte Mails speichern ausschließlich reine Adressen.
    it('hält bei mehreren Empfängern keinen Sammel-String für einen Namen', () => {
        expect(extractDisplayName('"Max" <max@example.com>, "Erika" <erika@example.com>'))
            .toBe('erika@example.com');
    });
});

describe('isSingleEmailAddress', () => {
    it('erkennt eine einzelne Adresse', () => {
        expect(isSingleEmailAddress('max@example.com')).toBe(true);
        expect(isSingleEmailAddress('"Max Mustermann" <max@example.com>')).toBe(true);
    });

    // Regression: Ein Sammel-String darf nicht als Kunden-/Projekt-E-Mail
    // gespeichert werden – sonst steht Müll in den verknüpften Adressen.
    it('erkennt mehrere Adressen und unvollständige Eingaben', () => {
        expect(isSingleEmailAddress('max@example.com, erika@example.com')).toBe(false);
        expect(isSingleEmailAddress('"Max" <max@example.com>, "Erika" <erika@example.com>')).toBe(false);
        expect(isSingleEmailAddress('Max Mustermann')).toBe(false);
        expect(isSingleEmailAddress('max@example')).toBe(false);
        expect(isSingleEmailAddress(undefined)).toBe(false);
    });
});

describe('formatRecipient', () => {
    it('behält Name und Adresse, wenn ein echter Name vorhanden ist', () => {
        expect(formatRecipient('"Max Mustermann" <max@example.com>'))
            .toBe('"Max Mustermann" <max@example.com>');
    });

    it('setzt Anführungszeichen bei unquotiertem Namen', () => {
        expect(formatRecipient('Max Mustermann <max@example.com>'))
            .toBe('"Max Mustermann" <max@example.com>');
    });

    it('behält einen Namen mit Komma', () => {
        expect(formatRecipient('"Mustermann, Max" <max@example.com>'))
            .toBe('"Mustermann, Max" <max@example.com>');
    });

    it('nutzt den übergebenen Namen statt des Namens in der Adresse', () => {
        expect(formatRecipient('"Max Mustermann" <max@example.com>', 'Musterbau GmbH'))
            .toBe('"Musterbau GmbH" <max@example.com>');
    });

    // Namen stammen teils aus fremden E-Mails: Anführungszeichen und Zeilenumbrüche
    // würden den Empfänger-Eintrag zerreißen.
    it('entschärft Anführungszeichen, spitze Klammern und Zeilenumbrüche im Namen', () => {
        expect(formatRecipient('max@example.com', 'Muster "Metallbau" GmbH'))
            .toBe('"Muster Metallbau GmbH" <max@example.com>');
        expect(formatRecipient('max@example.com', 'Muster\r\nGmbH'))
            .toBe('"Muster GmbH" <max@example.com>');
        expect(formatRecipient('max@example.com', 'Müller <Chef>'))
            .toBe('"Müller Chef" <max@example.com>');
    });

    // Regression: Beim Antworten wurde `"max@example.com" <max@example.com>`
    // eingetragen – dadurch schlug der Abgleich mit gespeicherten Adressen fehl
    // und die Rückfrage "E-Mail-Adresse speichern?" kam bei jeder Antwort.
    it('verzichtet auf den Namen, wenn er nur die Adresse wiederholt', () => {
        expect(formatRecipient('max@example.com')).toBe('max@example.com');
        expect(formatRecipient('"max@example.com" <max@example.com>')).toBe('max@example.com');
        expect(formatRecipient('"MAX@example.com" <max@example.com>')).toBe('max@example.com');
    });

    it('liefert leeren String ohne Eingabe', () => {
        expect(formatRecipient(undefined)).toBe('');
    });
});

describe('infoAdresseZuDomain', () => {
    it('bildet die info@-Adresse zur Domain einer Unter-Adresse', () => {
        expect(infoAdresseZuDomain('bestellung@meier.de')).toBe('info@meier.de');
        expect(infoAdresseZuDomain('Max.Mustermann@baustoffe-mueller.de')).toBe('info@baustoffe-mueller.de');
    });

    it('kommt auch mit Anzeigenamen zurecht', () => {
        expect(infoAdresseZuDomain('"Muster GmbH" <lager@muster-gmbh.de>')).toBe('info@muster-gmbh.de');
    });

    it('vereinheitlicht die Schreibweise der Domain', () => {
        expect(infoAdresseZuDomain('Lager@Meier.DE')).toBe('info@meier.de');
    });

    it('liefert leeren String ohne erkennbare Domain', () => {
        expect(infoAdresseZuDomain('keine-adresse')).toBe('');
        expect(infoAdresseZuDomain('@ohne-lokalteil.de')).toBe('');
        expect(infoAdresseZuDomain('')).toBe('');
        expect(infoAdresseZuDomain(undefined)).toBe('');
    });
});

describe('istInfoAdresse', () => {
    it('erkennt die allgemeine Adresse unabhängig von der Schreibweise', () => {
        expect(istInfoAdresse('info@meier.de')).toBe(true);
        expect(istInfoAdresse('INFO@meier.de')).toBe(true);
        expect(istInfoAdresse('"Zentrale" <info@meier.de>')).toBe(true);
    });

    it('erkennt Fachabteilungen nicht als allgemeine Adresse', () => {
        expect(istInfoAdresse('bestellung@meier.de')).toBe(false);
        expect(istInfoAdresse('information@meier.de')).toBe(false);
        expect(istInfoAdresse('')).toBe(false);
        expect(istInfoAdresse(undefined)).toBe(false);
    });
});

describe('waehleInfoEmpfaenger', () => {
    it('bevorzugt die info@-Adresse, egal an welcher Stelle sie steht', () => {
        expect(waehleInfoEmpfaenger(['bestellung@meier.de', 'info@meier.de'])).toBe('info@meier.de');
    });

    it('gibt die reine Adresse ohne Anzeigenamen zurück', () => {
        expect(waehleInfoEmpfaenger(['"Zentrale" <info@meier.de>'])).toBe('info@meier.de');
    });

    it('nimmt die erste Adresse, wenn es keine info@ gibt', () => {
        expect(waehleInfoEmpfaenger(['bestellung@meier.de', 'lager@meier.de'])).toBe('bestellung@meier.de');
    });

    it('liefert leeren String ohne hinterlegte Adressen', () => {
        expect(waehleInfoEmpfaenger([])).toBe('');
        expect(waehleInfoEmpfaenger(undefined)).toBe('');
        expect(waehleInfoEmpfaenger([undefined, ''])).toBe('');
    });
});

describe('parseRecipientList', () => {
    it('behandelt Whitespace-Strings und reine Klammer-Adressen ohne Namen', () => {
        expect(parseRecipientList('   ')).toEqual([]);
        const list = parseRecipientList('<only-email@example.com>');
        expect(list).toHaveLength(1);
        expect(list[0].email).toBe('only-email@example.com');
        expect(list[0].displayName).toBe('only-email@example.com');
    });
    it('parst Namen mit Apostroph ("O\x27Connor" <o@example.com>) korrekt', () => {
        const list = parseRecipientList('"O\x27Connor" <oconnor@example.com>');
        expect(list).toHaveLength(1);
        expect(list[0].email).toBe('oconnor@example.com');
        expect(list[0].displayName).toBe("O'Connor");
    });

    it('parst Namen mit Apostroph ohne Anführungszeichen (O\x27Connor <o@example.com>)', () => {
        const list = parseRecipientList("O'Connor <oconnor@example.com>");
        expect(list).toHaveLength(1);
        expect(list[0].email).toBe('oconnor@example.com');
        expect(list[0].displayName).toBe("O'Connor");
    });

    it('parst mehrere Empfänger inklusive Apostroph und Kommas in Namen', () => {
        const input = '"O\x27Connor, Sean" <oconnor@example.com>, "Mustermann, Max" <max@example.com>, erika@example.com';
        const list = parseRecipientList(input);
        expect(list).toHaveLength(3);
        expect(list[0].email).toBe('oconnor@example.com');
        expect(list[0].displayName).toBe("O'Connor, Sean");
        expect(list[1].email).toBe('max@example.com');
        expect(list[1].displayName).toBe('Mustermann, Max');
        expect(list[2].email).toBe('erika@example.com');
        expect(list[2].displayName).toBe('erika@example.com');
    });
});

describe('formatRecipientList', () => {
    it('fällt auf r.raw zurück, wenn formatRecipient einen leeren String liefert', () => {
        expect(formatRecipientList('keine-adresse')).toBe('keine-adresse');
        expect(formatRecipientList('keine-adresse-1, keine-adresse-2')).toBe('keine-adresse-1, keine-adresse-2');
    });
    it('behält vorhandene Anzeigenamen auch ohne expliziten singleNameOverride bei', () => {
        expect(formatRecipientList('"Mustermann" <max@example.com>')).toBe('"Mustermann" <max@example.com>');
        expect(formatRecipientList('max@example.com')).toBe('max@example.com');
    });

    it('gibt den getrimmten String zurück, wenn keine gültigen Adressen geparst werden konnten', () => {
        expect(formatRecipientList(',,,')).toBe(',,,');
        expect(formatRecipientList('   ')).toBe('');
    });

    it('formatiert gemischte Listen mit und ohne Namen', () => {
        const input = '"Anna" <anna@example.com>, ben@example.com';
        expect(formatRecipientList(input)).toBe('"Anna" <anna@example.com>, ben@example.com');
    });
    it('behält bei Rundmails alle Empfänger und vertauscht/verliert keine Namen', () => {
        const input = '"Anna" <anna@example.com>, "Ben" <ben@example.com>';
        const formatted = formatRecipientList(input, 'Schlotz Architekten');
        // Bei mehreren Empfängern darf der pauschale Kundenname NICHT auf alle angewendet werden
        expect(formatted).toBe('"Anna" <anna@example.com>, "Ben" <ben@example.com>');
    });

    it('wendet den hinterlegten Kundennamen nur bei genau einem Empfänger an', () => {
        const formatted = formatRecipientList('kunde@schlotz-architekten.de', 'Schlotz Architekten');
        expect(formatted).toBe('"Schlotz Architekten" <kunde@schlotz-architekten.de>');
    });

    it('formatiert reine Adresslisten ohne Namen sauber', () => {
        const input = 'anna@example.com, ben@example.com';
        expect(formatRecipientList(input)).toBe('anna@example.com, ben@example.com');
    });

    it('liefert leeren String bei leerer Eingabe', () => {
        expect(formatRecipientList('')).toBe('');
        expect(formatRecipientList(undefined)).toBe('');
    });
});

describe('escapeHtml', () => {
    it('maskiert spitze Klammern, damit E-Mail-Adressen nicht als HTML-Tags verschwinden', () => {
        const header = 'Am 11.09.2026, 16:00 Uhr schrieben Sie an "Anna" <anna@example.com>:';
        const escaped = escapeHtml(header);
        expect(escaped).toBe('Am 11.09.2026, 16:00 Uhr schrieben Sie an &quot;Anna&quot; &lt;anna@example.com&gt;:');
        expect(escaped).not.toContain('<anna@example.com>');
    });

    it('maskiert alle HTML-relevanten Sonderzeichen (&, <, >, ", \')', () => {
        expect(escapeHtml('<script>alert("XSS & test \'")</script>'))
            .toBe('&lt;script&gt;alert(&quot;XSS &amp; test &#039;&quot;)&lt;/script&gt;');
    });

    it('behandelt leere oder undefinierte Werte', () => {
        expect(escapeHtml('')).toBe('');
        expect(escapeHtml(undefined)).toBe('');
    });
});
