package org.example.kalkulationsprogramm.service.telefon;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.SteuerberaterAnsprechpartner;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RufnummernZuordnungServiceTest {

    @Mock KundeRepository kunden;
    @Mock LieferantenRepository lieferanten;
    @Mock SteuerberaterKontaktRepository steuerberater;
    @Mock KontaktRufnummerRepository gemerkte;
    @Mock TelefonEinstellungenService einstellungen;
    @Mock EntityManager em;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T10:00:00Z"), ZoneId.of("Europe/Berlin"));
    private RufnummernZuordnungService service;

    @BeforeEach
    void setUp() {
        service = new RufnummernZuordnungService(kunden, lieferanten, steuerberater, gemerkte, einstellungen, em, clock);
        when(einstellungen.landesvorwahl()).thenReturn("49");
        when(einstellungen.ortsvorwahl()).thenReturn("931");
        when(kunden.findeTelefonverzeichnis()).thenReturn(List.of(
                new Object[]{1L, "Mustermann GmbH", "K-1", "Würzburg", "0931 / 123 45-67", null},
                new Object[]{2L, "Erika Mustermann", "K-2", "Würzburg", "+49 171 1234567", "0171-1234567"},
                new Object[]{3L, "Max Mustermann", "K-3", "Kitzingen", "0931 999999", null}));
        when(lieferanten.findeTelefonverzeichnis()).thenReturn(List.<Object[]>of(
                new Object[]{10L, "Stahl Muster KG", "Schweinfurt", "999999", null}));
        when(gemerkte.findAllMitKontakt()).thenReturn(List.of());
        when(em.getReference(eq(Kunde.class), anyLong())).thenAnswer(i -> kunde(i.getArgument(1)));
        when(em.getReference(eq(Lieferanten.class), anyLong())).thenAnswer(i -> lieferant(i.getArgument(1)));
        when(em.getReference(eq(SteuerberaterKontakt.class), anyLong())).thenAnswer(i -> steuerberater(i.getArgument(1)));
        when(steuerberater.findeTelefonverzeichnis()).thenReturn(List.of());
    }

    private static Kunde kunde(long id) {
        Kunde k = new Kunde();
        k.setId(id);
        k.setName("Kunde " + id);
        return k;
    }

    private static Lieferanten lieferant(long id) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname("Lieferant " + id);
        return l;
    }

    private static SteuerberaterKontakt steuerberater(long id) {
        SteuerberaterKontakt s = new SteuerberaterKontakt();
        s.setId(id);
        s.setName("Kanzlei " + id);
        return s;
    }

    private TelefonAnruf anruf(String nummer) {
        TelefonAnruf a = new TelefonAnruf();
        a.setNummerRoh(nummer);
        a.setNummerNormalisiert(service.normalisiere(nummer));
        return a;
    }

    @Test
    @DisplayName("Eindeutige Nummer → automatisch dem Kunden zugeordnet, egal in welcher Schreibweise")
    void eindeutig() {
        TelefonAnruf a = anruf("09311234567");
        boolean neu = service.ordneAutomatischZu(a, service.frischesVerzeichnis());

        assertThat(neu).isTrue();
        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.AUTOMATISCH);
        assertThat(a.getKunde().getId()).isEqualTo(1L);
        assertThat(a.getLieferant()).isNull();
    }

    @Test
    @DisplayName("Handy- und Festnetzfeld desselben Kunden zählen nur einmal")
    void gleicherKontaktDoppelt() {
        TelefonAnruf a = anruf("+491711234567");
        service.ordneAutomatischZu(a, service.frischesVerzeichnis());
        assertThat(a.getKunde().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Nummer bei Kunde und Lieferant → mehrdeutig, keine Zuordnung, Kandidaten verfügbar")
    void mehrdeutig() {
        RufnummernZuordnungService.Verzeichnis v = service.frischesVerzeichnis();
        TelefonAnruf a = anruf("0931999999");
        service.ordneAutomatischZu(a, v);

        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.KEINE);
        assertThat(a.getKunde()).isNull();
        RufnummernZuordnungService.Treffer t = v.finde(a.getNummerNormalisiert());
        assertThat(t.mehrdeutig()).isTrue();
        assertThat(t.kontakte()).extracting(KontaktKurzDto::typ).containsExactlyInAnyOrder("KUNDE", "LIEFERANT");
    }

    @Test
    @DisplayName("Unbekannte und unterdrückte Nummern bleiben unbekannt")
    void unbekannt() {
        RufnummernZuordnungService.Verzeichnis v = service.frischesVerzeichnis();
        TelefonAnruf a = anruf("0800 0000000");
        TelefonAnruf anonym = anruf("");
        assertThat(service.ordneAutomatischZu(a, v)).isFalse();
        assertThat(service.ordneAutomatischZu(anonym, v)).isFalse();
        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.KEINE);
        assertThat(anonym.getNummerNormalisiert()).isNull();
    }

    @Test
    @DisplayName("Manuelle Zuordnung wird von der Automatik nie überschrieben")
    void manuellBleibt() {
        TelefonAnruf a = anruf("09311234567");
        a.setLieferant(lieferant(10L));
        a.setZuordnung(TelefonZuordnung.MANUELL);
        assertThat(service.ordneAutomatischZu(a, service.frischesVerzeichnis())).isFalse();
        assertThat(a.getLieferant().getId()).isEqualTo(10L);
        assertThat(a.getKunde()).isNull();
    }

    @Test
    @DisplayName("Bereits automatisch zugeordnet → kein 'neu zugeordnet'; Nummer entfernt → wieder unbekannt")
    void automatischErneut() {
        TelefonAnruf a = anruf("09311234567");
        RufnummernZuordnungService.Verzeichnis v = service.frischesVerzeichnis();
        service.ordneAutomatischZu(a, v);
        assertThat(service.ordneAutomatischZu(a, v)).isFalse();

        when(kunden.findeTelefonverzeichnis()).thenReturn(List.of());
        service.ordneAutomatischZu(a, service.frischesVerzeichnis());
        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.KEINE);
        assertThat(a.getKunde()).isNull();
    }

    @Test
    @DisplayName("Gemerkte Rufnummern werden gefunden")
    void gemerkteNummer() {
        KontaktRufnummer r = new KontaktRufnummer();
        r.setLieferant(lieferant(11L));
        r.setNummerNormalisiert("+4993155555");
        when(gemerkte.findAllMitKontakt()).thenReturn(List.of(r));
        TelefonAnruf a = anruf("093155555");
        service.ordneAutomatischZu(a, service.frischesVerzeichnis());
        assertThat(a.getLieferant().getId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("Von Hand zuordnen: genau ein Kontakt, sonst Fehler; unbekannter Kontakt → Fehler")
    void manuell() {
        when(kunden.findById(1L)).thenReturn(Optional.of(kunde(1L)));
        when(lieferanten.findById(10L)).thenReturn(Optional.of(lieferant(10L)));
        TelefonAnruf a = anruf("0800");

        KontaktKurzDto k = service.ordneManuellZu(a, 1L, null, null);
        assertThat(k.typ()).isEqualTo("KUNDE");
        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.MANUELL);

        service.ordneManuellZu(a, null, 10L, null);
        assertThat(a.getKunde()).isNull();
        assertThat(a.getLieferant().getId()).isEqualTo(10L);

        assertThatThrownBy(() -> service.ordneManuellZu(a, 1L, 10L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ordneManuellZu(a, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        when(kunden.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ordneManuellZu(a, 99L, null, null)).hasMessageContaining("nicht gefunden");
        when(lieferanten.findById(98L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ordneManuellZu(a, null, 98L, null)).hasMessageContaining("nicht gefunden");

        service.hebeZuordnungAuf(a);
        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.KEINE);
        assertThat(a.getLieferant()).isNull();
    }

    @Test
    @DisplayName("Nummer merken legt einen Eintrag an – aber nur einmal")
    void merken() {
        when(gemerkte.existsByKundeIdAndNummerNormalisiert(1L, "+4993155555")).thenReturn(false, true);
        assertThat(service.merkeNummer("0931 55555", "+4993155555", 1L, null, null)).isTrue();
        assertThat(service.merkeNummer("0931 55555", "+4993155555", 1L, null, null)).isFalse();
        ArgumentCaptor<KontaktRufnummer> c = ArgumentCaptor.forClass(KontaktRufnummer.class);
        verify(gemerkte, times(1)).save(c.capture());
        assertThat(c.getValue().getNummerRoh()).isEqualTo("0931 55555");
        assertThat(c.getValue().getKunde().getId()).isEqualTo(1L);

        when(gemerkte.existsByLieferantIdAndNummerNormalisiert(10L, "+4993155555")).thenReturn(false);
        assertThat(service.merkeNummer(" ", "+4993155555", null, 10L, null)).isTrue();
        assertThat(service.merkeNummer("x", null, 1L, null, null)).isFalse();
    }

    @Test
    @DisplayName("Anzeige-Verzeichnis wird 60 Sekunden gecacht")
    void cache() {
        service.verzeichnis();
        service.verzeichnis();
        verify(kunden, times(1)).findeTelefonverzeichnis();
        service.verwerfeCache();
        service.verzeichnis();
        verify(kunden, times(2)).findeTelefonverzeichnis();
        verify(gemerkte, never()).save(any());
    }

    @Test
    @DisplayName("kontaktVon liefert Kurzform für Kunde, Lieferant oder null")
    void kontaktVon() {
        TelefonAnruf a = new TelefonAnruf();
        assertThat(RufnummernZuordnungService.kontaktVon(a)).isNull();
        Kunde k = kunde(1L);
        k.setKundennummer("K-1");
        k.setOrt("Würzburg");
        a.setKunde(k);
        assertThat(RufnummernZuordnungService.kontaktVon(a)).isEqualTo(new KontaktKurzDto("KUNDE", 1L, "Kunde 1", "K-1", "Würzburg"));
        a.setKunde(null);
        a.setLieferant(lieferant(10L));
        assertThat(RufnummernZuordnungService.kontaktVon(a).typ()).isEqualTo("LIEFERANT");
    }

    @Test
    @DisplayName("Durchwahl: Anruf aus einer anderen Abteilung wird trotzdem als die Firma erkannt")
    void durchwahlLieferant() {
        when(lieferanten.findeTelefonverzeichnis()).thenReturn(List.<Object[]>of(
                new Object[]{20L, "Muster Baustoffe GmbH", "Kitzingen", "09321 7777-0", null}));
        RufnummernZuordnungService.Verzeichnis v = service.frischesVerzeichnis();

        for (String nummer : new String[]{"09321 77770", "093217777123", "+49 9321 7777 45678", "09321 7777"}) {
            TelefonAnruf a = anruf(nummer);
            service.ordneAutomatischZu(a, v);
            assertThat(a.getLieferant()).as(nummer).isNotNull();
            assertThat(a.getLieferant().getId()).as(nummer).isEqualTo(20L);
        }
        // Mehr als 5 Ziffern hinter dem Stamm, andere Nummer im selben Ortsnetz: nicht die Firma.
        for (String nummer : new String[]{"09321 7777123456", "09321 777", "09321 12345"}) {
            TelefonAnruf a = anruf(nummer);
            service.ordneAutomatischZu(a, v);
            assertThat(a.getZuordnung()).as(nummer).isEqualTo(TelefonZuordnung.KEINE);
        }
    }

    @Test
    @DisplayName("Durchwahl: nicht aus dem Handyfeld – fremde Anrufer werden nicht dem Kunden zugeordnet")
    void keineDurchwahlAusHandyfeld() {
        when(kunden.findeTelefonverzeichnis()).thenReturn(List.<Object[]>of(
                new Object[]{6L, "Erika Mustermann", "K-6", "Würzburg", null, "0931 5555-12"}));
        RufnummernZuordnungService.Verzeichnis v = service.frischesVerzeichnis();
        TelefonAnruf fremd = anruf("0931 555599");
        service.ordneAutomatischZu(fremd, v);
        assertThat(fremd.getZuordnung()).isEqualTo(TelefonZuordnung.KEINE);
        TelefonAnruf exakt = anruf("0931 555512");
        service.ordneAutomatischZu(exakt, v);
        assertThat(exakt.getKunde().getId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("Durchwahl: exakte Nummer hat Vorrang, sonst gewinnt die längste Stammnummer")
    void durchwahlVorrang() {
        when(lieferanten.findeTelefonverzeichnis()).thenReturn(List.<Object[]>of(
                new Object[]{20L, "Muster Baustoffe GmbH", "Kitzingen", "09321 7777-0", null},
                new Object[]{21L, "Muster Baustoffe Filiale", "Kitzingen", "09321 77771-0", null}));
        when(kunden.findeTelefonverzeichnis()).thenReturn(List.<Object[]>of(
                new Object[]{5L, "Erika Mustermann", "K-5", "Kitzingen", "09321 777712", null}));
        RufnummernZuordnungService.Verzeichnis v = service.frischesVerzeichnis();

        TelefonAnruf exakt = anruf("09321 777712");
        service.ordneAutomatischZu(exakt, v);
        assertThat(exakt.getKunde().getId()).isEqualTo(5L);

        TelefonAnruf filiale = anruf("09321 7777155");
        service.ordneAutomatischZu(filiale, v);
        assertThat(filiale.getLieferant().getId()).isEqualTo(21L);

        TelefonAnruf zentrale = anruf("09321 777799");
        service.ordneAutomatischZu(zentrale, v);
        assertThat(zentrale.getLieferant().getId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("Steuerberater: Kanzleinummer, Ansprechpartner-Durchwahl und andere Durchwahlen → Kanzlei")
    void steuerberaterErkannt() {
        when(steuerberater.findeTelefonverzeichnis()).thenReturn(List.<Object[]>of(
                new Object[]{30L, "Kanzlei Beispiel", "0931 4444-0"}));
        when(steuerberater.findeAnsprechpartnerTelefone()).thenReturn(List.<Object[]>of(
                new Object[]{30L, "Kanzlei Beispiel", "0931 4444-12", "Erika", "Beispiel"}));
        RufnummernZuordnungService.Verzeichnis v = service.frischesVerzeichnis();

        for (String nummer : new String[]{"0931 44440", "0931 444412", "0931 4444345"}) {
            TelefonAnruf a = anruf(nummer);
            assertThat(service.ordneAutomatischZu(a, v)).as(nummer).isTrue();
            assertThat(a.getSteuerberater().getId()).as(nummer).isEqualTo(30L);
            assertThat(a.getKunde()).isNull();
            assertThat(a.getLieferant()).isNull();
            KontaktKurzDto k = RufnummernZuordnungService.kontaktVon(a);
            assertThat(k.typ()).isEqualTo("STEUERBERATER");
            assertThat(k.name()).isEqualTo("Kanzlei 30");
        }
        assertThat(v.finde(service.normalisiere("0931 444412")).kontakte()).hasSize(1);
    }

    @Test
    @DisplayName("Ansprechpartner: genaue Nummer nennt die Person, andere Durchwahl und Zentrale nur die Kanzlei")
    void ansprechpartnerImVerzeichnis() {
        when(steuerberater.findeTelefonverzeichnis()).thenReturn(List.<Object[]>of(
                new Object[]{30L, "Kanzlei Beispiel", "0931 4444-0"}));
        when(steuerberater.findeAnsprechpartnerTelefone()).thenReturn(List.<Object[]>of(
                new Object[]{30L, "Kanzlei Beispiel", "0931 4444-12", "Erika", "Beispiel"},
                new Object[]{30L, "Kanzlei Beispiel", "0931 4444-0", null, "Muster"},
                new Object[]{31L, "Steuerbüro Muster", "0931 777777", " ", "Mustermann "}));
        RufnummernZuordnungService.Verzeichnis v = service.frischesVerzeichnis();

        TelefonAnruf erika = anruf("0931 444412");
        service.ordneAutomatischZu(erika, v);
        KontaktKurzDto k = v.mitAnsprechpartner(RufnummernZuordnungService.kontaktVon(erika), erika.getNummerNormalisiert());
        assertThat(k).isEqualTo(new KontaktKurzDto("STEUERBERATER", 30L, "Kanzlei 30", null, null, "Erika Beispiel"));

        TelefonAnruf andereDurchwahl = anruf("0931 4444345");
        service.ordneAutomatischZu(andereDurchwahl, v);
        assertThat(v.mitAnsprechpartner(RufnummernZuordnungService.kontaktVon(andereDurchwahl),
                andereDurchwahl.getNummerNormalisiert()).ansprechpartner()).isNull();

        // Kanzlei und Herr Muster teilen sich die Zentrale – dann ist keine Person gemeint.
        assertThat(v.finde(service.normalisiere("0931 44440")).kontakte()).singleElement()
                .extracting(KontaktKurzDto::ansprechpartner).isNull();
        assertThat(v.finde(service.normalisiere("0931 777777")).kontakte().getFirst().ansprechpartner()).isEqualTo("Mustermann");

        // Kunden, fehlende Kontakte und unterdrückte Nummern bleiben, wie sie sind.
        KontaktKurzDto kunde = new KontaktKurzDto("KUNDE", 1L, "Kunde 1", null, null);
        assertThat(v.mitAnsprechpartner(kunde, erika.getNummerNormalisiert())).isSameAs(kunde);
        assertThat(v.mitAnsprechpartner(null, erika.getNummerNormalisiert())).isNull();
        assertThat(v.mitAnsprechpartner(k.mitAnsprechpartner(null), null).ansprechpartner()).isNull();
    }

    private static SteuerberaterAnsprechpartner person(long id, long kanzleiId, String telefon) {
        SteuerberaterAnsprechpartner p = new SteuerberaterAnsprechpartner();
        p.setId(id);
        p.setSteuerberater(steuerberater(kanzleiId));
        p.setVorname("Erika");
        p.setNachname("Beispiel");
        p.setTelefon(telefon);
        return p;
    }

    @Test
    @DisplayName("Nummer beim Ansprechpartner speichern: nur in ein leeres Feld, nie überschreiben")
    void speichereBeimAnsprechpartner() {
        SteuerberaterAnsprechpartner leer = person(40L, 30L, null);
        SteuerberaterAnsprechpartner gleich = person(41L, 30L, "0931 / 55 55 5");
        SteuerberaterAnsprechpartner anders = person(42L, 30L, "0931 66666");
        SteuerberaterAnsprechpartner fremd = person(43L, 31L, null);
        when(em.find(SteuerberaterAnsprechpartner.class, 40L)).thenReturn(leer);
        when(em.find(SteuerberaterAnsprechpartner.class, 41L)).thenReturn(gleich);
        when(em.find(SteuerberaterAnsprechpartner.class, 42L)).thenReturn(anders);
        when(em.find(SteuerberaterAnsprechpartner.class, 43L)).thenReturn(fremd);

        assertThat(service.speichereBeimAnsprechpartner(30L, 40L, " 0931 55555 ", "+4993155555")).isTrue();
        assertThat(leer.getTelefon()).isEqualTo("0931 55555");
        assertThat(service.speichereBeimAnsprechpartner(30L, 41L, "0931 55555", "+4993155555")).isFalse();
        assertThat(gleich.getTelefon()).isEqualTo("0931 / 55 55 5");
        assertThatThrownBy(() -> service.speichereBeimAnsprechpartner(30L, 42L, "0931 55555", "+4993155555"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Erika Beispiel hat schon die Nummer 0931 66666");
        assertThat(anders.getTelefon()).isEqualTo("0931 66666");

        assertThatThrownBy(() -> service.speichereBeimAnsprechpartner(30L, 43L, "0931 55555", "+4993155555"))
                .hasMessageContaining("nicht gefunden");
        assertThatThrownBy(() -> service.speichereBeimAnsprechpartner(30L, 99L, "0931 55555", "+4993155555"))
                .hasMessageContaining("nicht gefunden");
        assertThatThrownBy(() -> service.speichereBeimAnsprechpartner(null, 40L, "0931 55555", "+4993155555"))
                .hasMessageContaining("Steuerberater");
        assertThat(fremd.getTelefon()).isNull();

        SteuerberaterAnsprechpartner ohneRoh = person(44L, 30L, " ");
        when(em.find(SteuerberaterAnsprechpartner.class, 44L)).thenReturn(ohneRoh);
        assertThat(service.speichereBeimAnsprechpartner(30L, 44L, null, "+4993155555")).isTrue();
        assertThat(ohneRoh.getTelefon()).isEqualTo("+4993155555");
        assertThat(service.speichereBeimAnsprechpartner(30L, 40L, "", null)).isFalse();
    }

    @Test
    @DisplayName("Steuerberater von Hand zuordnen, merken und wieder aufheben")
    void steuerberaterManuell() {
        when(steuerberater.findById(30L)).thenReturn(Optional.of(steuerberater(30L)));
        when(steuerberater.findById(31L)).thenReturn(Optional.empty());
        TelefonAnruf a = anruf("0800");
        a.setKunde(kunde(1L));

        KontaktKurzDto k = service.ordneManuellZu(a, null, null, 30L);
        assertThat(k.typ()).isEqualTo("STEUERBERATER");
        assertThat(a.getSteuerberater().getId()).isEqualTo(30L);
        assertThat(a.getKunde()).isNull();
        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.MANUELL);
        assertThatThrownBy(() -> service.ordneManuellZu(a, 1L, null, 30L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ordneManuellZu(a, null, null, 31L)).hasMessageContaining("nicht gefunden");

        when(gemerkte.existsBySteuerberaterIdAndNummerNormalisiert(30L, "+4993155555")).thenReturn(false);
        assertThat(service.merkeNummer("0931 55555", "+4993155555", null, null, 30L)).isTrue();
        ArgumentCaptor<KontaktRufnummer> c = ArgumentCaptor.forClass(KontaktRufnummer.class);
        verify(gemerkte).save(c.capture());
        assertThat(c.getValue().getSteuerberater().getId()).isEqualTo(30L);

        service.hebeZuordnungAuf(a);
        assertThat(a.getSteuerberater()).isNull();
        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.KEINE);
    }

    @Test
    @DisplayName("Gemerkte Steuerberater-Nummer mit Durchwahl-Schreibweise erkennt die ganze Anlage")
    void gemerkteSteuerberaterNummer() {
        KontaktRufnummer r = new KontaktRufnummer();
        r.setSteuerberater(steuerberater(30L));
        r.setNummerRoh("0931 6666-0");
        r.setNummerNormalisiert("+4993166660");
        when(gemerkte.findAllMitKontakt()).thenReturn(List.of(r));
        TelefonAnruf a = anruf("0931 666677");
        service.ordneAutomatischZu(a, service.frischesVerzeichnis());
        assertThat(a.getSteuerberater().getId()).isEqualTo(30L);
    }
}
