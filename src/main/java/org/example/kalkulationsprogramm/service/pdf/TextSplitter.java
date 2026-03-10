package org.example.kalkulationsprogramm.service.pdf;

import com.lowagie.text.pdf.BaseFont;
import java.util.ArrayList;
import java.util.List;

public class TextSplitter {

    private BaseFont baseFont;
    private float fontSize;

    public TextSplitter(BaseFont baseFont, float fontSize) {
        this.baseFont = baseFont;
        this.fontSize = fontSize;
    }

    /**
     * Kern-Funktion: Nimmt einen langen Text und den verfügbaren Platz (height)
     * und gibt zurück:
     * Index 0: Der Text, der auf Seite 1 passt.
     * Index 1: Der Rest, der auf Seite 2 muss.
     */
    public String[] splitTextToFitHeight(String fullText, float width, float availableHeight) {
        // Zeilenabstand (Leading) berechnen (z.B. 1.2 * Schriftgröße)
        float leading = fontSize * 1.2f;
        
        // Wie viele Zeilen passen mathematisch in den Rest-Platz?
        int maxLines = (int) (availableHeight / leading);
        
        if (maxLines <= 0) {
            return new String[] { "", fullText }; // Nichts passt, alles auf Seite 2
        }

        // Text in Worte zerlegen
        String[] words = fullText.split("\\s+");
        StringBuilder part1 = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();
        int linesCount = 1;
        int wordIndex = 0;

        // Wort für Wort aufbauen und prüfen
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            
            // Prüfen, wie breit die Zeile wäre, wenn wir das Wort hinzufügen
            float currentLineWidth = baseFont.getWidthPoint(currentLine.toString() + " " + word, fontSize);

            if (currentLineWidth < width) {
                // Wort passt noch in die aktuelle Zeile
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                // Wort passt nicht -> Neue Zeile
                linesCount++;
                
                if (linesCount > maxLines) {
                    // STOP! Wir haben den Platz auf Seite 1 verbraucht.
                    // Alles ab hier (current word + rest) ist Part 2
                    wordIndex = i;
                    break;
                }
                
                // Aktuelle Zeile zu Part 1 hinzufügen und resetten
                part1.append(currentLine).append("\n");
                currentLine = new StringBuilder(word);
            }
            wordIndex = i + 1; // Merken, bis wohin wir gekommen sind
        }
        
        // Letzte Zeile von Part 1 noch hinzufügen
        if (linesCount <= maxLines) {
             part1.append(currentLine);
        }

        // Den Rest für Seite 2 zusammenbauen
        StringBuilder part2 = new StringBuilder();
        // Wenn wir in der Schleife abgebrochen haben (Break), fangen wir beim wordIndex an
        // Falls wir nicht am Ende waren:
        if (wordIndex < words.length) {
             // Das Wort, das den Umbruch ausgelöst hat, ist schon in currentLine (für den Fall oben)
             // Hier vereinfachte Logik: Bau den Reststring zusammen
             // Achtung: Wenn wir im Loop ge-breakt sind, ist `currentLine` das erste Wort der neuen Zeile (Teil 2).
             // ABER: In der Loop-Logik oben wird `currentLine` neu gesetzt auf `word`.
             // Wenn linesCount > maxLines, brechen wir VOR dem appenden an part1 ab. 
             // Das heißt `currentLine` enthält das Wort, das den Überlauf verursacht hat.
             // Dieses muss als erstes in part2.
             
             part2.append(currentLine); 
             
             for(int j = wordIndex; j < words.length; j++) {
                 if(part2.length() > 0) part2.append(" ");
                 part2.append(words[j]);
             }
        }

        return new String[] { part1.toString(), part2.toString() };
    }
}
