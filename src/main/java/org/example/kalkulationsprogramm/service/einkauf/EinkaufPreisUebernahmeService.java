package org.example.kalkulationsprogramm.service.einkauf;

import java.math.BigDecimal; import java.math.RoundingMode; import java.time.LocalDate; import java.util.*;
import org.example.kalkulationsprogramm.domain.*; import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Kosten;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPreisUebernahmeDto.*;
import org.example.kalkulationsprogramm.repository.*; import org.example.kalkulationsprogramm.service.LieferantArtikelpreisService;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.http.HttpStatus; import org.springframework.web.server.ResponseStatusException;

@Service
public class EinkaufPreisUebernahmeService {
 private static final Set<String> EINMALIG=Set.of("FRACHT","BEARBEITUNG","VERPACKUNG","ZEUGNIS","ZUSCHNITT","MINDERMENGE");
 private final AngebotVersionRepository versionen; private final LieferantArtikelpreisService preishistorie; private final LieferantenArtikelPreiseRepository preise; private final EinkaufAuditService audit; private final com.fasterxml.jackson.databind.ObjectMapper json;
 public EinkaufPreisUebernahmeService(AngebotVersionRepository versionen,LieferantArtikelpreisService preishistorie,LieferantenArtikelPreiseRepository preise,EinkaufAuditService audit,com.fasterxml.jackson.databind.ObjectMapper json){this.versionen=versionen;this.preishistorie=preishistorie;this.preise=preise;this.audit=audit;this.json=json;}
 public static BigDecimal normalisiereEinzelpreis(BigDecimal preis,String basis){if(preis==null||preis.signum()<=0||basis==null)throw bad("Preis und Preisbasis müssen vollständig sein.");var parsed=EinkaufMengenUmrechnung.parsePreisBasis(basis);if(!parsed.vollstaendig()||parsed.menge().signum()<=0)throw bad("Die Preisbasis kann nicht eindeutig umgerechnet werden.");return preis.divide(parsed.menge(),4,RoundingMode.HALF_UP);}

 @Transactional public org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisDto uebernehmen(Long angebotVersionId,Long angebotPositionId,PreisUebernahme request,Long actor){
  if(angebotVersionId==null||angebotVersionId<=0||angebotPositionId==null||angebotPositionId<=0||request==null||actor==null||actor<=0||request.idempotenzKey()==null)throw bad("Angebot, Position, Benutzer und Idempotenzschlüssel sind erforderlich.");
  AngebotVersion version=versionen.findById(angebotVersionId).orElseThrow(()->new NoSuchElementException("Angebotsfassung nicht gefunden."));
  if(!"GEPRUEFT".equals(version.getStatus())||version.getBestaetigtVon()==null)throw conflict("Nur ein menschlich geprüftes Angebot darf übernommen werden.");
  AngebotPosition offered=version.getPositionen().stream().filter(p->Objects.equals(p.getId(),angebotPositionId)).findFirst().orElseThrow(()->new NoSuchElementException("Angebotsposition gehört nicht zu dieser Fassung."));
  if(offered.getAbweichungen()!=null&&!offered.getAbweichungen().isEmpty()&&version.getAbweichungBestaetigtVon()==null)throw conflict("Die technische Abweichung braucht eine eigene ausdrückliche Freigabe.");
  var requestPosition=offered.getAnfragePosition(); var snapshot=requestPosition.getSnapshot();
  if(snapshot==null||snapshot.art()!=Positionsart.ARTIKEL||snapshot.artikelId()==null||snapshot.basis()==null||snapshot.basis().einheit()==null)throw conflict("Für diese Position fehlt eine eindeutige Artikelidentität oder Mengenbasis.");
  if(!"EUR".equals(version.getWaehrung()))throw conflict("Nur bestätigte EUR-Preise können in die bestehende Preishistorie übernommen werden.");
  PreisScope scope;try{scope=PreisScope.valueOf(request.scope()==null?"STANDARD":request.scope());}catch(IllegalArgumentException ex){throw bad("Der Preis-Scope ist ungültig.");}
  if(scope==PreisScope.PROJEKT&&(request.projektId()==null||request.projektId()<=0)||scope==PreisScope.STANDARD&&request.projektId()!=null)throw bad("Projekt und Preis-Scope passen nicht zusammen.");
  if(scope==PreisScope.MENGENSTAFFEL&&(request.abMenge()==null||request.abMenge().signum()<=0)||request.abMenge()!=null&&request.bisMenge()!=null&&request.abMenge().compareTo(request.bisMenge())>=0)throw bad("Die Mengenstaffel ist ungültig.");
  List<Kosten> costLines=version.getKosten().stream().filter(c->Objects.equals(c.getPositionId(),offered.getId())).map(c->new Kosten(c.getSchluessel(),c.getArt(),c.getBetrag(),c.getBasis(),c.getBasisMenge(),c.getProzentBasisSchluessel(),c.isEnthalten(),c.isVariabel(),c.getQuelle())).filter(c->!EINMALIG.contains(c.art())).toList();
  if(costLines.isEmpty())throw conflict("Das Angebot enthält keinen wiederkehrenden Materialpreis.");
  var calculation=EinkaufVergleichService.berechneKosten(costLines,snapshot.basis());
  if(!calculation.vollstaendig()||calculation.nettoGesamt()==null||calculation.nettoGesamt().signum()<=0)throw conflict("Die wiederkehrenden Materialkosten sind nicht vollständig oder nicht eindeutig.");
  BigDecimal quantity=snapshot.basis().menge(); if(quantity==null||quantity.signum()<=0)throw conflict("Die Angebotsmenge muss positiv sein.");
  BigDecimal unitPrice=calculation.nettoGesamt().divide(quantity,4,RoundingMode.HALF_UP);
  var accepted=version.getDatum()==null?LocalDate.now():version.getDatum();
  String komponentenHash;try{komponentenHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(costLines)));}catch(Exception e){throw new IllegalStateException("Kostenbestandteile konnten nicht geprüft werden.",e);}
  String notiz="Angebotsfassung "+version.getId()+", Position "+angebotPositionId+(request.begruendung()==null?"":"; "+request.begruendung());
  var dto=preishistorie.schreibePreisstand(version.getAngebot().getBeteiligung().getKontakt().lieferantId(),snapshot.artikelId(),unitPrice,null,PreisQuelle.ANGEBOT_EMAIL,notiz,scope,request.projektId(),request.abMenge(),request.bisMenge(),accepted,version.getGueltigBis(),snapshot.basis().einheit().name(),BigDecimal.ONE,version.getId(),offered.getId(),request.idempotenzKey(),komponentenHash).orElseThrow(()->new NoSuchElementException("Artikel oder Lieferant nicht gefunden."));
  audit.protokolliere("ARTIKEL_PREIS",snapshot.artikelId(),"EINKAUFSPREIS_UEBERNOMMEN",actor,null,json.valueToTree(dto),request.begruendung());return dto;
 }
 @Transactional(readOnly=true) public Optional<Preisvorschlag> letzterBestaetigterPreis(Long artikelId,Long lieferantId,Long projektId,BigDecimal menge,LocalDate datum){
  if(artikelId==null||lieferantId==null||datum==null)return Optional.empty();
  return preise.findAllByArtikel_IdAndLieferant_IdAndAktuellTrue(artikelId,lieferantId).stream()
   .filter(p->p.getGueltigAb()==null||!p.getGueltigAb().isAfter(datum))
   .filter(p->p.getScope()==PreisScope.STANDARD
       ||p.getScope()==PreisScope.PROJEKT&&Objects.equals(p.getProjektId(),projektId)
       ||p.getScope()==PreisScope.MENGENSTAFFEL&&menge!=null&&p.getAbMenge()!=null&&menge.compareTo(p.getAbMenge())>=0&&(p.getBisMenge()==null||menge.compareTo(p.getBisMenge())<0))
   .max(Comparator.comparing((LieferantenArtikelPreise p)->p.getGueltigBis()==null||!p.getGueltigBis().isBefore(datum))
       .thenComparingInt(p->p.getScope()==PreisScope.PROJEKT?3:p.getScope()==PreisScope.MENGENSTAFFEL?2:1)
       .thenComparing(p->p.getGueltigAb(),Comparator.nullsFirst(Comparator.naturalOrder()))
       .thenComparing(LieferantenArtikelPreise::getId,Comparator.nullsFirst(Comparator.naturalOrder())))
   .map(p->new Preisvorschlag(artikelId,lieferantId,p.getPreis(),p.getWaehrung(),p.getEinheit(),p.getGueltigAb(),p.getGueltigBis(),p.getScope(),p.getGueltigBis()!=null&&p.getGueltigBis().isBefore(datum)?"Der Preis ist abgelaufen und dient nur als Hinweis.":null));
 }
 private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
}
