import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('Teilmenge, technische Bedarfsänderung und Konfliktabgleich behalten den Entwurf', async ({ page }, testInfo) => {
 const position={art:'ARTIKEL',artikelId:10,interneReferenz:'A-10',zeichnungsnummer:null,zeichnungsrevision:null,bezeichnung:'Profil Dummy',werkstoff:'S235',abmessung:'40 x 40',basis:{menge:5,einheit:'STUECK',stueckzahl:null,einzelLaengeMm:null,kgJeMeter:null,faktorQuelle:null},schnittForm:null,winkelLinks:null,winkelRechts:null,bearbeitung:null,oberflaeche:null,dokumente:[],anlageVersionIds:[]};
 let bedarf={id:70,version:4,position,liefergruppe:{projektId:19,lieferadresse:'Musterweg 1',bedarfstermin:'2026-10-15',lagerzweck:null},mengen:{disponierbar:5},nachpflegeErforderlich:false,historischerHinweis:null};
 const empfaenger={lieferantId:8,kontaktId:9,lieferantenname:'Musterlieferant',email:'einkauf@example.test',eigeneKundennummer:'00017',name:'Einkauf',anrede:'Guten Tag'};
 let erstellt=false; const attempts: Array<{positionen:Array<{menge:number;version:number}>;empfaenger:unknown[]}> = []; const puts:unknown[]=[];
 await page.route('**/api/**',async route=>{
  const url=new URL(route.request().url()), p=url.pathname, method=route.request().method();
  if(p==='/api/auth/me')return route.fulfill({json:{id:1,username:'max.mustermann',displayName:'Max Mustermann',active:true,roles:['ADMIN'],admin:true,requiresInitialSetup:false}});
  if(p==='/api/notifications/summary')return route.fulfill({json:{totalCount:0,categories:[],recentItems:[]}});
  if(p==='/api/einkauf/bedarf')return route.fulfill({json:{content:[bedarf],totalPages:1}});
  if(p==='/api/einkauf/bedarf/70') {if(method==='PUT'){const data=route.request().postDataJSON();puts.push(data);bedarf={...bedarf,position:data.position,version:5};}return route.fulfill({json:bedarf});}
  if(p==='/api/einkauf/bedarfe/70/anlagen')return route.fulfill({json:[{id:18,dateiId:8,bedarfId:70,revision:'B',dateiname:'Profil-B.pdf',mimeTyp:'application/pdf',byteAnzahl:100,sha256:'dummy',freigegeben:true,versendet:false}]});
  if(p==='/api/lieferanten')return route.fulfill({json:{lieferanten:[{id:8,lieferantenname:'Musterlieferant',kundenNummer:'00017'}],gesamt:1}});
  if(p==='/api/lieferanten/8/einkauf-kontakte')return route.fulfill({json:[{id:9,email:'einkauf@example.test',name:'Einkauf',standardAnfrage:true,aktiv:true}]});
  if(p==='/api/einkauf/anfragen'&&method==='POST'){
   attempts.push(route.request().postDataJSON());
   if(attempts.length===1){bedarf={...bedarf,version:6};return route.fulfill({status:409,json:{detail:'Der Bedarf wurde zwischenzeitlich geändert.'}});}
   erstellt=true;return route.fulfill({json:{kopf:{id:51}}});
  }
  if(p==='/api/einkauf/anfragen/51')return route.fulfill({json:{angezeigteRevisionId:2,historisch:false,kopf:{id:51,version:0,paNummer:'PA-00051',aktuelleRevisionId:2,revisionsNummer:1,status:'AUSSTEHEND',projektIds:[19],antworten:0,lieferantenAnzahl:1},positionen:[{id:1,snapshot:bedarf.position,herkuenfte:[{bedarfId:70,version:6,menge:2}]}],lieferanten:[{id:4,version:0,status:'AUSSTEHEND',lieferantenname:'Musterlieferant',kontakt:empfaenger}]}});
  if(p.endsWith('/revisionen'))return route.fulfill({json:[{id:2,nummer:1,status:'AUSSTEHEND'}]});
  if(p.endsWith('/versandstatus'))return route.fulfill({json:[]});
  if(p.endsWith('/verlauf'))return route.fulfill({json:{content:[],totalPages:0}});
  return route.fulfill({json:[]});
 });
 await page.goto('/einkaufsanfragen/neu');
 await page.getByLabel(/Profil Dummy ·/).check();
 await page.getByLabel('Menge Profil Dummy',{exact:true}).fill('2');
 await page.getByRole('button',{name:'Position und Anlagen bearbeiten'}).click();
 await page.getByLabel('Werkstoff',{exact:true}).fill('S355');
 await page.getByRole('combobox',{name:'Dateiversion auswählen'}).click();
 await page.getByRole('option',{name:/Profil-B.pdf/}).click();
 await page.getByRole('button',{name:'Version hinzufügen'}).click();
 await page.getByRole('button',{name:'Bedarf speichern'}).click();
 await expect.poll(()=>puts.length).toBe(1);
 expect(puts[0]).toMatchObject({version:4,position:{werkstoff:'S355',anlageVersionIds:[18]}});
 await page.getByRole('button',{name:'Kontakt auswählen'}).click();
 await page.getByRole('button',{name:'Kontakt übernehmen'}).click();
 await designPruefung(page,testInfo,'anfrage-neu-entwurf',{primaerAktion:page.getByRole('button',{name:'Anfrage speichern'})});
 await page.getByRole('button',{name:'Anfrage speichern'}).click();
 await expect(page.getByRole('heading',{name:'Zwischenzeitliche Änderungen prüfen'})).toBeVisible();
 await expect(page.getByLabel('Menge Profil Dummy',{exact:true})).toHaveValue('2');
 await expect(page.getByRole('button',{name:'Anfrage speichern'})).toBeDisabled();
 await page.getByRole('button',{name:'Aktuellen Stand übernehmen, Eingaben behalten'}).click();
 await page.getByRole('button',{name:'Anfrage speichern'}).click();
 await expect.poll(()=>erstellt).toBe(true);
 expect(attempts.map(a=>a.positionen[0])).toEqual([{bedarfId:70,version:5,menge:2},{bedarfId:70,version:6,menge:2}]);
 await page.reload();
 await expect(page.getByRole('heading',{name:'PA-00051'})).toBeVisible();
});

test('Mailfundstelle und Absender müssen vor manueller Zuordnung bestätigt werden', async ({page},testInfo)=>{
 const assignments:unknown[]=[];
 await page.route('**/api/**',async route=>{
  const p=new URL(route.request().url()).pathname;
  if(p==='/api/auth/me')return route.fulfill({json:{id:1,username:'max.mustermann',active:true,roles:['ADMIN'],admin:true,requiresInitialSetup:false}});
  if(p==='/api/notifications/summary')return route.fulfill({json:{totalCount:0,categories:[],recentItems:[]}});
  if(p==='/api/einkauf/anfragen/51')return route.fulfill({json:{angezeigteRevisionId:2,historisch:false,kopf:{id:51,version:0,paNummer:'PA-00051',aktuelleRevisionId:2,revisionsNummer:2,status:'AUSSTEHEND'},positionen:[],lieferanten:[{id:4,version:0,status:'AUSSTEHEND',lieferantenname:'Musterlieferant',kontakt:{email:'einkauf@example.test'}}]}});
  if(p.endsWith('/revisionen'))return route.fulfill({json:[{id:2,nummer:2,status:'AUSSTEHEND'}]});
  if(p.endsWith('/verlauf'))return route.fulfill({json:{content:[],totalPages:0}});
  if(p==='/api/emails/search')return route.fulfill({json:[{id:91,subject:'Angebot Profil',fromAddress:'einkauf@example.test'}]});
  if(p==='/api/emails/91')return route.fulfill({json:{id:91,subject:'Angebot Profil',fromAddress:'einkauf@example.test',body:'Anbei unser Angebot für das Profil.',attachments:[{id:7,originalFilename:'Angebot.pdf'}]}});
  if(p.endsWith('/zuordnung/ermitteln'))return route.fulfill({json:{status:'PRUEFEN',quelle:'PDF',vorgangId:51}});
  if(p==='/api/einkauf/mail/91/zuordnung'){assignments.push(route.request().postDataJSON());return route.fulfill({json:{status:'ZUGEORDNET'}});}
  return route.fulfill({json:[]});
 });
 await page.goto('/einkaufsanfragen/51');
 await page.getByRole('button',{name:'Nachricht zuordnen'}).click();
 await page.getByLabel('Nachricht suchen').fill('Profil');await page.getByRole('button',{name:'Suchen',exact:true}).click();
 await page.getByRole('button',{name:'Angebot Profil · einkauf@example.test'}).click();
 await expect(page.getByText('Absender: einkauf@example.test')).toBeVisible();
 await expect(page.getByRole('link',{name:'Angebot.pdf öffnen'})).toHaveAttribute('href','/api/emails/91/attachments/7');
 await page.getByRole('button',{name:'Zuordnungsvorschlag prüfen'}).click();
 await page.getByRole('combobox',{name:'Geprüfter Lieferant'}).click();await page.getByRole('option',{name:/Musterlieferant/}).click();
 await page.getByLabel('Begründung der Zuordnung').fill('Absender und Angebotsposition geprüft');
 expect(assignments).toEqual([]);await expect(page.getByRole('button',{name:'Zuordnung bestätigen'})).toBeDisabled();
 await page.getByLabel('Ich habe Absender, Lieferant, Fundstelle und Unterlagen für diese Anfragefassung geprüft.').check();
 await designPruefung(page,testInfo,'anfrage-mail-zuordnung',{primaerAktion:page.getByRole('button',{name:'Zuordnung bestätigen'})});
 await page.getByRole('button',{name:'Zuordnung bestätigen'}).click();
 await expect.poll(()=>assignments.length).toBe(1);
 expect(assignments[0]).toMatchObject({typ:'ANFRAGE',vorgangId:51,beteiligungId:4,revisionId:2,begruendung:'Absender und Angebotsposition geprüft'});
});
