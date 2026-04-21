package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.BestellQuelle;
import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.domain.Bestellung;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageAngebot;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferantStatus;
import org.example.kalkulationsprogramm.domain.PreisanfragePosition;
import org.example.kalkulationsprogramm.domain.PreisanfrageStatus;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageAngebotEintragenDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageErstellenDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfragePositionInputDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageVergleichDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageAngebotRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.PreisanfragePositionRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-Tests fuer {@link PreisanfrageService}. Nutzt Mockito-Mocks fuer alle
 * Repositories und einen Inline-Stub fuer {@code EmailService}, damit kein echter
 * SMTP-Aufbau erfolgt. Test-Daten nur Dummy (Max Mustermann).
 */
@ExtendWith(MockitoExtension.class)
class PreisanfrageServiceTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock private PreisanfrageRepository preisanfrageRepository;
    @Mock private PreisanfrageLieferantRepository preisanfrageLieferantRepository;
    @Mock private PreisanfragePositionRepository preisanfragePositionRepository;
    @Mock private PreisanfrageAngebotRepository preisanfrageAngebotRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private ArtikelInProjektRepository artikelInProjektRepository;
    @Mock private PreisanfragePdfGenerator pdfGenerator;
    @Mock private EmailService emailService;
    @Mock private BestellauftragService bestellauftragService;

    private PreisanfrageService service;
    private StubEmailServiceFactory emailFactory;

    @BeforeEach
    void setup() {
        emailFactory = new StubEmailServiceFactory(emailService);
        service = new PreisanfrageService(
                preisanfrageRepository,
                preisanfrageLieferantRepository,
                preisanfragePositionRepository,
                preisanfrageAngebotRepository,
                lieferantenRepository,
                projektRepository,
                artikelInProjektRepository,
                Optional.of(pdfGenerator),
                bestellauftragService,
                "smtp.example.invalid",
                465,
                "sender@example.com",
                "pw",
                "sender@example.com",
                "",
                emailFactory);
    }

    // ------------------------------------------------------------
    // Happy-Path 1: erstellen
    // ------------------------------------------------------------

    @Test
    void erstellen_generiertNummerUndTokens_undSpeichertPositionen() {
        Lieferanten l1 = lieferant(10L, "Stahlhandel Musterstrasse");
        Lieferanten l2 = lieferant(11L, "Metall Musterfirma");
        lenient().when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(l1));
        lenient().when(lieferantenRepository.findById(11L)).thenReturn(Optional.of(l2));
        when(preisanfrageRepository.findMaxLfdNrByPrefix(anyString())).thenReturn(0);
        when(preisanfrageLieferantRepository.existsByToken(anyString())).thenReturn(false);
        when(preisanfrageRepository.save(any(Preisanfrage.class))).thenAnswer(i -> i.getArgument(0));

        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.setBauvorhaben("Musterstrasse 1");
        dto.setLieferantIds(List.of(10L, 11L));
        dto.getPositionen().add(position("Stahltraeger IPE 200", new BigDecimal("10")));
        dto.getPositionen().add(position("Winkelstahl 50x5", new BigDecimal("5")));

        Preisanfrage pa = service.erstellen(dto);

        assertNotNull(pa.getNummer());
        assertTrue(pa.getNummer().startsWith("PA-" + Year.now().getValue() + "-"));
        assertEquals(PreisanfrageStatus.OFFEN, pa.getStatus());
        assertEquals(2, pa.getPositionen().size());
        assertEquals(2, pa.getLieferanten().size());
        for (PreisanfrageLieferant pal : pa.getLieferanten()) {
            assertTrue(pal.getToken().startsWith(pa.getNummer() + "-"),
                    "Token muss Nummer als Praefix haben: " + pal.getToken());
            assertEquals(PreisanfrageLieferantStatus.VORBEREITET, pal.getStatus());
        }
        // Die beiden Tokens sind zufaellig praktisch immer verschieden
        assertFalse(pa.getLieferanten().get(0).getToken().equals(pa.getLieferanten().get(1).getToken()));
    }

    // ------------------------------------------------------------
    // Happy-Path 2: Token-Kollision -> Retry
    // ------------------------------------------------------------

    @Test
    void erstellen_beiTokenKollisionWirdEinNeuerTokenGezogen() {
        Lieferanten l = lieferant(20L, "Max Mustermann GmbH");
        lenient().when(lieferantenRepository.findById(20L)).thenReturn(Optional.of(l));
        when(preisanfrageRepository.findMaxLfdNrByPrefix(anyString())).thenReturn(40);
        // Erste zwei Anfragen an existsByToken -> true (Kollision), dann false
        when(preisanfrageLieferantRepository.existsByToken(anyString()))
                .thenReturn(true, true, false);
        when(preisanfrageRepository.save(any(Preisanfrage.class))).thenAnswer(i -> i.getArgument(0));

        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.setLieferantIds(List.of(20L));
        dto.getPositionen().add(position("Rundstahl", new BigDecimal("3")));

        Preisanfrage pa = service.erstellen(dto);
        // Der eindeutig erzeugte Token muss existieren
        assertEquals(1, pa.getLieferanten().size());
        assertTrue(pa.getLieferanten().get(0).getToken().startsWith(pa.getNummer() + "-"));
        verify(preisanfrageLieferantRepository, org.mockito.Mockito.atLeast(3)).existsByToken(anyString());
    }

    // ------------------------------------------------------------
    // Happy-Path 3: versendeAnAlleLieferanten
    // ------------------------------------------------------------

    @Test
    void versende_setztOutgoingMessageIdUndStatus() throws Exception {
        Preisanfrage pa = baueFertigePreisanfrage(100L);
        PreisanfrageLieferant pal = pa.getLieferanten().get(0);
        when(preisanfrageRepository.findById(100L)).thenReturn(Optional.of(pa));
        when(pdfGenerator.generatePdfForPreisanfrage(pal.getId())).thenReturn(Path.of("uploads/test.pdf"));
        when(emailService.sendEmailAndReturnMessageId(
                anyString(), any(), anyString(), any(), anyString(), anyString(), any(), any()))
                .thenReturn("<abc-123@example.com>");
        when(preisanfrageLieferantRepository.save(any(PreisanfrageLieferant.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.versendeAnAlleLieferanten(100L);

        assertEquals(PreisanfrageLieferantStatus.VERSENDET, pal.getStatus());
        assertEquals("<abc-123@example.com>", pal.getOutgoingMessageId());
        assertEquals("max.mustermann@example.com", pal.getVersendetAn());
        assertNotNull(pal.getVersendetAm());
        // Subject enthaelt Nummer und Token
        verify(emailService).sendEmailAndReturnMessageId(
                eq("max.mustermann@example.com"),
                any(),
                eq("sender@example.com"),
                any(), // replyTo: null wenn Domain nicht konfiguriert
                argThat(subj -> subj.contains(pa.getNummer()) && subj.contains(pal.getToken())),
                anyString(), any(), any());
    }

    // ------------------------------------------------------------
    // Happy-Path 4: eintragen -> Status wechselt auf BEANTWORTET
    // ------------------------------------------------------------

    @Test
    void eintragen_aktualisiertStatusAufBeantwortetWennAllePositionenAngebotHaben() {
        Preisanfrage pa = baueFertigePreisanfrage(200L);
        PreisanfrageLieferant pal = pa.getLieferanten().get(0);
        PreisanfragePosition pos = pa.getPositionen().get(0);
        lenient().when(preisanfrageLieferantRepository.findById(pal.getId())).thenReturn(Optional.of(pal));
        lenient().when(preisanfragePositionRepository.findById(pos.getId())).thenReturn(Optional.of(pos));

        // Vor dem Save: keine Angebote; nach dem Save: das neue Angebot
        PreisanfrageAngebot gespeichert = new PreisanfrageAngebot();
        gespeichert.setId(9001L);
        gespeichert.setPreisanfrageLieferant(pal);
        gespeichert.setPreisanfragePosition(pos);
        gespeichert.setEinzelpreis(new BigDecimal("12.50"));
        when(preisanfrageAngebotRepository.findByPreisanfrageLieferantId(pal.getId()))
                .thenReturn(List.of())
                .thenReturn(List.of(gespeichert));
        when(preisanfrageAngebotRepository.save(any(PreisanfrageAngebot.class)))
                .thenAnswer(i -> {
                    PreisanfrageAngebot a = i.getArgument(0);
                    a.setId(9001L);
                    return a;
                });
        when(preisanfragePositionRepository.findByPreisanfrageIdOrderByReihenfolgeAsc(pa.getId()))
                .thenReturn(new ArrayList<>(pa.getPositionen()));
        when(preisanfrageLieferantRepository.findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(pa.getId()))
                .thenReturn(List.of(pal));
        lenient().when(preisanfrageLieferantRepository.save(any(PreisanfrageLieferant.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(preisanfrageRepository.save(any(Preisanfrage.class)))
                .thenAnswer(i -> i.getArgument(0));

        PreisanfrageAngebotEintragenDto dto = new PreisanfrageAngebotEintragenDto();
        dto.setPreisanfrageLieferantId(pal.getId());
        dto.setPreisanfragePositionId(pos.getId());
        dto.setEinzelpreis(new BigDecimal("12.50"));
        dto.setMwstProzent(new BigDecimal("19.00"));

        PreisanfrageAngebot result = service.eintragen(dto);

        assertNotNull(result);
        assertEquals(PreisanfrageLieferantStatus.BEANTWORTET, pal.getStatus());
        assertNotNull(pal.getAntwortErhaltenAm());
        assertEquals(PreisanfrageStatus.VOLLSTAENDIG, pa.getStatus());
    }

    // ------------------------------------------------------------
    // Happy-Path 5: getVergleich markiert guenstigsten
    // ------------------------------------------------------------

    @Test
    void getVergleich_markiertGuenstigstenPreisProPosition() {
        Preisanfrage pa = baueFertigePreisanfrage(300L);
        // zweiter Lieferant fuer Vergleich
        PreisanfrageLieferant l2 = lieferantEintrag(pa, 302L, lieferant(22L, "Zweite Firma Mustermann"));
        PreisanfragePosition pos = pa.getPositionen().get(0);
        PreisanfrageAngebot a1 = angebot(pa.getLieferanten().get(0), pos, new BigDecimal("15.00"));
        PreisanfrageAngebot a2 = angebot(l2, pos, new BigDecimal("12.00"));

        when(preisanfrageRepository.findById(300L)).thenReturn(Optional.of(pa));
        when(preisanfrageLieferantRepository.findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(300L))
                .thenReturn(List.of(pa.getLieferanten().get(0), l2));
        when(preisanfragePositionRepository.findByPreisanfrageIdOrderByReihenfolgeAsc(300L))
                .thenReturn(new ArrayList<>(pa.getPositionen()));
        when(preisanfrageAngebotRepository.findAllByPreisanfrageId(300L)).thenReturn(List.of(a1, a2));

        PreisanfrageVergleichDto v = service.getVergleich(300L);
        assertEquals(2, v.getLieferanten().size());
        assertEquals(1, v.getPositionen().size());
        PreisanfrageVergleichDto.PositionZeile zeile = v.getPositionen().get(0);
        assertEquals(l2.getId(), zeile.getGuenstigsterPreisanfrageLieferantId());
        long markiert = zeile.getZellen().stream().filter(PreisanfrageVergleichDto.AngebotsZelle::isGuenstigster).count();
        assertEquals(1, markiert);
    }

    // ------------------------------------------------------------
    // Happy-Path 6: vergebeAuftrag -> umrouten
    // ------------------------------------------------------------

    @Test
    void vergebeAuftrag_routetArtikelInProjektAufGewinnerUm() {
        // Nach A2 Phase 1β: vergebeAuftrag setzt weder Lieferant noch
        // preisProStueck auf der AiP. Der Lieferant lebt jetzt auf der
        // Bestellung (bestellauftragService), die Kalkulation kommt
        // später aus der Eingangsrechnung. Auf der AiP wird lediglich
        // quelle=BESTELLT gesetzt und der BestellauftragService gerufen.
        Preisanfrage pa = baueFertigePreisanfrage(400L);
        PreisanfrageLieferant gewinner = pa.getLieferanten().get(0);
        PreisanfragePosition pos = pa.getPositionen().get(0);
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(7777L);
        pos.setArtikelInProjekt(aip);

        PreisanfrageAngebot angebot = angebot(gewinner, pos, new BigDecimal("13.7734"));

        when(preisanfrageRepository.findById(400L)).thenReturn(Optional.of(pa));
        when(preisanfrageLieferantRepository.findById(gewinner.getId())).thenReturn(Optional.of(gewinner));
        lenient().when(preisanfrageAngebotRepository.findByPreisanfrageLieferantId(gewinner.getId()))
                .thenReturn(List.of(angebot));
        when(preisanfragePositionRepository.findByPreisanfrageIdOrderByReihenfolgeAsc(400L))
                .thenReturn(new ArrayList<>(pa.getPositionen()));
        when(artikelInProjektRepository.save(any(ArtikelInProjekt.class))).thenAnswer(i -> i.getArgument(0));
        when(preisanfrageRepository.save(any(Preisanfrage.class))).thenAnswer(i -> i.getArgument(0));
        when(bestellauftragService.erzeugeBestellungen(
                any(), any(Lieferanten.class), any(), eq(BestellStatus.VERSENDET)))
                .thenReturn(List.<Bestellung>of());

        service.vergebeAuftrag(400L, gewinner.getId());

        assertEquals(BestellQuelle.BESTELLT, aip.getQuelle());
        assertEquals(null, aip.getPreisProStueck());
        assertEquals(PreisanfrageStatus.VERGEBEN, pa.getStatus());
        assertEquals(gewinner, pa.getVergebenAn());
        verify(bestellauftragService).erzeugeBestellungen(
                argThat(l -> l.size() == 1 && l.contains(aip)),
                eq(gewinner.getLieferant()),
                eq(null),
                eq(BestellStatus.VERSENDET));
    }

    // ------------------------------------------------------------
    // Fehlerfaelle
    // ------------------------------------------------------------

    @Test
    void erstellen_ohneLieferantenWirftIllegalArgument() {
        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.getPositionen().add(position("Stahl", new BigDecimal("1")));
        assertThrows(IllegalArgumentException.class, () -> service.erstellen(dto));
    }

    @Test
    void erstellen_ohnePositionenWirftIllegalArgument() {
        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.setLieferantIds(List.of(10L));
        assertThrows(IllegalArgumentException.class, () -> service.erstellen(dto));
    }

    @Test
    void erstellen_mitDoppeltemLieferantWirft() {
        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.setLieferantIds(List.of(10L, 10L));
        dto.getPositionen().add(position("Stahl", new BigDecimal("1")));
        assertThrows(IllegalArgumentException.class, () -> service.erstellen(dto));
    }

    // ------------------------------------------------------------
    // Empfaenger-Override pro Lieferant
    // ------------------------------------------------------------

    @Test
    void erstellen_mitEmpfaengerOverride_setztVersendetAn() {
        Lieferanten l = lieferantMitMails(10L, "Stahlhandel",
                "info@example.com", "verkauf@example.com", "chef@example.com");
        lenient().when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(l));
        when(preisanfrageRepository.findMaxLfdNrByPrefix(anyString())).thenReturn(0);
        when(preisanfrageLieferantRepository.existsByToken(anyString())).thenReturn(false);
        when(preisanfrageRepository.save(any(Preisanfrage.class))).thenAnswer(i -> i.getArgument(0));

        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.setLieferantIds(List.of(10L));
        dto.getEmpfaengerProLieferant().put(10L, "verkauf@example.com");
        dto.getPositionen().add(position("Stahl", new BigDecimal("1")));

        Preisanfrage pa = service.erstellen(dto);
        assertEquals("verkauf@example.com", pa.getLieferanten().get(0).getVersendetAn());
    }

    @Test
    void erstellen_ohneOverride_nimmtErsteEmail() {
        Lieferanten l = lieferantMitMails(10L, "Stahlhandel",
                "info@example.com", "verkauf@example.com");
        lenient().when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(l));
        when(preisanfrageRepository.findMaxLfdNrByPrefix(anyString())).thenReturn(0);
        when(preisanfrageLieferantRepository.existsByToken(anyString())).thenReturn(false);
        when(preisanfrageRepository.save(any(Preisanfrage.class))).thenAnswer(i -> i.getArgument(0));

        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.setLieferantIds(List.of(10L));
        dto.getPositionen().add(position("Stahl", new BigDecimal("1")));

        Preisanfrage pa = service.erstellen(dto);
        assertEquals("info@example.com", pa.getLieferanten().get(0).getVersendetAn());
    }

    @Test
    void erstellen_mitFremderEmailAdresseWirft() {
        Lieferanten l = lieferantMitMails(10L, "Stahlhandel", "info@example.com");
        lenient().when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(l));
        when(preisanfrageRepository.findMaxLfdNrByPrefix(anyString())).thenReturn(0);

        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.setLieferantIds(List.of(10L));
        dto.getEmpfaengerProLieferant().put(10L, "angreifer@boese.example");
        dto.getPositionen().add(position("Stahl", new BigDecimal("1")));

        assertThrows(IllegalArgumentException.class, () -> service.erstellen(dto));
    }

    @Test
    void versende_nutztGewaehlteAdresseStattErsterEmail() {
        Preisanfrage pa = baueFertigePreisanfrage(800L);
        PreisanfrageLieferant pal = pa.getLieferanten().get(0);
        List<String> mails = new ArrayList<>();
        mails.add("info@example.com");
        mails.add("verkauf@example.com");
        pal.getLieferant().setKundenEmails(mails);
        pal.setVersendetAn("verkauf@example.com");
        when(preisanfrageRepository.findById(800L)).thenReturn(Optional.of(pa));
        when(pdfGenerator.generatePdfForPreisanfrage(any())).thenReturn(Path.of("x.pdf"));
        try {
            when(emailService.sendEmailAndReturnMessageId(anyString(), any(), anyString(),
                    any(), anyString(), anyString(), any(), any())).thenReturn("<mid@local>");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        service.versendeAnAlleLieferanten(800L);

        try {
            verify(emailService).sendEmailAndReturnMessageId(eq("verkauf@example.com"),
                    any(), anyString(), any(), anyString(), anyString(), any(), any());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void versende_ohneEmailAdresseWirft() {
        Preisanfrage pa = baueFertigePreisanfrage(500L);
        pa.getLieferanten().get(0).getLieferant().getKundenEmails().clear();
        when(preisanfrageRepository.findById(500L)).thenReturn(Optional.of(pa));
        when(pdfGenerator.generatePdfForPreisanfrage(any())).thenReturn(Path.of("x.pdf"));

        assertThrows(IllegalStateException.class,
                () -> service.versendeAnAlleLieferanten(500L));
        verify(preisanfrageLieferantRepository, never()).save(any(PreisanfrageLieferant.class));
    }

    @Test
    void eintragen_mitPositionAusAndererPreisanfrageWirft() {
        Preisanfrage pa1 = baueFertigePreisanfrage(600L);
        Preisanfrage pa2 = baueFertigePreisanfrage(700L);
        PreisanfrageLieferant pal = pa1.getLieferanten().get(0);
        PreisanfragePosition posFremd = pa2.getPositionen().get(0);
        lenient().when(preisanfrageLieferantRepository.findById(pal.getId())).thenReturn(Optional.of(pal));
        lenient().when(preisanfragePositionRepository.findById(posFremd.getId())).thenReturn(Optional.of(posFremd));

        PreisanfrageAngebotEintragenDto dto = new PreisanfrageAngebotEintragenDto();
        dto.setPreisanfrageLieferantId(pal.getId());
        dto.setPreisanfragePositionId(posFremd.getId());
        dto.setEinzelpreis(new BigDecimal("10"));

        assertThrows(IllegalArgumentException.class, () -> service.eintragen(dto));
    }

    // ------------------------------------------------------------
    // Security-Tests: SQL-Injection-String, XSS, negative IDs
    // ------------------------------------------------------------

    @Test
    void security_sqlInjectionInNotizWirdWieFreitextBehandelt() {
        Lieferanten l = lieferant(10L, "Max Mustermann GmbH");
        lenient().when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(l));
        when(preisanfrageRepository.findMaxLfdNrByPrefix(anyString())).thenReturn(0);
        when(preisanfrageLieferantRepository.existsByToken(anyString())).thenReturn(false);
        when(preisanfrageRepository.save(any(Preisanfrage.class))).thenAnswer(i -> i.getArgument(0));

        String payload = "'; DROP TABLE preisanfrage; --";
        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.setLieferantIds(List.of(10L));
        dto.setNotiz(payload);
        dto.getPositionen().add(position("Stahl", new BigDecimal("1")));

        Preisanfrage pa = service.erstellen(dto);
        // Payload bleibt im Freitext, wird nicht interpretiert, kein Throw
        assertEquals(payload, pa.getNotiz());
    }

    @Test
    void security_xssInProduktnameBleibtImFreitextUndWirftNicht() {
        Lieferanten l = lieferant(10L, "Max Mustermann GmbH");
        lenient().when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(l));
        when(preisanfrageRepository.findMaxLfdNrByPrefix(anyString())).thenReturn(0);
        when(preisanfrageLieferantRepository.existsByToken(anyString())).thenReturn(false);
        when(preisanfrageRepository.save(any(Preisanfrage.class))).thenAnswer(i -> i.getArgument(0));

        String xss = "<script>alert(1)</script>";
        PreisanfrageErstellenDto dto = new PreisanfrageErstellenDto();
        dto.setLieferantIds(List.of(10L));
        dto.getPositionen().add(position(xss, new BigDecimal("1")));

        Preisanfrage pa = service.erstellen(dto);
        assertEquals(xss, pa.getPositionen().get(0).getProduktname());
    }

    @Test
    void security_xssInLieferantennameWirdInMailBodyEscaped() throws Exception {
        Preisanfrage pa = baueFertigePreisanfrage(800L);
        PreisanfrageLieferant pal = pa.getLieferanten().get(0);
        pal.getLieferant().setLieferantenname("<script>alert(1)</script>");
        when(preisanfrageRepository.findById(800L)).thenReturn(Optional.of(pa));
        when(pdfGenerator.generatePdfForPreisanfrage(pal.getId())).thenReturn(Path.of("x.pdf"));
        when(emailService.sendEmailAndReturnMessageId(anyString(), any(), anyString(), any(), anyString(), anyString(), any(), any()))
                .thenReturn("<mid@example.com>");
        when(preisanfrageLieferantRepository.save(any(PreisanfrageLieferant.class))).thenAnswer(i -> i.getArgument(0));

        service.versendeAnAlleLieferanten(800L);

        verify(emailService).sendEmailAndReturnMessageId(
                anyString(), any(), anyString(), any(), anyString(),
                argThat((String body) -> !body.contains("<script>") && body.contains("&lt;script&gt;")),
                any(), any());
    }

    @Test
    void security_negativeIdWirftBeiVersende() {
        assertThrows(IllegalArgumentException.class,
                () -> service.versendeAnEinzelnenLieferanten(-1L));
    }

    @Test
    void security_nullIdWirftBeiVergabe() {
        assertThrows(IllegalArgumentException.class,
                () -> service.vergebeAuftrag(null, 1L));
    }

    @Test
    void security_negativerEinzelpreisWirftBeiEintragen() {
        PreisanfrageAngebotEintragenDto dto = new PreisanfrageAngebotEintragenDto();
        dto.setPreisanfrageLieferantId(1L);
        dto.setPreisanfragePositionId(1L);
        dto.setEinzelpreis(new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> service.eintragen(dto));
    }

    // ------------------------------------------------------------
    // findeById / listeAlle / abbrechen
    // ------------------------------------------------------------

    @Test
    void findeById_liefertEntityBeiTrefferZurueck() {
        Preisanfrage pa = baueFertigePreisanfrage(500L);
        when(preisanfrageRepository.findById(500L)).thenReturn(Optional.of(pa));

        Preisanfrage result = service.findeById(500L);

        assertEquals(500L, result.getId());
    }

    @Test
    void findeById_unbekannteIdWirftIllegalArgument() {
        when(preisanfrageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.findeById(999L));
    }

    @Test
    void listeAlle_ohneFilterSortiertNachErstelltAmDesc() {
        Preisanfrage a = baueFertigePreisanfrage(1L);
        Preisanfrage b = baueFertigePreisanfrage(2L);
        when(preisanfrageRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(b, a));

        List<Preisanfrage> result = service.listeAlle(null);

        assertEquals(2, result.size());
        verify(preisanfrageRepository).findAll(any(org.springframework.data.domain.Sort.class));
        verify(preisanfrageRepository, never()).findByStatusOrderByErstelltAmDesc(any());
    }

    @Test
    void listeAlle_mitStatusFilterRuftRepositoryMitStatusAuf() {
        Preisanfrage pa = baueFertigePreisanfrage(3L);
        when(preisanfrageRepository.findByStatusOrderByErstelltAmDesc(PreisanfrageStatus.OFFEN))
                .thenReturn(List.of(pa));

        List<Preisanfrage> result = service.listeAlle(PreisanfrageStatus.OFFEN);

        assertEquals(1, result.size());
        verify(preisanfrageRepository).findByStatusOrderByErstelltAmDesc(PreisanfrageStatus.OFFEN);
    }

    @Test
    void abbrechen_setztStatusAufAbgebrochenUndSpeichert() {
        Preisanfrage pa = baueFertigePreisanfrage(10L);
        pa.setStatus(PreisanfrageStatus.TEILWEISE_BEANTWORTET);
        when(preisanfrageRepository.findById(10L)).thenReturn(Optional.of(pa));
        when(preisanfrageRepository.save(any(Preisanfrage.class))).thenAnswer(i -> i.getArgument(0));

        service.abbrechen(10L);

        assertEquals(PreisanfrageStatus.ABGEBROCHEN, pa.getStatus());
        verify(preisanfrageRepository).save(pa);
    }

    @Test
    void abbrechen_bereitsVergebenWirftIllegalState() {
        Preisanfrage pa = baueFertigePreisanfrage(11L);
        pa.setStatus(PreisanfrageStatus.VERGEBEN);
        when(preisanfrageRepository.findById(11L)).thenReturn(Optional.of(pa));

        assertThrows(IllegalStateException.class, () -> service.abbrechen(11L));
        verify(preisanfrageRepository, never()).save(any());
    }

    @Test
    void abbrechen_unbekannteIdWirftIllegalArgument() {
        when(preisanfrageRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.abbrechen(42L));
    }

    // ------------------------------------------------------------
    // Helfer / Stubs
    // ------------------------------------------------------------

    private static Lieferanten lieferant(Long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        List<String> emails = new ArrayList<>();
        emails.add("max.mustermann@example.com");
        l.setKundenEmails(emails);
        return l;
    }

    private static Lieferanten lieferantMitMails(Long id, String name, String... emails) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        List<String> list = new ArrayList<>();
        for (String e : emails) {
            list.add(e);
        }
        l.setKundenEmails(list);
        return l;
    }

    private static PreisanfragePositionInputDto position(String produktname, BigDecimal menge) {
        PreisanfragePositionInputDto p = new PreisanfragePositionInputDto();
        p.setProduktname(produktname);
        p.setMenge(menge);
        p.setEinheit("Stueck");
        return p;
    }

    private static final AtomicLong ID_SEQ = new AtomicLong(1000);

    private Preisanfrage baueFertigePreisanfrage(Long id) {
        Preisanfrage pa = new Preisanfrage();
        pa.setId(id);
        pa.setNummer("PA-" + Year.now().getValue() + "-001");
        pa.setStatus(PreisanfrageStatus.OFFEN);

        PreisanfragePosition pos = new PreisanfragePosition();
        pos.setId(ID_SEQ.incrementAndGet());
        pos.setReihenfolge(0);
        pos.setProduktname("Stahltraeger Musterprofil");
        pos.setMenge(new BigDecimal("5"));
        pos.setEinheit("Stueck");
        pa.addPosition(pos);

        Lieferanten l = lieferant(ID_SEQ.incrementAndGet(), "Max Mustermann Stahl GmbH");
        PreisanfrageLieferant pal = new PreisanfrageLieferant();
        pal.setId(ID_SEQ.incrementAndGet());
        pal.setLieferant(l);
        pal.setToken(pa.getNummer() + "-ABCDE");
        pal.setStatus(PreisanfrageLieferantStatus.VORBEREITET);
        pa.addLieferant(pal);

        return pa;
    }

    private PreisanfrageLieferant lieferantEintrag(Preisanfrage pa, Long id, Lieferanten l) {
        PreisanfrageLieferant pal = new PreisanfrageLieferant();
        pal.setId(id);
        pal.setLieferant(l);
        pal.setToken(pa.getNummer() + "-FGHJK");
        pal.setStatus(PreisanfrageLieferantStatus.BEANTWORTET);
        pa.addLieferant(pal);
        return pal;
    }

    private PreisanfrageAngebot angebot(PreisanfrageLieferant pal, PreisanfragePosition pos, BigDecimal einzelpreis) {
        PreisanfrageAngebot a = new PreisanfrageAngebot();
        a.setId(ID_SEQ.incrementAndGet());
        a.setPreisanfrageLieferant(pal);
        a.setPreisanfragePosition(pos);
        a.setEinzelpreis(einzelpreis);
        return a;
    }

    /** Test-Factory, ersetzt new EmailService(...) im Produktivcode. */
    private static final class StubEmailServiceFactory
            implements PreisanfrageService.EmailServiceFactory {
        private final EmailService instance;

        StubEmailServiceFactory(EmailService instance) {
            this.instance = instance;
        }

        @Override
        public EmailService create(String host, int port, String username, String password) {
            return instance;
        }
    }
}
