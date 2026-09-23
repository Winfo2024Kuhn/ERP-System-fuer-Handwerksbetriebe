package org.example.kalkulationsprogramm.service;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import java.util.UUID;import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.EinkaufVersandAngenommen;import org.example.kalkulationsprogramm.service.einkauf.*;import org.junit.jupiter.api.Test;
class EinkaufBestellVersandListenerTest {
 @Test void verarbeitetNurDauerhaftAngenommeneBestellereignisse(){var service=mock(EinkaufBestellfreigabeService.class);var listener=new EinkaufBestellVersandListener(service);var event=new EinkaufVersandAngenommen(UUID.randomUUID(),31L,"BESTELLUNG",7L,9L,null,java.time.Instant.now());assertEquals(java.util.Set.of("BESTELLUNG"),listener.unterstuetzteVorgangstypen());listener.verarbeite(event);verify(service).versandAngenommen(event);}
}
