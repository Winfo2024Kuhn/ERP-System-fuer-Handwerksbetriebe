package org.example.kalkulationsprogramm.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.nio.file.Path;

import org.example.kalkulationsprogramm.service.LieferantDateiablage;
import org.mockito.AdditionalAnswers;
import org.mockito.stubbing.Answer;

/**
 * Lässt einen Mock der {@link LieferantDateiablage} (aus {@code @MockBean} oder {@code @Mock}) in
 * einen Testordner schreiben: Jeder Aufruf geht an eine echte Ablage mit diesem Ordner als
 * {@code upload.path}. So landen keine Testdateien im Arbeitsverzeichnis, und der Test prüft das
 * echte Speichern, Finden und Löschen. Die Weiterleitung ist {@code lenient}: Ein Test muss nicht
 * jede Methode der Ablage benutzen.
 */
public final class LieferantDateiablageImTestordner {

    private LieferantDateiablageImTestordner() {
    }

    /** Leitet alle Aufrufe von {@code mock} an eine echte Ablage unter {@code uploadPath} weiter. */
    public static LieferantDateiablage leiteUm(LieferantDateiablage mock, Path uploadPath) throws IOException {
        var echt = new LieferantDateiablage(uploadPath.toString());
        Answer<?> weiter = AdditionalAnswers.delegatesTo(echt);
        lenient().doAnswer(weiter).when(mock).speichereDokument(any(), any());
        lenient().doAnswer(weiter).when(mock).speichereDokument(any(), any(), any());
        lenient().doAnswer(weiter).when(mock).speichereBild(any(), any());
        lenient().doAnswer(weiter).when(mock).speichereBild(any(), any(), any());
        lenient().doAnswer(weiter).when(mock).loescheDokument(any(), any());
        lenient().doAnswer(weiter).when(mock).findeBild(any());
        lenient().doAnswer(weiter).when(mock).loescheBild(any());
        return echt;
    }
}
