package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Übernahme der tatsächlichen Versand-Empfänger in die Freigabe. Nur Dummy-Daten. */
@ExtendWith(MockitoExtension.class)
class FreigabeEmpfaengerServiceTest
{
    private static final String UUID_A = "11111111-2222-4333-8444-555555555555";
    private static final String UUID_B = "66666666-7777-4888-9999-aaaaaaaaaaaa";

    @Mock
    private DokumentFreigabeRepository repository;

    @InjectMocks
    private FreigabeEmpfaengerService service;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://www.example.de/");
    }

    private static DokumentFreigabe freigabe(String uuid, FreigabeStatus status)
    {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setUuid(uuid);
        f.setStatus(status);
        f.setKundeEmail("vorschlag@example.de");
        return f;
    }

    private static String mailMitLinks(String... uuids)
    {
        StringBuilder html = new StringBuilder("<p>Guten Tag,</p>");
        for (String uuid : uuids)
        {
            html.append("<a href=\"https://www.example.de/freigabe/").append(uuid).append("\">ansehen</a>");
        }
        return html.toString();
    }

    @Test
    void offeneFreigabe_bekommtDieEchtenEmpfaenger()
    {
        DokumentFreigabe f = freigabe(UUID_A, FreigabeStatus.PENDING);
        when(repository.findByUuidZumSperren(UUID_A)).thenReturn(Optional.of(f));

        service.uebernehmeVersandEmpfaenger(mailMitLinks(UUID_A, UUID_A),
                Arrays.asList("architekt@example.de", "kunde@example.de,architekt@example.de", null));

        assertThat(f.getKundeEmail()).isEqualTo("architekt@example.de, kunde@example.de");
        assertThat(f.isKundeEmailAusVersand()).isTrue();
        verify(repository).save(f);
    }

    @Test
    void schonUebernommenerEmpfaenger_bleibtFest()
    {
        // Spätere Weiterleitung mit demselben Link an einen Subunternehmer.
        DokumentFreigabe f = freigabe(UUID_A, FreigabeStatus.PENDING);
        f.setKundeEmail("kunde@example.de");
        f.setKundeEmailAusVersand(true);
        when(repository.findByUuidZumSperren(UUID_A)).thenReturn(Optional.of(f));

        service.uebernehmeVersandEmpfaenger(mailMitLinks(UUID_A), List.of("subunternehmer@example.de"));

        assertThat(f.getKundeEmail()).isEqualTo("kunde@example.de");
        verify(repository, never()).save(any());
    }

    @Test
    void grossgeschriebeneUuidImLink_wirdGefunden()
    {
        DokumentFreigabe f = freigabe(UUID_B, FreigabeStatus.PENDING);
        when(repository.findByUuidZumSperren(UUID_B)).thenReturn(Optional.of(f));

        service.uebernehmeVersandEmpfaenger(mailMitLinks(UUID_B.toUpperCase()), List.of("kunde@example.de"));

        assertThat(f.getKundeEmail()).isEqualTo("kunde@example.de");
    }

    @Test
    void angenommeneOderUnbekannteFreigabe_bleibtUnveraendert()
    {
        DokumentFreigabe angenommen = freigabe(UUID_A, FreigabeStatus.ACCEPTED);
        when(repository.findByUuidZumSperren(UUID_A)).thenReturn(Optional.of(angenommen));
        when(repository.findByUuidZumSperren(UUID_B)).thenReturn(Optional.empty());

        service.uebernehmeVersandEmpfaenger(mailMitLinks(UUID_A, UUID_B), List.of("kunde@example.de"));

        assertThat(angenommen.getKundeEmail()).isEqualTo("vorschlag@example.de");
        verify(repository, never()).save(any());
    }

    @Test
    void ohneEmpfaenger_wirdNichtsGeaendert()
    {
        service.uebernehmeVersandEmpfaenger(mailMitLinks(UUID_A), Arrays.asList(null, " ", ""));
        service.uebernehmeVersandEmpfaenger(mailMitLinks(UUID_A), null);

        verifyNoInteractions(repository);
    }

    @Test
    void ohneFreigabeLink_wirdNichtsGeladen()
    {
        service.uebernehmeVersandEmpfaenger("<p>Rechnung anbei</p>", List.of("kunde@example.de"));
        service.uebernehmeVersandEmpfaenger(null, List.of("kunde@example.de"));
        // Link auf eine fremde Seite zählt nicht.
        service.uebernehmeVersandEmpfaenger("<a href=\"https://boese.example/freigabe/" + UUID_A + "\">x</a>",
                List.of("kunde@example.de"));

        verifyNoInteractions(repository);
    }

    @Test
    void ohneOeffentlicheAdresse_wirdNichtsGeladen()
    {
        ReflectionTestUtils.setField(service, "publicBaseUrl", "  ");
        service.uebernehmeVersandEmpfaenger(mailMitLinks(UUID_A), List.of("kunde@example.de"));
        ReflectionTestUtils.setField(service, "publicBaseUrl", null);
        service.uebernehmeVersandEmpfaenger(mailMitLinks(UUID_A), List.of("kunde@example.de"));

        verifyNoInteractions(repository);
    }

    @Test
    void verbinde_kuerztAufVollstaendigeAdressenInnerhalbDerSpalte()
    {
        String lang = "a".repeat(120) + "@example.de"; // 131 Zeichen
        String zweite = "b".repeat(110) + "@example.de"; // 121 Zeichen → zusammen 254 mit ", "
        String dritte = "c@example.de";

        String ergebnis = FreigabeEmpfaengerService.verbinde(List.of(lang, zweite, dritte));

        assertThat(ergebnis).isEqualTo(lang + ", " + zweite).hasSizeLessThanOrEqualTo(FreigabeEmpfaengerService.MAX_LAENGE);
        assertThat(FreigabeEmpfaengerService.verbinde(List.of("x@example.de;y@example.de")))
                .isEqualTo("x@example.de, y@example.de");
    }
}
