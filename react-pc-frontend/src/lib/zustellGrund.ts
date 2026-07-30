/**
 * Übersetzt den technischen Servertext eines Rückläufers in einen Satz, mit dem
 * ein Handwerker etwas anfangen kann.
 *
 * Hintergrund: Was der ablehnende Mailserver zurückschickt, sieht aus wie
 * "550 5.1.1 <...> User unknown" — für den Nutzer wertlos. Er will wissen, was
 * er jetzt tun soll. Der Rohtext bleibt im title-Attribut erhalten, falls doch
 * jemand nachschauen will.
 */
export function klartextGrund(rohtext?: string): string {
    const standard =
        'Diese E-Mail konnte nicht zugestellt werden. Bitte E-Mail-Adresse prüfen und erneut senden.';
    if (!rohtext) return standard;

    const t = rohtext.toLowerCase();

    if (
        t.includes('unknown user') ||
        t.includes('user unknown') ||
        t.includes('no such user') ||
        t.includes('existiert nicht') ||
        t.includes('does not exist') ||
        t.includes('mailbox not found') ||
        t.includes('unbekannter empf')
    ) {
        return 'Diese E-Mail-Adresse gibt es nicht. Bitte Adresse prüfen und erneut senden.';
    }

    if (t.includes('host unknown') || t.includes('domain not found') || t.includes('unrouteable')) {
        return 'Den Anbieter dieser E-Mail-Adresse gibt es nicht. Bitte Adresse prüfen und erneut senden.';
    }

    if (t.includes('rejected')) {
        return 'Der Empfänger-Server hat die E-Mail abgelehnt. Bitte Adresse prüfen und erneut senden.';
    }

    return standard;
}
