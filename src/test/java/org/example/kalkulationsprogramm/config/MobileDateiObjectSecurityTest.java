package org.example.kalkulationsprogramm.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.Optional;
import java.util.List;
import jakarta.servlet.http.Cookie;
import org.example.kalkulationsprogramm.controller.DateiController;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ProjektNotizBild;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageNotizBildRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizBildRepository;
import org.example.kalkulationsprogramm.service.BildVorschauService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.MobileObjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Echte HTTP-Authentifizierung und Dateiauslieferung; Speicher und Datenbank sind Testdoubles. */
@WebMvcTest(controllers = DateiController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, MobileUrlaubsantragSecurityTest.FilterBeans.class, MobileObjectAccessService.class})
class MobileDateiObjectSecurityTest {
    private static final String TOKEN = "12345678-1234-4234-8234-123456789abc";
    private static final byte[] PRIVATE_BYTES = "vertraulicher-testinhalt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    @Autowired private MockMvc mvc;
    @MockBean private FrontendUserDetailsService users;
    @MockBean private MitarbeiterRepository mitarbeiter;
    @MockBean private ProjektDokumentRepository projektDokumente;
    @MockBean private AnfrageDokumentRepository anfrageDokumente;
    @MockBean private ProjektNotizBildRepository projektNotizBilder;
    @MockBean private AnfrageNotizBildRepository anfrageNotizBilder;
    @MockBean private DateiSpeicherService speicher;
    @MockBean private BildVorschauService vorschau;

    @BeforeEach
    void aktiveAnmeldungUndVorhandenePrivateDatei() {
        Mitarbeiter employee = new Mitarbeiter();
        employee.setId(7L);
        employee.setAktiv(true);
        when(mitarbeiter.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.of(employee));
        when(speicher.ladeDokumentMetadaten(anyString())).thenThrow(new NotFoundException("Keine Metadaten"));
        when(speicher.ladeDokumentAlsResource(anyString())).thenAnswer(i -> resource(i.getArgument(0)));
        when(speicher.ladeBildAlsResource(anyString())).thenAnswer(i -> resource(i.getArgument(0)));
        when(speicher.ladeMobileDateiAlsResource(any())).thenAnswer(i ->
                resource(i.<MobileObjectAccessService.FreigegebeneDatei>getArgument(0).dateiname()));
        when(vorschau.ausCache(anyString())).thenReturn(PRIVATE_BYTES);
        when(vorschau.anzeigeAusCache(anyString())).thenReturn(PRIVATE_BYTES);
    }

    private ByteArrayResource resource(String filename) {
        return new ByteArrayResource(PRIVATE_BYTES) {
            @Override public String getFilename() { return filename; }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/dokumente/personalakte.pdf", "/api/dokumente/rechnung.jpg",
            "/api/images/private-notiz.jpg", "/api/dokumente/private-notiz.jpg/thumbnail",
            "/api/dokumente/private-notiz.jpg/anzeige", "/api/dokumente/originalname.jpg"
    })
    void gespeicherteDateiOhneMobileObjektfreigabeBleibtAuchMitTokenUnsichtbar(String path) throws Exception {
        mvc.perform(get(path).header("X-Auth-Token", TOKEN)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void desktopSessionBehaeltDieBestehendeDateiauslieferung() throws Exception {
        mvc.perform(get("/api/dokumente/personalakte.pdf"))
                .andExpect(status().isOk()).andExpect(content().bytes(PRIVATE_BYTES));
    }

    @Test
    void anonymerDateizugriffBleibtGesperrt() throws Exception {
        mvc.perform(get("/api/dokumente/personalakte.pdf")).andExpect(status().isUnauthorized());
    }

    private ProjektDokument projektBild(String filename) {
        Projekt projekt = new Projekt();
        projekt.setId(42L);
        ProjektDokument dokument = new ProjektDokument();
        dokument.setProjekt(projekt);
        dokument.setGespeicherterDateiname(filename);
        dokument.setDokumentGruppe(DokumentGruppe.BILDER);
        return dokument;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/dokumente/foto.jpg", "/api/dokumente/foto.jpg/thumbnail", "/api/dokumente/foto.jpg/anzeige"})
    void freigegebeneProjektbilderSindLesbarOhneBrowserLangzeitCache(String path) throws Exception {
        when(projektDokumente.findByGespeicherterDateiname("foto.jpg")).thenReturn(Optional.of(projektBild("foto.jpg")));
        mvc.perform(get(path).cookie(new Cookie("ze_token", TOKEN)))
                .andExpect(status().isOk()).andExpect(content().bytes(PRIVATE_BYTES))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(speicher, never()).ladeDokumentMetadaten(anyString());
        verify(speicher, never()).ladeDokumentAlsResource(anyString());
    }

    @ParameterizedTest
    @EnumSource(value = DokumentGruppe.class, names = "BILDER", mode = EnumSource.Mode.EXCLUDE)
    void andereProjektDokumentgruppenBleibenAuchAlsJpegGesperrt(DokumentGruppe gruppe) throws Exception {
        ProjektDokument dokument = projektBild("rechnung.jpg");
        dokument.setDokumentGruppe(gruppe);
        when(projektDokumente.findByGespeicherterDateiname("rechnung.jpg")).thenReturn(Optional.of(dokument));
        mvc.perform(get("/api/dokumente/rechnung.jpg").header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
        verify(speicher, never()).ladeMobileDateiAlsResource(any());
    }

    @Test
    void anfrageBilderNutzenIhrenEigenenSpeicher() throws Exception {
        Anfrage anfrage = new Anfrage();
        anfrage.setId(24L);
        AnfrageDokument dokument = new AnfrageDokument();
        dokument.setAnfrage(anfrage);
        dokument.setGespeicherterDateiname("anfrage.jpg");
        dokument.setDokumentGruppe(DokumentGruppe.BILDER);
        when(anfrageDokumente.findByGespeicherterDateiname("anfrage.jpg")).thenReturn(Optional.of(dokument));
        mvc.perform(get("/api/dokumente/anfrage.jpg").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(content().bytes(PRIVATE_BYTES));
        verify(speicher).ladeMobileDateiAlsResource(new MobileObjectAccessService.FreigegebeneDatei(
                "anfrage.jpg", MobileObjectAccessService.Speicher.ANFRAGE));
    }

    private ProjektNotiz projektNotiz(long owner, boolean mobile, boolean privat) {
        ProjektNotiz notiz = new ProjektNotiz();
        notiz.setProjekt(projektBild("unused").getProjekt());
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(owner);
        notiz.setMitarbeiter(mitarbeiter);
        notiz.setMobileSichtbar(mobile);
        notiz.setNurFuerErsteller(privat);
        ProjektNotizBild bild = new ProjektNotizBild();
        bild.setGespeicherterDateiname("notiz.jpg");
        bild.setNotiz(notiz);
        when(projektNotizBilder.findByGespeicherterDateiname("notiz.jpg")).thenReturn(List.of(bild));
        return notiz;
    }

    @Test
    void privateNotizBilderSindNurFuerDenErstellerSichtbar() throws Exception {
        ProjektNotiz notiz = projektNotiz(8L, true, true);
        mvc.perform(get("/api/dokumente/notiz.jpg/thumbnail").header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
        notiz.getMitarbeiter().setId(7L);
        mvc.perform(get("/api/dokumente/notiz.jpg/thumbnail").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(content().bytes(PRIVATE_BYTES));
        notiz.setMobileSichtbar(false);
        mvc.perform(get("/api/dokumente/notiz.jpg/thumbnail").header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void freigabeEntzugGiltAuchBeiGefuelltemAnzeigeCache() throws Exception {
        ProjektNotiz notiz = projektNotiz(8L, true, false);
        mvc.perform(get("/api/dokumente/notiz.jpg/anzeige").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk());
        notiz.setNurFuerErsteller(true);
        mvc.perform(get("/api/dokumente/notiz.jpg/anzeige").header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void anfrageNotizBilderBeachtenMobileUndPrivateFreigabe() throws Exception {
        Anfrage anfrage = new Anfrage();
        anfrage.setId(24L);
        Mitarbeiter owner = new Mitarbeiter();
        owner.setId(7L);
        AnfrageNotiz notiz = new AnfrageNotiz();
        notiz.setAnfrage(anfrage);
        notiz.setMitarbeiter(owner);
        notiz.setNurFuerErsteller(true);
        AnfrageNotizBild bild = new AnfrageNotizBild();
        bild.setNotiz(notiz);
        bild.setGespeicherterDateiname("anfrage-notiz.jpg");
        when(anfrageNotizBilder.findByGespeicherterDateiname("anfrage-notiz.jpg")).thenReturn(List.of(bild));
        mvc.perform(get("/api/images/anfrage-notiz.jpg").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        owner.setId(8L);
        mvc.perform(get("/api/dokumente/anfrage-notiz.jpg/thumbnail").header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
        owner.setId(7L);
        notiz.setMobileSichtbar(false);
        mvc.perform(get("/api/images/anfrage-notiz.jpg").header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void datenbankGrosskleinschreibungErlaubtKeinenAlias() throws Exception {
        when(projektDokumente.findByGespeicherterDateiname("FOTO.jpg"))
                .thenReturn(Optional.of(projektBild("foto.jpg")));
        mvc.perform(get("/api/dokumente/FOTO.jpg").header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void mobileVorschauVerwendetNieDenDesktopCacheEinerGleichnamigenDatei() throws Exception {
        byte[] mobileBytes = "freigegebenes-testbild".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(projektDokumente.findByGespeicherterDateiname("foto.jpg")).thenReturn(Optional.of(projektBild("foto.jpg")));
        when(vorschau.ausCache(anyString())).thenReturn(null);
        // Der Desktop-Lookup kann einen anderen Speicherordner oder Originalnamen auflösen.
        when(vorschau.ausCache("foto.jpg")).thenReturn(PRIVATE_BYTES);
        when(vorschau.erzeugeUndCache(anyString(), any())).thenReturn(mobileBytes);
        mvc.perform(get("/api/dokumente/foto.jpg/thumbnail").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(content().bytes(mobileBytes));
    }
}
