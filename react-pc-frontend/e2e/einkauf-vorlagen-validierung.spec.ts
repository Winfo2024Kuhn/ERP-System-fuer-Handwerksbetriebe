import {test,expect} from './hilfen/test';
import {designPruefung} from './hilfen/design';
test('Unbekannte Einkaufstokens verhindern Speichern, Servervorschau zeigt korrigierte Fassung',async({page},testInfo)=>{
 const writes:unknown[]=[];
 await page.route('**/api/**',async route=>{
  const p=new URL(route.request().url()).pathname;
  if(p==='/api/auth/me')return route.fulfill({json:{id:1,username:'max.mustermann',active:true,roles:['ADMIN'],admin:true,requiresInitialSetup:false}});
  if(p==='/api/notifications/summary')return route.fulfill({json:{totalCount:0,categories:[],recentItems:[]}});
  if(p==='/api/email-textvorlagen/dokumenttypen')return route.fulfill({json:[{value:'EINKAUF_ANFRAGE',label:'Einkauf — Lieferantenanfrage',kategorie:'EINKAUF'}]});
  if(p.includes('/placeholders'))return route.fulfill({json:[{token:'ANFRAGENUMMER',label:'Anfragenummer'}]});
  if(p==='/api/email-textvorlagen/einkauf-vorschau'){
   const data=route.request().postDataJSON();if(data.subjectTemplate.includes('FALSCH'))return route.fulfill({status:400,json:{detail:'Platzhalter {{FALSCH}} ist für diese Einkaufs-Vorlage nicht zulässig'}});
   return route.fulfill({json:{subject:'Server: PA-2026-00001',htmlBody:'<p>Servergerendertes Beispielprofil</p>'}});
  }
  if(p==='/api/email-textvorlagen/8'&&route.request().method()==='PUT'){writes.push(route.request().postDataJSON());return route.fulfill({json:{id:8}});}
  return route.fulfill({json:[{id:8,dokumentTyp:'EINKAUF_ANFRAGE',kategorie:'EINKAUF',name:'Anfrage Dummy',subjectTemplate:'Anfrage {{ANFRAGENUMMER}}',htmlBody:'<p>{{POSITIONEN}}</p>',aktiv:true,standard:true}]});
 });
 await page.goto('/email-textvorlagen');
 await expect(page.getByText('Betreff: Server: PA-2026-00001')).toBeVisible();
 await page.getByRole('button',{name:'Bearbeiten',exact:true}).filter({hasText:'Bearbeiten'}).click();
 await page.locator('#email-template-subject').fill('{{FALSCH}}');
 await page.getByRole('button',{name:/speichern/i}).click();
 await expect(page.getByText('Platzhalter {{FALSCH}} ist für diese Einkaufs-Vorlage nicht zulässig').first()).toBeVisible();
 expect(writes).toEqual([]);
 await page.locator('#email-template-subject').fill('Anfrage {{ANFRAGENUMMER}}');
 await expect(page.getByText('Servergerendertes Beispielprofil')).toBeVisible();
 await page.getByLabel('Servervorschau mit Beispieldaten').scrollIntoViewIfNeeded();
 await designPruefung(page,testInfo,'einkauf-servervorlagenvorschau');
 await page.getByRole('button',{name:/speichern/i}).click();await expect.poll(()=>writes.length).toBe(1);
});
