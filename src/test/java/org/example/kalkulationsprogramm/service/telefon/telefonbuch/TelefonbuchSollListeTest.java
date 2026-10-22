package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.TelefonbuchQuelle;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.example.kalkulationsprogramm.service.telefon.TelefonEinstellungenService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelefonbuchSollListeTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 10, 21);

    @Mock KundeRepository kunden;
    @Mock LieferantenRepository lieferanten;
    @Mock MitarbeiterRepository mitarbeiter;
    @Mock SteuerberaterKontaktRepository steuerberater;
    @Mock KontaktRufnummerRepository zusatznummern;
    @Mock TelefonEinstellungenService einstellungen;

    private TelefonbuchSollListe sollListe;

    @BeforeEach
    void setUp() {
        when(einstellungen.landesvorwahl()).thenReturn("49");
        when(einstellungen.ortsvorwahl()).thenReturn("123");
        sollListe = new TelefonbuchSollListe(kunden, lieferanten, mitarbeiter, steuerberater, zusatznummern, einstellungen,
                Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneId.of("Europe/Berlin")));
    }

    private static List<Object[]> zeilen(Object[]... z) {
        return new ArrayList<>(List.of(z));
    }

    private static Nummer n(String nummer, NummerArt art) {
        return new Nummer(nummer, art);
    }

    @Test
    @DisplayName("Ein Eintrag je Kontakt mit allen Nummern, Name „Name (Art)“, Zusatznummern hängen am Kontakt")
    void eintraegeJeKontakt() {
        when(kunden.findeFuerTelefonbuch()).thenReturn(zeilen(
                new Object[]{1L, "Max Mustermann", Anrede.HERR, "0123 456789", "0170 1234567"},
                new Object[]{2L, "Muster Bau GmbH", Anrede.FIRMA, "0123 456789-0", null}));
        when(zusatznummern.findeKundenNummernFuerTelefonbuch()).thenReturn(zeilen(
                new Object[]{1L, "Max Mustermann", Anrede.HERR, "0123 999999"},
                // Kunde ohne eigene Nummer, aber mit gemerkter Zusatznummer
                new Object[]{3L, "Erika Musterfrau", Anrede.FRAU, "+49 123 777777"}));
        when(lieferanten.findeFuerTelefonbuch()).thenReturn(zeilen(new Object[]{10L, "Würth", "0123 555555", "0151 2222222"}));
        when(zusatznummern.findeLieferantenNummernFuerTelefonbuch()).thenReturn(zeilen(new Object[]{10L, "Würth", "0123 555556"}));
        when(mitarbeiter.findeFuerTelefonbuch()).thenReturn(zeilen(new Object[]{20L, "Max", "Mustermann", "0160 3333333", "0123 444444"}));
        when(steuerberater.findeFuerTelefonbuch(HEUTE)).thenReturn(zeilen(new Object[]{30L, "Kanzlei Beispiel", "0123 888888"}));
        when(zusatznummern.findeSteuerberaterNummernFuerTelefonbuch(HEUTE)).thenReturn(zeilen(new Object[]{30L, "Kanzlei Beispiel", "0123 888889"}));
        when(steuerberater.findeAnsprechpartnerFuerTelefonbuch(HEUTE)).thenReturn(zeilen(
                new Object[]{31L, "Erika", "Musterfrau", "0123 888888-12", "Kanzlei Beispiel"}));

        Map<Schluessel, TelefonbuchEintrag> soll = sollListe.berechne();

        assertThat(soll.get(new Schluessel(TelefonbuchQuelle.KUNDE, 1L))).isEqualTo(new TelefonbuchEintrag(
                "Max Mustermann (Kunde)", List.of(n("0123456789", NummerArt.PRIVAT), n("01701234567", NummerArt.MOBIL),
                n("0123999999", NummerArt.PRIVAT))));
        assertThat(soll.get(new Schluessel(TelefonbuchQuelle.KUNDE, 2L)).nummern())
                .containsExactly(n("01234567890", NummerArt.GESCHAEFTLICH));
        assertThat(soll.get(new Schluessel(TelefonbuchQuelle.KUNDE, 3L))).isEqualTo(new TelefonbuchEintrag(
                "Erika Musterfrau (Kunde)", List.of(n("0123777777", NummerArt.PRIVAT))));
        assertThat(soll.get(new Schluessel(TelefonbuchQuelle.LIEFERANT, 10L))).isEqualTo(new TelefonbuchEintrag(
                "Würth (Lieferant)", List.of(n("0123555555", NummerArt.GESCHAEFTLICH), n("01512222222", NummerArt.MOBIL),
                n("0123555556", NummerArt.GESCHAEFTLICH))));
        assertThat(soll.get(new Schluessel(TelefonbuchQuelle.MITARBEITER, 20L))).isEqualTo(new TelefonbuchEintrag(
                "Max Mustermann (Mitarbeiter)", List.of(n("01603333333", NummerArt.MOBIL), n("0123444444", NummerArt.PRIVAT))));
        assertThat(soll.get(new Schluessel(TelefonbuchQuelle.STEUERBERATER, 30L))).isEqualTo(new TelefonbuchEintrag(
                "Kanzlei Beispiel (Steuerberater)", List.of(n("0123888888", NummerArt.GESCHAEFTLICH),
                n("0123888889", NummerArt.GESCHAEFTLICH))));
        assertThat(soll.get(new Schluessel(TelefonbuchQuelle.STEUERBERATER_ANSPRECHPARTNER, 31L))).isEqualTo(
                new TelefonbuchEintrag("Erika Musterfrau (Steuerberater Kanzlei Beispiel)",
                        List.of(n("012388888812", NummerArt.GESCHAEFTLICH))));
        assertThat(soll.keySet()).extracting(Schluessel::quelle).containsExactly(TelefonbuchQuelle.KUNDE,
                TelefonbuchQuelle.KUNDE, TelefonbuchQuelle.KUNDE, TelefonbuchQuelle.LIEFERANT, TelefonbuchQuelle.MITARBEITER,
                TelefonbuchQuelle.STEUERBERATER, TelefonbuchQuelle.STEUERBERATER_ANSPRECHPARTNER);
        verify(steuerberater).findeFuerTelefonbuch(HEUTE);
    }

    @Test
    @DisplayName("Doppelte Nummern in anderer Schreibweise nur einmal; Kontakt ohne gültige Nummer fehlt")
    void dublettenUndOhneNummer() {
        when(kunden.findeFuerTelefonbuch()).thenReturn(zeilen(
                new Object[]{1L, "Max Mustermann", null, "0123 / 45 67 89", "+49 (0) 123 456789"},
                new Object[]{2L, "Erika Musterfrau", null, "--", "12"}));
        when(zusatznummern.findeKundenNummernFuerTelefonbuch()).thenReturn(zeilen(
                new Object[]{1L, "Max Mustermann", null, "0049123456789"}));

        Map<Schluessel, TelefonbuchEintrag> soll = sollListe.berechne();

        assertThat(soll).containsOnlyKeys(new Schluessel(TelefonbuchQuelle.KUNDE, 1L));
        assertThat(soll.values().iterator().next().nummern()).containsExactly(n("0123456789", NummerArt.GESCHAEFTLICH));
    }

    @Test
    @DisplayName("Handynummer im Festnetz-Feld bekommt den Typ Handy")
    void handyImFestnetzfeld() {
        when(lieferanten.findeFuerTelefonbuch()).thenReturn(zeilen(new Object[]{10L, "Würth", "0171 1234567", null}));

        assertThat(sollListe.berechne().values().iterator().next().nummern())
                .containsExactly(n("01711234567", NummerArt.MOBIL));
    }

    @Test
    @DisplayName("Ohne Namen kein Eintrag; höchstens 9 Nummern je Eintrag")
    void ohneNamenUndHoechstzahl() {
        when(lieferanten.findeFuerTelefonbuch()).thenReturn(zeilen(new Object[]{10L, "  ", "0123 555555", null}));
        List<Object[]> viele = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            viele.add(new Object[]{1L, "Max Mustermann", Anrede.HERR, "0123 45678" + i});
        }
        when(zusatznummern.findeKundenNummernFuerTelefonbuch()).thenReturn(viele);

        Map<Schluessel, TelefonbuchEintrag> soll = sollListe.berechne();

        assertThat(soll).containsOnlyKeys(new Schluessel(TelefonbuchQuelle.KUNDE, 1L));
        assertThat(soll.values().iterator().next().nummern()).hasSize(TelefonbuchSollListe.MAX_NUMMERN);
    }

    @Test
    @DisplayName("Wählbare Schreibweise: national mit 0, Ausland mit +, Ortsnetz, Unsinn verworfen")
    void waehlbar() {
        assertThat(TelefonbuchSollListe.waehlbar("+49 (0) 123 456789", "49", "123")).isEqualTo("0123456789");
        assertThat(TelefonbuchSollListe.waehlbar("0043 1 234567", "49", "123")).isEqualTo("+431234567");
        assertThat(TelefonbuchSollListe.waehlbar("456789", "49", "123")).isEqualTo("0123456789");
        assertThat(TelefonbuchSollListe.waehlbar("456789", "49", null)).isEqualTo("456789");
        assertThat(TelefonbuchSollListe.waehlbar("0123 456789", null, null)).isEqualTo("0123456789");
        assertThat(TelefonbuchSollListe.waehlbar("12", "49", "123")).isNull();
        assertThat(TelefonbuchSollListe.waehlbar("  ", "49", "123")).isNull();
        assertThat(TelefonbuchSollListe.waehlbar(null, "49", "123")).isNull();
        assertThat(TelefonbuchSollListe.waehlbar("0123 4567890123456789012345678901234", "49", "123")).isNull();
    }

    @Test
    @DisplayName("Lange Namen werden gekürzt, die Art bleibt sichtbar; Steuerzeichen und Leerraum bereinigt")
    void namen() {
        String lang = "Musterfirma für Metallbau, Stahlbau und Schlosserarbeiten aller Art GmbH & Co. KG";
        String name = TelefonbuchSollListe.anzeigename(lang, "Lieferant");
        assertThat(name).hasSize(TelefonbuchSollListe.MAX_NAME).endsWith("… (Lieferant)").startsWith("Musterfirma für Metallbau");

        String person = TelefonbuchSollListe.anzeigename("Max Mustermann",
                "Steuerberater Kanzlei Beispiel und Partner Steuerberatungsgesellschaft mbB");
        assertThat(person).hasSizeLessThanOrEqualTo(TelefonbuchSollListe.MAX_NAME)
                .startsWith("Max Mustermann (Steuerberater Kanzlei").endsWith("…)");

        assertThat(TelefonbuchSollListe.anzeigename("  Max \t\n Muster\u0000mann ", "Kunde")).isEqualTo("Max Mustermann (Kunde)");
        assertThat(TelefonbuchSollListe.bereinige(null)).isEmpty();
    }

    @Test
    @DisplayName("Festnetzart bei Kunden nach Anrede")
    void festnetzArt() {
        assertThat(TelefonbuchSollListe.festnetzArtKunde(Anrede.FAMILIE)).isEqualTo(NummerArt.PRIVAT);
        assertThat(TelefonbuchSollListe.festnetzArtKunde(Anrede.DAMEN_HERREN)).isEqualTo(NummerArt.GESCHAEFTLICH);
        assertThat(TelefonbuchSollListe.festnetzArtKunde(null)).isEqualTo(NummerArt.GESCHAEFTLICH);
    }

    @Test
    @DisplayName("Inhalts-Hash ändert sich mit Name, Nummer oder Nummernart – sonst nicht")
    void hash() {
        TelefonbuchEintrag a = new TelefonbuchEintrag("Max Mustermann (Kunde)", List.of(n("0123456789", NummerArt.PRIVAT)));
        assertThat(a.inhaltHash()).hasSize(64)
                .isEqualTo(new TelefonbuchEintrag("Max Mustermann (Kunde)", List.of(n("0123456789", NummerArt.PRIVAT))).inhaltHash())
                .isNotEqualTo(new TelefonbuchEintrag("Max Mustermann (Lieferant)", List.of(n("0123456789", NummerArt.PRIVAT))).inhaltHash())
                .isNotEqualTo(new TelefonbuchEintrag("Max Mustermann (Kunde)", List.of(n("0123456780", NummerArt.PRIVAT))).inhaltHash())
                .isNotEqualTo(new TelefonbuchEintrag("Max Mustermann (Kunde)", List.of(n("0123456789", NummerArt.MOBIL))).inhaltHash());
    }
}
