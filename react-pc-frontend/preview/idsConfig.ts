import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, renameSync, chmodSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

export const WUERTH_SHOP = 'https://eshop.wuerth.de/is-bin/INTERSHOP.enfinity/WFS/1401-B1-Site/de_DE/-/EUR/ViewIDSCatalogService-IDSInBound';
export interface IdsSettings { aktiviert: boolean; protokoll: string; punchoutUrl: string; kundennummer: string; loginName: string; passwort: string; notizen: string }
export const emptySettings = (): IdsSettings => ({ aktiviert: false, protokoll: 'WUERTH_LEGACY', punchoutUrl: WUERTH_SHOP, kundennummer: '', loginName: '', passwort: '', notizen: '' });

/** Local-only preview store. The production service uses database encryption instead. */
export function createIdsConfigStore(file = resolve(process.cwd(), '../src/main/resources/application-local.properties')) {
  function source() { return existsSync(file) ? readFileSync(file, 'utf8') : ''; }
  function properties() {
    return new Map(source().split(/\r?\n/).filter(line => line.includes('=') && !line.trim().startsWith('#')).map(line => {
      const i = line.indexOf('='); return [line.slice(0, i).trim(), line.slice(i + 1).trim()];
    }));
  }
  function load(): IdsSettings {
    const values = properties();
    const payload = values.get('ids.preview.wuerth');
    if (!payload) return { ...emptySettings(), aktiviert: !!values.get('ids.wuerth.pw_kunde'), kundennummer: values.get('ids.wuerth.kndnr') ?? '', loginName: values.get('ids.wuerth.name_kunde') ?? '', passwort: values.get('ids.wuerth.pw_kunde') ?? '' };
    const bytes = Buffer.from(payload, 'base64');
    const decipher = createDecipheriv('aes-256-gcm', Buffer.from(values.get('ids.preview.key') ?? '', 'base64'), bytes.subarray(0, 12));
    decipher.setAuthTag(bytes.subarray(12, 28));
    return JSON.parse(Buffer.concat([decipher.update(bytes.subarray(28)), decipher.final()]).toString('utf8')) as IdsSettings;
  }
  function save(input: IdsSettings): IdsSettings {
    const previous = load();
    if (typeof input.aktiviert !== 'boolean' || !['WUERTH_LEGACY', 'IDS_CONNECT_2_5'].includes(input.protokoll) || input.punchoutUrl !== WUERTH_SHOP) throw new Error('Für Würth bitte IDS-Connect und die Würth-Punchout-URL verwenden.');
    for (const field of ['kundennummer', 'loginName', 'passwort', 'notizen'] as const) {
      if (typeof input[field] !== 'string' || input[field].length > (field === 'notizen' ? 2000 : 200)) throw new Error('Bitte die Zugangsdaten vollständig und ohne überlange Werte eingeben.');
    }
    const value = { ...input, passwort: input.passwort === '' || input.passwort === '********' ? previous.passwort : input.passwort };
    if (value.aktiviert && (!value.kundennummer.trim() || !value.loginName.trim() || !value.passwort)) throw new Error('Kundennummer, Login-Name und Passwort sind erforderlich.');
    const key = randomBytes(32); const iv = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', key, iv);
    const encrypted = Buffer.concat([cipher.update(JSON.stringify(value), 'utf8'), cipher.final()]);
    const payload = Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString('base64');
    const preserved = source().split(/\r?\n/).filter(line => !/^\s*ids\.(preview\.|wuerth\.)/.test(line));
    const temp = `${file}.${randomBytes(8).toString('hex')}.tmp`;
    writeFileSync(temp, [...preserved, `ids.preview.key=${key.toString('base64')}`, `ids.preview.wuerth=${payload}`, ''].join('\n'), { mode: 0o600, flag: 'wx' });
    renameSync(temp, file); chmodSync(file, 0o600);
    return value;
  }
  return { load, save };
}
