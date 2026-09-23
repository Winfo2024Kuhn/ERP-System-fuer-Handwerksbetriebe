package org.example.kalkulationsprogramm.service.einkauf;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EinkaufZeugnisService {
    private final BestellungRevisionRepository revisions;
    private final EinkaufZeugnisRepository zeugnisse;
    private final EinkaufAnforderungsVorlageRepository vorlagen;
    private final EinkaufDateiRepository dateien;
    private final EntityManager em;
    private final Clock clock=Clock.systemDefaultZone();

    public EinkaufZeugnisService(BestellungRevisionRepository revisions, EinkaufZeugnisRepository zeugnisse,
            EinkaufAnforderungsVorlageRepository vorlagen, EinkaufDateiRepository dateien,
            EntityManager em) {
        this.revisions=revisions; this.zeugnisse=zeugnisse; this.vorlagen=vorlagen; this.dateien=dateien;
        this.em=em;
    }

    @Transactional
    public void erwarte(Long bestellungRevisionId, LocalDate frist) {
        if (bestellungRevisionId == null || bestellungRevisionId <= 0)
            throw bad("Eine gültige Bestellfassung muss angegeben sein.");
        BestellungRevision revision=revisions.findById(bestellungRevisionId)
                .orElseThrow(()->new NoSuchElementException("Die Bestellfassung wurde nicht gefunden."));
        if (!revision.istAngenommen() || revision.istVerworfen())
            throw conflict("Nur eine angenommene Bestellfassung begründet Zeugnisanforderungen.");
        List<EinkaufZeugnisErwartung> neu=new ArrayList<>();
        List<EinkaufZeugnisErwartung> existing=new ArrayList<>(zeugnisse.findByRevision_IdOrderByIdAsc(bestellungRevisionId));
        for (BestellungPosition position:revision.getPositionen()) {
            var soll=position.getPosition()==null?null:position.getPosition().dokumente();
            if (soll==null) continue;
            for (int index=0;index<soll.size();index++) {
                var dokument=soll.get(index);
                if (dokument==null || dokument.art()==null || dokument.grundlage()==null || dokument.grundlage().isBlank())
                    throw bad("Die Zeugnisgrundlage in der Bestellfassung ist unvollständig.");
                final int requirementIndex=index;
                boolean duplicate=existing.stream()
                        .anyMatch(x->Objects.equals(x.getBestellPosition().getId(),position.getId())&&x.getAnforderungsIndex()==requirementIndex);
                if (!duplicate) {
                    String version=blank(dokument.grundlageVersion())?"Bestellfassung "+revision.getNummer():dokument.grundlageVersion();
                    var expectation=new EinkaufZeugnisErwartung(revision,position,index,dokument.art(),dokument.grundlage(),version,frist);
                    neu.add(expectation);existing.add(expectation);
                }
            }
        }
        if (!neu.isEmpty()) zeugnisse.saveAll(neu);
    }

    @Transactional
    public ErwartungDto eingang(Long erwartungId,Long dokumentId) {
        if(erwartungId==null||erwartungId<=0||dokumentId==null||dokumentId<=0)throw bad("Zeugnisanforderung und Dokument sind erforderlich.");
        var expectation=zeugnisse.findById(erwartungId).orElseThrow(()->new NoSuchElementException("Die Zeugnisanforderung wurde nicht gefunden."));
        var file=dateien.findById(dokumentId).orElseThrow(()->new NoSuchElementException("Das Einkaufsdokument wurde nicht gefunden."));
        if(!"application/pdf".equalsIgnoreCase(file.getMimeTyp()))throw bad("Für Zeugnisse sind nur PDF-Dateien zulässig.");
        pruefeUndBindeBeleg(file,expectation.getRevision().getBestellung());
        if(expectation.getDateien().stream().anyMatch(existing->Objects.equals(existing.getId(),file.getId())))return dto(expectation);
        expectation.eingegangen(file,Instant.now(clock));
        ensureChargeStatuses(expectation);
        expectation.aktualisiereChargenstand(chargeStatuses(expectation.getId()));
        return dto(zeugnisse.saveAndFlush(expectation));
    }

    @Transactional
    public ZuordnungDto zuordnen(Zuordnung request, Long akteurId) {
        if (request==null || request.dokumentId()==null || request.dokumentId()<=0 || request.erwartungIds().isEmpty()
                || akteurId==null || akteurId<=0) throw bad("Dokument, Zeugnisanforderungen und Benutzer sind erforderlich.");
        if(request.erwartungIds().stream().distinct().count()!=request.erwartungIds().size()||request.erwartungIds().size()>100
                ||request.lieferPositionIds().isEmpty()||request.chargeIds().isEmpty()||request.lieferPositionIds().size()>500||request.chargeIds().size()>500)throw bad("Die Zuordnung enthält doppelte oder zu viele Einträge.");
        EinkaufDatei datei=dateien.findById(request.dokumentId()).orElseThrow(()->new NoSuchElementException("Das Einkaufsdokument wurde nicht gefunden."));
        if (!"application/pdf".equalsIgnoreCase(datei.getMimeTyp())) throw bad("Für Zeugnisse sind nur PDF-Dateien zulässig.");
        List<EinkaufZeugnisErwartung> targets=zeugnisse.findAllById(request.erwartungIds());
        if (targets.size()!=request.erwartungIds().stream().distinct().count()) throw new NoSuchElementException("Eine Zeugnisanforderung wurde nicht gefunden.");
        BestellungRevision latest=latestAccepted(targets.get(0).getRevision().getBestellung().getId());
        if(targets.stream().anyMatch(t->!Objects.equals(t.getRevision().getBestellung().getId(),latest.getBestellung().getId()))) throw bad("Ein Dokument kann nur Zeugnisse derselben Bestellung zugeordnet werden.");
        pruefeUndBindeBeleg(datei,latest.getBestellung());
        if(targets.stream().anyMatch(t->t.getRevision().getId()==null||!Objects.equals(t.getRevision().getId(),latest.getId())))
            throw conflict("Die Zeugnisanforderung gehört nicht zur zuletzt angenommenen Bestellfassung.");
        if(targets.stream().anyMatch(t->t.getDateien().stream().noneMatch(f->Objects.equals(f.getId(),datei.getId()))))
            throw conflict("Der PDF-Eingang muss zuerst der Zeugnisanforderung zugeordnet werden.");
        List<LieferungPosition> lines=load(request.lieferPositionIds(),LieferungPosition.class);
        List<EinkaufCharge> charges=load(request.chargeIds(),EinkaufCharge.class);
        Set<Long> lineIds=lines.stream().map(LieferungPosition::getId).collect(java.util.stream.Collectors.toSet());
        Map<Long,Long> chargePositionIds=new HashMap<>();
        em.createQuery("select c.id,c.position.bestellPosition.id from EinkaufCharge c where c.id in :ids",Object[].class).setParameter("ids",request.chargeIds()).getResultList()
                .forEach(row->{Object[] chargeRow=(Object[])row;chargePositionIds.put(((Number)chargeRow[0]).longValue(),((Number)chargeRow[1]).longValue());});
        Set<Long> matchingChargeIds=charges.isEmpty()?Set.of():new HashSet<>(em.createQuery(
                "select c.id from EinkaufCharge c where c.id in :chargeIds and c.position.id in :lineIds",Long.class)
                .setParameter("chargeIds",request.chargeIds()).setParameter("lineIds",lineIds).getResultList());
        Set<Long> targetPositionIds=targets.stream().map(t->t.getBestellPosition().getId()).collect(java.util.stream.Collectors.toSet());
        boolean conflict=targets.stream().anyMatch(t->lines.stream().noneMatch(line->Objects.equals(line.getBestellPosition().getId(),t.getBestellPosition().getId()))
                        ||charges.stream().noneMatch(charge->chargePositionIds.containsKey(charge.getId())&&Objects.equals(chargePositionIds.get(charge.getId()),t.getBestellPosition().getId())))
                ||lines.isEmpty()||lines.stream().anyMatch(line->!Objects.equals(line.getBestellPosition().getRevision().getId(),latest.getId())
                        ||!targetPositionIds.contains(line.getBestellPosition().getId()))
                || matchingChargeIds.size()!=charges.size();
        if (request.schmelznummer()!=null&&!request.schmelznummer().isBlank()) {
            conflict |= lines.stream().anyMatch(l->l.getSchmelznummer()==null||!request.schmelznummer().equals(l.getSchmelznummer()));
            conflict |= charges.stream().anyMatch(c->c.getSchmelznummer()==null||!request.schmelznummer().equals(c.getSchmelznummer()));
        }
        List<EinkaufZeugnisZuordnung> associations=new ArrayList<>();
        for(var target:targets){
            ensureChargeStatuses(target);
            target.zuordnen(lines.stream().filter(l->Objects.equals(l.getBestellPosition().getId(),target.getBestellPosition().getId())).toList(),
                    charges.stream().filter(c->Objects.equals(chargePositionIds.get(c.getId()),target.getBestellPosition().getId())).toList(),conflict);
            for(var charge:charges){
                if(!Objects.equals(chargePositionIds.get(charge.getId()),target.getBestellPosition().getId()))continue;
                var status=chargeStatus(target.getId(),charge.getId());status.zuordnen(conflict);
                List<EinkaufZeugnisZuordnung> old=em.createQuery("select z from EinkaufZeugnisZuordnung z where z.zeugnis.id=:eid and z.datei.id=:fid and z.chargeStatus.id=:sid",EinkaufZeugnisZuordnung.class)
                        .setParameter("eid",target.getId()).setParameter("fid",datei.getId()).setParameter("sid",status.getId()).getResultList();
                EinkaufZeugnisZuordnung association=old.isEmpty()?new EinkaufZeugnisZuordnung(target,datei,status,conflict):old.getFirst();
                if(old.isEmpty())em.persist(association);
                associations.add(association);
            }
            target.aktualisiereChargenstand(chargeStatuses(target.getId()));
        }
        zeugnisse.saveAll(targets);zeugnisse.flush();
        return new ZuordnungDto(targets.stream().map(this::dto).toList(),conflict,associations.stream().map(this::chargeDto).toList());
    }

    @Transactional
    public PruefungDto pruefen(Long zuordnungId, Pruefung request, Long akteurId) {
        if(zuordnungId==null||zuordnungId<=0||request==null||request.version()<0||akteurId==null||akteurId<=0
                ||blank(request.ergebnis())||!Set.of("BESTANDEN","ABGELEHNT","KLAERUNG").contains(request.ergebnis())
                ||blank(request.begruendung())||request.begruendung().length()>2000||blank(request.grundlageVersion()))
            throw bad("Prüfergebnis, Begründung, Grundlage und Benutzer müssen vollständig sein.");
        EinkaufZeugnisZuordnung association=em.find(EinkaufZeugnisZuordnung.class,zuordnungId);
        if(association==null)throw new NoSuchElementException("Die Zeugniszuordnung wurde nicht gefunden.");
        EinkaufZeugnisErwartung target=association.getZeugnis();EinkaufZeugnisChargeStatus chargeStatus=association.getChargeStatus();
        pruefeUndBindeBeleg(association.getDatei(),target.getRevision().getBestellung());
        if(chargeStatus.getVersion()==null||chargeStatus.getVersion()!=request.version())throw conflict("Die Zeugniszuordnung wurde geändert; bitte neu laden.");
        if(!Objects.equals(target.getGrundlageVersion(),request.grundlageVersion()))throw conflict("Die geprüfte Grundlage passt nicht mehr zur Bestellfassung.");
        if(association.isKlaerungNoetig()||chargeStatus.getStatus()==EinkaufZeugnisErwartung.Status.KLAERUNG_NOETIG)throw conflict("Widersprüchliche Charge oder Schmelznummer muss zuerst geklärt werden.");
        if(chargeStatus.getStatus()!=EinkaufZeugnisErwartung.Status.ZUGEORDNET&&chargeStatus.getStatus()!=EinkaufZeugnisErwartung.Status.GEPRUEFT)throw conflict("Nur eingegangene und zugeordnete Zeugnisse können geprüft werden.");
        boolean positive="BESTANDEN".equals(request.ergebnis());
        EinkaufDokumentPruefung check=new EinkaufDokumentPruefung(association,akteurId,Instant.now(clock),request.ergebnis(),request.begruendung().trim(),request.grundlageVersion().trim());
        chargeStatus.pruefen(positive);target.pruefungAbgelegt(check);
        em.persist(check); zeugnisse.flush();
        target.aktualisiereChargenstand(chargeStatuses(target.getId()));
        zeugnisse.flush();
        return new PruefungDto(check.getId(),target.getId(),check.getErgebnis(),check.getBegruendung(),check.getGrundlageVersion(),
                check.getAkteurId(),check.getGeprueftAm(),chargeStatus.isMaterialFreigegeben());
    }

    @Transactional
    public List<ErwartungDto> liste(Long bestellungId) {
        if(bestellungId==null||bestellungId<=0)throw bad("Die Bestell-ID ist ungültig.");
        List<EinkaufZeugnisErwartung> expectations=zeugnisse.findByRevision_Bestellung_IdOrderByFristAscIdAsc(bestellungId);
        for(var expectation:expectations){ensureChargeStatuses(expectation);expectation.aktualisiereChargenstand(chargeStatuses(expectation.getId()));}
        zeugnisse.flush();
        return expectations.stream().map(this::dto).toList();
    }

    @Transactional(readOnly=true)
    public List<DokumentSoll> vorschlagen(Long artikelId,Long projektId) {
        if((artikelId==null||artikelId<=0)&&(projektId==null||projektId<=0))throw bad("Artikel oder Projekt muss angegeben sein.");
        List<EinkaufAnforderungsVorlage> current=vorlagen.findAktuelleBestaetigteFuerArtikelOderProjekt(artikelId,projektId);
        if(current.isEmpty())return List.of(new DokumentSoll(null,"Für Werkstoff/EXC liegt keine bestätigte Vorgabe vor; bitte manuell prüfen.","UNGEKLAERT",false));
        return current.stream().map(v->new DokumentSoll(v.getArt(),v.getGrundlage(),"V"+v.getVersionsnummer(),true)).toList();
    }

    @Transactional
    public VorlageDto vorlageAnlegen(Vorlage request,Long akteurId) {
        if(request==null||((request.artikelId()==null||request.artikelId()<=0)&&(request.projektId()==null||request.projektId()<=0))
                ||request.art()==null||blank(request.grundlage())||request.grundlage().length()>1000||akteurId==null||akteurId<=0)
            throw bad("Artikel oder Projekt sowie Zeugnisart und fachliche Grundlage sind erforderlich.");
        List<EinkaufAnforderungsVorlage> old=vorlagen.findAktuelleBestaetigteFuerArtikelOderProjekt(request.artikelId(),request.projektId());
        int version=old.stream().filter(v->Objects.equals(v.getArtikelId(),request.artikelId())&&Objects.equals(v.getProjektId(),request.projektId())&&v.getArt()==request.art()).mapToInt(EinkaufAnforderungsVorlage::getVersionsnummer).max().orElse(0)+1;
        old.stream().filter(v->Objects.equals(v.getArtikelId(),request.artikelId())&&Objects.equals(v.getProjektId(),request.projektId())&&v.getArt()==request.art()).forEach(v->em.createQuery("update EinkaufAnforderungsVorlage x set x.aktiv=false where x.id=:id").setParameter("id",v.getId()).executeUpdate());
        var saved=vorlagen.saveAndFlush(new EinkaufAnforderungsVorlage(request.artikelId(),request.projektId(),request.art(),request.grundlage().trim(),version,akteurId,Instant.now(clock)));
        return new VorlageDto(saved.getId(),saved.getArtikelId(),saved.getProjektId(),saved.getArt(),saved.getGrundlage(),"V"+version,true);
    }

    private void pruefeUndBindeBeleg(EinkaufDatei file,EinkaufBestellung order) {
        LieferantDokument document=null;
        if(file.getLieferantDokumentId()!=null) {
            document=em.find(LieferantDokument.class,file.getLieferantDokumentId(),LockModeType.PESSIMISTIC_WRITE);
        } else if(file.getEmailAttachmentId()!=null) {
            List<LieferantDokument> linked=em.createQuery(
                    "select d from LieferantDokument d where d.attachment.id=:id order by d.id",LieferantDokument.class)
                    .setParameter("id",file.getEmailAttachmentId()).setMaxResults(2)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            if(linked.size()!=1)throw conflict("Der Lieferantenbeleg zur PDF-Datei ist nicht eindeutig.");
            document=linked.getFirst();
        }
        if(document==null)throw bad("Zum Zeugnis fehlt der Lieferantenbeleg.");
        // Refresh under the document lock: another workflow may already have bound this PDF.
        em.refresh(document,LockModeType.PESSIMISTIC_WRITE);
        if(document.getLieferant()==null||!Objects.equals(document.getLieferant().getId(),order.getLieferantId()))
            throw bad("Das Zeugnis gehört nicht zum Lieferanten der Bestellung.");
        if(document.getEinkaufBestellungId()!=null&&!Objects.equals(document.getEinkaufBestellungId(),order.getId()))
            throw conflict("Das Zeugnis gehört bereits zu einer anderen Bestellung.");
        document.setEinkaufBestellungId(order.getId());
    }

    private BestellungRevision latestAccepted(Long orderId){return revisions.findByBestellung_IdOrderByNummerAsc(orderId).stream().filter(BestellungRevision::istAngenommen)
            .max(Comparator.comparingInt(BestellungRevision::getNummer)).orElseThrow(()->conflict("Bestellung hat keine angenommene Fassung."));}
    private List<EinkaufZeugnisChargeStatus> chargeStatuses(Long expectationId){return em.createQuery("select s from EinkaufZeugnisChargeStatus s where s.zeugnis.id=:id order by s.id",EinkaufZeugnisChargeStatus.class).setParameter("id",expectationId).getResultList();}
    private EinkaufZeugnisChargeStatus chargeStatus(Long expectationId,Long chargeId){return em.createQuery("select s from EinkaufZeugnisChargeStatus s where s.zeugnis.id=:eid and s.charge.id=:cid",EinkaufZeugnisChargeStatus.class).setParameter("eid",expectationId).setParameter("cid",chargeId).getSingleResult();}
    private void ensureChargeStatuses(EinkaufZeugnisErwartung expectation){
        List<EinkaufCharge> all=em.createQuery("select c from EinkaufCharge c where c.position.bestellPosition.id=:positionId and c.position.bestellPosition.revision.id=:revisionId order by c.id",EinkaufCharge.class)
                .setParameter("positionId",expectation.getBestellPosition().getId()).setParameter("revisionId",expectation.getRevision().getId()).getResultList();
        Set<Long> existing=chargeStatuses(expectation.getId()).stream().map(s->s.getCharge().getId()).collect(java.util.stream.Collectors.toSet());
        for(EinkaufCharge charge:all)if(!existing.contains(charge.getId()))em.persist(new EinkaufZeugnisChargeStatus(expectation,charge));
        em.flush();
    }
    private ChargeZuordnungDto chargeDto(EinkaufZeugnisZuordnung association){var status=association.getChargeStatus();return new ChargeZuordnungDto(association.getId(),association.getZeugnis().getId(),status.getCharge().getId(),status.getStatus(),status.getVersion()==null?0:status.getVersion(),status.isMaterialFreigegeben());}
    private <T> List<T> load(List<Long> ids,Class<T> type){if(ids.stream().anyMatch(x->x==null||x<=0)||ids.stream().distinct().count()!=ids.size())throw bad("Lieferpositionen und Chargen müssen gültige IDs sein.");return ids.stream().map(id->{T value=em.find(type,id);if(value==null)throw new NoSuchElementException("Lieferposition oder Charge wurde nicht gefunden.");return value;}).toList();}
    private ErwartungDto dto(EinkaufZeugnisErwartung x){
        List<Long> actualCharges=em.createQuery("select c.id from EinkaufCharge c where c.position.bestellPosition.id=:positionId and c.position.bestellPosition.revision.id=:revisionId",Long.class)
                .setParameter("positionId",x.getBestellPosition().getId()).setParameter("revisionId",x.getRevision().getId()).getResultList();
        Set<Long> released=actualCharges.isEmpty()?Set.of():new HashSet<>(em.createQuery("select s.charge.id from EinkaufZeugnisChargeStatus s where s.zeugnis.id=:id and s.status=:done and s.materialFreigegeben=true",Long.class)
                .setParameter("id",x.getId()).setParameter("done",EinkaufZeugnisErwartung.Status.GEPRUEFT).getResultList());
        boolean allReleased=!actualCharges.isEmpty()&&released.containsAll(actualCharges);
        var status=x.getStatus()==EinkaufZeugnisErwartung.Status.GEPRUEFT&&!allReleased?EinkaufZeugnisErwartung.Status.EINGEGANGEN:x.getStatus();
        List<ChargeStatusDto> chargeStates=chargeStatuses(x.getId()).stream().map(s->new ChargeStatusDto(s.getCharge().getId(),s.getStatus(),s.getVersion()==null?0:s.getVersion(),s.isMaterialFreigegeben())).toList();
        return new ErwartungDto(x.getId(),x.getVersion()==null?0:x.getVersion(),x.getRevision().getId(),x.getBestellPosition().getId(),x.getArt(),x.getGrundlage(),x.getGrundlageVersion(),x.getFrist(),status,x.getDateien().stream().map(EinkaufDatei::getId).toList(),x.getLieferPositionen().stream().map(LieferungPosition::getId).toList(),x.getChargen().stream().map(EinkaufCharge::getId).toList(),allReleased,chargeStates);
    }
    private static boolean blank(String x){return x==null||x.isBlank();}
    private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
}
