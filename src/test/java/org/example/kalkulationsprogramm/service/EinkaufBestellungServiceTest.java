package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;
import java.math.BigDecimal; import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.repository.*; import org.example.kalkulationsprogramm.service.einkauf.*;
import org.example.kalkulationsprogramm.service.DokumentnummerService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EinkaufBestellungServiceTest {
 @Test void direktBlockiertOhneBelegtenAktuellenPreis(){
  var orders=mock(EinkaufBestellungRepository.class);var offers=mock(AngebotVersionRepository.class);var needs=mock(EinkaufBedarfRepository.class);
  var need=mock(EinkaufBedarf.class);when(need.getId()).thenReturn(11L);when(need.getVersion()).thenReturn(2L);when(need.getPosition()).thenReturn(new PositionSnapshot(Positionsart.ARTIKEL,21L,"A-21",null,null,"Blech",null,null,null,null,null,null,null,null,List.of(),List.of()));when(needs.findeAlleFuerUpdate(List.of(11L))).thenReturn(List.of(need));
  var service=new EinkaufBestellungService(orders,mock(BestellungRevisionRepository.class),offers,mock(EinkaufVergleichService.class),needs,mock(EinkaufMengenService.class),mock(DokumentnummerService.class),mock(EinkaufAuditService.class),new ObjectMapper(),mock(LieferantenArtikelPreiseRepository.class));
  Direkt direct=new Direkt(7L,new Snapshot(7L,8L,"Muster Stahl","test@example.com","Testkontakt","Herr",null),List.of(new Herkunft(11L,2,new BigDecimal("4"))),List.of(),null,null,"Lieferung frei Haus",UUID.randomUUID());
  assertThrows(ResponseStatusException.class,()->service.direkt(direct,9L));
  verify(orders,never()).saveAndFlush(any());
 }
 @Test void angebotImEntwurfKannNichtZurBestellungWerden(){
  var orders=mock(EinkaufBestellungRepository.class);var offers=mock(AngebotVersionRepository.class);var version=mock(AngebotVersion.class);when(offers.findById(90L)).thenReturn(Optional.of(version));when(version.getStatus()).thenReturn("ENTWURF");
  var service=new EinkaufBestellungService(orders,mock(BestellungRevisionRepository.class),offers,mock(EinkaufVergleichService.class),mock(EinkaufBedarfRepository.class),mock(EinkaufMengenService.class),mock(DokumentnummerService.class),mock(EinkaufAuditService.class),new ObjectMapper(),mock(LieferantenArtikelPreiseRepository.class));
  AusAngebot request=new AusAngebot(90L,List.of(new Herkunft(11L,2,new BigDecimal("4"))),"geprüft",UUID.randomUUID());
  assertThrows(ResponseStatusException.class,()->service.ausAngebot(request,9L));verifyNoInteractions(orders);
 }

 @Test void entwurfKannNichtAlsVersandteBestellrevisionGeaendertWerden(){
  var orders=mock(EinkaufBestellungRepository.class);var order=mock(EinkaufBestellung.class);var amounts=mock(EinkaufMengenService.class);
  when(orders.findeFuerUpdate(15L)).thenReturn(Optional.of(order));when(order.getVersion()).thenReturn(1L);
  when(order.getStatus()).thenReturn(BestellungStatus.ENTWURF);
  var service=new EinkaufBestellungService(orders,mock(BestellungRevisionRepository.class),mock(AngebotVersionRepository.class),mock(EinkaufVergleichService.class),mock(EinkaufBedarfRepository.class),amounts,mock(DokumentnummerService.class),mock(EinkaufAuditService.class),new ObjectMapper(),mock(LieferantenArtikelPreiseRepository.class));
  var content=new Direkt(7L,null,List.of(),List.of(),null,null,null,UUID.randomUUID());
  assertThrows(ResponseStatusException.class,()->service.aendern(15L,new Aenderung(1,content,"Grund"),9L));
  verifyNoInteractions(amounts);
 }
}
