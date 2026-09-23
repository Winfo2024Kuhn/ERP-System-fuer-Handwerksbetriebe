package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.ObjectMapper;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.time.*;import java.util.*;import java.util.stream.Collectors;
import org.example.email.EmailService;import org.example.kalkulationsprogramm.domain.LieferantDokument;import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;import org.example.kalkulationsprogramm.domain.einkauf.*;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellfreigabeDto.*;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Vorschau;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.*;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.*;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.*;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.*;import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Nachricht;import org.example.kalkulationsprogramm.repository.*;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.server.ResponseStatusException;

@Service public class EinkaufBestellfreigabeService {
 private static final java.security.SecureRandom RANDOM=new java.security.SecureRandom();
 private final EinkaufBestellungRepository orders;private final BestellungRevisionRepository revisions;private final AngebotVersionRepository offers;private final EinkaufKommunikationVorschauRepository previews;private final EinkaufVorlagenService templates;private final EinkaufPdfService pdf;private final EinkaufDateiService files;private final EinkaufOutboxService outbox;private final EinkaufVersandWorker worker;private final EinkaufVersandauftragRepository outboxRepo;private final EinkaufMengenService amounts;private final EinkaufBedarfRepository needs;private final LieferantDokumentRepository documents;private final EinkaufAuditService audit;private final ObjectMapper json;
 public EinkaufBestellfreigabeService(EinkaufBestellungRepository orders,BestellungRevisionRepository revisions,AngebotVersionRepository offers,EinkaufKommunikationVorschauRepository previews,EinkaufVorlagenService templates,EinkaufPdfService pdf,EinkaufDateiService files,EinkaufOutboxService outbox,EinkaufVersandWorker worker,EinkaufVersandauftragRepository outboxRepo,EinkaufMengenService amounts,EinkaufBedarfRepository needs,LieferantDokumentRepository documents,EinkaufAuditService audit,ObjectMapper json){this.orders=orders;this.revisions=revisions;this.offers=offers;this.previews=previews;this.templates=templates;this.pdf=pdf;this.files=files;this.outbox=outbox;this.worker=worker;this.outboxRepo=outboxRepo;this.amounts=amounts;this.needs=needs;this.documents=documents;this.audit=audit;this.json=json;}

 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Vorschau vorschau(Long orderId,Long templateId){EinkaufBestellung order=orders.findById(orderId).orElseThrow(()->new NoSuchElementException("Bestellung nicht gefunden."));BestellungRevision revision=latest(orderId);pruefeOffeneRevision(order,revision);validateContent(order,revision);List<PositionSnapshot> snapshots=revision.getPositionen().stream().map(BestellungPosition::getPosition).toList();List<Long> attachments=snapshots.stream().flatMap(p->p.anlageVersionIds().stream()).distinct().sorted().toList();List<Liefergruppe> groups=revision.getPositionen().stream().map(p->json.convertValue(p.getLiefergruppe(),Liefergruppe.class)).distinct().toList();
  String type=order.getAngebotsversionId()==null?"EINKAUF_DIREKTBESTELLUNG":"EINKAUF_BESTELLUNG";Map<String,String> values=new LinkedHashMap<>();values.put("LIEFERANTENNAME",safe(order.getEmpfaenger().lieferantenname()));values.put("ANSPRECHPARTNER",safe(order.getEmpfaenger().name()));values.put("ANREDE",safe(order.getEmpfaenger().anrede()));values.put("LIEFERADRESSE",groups.stream().map(Liefergruppe::lieferadresse).filter(Objects::nonNull).findFirst().orElse(""));values.put("EIGENE_KUNDENNUMMER_BEIM_LIEFERANTEN",safe(order.getEmpfaenger().eigeneKundennummer()));values.put("BESTELLNUMMER",order.getNummer());values.put("RUECKMELDECODE","BESTELLUNG:"+order.getNummer());if(order.getAnfrageRevisionId()!=null){var source=offers.findById(order.getAngebotsversionId()).orElseThrow();values.put("ANFRAGENUMMER",source.getAnfrageRevision().getAnfrage().getPaNummer());values.put("LIEFERANTEN_ANGEBOTSNUMMER",safe(source.getAngebotsnummer()));}
  Gerendert rendered=templates.rendern(templateId,new VorlagenKontext(type,values,snapshots,"BESTELLUNG:"+order.getNummer()));List<PdfPosition> pdfPositions=revision.getPositionen().stream().map(p->new PdfPosition(String.valueOf(p.getId()),p.getPosition(),p.getHerkuenfte().stream().map(h->new PdfHerkunft(h.getBedarfId(),null,h.getMenge(),p.getPosition().basis()==null?null:p.getPosition().basis().einheit())).toList(),List.of(),p.getNettoEinzelpreis()==null?null:p.getNettoEinzelpreis().multiply(p.getMenge()))).toList();byte[] pdfBytes=pdf.erzeugen(new Beleg("BESTELLUNG",order.getNummer(),revision.getNummer(),order.getEmpfaenger(),pdfPositions,List.of(),groups,null,null,(String)revision.getSnapshot().get("bedingungen"),null,true));files.pruefePaketgroesse(attachments,pdfBytes.length);var stored=files.speicherePdfSnapshot(pdfBytes,order.getNummer()+"-Bestellung-"+revision.getNummer()+".pdf");String token=token();String hash=sha256(token+"|"+order.getId()+"|"+order.getVersion()+"|"+revision.getId()+"|"+rendered.subject()+"|"+rendered.htmlBody()+"|"+stored.dateiId()+"|"+sha256(pdfBytes)+"|"+attachments);Instant now=Instant.now();previews.saveAndFlush(new EinkaufKommunikationVorschau(token,orderId,orderId,revision.getId(),templateId,rendered.version(),rendered.subject(),rendered.htmlBody(),order.getEmpfaenger().email(),attachments,stored.dateiId(),sha256(pdfBytes),hash,now,now.plus(Duration.ofDays(1))));return new Vorschau(order.getVersion(),token,rendered.subject(),rendered.htmlBody(),order.getEmpfaenger().email(),stored.dateiId(),attachments);}

 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public VersandDto freigeben(Long orderId,Freigabe r,Long actor){if(r==null||r.idempotenzKey()==null||r.vorschauHash()==null||actor==null||actor<=0)throw bad("Vorschau, Idempotenzschlüssel und Benutzer sind erforderlich.");EinkaufBestellung order=EinkaufBestellSperren.sperre(orderId,List.of(),orders,revisions,needs);
  var retry=outboxRepo.findByIdempotenzKey(r.idempotenzKey());
  if(retry.isPresent()){var existing=retry.get();if(!"BESTELLUNG".equals(existing.getTyp())||!orderId.equals(existing.getVorgangId())||!r.vorschauHash().equals(existing.getFreigabeHash()))throw conflict("Der Idempotenzschlüssel gehört zu einer anderen Freigabe.");return versand(existing);}
  BestellungRevision revision=latest(orderId);if(order.getVersion()==null||order.getVersion()!=r.version())throw conflict("Die Bestellung wurde geändert; bitte neu prüfen.");pruefeOffeneRevision(order,revision);validateContent(order,revision);EinkaufKommunikationVorschau preview=previews.findByFreigabeTokenAndAnfrageIdAndBeteiligungId(r.vorschauHash(),orderId,orderId).orElseThrow(()->bad("Die Vorschau ist abgelaufen. Bitte neu erstellen."));if(!Instant.now().isBefore(preview.getGueltigBis())||!preview.getRevisionId().equals(revision.getId()))throw conflict("Vorschau und Bestellfassung stimmen nicht mehr überein.");byte[] pdfBytes=files.ladePdfSnapshotBytes(preview.getPdfDateiId());String verified=sha256(r.vorschauHash()+"|"+order.getId()+"|"+order.getVersion()+"|"+revision.getId()+"|"+preview.getSubject()+"|"+preview.getHtmlBody()+"|"+preview.getPdfDateiId()+"|"+preview.getPdfSha256()+"|"+preview.getAnlageVersionIds());if(!sha256(pdfBytes).equals(preview.getPdfSha256())||!verified.equals(preview.getInhaltSha256()))throw conflict("PDF oder Freigabesnapshot wurde verändert.");
  Long participation=participationId(order);var prior=outboxRepo.findFirstByTypAndVorgangIdAndRevisionIdAndBeteiligungIdOrderByIdAsc("BESTELLUNG",orderId,revision.getId(),participation);if(prior.isPresent()){EinkaufVersandauftrag existing=prior.get();if(!r.idempotenzKey().equals(existing.getIdempotenzKey())||!r.vorschauHash().equals(existing.getFreigabeHash()))throw conflict("Für diese Bestellung besteht bereits ein Versandauftrag. Bitte dessen Status prüfen oder den vorhandenen Auftrag klären.");return versand(existing);}
  files.pruefePaketgroesse(preview.getAnlageVersionIds(),pdfBytes.length);List<EmailService.Attachment> attachments=new ArrayList<>(files.ladeVersandanlagen(preview.getAnlageVersionIds()));attachments.add(new EmailService.Attachment(pdfBytes,order.getNummer()+"-Bestellung.pdf","application/pdf"));var message=new Nachricht(null,preview.getEmpfaenger(),preview.getSubject(),preview.getHtmlBody(),null,List.of(),attachments);var snapshot=new VersandSnapshot("BESTELLUNG",orderId,revision.getId(),participation,new KontoZugangReferenz("EINKAUF"),message,r.vorschauHash());var result=outbox.einreihen(snapshot,r.idempotenzKey(),actor);revision.setVersandId(result.id());revisions.save(revision);audit.protokolliere("BESTELLUNG",orderId,"BESTELLUNG_FREIGEGEBEN",actor,null,json.valueToTree(result),"Freigegebener Vorschauhash "+r.vorschauHash());worker.dispatchNachCommit(result.id());return result;}


 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public void versandAngenommen(EinkaufVersandAngenommen event) {
  if(event==null || !"BESTELLUNG".equals(event.typ()) || event.ereignisSchluessel()==null)
   throw bad("Bestellannahmeereignis ist ungültig.");
  var order=EinkaufBestellSperren.sperre(event.vorgangId(),List.of(),orders,revisions,needs);
  var revision=revisions.findById(event.revisionId()).orElseThrow(()->bad("Bestellfassung fehlt."));
  if(!Objects.equals(revision.getBestellung().getId(),order.getId())
      || !Objects.equals(participationId(order),event.beteiligungId())
      || !Objects.equals(revision.getVersandId(),event.versandId()))throw conflict("Versandereignis gehört nicht zu dieser Bestellfassung.");
  if(revision.istAngenommen())return;
  if(!Objects.equals(latest(order.getId()).getId(),revision.getId()))throw conflict("Die ausstehende Bestellfassung wurde verändert.");
  var dispatch=outboxRepo.findById(event.versandId()).orElseThrow(()->bad("Versandauftrag fehlt."));
  if(dispatch.getStatus()!=EinkaufVersandauftrag.Status.ANGENOMMEN)throw conflict("Die Mail wurde noch nicht vom Mailserver angenommen.");
  bucheAnnahme(order,revision,event.ereignisSchluessel(),order.getAngelegtVon(),event.zeit());
 }

 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public void externGesendet(Long id,ExternerNachweis request,Long actor) {
  if(request==null || request.idempotenzKey()==null || request.dateiId()==null || request.dateiId()<=0
      || request.versendetAm()==null || request.versendetAm().isAfter(Instant.now())
      || request.begruendung()==null || request.begruendung().isBlank() || actor==null || actor<=0)
   throw bad("Versandnachweis, tatsächlicher Versandzeitpunkt und Benutzer sind erforderlich.");
  var payload=json.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(request);
  payload.put("versendetAm",request.versendetAm().toString());
  var order=EinkaufBestellSperren.sperre(id,List.of(),orders,revisions,needs);
  if(audit.istWiederholung("BESTELLUNG",id,"EXTERNER_VERSAND_DOKUMENTIERT",actor,payload))return;
  if(order.getVersion()==null || order.getVersion()!=request.version())throw conflict("Die Bestellung wurde geändert.");
  var revision=latest(id);pruefeOffeneRevision(order,revision);validateContent(order,revision);
  if(revision.getVersandId()!=null)throw conflict("Für diese Fassung besteht ein Mailauftrag. Bitte zuerst dessen Ausgang klären.");
  var evidence=new LinkedHashMap<>(files.pruefeExternenVersandbeleg(request.dateiId(),order.getLieferantId(),id));
  if(evidence.isEmpty())throw bad("Ein lesbarer, zugeordneter Übermittlungsbeleg fehlt.");
  evidence.put("versendetAm",request.versendetAm().toString());evidence.put("begruendung",request.begruendung());
  evidence.put("akteurId",actor);evidence.put("idempotenzKey",request.idempotenzKey().toString());
  revision.externerNachweis(evidence);
  bucheAnnahme(order,revision,request.idempotenzKey(),actor,request.versendetAm());
  audit.protokolliere("BESTELLUNG",id,"EXTERNER_VERSAND_DOKUMENTIERT",actor,null,payload,request.begruendung());
 }

 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public StornoErgebnis stornoBestaetigen(Long id,Storno request,Long actor) {
  if(request==null || request.idempotenzKey()==null || request.belegDateiId()==null || request.belegDateiId()<=0
      || request.grund()==null || request.grund().isBlank() || request.anteile().isEmpty() || actor==null || actor<=0)
   throw bad("Stornobeleg, Begründung, Mengenanteile und Benutzer sind erforderlich.");
  var order=EinkaufBestellSperren.sperre(id,List.of(),orders,revisions,needs);
  if(audit.istWiederholung("BESTELLUNG",id,"STORNO_BESTAETIGT",actor,json.valueToTree(request)))
   return new StornoErgebnis(id,order.getStatus(),"Dieser Stornobeleg wurde bereits erfasst.");
  if(order.getVersion()==null || order.getVersion()!=request.version())throw conflict("Bestellung wurde geändert.");
  if(!latest(id).istAngenommen())throw conflict("Bitte die ausstehende Bestelländerung zuerst versenden oder klären.");
  var proof=documents.sperreEinkaufsbeleg(request.belegDateiId()).orElseThrow(()->new NoSuchElementException("Stornobeleg nicht gefunden."));
  if(proof.getTyp()!=LieferantDokumentTyp.GUTSCHRIFT || proof.getLieferant()==null
      || !Objects.equals(proof.getLieferant().getId(),order.getLieferantId()) || proof.getEinkaufBestellungId()!=null)
   throw bad("Der Stornobeleg muss neu und vom Bestelllieferanten sein.");
  var balances=amounts.standFuerVorgang("BESTELLUNG:"+id);
  Map<Long,java.math.BigDecimal> shares=new LinkedHashMap<>();
  for(var share:request.anteile()) {
   if(share==null || share.bedarfId()==null || share.menge()==null || share.menge().signum()<=0
       || shares.putIfAbsent(share.bedarfId(),share.menge())!=null)throw bad("Stornoanteile sind ungültig oder doppelt.");
   var balance=balances.getOrDefault(share.bedarfId(),EinkaufMengenService.Vorgangsmenge.leer());
   if(share.menge().compareTo(balance.offen())>0)throw conflict("Der Storno überschreitet die noch offene Menge dieser Bestellung.");
  }
  amounts.buche(EinkaufBestellSperren.aktuelleAnteile(shares,needs),EinkaufMengenService.Mengenaktion.STORNO_BESTAETIGEN,
      "BESTELLUNG:"+id,request.idempotenzKey(),actor);needs.flush();
  proof.setEinkaufBestellungId(id);
  EinkaufBestellSperren.aktualisiereStatus(order,amounts.standFuerVorgang("BESTELLUNG:"+id));
  audit.protokolliere("BESTELLUNG",id,"STORNO_BESTAETIGT",actor,null,json.valueToTree(request),request.grund());
  return new StornoErgebnis(id,order.getStatus(),"Die belegten, noch nicht gelieferten Mengen wurden freigegeben.");
 }

 private void bucheAnnahme(EinkaufBestellung order,BestellungRevision revision,UUID key,Long actor,Instant zeit) {
  pruefeOffeneRevision(order,revision);
  var balances=amounts.standFuerVorgang("BESTELLUNG:"+order.getId());
  EinkaufBestellSperren.mengen(revision).forEach((id,quantity)->{
   var balance=balances.getOrDefault(id,EinkaufMengenService.Vorgangsmenge.leer());
   if(quantity.compareTo(balance.bestellt().add(balance.reserviert()))>0)throw conflict("Für die Bestellfassung fehlt eine eigene Mengenreservierung.");
  });
  Map<Long,java.math.BigDecimal> additions=new LinkedHashMap<>();
  balances.forEach((id,balance)->{if(balance.reserviert().signum()>0)additions.put(id,balance.reserviert());});
  if(!additions.isEmpty())amounts.buche(EinkaufBestellSperren.aktuelleAnteile(additions,needs),
      EinkaufMengenService.Mengenaktion.BESTELLEN,"BESTELLUNG:"+order.getId(),key,actor);
  needs.flush();revision.angenommen(zeit==null?Instant.now():zeit);
  EinkaufBestellSperren.aktualisiereStatus(order,amounts.standFuerVorgang("BESTELLUNG:"+order.getId()));
  order.setLieferantenStatus(LieferantenBestellstatus.AUSSTEHEND);
 }
 private void pruefeOffeneRevision(EinkaufBestellung order,BestellungRevision revision) {
  if(revision.istAngenommen() || order.getStatus()==BestellungStatus.STORNIERT || order.getStatus()==BestellungStatus.GELIEFERT)
   throw conflict("Diese Bestellfassung ist bereits versandt oder abgeschlossen.");
  if(order.getStatus()!=BestellungStatus.ENTWURF && !"AENDERUNG".equals(revision.getSnapshot().get("typ")))
   throw conflict("Nur ein Entwurf oder eine neue Änderungsfassung kann versendet werden.");
 }

 private void validateContent(EinkaufBestellung o,BestellungRevision r){if(o.getEmpfaenger()==null||o.getEmpfaenger().email()==null||o.getEmpfaenger().email().isBlank()||r.getPositionen().isEmpty())throw conflict("Empfänger oder Bestellpositionen fehlen.");boolean amendment="AENDERUNG".equals(r.getSnapshot().get("typ"));if(o.getAngebotsversionId()!=null&&!amendment){AngebotVersion v=offers.findById(o.getAngebotsversionId()).orElseThrow();if(v.getGueltigBis()!=null&&v.getGueltigBis().isBefore(LocalDate.now()))throw conflict("Angebot ist abgelaufen; vor dem Versand ist eine neue belegte Bestätigung nötig.");if(!"GEPRUEFT".equals(v.getStatus())||v.getBestaetigtVon()==null)throw conflict("Angebotsprüfung ist nicht mehr gültig.");}if(r.getPositionen().stream().anyMatch(p->p.getMenge()==null||p.getMenge().signum()<=0||p.getNettoEinzelpreis()==null||p.getNettoEinzelpreis().signum()<=0||p.getPosition()==null||p.getPosition().dokumente().stream().anyMatch(d->!d.fachlichBestaetigt())))throw conflict("Preis, Menge oder Zeugnisangaben sind unvollständig.");}
 private List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft> toMengenHerkunft(BestellungRevision r){return r.getPositionen().stream().flatMap(p->p.getHerkuenfte().stream()).map(h->new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft(h.getBedarfId(),h.getReservierungsVersion(),h.getMenge())).collect(Collectors.toMap(org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft::bedarfId,x->x,(a,b)->new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft(a.bedarfId(),a.version(),a.menge().add(b.menge())))).values().stream().toList();}
 private BestellungRevision latest(Long id){return revisions.findFirstByBestellung_IdOrderByNummerDesc(id).orElseThrow(()->conflict("Bestellung hat keine Revision."));}
 private Long participationId(EinkaufBestellung o){if(o.getAngebotsversionId()==null)return null;return offers.findById(o.getAngebotsversionId()).map(v->v.getAngebot().getBeteiligung().getId()).orElse(null);}
 private VersandDto versand(EinkaufVersandauftrag a){return new VersandDto(a.getId(),a.getVersion(),a.getTyp(),a.getVorgangId(),a.getRevisionId(),a.getStatus().name(),a.getFehlerCode(),a.getErstelltAm(),a.getAngenommenAm(),a.getArchiviertAm()!=null,a.getMessageId());}
 private static String token(){byte[] b=new byte[32];RANDOM.nextBytes(b);return HexFormat.of().formatHex(b);}private static String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}private static String sha256(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}private static String safe(String s){return s==null?"":s;}
 private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
}
