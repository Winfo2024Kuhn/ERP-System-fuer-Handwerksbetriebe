package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferantStatus;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreisanfrageZuordnungServiceTest {

    @Mock private PreisanfrageLieferantRepository preisanfrageLieferantRepository;
    @Mock private PreisanfrageRepository preisanfrageRepository;

    private PreisanfrageZuordnungService service;
    private PreisanfrageZuordnungService serviceWithDomain;

    @BeforeEach
    void setUp() {
        service = new PreisanfrageZuordnungService(
                preisanfrageLieferantRepository, preisanfrageRepository, "");
        serviceWithDomain = new PreisanfrageZuordnungService(
                preisanfrageLieferantRepository, preisanfrageRepository, "bauschlosserei-kuhn.de");
    }

    private Email erstelleEmail(Long id, String subject) {
        Email email = new Email();
        email.setId(id);
        email.setSubject(subject);
        return email;
    }

    private PreisanfrageLieferant erstellePal(Long id, String token) {
        PreisanfrageLieferant pal = new PreisanfrageLieferant();
        pal.setId(id);
        pal.setToken(token);
        Lieferanten l = new Lieferanten();
        l.setId(99L);
        l.setLieferantenname("Stahlhandel Mustermann");
        pal.setLieferant(l);
        return pal;
    }

    // ═══════════════════════════════════════════════════════════════
    // Primary-Match ueber parentEmail.messageId
    // ═══════════════════════════════════════════════════════════════

    @Test
    void tryMatch_matchtViaParentEmailMessageId() {
        Email parent = erstelleEmail(10L, "Preisanfrage PA-2026-001-ABCDE");
        parent.setMessageId("<orig-12345@erp.local>");

        Email incoming = erstelleEmail(20L, "Re: Preisanfrage");
        incoming.setParentEmail(parent);

        PreisanfrageLieferant pal = erstellePal(7L, "PA-2026-001-ABCDE");

        when(preisanfrageLieferantRepository.findByOutgoingMessageId("<orig-12345@erp.local>"))
                .thenReturn(Optional.of(pal));

        Optional<PreisanfrageLieferant> result = service.tryMatch(incoming);

        assertThat(result).containsSame(pal);
        assertThat(pal.getStatus()).isEqualTo(PreisanfrageLieferantStatus.BEANTWORTET);
        assertThat(pal.getAntwortEmail()).isEqualTo(incoming);
        assertThat(pal.getAntwortErhaltenAm()).isNotNull();
        // Token-Fallback nicht befragt
        verify(preisanfrageLieferantRepository, never()).findByToken(org.mockito.ArgumentMatchers.anyString());
    }

    // ═══════════════════════════════════════════════════════════════
    // Fallback: Token-Regex im Betreff, wenn parentEmail fehlt
    // ═══════════════════════════════════════════════════════════════

    @Test
    void tryMatch_matchtViaTokenImBetreff_wennParentEmailFehlt() {
        Email incoming = erstelleEmail(21L, "Ihr Angebot zu PA-2026-003-XY2Z9 – Stahltraeger");
        // keine parentEmail

        PreisanfrageLieferant pal = erstellePal(12L, "PA-2026-003-XY2Z9");

        when(preisanfrageLieferantRepository.findByToken("PA-2026-003-XY2Z9"))
                .thenReturn(Optional.of(pal));

        Optional<PreisanfrageLieferant> result = service.tryMatch(incoming);

        assertThat(result).containsSame(pal);
        assertThat(pal.getStatus()).isEqualTo(PreisanfrageLieferantStatus.BEANTWORTET);
        assertThat(pal.getAntwortEmail()).isEqualTo(incoming);
        verify(preisanfrageLieferantRepository, never()).findByOutgoingMessageId(org.mockito.ArgumentMatchers.anyString());
    }

    // ═══════════════════════════════════════════════════════════════
    // Kein Match: weder Parent-MessageId noch Token
    // ═══════════════════════════════════════════════════════════════

    @Test
    void tryMatch_nichtsGefunden_returnsEmpty() {
        Email incoming = erstelleEmail(22L, "Allgemeine Anfrage ohne Token");

        Optional<PreisanfrageLieferant> result = service.tryMatch(incoming);

        assertThat(result).isEmpty();
        verify(preisanfrageLieferantRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ═══════════════════════════════════════════════════════════════
    // Save-Verify: Status, antwortErhaltenAm + save() werden gesetzt
    // ═══════════════════════════════════════════════════════════════

    @Test
    void tryMatch_setztStatusUndAntwortErhaltenAm() {
        Email incoming = erstelleEmail(23L, "Re: PA-2026-004-BCDEF");
        PreisanfrageLieferant pal = erstellePal(30L, "PA-2026-004-BCDEF");

        when(preisanfrageLieferantRepository.findByToken("PA-2026-004-BCDEF"))
                .thenReturn(Optional.of(pal));

        LocalDateTime vorher = LocalDateTime.now().minusSeconds(1);
        service.tryMatch(incoming);
        LocalDateTime nachher = LocalDateTime.now().plusSeconds(1);

        assertThat(pal.getStatus()).isEqualTo(PreisanfrageLieferantStatus.BEANTWORTET);
        assertThat(pal.getAntwortErhaltenAm()).isBetween(vorher, nachher);
        verify(preisanfrageLieferantRepository).save(pal);
    }

    // ═══════════════════════════════════════════════════════════════
    // Security: Token-Regex matcht nicht bei SQL/HTML-Payload drumrum —
    // der isolierte Token wird zwar gefunden, aber sauber extrahiert,
    // sodass Einschleusungen ins Repository nicht passieren.
    // ═══════════════════════════════════════════════════════════════

    @Test
    void tryMatch_tokenRegex_extrahiertSauberTrotzPayloadDrumherum() {
        Email incoming = erstelleEmail(
                24L,
                "'; DROP TABLE preisanfrage_lieferant; -- PA-2026-005-PQRST <script>alert(1)</script>");

        PreisanfrageLieferant pal = erstellePal(55L, "PA-2026-005-PQRST");

        when(preisanfrageLieferantRepository.findByToken("PA-2026-005-PQRST"))
                .thenReturn(Optional.of(pal));

        Optional<PreisanfrageLieferant> result = service.tryMatch(incoming);

        assertThat(result).containsSame(pal);
        // Es darf NUR der saubere Token an das Repo gehen — kein SQL/HTML-Payload
        verify(preisanfrageLieferantRepository).findByToken("PA-2026-005-PQRST");
    }

    @Test
    void tryMatch_nullIncoming_returnsEmpty() {
        assertThat(service.tryMatch(null)).isEmpty();
        verify(preisanfrageLieferantRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ═══════════════════════════════════════════════════════════════
    // Fallback 2: To-Adresse = TOKEN@reply-to-domain (nur wenn Domain konfiguriert)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void tryMatch_matchtViaToAdresse_wennDomainKonfiguriert() {
        Email incoming = erstelleEmail(30L, "Angebot ohne Token im Betreff");
        incoming.setRecipient("PA-2026-007-ABCDE@bauschlosserei-kuhn.de");
        incoming.setFromAddress("lieferant@stahlhandel.de");

        PreisanfrageLieferant pal = erstellePal(15L, "PA-2026-007-ABCDE");
        when(preisanfrageLieferantRepository.findByToken("PA-2026-007-ABCDE"))
                .thenReturn(Optional.of(pal));

        Optional<PreisanfrageLieferant> result = serviceWithDomain.tryMatch(incoming);

        assertThat(result).containsSame(pal);
        assertThat(pal.getStatus()).isEqualTo(PreisanfrageLieferantStatus.BEANTWORTET);
    }

    @Test
    void tryMatch_toAdresseIgnoriertWennKeineDomainKonfiguriert() {
        Email incoming = erstelleEmail(31L, "Angebot ohne Token im Betreff");
        incoming.setRecipient("PA-2026-007-ABCDE@bauschlosserei-kuhn.de");

        // service (ohne Domain) darf findByToken NICHT aufrufen (kein Reply-To-Match)
        Optional<PreisanfrageLieferant> result = service.tryMatch(incoming);

        assertThat(result).isEmpty();
        verify(preisanfrageLieferantRepository, never()).findByToken(org.mockito.ArgumentMatchers.anyString());
    }

    // ═══════════════════════════════════════════════════════════════
    // Fallback 4: PA-Nummer im Betreff + Absender-E-Mail-Abgleich
    // ═══════════════════════════════════════════════════════════════

    @Test
    void tryMatch_matchtViaPaNummerUndAbsenderEmail() {
        Email incoming = erstelleEmail(40L, "Ihr Angebot zu PA-2026-009");
        incoming.setFromAddress("max.mustermann@stahlhandel.de");

        Preisanfrage pa = new Preisanfrage();
        pa.setId(200L);
        pa.setNummer("PA-2026-009");

        PreisanfrageLieferant pal = erstellePal(20L, "PA-2026-009-FGHJK");
        pal.setVersendetAn("max.mustermann@stahlhandel.de");

        when(preisanfrageRepository.findByNummer("PA-2026-009")).thenReturn(Optional.of(pa));
        when(preisanfrageLieferantRepository.findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(200L))
                .thenReturn(List.of(pal));

        Optional<PreisanfrageLieferant> result = service.tryMatch(incoming);

        assertThat(result).containsSame(pal);
        assertThat(pal.getStatus()).isEqualTo(PreisanfrageLieferantStatus.BEANTWORTET);
    }

    @Test
    void tryMatch_paNummerGefundenAberAbsenderUnbekannt_returnsEmpty() {
        Email incoming = erstelleEmail(41L, "PA-2026-010 Angebot");
        incoming.setFromAddress("unbekannt@fremdfirma.de");

        Preisanfrage pa = new Preisanfrage();
        pa.setId(201L);
        pa.setNummer("PA-2026-010");

        PreisanfrageLieferant pal = erstellePal(21L, "PA-2026-010-WXYZ2");
        pal.setVersendetAn("max.mustermann@stahlhandel.de");

        when(preisanfrageRepository.findByNummer("PA-2026-010")).thenReturn(Optional.of(pa));
        when(preisanfrageLieferantRepository.findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(201L))
                .thenReturn(List.of(pal));

        Optional<PreisanfrageLieferant> result = service.tryMatch(incoming);

        assertThat(result).isEmpty();
        verify(preisanfrageLieferantRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
