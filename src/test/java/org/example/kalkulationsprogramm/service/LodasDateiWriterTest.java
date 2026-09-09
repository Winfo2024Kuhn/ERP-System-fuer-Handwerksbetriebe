package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.dto.DatevDto.Konfiguration;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class LodasDateiWriterTest {
 final Konfiguration config = new Konfiguration(0L,"LODAS","1234","123",List.of(),List.of());
 @Test void goldenBytesMitCrLfOhneBomUndNumerischerSortierung() throws Exception {
  var writer=new LodasDateiWriter();
  byte[] result=writer.schreiben(config,2026,1,List.of(
   new LodasDateiWriter.Buchung("14","200",new BigDecimal("80.25")),
   new LodasDateiWriter.Buchung("2","0200",new BigDecimal("7.70")),
   new LodasDateiWriter.Buchung("14","0200",new BigDecimal("19.75")),
   new LodasDateiWriter.Buchung("2","201",BigDecimal.ZERO)));
  try(var fixture=getClass().getResourceAsStream("/datev/lodas-stunden-erwartet.txt")) {
   assertThat(result).isEqualTo(fixture.readAllBytes());
  }
  assertThat(new String(result,StandardCharsets.US_ASCII)).doesNotContain("\uFEFF");
 }
 @Test void keineRundungUebergrosserNegativerOderInjizierterWerte() {
  var writer=new LodasDateiWriter();
  for(String hours:List.of("-1","1000000000.00","1.001"))
   assertThatThrownBy(()->writer.schreiben(config,2026,1,List.of(new LodasDateiWriter.Buchung("1","200",new BigDecimal(hours)))))
    .isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->writer.schreiben(config,2026,1,List.of(new LodasDateiWriter.Buchung("../1","200",BigDecimal.ONE))))
   .isInstanceOf(IllegalArgumentException.class);
 }
 @Test void aggregierteGrenzeUndLeereDateiVerhindert() {
  var writer=new LodasDateiWriter();
  assertThatThrownBy(()->writer.schreiben(config,2026,2,List.of(new LodasDateiWriter.Buchung("1","200",new BigDecimal("600000000")),new LodasDateiWriter.Buchung("01","0200",new BigDecimal("600000000")))))
   .isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->writer.schreiben(config,2026,1,List.of())).isInstanceOf(IllegalArgumentException.class);
 }
}
