package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.EmailGelesenRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

@ExtendWith(MockitoExtension.class)
class EmailGelesenServiceTest {

    private static final Authentication ERIKA = UsernamePasswordAuthenticationToken.authenticated("erika", null,
            AuthorityUtils.createAuthorityList("ROLE_USER"));

    @Mock private EmailGelesenRepository repository;
    @Mock private EmailRepository emailRepository;
    @Mock private FrontendUserProfileService frontendUserProfileService;

    private EmailGelesenService service;

    @BeforeEach
    void setUp() {
        service = new EmailGelesenService(repository, emailRepository, frontendUserProfileService);
    }

    private void erikaAngemeldet() {
        FrontendUserProfile erika = new FrontendUserProfile();
        erika.setId(7L);
        erika.setDisplayName("Erika Mustermann");
        given(frontendUserProfileService.findByUsername("erika")).willReturn(Optional.of(erika));
    }

    private static Email mail(long id, EmailDirection richtung, boolean gemeinsamGelesen) {
        Email e = new Email();
        e.setId(id);
        e.setDirection(richtung);
        e.setRead(gemeinsamGelesen);
        return e;
    }

    @Test
    void standOhneAnmeldungIstDasGemeinsameFeld() {
        assertThat(service.fuer(null)).isEqualTo(GelesenStand.GEMEINSAM);
        assertThat(service.fuer(new AnonymousAuthenticationToken("k", "anonym",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")))).isEqualTo(GelesenStand.GEMEINSAM);
        assertThat(service.fuer(UsernamePasswordAuthenticationToken.authenticated(" ", null, List.of())))
                .isEqualTo(GelesenStand.GEMEINSAM);
        verifyNoInteractions(repository);
    }

    @Test
    void standUnbekannterBenutzerIstDasGemeinsameFeld() {
        given(frontendUserProfileService.findByUsername("erika")).willReturn(Optional.empty());

        assertThat(service.fuer(ERIKA)).isEqualTo(GelesenStand.GEMEINSAM);
    }

    @Test
    void standAngemeldeterBenutzer() {
        erikaAngemeldet();
        given(repository.findEmailIdsByBenutzerId(7L)).willReturn(Set.of(1L, 2L));

        GelesenStand stand = service.fuer(ERIKA);

        assertThat(stand.proBenutzer()).isTrue();
        assertThat(stand.gelesen()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void markierenMerktNurNeueUndSetztGemeinsamesFeld() {
        erikaAngemeldet();
        Email schonGelesen = mail(1L, EmailDirection.IN, true);
        Email vomChefGeoeffnet = mail(2L, EmailDirection.IN, true);
        Email ganzNeu = mail(3L, EmailDirection.IN, false);
        Email gesendet = mail(4L, EmailDirection.OUT, true);
        given(repository.geleseneAus(7L, List.of(1L, 2L, 3L, 4L))).willReturn(Set.of(1L));

        List<Email> neu = service.alsGelesenMarkieren(ERIKA,
                new ArrayList<>(Arrays.asList(schonGelesen, vomChefGeoeffnet, ganzNeu, gesendet, ganzNeu, null)));

        assertThat(neu).containsExactly(vomChefGeoeffnet, ganzNeu);
        verify(repository).alsGelesenMerken(eq(7L), eq(List.of(2L, 3L)), any(LocalDateTime.class));
        // Das gemeinsame Feld „irgendwer hat sie geöffnet“ setzt nur die ganz neue Mail.
        assertThat(ganzNeu.isRead()).isTrue();
        assertThat(ganzNeu.getFirstViewedAt()).isNotNull();
        verify(emailRepository).saveAll(List.of(ganzNeu));
    }

    @Test
    void ersteOeffnungBehaeltDenFruehestenZeitpunkt() {
        erikaAngemeldet();
        given(repository.geleseneAus(7L, List.of(5L))).willReturn(Set.of());
        LocalDateTime frueher = LocalDateTime.of(2026, 10, 1, 8, 0);
        Email mail = mail(5L, EmailDirection.IN, false);
        mail.setFirstViewedAt(frueher);

        service.alsGelesenMarkieren(ERIKA, List.of(mail));

        assertThat(mail.getFirstViewedAt()).isEqualTo(frueher);
    }

    @Test
    void schonAlleGelesenSpeichertNichts() {
        erikaAngemeldet();
        given(repository.geleseneAus(7L, List.of(1L))).willReturn(Set.of(1L));

        assertThat(service.alsGelesenMarkieren(ERIKA, List.of(mail(1L, EmailDirection.IN, true)))).isEmpty();

        verify(repository, never()).alsGelesenMerken(any(), anyList(), any());
        verify(emailRepository, never()).saveAll(anyList());
    }

    @Test
    void ohneAnmeldungNurDasGemeinsameFeld() {
        Email ungelesen = mail(8L, EmailDirection.IN, false);
        Email gelesen = mail(9L, EmailDirection.IN, true);

        List<Email> neu = service.alsGelesenMarkieren(null, List.of(ungelesen, gelesen));

        assertThat(neu).containsExactly(ungelesen);
        assertThat(ungelesen.isRead()).isTrue();
        verifyNoInteractions(repository);
    }
}
