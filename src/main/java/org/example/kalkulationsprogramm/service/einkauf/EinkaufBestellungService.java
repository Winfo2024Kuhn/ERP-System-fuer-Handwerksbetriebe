package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.LocalDate; import java.util.*; import java.util.stream.Collectors;
import org.example.kalkulationsprogramm.domain.*; import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.*; import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.repository.*; import org.example.kalkulationsprogramm.service.DokumentnummerService;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.server.ResponseStatusException;

@Service
public class EinkaufBestellungService {
 private final EinkaufBestellungRepository bestellungen; private final BestellungRevisionRepository revisionen; private final AngebotVersionRepository angebotVersionen; private final EinkaufVergleichService vergleich;
 private final EinkaufBedarfRepository bedarfe; private final EinkaufMengenService mengen; private final DokumentnummerService nummern; private final EinkaufAuditService audit; private final ObjectMapper json; private final LieferantenArtikelPreiseRepository preise;
 public EinkaufBestellungService(EinkaufBestellungRepository b,BestellungRevisionRepository r,AngebotVersionRepository a,EinkaufVergleichService vergleich,EinkaufBedarfRepository bedarfe,EinkaufMengenService m,DokumentnummerService n,EinkaufAuditService audit,ObjectMapper json,LieferantenArtikelPreiseRepository preise){this.bestellungen=b;this.revisionen=r;this.angebotVersionen=a;this.vergleich=vergleich;this.bedarfe=bedarfe;this.mengen=m;this.nummern=n;this.audit=audit;this.json=json;this.preise=preise;}

 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Detail ausAngebot(AusAngebot r,Long actor){
  validiere(r==null?null:r.paket(),r==null?null:r.idempotenzKey(),actor); if(r.angebotVersionId()==null||r.angebotVersionId()<=0) throw bad("Die Angebotsfassung fehlt.");
  AngebotVersion offer=angebotVersionen.findById(r.angebotVersionId()).orElseThrow(()->new NoSuchElementException("Angebotsfassung nicht gefunden."));
  if(!"GEPRUEFT".equals(offer.getStatus()) || offer.getBestaetigtVon()==null) throw conflict("Nur ein geprüftes Angebot kann vorbereitet werden.");
  var request=offer.getAnfrageRevision(); if(request==null||request.getAnfrage()==null)throw conflict("Das Angebot ist keiner Anfragefassung zugeordnet."); var current=request.getAnfrage().getAktuelleRevision();
  if(current==null || !current.getId().equals(request.getId())) throw conflict("Nur ein geprüftes Angebot zur aktuellen Anfragefassung kann vorbereitet werden.");
  var ranked=vergleich.vergleiche(request.getAnfrage().getId(),LocalDate.now()).angebote().stream().filter(x->Objects.equals(x.angebotVersionId(),offer.getId())).findFirst().orElseThrow(()->conflict("Das Angebot fehlt im aktuellen Vollkostenvergleich."));
  if(!ranked.vollstaendig()||!ranked.technischGeeignet()||!ranked.gueltig()||ranked.nettoGesamt()==null)throw conflict("Vollkosten, Liefertermin, Gültigkeit oder technische Eignung sind nicht vollständig bestätigt.");
  if(offer.getPositionen().stream().anyMatch(p->p.getAbweichungen()!=null&&!p.getAbweichungen().isEmpty())&&offer.getAbweichungBestaetigtVon()==null) throw conflict("Die technische Abweichung muss ausdrücklich bestätigt werden.");
  if(offer.getGueltigBis()!=null&&offer.getGueltigBis().isBefore(LocalDate.now())) throw conflict("Das Angebot ist abgelaufen und braucht eine neue Lieferantenbestätigung.");
  long supplierId=offer.getAngebot().getBeteiligung().getKontakt().lieferantId();
  String hash=hash(offer.getId()+"|"+r.paket()+"|"+r.entscheidungsgrund());
  Detail duplicate=existing(r.idempotenzKey(),hash); if(duplicate!=null)return duplicate;
  Map<Long,Herkunft> selected=index(r.paket());
  Set<Long> validOrigins=offer.getAnfrageRevision().getPositionen().stream().flatMap(p->p.getHerkuenfte().stream()).map(h->h.getBedarf().getId()).collect(Collectors.toSet());
  if(!validOrigins.equals(selected.keySet())) throw bad("Das Bestellpaket muss der vollständigen geprüften Angebotsauswahl entsprechen.");
  if(offer.getPositionen().stream().noneMatch(p->p.getAnfragePosition().getSnapshot().artikelId()!=null)) throw conflict("Mindestens eine Position hat keine eindeutige Artikelzuordnung.");
  bedarfe.findeAlleFuerUpdate(selected.keySet().stream().sorted().toList());
  EinkaufBestellung order=new EinkaufBestellung(nummern.naechsteEinkaufsnummer("B",LocalDate.now()),supplierId,offer.getId(),request.getId(),offer.getAngebot().getBeteiligung().getKontakt(),r.idempotenzKey(),hash,actor);
  bestellungen.saveAndFlush(order);
  List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft> origins=r.paket().stream().map(h->new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft(h.bedarfId(),h.version(),h.menge())).toList();
  mengen.buche(origins,EinkaufMengenService.Mengenaktion.RESERVIEREN,"BESTELLUNG:"+order.getId(),UUID.randomUUID(),actor);
  bedarfe.flush(); Map<Long,EinkaufBedarf> reservedNeeds=bedarfe.findeAlleFuerUpdate(origins.stream().map(org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft::bedarfId).toList()).stream().collect(Collectors.toMap(EinkaufBedarf::getId,x->x));
  BestellungRevision revision=new BestellungRevision(order,1,snapshot("ANGEBOT", offer.getZahlungsbedingungen(), offer.getAngebotsnummer(), offer.getGueltigBis(), null, null, null),hash,actor);
  for(AngebotPosition offered:offer.getPositionen()) {
   AnfragePosition requestPosition=offered.getAnfragePosition();
   for(AnfrageHerkunft source:requestPosition.getHerkuenfte()) {
    Herkunft chosen=selected.get(source.getBedarf().getId()); if(chosen==null)continue;
    if(chosen.version()!=source.getBedarfVersion()||chosen.menge().compareTo(source.getMenge())!=0)throw conflict("Ein Bedarf oder die angebotene Paketmenge wurde geändert; bitte das Angebot neu berechnen.");
    if(requestPosition.getSnapshot().dokumente().stream().anyMatch(required->offered.getZeugnisse().stream().noneMatch(confirmed->confirmed.art()==required.art()&&("ENTHALTEN".equals(confirmed.status())||"AUFPREIS".equals(confirmed.status())))))throw conflict("Ein gefordertes Zeugnis ist im Angebot nicht bestätigt.");
    var base=offered.getAngeboten(); if(base==null||base.menge()==null||base.einheit()==null)throw conflict("Das Angebot enthält keine vollständige Preisbasis.");
    BigDecimal unitPrice=preisJeEinheit(offer,offered,requestPosition.getSnapshot());
    BestellungPosition line=new BestellungPosition(revision,requestPosition.getSnapshot(),chosen.menge(),unitPrice,offer.getWaehrung(),kosten(offer,offered),map(source.getBedarf().getLiefergruppe()));
    long reservedVersion=reservedNeeds.get(chosen.bedarfId()).getVersion(); line.addHerkunft(new BestellungHerkunft(line,chosen.bedarfId(),reservedVersion,chosen.menge(),chosen.version(),reservedVersion)); revision.addPosition(line);
   }
  }
  if(revision.getPositionen().isEmpty())throw bad("Das Bestellpaket enthält keine Angebotsposition.");
  order.addRevision(revision); bestellungen.saveAndFlush(order); audit.protokolliere("BESTELLUNG",order.getId(),"ENTWURF_AUS_ANGEBOT",actor,null,json.valueToTree(detail(order)),r.entscheidungsgrund()); return detail(order);
 }

 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Detail direkt(Direkt r,Long actor){
  validiere(r==null?null:r.paket(),r==null?null:r.idempotenzKey(),actor); if(r.lieferantId()==null||r.lieferantId()<=0||r.empfaenger()==null||!Objects.equals(r.lieferantId(),r.empfaenger().lieferantId()))throw bad("Lieferant und Empfänger müssen zusammenpassen.");
  String hash=hash(r.toString()); Detail duplicate=existing(r.idempotenzKey(),hash);if(duplicate!=null)return duplicate;
  Map<Long,Direktpreis> directPrices=r.preise().stream().collect(Collectors.toMap(Direktpreis::bedarfId,x->x,(a,b)->{throw bad("Ein Bedarf hat mehrere Preise.");}));
  List<Long> ids=r.paket().stream().map(Herkunft::bedarfId).distinct().sorted().toList(); if(ids.size()!=r.paket().size())throw bad("Ein Bedarf darf nur einmal ausgewählt werden.");
  List<EinkaufBedarf> locked=bedarfe.findeAlleFuerUpdate(ids);if(locked.size()!=ids.size())throw new NoSuchElementException("Ein Einkaufsbedarf wurde nicht gefunden.");
  Map<Long,EinkaufBedarf> byId=locked.stream().collect(Collectors.toMap(EinkaufBedarf::getId,x->x));
  for(Herkunft h:r.paket()){EinkaufBedarf b=byId.get(h.bedarfId());Direktpreis p=directPrices.get(h.bedarfId());if(b.getVersion()==null||b.getVersion()!=h.version()||b.getPosition()==null||b.getPosition().artikelId()==null)throw conflict("Bedarf oder Artikelzuordnung ist nicht mehr aktuell.");if(p==null||p.preis()==null||p.preis().signum()<=0||p.basisMenge()==null||p.basisMenge().signum()<=0||b.getPosition().basis()==null||p.einheit()!=b.getPosition().basis().einheit()||p.bestaetigungsbeleg()==null||p.bestaetigungsbeleg().isBlank()||p.bestaetigtAm()==null||p.bestaetigtAm().isAfter(LocalDate.now())||p.gueltigBis()!=null&&p.gueltigBis().isBefore(LocalDate.now()))throw conflict("Für jede Position braucht es einen belegten aktuellen Lieferantenpreis auf derselben Einheitenbasis.");if(p.preisHistorieId()!=null&&!preise.findById(p.preisHistorieId()).filter(old->old.getArtikel()!=null&&old.getArtikel().getId().equals(b.getPosition().artikelId())&&old.getLieferant()!=null&&old.getLieferant().getId().equals(r.lieferantId())&&old.isAktuell()&&old.getPreis()!=null&&old.getPreis().compareTo(p.preis())==0).isPresent())throw conflict("Der ausgewählte Preisstand passt nicht zu Artikel, Lieferant und Betrag.");}
  EinkaufBestellung order=new EinkaufBestellung(nummern.naechsteEinkaufsnummer("B",LocalDate.now()),r.lieferantId(),null,null,r.empfaenger(),r.idempotenzKey(),hash,actor);bestellungen.saveAndFlush(order);
  mengen.buche(r.paket().stream().map(h->new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft(h.bedarfId(),h.version(),h.menge())).toList(),EinkaufMengenService.Mengenaktion.RESERVIEREN,"BESTELLUNG:"+order.getId(),UUID.randomUUID(),actor);
  bedarfe.flush(); Map<Long,EinkaufBedarf> reservedNeeds=bedarfe.findeAlleFuerUpdate(ids).stream().collect(Collectors.toMap(EinkaufBedarf::getId,x->x));
  BestellungRevision revision=new BestellungRevision(order,1,snapshot("DIREKT", r.bedingungen(), null, null, r.liefertermin(), r.bestaetigungsfrist(), r.idempotenzKey()),hash,actor);
  for(Herkunft h:r.paket()){EinkaufBedarf b=byId.get(h.bedarfId());Direktpreis p=directPrices.get(h.bedarfId());BigDecimal unitPrice=p.preis().divide(p.basisMenge(),6,java.math.RoundingMode.HALF_UP);List<Map<String,Object>> costs=List.of(Map.of("art","MATERIAL","preis",unitPrice,"beleg",p.bestaetigungsbeleg()));BestellungPosition line=new BestellungPosition(revision,b.getPosition(),h.menge(),unitPrice,"EUR",costs,map(b.getLiefergruppe()));long reservedVersion=reservedNeeds.get(h.bedarfId()).getVersion();line.addHerkunft(new BestellungHerkunft(line,h.bedarfId(),reservedVersion,h.menge(),h.version(),reservedVersion));revision.addPosition(line);}
  order.addRevision(revision);bestellungen.saveAndFlush(order);audit.protokolliere("BESTELLUNG",order.getId(),"DIREKT_ENTWURF",actor,null,json.valueToTree(detail(order)),r.bedingungen());return detail(order);
 }
 @Transactional(readOnly=true) public Page<Detail> suche(Pageable pageable){if(pageable==null)throw bad("Seiteneinstellungen fehlen.");return bestellungen.suche(pageable).map(this::detail);}
 @Transactional(readOnly=true) public Detail lade(Long id){return detail(bestellungen.findById(id).orElseThrow(()->new NoSuchElementException("Bestellung nicht gefunden.")));}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void verwerfen(Long id,long version,Long actor){EinkaufBestellung o=EinkaufBestellSperren.sperre(id,List.of(),bestellungen,revisionen,bedarfe);if(o.getVersion()==null||o.getVersion()!=version)throw conflict("Die Bestellung wurde geändert.");if(o.getStatus()!=BestellungStatus.ENTWURF)throw conflict("Nur ein noch nicht versandter Entwurf kann verworfen werden.");List<Herkunft> origins=o.getRevisionen().stream().reduce((a,b)->b).orElseThrow().getPositionen().stream().flatMap(p->p.getHerkuenfte().stream()).map(h->new Herkunft(h.getBedarfId(),h.getBedarfVersion(),h.getMenge())).toList();mengen.buche(origins.stream().map(h->new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft(h.bedarfId(),h.version(),h.menge())).toList(),EinkaufMengenService.Mengenaktion.RESERVIERUNG_FREIGEBEN,"BESTELLUNG:"+id,UUID.randomUUID(),actor);o.setStatus(BestellungStatus.STORNIERT);audit.protokolliere("BESTELLUNG",id,"ENTWURF_VERWORFEN",actor,null,null,"Entwurf verworfen");}
 @Transactional(readOnly=true) public List<Revision> revisionen(Long id){return revisionen.findByBestellung_IdOrderByNummerAsc(id).stream().map(this::revisionDto).toList();}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Detail aendern(Long id,Aenderung r,Long actor){
  validiere(r==null||r.inhalt()==null?null:r.inhalt().paket(), r==null||r.inhalt()==null?null:r.inhalt().idempotenzKey(), actor);
  EinkaufBestellung order=EinkaufBestellSperren.sperre(id,r.inhalt().paket().stream().map(Herkunft::bedarfId).toList(),bestellungen,revisionen,bedarfe);
  if(r==null||r.inhalt()==null||r.inhalt().idempotenzKey()==null||order.getVersion()==null||order.getVersion()!=r.version()||actor==null||actor<=0)throw conflict("Bestellversion, Inhalt und Benutzer müssen gültig sein.");
  if(order.getStatus()!=BestellungStatus.BESTELLT&&order.getStatus()!=BestellungStatus.TEILGELIEFERT)throw conflict("Nur versandte, noch offene Bestellungen können mit einer neuen Revision geändert werden.");
  if(!Objects.equals(order.getLieferantId(),r.inhalt().lieferantId())||r.inhalt().empfaenger()==null||!Objects.equals(order.getLieferantId(),r.inhalt().empfaenger().lieferantId()))throw bad("Eine Revision muss beim selben Lieferanten bleiben.");
  BestellungRevision previous=revisionen.findFirstByBestellung_IdOrderByNummerDesc(id).orElseThrow(()->conflict("Bestellung hat keine Revision."));
  String hash=hash(r.toString());if(Objects.equals(previous.getSnapshot().get("idempotenzKey"),r.inhalt().idempotenzKey().toString())){if(!Objects.equals(previous.getSha256(),hash))throw conflict("Der Idempotenzschlüssel gehört zu einer anderen Bestelländerung.");return detail(order);}
  if(!previous.istAngenommen())throw conflict("Die vorige Bestellfassung muss zuerst versandt und geklärt werden.");
  Map<Long,BigDecimal> old=new HashMap<>();mengen.standFuerVorgang("BESTELLUNG:"+id).forEach((need,stand)->old.put(need,stand.bestellt()));
  Map<Long,Herkunft> requested=index(r.inhalt().paket());Map<Long,Direktpreis> prices=r.inhalt().preise().stream().collect(Collectors.toMap(Direktpreis::bedarfId,x->x,(a,b)->{throw bad("Ein Bedarf hat mehrere Preisstände.");}));
  List<Long> ids=requested.keySet().stream().sorted().toList();List<EinkaufBedarf> locked=bedarfe.findeAlleFuerUpdate(ids);if(locked.size()!=ids.size())throw new NoSuchElementException("Ein Bedarf fehlt.");Map<Long,EinkaufBedarf> byId=locked.stream().collect(Collectors.toMap(EinkaufBedarf::getId,x->x));Map<Long,BigDecimal> unitPrices=new HashMap<>();
  List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft> additions=new ArrayList<>();for(Herkunft h:requested.values()){EinkaufBedarf b=byId.get(h.bedarfId());if(b.getVersion()==null||b.getVersion()!=h.version()||b.getPosition()==null||b.getPosition().artikelId()==null||b.getPosition().basis()==null)throw conflict("Ein Bedarf oder seine Artikelzuordnung ist veraltet.");Direktpreis price=prices.get(h.bedarfId());if(price==null||price.preis()==null||price.preis().signum()<=0||price.basisMenge()==null||price.basisMenge().signum()<=0||price.einheit()!=b.getPosition().basis().einheit()||price.bestaetigtAm()==null||price.bestaetigtAm().isAfter(LocalDate.now())||price.gueltigBis()!=null&&price.gueltigBis().isBefore(LocalDate.now())||price.bestaetigungsbeleg()==null||price.bestaetigungsbeleg().isBlank())throw conflict("Jede geänderte Position braucht einen aktuellen, belegten Preis auf derselben Einheitenbasis.");if(price.preisHistorieId()!=null&&!preise.findById(price.preisHistorieId()).filter(p->p.getArtikel()!=null&&Objects.equals(p.getArtikel().getId(),b.getPosition().artikelId())&&p.getLieferant()!=null&&Objects.equals(p.getLieferant().getId(),order.getLieferantId())&&p.isAktuell()&&p.getPreis()!=null&&p.getPreis().compareTo(price.preis())==0).isPresent())throw conflict("Der ausgewählte Preisstand passt nicht zu Artikel, Lieferant und Betrag.");unitPrices.put(h.bedarfId(),price.preis().divide(price.basisMenge(),6,java.math.RoundingMode.HALF_UP));BigDecimal delta=h.menge().subtract(old.getOrDefault(h.bedarfId(),BigDecimal.ZERO));if(delta.signum()>0)additions.add(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft(h.bedarfId(),h.version(),delta));}
  if(!additions.isEmpty()){mengen.buche(additions,EinkaufMengenService.Mengenaktion.RESERVIEREN,"BESTELLUNG:"+id,r.inhalt().idempotenzKey(),actor);bedarfe.flush();locked=bedarfe.findeAlleFuerUpdate(ids);byId=locked.stream().collect(Collectors.toMap(EinkaufBedarf::getId,x->x));}
  BestellungRevision next=new BestellungRevision(order,previous.getNummer()+1,snapshot("AENDERUNG", r.inhalt().bedingungen(), null, null, r.inhalt().liefertermin(), r.inhalt().bestaetigungsfrist(), r.inhalt().idempotenzKey()),hash,actor);
  for(Herkunft h:r.inhalt().paket()){EinkaufBedarf b=byId.get(h.bedarfId());Direktpreis p=prices.get(h.bedarfId());BigDecimal unitPrice=unitPrices.get(h.bedarfId());BestellungPosition line=new BestellungPosition(next,b.getPosition(),h.menge(),unitPrice,"EUR",List.of(Map.of("art","MATERIAL","preis",unitPrice,"beleg",p.bestaetigungsbeleg())),map(b.getLiefergruppe()));long currentVersion=b.getVersion();line.addHerkunft(new BestellungHerkunft(line,h.bedarfId(),currentVersion,h.menge(),h.version(),currentVersion));next.addPosition(line);}
  order.addRevision(next);bestellungen.saveAndFlush(order);audit.protokolliere("BESTELLUNG",id,"REVISION_ERSTELLT",actor,json.valueToTree(detail(order)),json.valueToTree(detail(order)),r.grund());return detail(order);
 }

 private Detail existing(UUID key,String hash){EinkaufBestellung e=bestellungen.findByIdempotenzKey(key).orElse(null);if(e==null)return null;if(!e.getPayloadHash().equals(hash))throw conflict("Der Idempotenzschlüssel gehört zu einem anderen Bestellinhalt.");return detail(e);}
 private void validiere(List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft> p,UUID key,Long actor){if(p==null||p.isEmpty()||p.stream().anyMatch(x->x==null||x.bedarfId()==null||x.bedarfId()<=0||x.version()<0||x.menge()==null||x.menge().signum()<=0)||key==null||actor==null||actor<=0)throw bad("Paket, Idempotenzschlüssel und Benutzer müssen gültig sein.");}
 private Map<Long,org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft> index(List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft> origins){if(origins.stream().map(org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft::bedarfId).distinct().count()!=origins.size())throw bad("Ein Bedarf darf nur einmal ausgewählt werden.");return origins.stream().collect(Collectors.toMap(org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft::bedarfId,x->x));}
 private Map<String,Object> snapshot(String typ, String bedingungen, String angebotsnummer, LocalDate gueltigBis,
         LocalDate liefertermin, LocalDate bestaetigungsfrist, UUID idempotenzKey) {
  Map<String,Object> value = new LinkedHashMap<>();
  value.put("typ",typ);
  if(bedingungen!=null)value.put("bedingungen",bedingungen);
  if(angebotsnummer!=null)value.put("angebotsnummer",angebotsnummer);
  if(gueltigBis!=null)value.put("gueltigBis",gueltigBis.toString());
  if(liefertermin!=null)value.put("liefertermin",liefertermin.toString());
  if(bestaetigungsfrist!=null)value.put("bestaetigungsfrist",bestaetigungsfrist.toString());
  if(idempotenzKey!=null)value.put("idempotenzKey",idempotenzKey.toString());
  return Map.copyOf(value);
 }
 private Map<String,Object> map(Object o){return json.convertValue(o,Map.class);}
 private List<Map<String,Object>> kosten(AngebotVersion v,AngebotPosition p){return v.getKosten().stream().filter(k->Objects.equals(k.getPositionId(),p.getId())).map(this::map).toList();}
 private BigDecimal preisJeEinheit(AngebotVersion version,AngebotPosition offered,PositionSnapshot snapshot){
  Set<String> oneTime=Set.of("FRACHT","BEARBEITUNG","VERPACKUNG","ZEUGNIS","ZUSCHNITT","MINDERMENGE","RABATT");
  var costs=version.getKosten().stream().filter(k->Objects.equals(k.getPositionId(),offered.getId())&&!oneTime.contains(k.getArt()))
    .map(k->new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Kosten(k.getSchluessel(),k.getArt(),k.getBetrag(),k.getBasis(),k.getBasisMenge(),k.getProzentBasisSchluessel(),k.isEnthalten(),k.isVariabel(),k.getQuelle())).toList();
  var calculated=EinkaufVergleichService.berechneKosten(costs,snapshot.basis());
  if(!calculated.vollstaendig()||calculated.nettoGesamt()==null||snapshot.basis().menge()==null||snapshot.basis().menge().signum()<=0)throw conflict("Die wiederkehrenden Materialkosten der Angebotsposition sind unvollständig.");
  return calculated.nettoGesamt().divide(snapshot.basis().menge(),6,java.math.RoundingMode.HALF_UP);
 }
 private Detail detail(EinkaufBestellung o){List<BestellungRevision> rs=o.getRevisionen().isEmpty()?revisionen.findByBestellung_IdOrderByNummerAsc(o.getId()):o.getRevisionen();return new Detail(o.getId(),o.getVersion()==null?0:o.getVersion(),o.getNummer(),o.getLieferantId(),o.getAngebotsversionId(),o.getAnfrageRevisionId(),o.getEmpfaenger(),o.getStatus(),o.getLieferantenStatus(),rs.stream().map(this::revisionDto).toList());}
 private Revision revisionDto(BestellungRevision r){return new Revision(r.getId(),r.getNummer(),r.getVersion()==null?0:r.getVersion(),r.getSnapshot(),r.getSha256(),r.getVersandId(),r.getPositionen().stream().map(p->new Position(p.getId(),p.getPosition(),p.getMenge(),p.getNettoEinzelpreis(),p.getHerkuenfte().stream().map(h->new Herkunft(h.getBedarfId(),h.getBedarfVersion(),h.getMenge())).toList())).toList());}
 private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
}
