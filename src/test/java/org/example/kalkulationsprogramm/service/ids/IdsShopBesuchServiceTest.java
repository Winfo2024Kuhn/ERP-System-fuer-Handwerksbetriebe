package org.example.kalkulationsprogramm.service.ids;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgang;
import org.example.kalkulationsprogramm.domain.ids.IdsVorgangStatus;
import org.example.kalkulationsprogramm.repository.IdsVorgangRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class IdsShopBesuchServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Instant JETZT = Instant.parse("2026-10-31T10:00:00Z");
    private static final String XML = "<Warenkorb><WarenkorbInfo><RueckgabeKZ>Warenkorbrückgabe</RueckgabeKZ></WarenkorbInfo>"
            + "<Order><OrderItem><ArtNo>0892 161</ArtNo><Qty>5</Qty><QU>PCE</QU><Kurztext>Silikon</Kurztext>"
            + "<NetPrice>8.40</NetPrice></OrderItem></Order></Warenkorb>";

    private IdsVorgangRepository repo;
    private IdsEinstellungenService einstellungen;
    private IdsShopBesuchService service;
    private Lieferanten wuerth;

    @BeforeEach
    void setUp() {
        repo = mock(IdsVorgangRepository.class);
        einstellungen = mock(IdsEinstellungenService.class);
        service = new IdsShopBesuchService(repo, einstellungen, List.of(new IdsConnect25Adapter()), Clock.fixed(JETZT, ZONE));
        wuerth = new Lieferanten();
        wuerth.setId(7L);
        wuerth.setLieferantenname("Würth");
    }

    private IdsVorgang wartend(LocalDateTime gueltigBis) {
        IdsVorgang v = new IdsVorgang();
        v.setId(42L);
        v.setLieferant(wuerth);
        v.setFrontendUserId(70L);
        v.setProtokoll(IdsProtokoll.IDS_CONNECT_2_5);
        v.setStatus(IdsVorgangStatus.WARTET);
        v.setGueltigBis(gueltigBis);
        return v;
    }

    private static IdsRueckgabe rueckgabe(String xml) {
        return new IdsRueckgabe(xml.getBytes(StandardCharsets.UTF_8), null);
    }

    @Test
    @DisplayName("Start: Vorgang mit gehashtem Schlüssel, 60 Minuten gültig, Rücksprung zeigt auf diesen Schlüssel")
    void starte() {
        when(einstellungen.nutzbarerZugang(7L)).thenReturn(new IdsEinstellungenService.NutzbarerZugang(wuerth,
                IdsProtokoll.IDS_CONNECT_2_5, new IdsZugangsdaten("https://shop.example.com/ids", "4711", "max", "geheim")));
        when(repo.save(any())).thenAnswer(i -> {
            IdsVorgang v = i.getArgument(0);
            v.setId(42L);
            return v;
        });

        IdsShopBesuchService.Gestartet g = service.starte(7L, 70L, "https://erp.example.com");

        ArgumentCaptor<IdsVorgang> captor = ArgumentCaptor.forClass(IdsVorgang.class);
        verify(repo).save(captor.capture());
        IdsVorgang v = captor.getValue();
        assertThat(g.vorgangId()).isEqualTo(42L);
        assertThat(v.getFrontendUserId()).isEqualTo(70L);
        assertThat(v.getStatus()).isEqualTo(IdsVorgangStatus.WARTET);
        LocalDateTime jetzt = LocalDateTime.ofInstant(JETZT, ZONE);
        assertThat(v.getGueltigBis()).isEqualTo(jetzt.plusMinutes(60));
        verify(repo).loescheAelterAls(jetzt.minusDays(30));
        verify(repo).loeschePositionenAelterAls(jetzt.minusDays(30));

        String hook = g.formular().felder().get("hookurl");
        assertThat(hook).startsWith("https://erp.example.com/api/ids/rueckgabe/");
        String schluessel = hook.substring(hook.lastIndexOf('/') + 1);
        assertThat(schluessel).hasSize(43);
        assertThat(v.getSchluesselHash()).isEqualTo(IdsShopBesuchService.hash(schluessel)).isNotEqualTo(schluessel);
    }

    @Test
    void starteOhneBenutzer() {
        assertThatThrownBy(() -> service.starte(7L, null, "https://erp")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(einstellungen);
    }

    @Test
    @DisplayName("Rücksprung: Warenkorb wird abgelegt, Schlüssel ist danach verbraucht")
    void rueckgabeOk() {
        String schluessel = "a".repeat(43);
        IdsVorgang v = wartend(LocalDateTime.ofInstant(JETZT, ZONE).plusMinutes(5));
        when(repo.findBySchluesselHashGesperrt(IdsShopBesuchService.hash(schluessel))).thenReturn(Optional.of(v));

        var r = service.nimmRueckgabeAn(schluessel, rueckgabe(XML));

        assertThat(r.ergebnis()).isEqualTo(IdsShopBesuchService.Ergebnis.OK);
        assertThat(r.vorgangId()).isEqualTo(42L);
        assertThat(v.getRohdaten()).startsWith("<Warenkorb>");
        assertThat(new IdsRueckgabe(null, "a\u0000b").alsText()).isEqualTo("ab");
        assertThat(new IdsRueckgabe(null, null).alsText()).isNull();
        assertThat(v.getStatus()).isEqualTo(IdsVorgangStatus.EMPFANGEN);
        assertThat(v.getEmpfangenAm()).isNotNull();
        assertThat(v.getPositionen()).singleElement().satisfies(p -> {
            assertThat(p.getPositionIndex()).isZero();
            assertThat(p.getArtikelnummer()).isEqualTo("0892 161");
            assertThat(p.getEinkaufspreis()).isEqualByComparingTo(new BigDecimal("1.68"));
            assertThat(p.getVorgang()).isSameAs(v);
        });

        assertThat(service.nimmRueckgabeAn(schluessel, rueckgabe(XML)).ergebnis())
                .isEqualTo(IdsShopBesuchService.Ergebnis.UNGUELTIG);
    }

    @Test
    @DisplayName("Rücksprung: falsches Format, unbekannt, abgelaufen, leer, kaputt")
    void rueckgabeFehler() {
        assertThat(service.nimmRueckgabeAn(null, rueckgabe(XML)).ergebnis()).isEqualTo(IdsShopBesuchService.Ergebnis.UNGUELTIG);
        assertThat(service.nimmRueckgabeAn("../../etc/passwd", rueckgabe(XML)).ergebnis()).isEqualTo(IdsShopBesuchService.Ergebnis.UNGUELTIG);
        assertThat(service.nimmRueckgabeAn("b".repeat(43), rueckgabe(XML)).ergebnis()).isEqualTo(IdsShopBesuchService.Ergebnis.UNGUELTIG);

        String abgelaufen = "c".repeat(43);
        IdsVorgang alt = wartend(LocalDateTime.ofInstant(JETZT, ZONE).minusMinutes(1));
        when(repo.findBySchluesselHashGesperrt(IdsShopBesuchService.hash(abgelaufen))).thenReturn(Optional.of(alt));
        assertThat(service.nimmRueckgabeAn(abgelaufen, rueckgabe(XML)).ergebnis()).isEqualTo(IdsShopBesuchService.Ergebnis.ABGELAUFEN);
        assertThat(alt.getStatus()).isEqualTo(IdsVorgangStatus.ABGELAUFEN);

        String leer = "d".repeat(43);
        IdsVorgang v1 = wartend(LocalDateTime.ofInstant(JETZT, ZONE).plusMinutes(5));
        when(repo.findBySchluesselHashGesperrt(IdsShopBesuchService.hash(leer))).thenReturn(Optional.of(v1));
        assertThat(service.nimmRueckgabeAn(leer, new IdsRueckgabe(null, null)).ergebnis()).isEqualTo(IdsShopBesuchService.Ergebnis.LEER);
        assertThat(v1.getStatus()).isEqualTo(IdsVorgangStatus.FEHLER);

        String kaputt = "e".repeat(43);
        IdsVorgang v2 = wartend(LocalDateTime.ofInstant(JETZT, ZONE).plusMinutes(5));
        when(repo.findBySchluesselHashGesperrt(IdsShopBesuchService.hash(kaputt))).thenReturn(Optional.of(v2));
        assertThat(service.nimmRueckgabeAn(kaputt, rueckgabe("<script>alert(1)</script>")).ergebnis())
                .isEqualTo(IdsShopBesuchService.Ergebnis.FEHLER);
        assertThat(v2.getFehlertext()).contains("keinen IDS-Warenkorb");
    }

    @Test
    @DisplayName("Laden: nur für den Benutzer, der den Shop geöffnet hat; wartend und abgelaufen wird markiert")
    void lade() {
        IdsVorgang v = wartend(LocalDateTime.ofInstant(JETZT, ZONE).minusMinutes(1));
        when(repo.findMitWarenkorb(42L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.lade(42L, 71L)).hasMessageContaining("nicht gefunden");
        assertThatThrownBy(() -> service.lade(42L, null)).hasMessageContaining("nicht gefunden");
        assertThat(service.lade(42L, 70L).getStatus()).isEqualTo(IdsVorgangStatus.ABGELAUFEN);

        IdsVorgang empfangen = wartend(LocalDateTime.ofInstant(JETZT, ZONE).minusMinutes(1));
        empfangen.setStatus(IdsVorgangStatus.EMPFANGEN);
        when(repo.findMitWarenkorb(43L)).thenReturn(Optional.of(empfangen));
        assertThat(service.lade(43L, 70L).getStatus()).isEqualTo(IdsVorgangStatus.EMPFANGEN);
    }

    @Test
    void ohneAdapter() {
        IdsShopBesuchService ohne = new IdsShopBesuchService(repo, einstellungen, List.of());
        when(einstellungen.nutzbarerZugang(7L)).thenReturn(new IdsEinstellungenService.NutzbarerZugang(wuerth,
                IdsProtokoll.IDS_CONNECT_2_5, new IdsZugangsdaten("https://shop", null, null, null)));
        assertThatThrownBy(() -> ohne.starte(7L, 70L, "https://erp")).isInstanceOf(IllegalStateException.class);
    }
}
