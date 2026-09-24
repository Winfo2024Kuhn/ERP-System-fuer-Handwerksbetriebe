package org.example.kalkulationsprogramm.service.einkauf;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufFaelligkeitDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.VorlagenKontext;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufZeugnisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class EinkaufFaelligkeitService {
    // Shared by the overview and its preview, including charges delivered after a completed review.
    private static final String OFFENE_CHARGEN_SQL = """
        SELECT c.id FROM einkauf_charge c
        JOIN einkauf_lieferung_position lp ON lp.id=c.lieferung_position_id
        WHERE lp.bestell_position_id=z.bestell_position_id
          AND NOT EXISTS (SELECT 1 FROM einkauf_zeugnis_charge_status cs
                          WHERE cs.zeugnis_id=z.id AND cs.charge_id=c.id
                            AND cs.status='GEPRUEFT' AND cs.material_freigegeben=TRUE)
        """;
    private static final String OFFENES_ZEUGNIS_SQL = "(z.status<>'GEPRUEFT' OR EXISTS ("+OFFENE_CHARGEN_SQL+"))";
    private static final String VORGANGS_SQL = """
        SELECT 'ANFRAGE_ANTWORTFRIST' typ, a.id vorgangId, a.pa_nummer nummer, l.id beteiligungId,
               r.antwortfrist frist, a.zustaendig_id zustaendigId,
               IF(r.antwortfrist IS NULL,'Termin klären','Antwortfrist überschritten') hinweis
          FROM einkaufsanfrage a JOIN einkaufsanfrage_revision r ON r.id=a.aktuelle_revision_id
          JOIN einkaufsanfrage_lieferant l ON l.revision_id=r.id
         WHERE a.geloescht_am IS NULL AND l.status='VERSENDET' AND l.versand_annahmeereignis IS NOT NULL
           AND l.antwort_am IS NULL AND (r.antwortfrist IS NULL OR r.antwortfrist<=:heute)
           AND (:zustaendig IS NULL OR a.zustaendig_id=:zustaendig)
        UNION ALL
        SELECT 'BESTELLBESTAETIGUNG' typ,b.id vorgangId,b.nummer,NULL beteiligungId,
               STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(r.snapshot,'$.bestaetigungsfrist')),'%Y-%m-%d') frist,
               b.angelegt_von zustaendigId,
               IF(JSON_EXTRACT(r.snapshot,'$.bestaetigungsfrist') IS NULL,'Termin klären','Auftragsbestätigung fehlt') hinweis
          FROM einkauf_bestellung b JOIN einkauf_bestellung_revision r ON r.bestellung_id=b.id
           AND r.angenommen_am IS NOT NULL AND r.verworfen_am IS NULL
           AND r.nummer=(SELECT MAX(r2.nummer) FROM einkauf_bestellung_revision r2 WHERE r2.bestellung_id=b.id AND r2.angenommen_am IS NOT NULL AND r2.verworfen_am IS NULL)
         WHERE b.status IN ('BESTELLT','TEILGELIEFERT')
           AND NOT EXISTS (SELECT 1 FROM einkauf_bestellbestaetigung bb WHERE bb.bestellung_id=b.id)
           AND (JSON_EXTRACT(r.snapshot,'$.bestaetigungsfrist') IS NULL OR STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(r.snapshot,'$.bestaetigungsfrist')),'%Y-%m-%d')<=:heute)
           AND (:zustaendig IS NULL OR b.angelegt_von=:zustaendig)
        UNION ALL
        SELECT 'LIEFERTERMIN' typ,b.id vorgangId,b.nummer,NULL beteiligungId,bb.liefertermin frist,
               b.angelegt_von zustaendigId,IF(bb.liefertermin IS NULL,'Termin klären','Bestätigter Liefertermin überschritten') hinweis
          FROM einkauf_bestellung b JOIN einkauf_bestellbestaetigung bb ON bb.bestellung_id=b.id
         WHERE b.status IN ('BESTELLT','TEILGELIEFERT')
           AND bb.id=(SELECT MAX(bb2.id) FROM einkauf_bestellbestaetigung bb2 WHERE bb2.bestellung_id=b.id)
           AND (bb.liefertermin IS NULL OR bb.liefertermin<=:heute)
           AND (:zustaendig IS NULL OR b.angelegt_von=:zustaendig)
        UNION ALL
        SELECT 'ZEUGNIS' typ,b.id vorgangId,CONCAT(b.nummer,' / ',z.art) nummer,NULL beteiligungId,z.frist frist,
               b.angelegt_von zustaendigId,CONCAT('Fehlt: ',z.art,' — ',z.grundlage) hinweis
         FROM einkauf_zeugnis_erwartung z JOIN einkauf_bestellung_revision r ON r.id=z.revision_id
          JOIN einkauf_bestellung b ON b.id=r.bestellung_id
         WHERE r.angenommen_am IS NOT NULL AND r.verworfen_am IS NULL
           AND r.nummer=(SELECT MAX(r2.nummer) FROM einkauf_bestellung_revision r2 WHERE r2.bestellung_id=b.id AND r2.angenommen_am IS NOT NULL AND r2.verworfen_am IS NULL)
           AND __OFFENES_ZEUGNIS__
           AND (z.frist IS NULL OR z.frist<=:heute) AND b.status IN ('BESTELLT','TEILGELIEFERT','GELIEFERT')
           AND (:zustaendig IS NULL OR b.angelegt_von=:zustaendig)
        """.replace("__OFFENES_ZEUGNIS__",OFFENES_ZEUGNIS_SQL);
    private final EntityManager em;
    private final EinkaufVorlagenService templates;
    private final Clock clock;

    @Autowired
    public EinkaufFaelligkeitService(EntityManager em,EinkaufVorlagenService templates){this(em,templates,Clock.systemDefaultZone());}
    public EinkaufFaelligkeitService(EntityManager em,EinkaufVorlagenService templates,Clock clock){this.em=em;this.templates=templates;this.clock=clock;}

    @Transactional(readOnly=true)
    public Page<Faelligkeit> liste(LocalDate heute,Long zustaendigId,Pageable pageable){
        if(heute==null||pageable==null||pageable.getPageSize()<1||pageable.getPageSize()>200||pageable.getOffset()>Integer.MAX_VALUE
                ||zustaendigId!=null&&zustaendigId<=0)throw bad("Datum, Zuständigkeit oder Seitengröße ist ungültig.");
        Query query=em.createNativeQuery("SELECT * FROM ("+VORGANGS_SQL+") faellig ORDER BY frist IS NULL DESC, frist ASC, typ ASC, vorgangId ASC LIMIT :limit OFFSET :offset");
        bind(query,heute,zustaendigId);query.setParameter("limit",pageable.getPageSize()).setParameter("offset",pageable.getOffset());
        @SuppressWarnings("unchecked") List<Object[]> rows=query.getResultList();
        List<Faelligkeit> content=rows.stream().map(this::row).filter(item->faellig(item.frist(),heute)).toList();
        Query count=em.createNativeQuery("SELECT COUNT(*) FROM ("+VORGANGS_SQL+") faellig");bind(count,heute,zustaendigId);
        long total=((Number)count.getSingleResult()).longValue();
        return new PageImpl<>(content,pageable,total);
    }

    @Transactional(readOnly=true)
    public NachfrageEntwurf nachfrage(String typ,Long vorgangId,Long beteiligungId){
        if(typ==null||vorgangId==null||vorgangId<=0||beteiligungId!=null&&beteiligungId<=0)throw bad("Nachfrageart und Vorgang sind ungültig.");
        if("ANFRAGE_ANTWORTFRIST".equals(typ))return anfrageNachfrage(vorgangId,beteiligungId);
        if(!Set.of("BESTELLBESTAETIGUNG","LIEFERTERMIN","ZEUGNIS").contains(typ)||beteiligungId!=null)throw bad("Diese Nachfrageart passt nicht zum angegebenen Vorgang.");
        EinkaufBestellung order=em.find(EinkaufBestellung.class,vorgangId);
        if(order==null)throw new NoSuchElementException("Die Bestellung wurde nicht gefunden.");
        if(order.getStatus()==BestellungStatus.STORNIERT||order.getStatus()==BestellungStatus.GELIEFERT&&!"ZEUGNIS".equals(typ))throw conflict("Für erledigte oder stornierte Bestellungen wird keine Nachfrage vorbereitet.");
        List<BestellungRevision> accepted=em.createQuery("select r from BestellungRevision r where r.bestellung.id=:id and r.angenommenAm is not null and r.verworfenAm is null order by r.nummer desc",BestellungRevision.class).setParameter("id",vorgangId).setMaxResults(1).getResultList();
        if(accepted.isEmpty())throw conflict("Es gibt keine angenommene Bestellfassung.");
        BestellungRevision revision=accepted.get(0);
        EmailTextTemplate template=findTemplate(templateType(typ));
        var contact=order.getEmpfaenger();
        if(contact==null||contact.email()==null||contact.email().isBlank())throw conflict("In der Bestellfassung fehlt eine gültige Lieferantenadresse.");
        Map<String,String> values=new HashMap<>();put(values,"LIEFERANTENNAME",contact.lieferantenname());put(values,"ANSPRECHPARTNER",contact.name());put(values,"ANREDE",contact.anrede());
        put(values,"EIGENE_KUNDENNUMMER_BEIM_LIEFERANTEN",contact.eigeneKundennummer());values.put("BESTELLNUMMER",order.getNummer());
        List<PositionSnapshot> snapshots=revision.getPositionen().stream().map(BestellungPosition::getPosition).filter(Objects::nonNull).toList();
        List<String> missing=List.of();
        if("ZEUGNIS".equals(typ)){
            @SuppressWarnings("unchecked")
            List<Number> openIds=em.createNativeQuery("SELECT z.id FROM einkauf_zeugnis_erwartung z WHERE z.revision_id=:id AND (z.frist IS NULL OR z.frist<=:heute) AND "+OFFENES_ZEUGNIS_SQL+" ORDER BY z.id")
                    .setParameter("id",revision.getId()).setParameter("heute",LocalDate.now(clock)).getResultList();
            List<EinkaufZeugnisErwartung> expected=openIds.isEmpty()?List.of():em.createQuery(
                    "select z from EinkaufZeugnisErwartung z where z.id in :ids order by z.id",EinkaufZeugnisErwartung.class)
                    .setParameter("ids",openIds.stream().map(Number::longValue).toList()).getResultList();
            if(expected.isEmpty())throw conflict("Für diese Bestellung sind keine fälligen Zeugnisse offen.");
            Map<Long,List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll>> byPosition=new HashMap<>();
            expected.forEach(x->byPosition.computeIfAbsent(x.getBestellPosition().getId(),ignored->new ArrayList<>()).add(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll(x.getArt(),x.getGrundlage(),x.getGrundlageVersion(),true)));
            snapshots=revision.getPositionen().stream().filter(p->byPosition.containsKey(p.getId())).map(p->withDocuments(p.getPosition(),byPosition.get(p.getId()))).toList();
            Map<Long,List<String>> charges=new HashMap<>();
            @SuppressWarnings("unchecked")
            List<Object[]> openCharges=em.createNativeQuery("SELECT z.id,c.kennung,c.schmelznummer FROM einkauf_zeugnis_erwartung z JOIN einkauf_charge c ON c.id IN ("+OFFENE_CHARGEN_SQL+") WHERE z.id IN (:ids) ORDER BY z.id,c.id")
                    .setParameter("ids",openIds.stream().map(Number::longValue).toList()).getResultList();
            for(Object[] chargeRow:openCharges){
                String label=String.join(" / ",java.util.stream.Stream.of((String)chargeRow[1],(String)chargeRow[2]).filter(v->v!=null&&!v.isBlank()).toList());
                charges.computeIfAbsent(((Number)chargeRow[0]).longValue(),ignored->new ArrayList<>()).add(label);
            }
            missing=expected.stream().map(x->{List<String> codes=charges.getOrDefault(x.getId(),List.of());String charge=codes.isEmpty()?"Charge noch nicht erfasst":"Charge(n): "+String.join(", ",codes);return x.getArt()+" — "+x.getGrundlage()+" ("+x.getBestellPosition().getPosition().bezeichnung()+"; "+charge+")";}).toList();
        } else if("BESTELLBESTAETIGUNG".equals(typ)){
            if(em.createQuery("select count(b) from BestellBestaetigung b where b.bestellung.id=:id",Long.class).setParameter("id",vorgangId).getSingleResult().longValue()!=0L)throw conflict("Die Auftragsbestätigung liegt bereits vor.");
            Object deadline=revision.getSnapshot().get("bestaetigungsfrist");
            if(deadline!=null&&LocalDate.parse(deadline.toString()).isAfter(LocalDate.now(clock)))throw conflict("Die Frist für die Auftragsbestätigung ist noch nicht abgelaufen.");
        } else {
            List<LocalDate> confirmations=em.createQuery("select b.liefertermin from BestellBestaetigung b where b.bestellung.id=:id order by b.id desc",LocalDate.class).setParameter("id",vorgangId).setMaxResults(1).getResultList();
            if(confirmations.isEmpty()||confirmations.get(0)!=null&&confirmations.get(0).isAfter(LocalDate.now(clock)))throw conflict("Es gibt keinen überfälligen bestätigten Liefertermin.");
        }
        if(values.containsKey("ANFRAGENUMMER"))values.put("ANFRAGENUMMER","");
        var rendered=templates.rendern(template.getId(),new VorlagenKontext(templateType(typ),values,snapshots,null));
        return new NachfrageEntwurf(typ,vorgangId,null,rendered.templateId(),rendered.version(),contact.email(),rendered.subject(),rendered.htmlBody(),missing);
    }

    private NachfrageEntwurf anfrageNachfrage(Long requestId,Long participationId){
        if(participationId==null)throw bad("Für eine Anfragenachfrage muss ein Lieferant angegeben sein.");
        var request=em.find(Einkaufsanfrage.class,requestId);
        var participant=em.find(AnfrageLieferant.class,participationId);
        if(request==null||participant==null||request.getAktuelleRevision()==null||!Objects.equals(participant.getRevision().getId(),request.getAktuelleRevision().getId()))throw new NoSuchElementException("Anfrage oder Lieferantenbeteiligung wurde nicht gefunden.");
        if(!"VERSENDET".equals(participant.getStatus())||participant.getVersandAnnahmeereignis()==null||participant.getAntwortAm()!=null)throw conflict("Für diese Anfrage ist keine Antwort mehr offen.");
        AnfrageRevision revision=request.getAktuelleRevision();
        if(revision.getAntwortfrist()!=null&&revision.getAntwortfrist().isAfter(LocalDate.now(clock)))throw conflict("Die Antwortfrist ist noch nicht abgelaufen.");
        var contact=participant.getKontakt();if(contact.email()==null||contact.email().isBlank())throw conflict("Für diesen Lieferanten fehlt eine Empfängeradresse.");
        EmailTextTemplate template=findTemplate("EINKAUF_NACHFRAGE");
        Map<String,String> values=new HashMap<>();put(values,"LIEFERANTENNAME",contact.lieferantenname());put(values,"ANSPRECHPARTNER",contact.name());put(values,"ANREDE",contact.anrede());put(values,"EIGENE_KUNDENNUMMER_BEIM_LIEFERANTEN",contact.eigeneKundennummer());
        put(values,"ANFRAGENUMMER",request.getPaNummer());put(values,"ANTWORTFRIST",revision.getAntwortfrist()==null?"Termin klären":revision.getAntwortfrist().toString());put(values,"LIEFERTERMIN",revision.getLiefertermin()==null?"":revision.getLiefertermin().toString());
        var rendered=templates.rendern(template.getId(),new VorlagenKontext("EINKAUF_NACHFRAGE",values,revision.getPositionen().stream().map(AnfragePosition::getSnapshot).toList(),participant.getRueckmeldecode()));
        return new NachfrageEntwurf("ANFRAGE_ANTWORTFRIST",requestId,participationId,rendered.templateId(),rendered.version(),contact.email(),rendered.subject(),rendered.htmlBody(),List.of());
    }

    private EmailTextTemplate findTemplate(String type){return em.createQuery("select t from EmailTextTemplate t where t.dokumentTyp=:type and t.aktiv=true and t.standard=true order by t.id",EmailTextTemplate.class).setParameter("type",type).setMaxResults(1).getResultStream().findFirst().orElseThrow(()->conflict("Für diese Nachfrage ist keine aktive Standardvorlage eingerichtet."));}
    private static String templateType(String typ){return switch(typ){case "BESTELLBESTAETIGUNG"->"EINKAUF_BESTAETIGUNG_NACHFRAGE";case "LIEFERTERMIN"->"EINKAUF_LIEFERUNG_NACHFRAGE";case "ZEUGNIS"->"EINKAUF_ZEUGNIS_NACHFORDERUNG";default->throw bad("Unbekannte Nachfrageart.");};}
    private static PositionSnapshot withDocuments(PositionSnapshot p,List<org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll> docs){return new PositionSnapshot(p.art(),p.artikelId(),p.interneReferenz(),p.zeichnungsnummer(),p.zeichnungsrevision(),p.bezeichnung(),p.werkstoff(),p.abmessung(),p.basis(),p.schnittForm(),p.winkelLinks(),p.winkelRechts(),p.bearbeitung(),p.oberflaeche(),docs,p.anlageVersionIds(),p.beschaffungsdetails(),p.positionsnummer());}
    private static void put(Map<String,String> map,String key,String value){if(value!=null&&!value.isBlank())map.put(key,value);}
    private void bind(Query query,LocalDate today,Long responsible){query.setParameter("heute",today);query.setParameter("zustaendig",responsible);}
    private Faelligkeit row(Object raw){Object[] r=(Object[])raw;return new Faelligkeit((String)r[0],number(r[1]),(String)r[2],number(r[3]),date(r[4]),number(r[5]),(String)r[6]);}
    private static Long number(Object x){return x==null?null:((Number)x).longValue();}
    private static LocalDate date(Object x){if(x==null)return null;if(x instanceof Date d)return d.toLocalDate();if(x instanceof LocalDate d)return d;return LocalDate.parse(x.toString());}
    private static boolean faellig(LocalDate termin,LocalDate heute){return termin==null||!termin.isAfter(heute);}
    private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
}
