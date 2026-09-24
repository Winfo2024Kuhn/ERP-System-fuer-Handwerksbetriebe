import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';

export type MailTls = 'TLS' | 'STARTTLS';

export interface MailkontoDraft {
    aktiv: boolean;
    fromAddress: string;
    fromName: string;
    smtpHost: string;
    smtpPort: number;
    smtpUsername: string;
    smtpTls: MailTls;
    imapHost: string;
    imapPort: number;
    imapUsername: string;
    imapTls: MailTls;
    inbox: string;
    sent: string;
}

export interface MailkontoPasswordState {
    smtpPassword: string;
    imapPassword: string;
    smtpPasswordSet: boolean;
    imapPasswordSet: boolean;
}

interface MailkontoFieldsProps {
    value: MailkontoDraft;
    onChange: (value: MailkontoDraft) => void;
    passwordState: MailkontoPasswordState;
    onPasswordChange: (value: MailkontoPasswordState) => void;
    disabled: boolean;
    mode?: 'all' | 'document';
}

export function MailkontoFields({ value, onChange, passwordState, onPasswordChange, disabled, mode = 'all' }: MailkontoFieldsProps) {
    const set = <K extends keyof MailkontoDraft>(key: K, next: MailkontoDraft[K]) => onChange({ ...value, [key]: next });
    const prefix = mode === 'document' ? 'dokument' : 'einkauf';
    const field = (id: string, label: string, key: keyof MailkontoDraft, type = 'text', placeholder?: string) => (
        <div className="space-y-1">
            <Label htmlFor={`${prefix}-${id}`}>{label}</Label>
            <Input id={`${prefix}-${id}`} type={type === 'number' ? 'text' : type} inputMode={type === 'number' ? 'numeric' : undefined}
                value={type === 'number' && value[key] === 0 ? '' : String(value[key])}
                placeholder={placeholder} disabled={disabled}
                onChange={event => set(key, (type === 'number' ? (Number.parseInt(event.target.value, 10) || 0) : event.target.value) as MailkontoDraft[typeof key])} />
        </div>
    );
    const password = (protocol: 'smtp' | 'imap') => {
        const label = `${protocol.toUpperCase()}-Passwort`;
        const key = protocol === 'smtp' ? 'smtpPassword' : 'imapPassword';
        const setKey = protocol === 'smtp' ? 'smtpPasswordSet' : 'imapPasswordSet';
        return <div className="space-y-1">
            <Label htmlFor={`${prefix}-${key}`}>{label}</Label>
            <Input id={`${prefix}-${key}`} type="password" autoComplete="new-password" value={passwordState[key]}
                placeholder={passwordState[setKey] ? 'Leer lassen = unverändert' : 'Passwort eintragen'} disabled={disabled}
                onChange={event => onPasswordChange({ ...passwordState, [key]: event.target.value })} />
            {passwordState[setKey] && <p className="text-xs text-slate-500">Passwort ist gespeichert. Leer lassen, damit es unverändert bleibt.</p>}
        </div>;
    };
    const tls = (protocol: 'smtp' | 'imap') => {
        const key = protocol === 'smtp' ? 'smtpTls' : 'imapTls';
        return <div className="space-y-1">
            <Label htmlFor={`${prefix}-${key}`}>{protocol.toUpperCase()}-Verschlüsselung</Label>
            <Select id={`${prefix}-${key}`} aria-label={`${protocol.toUpperCase()}-Verschlüsselung`} value={value[key]} disabled={disabled} onChange={next => set(key, next as MailTls)}
                options={[{ value: 'TLS', label: 'TLS' }, { value: 'STARTTLS', label: 'STARTTLS' }]} />
        </div>;
    };

    if (mode === 'document') return <div className="space-y-4">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {field('mailkonto-smtp-host', 'Mail-Server für den Versand', 'smtpHost', 'text', 'mail.ihre-domain.de')}
            {field('mailkonto-smtp-port', 'Port', 'smtpPort', 'number', '465')}
            {field('mailkonto-smtp-user', 'E-Mail-Adresse des Postfachs', 'smtpUsername', 'email', 'rechnungen@ihre-domain.de')}
            {password('smtp')}
            {field('mailkonto-from-name', 'Angezeigter Name', 'fromName', 'text', 'Bauschlosserei Kuhn')}
            {field('mailkonto-imap-host', 'Posteingangs-Server (optional)', 'imapHost', 'text', 'wie beim Versand')}
            {field('mailkonto-from-address', 'Absender-Adresse (optional)', 'fromAddress', 'email', 'rechnungen@ihre-domain.de')}
        </div>
    </div>;

    return <div className="space-y-5">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {field('mailkonto-from-address', 'Absender-Adresse', 'fromAddress', 'email', 'einkauf@firma.de')}
            {field('mailkonto-from-name', 'Absendername', 'fromName', 'text', 'Einkauf')}
        </div>
        <section aria-label="Versand per SMTP" className="space-y-3">
            <h4 className="font-semibold text-slate-800">Versand (SMTP)</h4>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {field('mailkonto-smtp-host', 'SMTP-Server', 'smtpHost', 'text', 'smtp.firma.de')}
                {field('mailkonto-smtp-port', 'SMTP-Port', 'smtpPort', 'number', '465')}
                {field('mailkonto-smtp-user', 'SMTP-Benutzername', 'smtpUsername', 'text', 'einkauf@firma.de')}
                {tls('smtp')}{password('smtp')}
            </div>
        </section>
        <section aria-label="Empfang per IMAP" className="space-y-3">
            <h4 className="font-semibold text-slate-800">Empfang (IMAP)</h4>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {field('mailkonto-imap-host', 'IMAP-Server', 'imapHost', 'text', 'imap.firma.de')}
                {field('mailkonto-imap-port', 'IMAP-Port', 'imapPort', 'number', '993')}
                {field('mailkonto-imap-user', 'IMAP-Benutzername', 'imapUsername', 'text', 'einkauf@firma.de')}
                {tls('imap')}{password('imap')}
                {field('mailkonto-inbox', 'Posteingangsordner', 'inbox', 'text', 'INBOX')}
                {field('mailkonto-sent', 'Gesendet-Ordner', 'sent', 'text', 'Sent')}
            </div>
        </section>
    </div>;
}
