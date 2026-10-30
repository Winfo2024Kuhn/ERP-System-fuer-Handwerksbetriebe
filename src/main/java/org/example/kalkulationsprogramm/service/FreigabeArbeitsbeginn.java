package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.DokumentFreigabe;

import java.time.format.DateTimeFormatter;

/**
 * Einheitliche Texte zur Entscheidung des Kunden über den Arbeitsbeginn bei einer digitalen
 * Annahme (Häkchen „Beginn vor Ablauf der Widerrufsfrist“, seit V423). Push, Glocke, Mail und
 * GoBD-Verlauf sollen dieselbe kurze Aussage treffen – deshalb steht sie hier. Die ausführlichen
 * Absätze an den Kunden in der Mail formuliert {@link AutoAuftragsbestaetigungVersandService} selbst.
 *
 * <p>Ältere Annahmen ohne Angabe ({@code vorzeitigerBeginn == null}) liefern {@code null}.</p>
 */
public final class FreigabeArbeitsbeginn
{
    /** Länge der gesetzlichen Widerrufsfrist (§ 355 Abs. 2 BGB). */
    static final int WIDERRUFSFRIST_TAGE = 14;

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private FreigabeArbeitsbeginn()
    {
    }

    /**
     * Was das Büro tun darf: „Sofort beginnen erlaubt.“ bzw. „Erst nach Ablauf der
     * Widerrufsfrist beginnen (bis 13.11.2026).“
     */
    public static String hinweisFuersBuero(DokumentFreigabe f)
    {
        if (f == null) return null;
        if (Boolean.TRUE.equals(f.getVorzeitigerBeginn()))
        {
            return "Sofort beginnen erlaubt.";
        }
        if (Boolean.FALSE.equals(f.getVorzeitigerBeginn()))
        {
            String bis = f.getAkzeptiertAm() == null ? ""
                    : " (bis " + f.getAkzeptiertAm().toLocalDate().plusDays(WIDERRUFSFRIST_TAGE).format(DATUM) + ")";
            return "Erst nach Ablauf der Widerrufsfrist beginnen" + bis + ".";
        }
        return null;
    }

    /**
     * Was der Kunde angekreuzt hat, als Beweis-Zeile: „Beginn vor Ablauf der Widerrufsfrist:
     * verlangt“ bzw. „… nicht verlangt“.
     */
    public static String entscheidungDesKunden(DokumentFreigabe f)
    {
        if (f == null || f.getVorzeitigerBeginn() == null) return null;
        return "Beginn vor Ablauf der Widerrufsfrist: "
                + (f.getVorzeitigerBeginn() ? "verlangt (Häkchen gesetzt)" : "nicht verlangt (Häkchen nicht gesetzt)");
    }
}
