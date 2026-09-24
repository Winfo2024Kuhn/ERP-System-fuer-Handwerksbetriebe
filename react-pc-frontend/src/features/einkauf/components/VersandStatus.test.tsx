import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { VersandStatus } from './VersandStatus';

describe('VersandStatus', () => {
  it('marks an unclear send and offers clarification without a retry action', () => {
    render(<VersandStatus versand={{ id: 7, version: 2, typ: 'ANFRAGE', vorgangId: 12, revisionId: 3, status: 'UNKLAR', fehlerCode: 'TIMEOUT', erstelltAm: '2026-09-23T10:00:00Z', angenommenAm: null, archiviert: false, messageId: null }} onKlaeren={vi.fn()} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Versandstatus unklar');
    expect(screen.getByRole('button', { name: 'Versand klären' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /erneut senden|wiederholen/i })).not.toBeInTheDocument();
  });

  it('shows a clear success status after acceptance', () => {
    render(<VersandStatus versand={{ id: 8, version: 1, typ: 'BESTELLUNG', vorgangId: 14, revisionId: 9, status: 'ANGENOMMEN', fehlerCode: null, erstelltAm: '2026-09-23T10:00:00Z', angenommenAm: '2026-09-23T10:00:03Z', archiviert: true, messageId: '<test@example.com>' }} />);
    expect(screen.getByRole('status')).toHaveTextContent('Versand angenommen');
    expect(screen.getByText('Message-ID: <test@example.com>')).toBeInTheDocument();
  });
});
