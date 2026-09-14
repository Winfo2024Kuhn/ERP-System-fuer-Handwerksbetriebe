import { useId } from 'react';
import { Button } from '../ui/button';
import { Dialog, DialogHeader, DialogTitle, DialogFooter } from '../ui/dialog';

// Abweichung von der Spec: Es gibt keinen Doku-Server und keine Route fuer
// Markdown aus docs/. Ein Link auf die Repo-Datei wuerde im Programm ins Leere
// laufen. Deshalb lebt der Text hier im Dialog und in docs/KASSE_ANLEITUNG.md
// fuer Entwickler und Steuerberater. Beide Fassungen behalten dieselben
// Abschnittsueberschriften und werden bei inhaltlichen Aenderungen mitgezogen.
const abschnitte = [
    {
        "titel": "Was gehört ins Kassenbuch",
        "text": "Hier steht jede Bewegung von Bargeld: Geld von Kunden, bar bezahlte Ausgaben, Geld von der Bank und eigenes Geld. Überweisungen und Kartenzahlungen gehören zu den Belegen, verändern aber nicht das Bargeld in der Kasse. Trage Barbewegungen sofort ein, damit der angezeigte Bestand zur Kasse passt."
    },
    {
        "titel": "Was passiert bei jeder Buchung",
        "text": "Jede Zeile zeigt Datum, Anlass, Beleg, Einnahme oder Ausgabe und den Bestand danach. Vor dem Monatsabschluss steht die vorläufige Position in Klammern. Beim Abschluss bekommt die Buchung ihre feste Nummer. Die Suche blendet Zeilen aus; die Bestände und Summen gelten weiterhin für den gewählten Zeitraum."
    },
    {
        "titel": "Wenn kein Beleg da ist",
        "text": "Fehlt die Quittung, erstellt das Programm einen Ersatzbeleg. Dieser wird auch Eigenbeleg genannt. Beschreibe, wofür das Geld war und warum der Originalbeleg fehlt. Daraus wird keine Vorsteuer abgezogen: Die auf einer Lieferantenrechnung ausgewiesene Umsatzsteuer kann mit diesem Ersatzbeleg also nicht zurückgefordert werden."
    },
    {
        "titel": "Die Kasse darf nie unter null",
        "text": "Du kannst nur Bargeld ausgeben, das in der Kasse liegt. Fehlt Geld, trage zuerst die tatsächliche Bank-Abhebung oder eigenes eingelegtes Geld ein. Das Programm erstellt dafür einen Ersatzbeleg. Der Betrag muss zur wirklichen Bewegung passen."
    },
    {
        "titel": "Kasse zählen und Monat abschließen",
        "text": "Zähle das Bargeld und vergleiche es über „Kasse zählen“ mit dem angezeigten Bestand. Kläre Unterschiede, bevor du den Monat abschließt. Beim Abschluss bleiben die Buchungen mit fester Nummer erhalten. Datum und Betrag lassen sich danach nicht mehr einfach ändern."
    },
    {
        "titel": "Falsch gebucht — was jetzt",
        "text": "Storniere die falsche Buchung. Das heißt: Eine neue Gegenbuchung hebt ihren Betrag auf. Das Original bleibt sichtbar und trägt die Markierung „Storniert“, die neue Zeile „Gegenbuchung“. Trage anschließend die richtige Buchung ein, falls sie noch fehlt."
    },
    {
        "titel": "Was der Steuerberater bekommt",
        "text": "Der Export enthält die Buchungen für den Steuerberater als DATEV-Datei und die zugehörigen Belege. DATEV ist ein verbreitetes Programm in Steuerkanzleien. Prüfe vor dem Export, ob die Buchungen vollständig sind und jeder Betrag einen Beleg hat."
    }
];

export function KasseAnleitungDialog({ onClose }: { onClose: () => void }) {
    const titelId = useId();
    return <Dialog open onOpenChange={offen => { if (!offen) onClose(); }} aria-labelledby={titelId}
        className="w-full max-w-3xl min-w-0 gap-5">
        <DialogHeader className="pr-8 text-left"><DialogTitle id={titelId} className="leading-snug text-slate-900">
            So funktioniert die Kasse
        </DialogTitle></DialogHeader>
        <div className="min-h-0 min-w-0 space-y-5 overflow-y-auto pr-2">
            {abschnitte.map(abschnitt => <section key={abschnitt.titel} className="min-w-0">
                <h4 className="font-semibold text-slate-900 break-words">{abschnitt.titel}</h4>
                <p className="mt-1 text-sm leading-relaxed text-slate-600 break-words">{abschnitt.text}</p>
            </section>)}
        </div>
        <DialogFooter><Button size="sm" onClick={onClose}>Verstanden</Button></DialogFooter>
    </Dialog>;
}
