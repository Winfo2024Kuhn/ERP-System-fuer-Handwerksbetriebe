package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAttachmentViewDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantKommunikationDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantStatistikDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailFileDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

@ExtendWith(MockitoExtension.class)
class LieferantDokumentZugriffServiceTest {

    @Mock
    private BelegService belegService;
    @Mock
    private LieferantDokumentService dokumentService;

    @InjectMocks
    private LieferantDokumentZugriffService service;

    private static Authentication session(FrontendUserRole rolle) {
        var principal = new FrontendUserPrincipal(1L, "max.mustermann", "Max Mustermann", "hash", true,
                Set.of(rolle));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static Mitarbeiter mitarbeiter(long id) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        return m;
    }

    private void berechtigt(long mitarbeiterId, LieferantDokumentTyp... typen) {
        given(dokumentService.getBerechtigungen(mitarbeiterId)).willReturn(
                LieferantDokumentDto.BerechtigungenResponse.builder()
                        .sichtbareTypen(List.of(typen)).scanbarTypen(List.of()).build());
    }

    @Test
    @DisplayName("Admin-Session sieht alle Dokumenttypen, ohne Abteilungsrechte zu prüfen")
    void adminSiehtAlles() {
        var typen = service.sichtbareTypen(null, session(FrontendUserRole.ADMIN));

        assertThat(typen).contains(EnumSet.allOf(LieferantDokumentTyp.class));
        verifyNoInteractions(dokumentService);
    }

    @Test
    @DisplayName("Normaler Benutzer sieht nur die Typen seiner Abteilungen")
    void benutzerSiehtNurAbteilungsTypen() {
        var auth = session(FrontendUserRole.USER);
        given(belegService.findCaller(null, auth)).willReturn(mitarbeiter(5L));
        berechtigt(5L, LieferantDokumentTyp.RECHNUNG, LieferantDokumentTyp.LIEFERSCHEIN);

        var typen = service.sichtbareTypen(null, auth);

        assertThat(typen).contains(EnumSet.of(LieferantDokumentTyp.RECHNUNG, LieferantDokumentTyp.LIEFERSCHEIN));
    }

    @Test
    @DisplayName("Angemeldeter Benutzer ohne verknüpften Mitarbeiter sieht nichts (kein 401)")
    void benutzerOhneMitarbeiterSiehtNichts() {
        var auth = session(FrontendUserRole.USER);
        given(belegService.findCaller(null, auth)).willReturn(null);

        var typen = service.sichtbareTypen(null, auth);

        assertThat(typen).isPresent();
        assertThat(typen.get()).isEmpty();
        assertThat(typen.get().contains(null)).isFalse();
    }

    @Test
    @DisplayName("Mitarbeiter ohne Abteilung bzw. ohne Rechte sieht nichts")
    void mitarbeiterOhneRechteSiehtNichts() {
        var auth = session(FrontendUserRole.USER);
        given(belegService.findCaller(null, auth)).willReturn(mitarbeiter(6L));
        berechtigt(6L);

        assertThat(service.sichtbareTypen(null, auth)).get().satisfies(t -> assertThat(t).isEmpty());
    }

    @Test
    @DisplayName("Gültiges Mobile-Token gewinnt vor der Session")
    void tokenGewinntVorSession() {
        given(belegService.findByToken("tok")).willReturn(mitarbeiter(8L));
        berechtigt(8L, LieferantDokumentTyp.LIEFERSCHEIN);

        var typen = service.sichtbareTypen("tok", session(FrontendUserRole.ADMIN));

        assertThat(typen).contains(EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN));
    }

    @Test
    @DisplayName("Ungültiges Token ohne Session ist nicht angemeldet")
    void ungueltigesTokenOhneSession() {
        given(belegService.findByToken("'; DROP TABLE x; --")).willReturn(null);

        assertThat(service.sichtbareTypen("'; DROP TABLE x; --", null)).isEmpty();
    }

    @Test
    @DisplayName("Anonyme Authentifizierung zählt nicht als angemeldet")
    void anonymIstNichtAngemeldet() {
        var anonym = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(service.sichtbareTypen(null, anonym)).isEmpty();
        assertThat(service.sichtbareTypen(null, null)).isEmpty();
    }

    @Test
    @DisplayName("Detailantwort: nicht sichtbare Dokumente und ihr Zähler werden entfernt")
    void beschraenkeDokumenteFiltertListeUndZaehler() {
        var auth = session(FrontendUserRole.USER);
        given(belegService.findCaller(null, auth)).willReturn(mitarbeiter(5L));
        berechtigt(5L, LieferantDokumentTyp.RECHNUNG);
        var detail = new LieferantDetailDto();
        detail.setDokumente(List.of(
                LieferantDokumentDto.Response.builder().id(1L).typ(LieferantDokumentTyp.RECHNUNG).build(),
                LieferantDokumentDto.Response.builder().id(2L).typ(LieferantDokumentTyp.ANGEBOT).build()));
        detail.setDokumenteAnzahl(2L);

        service.beschraenkeDokumente(detail, null, auth);

        assertThat(detail.getDokumente()).extracting(LieferantDokumentDto.Response::getId).containsExactly(1L);
        assertThat(detail.getDokumenteAnzahl()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Detailantwort: nicht zuordenbarer Aufrufer bekommt keine Dokumente")
    void beschraenkeDokumenteOhneAufrufer() {
        var detail = new LieferantDetailDto();
        detail.setDokumente(List.of(
                LieferantDokumentDto.Response.builder().id(1L).typ(LieferantDokumentTyp.RECHNUNG).build()));

        service.beschraenkeDokumente(detail, null, null);

        assertThat(detail.getDokumente()).isEmpty();
        assertThat(detail.getDokumenteAnzahl()).isZero();
    }

    @Test
    @DisplayName("Detailantwort ohne Dokumentliste (nurStammdaten): Zähler zählt nur sichtbare Typen")
    void beschraenkeDokumenteOhneListeZaehltNurSichtbare() {
        var auth = session(FrontendUserRole.USER);
        given(belegService.findCaller(null, auth)).willReturn(mitarbeiter(5L));
        berechtigt(5L, LieferantDokumentTyp.RECHNUNG);
        given(dokumentService.zaehleDokumente(7L, EnumSet.of(LieferantDokumentTyp.RECHNUNG))).willReturn(3L);
        var detail = new LieferantDetailDto();
        detail.setId(7L);
        detail.setDokumenteAnzahl(25L);

        service.beschraenkeDokumente(detail, null, auth);

        assertThat(detail.getDokumente()).isNull();
        assertThat(detail.getDokumenteAnzahl()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Detailantwort ohne Dokumentliste: Admin behält den Zähler ohne Zusatzabfrage")
    void beschraenkeDokumenteOhneListeAdminBehaeltZaehler() {
        var detail = new LieferantDetailDto();
        detail.setId(7L);
        detail.setDokumenteAnzahl(25L);

        service.beschraenkeDokumente(detail, null, session(FrontendUserRole.ADMIN));

        assertThat(detail.getDokumenteAnzahl()).isEqualTo(25L);
        verifyNoInteractions(dokumentService);
    }

    @Test
    @DisplayName("Ungültiges Token, aber gültige PC-Session: die Session zählt")
    void ungueltigesTokenMitSessionNutztSession() {
        given(belegService.findByToken("abgelaufen")).willReturn(null);

        var typen = service.sichtbareTypen("abgelaufen", session(FrontendUserRole.ADMIN));

        assertThat(typen).contains(EnumSet.allOf(LieferantDokumentTyp.class));
    }

    private static ProjektEmailDto email(long id, long... anhangIds) {
        var dto = new ProjektEmailDto();
        dto.setId(id);
        var anhaenge = new java.util.ArrayList<ProjektEmailFileDto>();
        for (long anhangId : anhangIds) {
            var datei = new ProjektEmailFileDto();
            datei.setId(anhangId);
            anhaenge.add(datei);
        }
        dto.setAttachments(anhaenge);
        return dto;
    }

    @Test
    @DisplayName("Einzelnes Dokument: sichtbarer Typ bleibt, Verknüpfungen nicht sichtbarer Typen entfallen")
    void beschraenkeDokumentFiltertVerknuepfungen() {
        var dokument = LieferantDokumentDto.Response.builder().id(1L).typ(LieferantDokumentTyp.LIEFERSCHEIN)
                .verknuepfteDokumente(List.of(
                        LieferantDokumentDto.VerknuepftesDoc.builder().id(2L).typ(LieferantDokumentTyp.RECHNUNG).build(),
                        LieferantDokumentDto.VerknuepftesDoc.builder().id(3L).typ(LieferantDokumentTyp.LIEFERSCHEIN).build()))
                .build();

        var ergebnis = service.beschraenkeDokument(dokument, EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN));

        assertThat(ergebnis).isPresent();
        assertThat(ergebnis.get().getVerknuepfteDokumente())
                .extracting(LieferantDokumentDto.VerknuepftesDoc::getId).containsExactly(3L);
    }

    @Test
    @DisplayName("Einzelnes Dokument: nicht sichtbarer Typ, fehlender Typ und null liefern leer")
    void beschraenkeDokumentNichtSichtbar() {
        var sichtbar = EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN);

        assertThat(service.beschraenkeDokument(
                LieferantDokumentDto.Response.builder().id(1L).typ(LieferantDokumentTyp.RECHNUNG).build(), sichtbar))
                .isEmpty();
        assertThat(service.beschraenkeDokument(LieferantDokumentDto.Response.builder().id(2L).build(), sichtbar))
                .isEmpty();
        assertThat(service.beschraenkeDokument(null, sichtbar)).isEmpty();
        assertThat(service.beschraenkeDokument(
                LieferantDokumentDto.Response.builder().id(3L).typ(LieferantDokumentTyp.RECHNUNG).build(),
                EnumSet.noneOf(LieferantDokumentTyp.class))).isEmpty();
    }

    @Test
    @DisplayName("Statistik: Gesamtkosten nur mit Rechnungs-, Bestellungen/Lieferzeit nur mit AB-Recht")
    void beschraenkeStatistikBlendetKennzahlenAus() {
        var statistik = new LieferantStatistikDto();
        statistik.setGesamtKosten(1234.5);
        statistik.setBestellungAnzahl(7);
        statistik.setLieferzeit(14);

        service.beschraenkeStatistik(statistik, EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN));

        assertThat(statistik.getGesamtKosten()).isNull();
        assertThat(statistik.getBestellungAnzahl()).isZero();
        assertThat(statistik.getLieferzeit()).isNull();
    }

    @Test
    @DisplayName("Statistik: sichtbare Typen behalten ihre Kennzahlen, null ist erlaubt")
    void beschraenkeStatistikBehaeltSichtbares() {
        var statistik = new LieferantStatistikDto();
        statistik.setGesamtKosten(99.0);
        statistik.setBestellungAnzahl(2);
        statistik.setLieferzeit(5);

        service.beschraenkeStatistik(statistik,
                EnumSet.of(LieferantDokumentTyp.RECHNUNG, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG));
        service.beschraenkeStatistik(null, EnumSet.noneOf(LieferantDokumentTyp.class));

        assertThat(statistik.getGesamtKosten()).isEqualTo(99.0);
        assertThat(statistik.getBestellungAnzahl()).isEqualTo(2);
        assertThat(statistik.getLieferzeit()).isEqualTo(5);
    }

    @Test
    @DisplayName("E-Mail-Verlauf: Anhänge, die als nicht sichtbares Dokument abgelegt sind, entfallen")
    void beschraenkeEmailVerlaufEntferntGesperrteAnhaenge() {
        var emails = List.of(email(1L, 11L, 12L), email(2L, 13L));
        given(dokumentService.findAnhangIdsMitTyp(anyCollection(), anySet())).willReturn(Set.of(11L));

        service.beschraenkeEmailVerlauf(emails, EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN));

        assertThat(emails.get(0).getAttachments()).extracting(ProjektEmailFileDto::getId).containsExactly(12L);
        assertThat(emails.get(1).getAttachments()).extracting(ProjektEmailFileDto::getId).containsExactly(13L);
        var gesperrteTypen = EnumSet.allOf(LieferantDokumentTyp.class);
        gesperrteTypen.remove(LieferantDokumentTyp.LIEFERSCHEIN);
        verify(dokumentService).findAnhangIdsMitTyp(Set.of(11L, 12L, 13L), gesperrteTypen);
    }

    @Test
    @DisplayName("E-Mail-Verlauf: wer alle Typen sieht, löst keine Zusatzabfrage aus; fehlende Listen sind ok")
    void beschraenkeEmailVerlaufMitAllenTypenOhneAbfrage() {
        var emails = List.of(email(1L, 11L));
        var ohneAnhaenge = new ProjektEmailDto();

        service.beschraenkeEmailVerlauf(emails, EnumSet.allOf(LieferantDokumentTyp.class));
        service.beschraenkeEmailVerlauf(List.of(ohneAnhaenge), EnumSet.noneOf(LieferantDokumentTyp.class));
        service.beschraenkeEmailVerlauf(null, EnumSet.noneOf(LieferantDokumentTyp.class));

        assertThat(emails.get(0).getAttachments()).hasSize(1);
        verifyNoInteractions(dokumentService);
    }

    @Test
    @DisplayName("Kommunikation der Detailantwort: gesperrte Anhänge entfallen")
    void beschraenkeKommunikationEntferntGesperrteAnhaenge() {
        var erlaubt = new LieferantAttachmentViewDto();
        erlaubt.setId(21L);
        var gesperrt = new LieferantAttachmentViewDto();
        gesperrt.setId(22L);
        var kommunikation = new LieferantKommunikationDto();
        kommunikation.setAttachments(List.of(erlaubt, gesperrt));
        given(dokumentService.findAnhangIdsMitTyp(anyCollection(), anySet())).willReturn(Set.of(22L));

        service.beschraenkeKommunikation(List.of(kommunikation), EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN));

        assertThat(kommunikation.getAttachments()).extracting(LieferantAttachmentViewDto::getId)
                .containsExactly(21L);
    }

    @Test
    @DisplayName("Detailantwort: E-Mails, Kommunikation und Kennzahlen werden gemeinsam beschränkt")
    void beschraenkeDokumenteBeschraenktAuchMailsUndStatistik() {
        var auth = session(FrontendUserRole.USER);
        given(belegService.findCaller(null, auth)).willReturn(mitarbeiter(5L));
        berechtigt(5L, LieferantDokumentTyp.LIEFERSCHEIN);
        given(dokumentService.findAnhangIdsMitTyp(anyCollection(), anySet())).willReturn(Set.of(11L));
        var detail = new LieferantDetailDto();
        detail.setEmails(List.of(email(1L, 11L, 12L)));
        var statistik = new LieferantStatistikDto();
        statistik.setGesamtKosten(500.0);
        detail.setStatistik(statistik);

        service.beschraenkeDokumente(detail, null, auth);

        assertThat(detail.getEmails().get(0).getAttachments()).extracting(ProjektEmailFileDto::getId)
                .containsExactly(12L);
        assertThat(detail.getStatistik().getGesamtKosten()).isNull();
    }

    @Test
    @DisplayName("Detailantwort: nicht angemeldet -> keine Rechnungs-Anhänge und keine Kennzahlen")
    void beschraenkeDokumenteOhneAufruferBlendetAllesAus() {
        given(dokumentService.findAnhangIdsMitTyp(anyCollection(), anySet())).willReturn(Set.of(11L));
        var detail = new LieferantDetailDto();
        detail.setEmails(List.of(email(1L, 11L)));
        var statistik = new LieferantStatistikDto();
        statistik.setGesamtKosten(500.0);
        detail.setStatistik(statistik);

        service.beschraenkeDokumente(detail, null, null);

        assertThat(detail.getEmails().get(0).getAttachments()).isEmpty();
        assertThat(detail.getStatistik().getGesamtKosten()).isNull();
    }

    @Test
    @DisplayName("Statistik (Mischfall): nur Rechnungs-Recht -> Gesamtkosten bleiben, Bestellungen/Lieferzeit entfallen")
    void beschraenkeStatistikMischfall() {
        var statistik = new LieferantStatistikDto();
        statistik.setGesamtKosten(10.0);
        statistik.setBestellungAnzahl(3);
        statistik.setLieferzeit(7);

        service.beschraenkeStatistik(statistik, EnumSet.of(LieferantDokumentTyp.RECHNUNG));

        assertThat(statistik.getGesamtKosten()).isEqualTo(10.0);
        assertThat(statistik.getBestellungAnzahl()).isZero();
        assertThat(statistik.getLieferzeit()).isNull();
    }

    @Test
    @DisplayName("Dokument per ID: sichtbar nur bei bekanntem Dokument mit erlaubtem Typ")
    void istSichtbarPerId() {
        var rechnung = new org.example.kalkulationsprogramm.domain.LieferantDokument();
        rechnung.setTyp(LieferantDokumentTyp.RECHNUNG);
        given(dokumentService.findById(1L)).willReturn(rechnung);
        given(dokumentService.findById(2L)).willReturn(null);

        assertThat(service.istSichtbar(1L, EnumSet.of(LieferantDokumentTyp.RECHNUNG))).isTrue();
        assertThat(service.istSichtbar(1L, EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN))).isFalse();
        assertThat(service.istSichtbar(1L, EnumSet.noneOf(LieferantDokumentTyp.class))).isFalse();
        assertThat(service.istSichtbar(2L, EnumSet.allOf(LieferantDokumentTyp.class))).isFalse();
        assertThat(service.istSichtbar(null, EnumSet.allOf(LieferantDokumentTyp.class))).isFalse();
    }

    @Test
    @DisplayName("Mail-Anhang per ID: gesperrt, wenn er als Dokument eines nicht sichtbaren Typs abgelegt ist")
    void istAnhangSichtbarPerId() {
        given(dokumentService.findAnhangIdsMitTyp(anyCollection(), anySet())).willReturn(Set.of(11L));

        assertThat(service.istAnhangSichtbar(11L, EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN))).isFalse();
        // Zweiter Anhang: kein Treffer in der Datenbank -> frei
        given(dokumentService.findAnhangIdsMitTyp(anyCollection(), anySet())).willReturn(Set.of());
        assertThat(service.istAnhangSichtbar(12L, EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN))).isTrue();
        // Wer alle Typen sieht oder keinen Anhang meint, löst keine Abfrage aus
        assertThat(service.istAnhangSichtbar(13L, EnumSet.allOf(LieferantDokumentTyp.class))).isTrue();
        assertThat(service.istAnhangSichtbar(null, EnumSet.noneOf(LieferantDokumentTyp.class))).isTrue();
    }
}
