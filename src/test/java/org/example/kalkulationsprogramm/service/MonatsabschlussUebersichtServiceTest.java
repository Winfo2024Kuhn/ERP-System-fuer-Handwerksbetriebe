package org.example.kalkulationsprogramm.service;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class MonatsabschlussUebersichtServiceTest {
    final MonatsabschlussUebersichtRepository repo=mock(MonatsabschlussUebersichtRepository.class);
    final MonatsSaldoService saldo=mock(MonatsSaldoService.class);
    final MonatsabschlussBerechtigungService recht=mock(MonatsabschlussBerechtigungService.class);
    final MonatsabschlussUebersichtService service=new MonatsabschlussUebersichtService(repo,saldo,recht);
    MonatsabschlussUebersichtRepository.Person person(long id) {
        return new MonatsabschlussUebersichtRepository.Person() { public Long getId(){return id;} public String getVorname(){return "Max";} public String getNachname(){return "Mustermann";} };
    }
    MonatsSaldo stand(long id,int jahr,int monat,boolean geschlossen) {
        var s=new MonatsSaldo();var m=new Mitarbeiter();m.setId(id);s.setMitarbeiter(m);s.setJahr(jahr);s.setMonat(monat);s.setFestgeschrieben(geschlossen);s.setIstStunden(new BigDecimal("7.7"));s.setVersion(2L);return s;
    }
    @Test void summenUndAuswahlUmfassenAlleSeitenUndHistorieOhneLiveOderAudit() {
        var people=new ArrayList<MonatsabschlussUebersichtRepository.Person>();var rows=new ArrayList<MonatsSaldo>();
        for(long i=1;i<=51;i++){people.add(person(i));rows.add(stand(i,2025,12,true));}
        when(repo.personen(any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),any())).thenReturn(people);when(repo.salden(anyList(),eq(2025*12+12),eq(2025*12+12))).thenReturn(rows);
        var result=service.lade(new Filter(2025,12,null,null,"ALLE",0,50),null);
        assertThat(result.items()).hasSize(50);assertThat(result.totalElements()).isEqualTo(51);assertThat(result.auswahl()).hasSize(51);assertThat(result.summen().istStunden()).isEqualByComparingTo("392.7");verifyNoInteractions(saldo);
    }
    @Test void offeneInvalidierteCachesWerdenBerechnetStatusDanachGefiltert() {
        when(repo.personen(eq(1L),eq(2L),any(),any(),any(),any(),anyInt(),anyInt(),any())).thenReturn(List.of(person(1)));
        var stale=stand(1,2025,1,false);stale.setGueltig(false);when(repo.salden(anyList(),anyInt(),anyInt())).thenReturn(List.of(stale));when(saldo.getOrBerechne(1L,2025,1)).thenReturn(stand(1,2025,1,true));
        assertThat(service.lade(new Filter(2025,1,1L,2L,"OFFEN",0,50),null).totalElements()).isZero();verify(saldo).getOrBerechne(1L,2025,1);
    }
    @Test void sechsMonateUeberJahresgrenzeEinSetAbruf() {
        when(repo.personen(any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),any())).thenReturn(List.of(person(1)));var rows=new ArrayList<MonatsSaldo>();for(int i=8;i<=12;i++)rows.add(stand(1,2024,i,true));rows.add(stand(1,2025,1,true));when(repo.salden(anyList(),eq(2024*12+8),eq(2025*12+1))).thenReturn(rows);
        var result=service.vergleich(2025,1,null,null,null);assertThat(result).hasSize(6);assertThat(result.getFirst().jahr()).isEqualTo(2024);assertThat(result.getFirst().monat()).isEqualTo(8);assertThat(result).allSatisfy(x->assertThat(x.abgeschlossen()).isEqualTo(1));verifyNoInteractions(saldo);
    }
    @Test void grenzenUndRechteVorDatenzugriff() {
        for(String status:List.of("<script>","'; DROP TABLE x; --","x".repeat(10001))) assertThatThrownBy(()->service.lade(new Filter(2025,1,null,null,status,0,50),null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->service.lade(new Filter(2025,13,null,null,"ALLE",0,50),null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(repo.personen(any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),any())).thenReturn(Collections.nCopies(501,person(1)));assertThatThrownBy(()->service.lade(new Filter(2025,1,null,null,"ALLE",0,50),null)).hasMessageContaining("500");
        doThrow(new org.springframework.security.access.AccessDeniedException("Nein")).when(recht).verlangeAkteur(null);assertThatThrownBy(()->service.vergleich(2025,1,null,null,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void auswahlEnthaeltEchtenAbschlussstatusAuchJenseitsDerSeite() {
        var geschlossen=stand(1,2025,1,true);geschlossen.setVersion(3L);
        var offen=stand(2,2025,1,false);offen.setVersion(3L);offen.setGueltig(true);
        when(repo.personen(any(),any(),any(),any(),any(),any(),anyInt(),anyInt(),any())).thenReturn(List.of(person(1),person(2)));
        when(repo.salden(anyList(),anyInt(),anyInt())).thenReturn(List.of(geschlossen,offen));
        var result=service.lade(new Filter(2025,1,null,null,"ALLE",0,1),null);
        assertThat(result.items()).hasSize(1);
        var json=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result.auswahl());
        assertThat(json.get(0).path("festgeschrieben").asBoolean()).isTrue();
        assertThat(json.get(1).path("festgeschrieben").isBoolean()).isTrue();
        assertThat(json.get(1).path("festgeschrieben").asBoolean()).isFalse();
        assertThat(result.auswahl()).allSatisfy(s->assertThat(s.version()).isEqualTo(3L));
        verifyNoInteractions(saldo);
    }
}
