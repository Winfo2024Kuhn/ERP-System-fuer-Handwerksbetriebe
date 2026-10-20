package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.Email.EmailDraftDto;
import org.example.kalkulationsprogramm.service.EmailDraftService;
import org.example.kalkulationsprogramm.service.EmailDraftService.Leser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;

/** Entwürfe sind persönlich: Jeder sieht seine eigenen und alte ohne Besitzer, Admins alle (Issue #271). */
@DataJpaTest
@Import(EmailDraftService.class)
class EmailDraftBesitzerTest {

    @Autowired private EmailDraftService service;
    @Autowired private EntityManager entityManager;

    private Leser max;
    private Leser erika;
    private final Leser admin = new Leser(null, true);

    private FrontendUserProfile benutzer(String username, String anzeigename) {
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setUsername(username);
        profil.setDisplayName(anzeigename);
        entityManager.persist(profil);
        return profil;
    }

    private static EmailDraftDto entwurf(String betreff) {
        return new EmailDraftDto(null, "kunde@example.org", null, betreff, "<p>Hallo</p>",
                null, null, null, null, false, null, null, null, null, null, List.of());
    }

    private List<String> betreffe(Leser leser) {
        return service.list(leser).stream().map(EmailDraftDto::subject).toList();
    }

    @BeforeEach
    void benutzerAnlegen() {
        max = new Leser(benutzer("max", "Max Mustermann"), false);
        erika = new Leser(benutzer("erika", "Erika Musterfrau"), false);
    }

    @Test
    @DisplayName("Jeder sieht nur eigene und alte Entwürfe, Admins sehen alle")
    void sichtbarkeit() {
        service.save(null, entwurf("von Max"), List.of(), max);
        service.save(null, entwurf("von Erika"), List.of(), erika);
        service.save(null, entwurf("alt ohne Besitzer"), List.of());
        entityManager.flush();
        entityManager.clear();

        assertThat(betreffe(max)).containsExactlyInAnyOrder("von Max", "alt ohne Besitzer");
        assertThat(betreffe(erika)).containsExactlyInAnyOrder("von Erika", "alt ohne Besitzer");
        assertThat(betreffe(admin)).containsExactlyInAnyOrder("von Max", "von Erika", "alt ohne Besitzer");
        assertThat(betreffe(new Leser(null, false))).containsExactly("alt ohne Besitzer");
    }

    @Test
    @DisplayName("Besitzer und Anzeigename kommen aus der Anmeldung, nicht aus dem Request")
    void besitzerAusAnmeldung() {
        EmailDraftDto gefaelscht = new EmailDraftDto(null, "kunde@example.org", null, "Betreff", "<p>x</p>",
                null, null, null, null, false, null, null, null, null, null, List.of(),
                erika.benutzer().getId(), "Erika Musterfrau");

        EmailDraftDto gespeichert = service.save(null, gefaelscht, List.of(), max);

        assertThat(gespeichert.besitzerId()).isEqualTo(max.benutzer().getId());
        assertThat(gespeichert.erstelltVon()).isEqualTo("Max Mustermann");
    }

    @Test
    @DisplayName("Fremde Entwürfe gibt es nicht: lesen, ändern, löschen und versenden ergeben 404")
    void fremdeEntwuerfe404() {
        Long id = service.save(null, entwurf("von Max"), List.of(), max).id();
        entityManager.flush();
        entityManager.clear();

        for (org.junit.jupiter.api.function.Executable zugriff : List.<org.junit.jupiter.api.function.Executable>of(
                () -> service.get(id, erika),
                () -> service.save(id, entwurf("überschrieben"), List.of(), erika),
                () -> service.delete(id, erika),
                () -> service.validateForSending(id, null, erika))) {
            assertThatThrownBy(zugriff::execute).isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        }
        assertThat(service.get(id, max).subject()).isEqualTo("von Max");
    }

    @Test
    @DisplayName("Ein Admin ändert einen fremden Entwurf, der Besitzer bleibt")
    void adminAendertOhneBesitzwechsel() {
        Long id = service.save(null, entwurf("von Max"), List.of(), max).id();

        EmailDraftDto geaendert = service.save(id, entwurf("vom Chef korrigiert"), List.of(), admin);

        assertThat(geaendert.subject()).isEqualTo("vom Chef korrigiert");
        assertThat(geaendert.besitzerId()).isEqualTo(max.benutzer().getId());
    }

    @Test
    @DisplayName("Leser aus der Anmeldung: Benutzername ohne Groß-/Kleinschreibung, Admin über ROLE_ADMIN")
    void leserAusAnmeldung() {
        Leser angemeldet = service.leserFuer(new UsernamePasswordAuthenticationToken(" MAX ", null,
                AuthorityUtils.createAuthorityList("ROLE_USER")));
        assertThat(angemeldet.benutzer().getUsername()).isEqualTo("max");
        assertThat(angemeldet.admin()).isFalse();

        assertThat(service.leserFuer(new UsernamePasswordAuthenticationToken("chef", null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"))).admin()).isTrue();
        assertThat(service.leserFuer(null)).isEqualTo(new Leser(null, false));
        assertThat(service.leserFuer(new AnonymousAuthenticationToken("k", "anonym",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")))).isEqualTo(new Leser(null, false));
    }
}
