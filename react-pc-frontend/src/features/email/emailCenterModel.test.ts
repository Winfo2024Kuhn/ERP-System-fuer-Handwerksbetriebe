import { describe, expect, it } from 'vitest';
import { emailEndpointWithMailbox } from './emailCenterModel';

describe('emailEndpointWithMailbox', () => {
    it('adds the selected mailbox while preserving folder pagination', () => {
        expect(emailEndpointWithMailbox('/api/emails/inbox?offset=0&limit=50', 'EINKAUF'))
            .toBe('/api/emails/inbox?offset=0&limit=50&kontoId=EINKAUF');
    });

    it('leaves requests unfiltered for all mailboxes', () => {
        expect(emailEndpointWithMailbox('/api/emails/search?q=angebot', undefined))
            .toBe('/api/emails/search?q=angebot');
    });
});
