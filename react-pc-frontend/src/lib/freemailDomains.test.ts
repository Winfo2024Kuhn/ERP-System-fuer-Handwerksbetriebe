import { describe, expect, it } from 'vitest';
import { FREEMAIL_DOMAINS, istFreemailDomain } from './freemailDomains';

describe('istFreemailDomain', () => {
    it('erkennt gängige Anbieter unabhängig von Schreibweise und Leerzeichen', () => {
        expect(istFreemailDomain('gmail.com')).toBe(true);
        expect(istFreemailDomain(' GMX.DE ')).toBe(true);
        expect(istFreemailDomain('t-online.de')).toBe(true);
    });

    it('lässt Firmen-Domains und leere Werte durch', () => {
        expect(istFreemailDomain('musterstahl.example')).toBe(false);
        expect(istFreemailDomain('')).toBe(false);
        expect(istFreemailDomain(undefined)).toBe(false);
        expect(istFreemailDomain(null)).toBe(false);
    });

    it('führt nur kleingeschriebene Domains ohne Leerzeichen', () => {
        for (const domain of FREEMAIL_DOMAINS) {
            expect(domain).toBe(domain.trim().toLowerCase());
        }
    });
});
