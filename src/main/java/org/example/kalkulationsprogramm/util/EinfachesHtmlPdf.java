package org.example.kalkulationsprogramm.util;

import org.openpdf.text.Chunk;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Setzt einfaches HTML (Absätze, Fett, Kursiv, Überschrift h3, Aufzählungen) in OpenPDF-Absätze
 * um. Gedacht für bereinigte Rechtstexte ({@link RechtstextHtml}); unbekannte Tags werden wie
 * Text behandelt, nie als Markup ausgewertet.
 */
public final class EinfachesHtmlPdf
{
    /** Schriften für die vier Auszeichnungen und die Überschrift. */
    public record Schriften(Font text, Font fett, Font kursiv, Font fettKursiv, Font ueberschrift)
    {
    }

    private record Stueck(String text, boolean fett, boolean kursiv)
    {
    }

    private static final float ZEILENABSTAND = 14f;

    private EinfachesHtmlPdf()
    {
    }

    /** Absätze in Lesereihenfolge; leeres oder {@code null}-HTML ergibt eine leere Liste. */
    public static List<Paragraph> zuAbsaetzen(String html, Schriften schriften)
    {
        List<Paragraph> absaetze = new ArrayList<>();
        if (html == null || html.isBlank()) return absaetze;
        Element body = Jsoup.parseBodyFragment(html).body();
        List<Stueck> lose = new ArrayList<>();
        for (Node knoten : body.childNodes())
        {
            if (knoten instanceof Element el && istBlock(el))
            {
                fuegeAbsatzHinzu(absaetze, lose, null, schriften);
                lose.clear();
                blockAbsaetze(el, absaetze, schriften);
            }
            else
            {
                sammle(knoten, false, false, lose);
            }
        }
        fuegeAbsatzHinzu(absaetze, lose, null, schriften);
        return absaetze;
    }

    private static boolean istBlock(Element el)
    {
        return switch (el.normalName())
        {
            case "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "div" -> true;
            default -> false;
        };
    }

    private static void blockAbsaetze(Element el, List<Paragraph> absaetze, Schriften schriften)
    {
        String name = el.normalName();
        if (name.matches("h[1-6]"))
        {
            String titel = el.text().trim();
            if (titel.isEmpty()) return;
            Paragraph h = new Paragraph(titel, schriften.ueberschrift());
            h.setSpacingBefore(12f);
            h.setSpacingAfter(4f);
            absaetze.add(h);
            return;
        }
        if ("ul".equals(name) || "ol".equals(name))
        {
            int nummer = 1;
            for (Element li : el.children())
            {
                if (!"li".equals(li.normalName())) continue;
                List<Stueck> stuecke = new ArrayList<>();
                sammleListenpunkt(li, stuecke);
                String marke = "ol".equals(name) ? (nummer++) + ". " : "– ";
                Paragraph p = fuegeAbsatzHinzu(absaetze, stuecke, marke, schriften);
                if (p != null)
                {
                    p.setIndentationLeft(12f);
                    p.setFirstLineIndent(-8f);
                }
            }
            return;
        }
        List<Stueck> stuecke = new ArrayList<>();
        for (Node kind : el.childNodes()) sammle(kind, false, false, stuecke);
        fuegeAbsatzHinzu(absaetze, stuecke, null, schriften);
    }

    /** Ein Listenpunkt kann mehrere Absätze enthalten (Tiptap: {@code <li><p>…</p></li>}). */
    private static void sammleListenpunkt(Element li, List<Stueck> stuecke)
    {
        boolean erster = true;
        for (Node kind : li.childNodes())
        {
            if (kind instanceof Element el && "p".equals(el.normalName()))
            {
                if (!erster) stuecke.add(new Stueck("\n", false, false));
                for (Node k : el.childNodes()) sammle(k, false, false, stuecke);
                erster = false;
            }
            else
            {
                sammle(kind, false, false, stuecke);
            }
        }
    }

    private static void sammle(Node knoten, boolean fett, boolean kursiv, List<Stueck> stuecke)
    {
        if (knoten instanceof TextNode t)
        {
            String text = t.text();
            if (!text.isEmpty()) stuecke.add(new Stueck(text, fett, kursiv));
            return;
        }
        if (!(knoten instanceof Element el)) return;
        String name = el.normalName().toLowerCase(Locale.ROOT);
        if ("br".equals(name))
        {
            stuecke.add(new Stueck("\n", fett, kursiv));
            return;
        }
        boolean f = fett || "strong".equals(name) || "b".equals(name);
        boolean k = kursiv || "em".equals(name) || "i".equals(name);
        for (Node kind : el.childNodes()) sammle(kind, f, k, stuecke);
    }

    /** Baut aus den Stücken einen Absatz; nur Leerraum ergibt keinen. */
    private static Paragraph fuegeAbsatzHinzu(List<Paragraph> absaetze, List<Stueck> stuecke, String marke,
            Schriften schriften)
    {
        List<Stueck> bereinigt = beschneide(stuecke);
        if (bereinigt.isEmpty()) return null;
        Paragraph p = new Paragraph();
        p.setLeading(ZEILENABSTAND);
        p.setSpacingAfter(5f);
        if (marke != null) p.add(new Chunk(marke, schriften.text()));
        for (Stueck s : bereinigt) p.add(new Chunk(s.text(), schrift(s, schriften)));
        absaetze.add(p);
        return p;
    }

    /** Entfernt Leerraum am Anfang des ersten und am Ende des letzten Stücks. */
    private static List<Stueck> beschneide(List<Stueck> stuecke)
    {
        List<Stueck> liste = new ArrayList<>(stuecke);
        while (!liste.isEmpty() && liste.get(0).text().isBlank()) liste.remove(0);
        while (!liste.isEmpty() && liste.get(liste.size() - 1).text().isBlank()) liste.remove(liste.size() - 1);
        if (liste.isEmpty()) return liste;
        Stueck erstes = liste.get(0);
        liste.set(0, new Stueck(erstes.text().stripLeading(), erstes.fett(), erstes.kursiv()));
        int letzter = liste.size() - 1;
        Stueck letztes = liste.get(letzter);
        liste.set(letzter, new Stueck(letztes.text().stripTrailing(), letztes.fett(), letztes.kursiv()));
        return liste;
    }

    private static Font schrift(Stueck s, Schriften schriften)
    {
        if (s.fett() && s.kursiv()) return schriften.fettKursiv();
        if (s.fett()) return schriften.fett();
        if (s.kursiv()) return schriften.kursiv();
        return schriften.text();
    }
}
