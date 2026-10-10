package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MobileDocumentContractTest {
    @Mock DateiSpeicherService files;
    @Mock AnfrageNotizRepository notes;
    @Mock ProjektNotizRepository projectNotes;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path storage;
    @Mock MitarbeiterRepository employees;
    @Mock LieferantDokumentService documents;
    @Mock LieferantBildRepository pictures;
    @Mock BildVorschauService thumbnails;
    @Mock BelegService belege;
    @Mock AnfrageService anfragen;
    @Mock LieferantenDetailService lieferantenDetails;
    @InjectMocks ProjektController projects;
    @InjectMocks AnfrageController enquiries;
    @InjectMocks LieferantenController suppliers;

    @BeforeEach void mobile() {
        org.springframework.test.util.ReflectionTestUtils.setField(suppliers, "uploadDir", storage.toString());
        org.springframework.test.util.ReflectionTestUtils.setField(suppliers, "mailAttachmentDir", storage.toString());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new MobilePrincipal(7L), null, List.of()));
        // Echter Zugriffs-Service: prüft, dass der Header-Token (MobilePrincipal) die Abteilungsrechte des Mitarbeiters bekommt.
        org.springframework.test.util.ReflectionTestUtils.setField(suppliers, "dokumentZugriffService",
            new LieferantDokumentZugriffService(belege, documents));
    }
    private org.springframework.security.core.Authentication auth() { return SecurityContextHolder.getContext().getAuthentication(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void projektListeVerraetNurMobilFreigegebeneBilder() {
        var parent = new Projekt(); parent.setId(1L);
        var image = new ProjektDokument(); image.setId(1L); image.setProjekt(parent); image.setDokumentGruppe(DokumentGruppe.BILDER); image.setGespeicherterDateiname("bild.jpg");
        var secret = new ProjektDokument(); secret.setId(2L); secret.setProjekt(parent); secret.setDokumentGruppe(DokumentGruppe.DIVERSE_DOKUMENTE); secret.setGespeicherterDateiname("intern.pdf");
        when(files.holeDokumenteZuProjekt(1L)).thenReturn(List.of(image, secret));
        assertThat(projects.listeDokumente(1L).getBody()).extracting("id").containsExactly(1L);
    }
    @Test void anfrageListeOhneGruppenFilterVerraetKeineInternenDokumente() {
        var parent = new Anfrage(); parent.setId(1L);
        var image = new AnfrageDokument(); image.setId(1L); image.setAnfrage(parent); image.setDokumentGruppe(DokumentGruppe.BILDER); image.setGespeicherterDateiname("bild.jpg");
        var secret = new AnfrageDokument(); secret.setId(2L); secret.setAnfrage(parent); secret.setDokumentGruppe(DokumentGruppe.DIVERSE_DOKUMENTE); secret.setGespeicherterDateiname("intern.pdf");
        when(files.holeDokumenteZuAnfrage(1L)).thenReturn(List.of(image, secret));
        assertThat(enquiries.listeDokumente(1L, null).getBody()).extracting("id").containsExactly(1L);
    }
    @Test void mobileUploadsKoennenKeineInterneDokumentgruppeBefuellen() {
        assertThat(projects.uploadDokument(1L, List.of(), DokumentGruppe.DIVERSE_DOKUMENTE, null, 7L, null).getStatusCode().value()).isEqualTo(403);
        assertThat(enquiries.uploadDokument(1L, List.of(), null).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(files);
    }
    @Test void anfragenListeLiefertMobilNurOffeneAnfragenOhneBetragUndBegrenzt() {
        var offen = anfrage(1L, false); var fertig = anfrage(2L, true); var dritte = anfrage(3L, false);
        when(anfragen.suche(null, null, null, null, null, false)).thenReturn(List.of(offen, fertig, dritte));
        List<?> liste = enquiries.liste(null, null, null, null, null, null, false, 1);
        assertThat(liste).hasSize(1).first().isInstanceOf(org.example.kalkulationsprogramm.dto.Anfrage.AnfrageMobilDto.class);
        var mobil = (org.example.kalkulationsprogramm.dto.Anfrage.AnfrageMobilDto) liste.get(0);
        assertThat(mobil.id()).isEqualTo(1L);
        assertThat(mobil.kundenTelefon()).isEqualTo("0123 456789");
        assertThat(enquiries.liste(null, null, null, null, null, null, false, 999)).hasSize(2);
        assertThat(enquiries.liste(null, null, null, null, null, null, false, null)).hasSize(2);
    }
    @Test void seitenweiseAnfragenListeIstMobilGesperrt() {
        // ?page= würde sonst die volle Seitenansicht mit Beträgen und E-Mails liefern.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                enquiries.listeSeite(null, null, null, null, null, null, false, null, null, null, null, 0, 500))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("403");
        verifyNoInteractions(anfragen);
    }
    @Test void anfrageDetailMobilOhneBetrag() {
        when(anfragen.findeDto(1L)).thenReturn(anfrage(1L, false));
        assertThat(enquiries.hole(1L).getBody()).isInstanceOf(org.example.kalkulationsprogramm.dto.Anfrage.AnfrageMobilDto.class);
        when(anfragen.findeDto(9L)).thenReturn(null);
        assertThat(enquiries.hole(9L).getStatusCode().value()).isEqualTo(404);
    }
    @Test void desktopBekommtVolleAnfrage() {
        SecurityContextHolder.clearContext();
        when(anfragen.findeDto(1L)).thenReturn(anfrage(1L, false));
        assertThat(enquiries.hole(1L).getBody()).isInstanceOf(org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto.class);
    }
    @Test void lieferantenDetailMobilNurStammdaten() {
        var stammdaten = new org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto(); stammdaten.setId(4L);
        when(lieferantenDetails.loadStammdaten(4L)).thenReturn(stammdaten);
        org.springframework.test.util.ReflectionTestUtils.setField(suppliers, "dokumentZugriffService",
            new LieferantDokumentZugriffService(belege, documents));
        assertThat(suppliers.getById(4L, false, null, auth()).getStatusCode().value()).isEqualTo(200);
        verify(lieferantenDetails, never()).loadDetails(anyLong());
    }
    private org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto anfrage(Long id, boolean abgeschlossen) {
        var dto = new org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto();
        dto.setId(id); dto.setBauvorhaben("Treppengeländer"); dto.setKundenName("Max Mustermann");
        dto.setKundenTelefon("0123 456789"); dto.setBetrag(java.math.BigDecimal.TEN); dto.setAbgeschlossen(abgeschlossen);
        return dto;
    }
    @Test void mobileBildUploadsNehmenKeineSvgOderHtmlDateienAn() {
        var svg = new org.springframework.mock.web.MockMultipartFile("datei", "bild.svg", "image/svg+xml", "<svg/>".getBytes());
        var html = new org.springframework.mock.web.MockMultipartFile("datei", "bild.html", "text/html", "<p>".getBytes());
        assertThat(projects.uploadDokument(1L, List.of(svg), DokumentGruppe.BILDER, null, 7L, null).getStatusCode().value()).isEqualTo(400);
        assertThat(enquiries.uploadDokument(1L, List.of(html), DokumentGruppe.BILDER).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(files);
    }
    @Test void anfrageNotizenVerbergenDesktopOnlyUndFremdePrivateNotizen() {
        var me = employee(7L); var other = employee(8L); var parent = new Anfrage(); parent.setId(1L);
        when(employees.findById(7L)).thenReturn(Optional.of(me));
        var visible = note(1L, parent, me, true, true);
        when(notes.findByAnfrageIdOrderByErstelltAmDesc(1L)).thenReturn(List.of(visible,
            note(2L,parent,me,false,false),note(3L,parent,other,true,true)));
        assertThat(enquiries.getNotizen(1L,null,7L,"token").getBody()).extracting("id").containsExactly(1L);
    }
    @Test void direkterLieferantenDownloadPrueftDasDokumentTypRecht() {
        var supplier = new Lieferanten(); supplier.setId(1L);
        var doc = new LieferantDokument(); doc.setId(2L); doc.setLieferant(supplier); doc.setTyp(LieferantDokumentTyp.RECHNUNG);
        when(documents.findById(2L)).thenReturn(doc);
        when(documents.getBerechtigungen(7L)).thenReturn(LieferantDokumentDto.BerechtigungenResponse.builder()
            .sichtbareTypen(List.of(LieferantDokumentTyp.LIEFERSCHEIN)).scanbarTypen(List.of()).build());
        when(belege.findCaller(null, auth())).thenReturn(employee(7L));
        assertThat(suppliers.downloadDokument(1L,2L,null,auth()).getStatusCode().value()).isEqualTo(404);
        verify(documents).getBerechtigungen(7L);
    }
    @Test void lieferantenVorschauOhneReklamationIstMobilNichtFreigegeben() {
        var supplier = new Lieferanten(); supplier.setId(1L);
        var image = new LieferantBild(); image.setLieferant(supplier); image.setGespeicherterDateiname("intern.jpg");
        when(pictures.findByGespeicherterDateiname("intern.jpg")).thenReturn(Optional.of(image));
        assertThat(suppliers.getBildVorschau("intern.jpg").getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(thumbnails);
    }
    @Test void freigegebenerLieferantenDokumenttypIstWeiterLesbar() throws Exception {
        var supplier = new Lieferanten(); supplier.setId(1L);
        var doc = new LieferantDokument(); doc.setId(2L); doc.setLieferant(supplier); doc.setTyp(LieferantDokumentTyp.LIEFERSCHEIN);
        doc.setGespeicherterDateiname("receipt.pdf"); doc.setOriginalDateiname("receipt.pdf");
        java.nio.file.Files.writeString(storage.resolve("receipt.pdf"), "%PDF-test");
        when(documents.findById(2L)).thenReturn(doc);
        when(documents.getBerechtigungen(7L)).thenReturn(LieferantDokumentDto.BerechtigungenResponse.builder()
            .sichtbareTypen(List.of(LieferantDokumentTyp.LIEFERSCHEIN)).scanbarTypen(List.of()).build());
        when(belege.findCaller(null, auth())).thenReturn(employee(7L));
        assertThat(suppliers.downloadDokument(1L,2L,null,auth()).getStatusCode().value()).isEqualTo(200);
    }
    @Test void eigeneNotizDarfNichtUeberFremdenElternpfadGeloeschtWerden() {
        var parent = new Projekt(); parent.setId(1L);
        var note = new ProjektNotiz(); note.setId(5L); note.setProjekt(parent); note.setMitarbeiter(employee(7L)); note.setMobileSichtbar(true);
        when(projectNotes.findById(5L)).thenReturn(Optional.of(note));
        assertThat(projects.deleteProjektNotiz(2L,5L,null,"token").getStatusCode().value()).isEqualTo(404);
        verify(projectNotes, never()).delete(any());
    }
    @Test void desktopListeBehaeltInterneDokumente() {
        SecurityContextHolder.clearContext();
        var secret = new AnfrageDokument(); secret.setId(2L); secret.setDokumentGruppe(DokumentGruppe.DIVERSE_DOKUMENTE); secret.setGespeicherterDateiname("intern.pdf");
        when(files.holeDokumenteZuAnfrage(1L)).thenReturn(List.of(secret));
        assertThat(enquiries.listeDokumente(1L,null).getBody()).extracting("id").containsExactly(2L);
    }
    private Mitarbeiter employee(Long id) { var m = new Mitarbeiter(); m.setId(id); m.setVorname("Max"); m.setNachname("Mustermann"); return m; }
    private AnfrageNotiz note(Long id, Anfrage parent, Mitarbeiter author, boolean mobile, boolean privateNote) {
        var n=new AnfrageNotiz(); n.setId(id); n.setAnfrage(parent); n.setMitarbeiter(author); n.setMobileSichtbar(mobile); n.setNurFuerErsteller(privateNote); n.setErstelltAm(LocalDateTime.of(2026,1,1,10,0)); return n;
    }
}
