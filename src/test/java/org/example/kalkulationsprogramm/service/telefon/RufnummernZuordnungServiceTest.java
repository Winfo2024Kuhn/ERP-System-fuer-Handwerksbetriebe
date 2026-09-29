package org.example.kalkulationsprogramm.service.telefon;

import jakarta.persistence.EntityManager;
import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonZuordnung;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
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
    @Mock KontaktRufnummerRepository gemerkte;
    @Mock TelefonEinstellungenService einstellungen;
    @Mock EntityManager em;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T10:00:00Z"), ZoneId.of("Europe/Berlin"));
    private RufnummernZuordnungService service;

    @BeforeEach
    void setUp() {
        service = new RufnummernZuordnungService(kunden, lieferanten, gemerkte, einstellungen, em, clock);
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

        KontaktKurzDto k = service.ordneManuellZu(a, 1L, null);
        assertThat(k.typ()).isEqualTo("KUNDE");
        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.MANUELL);

        service.ordneManuellZu(a, null, 10L);
        assertThat(a.getKunde()).isNull();
        assertThat(a.getLieferant().getId()).isEqualTo(10L);

        assertThatThrownBy(() -> service.ordneManuellZu(a, 1L, 10L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ordneManuellZu(a, null, null)).isInstanceOf(IllegalArgumentException.class);
        when(kunden.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ordneManuellZu(a, 99L, null)).hasMessageContaining("nicht gefunden");
        when(lieferanten.findById(98L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ordneManuellZu(a, null, 98L)).hasMessageContaining("nicht gefunden");

        service.hebeZuordnungAuf(a);
        assertThat(a.getZuordnung()).isEqualTo(TelefonZuordnung.KEINE);
        assertThat(a.getLieferant()).isNull();
    }

    @Test
    @DisplayName("Nummer merken legt einen Eintrag an – aber nur einmal")
    void merken() {
        when(gemerkte.existsByKundeIdAndNummerNormalisiert(1L, "+4993155555")).thenReturn(false, true);
        assertThat(service.merkeNummer("0931 55555", "+4993155555", 1L, null)).isTrue();
        assertThat(service.merkeNummer("0931 55555", "+4993155555", 1L, null)).isFalse();
        ArgumentCaptor<KontaktRufnummer> c = ArgumentCaptor.forClass(KontaktRufnummer.class);
        verify(gemerkte, times(1)).save(c.capture());
        assertThat(c.getValue().getNummerRoh()).isEqualTo("0931 55555");
        assertThat(c.getValue().getKunde().getId()).isEqualTo(1L);

        when(gemerkte.existsByLieferantIdAndNummerNormalisiert(10L, "+4993155555")).thenReturn(false);
        assertThat(service.merkeNummer(" ", "+4993155555", null, 10L)).isTrue();
        assertThat(service.merkeNummer("x", null, 1L, null)).isFalse();
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
}
