import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import type { AnfrageDetail, BedarfResponse, KontaktSnapshot, PositionSnapshot } from '../types';
import { AnfrageEntwurfEditor } from './AnfrageEntwurfEditor';
const position = (bezeichnung: string): PositionSnapshot => ({art:'ARTIKEL',artikelId:10,interneReferenz:'A-10',zeichnungsnummer:null,zeichnungsrevision:null,bezeichnung,werkstoff:'S235',abmessung:'40 mm',basis:{menge:5,einheit:'STUECK',stueckzahl:5,einzelLaengeMm:null,kgJeMeter:null,faktorQuelle:null},schnittForm:null,winkelLinks:null,winkelRechts:null,bearbeitung:null,oberflaeche:null,dokumente:[],anlageVersionIds:[]});
const bedarf = (id: number, version=4): BedarfResponse => ({id,version,position:position(`Profil ${id}`),liefergruppe:{projektId:19,lieferadresse:'Musterweg 1',bedarfstermin:null,lagerzweck:null},mengen:{bedarf:5,lagergedeckt:0,angefragt:0,reserviert:0,bestellt:0,geliefert:0,storniert:0,ungedeckt:5,disponierbar:5},nachpflegeErforderlich:false,historischerHinweis:null});
const kontakt = (id: number, email=`kontakt${id}@example.test`): KontaktSnapshot => ({lieferantId:id,kontaktId:id+100,lieferantenname:`Lieferant ${id}`,email,name:'Einkauf',anrede:'Guten Tag',eigeneKundennummer:'00017'});
const detail = (version: number, bedarfe: number[], kontakte: KontaktSnapshot[], frist='2026-10-01'): AnfrageDetail => ({angezeigteRevisionId:version+10,historisch:false,kopf:{id:51,version,paNummer:'PA-51',aktuelleRevisionId:version+10,revisionsNummer:version+1,status:'AUSSTEHEND',antwortfrist:frist,liefertermin:'2026-10-20',zustaendigId:null,projektIds:[19],antworten:0,lieferantenAnzahl:kontakte.length},positionen:bedarfe.map(id=>({id,snapshot:position(`Profil ${id}`),herkuenfte:[{bedarfId:id,version:4,menge:3}]})),lieferanten:kontakte.map((k,i)=>({id:i+1,version:0,status:'AUSSTEHEND',lieferantenname:k.lieferantenname!,kontakt:k}))});
const ok = (data: unknown) => ({ok:true,json:async()=>data}) as Response;
const conflict = () => ({ok:false,status:409,json:async()=>({message:'Zwischenzeitlich geändert'})}) as Response;
async function waehle(user: ReturnType<typeof userEvent.setup>, name: string, server=true) {
 await user.click(screen.getByRole('combobox',{name}));
 await user.click(screen.getByRole('option',{name:server?'Aktuell gespeicherten Wert übernehmen':'Meinen Entwurf verwenden'}));
}

describe('AnfrageEntwurfEditor gleichzeitige Änderungen',()=>{
 it('zeigt hinzugefügte/entfernte Positionen und Empfänger sowie Fristen und speichert erst nach jeder Entscheidung',async()=>{
  const user=userEvent.setup(), saved=vi.fn();const initial=detail(1,[70,90],[kontakt(8),kontakt(10)]);
  const aktuell=detail(2,[70,80],[kontakt(8,'neu@example.test'),kontakt(9)],'2026-10-07');
  let remote=false;const posts:unknown[]=[];
  global.fetch=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
   const url=String(input);
   if(url==='/api/einkauf/anfragen/51/revisionen'&&init?.method==='POST'){posts.push(JSON.parse(String(init.body)));if(posts.length===1){remote=true;return conflict();}return ok(aktuell);}
   if(url==='/api/einkauf/anfragen/51')return ok(aktuell);
   if(url.includes('/api/einkauf/bedarf?'))return ok({content:[],totalPages:0});
   const match=url.match(/\/api\/einkauf\/bedarf\/(\d+)$/);if(match){const b=bedarf(Number(match[1]),remote?5:4);if(remote&&b.id===70)b.position={...b.position,werkstoff:'S355'};return ok(b);}
   if(url.includes('/api/lieferanten?'))return ok({lieferanten:[],gesamt:0});
   return ok([]);
  });
  render(<ToastProvider><AnfrageEntwurfEditor initial={initial} onSaved={saved}/></ToastProvider>);
  await user.clear(await screen.findByLabelText('Menge Profil 70'));await user.type(screen.getByLabelText('Menge Profil 70'),'2');
  await user.click(screen.getByRole('button',{name:'Fassung speichern'}));
  const dialog=await screen.findByRole('dialog');
  expect(within(dialog).getByText(/S235 → S355/)).toBeInTheDocument();
  expect(within(dialog).getByText(/neu@example.test/)).toBeInTheDocument();
  expect(within(dialog).getByText('7.10.2026')).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'Abgleich übernehmen'})).toBeDisabled();
  await waehle(user,'Anfragemenge Profil 70: Stand auswählen',false);
  await waehle(user,'Anfragemenge Profil 80: Stand auswählen');
  await waehle(user,'Anfragemenge Profil 90: Stand auswählen');
  await waehle(user,'Empfänger Lieferant 8: Stand auswählen');
  await waehle(user,'Empfänger Lieferant 9: Stand auswählen');
  await waehle(user,'Empfänger Lieferant 10: Stand auswählen');
  expect(screen.getByRole('button',{name:'Abgleich übernehmen'})).toBeDisabled();
  await waehle(user,'Antwortfrist: Stand auswählen');
  await user.click(screen.getByRole('button',{name:'Abgleich übernehmen'}));
  expect(screen.getByLabelText('Menge Profil 70')).toHaveValue('2');
  expect(screen.getByLabelText('Menge Profil 80')).toHaveValue('3');
  expect(screen.queryByLabelText('Menge Profil 90')).not.toBeInTheDocument();
  await user.click(screen.getByRole('button',{name:'Fassung speichern'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(posts[1]).toMatchObject({version:2,inhalt:{antwortfrist:'2026-10-07',positionen:[{bedarfId:70,version:5,menge:2},{bedarfId:80,version:5,menge:3}],empfaenger:[{lieferantId:8,email:'neu@example.test'},{lieferantId:9}]}});
 });
 it('gleicht technische Felder nach Bedarf-PUT-Konflikt einzeln ab und behält gewählte eigene Werte',async()=>{
  const user=userEvent.setup();let remote=false;const puts: Array<{version:number;position:PositionSnapshot}>=[];
  global.fetch=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
   const url=String(input), b=bedarf(70,remote?5:4);
   if(remote)b.position={...b.position,werkstoff:'S355',abmessung:'50 mm',oberflaeche:'Verzinkt'};
   if(url==='/api/einkauf/bedarf/70'&&init?.method==='PUT'){puts.push(JSON.parse(String(init.body)));if(puts.length===1){remote=true;return conflict();}return ok({...b,version:6,position:puts[1].position});}
   if(url==='/api/einkauf/bedarf/70')return ok(b);
   if(url.includes('/api/einkauf/bedarf?'))return ok({content:[b],totalPages:1});
   if(url.includes('/api/lieferanten?'))return ok({lieferanten:[],gesamt:0});return ok([]);
  });
  render(<ToastProvider><AnfrageEntwurfEditor onSaved={vi.fn()}/></ToastProvider>);
  await user.click(await screen.findByLabelText(/Profil 70 ·/));
  await user.click(screen.getByRole('button',{name:'Position und Anlagen bearbeiten'}));
  await user.clear(await screen.findByLabelText('Werkstoff'));await user.type(screen.getByLabelText('Werkstoff'),'S460');
  await user.clear(screen.getByLabelText('Abmessung / Profil'));await user.type(screen.getByLabelText('Abmessung / Profil'),'60 mm');
  await user.click(screen.getByRole('button',{name:'Bedarf speichern'}));
  await screen.findByRole('heading',{name:'Zwischenzeitliche Änderungen prüfen'});
  await waehle(user,'Werkstoff: Stand auswählen',false);
  await waehle(user,'Abmessung: Stand auswählen');
  await waehle(user,'Oberfläche: Stand auswählen');
  await user.click(screen.getByRole('button',{name:'Abgleich übernehmen'}));
  expect(screen.getByLabelText('Werkstoff')).toHaveValue('S460');
  expect(screen.getByLabelText('Abmessung / Profil')).toHaveValue('50 mm');
  await user.click(screen.getByRole('button',{name:'Bedarf speichern'}));
  await waitFor(()=>expect(puts).toHaveLength(2));
  expect(puts[1]).toMatchObject({version:5,position:{werkstoff:'S460',abmessung:'50 mm',oberflaeche:'Verzinkt'}});
 });
});
