package org.example.kalkulationsprogramm.service.vorschau;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Rechnet die EXIF-Lage einer Kamera fest ins Bild (drehen und/oder spiegeln). Handyfotos speichern die Lage nur als
 * Vermerk im Datei-Kopf ({@link ExifOrientierung}); ein neu berechnetes Vorschau-JPEG hat diesen Vermerk nicht mehr
 * und läge ohne diesen Schritt quer. Reine Rechenklasse ohne Spring.
 */
public final class BildDrehung {

    private BildDrehung() {
    }

    /**
     * Dreht bzw. spiegelt das Bild so, wie es die EXIF-Angabe der Kamera vorsieht.
     *
     * @param orientierung EXIF-Wert 1–8; alles andere lässt das Bild unverändert
     */
    public static BufferedImage wendeAn(BufferedImage bild, int orientierung) {
        if (orientierung <= 1 || orientierung > 8) {
            return bild;
        }

        int breite = bild.getWidth();
        int hoehe = bild.getHeight();
        // Die Werte 5–8 kippen das Bild um 90°, dadurch tauschen Breite und Höhe die Plätze
        boolean gekippt = orientierung >= 5;
        int zielBreite = gekippt ? hoehe : breite;
        int zielHoehe = gekippt ? breite : hoehe;

        AffineTransform transform = new AffineTransform();
        switch (orientierung) {
            case 2 -> { // horizontal gespiegelt
                transform.scale(-1.0, 1.0);
                transform.translate(-breite, 0);
            }
            case 3 -> { // um 180° gedreht
                transform.translate(breite, hoehe);
                transform.rotate(Math.PI);
            }
            case 4 -> { // vertikal gespiegelt
                transform.scale(1.0, -1.0);
                transform.translate(0, -hoehe);
            }
            case 5 -> { // um 90° gegen den Uhrzeigersinn und gespiegelt
                transform.rotate(-Math.PI / 2);
                transform.scale(-1.0, 1.0);
            }
            case 6 -> { // um 90° im Uhrzeigersinn
                transform.translate(hoehe, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> { // um 90° im Uhrzeigersinn und gespiegelt
                transform.scale(-1.0, 1.0);
                transform.translate(-hoehe, 0);
                transform.translate(0, breite);
                transform.rotate(3 * Math.PI / 2);
            }
            case 8 -> { // um 90° gegen den Uhrzeigersinn
                transform.translate(0, breite);
                transform.rotate(3 * Math.PI / 2);
            }
            default -> {
                return bild;
            }
        }

        BufferedImage gedreht = new BufferedImage(zielBreite, zielHoehe, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = gedreht.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(bild, transform, null);
        g2d.dispose();
        return gedreht;
    }
}
