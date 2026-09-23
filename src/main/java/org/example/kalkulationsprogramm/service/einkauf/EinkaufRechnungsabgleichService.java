package org.example.kalkulationsprogramm.service.einkauf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufRechnungsabgleichDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EinkaufRechnungsabgleichService {
    private final EinkaufBestellungRepository bestellungen;
    private final BestellungRevisionRepository revisionen;
    private final LieferantDokumentRepository dokumente;
    private final EinkaufBelegPositionRepository positionen;
    private final EinkaufLieferungRepository lieferungen;
    private final EntityManager entityManager;
    private final ObjectMapper json;
    private final EinkaufMengenService mengen;
    public record Reklamationsbezug(EinkaufBestellung bestellung, BestellungPosition position, LieferantDokument rechnung) {}

    public EinkaufRechnungsabgleichService(EinkaufBestellungRepository bestellungen, BestellungRevisionRepository revisionen,
            LieferantDokumentRepository dokumente, EinkaufBelegPositionRepository positionen,
            EinkaufLieferungRepository lieferungen, EntityManager entityManager, ObjectMapper json,EinkaufMengenService mengen) {
        this.bestellungen=bestellungen; this.revisionen=revisionen; this.dokumente=dokumente; this.positionen=positionen; this.lieferungen=lieferungen; this.entityManager=entityManager; this.json=json; this.mengen=mengen;
    }

    public static Mengenstand mengenstand(BigDecimal vereinbart, BigDecimal kumuliert, BigDecimal teilmenge) {
        BigDecimal billed = zero(kumuliert).add(zero(teilmenge));
        return new Mengenstand(vereinbart.subtract(billed).max(BigDecimal.ZERO), billed.signum()<0?billed:billed.subtract(vereinbart).max(BigDecimal.ZERO));
    }

    @Transactional(readOnly=true)
    public Reklamationsbezug pruefeReklamationsbezug(Long lieferantId, Long bestellungId, Long positionId, Long rechnungId) {
        EinkaufBestellung order=bestellungId==null?null:bestellungen.findById(bestellungId).orElse(null);
        if(bestellungId!=null && (order==null || !Objects.equals(order.getLieferantId(),lieferantId))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bestellung und Lieferant passen nicht zusammen.");
        BestellungPosition line=null;
        if(positionId!=null) {
            if(order==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Eine Bestellposition braucht eine zugehörige Bestellung.");
            line=revisionen.findByBestellung_IdOrderByNummerAsc(order.getId()).stream().flatMap(r->r.getPositionen().stream()).filter(p->Objects.equals(p.getId(),positionId)).findFirst().orElse(null);
            if(line==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Die Bestellposition gehört nicht zur angegebenen Bestellung.");
        }
        LieferantDokument invoice=rechnungId==null?null:dokumente.findById(rechnungId).orElse(null);
        if(rechnungId!=null && (invoice==null || invoice.getTyp()!=LieferantDokumentTyp.RECHNUNG || invoice.getLieferant()==null || !Objects.equals(invoice.getLieferant().getId(),lieferantId)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Die Rechnung gehört nicht zum angegebenen Lieferanten.");
        if(invoice!=null && order!=null && !Objects.equals(invoice.getEinkaufBestellungId(),order.getId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Rechnung und Bestellung gehören nicht zusammen.");
        return new Reklamationsbezug(order,line,invoice);
    }

    @Transactional(readOnly=true)
    public Abgleich vergleichen(Long bestellungId) {
        EinkaufBestellung bestellung=bestellungen.findById(bestellungId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Bestellung nicht gefunden."));
        List<BestellungRevision> accepted=revisionen.findByBestellung_IdOrderByNummerAsc(bestellungId).stream().filter(BestellungRevision::istAngenommen).toList();
        if(accepted.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Für diese Bestellung gibt es noch keine angenommene Fassung.");
        var activeAmounts=mengen.standFuerVorgang("BESTELLUNG:"+bestellungId);
        Map<Long,EinkaufBelegZuordnung> billTypes=findByBestellungId(bestellungId).stream()
                .collect(Collectors.toMap(z->z.getDokument().getId(),Function.identity()));
        List<EinkaufBelegPosition> allBills=billTypes.isEmpty()?List.of():positionen.findeAlleBelege(billTypes.keySet());
        List<Long> unassigned=allBills.stream().filter(p->p.isPruefen()||p.getBestellPosition()==null)
                .map(p->p.getDokument().getId()).distinct().toList();
        var headerCosts=headerCosts(bestellung);
        Set<String> headerAllowances=new HashSet<>();
        Map<Long,BigDecimal> delivered=new HashMap<>();
        lieferungen.findByBestellung_IdOrderByEingangAsc(bestellungId).stream().flatMap(l->l.getPositionen().stream())
                .forEach(p->delivered.merge(p.getBestellPosition().getId(),p.getMenge(),BigDecimal::add));
        Map<Long,BigDecimal> confirmed=latestConfirmation(bestellungId);
        List<PositionAbgleich> rows=new ArrayList<>();
        List<BestellungPosition> acceptedLines=accepted.stream().flatMap(r->r.getPositionen().stream()).toList();
        Map<String,BestellungPosition> currentLines=new LinkedHashMap<>();
        acceptedLines.forEach(p->currentLines.put(EinkaufBelegKostenRechner.lineIdentity(p),p));
        for(BestellungPosition line:currentLines.values()) {
            Set<Long> relatedLineIds=acceptedLines.stream().filter(p->Objects.equals(EinkaufBelegKostenRechner.lineIdentity(p),EinkaufBelegKostenRechner.lineIdentity(line)))
                    .map(BestellungPosition::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            List<EinkaufBelegPosition> bills=allBills.stream().filter(p->p.getBestellPosition()!=null&&relatedLineIds.contains(p.getBestellPosition().getId())).toList();
            BigDecimal billed=bills.stream().filter(p->!p.isPruefen()&&!p.isNurPreisKorrektur()).map(p->signed(billTypes.get(p.getDokument().getId()),p.getMenge())).reduce(BigDecimal.ZERO,BigDecimal::add);
            List<Quelle> sources=bills.stream().map(p->new Quelle("BELEG",p.getDokument().getId(),p.getOriginalPositionsnummer(),p.getMenge())).toList();
            boolean review=bills.stream().anyMatch(EinkaufBelegPosition::isPruefen);
            var originIds=line.getHerkuenfte().stream().map(BestellungHerkunft::getBedarfId).distinct().toList();
            BigDecimal amount=originIds.isEmpty()?line.getMenge():originIds.stream()
                    .map(id->activeAmounts.getOrDefault(id,EinkaufMengenService.Vorgangsmenge.leer()).bestellt()).reduce(BigDecimal.ZERO,BigDecimal::add);
            Mengenstand state=mengenstand(amount,billed,BigDecimal.ZERO);
            List<Abweichung> deviations=new ArrayList<>();
            if(state.differenz().signum()!=0) deviations.add(new Abweichung(line.getId(),"menge",amount,billed,state.differenz(),"Überabrechnung gegenüber vereinbarter Menge oder negative Korrekturmenge",sources));
            var costs=EinkaufBelegKostenRechner.berechne(bills,billTypes,headerCosts,headerAllowances);
            review|=!costs.vollstaendig();
            if(costs.vollstaendig()) {
                BigDecimal costDelta=costs.abgerechnet().subtract(costs.vereinbart());
                if(costDelta.signum()!=0)deviations.add(new Abweichung(line.getId(),"kosten",costs.vereinbart(),costs.abgerechnet(),costDelta,
                    "Kumulierte belegte Teilmengen × vereinbarter Einzelpreis, mit einmaligen Zuschlägen und signierten Korrekturen",sources));
            }
            BigDecimal deliveredQuantity=relatedLineIds.stream().map(id->delivered.getOrDefault(id,BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal confirmedQuantity=relatedLineIds.stream().map(id->confirmed.getOrDefault(id,BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);
            rows.add(new PositionAbgleich(line.getId(),line.getPosition().bezeichnung(),line.getPosition().basis().einheit(),amount,confirmedQuantity,deliveredQuantity,billed,state.offen(),deviations,review,sources));
        }
        return new Abgleich(bestellungId,List.copyOf(rows),unassigned.stream().distinct().toList());
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public EinkaufBelegZuordnung zuordnen(Long dokumentId, BelegZuordnung request, Long akteurId) {
        if(dokumentId==null||dokumentId<=0||request==null||request.bestellungId()==null||request.bestellungId()<=0
                ||request.idempotenzKey()==null||request.positionen()==null||request.positionen().isEmpty()
                ||request.positionen().size()>500||akteurId==null||akteurId<=0)
            throw bad("Bestellung, Belegpositionen und Benutzer sind erforderlich.");
        var order=bestellungen.findeFuerUpdate(request.bestellungId()).orElseThrow(()->missing("Bestellung nicht gefunden."));
        var doc=dokumente.sperreEinkaufsbeleg(dokumentId).orElseThrow(()->missing("Lieferantenbeleg nicht gefunden."));
        String hash=hash(request.toString()+"|"+akteurId+"|"+dokumentId);
        var duplicate=findByIdempotenzKey(request.idempotenzKey());
        if(duplicate.isPresent()) {
            if(!duplicate.get().getPayloadHash().equals(hash))throw conflict("Idempotenzschlüssel wurde mit anderen Daten verwendet.");
            return duplicate.get();
        }
        if(findByDokumentId(dokumentId).isPresent())throw conflict("Dieser Beleg wurde bereits erfasst.");
        if(doc.getEinkaufBestellungId()!=null&&!Objects.equals(doc.getEinkaufBestellungId(),order.getId()))
            throw conflict("Der Beleg gehört bereits zu einer anderen Bestellung.");
        if(order.getVersion()==null||order.getVersion()!=request.version())throw conflict("Die Bestellung wurde zwischenzeitlich geändert.");
        if(doc.getLieferant()==null||!Objects.equals(doc.getLieferant().getId(),order.getLieferantId()))throw bad("Beleg und Bestellung gehören zu unterschiedlichen Lieferanten.");
        LieferantDokumentTyp expected=switch(Objects.toString(request.art(),"")) {
            case "RECHNUNG","NACHBERECHNUNG" -> LieferantDokumentTyp.RECHNUNG;
            case "GUTSCHRIFT","STORNO" -> LieferantDokumentTyp.GUTSCHRIFT;
            default -> throw bad("Unbekannte Belegart.");
        };
        if(doc.getTyp()!=expected)throw bad("Belegart und gespeicherter Dokumenttyp passen nicht zusammen.");
        boolean correction=!"RECHNUNG".equals(request.art());
        if(correction&&request.bezugsDokumentId()==null)throw bad("Eine Korrektur braucht den Bezug zur ursprünglichen Rechnung.");
        if(!correction&&request.bezugsDokumentId()!=null)throw bad("Eine normale Rechnung braucht keinen Korrekturbezug.");
        List<EinkaufBelegPosition> sourceLines=List.of();
        if(correction) {
            var source=findByDokumentId(request.bezugsDokumentId()).orElseThrow(()->bad("Der Bezugsbeleg ist nicht zugeordnet."));
            if(!source.getBestellungId().equals(order.getId())||!Set.of("RECHNUNG","NACHBERECHNUNG").contains(source.getArt()))
                throw bad("Der Bezug muss eine Rechnung derselben Bestellung sein.");
            sourceLines=positionen.findByDokumentIdOrderByIdAsc(request.bezugsDokumentId());
        }
        var accepted=revisionen.findByBestellung_IdOrderByNummerAsc(order.getId()).stream().filter(BestellungRevision::istAngenommen).toList();
        if(accepted.isEmpty())throw conflict("Keine angenommene Bestellfassung vorhanden.");
        Map<Long,BestellungPosition> orderLines=accepted.stream().flatMap(r->r.getPositionen().stream()).collect(Collectors.toMap(BestellungPosition::getId,Function.identity()));
        Set<String> numbers=new HashSet<>();List<EinkaufBelegPosition> entities=new ArrayList<>();
        for(BelegPosition p:request.positionen()) {
            if(p==null||p.originalPositionsnummer()==null||p.originalPositionsnummer().isBlank()||p.originalPositionsnummer().length()>100
                    ||!numbers.add(p.originalPositionsnummer())||p.menge()==null||p.menge().signum()<=0)
                throw bad("Jede Belegposition braucht eine eindeutige Nummer und eine positive Menge.");
            if(p.nurPreisKorrektur()&&!correction)throw bad("Eine reine Preisänderung braucht einen Korrekturbeleg.");
            var line=p.bestellPositionId()==null?null:orderLines.get(p.bestellPositionId());
            if(p.bestellPositionId()!=null&&line==null)throw bad("Die Position gehört zu keiner angenommenen Fassung dieser Bestellung.");
            if(correction&&(line==null||sourceLines.stream().noneMatch(l->l.getBestellPosition()!=null&&Objects.equals(l.getBestellPosition().getId(),line.getId()))))
                throw bad("Die korrigierte Position fehlt auf der Bezugsrechnung.");
            boolean review=line==null||p.einheit()==null||line.getPosition().basis()==null||p.nettoEinzelpreis()==null||p.preisBasisMenge()==null;
            if(p.nettoEinzelpreis()!=null&&p.nettoEinzelpreis().signum()<0||p.preisBasisMenge()!=null&&p.preisBasisMenge().signum()<=0)
                throw bad("Einzelpreis darf nicht negativ sein; die Preisbasismenge muss positiv sein.");
            BigDecimal quantity=p.menge();Einheit unit=p.einheit();
            if(line!=null&&line.getPosition().basis()!=null&&p.einheit()!=null&&p.einheit()!=line.getPosition().basis().einheit()) {
                var conversion=new EinkaufMengenUmrechnung().normalisieren(EinkaufBelegKostenRechner.basis(line,p.menge(),p.einheit()),line.getPosition().basis().einheit());
                if(conversion.vollstaendig()){quantity=conversion.menge();unit=conversion.einheit();}else review=true;
            }
            BigDecimal price=review?null:p.nettoEinzelpreis().multiply(p.menge()).divide(p.preisBasisMenge().multiply(quantity),6,RoundingMode.HALF_UP);
            List<Map<String,Object>> costs=new ArrayList<>();Set<String> keys=new HashSet<>();
            if(p.kosten()!=null)for(var cost:p.kosten()) {
                if(cost==null||cost.schluessel()==null||cost.schluessel().isBlank()||!keys.add(cost.schluessel())||"material".equals(cost.schluessel())||"MATERIAL".equals(cost.art()))
                    throw bad("Nebenkosten brauchen eindeutige Schlüssel; Material wird über den Einzelpreis erfasst.");
                var snapshot=new LinkedHashMap<String,Object>();
                snapshot.put("schluessel",cost.schluessel());snapshot.put("art",cost.art());snapshot.put("betrag",cost.betrag());
                snapshot.put("basis",cost.basis());snapshot.put("basisMenge",cost.basisMenge());snapshot.put("enthalten",cost.enthalten());
                snapshot.put("quelle",cost.quelle());snapshot.put("prozentBasisSchluessel",cost.prozentBasisSchluessel());costs.add(snapshot);
            }
            if(costs.stream().anyMatch(c->c.get("betrag")==null||c.get("basis")==null||c.get("basisMenge")==null))review=true;
            if(costs.stream().anyMatch(c->c.get("betrag")!=null&&decimal(c.get("betrag")).signum()<0))throw bad("Nebenkostenbeträge dürfen nicht negativ sein; Rabatte separat erfassen.");
            var sources=new ArrayList<Map<String,Object>>();
            sources.add(Map.of("typ","BELEG","id",dokumentId,"bezeichnung",p.originalPositionsnummer()));
            if(p.quellen()!=null)p.quellen().forEach(q->sources.add(json.convertValue(q,new TypeReference<Map<String,Object>>(){})));
            var entity=new EinkaufBelegPosition(doc,p.originalPositionsnummer(),line,quantity,unit,p.menge(),p.einheit(),price,"EUR",costs,sources,review);
            entity.preisBasis(p.nettoEinzelpreis(),p.preisBasisMenge(),p.nurPreisKorrektur());entities.add(entity);
        }
        positionen.saveAll(entities);
        var result=new EinkaufBelegZuordnung(doc,order.getId(),request.art(),request.bezugsDokumentId(),request.idempotenzKey(),hash,akteurId);
        entityManager.persist(result);doc.setEinkaufBestellungId(order.getId());
        entityManager.lock(order,jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return result;
    }
    private List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Kosten> headerCosts(EinkaufBestellung order) {
        if(order.getAngebotsversionId()==null)return List.of();
        var offer=entityManager.find(AngebotVersion.class,order.getAngebotsversionId());
        if(offer==null)return List.of();
        return offer.getKosten().stream().filter(c->c.getPositionId()==null).map(c->new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.Kosten(
            c.getSchluessel(),c.getArt(),c.getBetrag(),c.getBasis(),c.getBasisMenge(),c.getProzentBasisSchluessel(),c.isEnthalten(),c.isVariabel(),c.getQuelle())).toList();
    }
    private static ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    private static ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
    private static ResponseStatusException missing(String text){return new ResponseStatusException(HttpStatus.NOT_FOUND,text);}
    private Optional<EinkaufBelegZuordnung> findByIdempotenzKey(UUID key){return entityManager.createQuery("select z from EinkaufBelegZuordnung z where z.idempotenzKey=:key",EinkaufBelegZuordnung.class).setParameter("key",key).getResultStream().findFirst();}
    private Optional<EinkaufBelegZuordnung> findByDokumentId(Long id){return entityManager.createQuery("select z from EinkaufBelegZuordnung z where z.dokument.id=:id",EinkaufBelegZuordnung.class).setParameter("id",id).getResultStream().findFirst();}
    private List<EinkaufBelegZuordnung> findByBestellungId(Long id){return entityManager.createQuery("select z from EinkaufBelegZuordnung z join fetch z.dokument where z.bestellungId=:id",EinkaufBelegZuordnung.class).setParameter("id",id).getResultList();}
    private Map<Long,BigDecimal> latestConfirmation(Long bestellungId){
        Object raw=entityManager.createNativeQuery("select positionen_snapshot from einkauf_bestellbestaetigung where bestellung_id=:id order by datum desc, id desc")
                .setParameter("id",bestellungId).setMaxResults(1).getResultStream().findFirst().orElse(null);
        if(raw==null)return Map.of();
        try {
        List<Map<String,Object>> rows=raw instanceof byte[] bytes?json.readValue(bytes,new TypeReference<>(){}):json.readValue(raw.toString(),new TypeReference<>(){});
        Map<Long,BigDecimal> result=new HashMap<>();
        for(Map<String,Object> values:rows) if(values.get("bestellPositionId") instanceof Number id)
            result.put(id.longValue(),decimal(values.get("menge")));
        return Map.copyOf(result);
        } catch(java.io.IOException e) { throw new IllegalStateException("Die gespeicherte Auftragsbestätigung ist beschädigt.",e); }
    }
    static BigDecimal signed(EinkaufBelegZuordnung zuordnung,BigDecimal amount){return signed(zuordnung==null?null:zuordnung.getArt(),amount);}
    static BigDecimal signed(String art,BigDecimal amount){if(amount==null)return BigDecimal.ZERO;return "GUTSCHRIFT".equals(art) || "STORNO".equals(art)?amount.negate():amount;}
    private static BigDecimal decimal(Object value){if(value instanceof BigDecimal b)return b;if(value instanceof Number n)return new BigDecimal(n.toString());return BigDecimal.ZERO;}
    private static BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
