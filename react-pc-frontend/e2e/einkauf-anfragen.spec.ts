import { test, expect } from './hilfen/test';

test('Anfrage prüfen, hashgebunden freigeben und Verlauf nach Neuladen sehen', async ({ page }) => {
 let status='AUSSTEHEND'; const freigaben: unknown[]=[]; let templateSubject='Anfrage {{PA_NUMMER}}'; let gesendeterBetreff='';
 await page.route('**/api/auth/me', route=>route.fulfill({json:{id:1,username:'max.mustermann',displayName:'Max Mustermann',active:true,roles:['ADMIN'],admin:true,requiresInitialSetup:false}}));
 await page.route('**/api/notifications/summary', route=>route.fulfill({json:{totalCount:0,categories:[],recentItems:[]}}));
 await page.route('**/api/einkauf/anfragen**', async route => {
  const url=new URL(route.request().url());
  if(url.pathname==='/api/einkauf/anfragen' && route.request().method()==='GET') return route.fulfill({json:{content:[{id:51,paNummer:'PA-00051',revisionsNummer:2,status,antwortfrist:'2026-10-01',liefertermin:'2026-10-14',projektIds:[19],antworten:0,lieferantenAnzahl:1}],totalPages:1,totalElements:1}});
  if(url.pathname==='/api/einkauf/anfragen/51/revisionen') return route.fulfill({json:[{id:1,nummer:1,status:'VERSENDET',antwortfrist:'2026-09-20',liefertermin:'2026-09-25'},{id:2,nummer:2,status,antwortfrist:'2026-10-01',liefertermin:'2026-10-14'}]});
  if(url.pathname.endsWith('/versandstatus')) return route.fulfill({json:[]});
  if(url.pathname==='/api/einkauf/anfragen/51/revisionen/1') return route.fulfill({json:{kopf:{id:51,version:0,paNummer:'PA-00051',aktuelleRevisionId:2,angezeigteRevisionId:1,historisch:true,revisionsNummer:1,status:'VERSENDET'},positionen:[],lieferanten:[{id:3,lieferantenname:'Alter Anbieter',status:'VERSENDET',version:0}]}});
  if(url.pathname==='/api/einkauf/anfragen/51/revisionen/2') return route.fulfill({json:{kopf:{id:51,version:0,paNummer:'PA-00051',aktuelleRevisionId:2,angezeigteRevisionId:2,historisch:false,revisionsNummer:2,status,antwortfrist:'2026-10-01',liefertermin:'2026-10-14'},positionen:[{id:9,snapshot:{bezeichnung:'Profil Dummy',basis:{menge:2,einheit:'STUECK'}},herkuenfte:[]}],lieferanten:[{id:4,lieferantenname:'Musterlieferant',status:'AUSSTEHEND',version:0,kontakt:{lieferantId:4,kontaktId:9,lieferantenname:'Musterlieferant',email:'einkauf@example.test',name:'Einkauf',anrede:'Guten Tag',eigeneKundennummer:'00017'}}]}});
  if(url.pathname==='/api/einkauf/anfragen/51/lieferanten/4/senden' && route.request().method()==='POST'){const request=route.request().postDataJSON();freigaben.push(request);gesendeterBetreff=request.vorschauHash==='hash-pa-51'?'Anfrage PA-00051':'';status='VERSENDET';return route.fulfill({json:{beteiligungId:4,status:'ERFOLGREICH'}});}
  if(url.pathname==='/api/einkauf/anfragen/51') return route.fulfill({json:{kopf:{id:51,version:0,paNummer:'PA-00051',aktuelleRevisionId:2,angezeigteRevisionId:2,historisch:false,revisionsNummer:2,status,antwortfrist:'2026-10-01',liefertermin:'2026-10-14'},positionen:[{id:9,snapshot:{bezeichnung:'Profil Dummy',basis:{menge:2,einheit:'STUECK'}},herkuenfte:[]}],lieferanten:[{id:4,lieferantenname:'Musterlieferant',status:'AUSSTEHEND',version:0,kontakt:{lieferantId:4,kontaktId:9,lieferantenname:'Musterlieferant',email:'einkauf@example.test',name:'Einkauf',anrede:'Guten Tag',eigeneKundennummer:'00017'}}]}});
  if(url.pathname==='/api/einkauf/ANFRAGE/51/verlauf')return route.fulfill({json:{content:[{emailId:2,subject:'Ihre Anfrage PA-00051',fromAddress:'einkauf@example.test',status,quelle:'AUSGANG',revisionId:1}]}});
  return route.fulfill({json:{}});
 });
 await page.route('**/api/einkauf/ANFRAGE/51/verlauf*', route=>route.fulfill({json:{content:[{emailId:2,subject:'Ihre Anfrage PA-00051',fromAddress:'einkauf@example.test',status,quelle:'AUSGANG',revisionId:1}]}}));
 await page.route('**/api/email-textvorlagen**', async route=>{const url=new URL(route.request().url());if(url.pathname.endsWith('/dokumenttypen'))return route.fulfill({json:[{value:'EINKAUF_ANFRAGE',label:'Einkauf — Lieferantenanfrage',kategorie:'EINKAUF',kategorieLabel:'Einkauf'}]});if(url.pathname.includes('/placeholders/'))return route.fulfill({json:[{token:'{{PA_NUMMER}}',label:'Anfragenummer',pflicht:true,imBetreffErlaubt:true},{token:'{{ANREDE}}',label:'Anrede',pflicht:true,imBetreffErlaubt:false}]});if(url.pathname.endsWith('/placeholders'))return route.fulfill({json:[{token:'{{PA_NUMMER}}',label:'Anfragenummer'}]});if(route.request().method()==='PUT'){templateSubject=route.request().postDataJSON().subjectTemplate;return route.fulfill({json:{id:7,dokumentTyp:'EINKAUF_ANFRAGE',aktiv:true,standard:true,subjectTemplate:templateSubject}});}return route.fulfill({json:[{id:7,dokumentTyp:'EINKAUF_ANFRAGE',aktiv:true,standard:true,subjectTemplate:templateSubject,name:'Musteranfrage',htmlBody:'<p>Guten Tag</p>'}]});});
 await page.route('**/api/einkauf/anfragen/51/lieferanten/4/vorschau**', route=>route.fulfill({json:{version:0,vorschauHash:'hash-pa-51',subject:'Anfrage PA-00051',htmlBody:'<p>Guten Tag</p>',empfaenger:'einkauf@example.test',pdfDateiId:1,anlageVersionIds:[8]}}));
 await page.goto('/email-textvorlagen');
 await page.getByRole('button',{name:/bearbeiten/i}).first().click();
 await page.locator('#email-template-subject').fill('Alttext {{PA_NUMMER}}');
 await page.getByRole('button',{name:/speichern/i}).click();
 await expect.poll(()=>templateSubject).toBe('Alttext {{PA_NUMMER}}');
 await page.goto('/einkaufsanfragen');
 await expect(page.getByRole('heading',{name:'EINKAUFSANFRAGEN'})).toBeVisible();
 await page.getByRole('link',{name:/PA-00051/}).click();
 await expect(page.getByText('Profil Dummy')).toBeVisible();
 await page.getByRole('combobox',{name:'Anfragefassung'}).click(); await page.getByText('Fassung 1 · VERSENDET').click(); await expect(page.getByText(/nur Lesen/)).toBeVisible(); await page.getByRole('combobox',{name:'Anfragefassung'}).click(); await page.getByText('Fassung 2 · AUSSTEHEND').click(); await expect(page.getByRole('button',{name:/vorschau und freigabe/i})).toBeVisible();
 await page.getByRole('button',{name:/vorschau und freigabe/i}).click();
 await expect(page.getByText('einkauf@example.test').first()).toBeVisible();
 await page.evaluate(async()=>{await fetch('/api/email-textvorlagen/7',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({dokumentTyp:'EINKAUF_ANFRAGE',kategorie:'EINKAUF',name:'Musteranfrage',subjectTemplate:'NEUE FASSUNG',htmlBody:'<p>Neu</p>',aktiv:true,standard:true})});});
 await expect(page.getByText('Guten Tag')).toBeVisible();
 await expect(page.getByText(/Revision 0/)).toBeVisible();
 await page.getByRole('button',{name:/freigeben und senden/i}).click();
 await expect.poll(()=>freigaben.length).toBe(1);
 expect(freigaben[0]).toMatchObject({version:0,vorschauHash:'hash-pa-51'});
 expect(templateSubject).toBe('NEUE FASSUNG');
 expect(gesendeterBetreff).toBe('Anfrage PA-00051');
 await page.reload();
 await expect(page.getByRole('heading',{name:'PA-00051'})).toBeVisible();
 await expect(page.getByText(/Ihre Anfrage PA-00051/)).toBeVisible();
});
