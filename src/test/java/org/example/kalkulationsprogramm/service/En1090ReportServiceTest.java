package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Betriebsmittel;
import org.example.kalkulationsprogramm.domain.SchweisserZertifikat;
import org.example.kalkulationsprogramm.domain.Werkstoffzeugnis;
import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.repository.BetriebsmittelRepository;
import org.example.kalkulationsprogramm.repository.SchweisserZertifikatRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffzeugnisRepository;
import org.example.kalkulationsprogramm.repository.WpsRepository;
import org.example.kalkulationsprogramm.service.En1090ReportService.WpkStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class En1090ReportServiceTest {

    @Mock
    private SchweisserZertifikatRepository zertifikatRepository;

    @Mock
    private WpsRepository wpsRepository;

    @Mock
    private WerkstoffzeugnisRepository werkstoffzeugnisRepository;

    @Mock
    private BetriebsmittelRepository betriebsmittelRepository;

    @InjectMocks
    private En1090ReportService service;

    // --- Schweißer-Zertifikate ---

    @Test
    void wpkStatus_keineZertifikate_fehler() {
        when(zertifikatRepository.findAll()).thenReturn(Collections.emptyList());
        when(wpsRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("FEHLER", status.schweisser);
        assertTrue(status.schweisserHinweis.contains("Keine"));
    }

    @Test
    void wpkStatus_abgelaufenesZertifikat_fehler() {
        SchweisserZertifikat abgelaufen = createZertifikat(LocalDate.now().minusDays(10));
        when(zertifikatRepository.findAll()).thenReturn(List.of(abgelaufen));
        when(wpsRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("FEHLER", status.schweisser);
        assertTrue(status.schweisserHinweis.contains("abgelaufen"));
    }

    @Test
    void wpkStatus_baldAblaufendesZertifikat_warnung() {
        SchweisserZertifikat bald = createZertifikat(LocalDate.now().plusDays(30));
        when(zertifikatRepository.findAll()).thenReturn(List.of(bald));
        when(wpsRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("WARNUNG", status.schweisser);
        assertTrue(status.schweisserHinweis.contains("<60 Tagen"));
    }

    @Test
    void wpkStatus_gueltigesZertifikat_ok() {
        SchweisserZertifikat gueltig = createZertifikat(LocalDate.now().plusDays(120));
        when(zertifikatRepository.findAll()).thenReturn(List.of(gueltig));
        when(wpsRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("OK", status.schweisser);
        assertTrue(status.schweisserHinweis.contains("gültige"));
    }

    @Test
    void wpkStatus_zertifikatOhneAblaufdatum_gueltig() {
        SchweisserZertifikat unbefristet = createZertifikat(null); // kein Ablaufdatum = unbefristet
        when(zertifikatRepository.findAll()).thenReturn(List.of(unbefristet));
        when(wpsRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("OK", status.schweisser);
    }

    // --- WPS ---

    @Test
    void wpkStatus_keineWpsFuerProjekt_warnung() {
        when(zertifikatRepository.findAll()).thenReturn(List.of(createZertifikat(LocalDate.now().plusYears(1))));
        when(wpsRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("WARNUNG", status.wps);
        assertTrue(status.wpsHinweis.contains("Keine WPS"));
    }

    @Test
    void wpkStatus_abgelaufeneWps_fehler() {
        Wps abgelaufen = createWps(LocalDate.now().minusDays(5));
        when(zertifikatRepository.findAll()).thenReturn(List.of(createZertifikat(LocalDate.now().plusYears(1))));
        when(wpsRepository.findByProjektId(1L)).thenReturn(List.of(abgelaufen));
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("FEHLER", status.wps);
        assertTrue(status.wpsHinweis.contains("abgelaufen"));
    }

    @Test
    void wpkStatus_gueltigeWps_ok() {
        Wps gueltig = createWps(LocalDate.now().plusYears(1));
        when(zertifikatRepository.findAll()).thenReturn(List.of(createZertifikat(LocalDate.now().plusYears(1))));
        when(wpsRepository.findByProjektId(1L)).thenReturn(List.of(gueltig));
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("OK", status.wps);
    }

    @Test
    void wpkStatus_wpsOhneAblaufdatum_ok() {
        Wps ohneAblauf = createWps(null);
        when(zertifikatRepository.findAll()).thenReturn(List.of(createZertifikat(LocalDate.now().plusYears(1))));
        when(wpsRepository.findByProjektId(1L)).thenReturn(List.of(ohneAblauf));
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("OK", status.wps);
    }

    // --- Werkstoffzeugnisse ---

    @Test
    void wpkStatus_keineWerkstoffzeugnisse_warnung() {
        when(zertifikatRepository.findAll()).thenReturn(List.of(createZertifikat(LocalDate.now().plusYears(1))));
        when(wpsRepository.findByProjektId(1L)).thenReturn(List.of(createWps(LocalDate.now().plusYears(1))));
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(Collections.emptyList());
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("WARNUNG", status.werkstoffzeugnisse);
    }

    @Test
    void wpkStatus_werkstoffzeugnisseVorhanden_ok() {
        Werkstoffzeugnis wz = new Werkstoffzeugnis();
        when(zertifikatRepository.findAll()).thenReturn(List.of(createZertifikat(LocalDate.now().plusYears(1))));
        when(wpsRepository.findByProjektId(1L)).thenReturn(List.of(createWps(LocalDate.now().plusYears(1))));
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(List.of(wz));
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("OK", status.werkstoffzeugnisse);
    }

    // --- E-Check ---

    @Test
    void wpkStatus_ueberfaelligeBm_fehler() {
        Betriebsmittel faellig = new Betriebsmittel();
        faellig.setBezeichnung("Überfällig");
        LocalDate heute = LocalDate.now();
        LocalDate warnFrist = heute.plusDays(60);
        when(zertifikatRepository.findAll()).thenReturn(List.of(createZertifikat(heute.plusYears(1))));
        when(wpsRepository.findByProjektId(1L)).thenReturn(List.of(createWps(heute.plusYears(1))));
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(List.of(new Werkstoffzeugnis()));
        when(betriebsmittelRepository.findFaelligBis(heute)).thenReturn(List.of(faellig));
        when(betriebsmittelRepository.findFaelligBis(warnFrist)).thenReturn(List.of(faellig));

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("FEHLER", status.echeck);
        assertTrue(status.echeckHinweis.contains("überfällig"));
    }

    @Test
    void wpkStatus_baldFaelligeBm_warnung() {
        Betriebsmittel bald = new Betriebsmittel();
        bald.setBezeichnung("BaldFällig");
        LocalDate heute = LocalDate.now();
        LocalDate warnFrist = heute.plusDays(60);
        when(zertifikatRepository.findAll()).thenReturn(List.of(createZertifikat(heute.plusYears(1))));
        when(wpsRepository.findByProjektId(1L)).thenReturn(List.of(createWps(heute.plusYears(1))));
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(List.of(new Werkstoffzeugnis()));
        // heute: nichts fällig
        when(betriebsmittelRepository.findFaelligBis(heute)).thenReturn(Collections.emptyList());
        // bis warnFrist: 1 fällig
        when(betriebsmittelRepository.findFaelligBis(warnFrist)).thenReturn(List.of(bald));

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("WARNUNG", status.echeck);
        assertTrue(status.echeckHinweis.contains("<60 Tagen"));
    }

    @Test
    void wpkStatus_alleAktuell_ok() {
        LocalDate heute = LocalDate.now();
        when(zertifikatRepository.findAll()).thenReturn(List.of(createZertifikat(heute.plusYears(1))));
        when(wpsRepository.findByProjektId(1L)).thenReturn(List.of(createWps(heute.plusYears(1))));
        when(werkstoffzeugnisRepository.findByProjektId(1L)).thenReturn(List.of(new Werkstoffzeugnis()));
        when(betriebsmittelRepository.findFaelligBis(any())).thenReturn(Collections.emptyList());

        WpkStatus status = service.getWpkStatus(1L);

        assertEquals("OK", status.schweisser);
        assertEquals("OK", status.wps);
        assertEquals("OK", status.werkstoffzeugnisse);
        assertEquals("OK", status.echeck);
    }

    // --- Hilfs-Methoden ---

    private SchweisserZertifikat createZertifikat(LocalDate ablaufdatum) {
        SchweisserZertifikat z = new SchweisserZertifikat();
        z.setAblaufdatum(ablaufdatum);
        z.setZertifikatsnummer("TEST-001");
        return z;
    }

    private Wps createWps(LocalDate gueltigBis) {
        Wps wps = new Wps();
        wps.setWpsNummer("WPS-001");
        wps.setGueltigBis(gueltigBis);
        return wps;
    }
}
