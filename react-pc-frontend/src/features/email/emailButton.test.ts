import { describe, expect, it } from 'vitest';
import {
  bereinigeButtonAdresse,
  ersetzeAdressPlatzhalterFuerVorschau,
  normalisiereEigeneAdresse,
} from './emailButton';

describe('bereinigeButtonAdresse', () => {
  it.each([
    ['https://www.beispiel.de', 'https://www.beispiel.de'],
    ['  http://beispiel.de  ', 'http://beispiel.de'],
    ['mailto:info@beispiel.de', 'mailto:info@beispiel.de'],
    ['tel:+49123456', 'tel:+49123456'],
    ['{{REVIEW_URL}}', '{{REVIEW_URL}}'],
    [' {{REVIEW_URL}} ', '{{REVIEW_URL}}'],
  ])('lässt %s zu', (eingabe, erwartet) => {
    expect(bereinigeButtonAdresse(eingabe)).toBe(erwartet);
  });

  it.each([
    'javascript:alert(1)',
    'data:text/html,x',
    'www.beispiel.de',
    '{{KUNDENNAME}}',
    'https://beispiel.de/{{KUNDENNAME}}',
    'https://beispiel.de/"onmouseover="x',
    '',
    null,
    undefined,
  ])(
    'verwirft %s',
    (eingabe) => {
      expect(bereinigeButtonAdresse(eingabe)).toBe('');
    }
  );
});

describe('normalisiereEigeneAdresse', () => {
  it('ergänzt https:// bei einer Adresse ohne Schema', () => {
    expect(normalisiereEigeneAdresse(' www.beispiel.de ')).toBe('https://www.beispiel.de');
  });

  it('behält eine vollständige Adresse', () => {
    expect(normalisiereEigeneAdresse('http://beispiel.de/kontakt')).toBe('http://beispiel.de/kontakt');
  });

  it.each([
    '',
    '   ',
    'kein link',
    'javascript:alert(1)',
    'mailto:info@beispiel.de',
    'localhost',
    'https://',
    'https://[',
    'www.beispiel.de/{{KUNDENNAME}}',
    'www.beispiel.de/<b>',
  ])(
    'lehnt %s ab',
    (eingabe) => {
      expect(normalisiereEigeneAdresse(eingabe)).toBeNull();
    }
  );
});

describe('ersetzeAdressPlatzhalterFuerVorschau', () => {
  it('ersetzt Platzhalter im href, lässt Text-Platzhalter stehen', () => {
    const html = '<a href="{{REVIEW_URL}}">{{KUNDENNAME}}</a>';
    expect(ersetzeAdressPlatzhalterFuerVorschau(html)).toBe('<a href="#">{{KUNDENNAME}}</a>');
  });
});
