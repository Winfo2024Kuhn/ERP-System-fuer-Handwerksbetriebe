import { fireEvent, render, screen } from '@testing-library/react';
import { MailkontoFields, type MailkontoDraft, type MailkontoPasswordState } from './MailkontoFields';

const value: MailkontoDraft = {
    aktiv: false, fromAddress: 'einkauf@example.invalid', fromName: 'Einkauf',
    smtpHost: 'smtp.example.invalid', smtpPort: 465, smtpUsername: 'smtp-user', smtpTls: 'TLS',
    imapHost: 'imap.example.invalid', imapPort: 993, imapUsername: 'imap-user', imapTls: 'TLS',
    inbox: 'INBOX', sent: 'Sent',
};
const passwords: MailkontoPasswordState = { smtpPassword: '', imapPassword: '', smtpPasswordSet: true, imapPasswordSet: true };

test('zeigt getrennte SMTP- und IMAP-Zugänge und lässt gesetzte Passwörter beim leeren Edit unverändert', () => {
    let latest = passwords;
    render(<MailkontoFields value={value} onChange={() => undefined} passwordState={latest} onPasswordChange={next => { latest = next; }} disabled={false} />);
    expect(screen.getByLabelText('SMTP-Server')).toHaveValue('smtp.example.invalid');
    expect(screen.getByLabelText('IMAP-Server')).toHaveValue('imap.example.invalid');
    expect(screen.getAllByText('Passwort ist gespeichert. Leer lassen, damit es unverändert bleibt.')).toHaveLength(2);
    fireEvent.change(screen.getByLabelText('SMTP-Passwort'), { target: { value: '' } });
    expect(latest.smtpPasswordSet).toBe(true);
    expect(latest.smtpPassword).toBe('');
});
