import { test, expect } from './hilfen/test';

test('Einkaufskontakt bleibt getrennt von Rechnungs-E-Mail und kann gespeichert werden', async ({ page }) => {
 let gespeicherterKontakt: Record<string,unknown>|undefined;
 await page.route('**/api/**',async route=>{const url=new URL(route.request().url());const path=url.pathname;const method=route.request().method();if(path==='/api/auth/me')return route.fulfill({json:{id:1,username:'max.mustermann',displayName:'Max Mustermann',active:true,roles:['ADMIN'],admin:true,requiresInitialSetup:false}});if(path==='/api/notifications/summary')return route.fulfill({json:{totalCount:0,categories:[],recentItems:[]}});if(path==='/api/lieferanten'&&method==='GET')return route.fulfill({json:{lieferanten:[],gesamt:0}});if(path==='/api/lieferanten/21')return route.fulfill({json:{id:21,lieferantenname:'Musterlieferant GmbH',strasse:'Musterweg 1',plz:'12345',ort:'Musterstadt',kundenEmails:['rechnung@example.test'],kommunikation:[],dokumente:[],notizen:[],statistik:{gesamtKosten:0,bestellungAnzahl:0,artikelAnzahl:0}}});if(path==='/api/lieferanten/21/einkauf-kontakte'&&method==='GET')return route.fulfill({json:[]});if(path==='/api/lieferanten/21/einkauf-kontakte'&&method==='POST'){gespeicherterKontakt=route.request().postDataJSON();return route.fulfill({status:201,json:{id:91,...gespeicherterKontakt}});}if(path==='/api/email-textvorlagen')return route.fulfill({json:[]});return route.fulfill({json:[]});});
 await page.goto('/lieferanten?lieferantId=21&tab=einkauf');
 await expect(page.getByRole('heading',{name:'Einkaufskontakte'})).toBeVisible();
 const bereich=page.getByRole('region',{name:'Einkaufskontakte'});
 await expect(bereich.getByText('rechnung@example.test')).toHaveCount(0);
 await page.getByLabel('E-Mail für den Einkauf *').fill('einkauf@example.test');
 await page.getByLabel('Anrede (optional)').fill('Guten Tag');
 await page.getByLabel(/Standard für Anfragen/).check();
 await page.getByRole('button',{name:'Kontakt speichern'}).click();
 await expect.poll(()=>gespeicherterKontakt).toMatchObject({email:'einkauf@example.test',anrede:'Guten Tag',standardAnfrage:true});
});

test('Einkaufsvorlage lädt passende Tokens und lässt die Standardvariante gezielt wechseln', async ({ page }) => {
 let gespeichert: Record<string,unknown>|undefined;
 await page.route('**/api/**',async route=>{const url=new URL(route.request().url());const path=url.pathname;const method=route.request().method();if(path==='/api/auth/me')return route.fulfill({json:{id:1,username:'max.mustermann',displayName:'Max Mustermann',active:true,roles:['ADMIN'],admin:true,requiresInitialSetup:false}});if(path==='/api/notifications/summary')return route.fulfill({json:{totalCount:0,categories:[],recentItems:[]}});if(path==='/api/email-textvorlagen/einkauf-vorschau')return route.fulfill({json:{subject:'Anfrage PA-2026-00001',htmlBody:'<p>Guten Tag</p>'}});if(path==='/api/email-textvorlagen/dokumenttypen')return route.fulfill({json:[{value:'EINKAUF_ANFRAGE',label:'Einkauf — Lieferantenanfrage',kategorie:'EINKAUF',kategorieLabel:'Einkauf'}]});if(path==='/api/email-textvorlagen/placeholders')return route.fulfill({json:[{token:'{{ANREDE}}',label:'Anrede'}]});if(path==='/api/email-textvorlagen/placeholders/EINKAUF_ANFRAGE')return route.fulfill({json:[{token:'{{ANREDE}}',label:'Neutrale Anrede',pflicht:true,imBetreffErlaubt:false}]});if(path==='/api/email-textvorlagen')return route.fulfill({json:[{id:8,dokumentTyp:'EINKAUF_ANFRAGE',kategorie:'EINKAUF',name:'Anfrage Variante A',subjectTemplate:'Anfrage {{ANFRAGENUMMER}}',htmlBody:'<p>{{ANREDE}}</p>',aktiv:true,standard:false},{id:9,dokumentTyp:'EINKAUF_ANFRAGE',kategorie:'EINKAUF',name:'Anfrage Variante B',subjectTemplate:'Anfrage {{ANFRAGENUMMER}}',htmlBody:'<p>{{ANREDE}}</p>',aktiv:true,standard:true}]});if(path==='/api/email-textvorlagen/8'&&method==='PUT'){gespeichert=route.request().postDataJSON();return route.fulfill({json:{id:8,...gespeichert,standard:true}});}return route.fulfill({json:[]});});
 await page.goto('/email-textvorlagen');
 await expect(page.getByText('Einkauf',{exact:true}).first()).toBeVisible();
 await page.getByRole('button',{name:/bearbeiten/i}).first().click();
 await expect(page.getByTitle('Neutrale Anrede')).toBeVisible();
 await page.getByLabel(/Standard für diesen Einkaufstyp/).check();
 await page.getByRole('button',{name:/speichern/i}).click();
 await expect.poll(()=>gespeichert).toMatchObject({standard:true,kategorie:'EINKAUF'});
});
