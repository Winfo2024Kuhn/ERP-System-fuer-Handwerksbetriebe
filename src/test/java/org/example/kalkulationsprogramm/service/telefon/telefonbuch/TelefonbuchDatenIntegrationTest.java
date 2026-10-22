package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.domain.SteuerberaterAnsprechpartner;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.domain.TelefonbuchQuelle;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchAbgleichErgebnisDto;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.example.kalkulationsprogramm.repository.TelefonbuchZuordnungRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.telefon.TelefonEinstellungenService;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchSollListe.Schluessel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Telefonbuch-Abgleich auf H2 mit echten Abfragen und Einstellungen: welche Kontakte
 * ins Telefonbuch kommen, dass Anonymisieren sie beim nächsten Abgleich entfernt und
 * dass Änderungen erst nach dem Commit (nie bei Rollback) einen Abgleich anstoßen.
 * Nur Dummy-Daten.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TelefonbuchSollListe.class, TelefonbuchAbgleichService.class, TelefonbuchEinstellungen.class,
        SystemSettingsService.class, TelefonbuchAenderungsBeobachter.class,
        TelefonbuchDatenIntegrationTest.Konfiguration.class})
class TelefonbuchDatenIntegrationTest {

    static final Instant JETZT = Instant.parse("2026-10-21T08:00:00Z");
    static final LocalDate HEUTE = LocalDate.of(2026, 10, 21);

    @TestConfiguration
    static class Konfiguration {
        @Bean
        Clock clock() {
            return Clock.fixed(JETZT, ZoneId.of("Europe/Berlin"));
        }

        @Bean
        FakeTelefonbuchAnlage telefonbuchAnlage() {
            return new FakeTelefonbuchAnlage();
        }
    }

    @MockBean TelefonEinstellungenService telefon;
    @MockBean TelefonbuchAbgleichPlaner planer;

    @Autowired TelefonbuchSollListe sollListe;
    @Autowired TelefonbuchAbgleichService abgleich;
    @Autowired TelefonbuchEinstellungen einstellungen;
    @Autowired FakeTelefonbuchAnlage box;
    @Autowired KundeRepository kunden;
    @Autowired LieferantenRepository lieferanten;
    @Autowired MitarbeiterRepository mitarbeiter;
    @Autowired SteuerberaterKontaktRepository steuerberater;
    @Autowired KontaktRufnummerRepository zusatznummern;
    @Autowired TelefonbuchZuordnungRepository zuordnungen;
    @Autowired SystemSettingRepository systemSettings;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        zusatznummern.deleteAll();
        kunden.deleteAll();
        lieferanten.deleteAll();
        mitarbeiter.deleteAll();
        steuerberater.deleteAll();
        zuordnungen.deleteAll();
        systemSettings.deleteAll();
        box.buecher.clear();
        box.eintraege.clear();
        when(telefon.istAktiv()).thenReturn(true);
        when(telefon.zugang()).thenReturn(Optional.of(new TelefonZugang("fritz.box", "erp", "pw")));
        when(telefon.landesvorwahl()).thenReturn("49");
        when(telefon.ortsvorwahl()).thenReturn("123");
        einstellungen.setzeAktiv(true);
        clearInvocations(planer);
    }

    private Kunde kunde(String nummer, String name, String telefonNr) {
        Kunde k = new Kunde();
        k.setKundennummer(nummer);
        k.setName(name);
        k.setAnrede(Anrede.HERR);
        k.setTelefon(telefonNr);
        return kunden.save(k);
    }

    private Lieferanten lieferant(String name, Boolean aktiv) {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname(name);
        l.setIstAktiv(aktiv);
        l.setTelefon("0123 555555");
        return lieferanten.save(l);
    }

    private Mitarbeiter mitarbeiter(String vorname, boolean aktiv, MitarbeiterArt art) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vorname);
        m.setNachname("Mustermann");
        m.setAktiv(aktiv);
        m.setArt(art);
        m.setTelefon("0170 1234567");
        m.setFestnetz("0123 444444");
        return mitarbeiter.save(m);
    }

    private SteuerberaterKontakt kanzlei(String name, boolean aktiv, LocalDate gueltigBis) {
        SteuerberaterKontakt s = new SteuerberaterKontakt();
        s.setName(name);
        s.setEmail(name.replace(' ', '.').toLowerCase() + "@example.com");
        s.setTelefon("0123 888888");
        s.setAktiv(aktiv);
        s.setGueltigBis(gueltigBis);
        SteuerberaterAnsprechpartner a = new SteuerberaterAnsprechpartner();
        a.setSteuerberater(s);
        a.setVorname("Erika");
        a.setNachname("Musterfrau");
        a.setTelefon("0123 888888-12");
        s.getAnsprechpartnerListe().add(a);
        return steuerberater.save(s);
    }

    private void zusatznummer(Kunde k, String nummer) {
        KontaktRufnummer r = new KontaktRufnummer();
        r.setKunde(k);
        r.setNummerRoh(nummer);
        r.setNummerNormalisiert(nummer);
        r.setAngelegtAm(LocalDateTime.of(2026, 10, 1, 9, 0));
        zusatznummern.save(r);
    }

    @Test
    @DisplayName("Soll-Liste: anonymisierte/gesperrte Kunden, inaktive Lieferanten/Mitarbeiter, System-Benutzer und abgelaufene Steuerberater fehlen")
    void filter() {
        Kunde max = kunde("K-1", "Max Mustermann", "0123 456789");
        Kunde anonym = kunde("K-2", "Gelöschter Kunde K-2", "0123 222222");
        anonym.setAnonymisiertAm(LocalDateTime.of(2026, 10, 1, 9, 0));
        kunden.save(anonym);
        zusatznummer(anonym, "0123 222223");
        Kunde gesperrt = kunde("K-3", "Erika Musterfrau", "0123 333333");
        gesperrt.setGesperrtAm(LocalDateTime.of(2026, 10, 1, 9, 0));
        kunden.save(gesperrt);
        Kunde nurZusatz = kunde("K-4", "Musterhaus", null);
        zusatznummer(nurZusatz, "0123 444445");
        kunde("K-5", "Ohne Nummer", null);
        Lieferanten aktiv = lieferant("Muster Handel", true);
        Lieferanten ohneAngabe = lieferant("Muster Stahl", null);
        lieferant("Alt Lieferant", false);
        Mitarbeiter m = mitarbeiter("Max", true, MitarbeiterArt.MENSCH);
        mitarbeiter("Ehemalig", false, MitarbeiterArt.MENSCH);
        mitarbeiter("Robot", true, MitarbeiterArt.SYSTEM);
        SteuerberaterKontakt k = kanzlei("Kanzlei Beispiel", true, HEUTE);
        kanzlei("Kanzlei Alt", true, HEUTE.minusDays(1));
        kanzlei("Kanzlei Inaktiv", false, null);

        Map<Schluessel, TelefonbuchEintrag> soll = sollListe.berechne();

        Long ansprechpartnerId = steuerberater.findAllMitAnsprechpartnernFuerAuswahl().stream()
                .filter(s -> s.getId().equals(k.getId())).findFirst().orElseThrow().getAnsprechpartnerListe().getFirst().getId();
        assertThat(soll).containsOnlyKeys(
                new Schluessel(TelefonbuchQuelle.KUNDE, max.getId()),
                new Schluessel(TelefonbuchQuelle.KUNDE, nurZusatz.getId()),
                new Schluessel(TelefonbuchQuelle.LIEFERANT, aktiv.getId()),
                new Schluessel(TelefonbuchQuelle.LIEFERANT, ohneAngabe.getId()),
                new Schluessel(TelefonbuchQuelle.MITARBEITER, m.getId()),
                new Schluessel(TelefonbuchQuelle.STEUERBERATER, k.getId()),
                new Schluessel(TelefonbuchQuelle.STEUERBERATER_ANSPRECHPARTNER, ansprechpartnerId));
        assertThat(soll.get(new Schluessel(TelefonbuchQuelle.MITARBEITER, m.getId())).name()).isEqualTo("Max Mustermann (Mitarbeiter)");
        assertThat(soll.get(new Schluessel(TelefonbuchQuelle.STEUERBERATER_ANSPRECHPARTNER, ansprechpartnerId)).name())
                .isEqualTo("Erika Musterfrau (Steuerberater Kanzlei Beispiel)");
    }

    @Test
    @DisplayName("Anonymisierter Kunde fliegt beim nächsten Abgleich aus dem Telefonbuch")
    void anonymisierenEntferntEintrag() {
        Kunde max = kunde("K-1", "Max Mustermann", "0123 456789");
        lieferant("Muster Handel", true);

        TelefonbuchAbgleichErgebnisDto erster = abgleich.abgleichen(false);
        String buch = einstellungen.telefonbuchId();
        assertThat(erster.neu()).isEqualTo(2);
        assertThat(box.namen(buch)).containsExactly("Max Mustermann (Kunde)", "Muster Handel (Lieferant)");
        assertThat(zuordnungen.count()).isEqualTo(2);

        max.setName("Gelöschter Kunde K-1");
        max.setAnonymisiertAm(LocalDateTime.of(2026, 10, 21, 9, 0));
        kunden.save(max);
        TelefonbuchAbgleichErgebnisDto zweiter = abgleich.abgleichen(false);

        assertThat(zweiter.geloescht()).isEqualTo(1);
        assertThat(box.namen(buch)).containsExactly("Muster Handel (Lieferant)");
        assertThat(zuordnungen.count()).isEqualTo(1);
        assertThat(einstellungen.anzahlEintraege()).isEqualTo(1);
    }

    @Test
    @DisplayName("Änderungen lösen nach dem Commit einen Abgleich aus – unwichtige Felder und Rollback nicht")
    void ausloesungNachCommit() {
        Kunde max = kunde("K-1", "Max Mustermann", "0123 456789");
        verify(planer).aenderungGemeldet();

        clearInvocations(planer);
        max = kunden.findById(max.getId()).orElseThrow();
        max.setStrasse("Musterweg 1");
        kunden.save(max);
        verify(planer, never()).aenderungGemeldet();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long id = max.getId();
        tx.executeWithoutResult(status -> {
            Kunde k = kunden.findById(id).orElseThrow();
            k.setTelefon("0123 999999");
            kunden.saveAndFlush(k);
            status.setRollbackOnly();
        });
        verify(planer, never()).aenderungGemeldet();

        tx.executeWithoutResult(status -> kunden.findById(id).orElseThrow().setTelefon("0123 999999"));
        verify(planer).aenderungGemeldet();

        clearInvocations(planer);
        kunden.deleteById(id);
        verify(planer).aenderungGemeldet();
    }
}
