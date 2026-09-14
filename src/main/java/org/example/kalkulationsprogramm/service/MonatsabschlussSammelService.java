package org.example.kalkulationsprogramm.service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.*;
import org.example.kalkulationsprogramm.repository.MonatsSaldoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.YearMonth;
import java.util.*;
@Service
public class MonatsabschlussSammelService {
    private static final Logger log=LoggerFactory.getLogger(MonatsabschlussSammelService.class);
    private final MonatsabschlussBerechtigungService berechtigung;
    private final MonatsSaldoService service;
    private final MonatsSaldoRepository repository;
    private final EntityManager em;
    private final TransactionTemplate tx;
    public MonatsabschlussSammelService(MonatsabschlussBerechtigungService berechtigung,MonatsSaldoService service,MonatsSaldoRepository repository,EntityManager em,PlatformTransactionManager tm) {
        this.berechtigung=berechtigung;this.service=service;this.repository=repository;this.em=em;this.tx=new TransactionTemplate(tm);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public SammelResponse abschliessen(SammelRequest request,Authentication auth) {
        berechtigung.verlangeAkteur(auth);
        if(request==null||request.auswahl()==null||request.auswahl().isEmpty()||request.auswahl().size()>500) throw MonatsabschlussUebersichtService.ungueltig();
        var seen=new HashSet<Referenz>();
        for(var r:request.auswahl()) {
            if(r==null||r.mitarbeiterId()==null||!seen.add(r)) throw MonatsabschlussUebersichtService.ungueltig();
            MonatsabschlussUebersichtService.validiere(r.jahr(),r.monat(),r.mitarbeiterId(),null);
            if(!YearMonth.of(r.jahr(),r.monat()).isBefore(YearMonth.now())) throw MonatsabschlussUebersichtService.ungueltig();
        }
        var result=new ArrayList<Einzelergebnis>();
        for(var r:request.auswahl()) {
            try { result.add(tx.execute(status -> {
                var person=em.find(Mitarbeiter.class,r.mitarbeiterId(),LockModeType.PESSIMISTIC_WRITE);
                if(person==null||person.getArt()!=MitarbeiterArt.MENSCH) return new Einzelergebnis(r,"FEHLGESCHLAGEN","Mitarbeiter nicht vorhanden oder kein Mensch.");
                if(Boolean.TRUE.equals(person.getIstGeschaeftsfuehrer())) return new Einzelergebnis(r,"FEHLGESCHLAGEN","Geschäftsführer führen kein Zeitkonto und haben keinen Monatsabschluss.");
                if(!Boolean.TRUE.equals(person.getFuehrtZeitkonto())) return new Einzelergebnis(r,"FEHLGESCHLAGEN","Mitarbeiter ohne Zeiterfassung haben keinen Monatsabschluss.");
                var saldo=repository.findGesperrt(r.mitarbeiterId(),r.jahr(),r.monat());
                saldo.ifPresent(s->em.refresh(s,LockModeType.PESSIMISTIC_WRITE));
                if(saldo.filter(s->Boolean.TRUE.equals(s.getFestgeschrieben())).isPresent()) return new Einzelergebnis(r,"BEREITS_ABGESCHLOSSEN","Dieser Monat ist bereits abgeschlossen.");
                service.abschliessenOhneVerlauf(r.mitarbeiterId(),r.jahr(),r.monat(),auth);
                return new Einzelergebnis(r,"ABGESCHLOSSEN","Monat abgeschlossen.");
            })); } catch(RuntimeException e) {
                log.warn("Sammelabschluss fehlgeschlagen: Mitarbeiter={}, Jahr={}, Monat={}, Ursache={}, Stack={}",r.mitarbeiterId(),r.jahr(),r.monat(),e.getClass().getName(),Arrays.toString(e.getStackTrace()));
                result.add(new Einzelergebnis(r,"FEHLGESCHLAGEN","Monat konnte nicht abgeschlossen werden. Bitte den Monatsstand prüfen und erneut versuchen."));
            }
        }
        log.info("Sammelabschluss beendet: angefragt={}, abgeschlossen={}, fehlgeschlagen={}", result.size(),
                result.stream().filter(r -> r.status().equals("ABGESCHLOSSEN")).count(),
                result.stream().filter(r -> r.status().equals("FEHLGESCHLAGEN")).count());
        return new SammelResponse(List.copyOf(result));
    }
}
