package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.ZeitkontoWechselDto;
import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import({ZeitkontoService.class, ZeitkontenmodellService.class, ZeitkontoPausenIntegrationTest.ValidationConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ZeitkontoPausenIntegrationTest {
    @Autowired ZeitkontoService service;
    @Autowired ZeitkontenmodellService katalog;
    @Autowired MitarbeiterRepository mitarbeiterRepository;
    @Autowired ZeitkontoVersionRepository versionRepository;
    @Autowired ZeitkontoPauseRepository pauseRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @MockBean TagesSollService tagesSollService;
    @MockBean MonatsSaldoRepository monatsSaldoRepository;
    @TestConfiguration static class ValidationConfig {
        @Bean Validator validator() { return new LocalValidatorFactoryBean(); }
    }
    ZeitkontenmodellDto.Arbeitszeit arbeitszeit() {
        return new ZeitkontenmodellDto.Arbeitszeit(new BigDecimal("7"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }
    Mitarbeiter mitarbeiter() {
        Mitarbeiter m = new Mitarbeiter(); m.setVorname("Max"); m.setNachname("Mustermann");
        return mitarbeiterRepository.saveAndFlush(m);
    }

    @Test void zweiGleichzeitigeErstzuweisungenErzeugenGenauEineVersionUndEinen409() throws Exception {
        Mitarbeiter m = mitarbeiter();
        var request = new ZeitkontoWechselDto(LocalDate.now().minusDays(2), m.getVersion(), null, null, null, null, arbeitszeit());
        CountDownLatch bereit = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> zuweisen = () -> {
                bereit.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS));
                try { service.zuweisen(m.getId(), request); return 201; }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
            };
            var a = pool.submit(zuweisen); var b = pool.submit(zuweisen);
            assertTrue(bereit.await(5, TimeUnit.SECONDS)); start.countDown();
            var codes = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertTrue(codes.contains(201)); assertTrue(codes.contains(409));
        }
        assertEquals(1, service.versionenImZeitraum(m.getId(), LocalDate.now().minusYears(1), LocalDate.now()).size());
    }

    @Test void ausschaltenSpeichertPauseUndLiefertHistorischeVersionTrotzFalse() {
        Mitarbeiter m = mitarbeiter(); LocalDate heute = LocalDate.now();
        var version = service.zuweisen(m.getId(), new ZeitkontoWechselDto(heute.minusDays(2), m.getVersion(),
                null, null, null, null, arbeitszeit()));
        service.ausschalten(m.getId(), new ZeitkontoWechselDto.Ausschalten(m.getVersion(), version.id(), version.version()));
        assertFalse(mitarbeiterRepository.findById(m.getId()).orElseThrow().getFuehrtZeitkonto());
        assertTrue(service.versionAm(m.getId(), heute).isEmpty());
        assertEquals(heute.minusDays(1), service.versionAm(m.getId(), heute.minusDays(1)).orElseThrow().getGueltigBis());
        var pausen = pauseRepository.findByMitarbeiterIdOrderByGueltigVonAsc(m.getId());
        assertEquals(1, pausen.size()); assertEquals(heute, pausen.getFirst().getGueltigVon());
        assertNull(pausen.getFirst().getGueltigBis());
    }

    @Test void fehlgeschlageneZuweisungRolltVorversionsaenderungZurueck() {
        Mitarbeiter m = mitarbeiter(); LocalDate heute = LocalDate.now();
        var version = service.zuweisen(m.getId(), new ZeitkontoWechselDto(heute.minusDays(2), m.getVersion(),
                null, null, null, null, arbeitszeit()));
        org.mockito.Mockito.doThrow(new IllegalStateException("Testfehler beim Invalidieren"))
                .when(monatsSaldoRepository).invalidiereAlle(m.getId());
        assertThrows(IllegalStateException.class, () -> service.zuweisen(m.getId(), new ZeitkontoWechselDto(heute,
                m.getVersion(), version.id(), version.version(), null, null, arbeitszeit())));
        var versionen = service.versionenImZeitraum(m.getId(), heute.minusDays(2), heute);
        assertEquals(1, versionen.size()); assertNull(versionen.getFirst().getGueltigBis());
    }

    @Test void wechselInOderVorAbgeschlossenemMonatWirdAbgelehntOffenerSpaetererWechselBleibtMoeglich() {
        Mitarbeiter m = mitarbeiter(); LocalDate heute = LocalDate.now();
        LocalDate geschlossen = heute.minusMonths(2).withDayOfMonth(1);
        var version = service.zuweisen(m.getId(), new ZeitkontoWechselDto(geschlossen.minusMonths(1), m.getVersion(),
                null, null, null, null, arbeitszeit()));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var saldo = new org.example.kalkulationsprogramm.domain.MonatsSaldo();
            saldo.setMitarbeiter(entityManager.find(Mitarbeiter.class, m.getId()));
            saldo.setJahr(geschlossen.getYear()); saldo.setMonat(geschlossen.getMonthValue());
            saldo.setFestgeschrieben(true); saldo.setGueltig(false); saldo.setSollStunden(new BigDecimal("123.45"));
            entityManager.persist(saldo);
        });
        for (LocalDate start : List.of(geschlossen.minusDays(1), geschlossen.plusDays(3))) {
            var ex = assertThrows(ResponseStatusException.class, () -> service.zuweisen(m.getId(),
                    new ZeitkontoWechselDto(start, m.getVersion(), version.id(), version.version(), null, null, arbeitszeit())));
            assertEquals(409, ex.getStatusCode().value());
        }
        service.zuweisen(m.getId(), new ZeitkontoWechselDto(geschlossen.plusMonths(1), m.getVersion(),
                version.id(), version.version(), null, null, arbeitszeit()));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var saldo = entityManager.createQuery("SELECT m FROM MonatsSaldo m WHERE m.mitarbeiter.id = :id",
                    org.example.kalkulationsprogramm.domain.MonatsSaldo.class).setParameter("id", m.getId()).getSingleResult();
            assertTrue(saldo.getFestgeschrieben()); assertEquals(new BigDecimal("123.45"), saldo.getSollStunden());
        });
    }

    @Test void katalogKopieBleibtNachAenderungEigenstaendigUndHistorischeHerkunftSperrtLoeschung() {
        Mitarbeiter m = mitarbeiter(); LocalDate gestern = LocalDate.now().minusDays(1);
        var vorlage = katalog.erstellen(new ZeitkontenmodellDto.Create("Werkstatt", arbeitszeit()));
        var version = service.zuweisen(m.getId(), new ZeitkontoWechselDto(gestern, m.getVersion(),
                null, null, vorlage.id(), vorlage.version(), null));
        var geaendert = katalog.aktualisieren(vorlage.id(),
                new ZeitkontenmodellDto.Update(vorlage.version(), "Büro", new ZeitkontenmodellDto.Arbeitszeit(
                        new BigDecimal("6"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null)));
        assertTrue(geaendert.version() > vorlage.version());
        assertEquals(new BigDecimal("7.00"), service.versionAm(m.getId(), gestern).orElseThrow().getMontagStunden());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> katalog.aktualisieren(vorlage.id(), new ZeitkontenmodellDto.Update(vorlage.version(), "Alt", arbeitszeit())))
                .getStatusCode().value());
        service.ausschalten(m.getId(), new ZeitkontoWechselDto.Ausschalten(m.getVersion(), version.id(), version.version()));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> katalog.loeschen(vorlage.id(), geaendert.version())).getStatusCode().value());
    }
}
