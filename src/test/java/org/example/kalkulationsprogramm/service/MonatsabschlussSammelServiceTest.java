package org.example.kalkulationsprogramm.service;
import jakarta.persistence.*;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class MonatsabschlussSammelServiceTest {
    final MonatsSaldoService saldo=mock(MonatsSaldoService.class);final MonatsSaldoRepository repo=mock(MonatsSaldoRepository.class);final EntityManager em=mock(EntityManager.class);final PlatformTransactionManager tm=mock(PlatformTransactionManager.class);final MonatsabschlussBerechtigungService recht=mock(MonatsabschlussBerechtigungService.class);
    final MonatsabschlussSammelService service=new MonatsabschlussSammelService(recht,saldo,repo,em,tm);
    @Test void teilergebnisseMitRollbackUndFortsetzung() {
        when(tm.getTransaction(any())).thenAnswer(i->{assertThat(((TransactionDefinition)i.getArgument(0)).getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);return mock(TransactionStatus.class);});
        var m=new Mitarbeiter();when(em.find(eq(Mitarbeiter.class),anyLong(),eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(m);
        var closed=new MonatsSaldo();closed.setFestgeschrieben(true);when(repo.findGesperrt(2L,2025,1)).thenReturn(Optional.of(closed));
        when(saldo.abschliessenOhneVerlauf(3L,2025,1,null)).thenThrow(new IllegalStateException("secret SQL"));
        var result=service.abschliessen(new SammelRequest(List.of(new Referenz(1L,2025,1),new Referenz(2L,2025,1),new Referenz(3L,2025,1),new Referenz(4L,2025,1))),null);
        assertThat(result.ergebnisse()).extracting(Einzelergebnis::status).containsExactly("ABGESCHLOSSEN","BEREITS_ABGESCHLOSSEN","FEHLGESCHLAGEN","ABGESCHLOSSEN");verify(tm).rollback(any());verify(tm,times(3)).commit(any());verify(saldo,never()).abschliessenOhneVerlauf(2L,2025,1,null);assertThat(result.ergebnisse().get(2).meldung()).doesNotContain("secret");
    }
    @Test void gesamtrequestWirdVorErstemSchreibenValidiert() {
        var r=new Referenz(1L,2025,1);
        for(var request:List.of(new SammelRequest(List.of()),new SammelRequest(List.of(r,r)),new SammelRequest(Collections.nCopies(501,r)),new SammelRequest(List.of(new Referenz(0L,2025,1))),new SammelRequest(List.of(new Referenz(1L,2025,13))),new SammelRequest(List.of(new Referenz(1L,9999,1))))) assertThatThrownBy(()->service.abschliessen(request,null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(tm,saldo);
    }
}
