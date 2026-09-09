package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import({ZeitkontoWechselService.class, ZeitkontoService.class, MonatsSaldoService.class,
        TagesSollService.class, ZeitkontoWechselIntegrationTest.ValidationConfig.class})
class ZeitkontoWechselIntegrationTest {
    @Autowired ZeitkontoWechselService wechsel;
    @Autowired MonatsSaldoService salden;
    @Autowired MitarbeiterRepository menschen;
    @Autowired ZeitkontoVersionRepository versionen;
    @Autowired MonatsSaldoRepository monate;
    @Autowired AbwesenheitRepository abwesenheiten;
    @Autowired EntityManager em;
    @MockBean FeiertagService feiertage;
    @MockBean MonatsabschlussBerechtigungService rechte;
    @TestConfiguration static class ValidationConfig {
        @Bean Validator validator() { return new LocalValidatorFactoryBean(); }
    }
    @Test void wechselRechnetOffenenCacheMitNeuerVersionUndBewahrtAbschlussSowieUrlaub() {
        YearMonth offen = YearMonth.now().minusMonths(1);
        YearMonth geschlossen = offen.minusMonths(1);
        Mitarbeiter m = new Mitarbeiter(); m.setVorname("Max"); m.setNachname("Mustermann");
        m.setEintrittsdatum(geschlossen.atDay(1)); menschen.saveAndFlush(m);
        ZeitkontoVersion alt = new ZeitkontoVersion(); alt.setMitarbeiter(m); alt.setGueltigVon(geschlossen.atDay(1));
        alt.setMontagStunden(new BigDecimal("8")); versionen.saveAndFlush(alt);
        Abwesenheit urlaub = new Abwesenheit(); urlaub.setMitarbeiter(m); urlaub.setDatum(offen.atDay(2));
        urlaub.setTyp(AbwesenheitsTyp.URLAUB); urlaub.setStunden(new BigDecimal("8")); abwesenheiten.saveAndFlush(urlaub);
        MonatsSaldo fest = salden.berechneOhneSpeichern(m.getId(), geschlossen.getYear(), geschlossen.getMonthValue());
        fest.setMitarbeiter(m); fest.setFestgeschrieben(true); monate.saveAndFlush(fest);
        BigDecimal festSoll = fest.getSollStunden(); Long festVersion = fest.getVersion();
        MonatsSaldo cache = salden.berechneOhneSpeichern(m.getId(), offen.getYear(), offen.getMonthValue());
        cache.setMitarbeiter(m); monate.saveAndFlush(cache);
        BigDecimal vorher = cache.getDifferenz();
        var stunden = new ZeitkontenmodellDto.Arbeitszeit(new BigDecimal("4"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        var request = new ZeitkontoWechselDto(offen.atDay(1), m.getVersion(), alt.getId(), alt.getVersion(), null, null, stunden);
        long anzahlVorher = versionen.count();
        var preview = wechsel.vorschau(m.getId(), request);
        assertFalse(preview.gespeichert()); assertEquals(anzahlVorher, versionen.count());
        var result = wechsel.uebernehmen(m.getId(), request);
        em.flush(); em.clear();
        var neuCache = monate.findByMitarbeiterIdAndJahrAndMonat(m.getId(), offen.getYear(), offen.getMonthValue()).orElseThrow();
        assertTrue(neuCache.getGueltig());
        assertTrue(neuCache.getDifferenz().compareTo(vorher) > 0);
        var geaendert = result.monate().stream().filter(x -> x.jahr() == offen.getYear() && x.monat() == offen.getMonthValue()).findFirst().orElseThrow();
        assertTrue(geaendert.geaendert()); assertEquals(0, geaendert.saldoNachher().compareTo(neuCache.getDifferenz()));
        var fix = monate.findByMitarbeiterIdAndJahrAndMonat(m.getId(), geschlossen.getYear(), geschlossen.getMonthValue()).orElseThrow();
        assertEquals(0, festSoll.compareTo(fix.getSollStunden())); assertEquals(festVersion, fix.getVersion());
        assertEquals(0, new BigDecimal("8").compareTo(abwesenheiten.findById(urlaub.getId()).orElseThrow().getStunden()));
        assertEquals(anzahlVorher + 1, versionen.count());
    }
}
