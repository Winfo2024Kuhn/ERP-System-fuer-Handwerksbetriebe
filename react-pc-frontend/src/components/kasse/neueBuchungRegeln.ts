export type BuchungsArt =
    | 'GELD_EINGENOMMEN' | 'GELD_AUSGEGEBEN' | 'VON_BANK_GEHOLT'
    | 'ZUR_BANK_GEBRACHT' | 'EIGENES_GELD_EINGELEGT' | 'GELD_PRIVAT_ENTNOMMEN';

export interface KachelDefinition {
    art: BuchungsArt;
    titel: string;
    untertitel: string;
    icon: string;
    brauchtKonto: boolean;
    brauchtMwst: boolean;
    brauchtGegenpartei: boolean;
    erlaubtBelegdatei: boolean;
    erlaubtRechnung: boolean;
    kontoTyp?: 'AUFWAND' | 'ERTRAG';
}

export interface NeueBuchungFormular {
    betrag: string;
    datum: string;
    gegenpartei: string;
    beschreibung: string;
    sachkontoId: string;
    grundOhneBeleg: string;
    keinBelegVorhanden: boolean;
}

export const KACHELN: KachelDefinition[] = [
    { art: 'GELD_EINGENOMMEN', titel: 'Geld eingenommen', untertitel: 'Kunde hat bar bezahlt', icon: 'Banknote', brauchtKonto: true, brauchtMwst: true, brauchtGegenpartei: true, erlaubtBelegdatei: false, erlaubtRechnung: true, kontoTyp: 'ERTRAG' },
    { art: 'GELD_AUSGEGEBEN', titel: 'Geld ausgegeben', untertitel: 'bar bezahlt', icon: 'ShoppingCart', brauchtKonto: true, brauchtMwst: true, brauchtGegenpartei: true, erlaubtBelegdatei: true, erlaubtRechnung: false, kontoTyp: 'AUFWAND' },
    { art: 'VON_BANK_GEHOLT', titel: 'Geld von der Bank geholt', untertitel: 'Bargeld abgehoben', icon: 'ArrowDownToLine', brauchtKonto: false, brauchtMwst: false, brauchtGegenpartei: false, erlaubtBelegdatei: false, erlaubtRechnung: false },
    { art: 'ZUR_BANK_GEBRACHT', titel: 'Geld zur Bank gebracht', untertitel: 'Bargeld eingezahlt', icon: 'ArrowUpFromLine', brauchtKonto: false, brauchtMwst: false, brauchtGegenpartei: false, erlaubtBelegdatei: false, erlaubtRechnung: false },
    { art: 'EIGENES_GELD_EINGELEGT', titel: 'Eigenes Geld eingelegt', untertitel: 'Privateinlage', icon: 'PiggyBank', brauchtKonto: false, brauchtMwst: false, brauchtGegenpartei: false, erlaubtBelegdatei: false, erlaubtRechnung: false },
    { art: 'GELD_PRIVAT_ENTNOMMEN', titel: 'Geld privat entnommen', untertitel: 'Privatentnahme', icon: 'Wallet', brauchtKonto: false, brauchtMwst: false, brauchtGegenpartei: false, erlaubtBelegdatei: false, erlaubtRechnung: false },
];

export function pflichtfelderFehlen(art: BuchungsArt, formular: NeueBuchungFormular): string | null {
    const kachel = KACHELN.find(item => item.art === art);
    if (!formular.betrag.trim()) return 'Bitte trag einen Betrag ein.';
    if (!formular.datum) return 'Bitte wähle ein Datum.';
    if (kachel?.brauchtKonto && !formular.sachkontoId) return 'Bitte wähle ein Konto aus.';
    if (kachel?.brauchtGegenpartei && !formular.gegenpartei.trim()) return 'Bitte trag ein, von wem das Geld kam.';
    if (art === 'GELD_AUSGEGEBEN' && formular.keinBelegVorhanden && !formular.grundOhneBeleg.trim()) {
        return 'Bitte erklär, warum es keinen Beleg gibt.';
    }
    return null;
}
