import {
    escapeHtml,
    kontrastFarbe,
    mailtoHref,
    sichereBildUrl,
    sichereFarbe,
    sichereLinkUrl,
    sozialUrl,
    telHref,
    websiteAnzeige,
    whatsappUrl,
} from './signaturSicherheit';
import { schriftartCss, type SignaturDaten, type VorlagenId } from './signaturTypen';

/**
 * Erzeugt aus dem Formularzustand fertiges Signatur-HTML.
 *
 * E-Mail-Programme (Outlook, Gmail, Apple Mail) verstehen nur einen kleinen
 * Teil von HTML/CSS. Darum: verschachtelte Tabellen statt div-Layout, nur
 * Inline-Styles, websichere Schriften, feste Bildbreiten über das
 * width-Attribut, keine SVG-Icons und keine fremden Icon-Server. Alle
 * Eingaben werden escaped, Links und Bilder vorher geprüft.
 * Leere Felder fallen ersatzlos weg.
 */

const TEXT = '#292524';
const GRAU = '#57534e';
const HELLGRAU = '#78716c';
const LINIE = '#e7e5e4';
const TRENNER = '#d6d3d1';

interface Stil {
    farbe: string;
    kontrast: string;
    schrift: string;
}

interface Kontakt {
    kuerzel: string;
    bezeichnung: string;
    text: string;
    href: string;
}

const tabelle = (inhalt: string, stil = '', breite = '') =>
    `<table cellpadding="0" cellspacing="0" border="0" role="presentation"${breite ? ` width="${breite}"` : ''} style="border-collapse:collapse;${stil}">${inhalt}</table>`;

const abstand = (hoehe: number) =>
    `<tr><td height="${hoehe}" style="height:${hoehe}px;font-size:0;line-height:0;">&nbsp;</td></tr>`;

const verlinkt = (text: string, href: string, farbe: string) => href
    ? `<a href="${escapeHtml(href)}" style="color:${farbe};text-decoration:none;">${escapeHtml(text)}</a>`
    : `<span style="color:${farbe};">${escapeHtml(text)}</span>`;

function kontakte(d: SignaturDaten): Kontakt[] {
    const liste: Kontakt[] = [];
    const telefon = d.telefon.trim();
    const mobil = d.mobil.trim();
    const fax = d.fax.trim();
    const email = d.email.trim();
    const website = sichereLinkUrl(d.website);
    const adresse = d.adresse.trim();
    if (telefon) liste.push({ kuerzel: 'T', bezeichnung: 'Tel.', text: telefon, href: telHref(telefon) });
    if (mobil) liste.push({ kuerzel: 'M', bezeichnung: 'Mobil', text: mobil, href: telHref(mobil) });
    if (fax) liste.push({ kuerzel: 'F', bezeichnung: 'Fax', text: fax, href: '' });
    if (email) liste.push({ kuerzel: '@', bezeichnung: 'E-Mail', text: email, href: mailtoHref(email) });
    if (website) liste.push({ kuerzel: 'W', bezeichnung: 'Web', text: websiteAnzeige(d.website), href: website });
    if (adresse) liste.push({ kuerzel: 'A', bezeichnung: 'Adresse', text: adresse, href: '' });
    return liste;
}

function sozialeLinks(d: SignaturDaten): { titel: string; href: string }[] {
    return [
        { titel: 'Facebook', href: sozialUrl('facebook', d.social.facebook) },
        { titel: 'Instagram', href: sozialUrl('instagram', d.social.instagram) },
        { titel: 'LinkedIn', href: sozialUrl('linkedin', d.social.linkedin) },
        { titel: 'WhatsApp', href: whatsappUrl(d.social.whatsapp) },
    ].filter((link) => link.href);
}

/** Kleine farbige Knöpfe mit Text – funktionieren ohne Bilder in jedem Programm. */
function sozialeKnoepfe(d: SignaturDaten, s: Stil): string {
    const links = sozialeLinks(d);
    if (links.length === 0) return '';
    const zellen = links.map((link, i) =>
        `${i > 0 ? '<td width="6" style="width:6px;font-size:0;line-height:0;">&nbsp;</td>' : ''}`
        + `<td style="background-color:${s.farbe};border-radius:4px;padding:4px 9px;">`
        + `<a href="${escapeHtml(link.href)}" style="color:${s.kontrast};text-decoration:none;font-size:11px;font-weight:bold;font-family:${s.schrift};">${link.titel}</a></td>`,
    ).join('');
    return tabelle(`<tr>${zellen}</tr>`);
}

/** Soziale Netzwerke als schlichte Textlinks („Facebook · Instagram“). */
function sozialeTextLinks(d: SignaturDaten, s: Stil): string {
    const links = sozialeLinks(d);
    if (links.length === 0) return '';
    return links
        .map((link) => `<a href="${escapeHtml(link.href)}" style="color:${s.farbe};text-decoration:none;font-weight:bold;">${link.titel}</a>`)
        .join(`<span style="color:${TRENNER};">&nbsp;·&nbsp;</span>`);
}

function bild(src: string, breite: number, alt: string, extraStil = ''): string {
    return `<img src="${escapeHtml(src)}" width="${breite}" alt="${escapeHtml(alt)}" style="display:block;width:${breite}px;max-width:${breite}px;height:auto;border:0;outline:none;text-decoration:none;${extraStil}">`;
}

function zeile(inhalt: string, stil: string): string {
    return `<p style="margin:0;${stil}">${inhalt}</p>`;
}

function kleingedrucktes(d: SignaturDaten, s: Stil): string {
    const zusatz = d.zusatz.trim();
    if (!zusatz) return '';
    const zeilen = zusatz.split(/\r?\n/).map((z) => escapeHtml(z.trim())).filter(Boolean).join('<br>');
    return zeile(zeilen, `font-family:${s.schrift};font-size:11px;line-height:16px;color:${HELLGRAU};`);
}

function slogan(d: SignaturDaten, s: Stil, groesse = 13): string {
    const text = d.slogan.trim();
    if (!text) return '';
    return zeile(escapeHtml(text), `font-family:${s.schrift};font-size:${groesse}px;line-height:${groesse + 7}px;font-style:italic;color:${s.farbe};`);
}

/** Text-Zeilen untereinander, leere Teile fallen weg, dazwischen Abstand. */
function stapel(teile: string[], luecke: number, stil = ''): string {
    const gefuellt = teile.filter(Boolean);
    if (gefuellt.length === 0) return '';
    return tabelle(gefuellt.map((teil, i) => `${i > 0 ? abstand(luecke) : ''}<tr><td>${teil}</td></tr>`).join(''), stil);
}

function abteilungUndFirma(d: SignaturDaten, s: Stil, groesse: number): string {
    const teile = [d.abteilung.trim(), d.firma.trim()].filter(Boolean).map(escapeHtml);
    if (teile.length === 0) return '';
    return zeile(
        teile.join(`<span style="color:${TRENNER};">&nbsp;|&nbsp;</span>`),
        `font-family:${s.schrift};font-size:${groesse}px;line-height:${groesse + 8}px;color:${GRAU};`,
    );
}

// ---------------------------------------------------------------------------
// Vorlage 1: Klassisch mit Foto
// ---------------------------------------------------------------------------

function klassischMitFoto(d: SignaturDaten, s: Stil): string {
    const foto = sichereBildUrl(d.fotoUrl);
    const logo = sichereBildUrl(d.logoUrl);

    const bildSpalte = foto || logo
        ? `<td valign="top" style="vertical-align:top;padding-right:30px;">${tabelle([
            foto ? `<tr><td align="center">${bild(foto, 130, d.name.trim() || 'Foto', 'border-radius:12px;')}</td></tr>` : '',
            foto && logo ? abstand(16) : '',
            logo ? `<tr><td align="center">${bild(logo, 130, d.firma.trim() || 'Logo')}</td></tr>` : '',
        ].join(''))}</td>`
        : '';

    const kopf = [
        d.name.trim() ? zeile(escapeHtml(d.name.trim()), `font-family:${s.schrift};font-size:20px;line-height:26px;font-weight:bold;color:${s.farbe};`) : '',
        d.position.trim() ? zeile(escapeHtml(d.position.trim()), `font-family:${s.schrift};font-size:15px;line-height:22px;color:${TEXT};`) : '',
        abteilungUndFirma(d, s, 15),
    ].join('');

    const liste = kontakte(d);
    const kontaktTabelle = liste.length > 0
        ? tabelle(liste.map((k, i) => `${i > 0 ? abstand(6) : ''}<tr>`
            + `<td width="28" valign="middle" style="width:28px;vertical-align:middle;">${tabelle(
                `<tr><td width="20" height="20" align="center" valign="middle" style="width:20px;height:20px;background-color:${s.farbe};color:${s.kontrast};font-family:Arial, Helvetica, sans-serif;font-size:11px;line-height:20px;font-weight:bold;text-align:center;border-radius:4px;">${escapeHtml(k.kuerzel)}</td></tr>`,
            )}</td>`
            + `<td valign="middle" style="vertical-align:middle;font-family:${s.schrift};font-size:14px;line-height:20px;color:${GRAU};">${verlinkt(k.text, k.href, GRAU)}</td>`
            + '</tr>').join(''))
        : '';

    const trennlinie = kopf && (kontaktTabelle || sozialeLinks(d).length > 0)
        ? tabelle(`${abstand(14)}<tr><td style="border-top:1px solid ${LINIE};height:1px;font-size:0;line-height:0;">&nbsp;</td></tr>${abstand(14)}`, 'width:100%;', '100%')
        : '';

    const fuss = stapel([sozialeKnoepfe(d, s), slogan(d, s), kleingedrucktes(d, s)], 10);
    const textSpalte = `<td valign="middle" style="vertical-align:middle;">${[
        kopf,
        trennlinie,
        kontaktTabelle,
        fuss ? tabelle(`${kopf || kontaktTabelle ? abstand(14) : ''}<tr><td>${fuss}</td></tr>`) : '',
    ].join('')}</td>`;

    return tabelle(`<tr>${bildSpalte}${textSpalte}</tr>`, `font-family:${s.schrift};`);
}

// ---------------------------------------------------------------------------
// Vorlage 2: Modern mit Farbbalken
// ---------------------------------------------------------------------------

function kontaktZeilenMitKuerzel(d: SignaturDaten, s: Stil, groesse: number): string {
    return kontakte(d).map((k) => zeile(
        `<span style="color:${s.farbe};font-weight:bold;">${escapeHtml(k.kuerzel)}</span>&nbsp;&nbsp;${verlinkt(k.text, k.href, GRAU)}`,
        `font-family:${s.schrift};font-size:${groesse}px;line-height:${groesse + 8}px;color:${GRAU};`,
    )).join('');
}

function modernMitBalken(d: SignaturDaten, s: Stil): string {
    const foto = sichereBildUrl(d.fotoUrl);
    const logo = sichereBildUrl(d.logoUrl);
    const kopf = [
        d.name.trim() ? zeile(escapeHtml(d.name.trim()), `font-family:${s.schrift};font-size:19px;line-height:25px;font-weight:bold;color:${TEXT};`) : '',
        d.position.trim() ? zeile(escapeHtml(d.position.trim()), `font-family:${s.schrift};font-size:12px;line-height:18px;font-weight:bold;letter-spacing:1px;text-transform:uppercase;color:${s.farbe};`) : '',
        abteilungUndFirma(d, s, 13),
    ].join('');
    const inhalt = stapel([kopf, kontaktZeilenMitKuerzel(d, s, 13), sozialeKnoepfe(d, s)], 10);

    const zellen = [
        foto ? `<td valign="middle" style="vertical-align:middle;padding-right:18px;">${bild(foto, 96, d.name.trim() || 'Foto', 'border-radius:50%;')}</td>` : '',
        `<td width="4" style="width:4px;background-color:${s.farbe};font-size:0;line-height:0;">&nbsp;</td>`,
        `<td valign="middle" style="vertical-align:middle;padding-left:18px;">${inhalt}</td>`,
        logo ? `<td valign="middle" style="vertical-align:middle;padding-left:28px;">${bild(logo, 110, d.firma.trim() || 'Logo')}</td>` : '',
    ].join('');

    const unten = stapel([slogan(d, s), kleingedrucktes(d, s)], 6);
    return tabelle(
        `<tr><td>${tabelle(`<tr>${zellen}</tr>`)}</td></tr>${unten ? `${abstand(14)}<tr><td>${unten}</td></tr>` : ''}`,
        `font-family:${s.schrift};`,
    );
}

// ---------------------------------------------------------------------------
// Vorlage 3: Kompakt
// ---------------------------------------------------------------------------

function kompakt(d: SignaturDaten, s: Stil): string {
    const logo = sichereBildUrl(d.logoUrl);
    const trenner = `<span style="color:${TRENNER};">&nbsp;|&nbsp;</span>`;
    const punkt = `<span style="color:${TRENNER};">&nbsp;·&nbsp;</span>`;

    const kopfTeile = [
        d.name.trim() ? `<strong style="color:${s.farbe};">${escapeHtml(d.name.trim())}</strong>` : '',
        d.position.trim() ? `<span style="color:${GRAU};">${escapeHtml(d.position.trim())}</span>` : '',
        d.firma.trim() ? `<span style="color:${GRAU};">${escapeHtml(d.firma.trim())}</span>` : '',
    ].filter(Boolean);
    const kopf = kopfTeile.length > 0
        ? zeile(kopfTeile.join(trenner), `font-family:${s.schrift};font-size:14px;line-height:21px;color:${TEXT};`)
        : '';

    const liste = kontakte(d).filter((k) => k.kuerzel !== 'A');
    const kontaktZeile = liste.length > 0
        ? zeile(
            liste.map((k) => `<span style="color:${s.farbe};font-weight:bold;">${escapeHtml(k.kuerzel)}</span>&nbsp;${verlinkt(k.text, k.href, GRAU)}`).join(punkt),
            `font-family:${s.schrift};font-size:12px;line-height:19px;color:${GRAU};`,
        )
        : '';
    const adresse = d.adresse.trim()
        ? zeile(escapeHtml(d.adresse.trim()), `font-family:${s.schrift};font-size:12px;line-height:19px;color:${HELLGRAU};`)
        : '';
    const social = sozialeTextLinks(d, s);
    const sozialZeile = social ? zeile(social, `font-family:${s.schrift};font-size:12px;line-height:19px;`) : '';

    const textBlock = stapel([kopf + kontaktZeile + adresse, sozialZeile, slogan(d, s, 12), kleingedrucktes(d, s)], 4);
    const zellen = [
        logo ? `<td valign="middle" style="vertical-align:middle;padding-right:14px;">${bild(logo, 72, d.firma.trim() || 'Logo')}</td>` : '',
        `<td valign="middle" style="vertical-align:middle;">${textBlock}</td>`,
    ].join('');
    return tabelle(`<tr>${zellen}</tr>`, `font-family:${s.schrift};`);
}

// ---------------------------------------------------------------------------
// Vorlage 4: Mit Banner
// ---------------------------------------------------------------------------

function mitBanner(d: SignaturDaten, s: Stil): string {
    const logo = sichereBildUrl(d.logoUrl);
    const banner = sichereBildUrl(d.bannerUrl);
    const bannerLink = sichereLinkUrl(d.bannerLink);

    const kopf = [
        d.name.trim() ? zeile(escapeHtml(d.name.trim()), `font-family:${s.schrift};font-size:18px;line-height:24px;font-weight:bold;color:${s.farbe};`) : '',
        d.position.trim() ? zeile(escapeHtml(d.position.trim()), `font-family:${s.schrift};font-size:14px;line-height:21px;color:${TEXT};`) : '',
        abteilungUndFirma(d, s, 14),
    ].join('');
    const inhalt = stapel([kopf, kontaktZeilenMitKuerzel(d, s, 13), sozialeKnoepfe(d, s)], 10);

    const oben = tabelle(`<tr><td valign="top" style="vertical-align:top;">${inhalt}</td>${logo
        ? `<td valign="top" style="vertical-align:top;padding-left:32px;">${bild(logo, 120, d.firma.trim() || 'Logo')}</td>`
        : ''}</tr>`);

    const bannerBild = banner ? bild(banner, 560, d.firma.trim() ? `Banner ${d.firma.trim()}` : 'Banner', 'width:100%;max-width:560px;border-radius:6px;') : '';
    const bannerZeile = bannerBild
        ? (bannerLink ? `<a href="${escapeHtml(bannerLink)}" style="text-decoration:none;">${bannerBild}</a>` : bannerBild)
        : '';
    const unten = stapel([slogan(d, s), kleingedrucktes(d, s)], 6);

    return tabelle(
        `<tr><td>${oben}</td></tr>`
        + (bannerZeile ? `${abstand(16)}<tr><td>${bannerZeile}</td></tr>` : '')
        + (unten ? `${abstand(12)}<tr><td>${unten}</td></tr>` : ''),
        `font-family:${s.schrift};max-width:560px;`,
    );
}

// ---------------------------------------------------------------------------
// Vorlage 5: Schlicht
// ---------------------------------------------------------------------------

function schlicht(d: SignaturDaten, s: Stil): string {
    const logo = sichereBildUrl(d.logoUrl);
    const textStil = `font-family:${s.schrift};font-size:13px;line-height:20px;color:${TEXT};`;

    const person = [
        d.name.trim() ? `<strong style="font-size:14px;color:${s.farbe};">${escapeHtml(d.name.trim())}</strong>` : '',
        d.position.trim() ? escapeHtml(d.position.trim()) : '',
        d.abteilung.trim() ? escapeHtml(d.abteilung.trim()) : '',
    ].filter(Boolean).join('<br>');

    const betrieb = [
        d.firma.trim() ? `<strong>${escapeHtml(d.firma.trim())}</strong>` : '',
        d.adresse.trim() ? escapeHtml(d.adresse.trim()) : '',
    ].filter(Boolean).join('<br>');

    const kontaktZeilen = kontakte(d)
        .filter((k) => k.kuerzel !== 'A')
        .map((k) => `<span style="color:${HELLGRAU};">${escapeHtml(k.bezeichnung)}:</span>&nbsp;${verlinkt(k.text, k.href, TEXT)}`)
        .join('<br>');

    const social = sozialeTextLinks(d, s);

    return stapel([
        person ? zeile(person, textStil) : '',
        betrieb ? zeile(betrieb, textStil) : '',
        kontaktZeilen ? zeile(kontaktZeilen, textStil) : '',
        social ? zeile(social, textStil) : '',
        slogan(d, s),
        logo ? bild(logo, 120, d.firma.trim() || 'Logo') : '',
        kleingedrucktes(d, s),
    ], 10, `font-family:${s.schrift};`);
}

const RENDERER: Record<VorlagenId, (d: SignaturDaten, s: Stil) => string> = {
    'klassisch-foto': klassischMitFoto,
    'modern-balken': modernMitBalken,
    kompakt,
    banner: mitBanner,
    schlicht,
};

/** Fertiges Signatur-HTML zur gewählten Vorlage. */
export function renderSignatur(daten: SignaturDaten): string {
    const farbe = sichereFarbe(daten.akzentfarbe);
    const stil: Stil = { farbe, kontrast: kontrastFarbe(farbe), schrift: schriftartCss(daten.schriftart) };
    const renderer = RENDERER[daten.vorlage] ?? klassischMitFoto;
    return renderer(daten, stil);
}

/** Hat die Signatur sichtbaren Inhalt (Text oder Bild)? */
export function hatInhalt(daten: SignaturDaten): boolean {
    const texte = [daten.name, daten.position, daten.abteilung, daten.firma, daten.telefon, daten.mobil, daten.fax,
        daten.email, daten.website, daten.adresse, daten.slogan, daten.zusatz];
    return texte.some((t) => t.trim()) || !!sichereBildUrl(daten.fotoUrl) || !!sichereBildUrl(daten.logoUrl)
        || !!sichereBildUrl(daten.bannerUrl);
}
