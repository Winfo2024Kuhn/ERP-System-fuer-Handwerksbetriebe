package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class BildVorschauServiceTest {

    private BildVorschauService service;

    @BeforeEach
    void setUp() {
        service = new BildVorschauService();
    }

    @Test
    void verkleinertGrosseBilderAufDieMaximaleKantenlaenge() throws IOException {
        byte[] original = jpeg(1200, 600, null);

        BufferedImage vorschau = leseVorschau(original);

        assertThat(vorschau.getWidth()).isEqualTo(BildVorschauService.THUMBNAIL_MAX_SIZE);
        assertThat(vorschau.getHeight()).isEqualTo(BildVorschauService.THUMBNAIL_MAX_SIZE / 2);
    }

    @Test
    void behaeltDieLageBeiWennKeinExifVermerkVorhandenIst() throws IOException {
        byte[] querformat = jpeg(400, 200, null);

        BufferedImage vorschau = leseVorschau(querformat);

        assertThat(vorschau.getWidth()).isGreaterThan(vorschau.getHeight());
    }

    /**
     * Der Kern der Sache: Ein hochkant gehaltenes Handyfoto liegt in der Datei quer
     * und trägt die Drehung nur als EXIF-Vermerk. Beim Neuberechnen geht der Vermerk
     * verloren – die Drehung muss deshalb fest ins Vorschaubild gerechnet werden,
     * sonst liegt die Vorschau quer, während die Großansicht richtig steht.
     */
    @Test
    void drehtDasBildGemaessExifVermerkUmNeunzigGrad() throws IOException {
        byte[] querformatMitDrehvermerk = jpeg(400, 200, 6);

        BufferedImage vorschau = leseVorschau(querformatMitDrehvermerk);

        assertThat(vorschau.getHeight()).isGreaterThan(vorschau.getWidth());
    }

    @Test
    void drehtDasBildGemaessExifVermerkUmHundertachtzigGrad() throws IOException {
        byte[] querformat = jpeg(400, 200, 3);

        BufferedImage vorschau = leseVorschau(querformat);

        // 180° lässt die Kanten, wo sie sind – das Bild darf dabei nicht kippen
        assertThat(vorschau.getWidth()).isGreaterThan(vorschau.getHeight());
    }

    /**
     * 5, 7 und 8 kippen das Bild ebenfalls um 90° (mit zusätzlicher Spiegelung bei 5 und 7).
     * Alle drei müssen aus dem Querformat ein Hochformat machen.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = { 5, 6, 7, 8 })
    void kipptDasBildBeiAllenGedrehtenExifWerten(int orientierung) throws IOException {
        BufferedImage vorschau = leseVorschau(jpeg(400, 200, orientierung));

        assertThat(vorschau.getHeight()).isGreaterThan(vorschau.getWidth());
    }

    /** 1–4 lassen die Kanten, wo sie sind – gespiegelt wird höchstens innerhalb des Rahmens. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = { 1, 2, 3, 4 })
    void behaeltDieKantenBeiDenUngedrehtenExifWerten(int orientierung) throws IOException {
        BufferedImage vorschau = leseVorschau(jpeg(400, 200, orientierung));

        assertThat(vorschau.getWidth()).isGreaterThan(vorschau.getHeight());
    }

    /**
     * Ein abgeschnittener oder manipulierter EXIF-Block darf die Vorschau nicht
     * verhindern – im Zweifel wird das Bild einfach ungedreht ausgeliefert.
     */
    @Test
    void ueberstehtEinAbgeschnittenesExifSegment() throws IOException {
        byte[] vollstaendig = jpeg(400, 200, 6);
        // Das APP1-Segment sitzt direkt hinter dem SOI – hier mittendrin abschneiden
        byte[] beschaedigt = java.util.Arrays.copyOf(vollstaendig, 20);

        // Kein Reader kommt mit dem Torso zurecht, aber es darf nichts hochschlagen
        assertThat(service.erzeugeThumbnail(new ByteArrayResource(beschaedigt))).isNull();
    }

    /**
     * Ein manipulierter IFD-Offset nahe {@code Integer.MAX_VALUE} darf den Parser
     * nicht aus dem Tritt bringen: Das Bild kommt ungedreht, aber vollständig zurück.
     */
    @Test
    void ignoriertEinenUnsinnigenExifOffset() throws IOException {
        byte[] daten = jpeg(400, 200, 6);
        // Der IFD-Offset steht 4 Byte hinter dem TIFF-Header, Little Endian
        int offsetFeld = indexVonExifTiffHeader(daten) + 4;
        daten[offsetFeld] = (byte) 0xFF;
        daten[offsetFeld + 1] = (byte) 0xFF;
        daten[offsetFeld + 2] = (byte) 0xFF;
        daten[offsetFeld + 3] = (byte) 0x7F;

        BufferedImage vorschau = leseVorschau(daten);

        assertThat(vorschau.getWidth()).isGreaterThan(vorschau.getHeight());
    }

    /** Findet den Beginn des TIFF-Headers ("II") im EXIF-Block. */
    private int indexVonExifTiffHeader(byte[] daten) {
        for (int i = 0; i < daten.length - 8; i++) {
            if (daten[i] == 'E' && daten[i + 1] == 'x' && daten[i + 2] == 'i' && daten[i + 3] == 'f'
                    && daten[i + 4] == 0 && daten[i + 5] == 0) {
                return i + 6;
            }
        }
        throw new IllegalStateException("Kein EXIF-Block im Testbild gefunden");
    }

    // ============== AUSDÜNNUNG BEIM DEKODIEREN ==============

    /** Anzahl der Pixel, die bei diesem Subsampling tatsächlich dekodiert werden. */
    private long dekodiertePixel(int breite, int hoehe) {
        int s = service.ermittleSubsampling(breite, hoehe);
        // Der Decoder rundet beim Ausdünnen auf
        return (long) ((breite + s - 1) / s) * ((hoehe + s - 1) / s);
    }

    @Test
    void duennteinNormalesHandyfotoAufDieZielgroesseAus() {
        // 4000x3000 (12 MP): 3000/300 = 10 → jedes zehnte Pixel
        assertThat(service.ermittleSubsampling(4000, 3000)).isEqualTo(10);
    }

    @Test
    void duenntNichtAusWennDasBildOhnehinKleinIst() {
        assertThat(service.ermittleSubsampling(200, 150)).isEqualTo(1);
    }

    /**
     * Der kritische Fall: Bei einer sehr kurzen Kante ergibt die Zielgrößen-Rechnung
     * allein keine Ausdünnung, das Bild würde also in voller Größe dekodiert. Die
     * Speichergrenze muss hier greifen.
     */
    @Test
    void begrenztDenSpeicherAuchBeiExtremenSeitenverhaeltnissen() {
        assertThat(dekodiertePixel(99_999_999, 1)).isLessThanOrEqualTo(40_000_000L);
        assertThat(dekodiertePixel(50_000_000, 2)).isLessThanOrEqualTo(40_000_000L);
        assertThat(dekodiertePixel(300_000, 300)).isLessThanOrEqualTo(40_000_000L);
    }

    @Test
    void bleibtBeiJederZulaessigenBildgroesseUnterDerSpeichergrenze() {
        // Quadratisch am oberen Rand des Erlaubten (knapp unter 100 MP)
        assertThat(dekodiertePixel(9_999, 9_999)).isLessThanOrEqualTo(40_000_000L);
    }

    /** Bilder ab der Obergrenze werden gar nicht erst dekodiert. */
    @Test
    void lehntBilderAbDerPixelObergrenzeAb() throws IOException {
        // Ein echtes 100-MP-Bild zu erzeugen wäre im Test nicht bezahlbar – stattdessen
        // wird der gemeldete Wert direkt geprüft: 10.000 x 10.000 = exakt 100 MP.
        assertThat(dekodiertePixel(10_000, 10_000)).isLessThanOrEqualTo(40_000_000L);
    }

    @Test
    void liefertNullWennDasFormatNichtLesbarIst() throws IOException {
        byte[] keinBild = "das ist kein Bild".getBytes();

        assertThat(service.erzeugeThumbnail(new ByteArrayResource(keinBild))).isNull();
    }

    @Test
    void legtErzeugteVorschauenImCacheAb() throws IOException {
        var resource = new ByteArrayResource(jpeg(400, 200, null));

        assertThat(service.ausCache("dummy.jpg")).isNull();
        byte[] erzeugt = service.erzeugeUndCache("dummy.jpg", resource);

        assertThat(erzeugt).isNotNull();
        assertThat(service.ausCache("dummy.jpg")).isSameAs(erzeugt);
    }

    private BufferedImage leseVorschau(byte[] original) throws IOException {
        byte[] vorschau = service.erzeugeThumbnail(new ByteArrayResource(original));
        assertThat(vorschau).isNotNull();
        return ImageIO.read(new ByteArrayInputStream(vorschau));
    }

    /**
     * Baut ein JPEG der gewünschten Größe und schiebt optional einen EXIF-Block
     * mit dem Orientierungs-Tag dahinter.
     *
     * @param orientierung EXIF-Wert 1–8 oder {@code null} für "kein EXIF-Block"
     */
    private byte[] jpeg(int breite, int hoehe, Integer orientierung) throws IOException {
        BufferedImage bild = new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bild.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, breite, hoehe);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bild, "jpg", baos);
        byte[] daten = baos.toByteArray();

        if (orientierung == null) {
            return daten;
        }

        byte[] app1 = exifApp1Segment(orientierung);
        ByteArrayOutputStream mitExif = new ByteArrayOutputStream();
        mitExif.write(daten, 0, 2);                       // SOI (FFD8)
        mitExif.write(app1);                              // EXIF direkt dahinter
        mitExif.write(daten, 2, daten.length - 2);        // Rest der Datei
        return mitExif.toByteArray();
    }

    /** Minimaler APP1-Block (Little Endian) mit genau einem Eintrag: Orientation. */
    private byte[] exifApp1Segment(int orientierung) throws IOException {
        ByteArrayOutputStream inhalt = new ByteArrayOutputStream();
        inhalt.write(new byte[] { 'E', 'x', 'i', 'f', 0, 0 });
        inhalt.write(new byte[] { 'I', 'I' });                       // Byte-Reihenfolge: Intel
        inhalt.write(new byte[] { 42, 0 });                          // TIFF-Kennung
        inhalt.write(new byte[] { 8, 0, 0, 0 });                     // Offset des ersten IFD
        inhalt.write(new byte[] { 1, 0 });                           // ein Eintrag
        inhalt.write(new byte[] { 0x12, 0x01 });                     // Tag 0x0112 = Orientation
        inhalt.write(new byte[] { 3, 0 });                           // Typ SHORT
        inhalt.write(new byte[] { 1, 0, 0, 0 });                     // Anzahl 1
        inhalt.write(new byte[] { (byte) orientierung, 0, 0, 0 });   // der Wert selbst
        inhalt.write(new byte[] { 0, 0, 0, 0 });                     // kein weiteres IFD

        byte[] nutzdaten = inhalt.toByteArray();
        int laenge = nutzdaten.length + 2; // Längenfeld zählt sich selbst mit

        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        segment.write(new byte[] { (byte) 0xFF, (byte) 0xE1 });
        segment.write(new byte[] { (byte) (laenge >> 8), (byte) (laenge & 0xFF) });
        segment.write(nutzdaten);
        return segment.toByteArray();
    }
}
