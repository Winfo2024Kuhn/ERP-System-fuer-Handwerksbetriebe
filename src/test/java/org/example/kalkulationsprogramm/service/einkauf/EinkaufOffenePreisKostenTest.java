package org.example.kalkulationsprogramm.service.einkauf;

import org.example.kalkulationsprogramm.domain.einkauf.BestellungPosition;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBelegPosition;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EinkaufOffenePreisKostenTest {
    @Test void offenerBestellpreisErzeugtKeineErfundeneNullsumme() {
        var line = mock(BestellungPosition.class);
        var bill = mock(EinkaufBelegPosition.class);
        when(bill.getBestellPosition()).thenReturn(line);
        when(bill.getEinzelpreis()).thenReturn(new BigDecimal("12.50"));
        var result = EinkaufBelegKostenRechner.berechne(List.of(bill), Map.of(), List.of(), new HashSet<>());
        assertFalse(result.vollstaendig());
        assertNull(result.vereinbart());
        assertNull(result.abgerechnet());
    }
}
