package org.example.kalkulationsprogramm.service.ids;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.ids.IdsZugang;
import org.example.kalkulationsprogramm.dto.Ids.IdsZugangDto;
import org.example.kalkulationsprogramm.dto.Ids.IdsZugangSpeichernDto;
import org.example.kalkulationsprogramm.repository.IdsZugangRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class IdsEinstellungenServiceTest {

    @Mock private SystemSettingsService settings;
    @Mock private MailSecretService secrets;
    @Mock private IdsZugangRepository zugangRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @InjectMocks private IdsEinstellungenService service;

    private Lieferanten wuerth;

    @BeforeEach
    void setUp() {
        wuerth = new Lieferanten();
        wuerth.setId(7L);
        wuerth.setLieferantenname("Würth Musterstadt");
    }

    private IdsZugang zugang(boolean aktiv, String url, String passwort) {
        IdsZugang z = new IdsZugang();
        z.setLieferant(wuerth);
        z.setAktiv(aktiv);
        z.setShopUrl(url);
        z.setPasswortVerschluesselt(passwort);
        return z;
    }

    private static IdsZugangSpeichernDto dto(boolean aktiv, String url, String passwort) {
        return new IdsZugangSpeichernDto(aktiv, IdsProtokoll.IDS_CONNECT_2_5, url, " 4711 ", "max", passwort);
    }

    @Test
    @DisplayName("Laden zeigt Schalter, Schlüssel-Zustand und Zugänge – nie das Passwort")
    void lade() {
        when(settings.get("ids.aktiv", "false")).thenReturn("true");
        when(secrets.isConfigured()).thenReturn(true);
        when(zugangRepository.findeAlleMitLieferant()).thenReturn(List.of(zugang(true, "https://shop", "v1:xyz")));

        var dto = service.lade();

        assertThat(dto.aktiv()).isTrue();
        assertThat(dto.verschluesselungEingerichtet()).isTrue();
        assertThat(dto.zugaenge()).singleElement().satisfies(z -> {
            assertThat(z.lieferantName()).isEqualTo("Würth Musterstadt");
            assertThat(z.passwortGesetzt()).isTrue();
        });
    }

    @Test
    void setzeAktiv() {
        when(zugangRepository.findeAlleMitLieferant()).thenReturn(List.of());
        service.setzeAktiv(true);
        verify(settings).save(eq("ids.aktiv"), eq("true"), anyString());
    }

    @Test
    @DisplayName("Neuer Zugang: Passwort verschlüsselt, Felder getrimmt")
    void speichereNeu() {
        when(lieferantenRepository.findById(7L)).thenReturn(Optional.of(wuerth));
        when(zugangRepository.findByLieferant_Id(7L)).thenReturn(Optional.empty());
        when(secrets.isConfigured()).thenReturn(true);
        when(secrets.encrypt("geheim")).thenReturn("v1:verschluesselt");
        when(zugangRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        IdsZugangDto gespeichert = service.speichereZugang(7L, dto(true, " https://shop.example.com/ids ", "geheim"));

        assertThat(gespeichert.shopUrl()).isEqualTo("https://shop.example.com/ids");
        assertThat(gespeichert.kundennummer()).isEqualTo("4711");
        assertThat(gespeichert.passwortGesetzt()).isTrue();
        assertThat(gespeichert.geaendertAm()).isNotNull();
    }

    @Test
    @DisplayName("Leeres Passwort lässt das bisherige stehen")
    void passwortBleibt() {
        IdsZugang bestehend = zugang(true, "https://alt", "v1:alt");
        when(lieferantenRepository.findById(7L)).thenReturn(Optional.of(wuerth));
        when(zugangRepository.findByLieferant_Id(7L)).thenReturn(Optional.of(bestehend));
        when(zugangRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.speichereZugang(7L, new IdsZugangSpeichernDto(false, null, null, null, null, ""));

        assertThat(bestehend.getPasswortVerschluesselt()).isEqualTo("v1:alt");
        assertThat(bestehend.getProtokoll()).isEqualTo(IdsProtokoll.IDS_CONNECT_2_5);
        assertThat(bestehend.getShopUrl()).isNull();
        verify(secrets, never()).encrypt(any());
    }

    @Test
    @DisplayName("Ungültige Eingaben: verständliche 400/404")
    void ungueltig() {
        assertThatThrownBy(() -> service.speichereZugang(7L, null)).hasMessageContaining("Angaben");
        when(lieferantenRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.speichereZugang(99L, dto(false, null, null))).hasMessageContaining("nicht gefunden");

        when(lieferantenRepository.findById(7L)).thenReturn(Optional.of(wuerth));
        assertThatThrownBy(() -> service.speichereZugang(7L, dto(true, null, null))).hasMessageContaining("Shop-Adresse eintragen");
        assertThatThrownBy(() -> service.speichereZugang(7L, dto(false, "http://shop.example.com", null))).hasMessageContaining("https://");
        assertThatThrownBy(() -> service.speichereZugang(7L, dto(false, "javascript:alert(1)", null))).hasMessageContaining("gültige");
        assertThatThrownBy(() -> service.speichereZugang(7L, dto(false, "https://" + "a".repeat(600), null))).hasMessageContaining("zu lang");
        assertThatThrownBy(() -> service.speichereZugang(7L, dto(false, "https://exa mple", null))).hasMessageContaining("gültige");
        assertThatThrownBy(() -> service.speichereZugang(7L,
                new IdsZugangSpeichernDto(false, null, null, "1".repeat(51), null, null))).hasMessageContaining("Kundennummer");
        assertThatThrownBy(() -> service.speichereZugang(7L,
                new IdsZugangSpeichernDto(false, null, null, null, null, "p".repeat(101)))).hasMessageContaining("Passwort");

        when(zugangRepository.findByLieferant_Id(7L)).thenReturn(Optional.empty());
        when(secrets.isConfigured()).thenReturn(false);
        assertThatThrownBy(() -> service.speichereZugang(7L, dto(false, "http://localhost:9000", "geheim")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Verschlüsselungsschlüssel");
    }

    @Test
    void loesche() {
        IdsZugang z = zugang(true, "https://shop", null);
        when(zugangRepository.findByLieferant_Id(7L)).thenReturn(Optional.of(z));
        service.loescheZugang(7L);
        verify(zugangRepository).delete(z);

        when(zugangRepository.findByLieferant_Id(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loescheZugang(8L)).hasMessageContaining("kein Shop-Zugang");
    }

    @Test
    @DisplayName("Verfügbar nur mit Grundschalter und nutzbarem Zugang")
    void verfuegbar() {
        when(settings.get("ids.aktiv", "false")).thenReturn("false");
        assertThat(service.verfuegbar().aktiv()).isFalse();

        when(settings.get("ids.aktiv", "false")).thenReturn("true");
        Lieferanten berner = new Lieferanten();
        berner.setId(8L);
        berner.setLieferantenname("Berner");
        IdsZugang ohneUrl = zugang(true, " ", null);
        IdsZugang ohneSchluessel = zugang(true, "https://shop", "v1:x");
        IdsZugang ok = zugang(true, "https://shop", null);
        ok.setLieferant(berner);
        when(secrets.isConfigured()).thenReturn(false);
        when(zugangRepository.findeAlleMitLieferant()).thenReturn(List.of(ohneUrl, ohneSchluessel, ok));

        var v = service.verfuegbar();
        assertThat(v.aktiv()).isTrue();
        assertThat(v.lieferanten()).singleElement().satisfies(l -> {
            assertThat(l.lieferantName()).isEqualTo("Berner");
            assertThat(l.passwortGesetzt()).isFalse();
        });

        when(zugangRepository.findeAlleMitLieferant()).thenReturn(List.of(ohneUrl));
        assertThat(service.verfuegbar().aktiv()).isFalse();
    }

    @Test
    @DisplayName("Nutzbarer Zugang: entschlüsselt; aus, ohne Zugang oder unlesbares Passwort ergibt 400")
    void nutzbarerZugang() {
        when(settings.get("ids.aktiv", "false")).thenReturn("false");
        assertThatThrownBy(() -> service.nutzbarerZugang(7L)).hasMessageContaining("ausgeschaltet");

        when(settings.get("ids.aktiv", "false")).thenReturn("true");
        when(zugangRepository.findByLieferant_Id(7L)).thenReturn(Optional.of(zugang(false, "https://shop", null)));
        assertThatThrownBy(() -> service.nutzbarerZugang(7L)).hasMessageContaining("kein eingeschalteter");

        IdsZugang z = zugang(true, "https://shop", "v1:x");
        z.setKundennummer("4711");
        when(zugangRepository.findByLieferant_Id(7L)).thenReturn(Optional.of(z));
        when(secrets.decrypt("v1:x")).thenReturn("geheim");
        var nutzbar = service.nutzbarerZugang(7L);
        assertThat(nutzbar.daten().passwort()).isEqualTo("geheim");
        assertThat(nutzbar.daten().toString()).doesNotContain("geheim");
        assertThat(nutzbar.lieferant()).isSameAs(wuerth);

        when(secrets.decrypt("v1:x")).thenThrow(new IllegalStateException("kaputt"));
        assertThatThrownBy(() -> service.nutzbarerZugang(7L)).hasMessageContaining("nicht lesbar");

        z.setPasswortVerschluesselt(null);
        assertThat(service.nutzbarerZugang(7L).daten().passwort()).isNull();
    }
}
