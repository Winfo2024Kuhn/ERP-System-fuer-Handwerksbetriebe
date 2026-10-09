package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
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
}
