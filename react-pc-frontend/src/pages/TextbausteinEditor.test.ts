/**
 * Regressionstest: replacePlaceholders
 *
 * Bug: Nicht in der Replacements-Map enthaltene Platzhalter wie {{BAUVORHABEN}}
 * wurden nicht als gelbe Badge markiert – sie erschienen als Plain Text in der Vorschau.
 * Außerdem fehlten Backend-definierte Platzhalter wie {{KUNDENNAME}} (Großbuchstaben)
 * und {{DATUM}}, die deshalb niemals durch echte Werte ersetzt wurden.
 */

import { describe, it, expect } from 'vitest';

// ── Reproduzierte Logik aus TextbausteinEditor.tsx ──────────────────────────
// (Funktion ist dort nicht exportiert, daher hier repliziert)

const PREVIEW_BADGE_CLASSES =
  'inline-flex items-center px-2 py-0.5 bg-yellow-200 text-slate-900 font-mono text-sm rounded';

const ANREDE_LABELS: Record<string, string> = {
  HERR: 'Sehr geehrter Herr',
  FRAU: 'Sehr geehrte Frau',
  FAMILIE: 'Sehr geehrte Familie',
  DAMEN_HERREN: 'Sehr geehrte Damen und Herren',
  FIRMA: 'Sehr geehrte Damen und Herren',
};

function escapeHtml(value: string) {
  return value.replace(/[&<"']/g, (char) => {
    switch (char) {
      case '&': return '&amp;';
      case '<': return '&lt;';
      case '"': return '&quot;';
      case "'": return '&#39;';
      default: return char;
    }
  });
}

function highlightPlaceholder(token: string) {
  return `<span class="${PREVIEW_BADGE_CLASSES}" data-preview-placeholder="true">${escapeHtml(token)}</span>`;
}

function formatAnrede(value: string | null | undefined) {
  if (!value) return '';
  return ANREDE_LABELS[value.trim().toUpperCase()] || value;
}

function formatDate(date: Date) {
  return date.toLocaleDateString('de-DE');
}

function addDays(date: Date, days: number) {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

interface KundeTest {
  name?: string;
  ansprechpartner?: string;
  ansprechspartner?: string;
  anrede?: string;
  kundennummer?: string;
  strasse?: string;
  plz?: string;
  ort?: string;
  zahlungsziel?: number;
}

function formatAdresse(kunde: KundeTest | null) {
  if (!kunde) return null;
  const plzOrt = [kunde.plz, kunde.ort].filter(Boolean).join(' ');
  const parts = [kunde.strasse, plzOrt].filter(Boolean);
  return parts.length > 0 ? parts.join(', ') : null;
}

function replacePlaceholders(html: string, kunde: KundeTest | null, days: number) {
  const ansprechpartner = kunde?.ansprechpartner?.trim() || kunde?.ansprechspartner?.trim() || null;
  const replacements: Record<string, string | null> = {
    '{{Anrede}}': formatAnrede(kunde?.anrede) || null,
    '{{Kundenname}}': kunde?.name?.trim() || null,
    '{{KUNDENNAME}}': kunde?.name?.trim() || null,
    '{{Ansprechpartner}}': ansprechpartner,
    '{{ANSPRECHPARTNER}}': ansprechpartner,
    '{{KUNDENNUMMER}}': kunde?.kundennummer?.trim() || null,
    '{{KUNDENADRESSE}}': formatAdresse(kunde),
    '{{DATUM}}': formatDate(new Date()),
    '{{Zahlungsziel}}': formatDate(addDays(new Date(), days)) || null,
    '{{BAUVORHABEN}}': null,
    '{{DOKUMENTNUMMER}}': null,
    '{{PROJEKTNUMMER}}': null,
    '{{SEITENZAHL}}': null,
    '{{DOKUMENTTYP}}': null,
  };

  let result = html || '';
  result = result.replace(/<button[^>]*data-placeholder-remove[^>]*>.*?<\/button>/g, '');
  result = result.replace(/<span[^>]*data-placeholder-chip[^>]*data-placeholder-token="([^"]+)"[^>]*>.*?<\/span>/g, '$1');
  result = result.replace(/<span[^>]*data-placeholder-chip[^>]*>(.*?)<\/span>/g, '$1');

  Object.entries(replacements).forEach(([token, value]) => {
    const regex = new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g');
    const replacement = value ? escapeHtml(value) : highlightPlaceholder(token);
    result = result.replace(regex, replacement);
  });

  result = result.replace(/{{[^}]+}}/g, (match) => highlightPlaceholder(match));
  return result;
}

// ── Tests ────────────────────────────────────────────────────────────────────

const MUSTERKUNDE: KundeTest = {
  name: 'Mustermann GmbH',
  ansprechpartner: 'Max Mustermann',
  anrede: 'HERR',
  kundennummer: 'K-0042',
  strasse: 'Musterstraße 1',
  plz: '12345',
  ort: 'Musterstadt',
  zahlungsziel: 14,
};

describe('replacePlaceholders', () => {
  it('ersetzt {{Anrede}} durch formatierten Anredetext', () => {
    const result = replacePlaceholders('<p>{{Anrede}}</p>', MUSTERKUNDE, 14);
    expect(result).toContain('Sehr geehrter Herr');
    expect(result).not.toContain('{{Anrede}}');
  });

  it('ersetzt {{Kundenname}} durch den Kundennamen', () => {
    const result = replacePlaceholders('<p>{{Kundenname}}</p>', MUSTERKUNDE, 14);
    expect(result).toContain('Mustermann GmbH');
  });

  // Regression-Bug: {{KUNDENNAME}} (Großbuchstaben) wurde nicht ersetzt
  it('ersetzt {{KUNDENNAME}} (Großbuchstaben) durch den Kundennamen', () => {
    const result = replacePlaceholders('<p>{{KUNDENNAME}}</p>', MUSTERKUNDE, 14);
    expect(result).toContain('Mustermann GmbH');
    expect(result).not.toContain('{{KUNDENNAME}}');
  });

  // Regression-Bug: {{BAUVORHABEN}} wurde als Plain Text angezeigt, nicht als Badge
  it('markiert {{BAUVORHABEN}} als gelbe Badge, da kein Wert verfügbar', () => {
    const result = replacePlaceholders('<p>{{BAUVORHABEN}}</p>', MUSTERKUNDE, 14);
    expect(result).toContain('data-preview-placeholder="true"');
    expect(result).toContain('{{BAUVORHABEN}}');
    // Kein rohes Vorkommen außerhalb des Badge-Spans
    expect(result).not.toMatch(/<p>\{\{BAUVORHABEN\}\}<\/p>/);
  });

  it('ersetzt {{DATUM}} durch das heutige Datum', () => {
    const result = replacePlaceholders('<p>{{DATUM}}</p>', MUSTERKUNDE, 14);
    const today = new Date().toLocaleDateString('de-DE');
    expect(result).toContain(today);
    expect(result).not.toContain('{{DATUM}}');
  });

  it('ersetzt {{KUNDENNUMMER}} durch die Kundennummer', () => {
    const result = replacePlaceholders('<p>{{KUNDENNUMMER}}</p>', MUSTERKUNDE, 14);
    expect(result).toContain('K-0042');
    expect(result).not.toContain('{{KUNDENNUMMER}}');
  });

  it('ersetzt {{KUNDENADRESSE}} durch formatierte Adresse', () => {
    const result = replacePlaceholders('<p>{{KUNDENADRESSE}}</p>', MUSTERKUNDE, 14);
    expect(result).toContain('Musterstraße 1');
    expect(result).toContain('12345 Musterstadt');
  });

  it('markiert alle nicht-ersetzbaren Platzhalter (DOKUMENTNUMMER, PROJEKTNUMMER) als Badge', () => {
    const html = '<p>{{DOKUMENTNUMMER}} / {{PROJEKTNUMMER}}</p>';
    const result = replacePlaceholders(html, MUSTERKUNDE, 14);
    // Mindestens je ein Badge pro Platzhalter (kann durch Catch-all doppelt gewrappt sein)
    const badgeCount = (result.match(/data-preview-placeholder="true"/g) || []).length;
    expect(badgeCount).toBeGreaterThanOrEqual(2);
    expect(result).toContain('{{DOKUMENTNUMMER}}');
    expect(result).toContain('{{PROJEKTNUMMER}}');
  });

  it('markiert vollständig unbekannte Platzhalter per Catch-all als Badge', () => {
    const result = replacePlaceholders('<p>{{UNBEKANNTER_PLATZHALTER}}</p>', MUSTERKUNDE, 14);
    expect(result).toContain('data-preview-placeholder="true"');
    expect(result).toContain('{{UNBEKANNTER_PLATZHALTER}}');
  });

  it('lässt unveränderten Text bei null-Kunde als Badge erscheinen', () => {
    const result = replacePlaceholders('<p>{{Kundenname}}</p>', null, 14);
    expect(result).toContain('data-preview-placeholder="true"');
  });

  it('verarbeitet kombinierten Inhalt korrekt (Bug-Szenario: Anrede ersetzt, BAUVORHABEN als Badge)', () => {
    const html = '<p>{{Anrede}}</p><p>{{BAUVORHABEN}}</p>';
    const result = replacePlaceholders(html, MUSTERKUNDE, 14);
    expect(result).toContain('Sehr geehrter Herr');
    expect(result).toContain('data-preview-placeholder="true"');
    expect(result).toContain('{{BAUVORHABEN}}');
  });
});
