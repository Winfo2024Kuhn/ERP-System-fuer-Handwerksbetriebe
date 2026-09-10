package org.example.kalkulationsprogramm.service;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.MonatsSaldo;
import org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.*;
import org.example.kalkulationsprogramm.repository.MonatsabschlussUebersichtRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
@Service
@RequiredArgsConstructor
public class MonatsabschlussUebersichtService {
    private final MonatsabschlussUebersichtRepository repository;
    private final MonatsSaldoService salden;
    private final MonatsabschlussBerechtigungService berechtigung;
    public Uebersicht lade(Filter f, Authentication auth) {
        berechtigung.verlangeAkteur(auth);
        if (f == null) throw ungueltig();
        validiere(f.jahr(), f.monat(), f.mitarbeiterId(), f.abteilungId());
        if (f.page()<0 || f.size()<1 || f.size()>100 || !Set.of("ALLE","OFFEN","ABGESCHLOSSEN").contains(f.status()==null?"":f.status())) throw ungueltig();
        var monate = zeilen(f.jahr(), f.monat(), f.mitarbeiterId(), f.abteilungId(), 1);
        var alle = monate.getFirst().stream().filter(z -> f.status().equals("ALLE") || z.festgeschrieben()==f.status().equals("ABGESCHLOSSEN")).toList();
        int start=(int)Math.min((long)f.page()*f.size(), alle.size());
        return new Uebersicht(alle.subList(start, Math.min(start+f.size(),alle.size())), alle.size(),f.page(),f.size(),summe(alle),alle.stream().map(z -> new Stand(z.referenz().mitarbeiterId(),f.jahr(),f.monat(),z.version(),z.festgeschrieben())).toList());
    }
    public List<Vergleichsmonat> vergleich(int jahr,int monat,Long mitarbeiterId,Long abteilungId,Authentication auth) {
        berechtigung.verlangeAkteur(auth); validiere(jahr,monat,mitarbeiterId,abteilungId);
        var rows=zeilen(jahr,monat,mitarbeiterId,abteilungId,6);
        var start=YearMonth.of(jahr,monat).minusMonths(5);
        var result=new ArrayList<Vergleichsmonat>();
        for(int i=0;i<6;i++) { var ym=start.plusMonths(i); var list=rows.get(i); int closed=(int)list.stream().filter(Zeile::festgeschrieben).count(); result.add(new Vergleichsmonat(ym.getYear(),ym.getMonthValue(),summe(list),list.size()-closed,closed)); }
        return List.copyOf(result);
    }
    private List<List<Zeile>> zeilen(int jahr,int monat,Long id,Long abteilung,int anzahl) {
        var personen=repository.personen(id,abteilung,PageRequest.of(0,501));
        if(personen.size()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mehr als 500 Mitarbeiter. Bitte den Filter eingrenzen.");
        var ids=personen.stream().map(MonatsabschlussUebersichtRepository.Person::getId).toList();
        var ende=YearMonth.of(jahr,monat); var start=ende.minusMonths(anzahl-1);
        var abteilungen=new HashMap<Long,List<Long>>(); var snapshots=new HashMap<Referenz,MonatsSaldo>();
        if(!ids.isEmpty()) {
            for(var a:repository.abteilungen(ids)) abteilungen.computeIfAbsent(a.getMitarbeiterId(),k->new ArrayList<>()).add(a.getAbteilungId());
            for(var s:repository.salden(ids,start.getYear()*12+start.getMonthValue(),jahr*12+monat)) snapshots.put(new Referenz(s.getMitarbeiter().getId(),s.getJahr(),s.getMonat()),s);
        }
        var result=new ArrayList<List<Zeile>>();
        for(int i=0;i<anzahl;i++) {
            var ym=start.plusMonths(i); var rows=new ArrayList<Zeile>();
            for(var p:personen) {
                var ref=new Referenz(p.getId(),ym.getYear(),ym.getMonthValue()); var s=snapshots.get(ref);
                // Begrenzte Bestandsberechnung nur bei kaltem/ungültigem Cache oder laufenden Monaten.
                if(s==null || (!Boolean.TRUE.equals(s.getFestgeschrieben()) && (!Boolean.TRUE.equals(s.getGueltig()) || !ym.isBefore(YearMonth.now())))) s=salden.getOrBerechne(p.getId(),ym.getYear(),ym.getMonthValue());
                rows.add(new Zeile(ref,((p.getVorname()==null?"":p.getVorname())+" "+(p.getNachname()==null?"":p.getNachname())).trim(),List.copyOf(abteilungen.getOrDefault(p.getId(),List.of())),Boolean.TRUE.equals(s.getFestgeschrieben()),s.getVersion(),s.getFestgeschriebenAm(),kennzahlen(s)));
            }
            rows.sort(Comparator.comparing(Zeile::mitarbeiterName,String.CASE_INSENSITIVE_ORDER).thenComparing(z->z.referenz().mitarbeiterId()));
            result.add(List.copyOf(rows));
        }
        return result;
    }
    private Kennzahlen kennzahlen(MonatsSaldo s) { return new Kennzahlen(s.getIstStunden(),s.getSollStunden(),s.getAbwesenheitsStunden(),s.getFeiertagsStunden(),s.getKorrekturStunden(),s.getGesamtIst(),s.getDifferenz()); }
    private Kennzahlen summe(List<Zeile> rows) {
        BigDecimal[] v=new BigDecimal[7]; Arrays.fill(v,BigDecimal.ZERO);
        for(var z:rows) { var k=z.kennzahlen(); var a=List.of(k.istStunden(),k.sollStunden(),k.abwesenheitsStunden(),k.feiertagsStunden(),k.korrekturStunden(),k.gesamtIst(),k.differenz()); for(int i=0;i<7;i++)v[i]=v[i].add(a.get(i)); }
        return new Kennzahlen(v[0],v[1],v[2],v[3],v[4],v[5],v[6]);
    }
    static void validiere(int jahr,int monat,Long id,Long abteilung) { if(jahr<1000||jahr>9999||monat<1||monat>12||(id!=null&&id<=0)||(abteilung!=null&&abteilung<=0)) throw ungueltig(); }
    static ResponseStatusException ungueltig() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bitte gültige Mitarbeiter, Monate und Filter wählen."); }
}
