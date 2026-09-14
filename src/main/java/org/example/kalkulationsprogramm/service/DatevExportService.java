package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.DatevDto.*;
import org.example.kalkulationsprogramm.repository.DatevExportRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.zip.*;

@Service @Slf4j
public class DatevExportService {
 private final DatevExportRepository repo;
 private final MonatsabschlussBerechtigungService rechte;
 private final LodasDateiWriter writer;
 private final TransactionTemplate tx;
 private static final ObjectMapper JSON=new ObjectMapper();
 private static final List<String> ABWESENHEIT=List.of("URLAUB","KRANKHEIT","FORTBILDUNG","ZEITAUSGLEICH","KRANKENGELD","WIEDEREINGLIEDERUNG");
 private record Pruefstand(Vorpruefung dto,Konfiguration config,SortedMap<YearMonth,List<LodasDateiWriter.Buchung>> monate,boolean konflikt) {}
 public DatevExportService(DatevExportRepository repo,MonatsabschlussBerechtigungService rechte,LodasDateiWriter writer,PlatformTransactionManager manager) {
  this.repo=repo;this.rechte=rechte;this.writer=writer;this.tx=new TransactionTemplate(manager);
  tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  tx.setTimeout(60);
 }
 public Vorpruefung pruefen(ExportRequest request,Authentication auth) {
  rechte.verlangeAkteur(auth);validieren(request);
  return inTransaktion(ignored->pruefstand(request).dto());
 }
 public Datei exportieren(ExportRequest request,Authentication auth) {
  rechte.verlangeAkteur(auth);validieren(request);
  // execute returns ONLY after the optimistic checks at commit succeed. No HTTP streaming.
  Datei result=inTransaktion(ignored->{
   var stand=pruefstand(request);
   if(stand.konflikt()) throw konflikt();
   if(!stand.dto().gueltig()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
    "DATEV-Export nicht möglich: "+stand.dto().fehler().getFirst().meldung()+" Bitte die Vorprüfung erneut öffnen.");
   var dateien=new TreeMap<String,byte[]>();
   stand.monate().forEach((month,rows)->dateien.put(dateiname(month),writer.schreiben(stand.config(),month.getYear(),month.getMonthValue(),rows)));
   if(dateien.size()==1) return new Datei(dateien.firstKey(),"text/plain",dateien.get(dateien.firstKey()));
   try(var bytes=new ByteArrayOutputStream();var zip=new ZipOutputStream(bytes)) {
    for(var entry:dateien.entrySet()) {
     var ze=new ZipEntry(entry.getKey());ze.setTime(0);zip.putNextEntry(ze);zip.write(entry.getValue());zip.closeEntry();
    }
    zip.finish();return new Datei("lodas-"+stand.monate().firstKey()+"-bis-"+stand.monate().lastKey()+".zip","application/zip",bytes.toByteArray());
   } catch(IOException ex) { throw new IllegalStateException("DATEV-Datei konnte nicht erzeugt werden",ex); }
  });
  log.info("DATEV-Export erzeugt: monate={} staende={}",request.auswahl().stream().map(s->YearMonth.of(s.jahr(),s.monat())).distinct().count(),request.auswahl().size());
  return result;
 }
 private <T>T inTransaktion(Function<TransactionStatus,T> action) {
  try { return tx.execute(action::apply); }
  catch(OptimisticLockException|OptimisticLockingFailureException ex) {
   log.info("DATEV-Export wegen zwischenzeitlicher Standänderung abgebrochen",ex);throw konflikt();
  }
 }
 private Pruefstand pruefstand(ExportRequest request) {
  var fehler=new ArrayList<Hinweis>();var ausgeschlossen=new ArrayList<Hinweis>();
  var configEntity=repo.konfiguration();
  if(configEntity==null) throw new IllegalStateException("DATEV-Konfiguration fehlt; Migration prüfen");
  boolean konflikt=!Objects.equals(configEntity.getVersion(),request.konfigurationVersion());
  if(konflikt) hinweis(fehler,null,"STAND",null,"Die DATEV-Einstellungen wurden geändert. Bitte neu laden und erneut prüfen.");
  Set<Long> ids=new HashSet<>();Set<Integer> periods=new HashSet<>();
  request.auswahl().forEach(s->{ids.add(s.mitarbeiterId());periods.add(s.jahr()*100+s.monat());});
  var nummern=repo.personalnummern(ids);Map<Long,String> personal=new HashMap<>();Set<Integer> vergeben=new HashSet<>();
  for(var n:nummern) {
   personal.put(n.getMitarbeiterId(),n.getPersonalnummer());
   if(nummerGueltig(n.getPersonalnummer(),1,5) && !vergeben.add(Integer.parseInt(n.getPersonalnummer())))
    hinweis(fehler,null,"PERSONALNUMMER",null,"Eine Personalnummer ist mehrfach vergeben (auch mit führenden Nullen).");
  }
  List<Zuordnung> zuordnungen;
  try { zuordnungen=JSON.readValue(configEntity.getZuordnungenJson(),new TypeReference<List<Zuordnung>>(){}); }
  catch(JsonProcessingException ex) { throw new IllegalStateException("Gespeicherte DATEV-Zuordnung ist ungültig",ex); }
  var config=new Konfiguration(configEntity.getVersion(),configEntity.getZiel(),configEntity.getBeraterNr(),configEntity.getMandantenNr(),zuordnungen,List.of());
  if(!"LODAS".equals(config.ziel()) || !nummerGueltig(config.beraterNr(),4,7) || !nummerGueltig(config.mandantenNr(),1,5))
   hinweis(fehler,null,"KONFIGURATION",null,"LODAS, eine gültige Beraternummer (4–7 Ziffern) und Mandantennummer (1–5 Ziffern) einrichten.");
  Map<String,Zuordnung> mapping=new HashMap<>();
  if(zuordnungen!=null) for(var z:zuordnungen) {
   if(z==null || !DatevKonfigurationService.KATEGORIEN.contains(z.kategorie()) || mapping.putIfAbsent(z.kategorie(),z)!=null)
    hinweis(fehler,null,"KONFIGURATION",null,"Eine Lohnkategorie ist unbekannt oder doppelt.");
  }
  for(String k:DatevKonfigurationService.KATEGORIEN) {
   var z=mapping.get(k);
   if(z==null || (!z.ausgeschlossen()&&!nummerGueltig(z.lohnart(),1,4)) || (z.ausgeschlossen()&&z.lohnart()!=null&&!z.lohnart().isEmpty()))
    hinweis(fehler,null,k,null,"Für "+k+" eine gültige Lohnart (1–4 Ziffern) festlegen oder ausdrücklich nicht exportieren.");
  }
  Map<Referenz,MonatsSaldo> salden=new HashMap<>();
  repo.salden(ids,periods).forEach(s->salden.put(new Referenz(s.getMitarbeiter().getId(),s.getJahr(),s.getMonat()),s));
  SortedMap<YearMonth,List<LodasDateiWriter.Buchung>> monate=new TreeMap<>();
  for(var selected:request.auswahl()) {
   var ref=new Referenz(selected.mitarbeiterId(),selected.jahr(),selected.monat());var s=salden.get(ref);
   if(s==null || !Boolean.TRUE.equals(s.getFestgeschrieben()) || !Objects.equals(s.getVersion(),selected.version())) {
    konflikt=true;hinweis(fehler,ref,"STAND",null,"Der Monat fehlt, ist offen oder wurde geändert. Bitte die Monatsübersicht neu laden.");continue;
   }
   repo.standSichern(s);
   String pnr=personal.get(ref.mitarbeiterId());
   if(!nummerGueltig(pnr,1,5)) hinweis(fehler,ref,"PERSONALNUMMER",null,"Eine gültige Personalnummer mit 1–5 Ziffern fehlt.");
   var rows=monate.computeIfAbsent(YearMonth.of(ref.jahr(),ref.monat()),ignored->new ArrayList<>());
   var werte=new LinkedHashMap<String,BigDecimal>();werte.put("ARBEIT",s.getIstStunden());werte.put("FEIERTAG",s.getFeiertagsStunden());
   var details=Arrays.asList(s.getUrlaubStunden(),s.getKrankheitStunden(),s.getFortbildungStunden(),s.getZeitausgleichStunden(),s.getKrankengeldStunden(),s.getWiedereingliederungStunden());
   if(details.stream().anyMatch(Objects::isNull)) {
    boolean alleAus=ABWESENHEIT.stream().allMatch(k->mapping.containsKey(k)&&mapping.get(k).ausgeschlossen());
    hinweis(alleAus?ausgeschlossen:fehler,ref,"ABWESENHEIT_UNGEGLIEDERT",s.getAbwesenheitsStunden(),
     alleAus?"Abwesenheiten ohne historische Aufschlüsselung werden nicht exportiert.":"Historische Abwesenheitsdetails fehlen. Alle Abwesenheitskategorien ausdrücklich ausschließen oder diesen Monat nicht exportieren.");
   } else {
    if(details.stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(s.getAbwesenheitsStunden())!=0)
     hinweis(fehler,ref,"ABWESENHEIT",s.getAbwesenheitsStunden(),"Die gespeicherten Abwesenheitsdetails stimmen nicht mit der Gesamtsumme überein.");
    for(int i=0;i<ABWESENHEIT.size();i++) werte.put(ABWESENHEIT.get(i),details.get(i));
   }
   if(s.getKorrekturStunden()!=null && s.getKorrekturStunden().signum()!=0)
    hinweis(ausgeschlossen,ref,"KORREKTUR",s.getKorrekturStunden(),"Zeitkontokorrekturen werden nicht ausgezahlt.");
   werte.forEach((k,v)->{
    var z=mapping.get(k);
    if(z!=null&&z.ausgeschlossen()) { if(v!=null&&v.signum()!=0) hinweis(ausgeschlossen,ref,k,v,"Diese Kategorie wird ausdrücklich nicht exportiert."); }
    else if(!LodasDateiWriter.gueltigeStunden(v)) hinweis(fehler,ref,k,v,"Die Stunden sind negativ, zu groß oder haben mehr als zwei Nachkommastellen.");
    else if(v.signum()!=0&&z!=null&&nummerGueltig(z.lohnart(),1,4)&&nummerGueltig(pnr,1,5)) rows.add(new LodasDateiWriter.Buchung(pnr,z.lohnart(),v));
   });
  }
  if(fehler.isEmpty()) monate.forEach((m,rows)->{
   try { writer.schreiben(config,m.getYear(),m.getMonthValue(),rows); }
   catch(IllegalArgumentException ex) { hinweis(fehler,new Referenz(null,m.getYear(),m.getMonthValue()),"BUCHUNGEN",null,ex.getMessage()); }
  });
  return new Pruefstand(new Vorpruefung(fehler.isEmpty(),List.copyOf(fehler),List.copyOf(ausgeschlossen),List.copyOf(request.auswahl()),request.konfigurationVersion()),config,monate,konflikt);
 }
 private static boolean nummerGueltig(String value,int min,int max) {
  return value!=null&&value.matches("[0-9]{"+min+","+max+"}")&&Integer.parseInt(value)>0;
 }
 private static void validieren(ExportRequest request) {
  if(request==null || request.konfigurationVersion()==null || request.konfigurationVersion()<0 || request.auswahl()==null || request.auswahl().isEmpty() || request.auswahl().size()>500)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"1 bis 500 Monatsstände und eine gültige Konfigurationsversion auswählen.");
  Set<Referenz> unique=new HashSet<>();Set<YearMonth> months=new HashSet<>();
  for(var s:request.auswahl()) {
   if(s==null||s.mitarbeiterId()==null||s.mitarbeiterId()<=0||s.version()==null||s.version()<0||s.jahr()<1900||s.jahr()>9999||s.monat()<1||s.monat()>12)
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ein ausgewählter Mitarbeiter, Monat oder Versionsstand ist ungültig.");
   if(!unique.add(new Referenz(s.mitarbeiterId(),s.jahr(),s.monat()))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ein Monatsstand wurde mehrfach ausgewählt.");
   months.add(YearMonth.of(s.jahr(),s.monat()));
  }
  if(months.size()>12) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Höchstens 12 Monate pro Export auswählen.");
 }
 private static void hinweis(List<Hinweis> list,Referenz ref,String k,BigDecimal hours,String text) { list.add(new Hinweis(ref,k,hours,text)); }
 private static String dateiname(YearMonth m) { return "lodas-"+m+".txt"; }
 private static ResponseStatusException konflikt() { return new ResponseStatusException(HttpStatus.CONFLICT,"Ein Monatsstand oder die DATEV-Einstellungen wurden geändert. Bitte neu laden und erneut prüfen."); }
}
