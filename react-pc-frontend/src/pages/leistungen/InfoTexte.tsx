import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';

/**
 * Die Erklärungen („Was passiert im Hintergrund?“ / „Was bewirkt das?“) der
 * Seite „Leistungen & Kategorien“ an einem Ort – „Stunden je Einheit“ steht an
 * zwei Stellen und soll dort gleich lauten.
 */

const HINTERGRUND = 'Was passiert im Hintergrund?';
const BEWIRKT = 'Was bewirkt das?';

function Erklaerung({ titel, hintergrund, bewirkt }: { titel: string; hintergrund: string; bewirkt: string }) {
    return (
        <InfoButton titel={titel}>
            <InfoAbschnitt titel={HINTERGRUND}><p>{hintergrund}</p></InfoAbschnitt>
            <InfoAbschnitt titel={BEWIRKT}><p>{bewirkt}</p></InfoAbschnitt>
        </InfoButton>
    );
}

export const InfoUmziehen = () => (
    <Erklaerung
        titel="In anderen Ordner umziehen"
        hintergrund="Die Projekte dieses Ordners werden dem Ziel-Ordner zugeordnet – mit ihren Arbeitszeiten. Unterordner und Leistungen ziehen mit um. Jede umgehängte Arbeitszeit wird im Änderungsprotokoll der Zeiterfassung vermerkt."
        bewirkt="Die Auswertung des Ziel-Ordners enthält danach auch diese Projekte. Die Mengen kommen wie gewohnt aus Angebot bzw. Auftragsbestätigung des Projekts. Angebote und Rechnungen bleiben unverändert. Hat der Ziel-Ordner eine andere Einheit, passen die Mengen in der Auswertung nicht mehr zusammen."
    />
);

export const InfoAllesLoeschen = () => (
    <Erklaerung
        titel="Ordner und Inhalt löschen"
        hintergrund="Der Ordner, alle Unterordner und die Leistungen darin werden gelöscht. Die Projekte verlieren diese Kategorie; bei den Arbeitszeiten wird das im Änderungsprotokoll der Zeiterfassung vermerkt."
        bewirkt="Die Arbeitszeiten bleiben am Projekt, zählen aber in keiner Ordner-Auswertung mehr. Angebote und Rechnungen behalten Text und Preis. Das lässt sich nicht rückgängig machen."
    />
);

export const InfoLeistungLoeschen = () => (
    <Erklaerung
        titel="Leistung löschen"
        hintergrund="Die Leistung verschwindet aus der Auswahl im Angebots-Editor. In den genannten Dokumenten steht sie mit Text und Preis fest drin."
        bewirkt="Bestehende Angebote, Auftragsbestätigungen und Rechnungen ändern sich nicht. Die Projekte behalten ihre Kategorie, weil sie auch in der Angebotsposition steht – nur bei sehr alten Angeboten ohne diese Angabe kann sie beim nächsten Abgleich wegfallen. Gebuchte Arbeitszeiten bleiben immer erhalten."
    />
);

export const InfoBeschreibung = () => (
    <Erklaerung
        titel="Beschreibung des Ordners"
        hintergrund="Die Beschreibung gilt für den ganzen Ordner. Legen Sie darin eine neue Leistung an, wird sie – zusammen mit den Beschreibungen der übergeordneten Ordner – als Starttext vorgeschlagen."
        bewirkt="Bestehende Leistungen und Dokumente ändern sich nicht."
    />
);

export const InfoEinheit = () => (
    <Erklaerung
        titel="Einheit des Ordners"
        hintergrund="In dieser Einheit werden die Mengen der Projekte in diesem Ordner gezählt, z. B. Meter Geländer."
        bewirkt="Die Auswertung rechnet damit „Stunden je Meter“. Unterordner mit einer anderen Einheit zählen bei Menge und „Stunden je Einheit“ nicht mit."
    />
);

export const InfoStundenJeEinheit = () => (
    <Erklaerung
        titel="Stunden je Einheit"
        hintergrund="Alle Arbeitszeiten, die bei abgeschlossenen Projekten auf diesen Ordner gebucht wurden, geteilt durch die Menge aus den Auftragsbestätigungen (ohne Auftragsbestätigung: aus dem Angebot)."
        bewirkt="Ein Richtwert für die Kalkulation: Wie lange brauchen wir im Schnitt für eine Einheit? Offene Projekte zählen noch nicht mit."
    />
);

export const InfoJahr = () => (
    <Erklaerung
        titel="Jahr der Auswertung"
        hintergrund="Gezählt wird das Jahr, in dem ein Projekt abgeschlossen wurde."
        bewirkt="Projekte, die über den Jahreswechsel liefen, zählen ganz in ihr Abschlussjahr."
    />
);

export const InfoVerschieben = () => (
    <Erklaerung
        titel="Ordner verschieben"
        hintergrund="Projekte, Leistungen und Unterordner ziehen mit dem Ordner um."
        bewirkt="Die Auswertung des bisherigen übergeordneten Ordners zählt sie danach nicht mehr mit, die des neuen schon. Angebote und Rechnungen bleiben unverändert."
    />
);

export const InfoSeite = () => (
    <Erklaerung
        titel="Leistungen & Kategorien"
        hintergrund="Die Ordner Ihrer Leistungen sind zugleich die Kategorien Ihrer Projekte. Steht eine Leistung in Angebot oder Auftragsbestätigung, bekommt das Projekt automatisch deren Ordner als Kategorie."
        bewirkt="Ordnen Sie hier um, ändert sich auch, wie Projekte ausgewertet werden."
    />
);
