package org.example.kalkulationsprogramm.repository;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false)
class ZeitkontoDatenfundamentTest {
    @Autowired ZeitkontoVersionRepository versionen;
    @Autowired ZeitkontenmodellRepository vorlagen;
    @Autowired MonatsabschlussAuditRepository audit;
    @Autowired MitarbeiterRepository mitarbeiterRepository;
    @Autowired EntityManager em;

    @Test
    void inklusiveGrenzenOffeneVersionSortierungUndMitarbeiterTrennung() {
        Mitarbeiter m = mitarbeiter();
        ZeitkontoVersion alt = version(m, "2026-01-01", "2026-06-30");
        ZeitkontoVersion aktuell = version(m, "2026-07-01", null);
        version(mitarbeiter(), "2026-01-01", null);
        em.clear();
        assertThat(versionen.findAm(m.getId(), LocalDate.of(2025, 12, 31))).isEmpty();
        assertThat(versionen.findAm(m.getId(), LocalDate.of(2026, 1, 1))).get()
                .extracting(ZeitkontoVersion::getId).isEqualTo(alt.getId());
        assertThat(versionen.findAm(m.getId(), LocalDate.of(2026, 6, 30))).get()
                .extracting(ZeitkontoVersion::getId).isEqualTo(alt.getId());
        assertThat(versionen.findAm(m.getId(), LocalDate.of(2030, 1, 1))).get()
                .extracting(ZeitkontoVersion::getId).isEqualTo(aktuell.getId());
        assertThat(versionen.findImZeitraum(m.getId(), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 1)))
                .extracting(ZeitkontoVersion::getId).containsExactly(alt.getId(), aktuell.getId());
        assertThat(versionen.findImZeitraum(m.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))).isEmpty();
        assertThat(versionen.findAm(-1L, LocalDate.of(2026, 1, 1))).isEmpty();
    }

    @Test
    void vorlagenAenderungLaesstVersionsStundenUndZeitfensterUnveraendert() {
        Zeitkontenmodell vorlage = new Zeitkontenmodell();
        vorlage.setBezeichnung("Test-Werkstatt");
        vorlage.setMontagStunden(new BigDecimal("7.25"));
        vorlage.setBuchungStartZeit(LocalTime.of(6, 0));
        vorlagen.saveAndFlush(vorlage);
        ZeitkontoVersion v = version(mitarbeiter(), "2026-01-01", null);
        v.setVorlage(vorlage);
        v.setMontagStunden(vorlage.getMontagStunden());
        v.setBuchungStartZeit(vorlage.getBuchungStartZeit());
        versionen.flush();
        vorlage.setMontagStunden(new BigDecimal("4.00"));
        vorlage.setBuchungStartZeit(LocalTime.of(8, 0));
        vorlagen.flush();
        em.clear();
        ZeitkontoVersion geladen = versionen.findAm(v.getMitarbeiter().getId(), LocalDate.of(2026, 1, 1)).orElseThrow();
        assertThat(geladen.getWochenstunden()).isEqualByComparingTo("7.25");
        assertThat(geladen.getBuchungStartZeit()).isEqualTo(LocalTime.of(6, 0));
        assertThat(geladen.getVorlage().getMontagStunden()).isEqualByComparingTo("4.00");
    }

    @Test
    void tageSummenNullWerteUndDefaults() {
        ZeitkontoVersion v = new ZeitkontoVersion();
        Zeitkontenmodell modell = new Zeitkontenmodell();
        v.setMontagStunden(new BigDecimal("1.25")); modell.setMontagStunden(new BigDecimal("1.25"));
        v.setDienstagStunden(new BigDecimal("2.00")); modell.setDienstagStunden(new BigDecimal("2.00"));
        v.setMittwochStunden(new BigDecimal("3.00")); modell.setMittwochStunden(new BigDecimal("3.00"));
        v.setDonnerstagStunden(new BigDecimal("4.00")); modell.setDonnerstagStunden(new BigDecimal("4.00"));
        v.setFreitagStunden(new BigDecimal("5.00")); modell.setFreitagStunden(new BigDecimal("5.00"));
        v.setSamstagStunden(new BigDecimal("6.00")); modell.setSamstagStunden(new BigDecimal("6.00"));
        v.setSonntagStunden(new BigDecimal("7.00")); modell.setSonntagStunden(new BigDecimal("7.00"));
        for (int tag = 1; tag <= 7; tag++) {
            assertThat(v.getSollstundenFuerTag(tag)).isEqualByComparingTo(tag == 1 ? "1.25" : tag + ".00");
            assertThat(modell.getSollstundenFuerTag(tag)).isEqualByComparingTo(v.getSollstundenFuerTag(tag));
        }
        assertThat(v.getWochenstunden()).isEqualByComparingTo("28.25");
        assertThat(modell.getWochenstunden()).isEqualByComparingTo("28.25");
        v.setSonntagStunden(null); modell.setSonntagStunden(null);
        assertThat(v.getWochenstunden()).isEqualByComparingTo("21.25");
        assertThat(modell.getWochenstunden()).isEqualByComparingTo("21.25");
        assertThat(v.getSollstundenFuerTag(0)).isEqualByComparingTo("0");
        assertThat(modell.getSollstundenFuerTag(8)).isEqualByComparingTo("0");
        assertThat(new Mitarbeiter().getArt()).isEqualTo(MitarbeiterArt.MENSCH);
        assertThat(new Mitarbeiter().getFuehrtZeitkonto()).isTrue();
        assertThat(new Abteilung().getDarfMonatAbschliessen()).isFalse();
        assertThat(new MonatsSaldo().getFestgeschrieben()).isFalse();
    }

    @Test
    void alleDreiVersionenSteigenBeiAenderungUndVeralteteVersionWirdAbgewiesen() {
        Zeitkontenmodell modell = new Zeitkontenmodell(); modell.setBezeichnung("Test");
        vorlagen.saveAndFlush(modell);
        ZeitkontoVersion v = version(mitarbeiter(), "2026-01-01", null);
        MonatsSaldo saldo = new MonatsSaldo(); saldo.setMitarbeiter(v.getMitarbeiter());
        saldo.setJahr(2026); saldo.setMonat(1); em.persist(saldo); em.flush();
        Long mv = modell.getVersion(), vv = v.getVersion(), sv = saldo.getVersion();
        modell.setBezeichnung("Test geändert"); v.setMontagStunden(BigDecimal.ONE);
        saldo.setFestgeschrieben(true); em.flush();
        assertThat(modell.getVersion()).isGreaterThan(mv);
        assertThat(v.getVersion()).isGreaterThan(vv);
        assertThat(saldo.getVersion()).isGreaterThan(sv);
        em.detach(v);
        ZeitkontoVersion neu = versionen.findById(v.getId()).orElseThrow();
        neu.setDienstagStunden(BigDecimal.ONE); em.flush();
        v.setFreitagStunden(BigDecimal.TEN);
        assertThatThrownBy(() -> versionen.saveAndFlush(v))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void wiederholtesOeffnenBleibtMitAkteurChronologischErhalten() {
        Mitarbeiter m = mitarbeiter(); Mitarbeiter akteur = mitarbeiter();
        LocalDateTime jetzt = LocalDateTime.of(2026, 9, 1, 12, 0);
        for (MonatsabschlussAudit.Aktion aktion : new MonatsabschlussAudit.Aktion[] {
                MonatsabschlussAudit.Aktion.ABSCHLIESSEN, MonatsabschlussAudit.Aktion.OEFFNEN,
                MonatsabschlussAudit.Aktion.ABSCHLIESSEN, MonatsabschlussAudit.Aktion.OEFFNEN}) {
            audit.save(new MonatsabschlussAudit(m, 2026, 8, aktion, akteur, jetzt));
        }
        em.flush(); em.clear();
        var historie = audit.findByMitarbeiterIdAndJahrAndMonatOrderByZeitpunktAscIdAsc(m.getId(), 2026, 8);
        assertThat(historie).extracting(MonatsabschlussAudit::getAktion).containsExactly(
                MonatsabschlussAudit.Aktion.ABSCHLIESSEN, MonatsabschlussAudit.Aktion.OEFFNEN,
                MonatsabschlussAudit.Aktion.ABSCHLIESSEN, MonatsabschlussAudit.Aktion.OEFFNEN);
        assertThat(historie).allSatisfy(e -> assertThat(e.getAkteur().getId()).isEqualTo(akteur.getId()));
        assertThat(audit.findByMitarbeiterIdAndJahrAndMonatOrderByZeitpunktAscIdAsc(akteur.getId(), 2026, 8)).isEmpty();
        assertThatThrownBy(() -> em.remove(historie.getFirst())).isInstanceOf(IllegalStateException.class);
    }

    private Mitarbeiter mitarbeiter() {
        Mitarbeiter m = new Mitarbeiter(); m.setVorname("Max"); m.setNachname("Mustermann");
        return mitarbeiterRepository.saveAndFlush(m);
    }

    private ZeitkontoVersion version(Mitarbeiter m, String von, String bis) {
        ZeitkontoVersion v = new ZeitkontoVersion(); v.setMitarbeiter(m);
        v.setGueltigVon(LocalDate.parse(von)); v.setGueltigBis(bis == null ? null : LocalDate.parse(bis));
        return versionen.saveAndFlush(v);
    }
}
