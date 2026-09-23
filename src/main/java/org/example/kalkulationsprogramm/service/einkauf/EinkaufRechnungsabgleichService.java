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
    public record Reklamationsbezug(EinkaufBestellung bestellung, BestellungPosition position, LieferantDokument rechnung) {}

    public EinkaufRechnungsabgleichService(EinkaufBestellungRepository bestellungen, BestellungRevisionRepository revisionen,
            LieferantDokumentRepository dokumente, EinkaufBelegPositionRepository positionen,
            EinkaufLieferungRepository lieferungen, EntityManager entityManager, ObjectMapper json) {
        this.bestellungen=bestellungen; this.revisionen=revisionen; this.dokumente=dokumente; this.positionen=positionen; this.lieferungen=lieferungen; this.entityManager=entityManager; this.json=json;
    }

    public static Mengenstand mengenstand(BigDecimal vereinbart, BigDecimal kumuliert, BigDecimal teilmenge) {
        BigDecimal billed = zero(kumuliert).add(zero(teilmenge));
        return new Mengenstand(vereinbart.subtract(billed).max(BigDecimal.ZERO), billed.subtract(vereinbart));
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
        if(rechnungId!=null && (invoice==null || invoice.getTyp()!=LieferantDokumentTyp.RECHNUNG || !Objects.equals(invoice.getLieferant().getId(),lieferantId)))
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
        BestellungRevision basis=accepted.get(accepted.size()-1);
        Map<Long,EinkaufBelegZuordnung> billTypes=findByBestellungId(bestellungId).stream()
                .collect(Collectors.toMap(z->z.getDokument().getId(),Function.identity()));
        List<Long> unassigned=billTypes.keySet().stream().filter(documentId->positionen.findByDokumentIdOrderByIdAsc(documentId).stream()
                .anyMatch(p->p.isPruefen() || p.getBestellPosition()==null)).toList();
        Map<Long,BigDecimal> delivered=new HashMap<>();
        lieferungen.findByBestellung_IdOrderByEingangAsc(bestellungId).stream().flatMap(l->l.getPositionen().stream())
                .forEach(p->delivered.merge(p.getBestellPosition().getId(),p.getMenge(),BigDecimal::add));
        Map<Long,BigDecimal> confirmed=latestConfirmation(bestellungId);
        List<PositionAbgleich> rows=new ArrayList<>();
        List<BestellungPosition> acceptedLines=accepted.stream().flatMap(r->r.getPositionen().stream()).toList();
        for(BestellungPosition line:basis.getPositionen()) {
            Set<Long> relatedLineIds=acceptedLines.stream().filter(p->Objects.equals(p.getPosition(),line.getPosition()))
                    .map(BestellungPosition::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            List<EinkaufBelegPosition> bills=relatedLineIds.stream().flatMap(id->positionen.findByBestellPositionIdOrderByIdAsc(id).stream()).toList();
            BigDecimal billed=bills.stream().filter(p->!p.isPruefen()).map(p->signed(billTypes.get(p.getDokument().getId()),p.getMenge())).reduce(BigDecimal.ZERO,BigDecimal::add);
            List<Quelle> sources=bills.stream().map(p->new Quelle("BELEG",p.getDokument().getId(),p.getOriginalPositionsnummer(),p.getMenge())).toList();
            boolean review=bills.stream().anyMatch(EinkaufBelegPosition::isPruefen);
            BigDecimal amount=line.getMenge();
            Mengenstand state=mengenstand(amount,billed,BigDecimal.ZERO);
            List<Abweichung> deviations=new ArrayList<>();
            if(state.differenz().signum()!=0) deviations.add(new Abweichung(line.getId(),"menge",amount,billed,state.differenz(),"vereinbarte Menge minus kumulierte Belegmengen",sources));
            BigDecimal agreedCost=orderTotal(line);
            BigDecimal invoiceCost=bills.stream().filter(p->!p.isPruefen()).map(EinkaufRechnungsabgleichService::invoiceTotal).reduce(BigDecimal.ZERO,BigDecimal::add);
            boolean hasInvoiceCosts=bills.stream().filter(p->!p.isPruefen()).anyMatch(p->!p.getKosten().isEmpty());
            if(agreedCost!=null && hasInvoiceCosts) {
                BigDecimal costDelta=invoiceCost.subtract(agreedCost).setScale(2,RoundingMode.HALF_UP);
                if(costDelta.signum()!=0) deviations.add(new Abweichung(line.getId(),"kosten",agreedCost,invoiceCost,costDelta,
                        "vereinbarter Positionspreis einschließlich enthaltener Kosten gegen kumulierte Belegkosten",sources));
            } else if(!bills.isEmpty()) review=true;
            BigDecimal deliveredQuantity=relatedLineIds.stream().map(id->delivered.getOrDefault(id,BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal confirmedQuantity=relatedLineIds.stream().map(id->confirmed.getOrDefault(id,BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);
            rows.add(new PositionAbgleich(line.getId(),line.getPosition().bezeichnung(),line.getPosition().basis().einheit(),amount,confirmedQuantity,deliveredQuantity,billed,state.offen(),deviations,review,sources));
        }
        return new Abgleich(bestellungId,List.copyOf(rows),unassigned.stream().distinct().toList());
    }

    @Transactional
    public EinkaufBelegZuordnung zuordnen(Long dokumentId, BelegZuordnung request, Long akteurId) {
        if(request==null || request.idempotenzKey()==null || request.positionen()==null || request.positionen().isEmpty() || akteurId==null || akteurId<=0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Belegzuordnung und Benutzer sind erforderlich.");
        LieferantDokument doc=dokumente.sperreEinkaufsbeleg(dokumentId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Lieferantenbeleg nicht gefunden."));
        String hash=hash(request.toString()+"|"+akteurId+"|"+dokumentId);
        var duplicate=findByIdempotenzKey(request.idempotenzKey());
        if(duplicate.isPresent()) { if(!duplicate.get().getPayloadHash().equals(hash)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotenzschlüssel wurde mit anderen Daten verwendet."); return duplicate.get(); }
        Long orderId=doc.getEinkaufBestellungId();
        if(orderId==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Der Beleg muss zuerst einer Einkaufsbestellung zugeordnet werden.");
        EinkaufBestellung order=bestellungen.findeFuerUpdate(orderId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Bestellung nicht gefunden."));
        if(order.getVersion()==null || order.getVersion()!=request.version()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Die Bestellung wurde zwischenzeitlich geändert.");
        if(!Objects.equals(doc.getLieferant().getId(),order.getLieferantId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Beleg und Bestellung gehören zu unterschiedlichen Lieferanten.");
        if(request.art()==null || !Set.of("RECHNUNG","GUTSCHRIFT","STORNO","NACHBERECHNUNG").contains(request.art())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unbekannte Belegart.");
        if((request.art().equals("GUTSCHRIFT") || request.art().equals("STORNO")) && request.bezugsDokumentId()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Gutschrift und Storno brauchen den Bezug zur ursprünglichen Rechnung.");
        if(request.bezugsDokumentId()!=null) {
            var source=findByDokumentId(request.bezugsDokumentId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Der Bezugsbeleg ist nicht zugeordnet."));
            if(!source.getBestellungId().equals(orderId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bezugsbeleg und Korrektur gehören zu verschiedenen Bestellungen.");
        }
        var revision=revisionen.findByBestellung_IdOrderByNummerAsc(order.getId()).stream().filter(BestellungRevision::istAngenommen).max(Comparator.comparingInt(BestellungRevision::getNummer)).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Keine angenommene Bestellfassung vorhanden."));
        Map<Long,BestellungPosition> orderLines=revision.getPositionen().stream().collect(Collectors.toMap(BestellungPosition::getId,Function.identity()));
        Set<String> numbers=new HashSet<>();
        List<EinkaufBelegPosition> entities=new ArrayList<>();
        for(BelegPosition p:request.positionen()) {
            if(p.originalPositionsnummer()==null || p.originalPositionsnummer().isBlank() || !numbers.add(p.originalPositionsnummer()) || p.menge()==null || p.menge().signum()<=0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Jede Belegposition braucht eine eindeutige Nummer und eine positive Menge.");
            BestellungPosition line=p.bestellPositionId()==null?null:orderLines.get(p.bestellPositionId());
            BigDecimal normalizedQuantity=p.menge();
            Einheit normalizedUnit=p.einheit();
            boolean review=line==null || p.einheit()==null || line.getPosition().basis()==null;
            if(!review && p.einheit()!=line.getPosition().basis().einheit()) {
                var basis=line.getPosition().basis();
                var input=new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis(p.menge(),p.einheit(),basis.stueckzahl(),basis.einzelLaengeMm(),basis.kgJeMeter(),basis.faktorQuelle());
                var conversion=new EinkaufMengenUmrechnung().normalisieren(input,basis.einheit());
                if(conversion.vollstaendig()) {normalizedQuantity=conversion.menge();normalizedUnit=conversion.einheit();}
                else review=true;
            }
            List<Map<String,Object>> costs=p.kosten()==null?List.of():p.kosten().stream().map(k->Map.<String,Object>of("schluessel",Objects.toString(k.schluessel(),""),"art",Objects.toString(k.art(),""),"betrag",zero(k.betrag()),"basis",Objects.toString(k.basis(),""),"basisMenge",zero(k.basisMenge()),"enthalten",k.enthalten(),"quelle",Objects.toString(k.quelle(),""))).toList();
            List<Map<String,Object>> sources=p.quellen()==null?List.of():p.quellen().stream().map(s->Map.<String,Object>of("typ",Objects.toString(s.typ(),""),"id",Objects.toString(s.id(),""),"bezeichnung",Objects.toString(s.bezeichnung(),""),"betrag",zero(s.betrag()))).toList();
            entities.add(new EinkaufBelegPosition(doc,p.originalPositionsnummer(),line,normalizedQuantity,normalizedUnit,p.menge(),p.einheit(),null,"EUR",costs,sources,review));
        }
        positionen.saveAll(entities);
        var result=new EinkaufBelegZuordnung(doc,order.getId(),request.art(),request.bezugsDokumentId(),request.idempotenzKey(),hash,akteurId);
        entityManager.persist(result);
        return result;
    }
    private Optional<EinkaufBelegZuordnung> findByIdempotenzKey(UUID key){return entityManager.createQuery("select z from EinkaufBelegZuordnung z where z.idempotenzKey=:key",EinkaufBelegZuordnung.class).setParameter("key",key).getResultStream().findFirst();}
    private Optional<EinkaufBelegZuordnung> findByDokumentId(Long id){return entityManager.createQuery("select z from EinkaufBelegZuordnung z where z.dokument.id=:id",EinkaufBelegZuordnung.class).setParameter("id",id).getResultStream().findFirst();}
    private List<EinkaufBelegZuordnung> findByBestellungId(Long id){return entityManager.createQuery("select z from EinkaufBelegZuordnung z join fetch z.dokument where z.bestellungId=:id",EinkaufBelegZuordnung.class).setParameter("id",id).getResultList();}
    private Map<Long,BigDecimal> latestConfirmation(Long bestellungId){
        Object raw=entityManager.createNativeQuery("select positionen_snapshot from einkauf_bestellbestaetigung where bestellung_id=:id order by datum desc limit 1")
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
    private static BigDecimal orderTotal(BestellungPosition p) {
        if(p.getNettoEinzelpreis()==null) return null;
        BigDecimal total=p.getNettoEinzelpreis().multiply(p.getMenge());
        for(Map<String,Object> cost:p.getKosten()) if(Boolean.TRUE.equals(cost.get("enthalten"))) total=total.add(decimal(cost.get("betrag")));
        return total.setScale(2,RoundingMode.HALF_UP);
    }
    private static BigDecimal invoiceTotal(EinkaufBelegPosition p) {
        BigDecimal total=BigDecimal.ZERO;
        for(Map<String,Object> cost:p.getKosten()) if(Boolean.TRUE.equals(cost.get("enthalten"))) total=total.add(decimal(cost.get("betrag")));
        return total.setScale(2,RoundingMode.HALF_UP);
    }
    private static BigDecimal decimal(Object value){if(value instanceof BigDecimal b)return b;if(value instanceof Number n)return new BigDecimal(n.toString());return BigDecimal.ZERO;}
    private static BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
