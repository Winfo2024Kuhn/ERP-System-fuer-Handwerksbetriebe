import { afterEach, expect, it } from 'vitest';
import { mkdtempSync, readFileSync, writeFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createIdsConfigStore, emptySettings } from './idsConfig';
const dirs: string[] = [];
afterEach(() => dirs.splice(0).forEach(dir => rmSync(dir, { recursive: true, force: true })));
it('persists encrypted configuration and keeps the password when unchanged or blank', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ids-config-test-')); dirs.push(dir);
  const file = join(dir, 'application-local.properties');
  const store = createIdsConfigStore(file);
  const config = { ...emptySettings(), aktiviert: true, kundennummer: '00123', loginName: 'dummy', passwort: 'dummy-secret-only', notizen: 'Test' };
  store.save(config);
  expect(readFileSync(file, 'utf8')).not.toContain(config.passwort);
  expect(statSync(file).mode & 0o777).toBe(0o600);
  expect(createIdsConfigStore(file).load()).toEqual(config);
  store.save({ ...config, passwort: '********' });
  expect(store.load().passwort).toBe(config.passwort);
  store.save({ ...config, passwort: '' });
  expect(store.load().passwort).toBe(config.passwort);
  expect(() => store.save({ ...config, punchoutUrl: 'https://example.test' })).toThrow();
  expect(() => store.save({ ...config, loginName: '' })).toThrow();
  expect(store.load()).toEqual(config);
});

it('migrates indented legacy secrets without removing unrelated settings', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ids-config-test-')); dirs.push(dir);
  const file = join(dir, 'application-local.properties');
  writeFileSync(file, 'spring.example=keep\n  ids.wuerth.kndnr=dummy\n  ids.wuerth.name_kunde=dummy\n  ids.wuerth.pw_kunde=dummy-secret-only\n');
  const store = createIdsConfigStore(file); store.save(store.load());
  expect(readFileSync(file, 'utf8')).not.toContain('dummy-secret-only');
  expect(readFileSync(file, 'utf8')).toContain('spring.example=keep');
  expect(store.load().passwort).toBe('dummy-secret-only');
});
