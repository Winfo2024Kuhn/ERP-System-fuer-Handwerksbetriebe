package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import org.example.kalkulationsprogramm.domain.TelefonbuchQuelle;
import org.example.kalkulationsprogramm.domain.TelefonbuchZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchAbgleichErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchStatusDto;
import org.example.kalkulationsprogramm.repository.TelefonbuchZuordnungRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.example.kalkulationsprogramm.service.telefon.TelefonEinstellungenService;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchEintrag.Nummer;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchEintrag.NummerArt;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchSollListe.Schluessel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Diff-Logik des Telefonbuch-Abgleichs mit einer Telefonanlage im Speicher.
 * Nur Dummy-Daten (Max Mustermann, Erika Musterfrau, 0123 456789).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelefonbuchAbgleichServiceTest {

    @Mock TelefonEinstellungenService telefon;
    @Mock SystemSettingsService settings;
    @Mock TelefonbuchSollListe sollListe;
    @Mock TelefonbuchZuordnungRepository repo;

    private final Map<String, String> gespeicherteSettings = new HashMap<>();
    private final Map<Long, TelefonbuchZuordnung> tabelle = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final Map<Schluessel, TelefonbuchEintrag> soll = new LinkedHashMap<>();
    private final FakeTelefonbuchAnlage box = new FakeTelefonbuchAnlage();
    private TelefonbuchAbgleichService service;

    private static final Schluessel MAX = new Schluessel(TelefonbuchQuelle.KUNDE, 1L);
    private static final Schluessel ERIKA = new Schluessel(TelefonbuchQuelle.LIEFERANT, 2L);
    private static final Schluessel MITARBEITER = new Schluessel(TelefonbuchQuelle.MITARBEITER, 3L);

    @BeforeEach
    void setUp() {
        when(settings.get(anyString(), any())).thenAnswer(i -> gespeicherteSettings.getOrDefault(i.getArgument(0), i.getArgument(1)));
        doAnswer(i -> gespeicherteSettings.put(i.getArgument(0), i.getArgument(1))).when(settings).save(anyString(), any(), any());
        when(repo.findAll()).thenAnswer(i -> new ArrayList<>(tabelle.values()));
        when(repo.count()).thenAnswer(i -> (long) tabelle.size());
        when(repo.save(any())).thenAnswer(i -> {
            TelefonbuchZuordnung z = i.getArgument(0);
            if (z.getId() == null) {
                z.setId(ids.incrementAndGet());
            }
            tabelle.put(z.getId(), z);
            return z;
        });
        doAnswer(i -> tabelle.remove(((TelefonbuchZuordnung) i.getArgument(0)).getId())).when(repo).delete(any());
        doAnswer(i -> {
            tabelle.clear();
            return null;
        }).when(repo).deleteAllInBatch();
        when(telefon.istAktiv()).thenReturn(true);
        when(telefon.zugang()).thenReturn(Optional.of(new TelefonZugang("fritz.box", "erp", "pw")));
        when(sollListe.berechne()).thenAnswer(i -> new LinkedHashMap<>(soll));

        TelefonbuchEinstellungen einstellungen = new TelefonbuchEinstellungen(settings);
        einstellungen.setzeAktiv(true);
        service = new TelefonbuchAbgleichService(box, telefon, einstellungen, sollListe, repo,
                Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneId.of("Europe/Berlin")));
        soll.put(MAX, eintrag("Max Mustermann (Kunde)", "0123456789"));
        soll.put(ERIKA, eintrag("Erika Musterfrau (Lieferant)", "0123456780"));
    }

    private static TelefonbuchEintrag eintrag(String name, String nummer) {
        return new TelefonbuchEintrag(name, List.of(new Nummer(nummer, NummerArt.GESCHAEFTLICH)));
    }

    private String buchId() {
        return gespeicherteSettings.get(TelefonbuchEinstellungen.TELEFONBUCH_ID);
    }

    @Test
    @DisplayName("Erster Lauf: Telefonbuch anlegen, alle Einträge neu, Stand gemerkt")
    void ersterLauf() {
        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(false);

        assertThat(e.erfolgreich()).isTrue();
        assertThat(e.neu()).isEqualTo(2);
        assertThat(box.namen(buchId())).containsExactly("Erika Musterfrau (Lieferant)", "Max Mustermann (Kunde)");
        assertThat(tabelle).hasSize(2);
        TelefonbuchStatusDto status = service.status();
        assertThat(status.anzahlEintraege()).isEqualTo(2);
        assertThat(status.zuletztAbgeglichen()).isEqualTo(LocalDateTime.of(2026, 10, 21, 10, 0));
        assertThat(status.letzterFehler()).isNull();
        assertThat(status.telefonbuchName()).isEqualTo("ERP-Kontakte");
    }

    @Test
    @DisplayName("Unverändert → nichts gesendet; geändert → nur dieser Eintrag mit seiner Kennung; neu und weggefallen")
    void diff() {
        service.abgleichen(false);
        box.aufrufe.clear();

        TelefonbuchAbgleichErgebnisDto unveraendert = service.abgleichen(false);
        assertThat(unveraendert.neu() + unveraendert.geaendert() + unveraendert.geloescht()).isZero();
        assertThat(unveraendert.meldung()).isEqualTo("Das Telefonbuch ist schon aktuell.");
        assertThat(box.aufrufe).containsExactly("bereit");

        String maxUid = tabelle.values().stream().filter(z -> z.getQuelleId() == 1L).findFirst().orElseThrow().getEintragId();
        String erikaUid = tabelle.values().stream().filter(z -> z.getQuelleId() == 2L).findFirst().orElseThrow().getEintragId();
        soll.put(MAX, eintrag("Max Mustermann (Kunde)", "0123999999"));
        soll.remove(ERIKA);
        soll.put(MITARBEITER, eintrag("Max Mustermann (Mitarbeiter)", "01701234567"));
        box.aufrufe.clear();

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(false);

        assertThat(e).extracting("neu", "geaendert", "geloescht", "eintraege").containsExactly(1, 1, 1, 2);
        assertThat(box.aufrufe).containsExactly("bereit", "aendern:" + maxUid, "neu:102", "loeschen:" + erikaUid);
        assertThat(box.namen(buchId())).containsExactly("Max Mustermann (Kunde)", "Max Mustermann (Mitarbeiter)");
        assertThat(tabelle.values()).extracting(TelefonbuchZuordnung::getQuelleTyp)
                .containsExactlyInAnyOrder(TelefonbuchQuelle.KUNDE, TelefonbuchQuelle.MITARBEITER);
    }

    @Test
    @DisplayName("Telefonbuch in der Box gelöscht → neu angelegt und alles neu gesendet")
    void telefonbuchFehlt() {
        service.abgleichen(false);
        String alt = buchId();
        box.buecher.clear();
        box.eintraege.clear();

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(false);

        assertThat(buchId()).isNotEqualTo(alt);
        assertThat(e.neu()).isEqualTo(2);
        assertThat(box.namen(buchId())).hasSize(2);
        assertThat(tabelle).hasSize(2);
    }

    @Test
    @DisplayName("Vollabgleich: in der Box fehlende Einträge kommen wieder, fremde Einträge fliegen raus")
    void vollabgleichRepariert() {
        service.abgleichen(false);
        String id = buchId();
        String maxUid = tabelle.values().stream().filter(z -> z.getQuelleId() == 1L).findFirst().orElseThrow().getEintragId();
        box.eintraege.get(id).remove(maxUid);
        box.eintraege.get(id).put("999", eintrag("Von Hand eingetragen", "0123111111"));

        TelefonbuchAbgleichErgebnisDto ohneVoll = service.abgleichen(false);
        assertThat(ohneVoll.neu()).isZero();

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.neu()).isEqualTo(1);
        assertThat(e.geloescht()).isEqualTo(1);
        assertThat(box.namen(id)).containsExactly("Erika Musterfrau (Lieferant)", "Max Mustermann (Kunde)");
        assertThat(box.aufrufe).contains("lesen", "loeschen:999");
    }

    @Test
    @DisplayName("Ausgeschaltet, Telefon-Anbindung aus oder nicht eingerichtet → nichts senden")
    void nichtAktiv() {
        gespeicherteSettings.put(TelefonbuchEinstellungen.AKTIV, "false");
        assertThat(service.abgleichen(true).meldung()).contains("ausgeschaltet");
        gespeicherteSettings.put(TelefonbuchEinstellungen.AKTIV, "true");
        when(telefon.istAktiv()).thenReturn(false);
        assertThat(service.abgleichen(true).meldung()).isEqualTo("Die Telefon-Anbindung ist ausgeschaltet.");
        assertThat(service.istBereit()).isFalse();
        when(telefon.istAktiv()).thenReturn(true);
        when(telefon.zugang()).thenReturn(Optional.empty());
        assertThat(service.abgleichen(true).meldung()).isEqualTo(Grund.NICHT_EINGERICHTET.text());

        assertThat(box.aufrufe).isEmpty();
        verify(sollListe, never()).berechne();
    }

    @Test
    @DisplayName("Fehler mit Schritt und Fehlercode der Box: beides steht in der gemerkten Meldung")
    void fehlerMitDetail() {
        box.fehler = new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, "AddPhonebook: Fehlercode 820", null);

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.meldung()).isEqualTo("Unerwartete Antwort der FRITZ!Box (AddPhonebook: Fehlercode 820)");
        assertThat(service.status().letzterFehler())
                .isEqualTo("Unerwartete Antwort der FRITZ!Box (AddPhonebook: Fehlercode 820)");
    }

    @Test
    @DisplayName("Abgelehnter Eintrag mit Fehlercode: die Meldung nennt den letzten Grund der Box")
    void einzelnerEintragAbgelehntMitDetail() {
        box.abgelehnteNamen.add("Max Mustermann (Kunde)");
        box.ablehnungsDetail = "SetPhonebookEntryUID: Fehlercode 402 „Invalid Args“";

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(false);

        assertThat(e.meldung()).isEqualTo(
                "1 Eintrag hat die FRITZ!Box abgelehnt (zuletzt SetPhonebookEntryUID: Fehlercode 402 „Invalid Args“).");
        assertThat(service.status().letzterFehler()).isEqualTo(e.meldung());
    }

    @Test
    @DisplayName("Nimmt die Box gar nichts an, steht der Fehlercode in der Meldung")
    void boxNimmtNichtsAnMitDetail() {
        soll.clear();
        for (long i = 1; i <= 20; i++) {
            soll.put(new Schluessel(TelefonbuchQuelle.KUNDE, i), eintrag("Kunde " + i, "0123456789"));
            box.abgelehnteNamen.add("Kunde " + i);
        }
        box.ablehnungsDetail = "SetPhonebookEntryUID: Fehlercode 820";

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(false);

        assertThat(e.meldung()).isEqualTo("Unerwartete Antwort der FRITZ!Box (SetPhonebookEntryUID: Fehlercode 820)");
    }

    @Test
    @DisplayName("Keine Rechte: Meldung im Klartext gemerkt, nach Erfolg wieder gelöscht")
    void keineRechte() {
        box.fehler = new TelefonAnlageException(Grund.TELEFONBUCH_KEINE_RECHTE);

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.meldung()).contains("darf das Telefonbuch nicht ändern");
        assertThat(service.status().letzterFehler()).contains("darf das Telefonbuch nicht ändern");

        box.fehler = null;
        assertThat(service.abgleichen(true).erfolgreich()).isTrue();
        assertThat(service.status().letzterFehler()).isNull();
    }

    @Test
    @DisplayName("Lehnt die Box einen Eintrag ab, gehen die übrigen trotzdem rüber")
    void einzelnerEintragAbgelehnt() {
        box.abgelehnteNamen.add("Max Mustermann (Kunde)");

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(false);

        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.neu()).isEqualTo(1);
        assertThat(e.meldung()).isEqualTo("1 Eintrag hat die FRITZ!Box abgelehnt.");
        assertThat(box.namen(buchId())).containsExactly("Erika Musterfrau (Lieferant)");
        assertThat(service.status().letzterFehler()).isEqualTo("1 Eintrag hat die FRITZ!Box abgelehnt.");

        box.abgelehnteNamen.clear();
        assertThat(service.abgleichen(false).neu()).isEqualTo(1);
    }

    @Test
    @DisplayName("Nimmt die Box gar nichts an, bricht der Lauf nach wenigen Versuchen ab")
    void boxNimmtNichtsAn() {
        soll.clear();
        for (long i = 1; i <= 20; i++) {
            soll.put(new Schluessel(TelefonbuchQuelle.KUNDE, i), eintrag("Kunde " + i, "0123456789"));
            box.abgelehnteNamen.add("Kunde " + i);
        }

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(false);

        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.meldung()).isEqualTo(Grund.UNERWARTETE_ANTWORT.text());
        assertThat(service.status().letzterFehler()).isEqualTo(Grund.UNERWARTETE_ANTWORT.text());
    }

    @Test
    @DisplayName("Läufe nie parallel: ein zweiter Aufruf während eines Laufs kommt sofort mit „läuft schon“ zurück")
    void keineParallelenLaeufe() throws Exception {
        box.angehalten = new CountDownLatch(1);
        box.weiter = new CountDownLatch(1);
        CompletableFuture<TelefonbuchAbgleichErgebnisDto> erster = CompletableFuture.supplyAsync(() -> service.abgleichen(false));
        assertThat(box.angehalten.await(5, TimeUnit.SECONDS)).isTrue();

        TelefonbuchAbgleichErgebnisDto zweiter = service.abgleichen(true);

        assertThat(zweiter.laeuftSchon()).isTrue();
        box.weiter.countDown();
        assertThat(erster.get(5, TimeUnit.SECONDS).erfolgreich()).isTrue();
        assertThat(service.abgleichen(false).laeuftSchon()).isFalse();
    }

    @Test
    @DisplayName("Schalter: an/aus wird gespeichert")
    void schalter() {
        assertThat(service.schalte(false).aktiv()).isFalse();
        assertThat(service.schalte(true).aktiv()).isTrue();
        assertThat(service.istBereit()).isTrue();
    }

    @Test
    @DisplayName("Ausschalten löscht das Telefonbuch „ERP-Kontakte“ aus der Box und leert die Zuordnungen")
    void ausschaltenLoescht() {
        service.abgleichen(false);
        String id = buchId();
        box.buecher.put("0", "Telefonbuch");
        box.eintraege.put("0", new LinkedHashMap<>(Map.of("1", eintrag("Privat", "0123111111"))));

        assertThat(service.schalte(false).loeschungOffen()).isTrue();
        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isTrue();
        assertThat(e.geloescht()).isEqualTo(2);
        assertThat(box.buecher).containsOnlyKeys("0");
        assertThat(box.eintraege.get("0")).hasSize(1);
        assertThat(box.aufrufe).contains("telefonbuch-loeschen:" + id);
        assertThat(tabelle).isEmpty();
        TelefonbuchStatusDto status = service.status();
        assertThat(status.loeschungOffen()).isFalse();
        assertThat(status.anzahlEintraege()).isZero();
        assertThat(status.letztesErgebnis()).isEqualTo(e);
        assertThat(buchId()).isEmpty();

        // danach gibt es nichts mehr zu tun
        assertThat(service.istBereit()).isFalse();
        assertThat(service.abgleichen(true).meldung()).contains("ausgeschaltet");
    }

    @Test
    @DisplayName("Ausschalten löscht nur das eigene Telefonbuch – nie eines mit anderem Namen, auch nicht unter der gemerkten ID")
    void nurEigenesTelefonbuch() {
        box.buecher.put("0", "Telefonbuch");
        box.eintraege.put("0", new LinkedHashMap<>());
        box.buecher.put("4", "Privat");
        box.eintraege.put("4", new LinkedHashMap<>());
        gespeicherteSettings.put(TelefonbuchEinstellungen.TELEFONBUCH_ID, "4");

        service.schalte(false);
        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isTrue();
        assertThat(box.buecher).containsOnlyKeys("0", "4");
        assertThat(box.aufrufe).noneMatch(a -> a.startsWith("telefonbuch-loeschen"));
        assertThat(service.status().loeschungOffen()).isFalse();
    }

    @Test
    @DisplayName("Box beim Ausschalten nicht erreichbar: Schalter bleibt aus, Löschung offen, wird später nachgeholt")
    void loeschenNachholen() {
        service.abgleichen(false);
        box.fehler = new TelefonAnlageException(Grund.NICHT_ERREICHBAR);

        service.schalte(false);
        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isFalse();
        TelefonbuchStatusDto status = service.status();
        assertThat(status.aktiv()).isFalse();
        assertThat(status.loeschungOffen()).isTrue();
        assertThat(status.letzterFehler()).isEqualTo(Grund.NICHT_ERREICHBAR.text());
        assertThat(service.istBereit()).as("Nachholen beim Start/nachts/per Knopf").isTrue();
        assertThat(tabelle).hasSize(2);

        // auch bei ausgeschalteter Telefon-Anbindung wird die Löschung nachgeholt, sobald die Box erreichbar ist
        when(telefon.istAktiv()).thenReturn(false);
        box.fehler = null;
        assertThat(service.abgleichen(true).erfolgreich()).isTrue();
        assertThat(service.status().loeschungOffen()).isFalse();
        assertThat(service.status().letzterFehler()).isNull();
        assertThat(box.buecher).isEmpty();
    }

    @Test
    @DisplayName("Wieder einschalten verwirft die offene Löschung und überträgt alles")
    void wiederEinschalten() {
        service.schalte(false);
        assertThat(service.status().loeschungOffen()).isTrue();

        assertThat(service.schalte(true).loeschungOffen()).isFalse();
        assertThat(service.abgleichen(true).neu()).isEqualTo(2);
    }

    @Test
    @DisplayName("Unerwarteter Fehler (z. B. Datenbank): allgemeine Meldung gemerkt, Lauf sauber beendet")
    void unerwarteterFehler() {
        when(sollListe.berechne()).thenThrow(new IllegalStateException("Max Mustermann 0123 456789"));

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.meldung()).isEqualTo(TelefonbuchAbgleichService.UNERWARTETER_FEHLER).doesNotContain("Mustermann");
        assertThat(service.status().letzterFehler()).isEqualTo(TelefonbuchAbgleichService.UNERWARTETER_FEHLER);
        assertThat(service.laeuftGerade()).isFalse();
        assertThat(service.abgleichen(true).laeuftSchon()).as("Sperre wieder frei").isFalse();
    }

    @Test
    @DisplayName("Status zeigt „läuft“ ab der Anforderung bis zum Ende des Laufs, danach das Ergebnis")
    void laeuftUndErgebnis() throws Exception {
        assertThat(service.status().laeuftGerade()).isFalse();
        assertThat(service.status().letztesErgebnis()).isNull();
        service.markiereAngefordert();
        assertThat(service.status().laeuftGerade()).isTrue();
        service.verwerfeAnforderung();
        assertThat(service.status().laeuftGerade()).isFalse();

        box.angehalten = new CountDownLatch(1);
        box.weiter = new CountDownLatch(1);
        service.markiereAngefordert();
        CompletableFuture<TelefonbuchAbgleichErgebnisDto> lauf = CompletableFuture.supplyAsync(() -> service.abgleichen(true));
        assertThat(box.angehalten.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.status().laeuftGerade()).isTrue();
        box.weiter.countDown();
        TelefonbuchAbgleichErgebnisDto e = lauf.get(5, TimeUnit.SECONDS);

        assertThat(service.status().laeuftGerade()).isFalse();
        assertThat(service.status().letztesErgebnis()).isEqualTo(e);
        assertThat(e.neu()).isEqualTo(2);
    }

    @Test
    @DisplayName("Abgelehnte Einträge stehen als Zahl im Ergebnis")
    void abgelehntGezaehlt() {
        box.abgelehnteNamen.add("Max Mustermann (Kunde)");
        assertThat(service.abgleichen(false).abgelehnt()).isEqualTo(1);
    }

    /** Telefonbuch „ERP-Kontakte“ mit Geister-Einträgen, die der Export nennt, die Box aber nicht löschen lässt. */
    private void buchMitGeistern(String... uids) {
        box.buecher.put("1", TelefonbuchAbgleichService.TELEFONBUCH_NAME);
        Map<String, TelefonbuchEintrag> geister = new LinkedHashMap<>();
        for (String uid : uids) {
            geister.put(uid, eintrag("Geist", "0123000000"));
            box.unloeschbar.add(uid);
        }
        box.eintraege.put("1", geister);
    }

    @Test
    @DisplayName("Fremde Einträge, die die Box nicht löschen lässt, werden übersprungen – Kontakte kommen trotzdem an")
    void nichtLoeschbareFremdeUeberspringen() {
        buchMitGeistern("10", "11");

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isTrue();
        assertThat(e.neu()).isEqualTo(2);
        assertThat(e.geloescht()).isZero();
        assertThat(e.meldung()).isEqualTo("Telefonbuch abgeglichen. 2 alte Einträge ließen sich in der FRITZ!Box "
                + "nicht löschen. Das schadet nicht, die Kontakte sind übertragen.");
        assertThat(box.aufrufe).contains("loeschen:10", "loeschen:11");
        assertThat(tabelle).hasSize(2);
        assertThat(service.status().letzterFehler()).isNull();
    }

    @Test
    @DisplayName("Ein einzelner nicht löschbarer Eintrag: Meldung in der Einzahl, löschbare werden gelöscht")
    void einNichtLoeschbarerEintrag() {
        buchMitGeistern("10");
        box.eintraege.get("1").put("12", eintrag("Fremd", "0123000001"));

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isTrue();
        assertThat(e.geloescht()).isEqualTo(1);
        assertThat(e.meldung()).contains(" 1 alter Eintrag ließ sich in der FRITZ!Box nicht löschen.");
        assertThat(box.eintraege.get("1")).containsKey("10").doesNotContainKey("12");
    }

    @Test
    @DisplayName("Nach 5 abgelehnten Löschungen ohne Erfolg versucht der Lauf keine weiteren")
    void loeschenNachFuenfAblehnungenNichtMehrVersucht() {
        buchMitGeistern("3", "4", "5", "6", "7", "8", "9");

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(box.aufrufe.stream().filter(a -> a.startsWith("loeschen:"))).hasSize(5);
        assertThat(e.meldung()).contains("7 alte Einträge");
        assertThat(e.neu()).isEqualTo(2);
    }

    @Test
    @DisplayName("Hat die Box schon eine Löschung angenommen, gilt die 5er-Grenze nicht – alle werden versucht")
    void nachErfolgreicherLoeschungKeineGrenze() {
        buchMitGeistern();
        Map<String, TelefonbuchEintrag> buch = box.eintraege.get("1");
        buch.put("2", eintrag("Fremd", "0123000001"));
        for (String uid : List.of("3", "4", "5", "6", "7", "8")) {
            buch.put(uid, eintrag("Geist", "0123000000"));
            box.unloeschbar.add(uid);
        }

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        // Der Fake liefert die IDs in Einfüge-Reihenfolge, „2“ (löschbar) also zuerst.
        assertThat(box.aufrufe.stream().filter(a -> a.startsWith("loeschen:"))).hasSize(7);
        assertThat(e.geloescht()).isEqualTo(1);
        assertThat(e.meldung()).contains("6 alte Einträge");
    }

    @Test
    @DisplayName("Abgelehnte und nicht löschbare Einträge im selben Lauf: Fehler bleibt gemerkt, kein „schadet nicht“")
    void abgelehntUndNichtLoeschbar() {
        buchMitGeistern("10");
        box.abgelehnteNamen.add("Max Mustermann (Kunde)");

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.meldung()).startsWith("1 Eintrag hat die FRITZ!Box abgelehnt")
                .contains("1 alter Eintrag ließ sich").doesNotContain("schadet nicht");
        assertThat(service.status().letzterFehler()).startsWith("1 Eintrag hat die FRITZ!Box abgelehnt")
                .doesNotContain("alter Eintrag");
        assertThat(service.abgleichen(true).meldung()).isEqualTo(e.meldung());
    }

    @Test
    @DisplayName("Weggefallener eigener Eintrag nicht löschbar: Zuordnung bleibt, der nächste Lauf versucht es wieder")
    void weggefallenNichtLoeschbarBleibtGemerkt() {
        service.abgleichen(false);
        String maxUid = tabelle.values().stream()
                .filter(z -> z.getQuelleTyp() == MAX.quelle() && z.getQuelleId().equals(MAX.id()))
                .findFirst().orElseThrow().getEintragId();
        soll.remove(MAX);
        box.unloeschbar.add(maxUid);

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(false);

        assertThat(e.erfolgreich()).isTrue();
        assertThat(e.geloescht()).isZero();
        assertThat(e.meldung()).contains("1 alter Eintrag");
        assertThat(tabelle).hasSize(2);

        box.unloeschbar.clear();
        TelefonbuchAbgleichErgebnisDto zweiter = service.abgleichen(false);
        assertThat(zweiter.geloescht()).isEqualTo(1);
        assertThat(zweiter.meldung()).isEqualTo("Telefonbuch abgeglichen.");
        assertThat(tabelle).hasSize(1);
    }

    @Test
    @DisplayName("Fehlende Rechte beim Löschen brechen den Lauf weiter ab")
    void fehlendeRechteBeimLoeschenBrechenAb() {
        buchMitGeistern("10");
        box.loeschFehler = new TelefonAnlageException(Grund.TELEFONBUCH_KEINE_RECHTE);

        TelefonbuchAbgleichErgebnisDto e = service.abgleichen(true);

        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.meldung()).isEqualTo(Grund.TELEFONBUCH_KEINE_RECHTE.text());
        assertThat(box.aufrufe).noneMatch(a -> a.startsWith("neu:"));
    }
}
