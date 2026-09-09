package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.dto.DatevDto.Konfiguration;
import org.springframework.stereotype.Component;
import java.math.*;
import java.nio.charset.Charset;
import java.time.YearMonth;
import java.util.*;

/** LODAS ASCII, Fach 3 Musterdatei / Fach 4 Tabelle 3.9; only numeric movement data. */
@Component
public class LodasDateiWriter {
 public record Buchung(String personalnummer, String lohnart, BigDecimal stunden) {}
 private record Schluessel(int personalnummer,int lohnart) implements Comparable<Schluessel> {
  public int compareTo(Schluessel other) { int n=Integer.compare(personalnummer,other.personalnummer);return n==0?Integer.compare(lohnart,other.lohnart):n; }
 }
 // NUM 11.2: nine integer and two fractional digits (separator does not count).
 public static final BigDecimal MAX_STUNDEN=new BigDecimal("999999999.99");
 public static boolean gueltigeStunden(BigDecimal value) {
  return value!=null && value.signum()>=0 && value.compareTo(MAX_STUNDEN)<=0 && value.stripTrailingZeros().scale()<=2;
 }
 public byte[] schreiben(Konfiguration config,int jahr,int monat,List<Buchung> buchungen) {
  if(config==null || !"LODAS".equals(config.ziel())) throw new IllegalArgumentException("Nur LODAS wird unterstützt.");
  nummer(config.beraterNr(),4,7);nummer(config.mandantenNr(),1,5);
  if(jahr<1900 || jahr>9999) throw new IllegalArgumentException("Ungültiges Exportjahr.");
  YearMonth.of(jahr,monat);
  var sums=new TreeMap<Schluessel,BigDecimal>();
  for(var b:buchungen) {
   if(b==null || !gueltigeStunden(b.stunden())) throw new IllegalArgumentException("Stunden müssen positiv oder 0 sein und höchstens zwei Nachkommastellen haben; DATEV-Feldgrenze überschritten.");
   var key=new Schluessel(nummer(b.personalnummer(),1,5),nummer(b.lohnart(),1,4));
   sums.merge(key,b.stunden(),BigDecimal::add);
  }
  sums.values().removeIf(v->v.signum()==0);
  if(sums.isEmpty()) throw new IllegalArgumentException("Für diesen Monat sind keine Stunden zum Exportieren vorhanden.");
  if(sums.values().stream().anyMatch(v->!gueltigeStunden(v))) throw new IllegalArgumentException("Die Stundensumme je Personalnummer und Lohnart überschreitet die DATEV-Feldgrenze.");
  StringBuilder text=new StringBuilder("[Allgemein]\r\nZiel=LODAS\r\nBeraterNr=").append(config.beraterNr())
   .append("\r\nMandantenNr=").append(config.mandantenNr())
   .append("\r\nDatumsformat=TT/MM/JJJJ\r\nFeldtrennzeichen=;\r\nZahlenkomma=,\r\n[Satzbeschreibung]\r\n")
   .append("3;u_lod_bwd_buchung_standard;abrechnung_zeitraum#bwd;bs_wert_butab#bwd;bs_nr#bwd;la_eigene#bwd;pnr#bwd;\r\n[Bewegungsdaten]\r\n");
  String datum=String.format(Locale.ROOT,"01/%02d/%04d",monat,jahr);
  sums.forEach((key,value)->text.append("3;").append(datum).append(';')
   .append(value.setScale(2,RoundingMode.UNNECESSARY).toPlainString().replace('.',','))
   .append(";01;").append(key.lohnart()).append(';').append(key.personalnummer()).append(";\r\n"));
  return text.toString().getBytes(Charset.forName("windows-1252"));
 }
 static int nummer(String value,int min,int max) {
  if(value==null || !value.matches("[0-9]{"+min+","+max+"}") || Integer.parseInt(value)==0)
   throw new IllegalArgumentException("Ungültige DATEV-Nummer.");
  return Integer.parseInt(value);
 }
}
