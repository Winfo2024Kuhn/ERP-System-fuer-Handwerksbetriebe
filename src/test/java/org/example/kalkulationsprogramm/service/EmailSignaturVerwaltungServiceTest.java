package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.EmailSignatureImage;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.dto.Email.EmailSignaturDto;
import org.example.kalkulationsprogramm.dto.Email.EmailSignaturSpeichernRequest;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.example.kalkulationsprogramm.service.EmailSignaturVerwaltungService.Benutzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailSignaturVerwaltungServiceTest {

    @Mock private EmailSignatureRepository signaturRepository;
    @Mock private FrontendUserProfileRepository profilRepository;
    @Mock private OutOfOfficeScheduleRepository abwesenheitRepository;
    @Mock private EmailSignatureService signaturService;

    private EmailSignaturVerwaltungService service;

    private final FrontendUserProfile max = profil(5L, "Max Mustermann");
    private final FrontendUserProfile erika = profil(6L, "Erika Musterfrau");
    private final FrontendUserProfile chef = profil(1L, "Chef Mustermann");

    private static final Benutzer MAX = new Benutzer(5L, false);
    private static final Benutzer CHEF = new Benutzer(1L, true);

    private static FrontendUserProfile profil(Long id, String name) {
        FrontendUserProfile p = new FrontendUserProfile();
        p.setId(id);
        p.setDisplayName(name);
        return p;
    }

    private static EmailSignature signatur(Long id, FrontendUserProfile besitzer) {
        EmailSignature s = new EmailSignature();
        s.setId(id);
        s.setName("Signatur " + id);
        s.setHtml("<p>Gruß</p>");
        s.setBesitzer(besitzer);
        return s;
    }

    @BeforeEach
    void setup() {
        service = new EmailSignaturVerwaltungService(signaturRepository, profilRepository, abwesenheitRepository, signaturService);
        when(profilRepository.findById(5L)).thenReturn(Optional.of(max));
        when(profilRepository.findById(6L)).thenReturn(Optional.of(erika));
        when(profilRepository.findById(1L)).thenReturn(Optional.of(chef));
        when(signaturRepository.save(any(EmailSignature.class))).thenAnswer(inv -> {
            EmailSignature s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(100L);
            }
            return s;
        });
    }

    @Nested
    @DisplayName("Benutzer aus der Anmeldung")
    class BenutzerErmitteln {

        @Test
        void profilUndAdminRolleAusDerAnmeldung() {
            var auth = new UsernamePasswordAuthenticationToken("chef", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
            when(signaturService.angemeldeteProfilId(auth)).thenReturn(Optional.of(1L));
            assertThat(service.benutzer(auth)).isEqualTo(new Benutzer(1L, true));
        }

        @Test
        void ohneAnmeldungKeiner() {
            when(signaturService.angemeldeteProfilId(null)).thenReturn(Optional.empty());
            assertThat(service.benutzer(null)).isEqualTo(Benutzer.KEINER);
        }
    }

    @Nested
    @DisplayName("Sichtbarkeit")
    class Sichtbarkeit {

        @Test
        void adminSiehtAlleAuchOhneBesitzer() {
            EmailSignature mitFormular = signatur(1L, max);
            mitFormular.setVorlageDaten("{\"vorlage\":\"klassisch\"}");
            when(signaturRepository.findAllMitBesitzer())
                    .thenReturn(List.of(mitFormular, signatur(2L, erika), signatur(3L, null)));

            List<EmailSignaturDto> liste = service.liste(CHEF);
            assertThat(liste).allSatisfy(d -> assertThat(d.vorlageDaten()).isNull());

            assertThat(liste).extracting(EmailSignaturDto::id).containsExactly(1L, 2L, 3L);
            assertThat(liste.get(2).besitzerId()).isNull();
            assertThat(liste.get(2).besitzerName()).isNull();
            assertThat(liste.get(0).eigene()).isFalse();
        }

        @Test
        void benutzerSiehtNurEigene() {
            EmailSignature eigene = signatur(1L, max);
            max.setDefaultSignature(eigene);
            when(signaturRepository.findAllByBesitzerIdOrderByUpdatedAtDesc(5L)).thenReturn(List.of(eigene));

            List<EmailSignaturDto> liste = service.liste(MAX);

            assertThat(liste).hasSize(1);
            assertThat(liste.get(0).eigene()).isTrue();
            assertThat(liste.get(0).defaultSignature()).isTrue();
            assertThat(liste.get(0).besitzerName()).isEqualTo("Max Mustermann");
            verify(signaturRepository, never()).findAllByOrderByUpdatedAtDesc();
        }

        @Test
        void ohneProfilLeereListe() {
            assertThat(service.liste(Benutzer.KEINER)).isEmpty();
        }

        @Test
        void fremdeUndBesitzloseSindFuerBenutzerUnsichtbar() {
            when(signaturRepository.findById(2L)).thenReturn(Optional.of(signatur(2L, erika)));
            when(signaturRepository.findById(3L)).thenReturn(Optional.of(signatur(3L, null)));
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(signatur(1L, max)));

            assertThat(service.sichtbare(2L, MAX)).isEmpty();
            assertThat(service.sichtbare(3L, MAX)).isEmpty();
            assertThat(service.sichtbare(1L, MAX)).isPresent();
            assertThat(service.sichtbare(null, MAX)).isEmpty();
            assertThat(service.sichtbare(2L, CHEF)).isPresent();
            assertThat(service.laden(2L, MAX)).isEmpty();
            assertThat(service.laden(1L, MAX)).map(EmailSignaturDto::name).contains("Signatur 1");
        }

        @Test
        void bilderAlterUploadsImDto() {
            EmailSignature s = signatur(1L, max);
            EmailSignatureImage bild = new EmailSignatureImage();
            bild.setId(9L);
            bild.setCid("sig-1");
            bild.setOriginalFilename("logo.png");
            bild.setContentType("image/png");
            bild.setSizeBytes(1234);
            s.getImages().add(bild);
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(s));

            EmailSignaturDto dto = service.laden(1L, MAX).orElseThrow();

            assertThat(dto.images()).singleElement().satisfies(b -> {
                assertThat(b.cid()).isEqualTo("sig-1");
                assertThat(b.sizeBytes()).isEqualTo(1234);
            });
        }

        @Test
        void systemSignaturNurWennSichtbar() {
            EmailSignature system = signatur(3L, null);
            system.setSystemDefault(true);
            when(signaturRepository.findFirstByIsSystemDefaultTrue()).thenReturn(Optional.of(system));

            assertThat(service.systemSignatur(MAX)).isEmpty();
            assertThat(service.systemSignatur(CHEF)).map(EmailSignaturDto::isSystemDefault).contains(true);
        }
    }

    @Nested
    @DisplayName("Vom Betrieb festgelegte Standard-Signatur")
    class Zugewiesen {

        private EmailSignature zugewiesen;

        @BeforeEach
        void zuweisen() {
            zugewiesen = signatur(3L, null);
            max.setDefaultSignature(zugewiesen);
            when(signaturRepository.findById(3L)).thenReturn(Optional.of(zugewiesen));
        }

        @Test
        void stehtVornInDerListeUndIstNurZumLesen() {
            EmailSignature eigene = signatur(1L, max);
            when(signaturRepository.findAllByBesitzerIdOrderByUpdatedAtDesc(5L)).thenReturn(List.of(eigene));

            List<EmailSignaturDto> liste = service.liste(MAX);

            assertThat(liste).extracting(EmailSignaturDto::id).containsExactly(3L, 1L);
            assertThat(liste.get(0).nurLesen()).isTrue();
            assertThat(liste.get(0).defaultSignature()).isTrue();
            assertThat(liste.get(0).eigene()).isFalse();
            assertThat(liste.get(1).nurLesen()).isFalse();
        }

        @Test
        void eigeneStandardSignaturErscheintNichtDoppelt() {
            EmailSignature eigene = signatur(1L, max);
            max.setDefaultSignature(eigene);
            when(signaturRepository.findAllByBesitzerIdOrderByUpdatedAtDesc(5L)).thenReturn(List.of(eigene));

            assertThat(service.liste(MAX)).extracting(EmailSignaturDto::id).containsExactly(1L);
        }

        @Test
        void sichtbarUndAlsStandardNutzbarAberNichtAenderbar() {
            assertThat(service.sichtbare(3L, MAX)).isPresent();
            assertThat(service.laden(3L, MAX)).map(EmailSignaturDto::nurLesen).contains(true);
            assertThat(service.alsStandard(3L, MAX).id()).isEqualTo(3L);

            var req = new EmailSignaturSpeichernRequest(3L, "Geändert", "<p>x</p>", null, null, null, false, null);
            assertThatThrownBy(() -> service.speichern(req, MAX))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(EmailSignaturVerwaltungService.MELDUNG_NUR_LESEN);
            assertThatThrownBy(() -> service.loeschen(3L, MAX))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(EmailSignaturVerwaltungService.MELDUNG_NUR_LESEN);
            assertThatThrownBy(() -> service.bearbeitbare(3L, MAX)).isInstanceOf(IllegalArgumentException.class);
            verify(signaturService, never()).delete(any());
            verify(signaturRepository, never()).save(any());
        }

        @Test
        void andereBenutzerSehenSieNicht() {
            assertThat(service.sichtbare(3L, new Benutzer(6L, false))).isEmpty();
            assertThatThrownBy(() -> service.bearbeitbare(3L, new Benutzer(6L, false)))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void adminDarfSieAendern() {
            assertThat(service.bearbeitbare(3L, CHEF)).isSameAs(zugewiesen);
            assertThat(service.laden(3L, CHEF)).map(EmailSignaturDto::nurLesen).contains(false);
        }
    }

    @Nested
    @DisplayName("Firmen-Vorlagen")
    class FirmenVorlagen {

        private EmailSignature vorlage;

        @BeforeEach
        void vorlageAnlegen() {
            vorlage = signatur(4L, chef);
            vorlage.setFirmenVorlage(true);
            vorlage.setVorlageDaten("{\"vorlage\":\"kompakt\",\"name\":\"Chef Mustermann\",\"position\":\"Inhaber\","
                    + "\"abteilung\":\"Leitung\",\"mobil\":\"0171 1234567\",\"email\":\"chef@example.com\","
                    + "\"fotoUrl\":\"data:image/png;base64,AAAA\",\"telefon\":\"0931 123456\",\"firma\":\"Mustermann Metallbau GmbH\"}");
            vorlage.setHtml("<p>Chef Mustermann</p>");
            EmailSignatureImage bild = new EmailSignatureImage();
            bild.setId(9L);
            vorlage.getImages().add(bild);
            when(signaturRepository.findById(4L)).thenReturn(Optional.of(vorlage));
            when(signaturRepository.findAllByFirmenVorlageTrueOrderByNameAsc()).thenReturn(List.of(vorlage));
            when(signaturRepository.existsByFirmenVorlageTrue()).thenReturn(true);
        }

        @Test
        void benutzerSiehtNurDasDesignOhnePersoenlicheAngaben() throws Exception {
            EmailSignaturDto dto = service.firmenVorlagen(MAX).get(0);

            assertThat(dto.firmenVorlage()).isTrue();
            var daten = JsonMapper.builderWithJackson2Defaults().build().readTree(dto.vorlageDaten());
            assertThat(daten.get("vorlage").asString("")).isEqualTo("kompakt");
            assertThat(daten.get("firma").asString("")).isEqualTo("Mustermann Metallbau GmbH");
            assertThat(daten.get("telefon").asString("")).isEqualTo("0931 123456");
            for (String feld : List.of("name", "position", "abteilung", "mobil", "email", "fotoUrl")) {
                assertThat(daten.get(feld).asString("")).as(feld).isEmpty();
            }
            assertThat(dto.html()).isEmpty();
            assertThat(dto.images()).isEmpty();
            assertThat(dto.besitzerId()).isNull();
            assertThat(dto.besitzerName()).isNull();
            assertThat(dto.eigene()).isFalse();
            assertThat(dto.nurLesen()).isTrue();
            assertThat(dto.defaultSignature()).isFalse();
        }

        @Test
        void adminSiehtBesitzer() {
            EmailSignaturDto dto = service.firmenVorlagen(CHEF).get(0);

            assertThat(dto.besitzerName()).isEqualTo("Chef Mustermann");
            assertThat(dto.nurLesen()).isFalse();
            assertThat(dto.vorlageDaten()).contains("chef@example.com");
            assertThat(dto.html()).isEqualTo("<p>Chef Mustermann</p>");
        }

        @Test
        void festlegenUndZuruecknehmen() {
            EmailSignature abgeleitet = signatur(2L, chef);
            abgeleitet.setFirmenVorlageId(4L);
            abgeleitet.setVorlageDaten("{\"vorlage\":\"schlicht\"}");
            when(signaturRepository.findById(2L)).thenReturn(Optional.of(abgeleitet));

            EmailSignaturDto dto = service.alsFirmenVorlage(2L, true, CHEF);
            assertThat(dto.firmenVorlage()).isTrue();
            assertThat(dto.firmenVorlageId()).isNull();

            assertThat(service.alsFirmenVorlage(2L, false, CHEF).firmenVorlage()).isFalse();
            assertThatThrownBy(() -> service.alsFirmenVorlage(77L, true, CHEF)).isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void formularzustandOhneJsonObjektWirdNichtWeitergegeben() {
            assertThat(EmailSignaturVerwaltungService.ohnePersoenlicheAngaben(null)).isNull();
            assertThat(EmailSignaturVerwaltungService.ohnePersoenlicheAngaben(" ")).isNull();
            assertThat(EmailSignaturVerwaltungService.ohnePersoenlicheAngaben("[1,2]")).isNull();
            assertThat(EmailSignaturVerwaltungService.ohnePersoenlicheAngaben("{kaputt")).isNull();
        }

        @Test
        void besitzerOhneAdminRechteKannSeineFirmenVorlageNichtAendern() {
            EmailSignature maxVorlage = signatur(6L, max);
            maxVorlage.setFirmenVorlage(true);
            when(signaturRepository.findById(6L)).thenReturn(Optional.of(maxVorlage));

            assertThat(service.laden(6L, MAX)).map(EmailSignaturDto::nurLesen).contains(true);
            var req = new EmailSignaturSpeichernRequest(6L, "Umgebaut", "<p>x</p>", null, null, null, false, null);
            assertThatThrownBy(() -> service.speichern(req, MAX))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(EmailSignaturVerwaltungService.MELDUNG_NUR_LESEN);
            assertThatThrownBy(() -> service.loeschen(6L, MAX))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(EmailSignaturVerwaltungService.MELDUNG_NUR_LESEN);
            verify(signaturService, never()).delete(any());
            assertThat(service.bearbeitbare(6L, CHEF)).isSameAs(maxVorlage);
        }

        @Test
        void htmlSignaturOhneFormularWirdKeineVorlage() {
            EmailSignature nurHtml = signatur(2L, chef);
            when(signaturRepository.findById(2L)).thenReturn(Optional.of(nurHtml));

            assertThatThrownBy(() -> service.alsFirmenVorlage(2L, true, CHEF))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(EmailSignaturVerwaltungService.MELDUNG_VORLAGE_OHNE_FORMULAR);
            assertThat(nurHtml.isFirmenVorlage()).isFalse();
            // Zurücknehmen geht immer.
            assertThat(service.alsFirmenVorlage(2L, false, CHEF).firmenVorlage()).isFalse();
        }

        @Test
        void neueSignaturDesBenutzersBrauchtDieVorlage() {
            var ohne = new EmailSignaturSpeichernRequest(null, "Meine", "<p>x</p>", null, null, null, false, null);
            assertThatThrownBy(() -> service.speichern(ohne, MAX))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(EmailSignaturVerwaltungService.MELDUNG_FIRMEN_VORLAGE_PFLICHT);

            var mit = new EmailSignaturSpeichernRequest(null, "Meine", "<p>x</p>", null, null, null, false, 4L);
            EmailSignaturDto dto = service.speichern(mit, MAX);
            assertThat(dto.firmenVorlageId()).isEqualTo(4L);
            assertThat(dto.eigene()).isTrue();
        }

        @Test
        void adminDarfFreiGestalten() {
            var ohne = new EmailSignaturSpeichernRequest(null, "Frei", "<p>x</p>", null, null, null, false, null);
            assertThat(service.speichern(ohne, CHEF).firmenVorlageId()).isNull();
        }

        @Test
        void bestehendeFreieSignaturBleibtBearbeitbar() {
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(signatur(1L, max)));
            var req = new EmailSignaturSpeichernRequest(1L, "Alt", "<p>alt</p>", null, null, null, false, null);

            EmailSignaturDto dto = service.speichern(req, MAX);

            assertThat(dto.name()).isEqualTo("Alt");
            assertThat(dto.firmenVorlageId()).isNull();
        }

        @Test
        void unbekannteOderKeineVorlageWirdAbgelehnt() {
            when(signaturRepository.findById(2L)).thenReturn(Optional.of(signatur(2L, erika)));
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(signatur(1L, max)));

            for (Long id : List.of(2L, 77L, -1L, Long.MAX_VALUE)) {
                var req = new EmailSignaturSpeichernRequest(null, "Meine", "<p>x</p>", null, null, null, false, id);
                assertThatThrownBy(() -> service.speichern(req, MAX))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage(EmailSignaturVerwaltungService.MELDUNG_FIRMEN_VORLAGE_FEHLT);
            }
            // Eine Signatur kann nicht aus sich selbst entstehen.
            vorlage.setFirmenVorlage(false);
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(signatur(1L, max)));
            var selbst = new EmailSignaturSpeichernRequest(4L, "Selbst", "<p>x</p>", null, null, null, false, 4L);
            assertThatThrownBy(() -> service.speichern(selbst, CHEF))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(EmailSignaturVerwaltungService.MELDUNG_FIRMEN_VORLAGE_FEHLT);
        }

        @Test
        void vorlageSelbstStammtAusKeinerAnderen() {
            EmailSignature zweite = signatur(5L, chef);
            zweite.setFirmenVorlage(true);
            when(signaturRepository.findById(5L)).thenReturn(Optional.of(zweite));
            var req = new EmailSignaturSpeichernRequest(5L, "Zweite", "<p>x</p>", null, null, null, false, 4L);

            assertThat(service.speichern(req, CHEF).firmenVorlageId()).isNull();
        }
    }

    @Nested
    @DisplayName("Speichern")
    class Speichern {

        @Test
        void neueSignaturGehoertDemBenutzerBesitzerAngabeWirdIgnoriert() {
            var req = new EmailSignaturSpeichernRequest(null, "  Meine  ", "<p onclick=\"x()\">Gruß</p>",
                    "{\"vorlage\":\"klassisch\"}", 6L, true, false, null);

            EmailSignaturDto dto = service.speichern(req, MAX);

            assertThat(dto.besitzerId()).isEqualTo(5L);
            assertThat(dto.eigene()).isTrue();
            assertThat(dto.name()).isEqualTo("Meine");
            assertThat(dto.html()).isEqualTo("<p>Gruß</p>");
            assertThat(dto.vorlageDaten()).isEqualTo("{\"vorlage\":\"klassisch\"}");
            verify(profilRepository, never()).save(any());
        }

        @Test
        void adminLegtFuerKollegenOderOhneBesitzerAn() {
            EmailSignaturDto fuerErika = service.speichern(
                    new EmailSignaturSpeichernRequest(null, "Erika", "<p>x</p>", null, 6L, true, null, null), CHEF);
            assertThat(fuerErika.besitzerId()).isEqualTo(6L);
            assertThat(fuerErika.besitzerName()).isEqualTo("Erika Musterfrau");

            EmailSignaturDto ohne = service.speichern(
                    new EmailSignaturSpeichernRequest(null, "Firma", "<p>x</p>", null, null, true, null, null), CHEF);
            assertThat(ohne.besitzerId()).isNull();
        }

        @Test
        void adminOhneFlagLaesstBesitzerUnveraendert() {
            EmailSignature erikas = signatur(2L, erika);
            when(signaturRepository.findById(2L)).thenReturn(Optional.of(erikas));

            EmailSignaturDto geaendert = service.speichern(
                    new EmailSignaturSpeichernRequest(2L, "Erika", "<p>x</p>", null, null, null, null, null), CHEF);
            assertThat(geaendert.besitzerId()).isEqualTo(6L);

            EmailSignaturDto neu = service.speichern(
                    new EmailSignaturSpeichernRequest(null, "Chef", "<p>x</p>", null, null, null, null, null), CHEF);
            assertThat(neu.besitzerId()).isEqualTo(1L);
        }

        @Test
        void besitzerwechselLoestStandardBeiAnderen() {
            EmailSignature erikas = signatur(2L, erika);
            erika.setDefaultSignature(erikas);
            max.setDefaultSignature(erikas);
            when(signaturRepository.findById(2L)).thenReturn(Optional.of(erikas));
            when(profilRepository.findByDefaultSignatureId(2L)).thenReturn(List.of(erika, max));

            EmailSignaturDto dto = service.speichern(
                    new EmailSignaturSpeichernRequest(2L, "Erika", "<p>x</p>", null, 5L, true, null, null), CHEF);

            assertThat(dto.besitzerId()).isEqualTo(5L);
            assertThat(erika.getDefaultSignature()).isNull();
            assertThat(max.getDefaultSignature()).isSameAs(erikas);
            verify(profilRepository).save(erika);
            verify(profilRepository, never()).save(max);
        }

        @Test
        void gleicherBesitzerLoestNichts() {
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(signatur(1L, max)));

            service.speichern(new EmailSignaturSpeichernRequest(1L, "Max", "<p>x</p>", null, 5L, true, null, null), CHEF);

            verify(profilRepository, never()).findByDefaultSignatureId(any());
        }

        @Test
        void systemSignaturAendertNurAdmin() {
            EmailSignature system = signatur(1L, max);
            system.setSystemDefault(true);
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(system));
            var req = new EmailSignaturSpeichernRequest(1L, "System", "<p>x</p>", null, null, null, null, null);

            assertThatThrownBy(() -> service.speichern(req, MAX))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nur ein Admin");
            assertThat(service.speichern(req, CHEF).name()).isEqualTo("System");
        }

        @Test
        void adminMitUnbekanntemBesitzer() {
            var req = new EmailSignaturSpeichernRequest(null, "X", "<p>x</p>", null, 404L, true, null, null);
            assertThatThrownBy(() -> service.speichern(req, CHEF))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Benutzer");
        }

        @Test
        void aendernFremderSignaturIst404() {
            when(signaturRepository.findById(2L)).thenReturn(Optional.of(signatur(2L, erika)));
            var req = new EmailSignaturSpeichernRequest(2L, "Geklaut", "<p>x</p>", null, 5L, true, null, null);

            assertThatThrownBy(() -> service.speichern(req, MAX)).isInstanceOf(NoSuchElementException.class);
            verify(signaturRepository, never()).save(any());
        }

        @Test
        void aendernEigenerBehaeltBesitzer() {
            EmailSignature eigene = signatur(1L, max);
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(eigene));

            EmailSignaturDto dto = service.speichern(
                    new EmailSignaturSpeichernRequest(1L, "Neu", "<p>neu</p>", null, 6L, true, null, null), MAX);

            assertThat(dto.besitzerId()).isEqualTo(5L);
            assertThat(eigene.getHtml()).isEqualTo("<p>neu</p>");
        }

        @Test
        void alsStandardDirektBeimSpeichern() {
            EmailSignaturDto dto = service.speichern(
                    new EmailSignaturSpeichernRequest(null, "Meine", "<p>x</p>", null, null, null, true, null), MAX);

            assertThat(max.getDefaultSignature()).isNotNull();
            assertThat(dto.defaultSignature()).isTrue();
            verify(profilRepository).save(max);
        }

        @Test
        void ohneProfilKeineNeueSignatur() {
            var req = new EmailSignaturSpeichernRequest(null, "X", "<p>x</p>", null, null, null, null, null);
            assertThatThrownBy(() -> service.speichern(req, Benutzer.KEINER))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.speichern(null, MAX))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void adminOhneProfilKannNichtAlsStandardSetzen() {
            var req = new EmailSignaturSpeichernRequest(null, "X", "<p>x</p>", null, null, null, true, null);
            assertThatThrownBy(() -> service.speichern(req, new Benutzer(null, true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Benutzerprofil");
        }

        @Test
        void ungueltigeEingaben() {
            assertThatThrownBy(() -> service.speichern(
                    new EmailSignaturSpeichernRequest(null, " ", "<p>x</p>", null, null, null, null, null), MAX))
                    .hasMessageContaining("Namen");
            assertThatThrownBy(() -> service.speichern(
                    new EmailSignaturSpeichernRequest(null, "X", "<script>alert(1)</script>", null, null, null, null, null), MAX))
                    .hasMessageContaining("leer");
        }
    }

    @Nested
    @DisplayName("Standard, System-Signatur, Löschen")
    class Weiteres {

        @Test
        void alsStandardNurEigene() {
            EmailSignature eigene = signatur(1L, max);
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(eigene));
            when(signaturRepository.findById(2L)).thenReturn(Optional.of(signatur(2L, erika)));

            EmailSignaturDto dto = service.alsStandard(1L, MAX);

            assertThat(dto.defaultSignature()).isTrue();
            assertThat(max.getDefaultSignature()).isSameAs(eigene);
            assertThatThrownBy(() -> service.alsStandard(2L, MAX)).isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void standardEntfernen() {
            max.setDefaultSignature(signatur(1L, max));

            service.standardEntfernen(MAX);
            service.standardEntfernen(Benutzer.KEINER);

            assertThat(max.getDefaultSignature()).isNull();
            verify(profilRepository).save(max);
        }

        @Test
        void standardSignaturBenutzerBekommtImmerDieEigene() {
            EmailSignature eigene = signatur(1L, max);
            when(signaturService.getDefaultForFrontendUser(5L, null)).thenReturn(Optional.of(eigene));

            Optional<EmailSignaturDto> dto = service.standardSignatur(MAX, 6L, "Erika Musterfrau");

            assertThat(dto).map(EmailSignaturDto::id).contains(1L);
            assertThat(dto).map(EmailSignaturDto::defaultSignature).contains(true);
            verify(signaturService, never()).getDefaultForFrontendUser(6L, "Erika Musterfrau");
        }

        @Test
        void standardSignaturAdminDarfNachAnderenFragen() {
            when(signaturService.getDefaultForFrontendUser(6L, null)).thenReturn(Optional.of(signatur(2L, erika)));

            assertThat(service.standardSignatur(CHEF, 6L, null)).map(EmailSignaturDto::id).contains(2L);
            assertThat(service.standardSignatur(Benutzer.KEINER, null, null)).isEmpty();
        }

        @Test
        void systemSignaturSetzen() {
            EmailSignature s = signatur(3L, null);
            when(signaturRepository.findById(3L)).thenReturn(Optional.of(s));
            when(signaturService.setSystemDefault(3L)).thenAnswer(inv -> {
                s.setSystemDefault(true);
                return s;
            });

            assertThat(service.alsSystemSignatur(3L, CHEF).isSystemDefault()).isTrue();
            assertThatThrownBy(() -> service.alsSystemSignatur(3L, MAX)).isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void loeschenLoestVerweiseVorher() {
            when(signaturRepository.findById(1L)).thenReturn(Optional.of(signatur(1L, max)));

            service.loeschen(1L, MAX);

            verify(profilRepository).entferneStandardSignatur(1L);
            verify(abwesenheitRepository).entferneSignatur(1L);
            verify(signaturService).delete(1L);
        }

        @Test
        void systemSignaturLaesstSichNichtLoeschen() {
            EmailSignature system = signatur(3L, null);
            system.setSystemDefault(true);
            when(signaturRepository.findById(3L)).thenReturn(Optional.of(system));

            assertThatThrownBy(() -> service.loeschen(3L, CHEF))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("System-Signatur");
            verify(signaturService, never()).delete(any());
        }

        @Test
        void loeschenFremderIst404() {
            when(signaturRepository.findById(2L)).thenReturn(Optional.of(signatur(2L, erika)));

            assertThatThrownBy(() -> service.loeschen(2L, MAX)).isInstanceOf(NoSuchElementException.class);
            verify(signaturService, never()).delete(any());
        }
    }
}
