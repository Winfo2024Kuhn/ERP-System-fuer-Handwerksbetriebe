import { useState, useEffect } from 'react';
import { Mail, Loader2, Check, BarChart3 } from 'lucide-react';
import { Button } from '../components/ui/button';
import { SteuerberaterEmailModal } from '../components/SteuerberaterEmailModal';

interface MitarbeiterStunden {
    mitarbeiterId: number;
    mitarbeiterName: string;
    tagessollWoche: number; // Tagessoll (Wochenstunden / 5)
    sollstundenMonat: number; // Monatliche Sollstunden
    arbeitsstunden: number; // Tatsächliche Arbeitsstunden
    urlaub: number;
    feiertage: number;
    krankheit: number;
    fortbildung: number;
}

interface Zeitkonto {
    mitarbeiterId: number;
    mitarbeiterName: string;
    montagStunden: number;
    dienstagStunden: number;
    mittwochStunden: number;
    donnerstagStunden: number;
    freitagStunden: number;
    samstagStunden: number;
    sonntagStunden: number;
    wochenstunden: number;
}

export default function ZeiterfassungSteuerberater() {
    const [zeitkonten, setZeitkonten] = useState<Zeitkonto[]>([]);
    const [stundenDaten, setStundenDaten] = useState<MitarbeiterStunden[]>([]);
    const [selectedMitarbeiter, setSelectedMitarbeiter] = useState<number[]>([]);
    const [loading, setLoading] = useState(false);
    const [loadingZeitkonten, setLoadingZeitkonten] = useState(true);
    const [showEmailModal, setShowEmailModal] = useState(false);

    // Default to current month
    const today = new Date();
    const [jahr, setJahr] = useState(today.getFullYear());
    const [monat, setMonat] = useState(today.getMonth() + 1);

    // Load Zeitkonten on mount
    const loadZeitkonten = async () => {
        setLoadingZeitkonten(true);
        try {
            const res = await fetch('/api/zeitverwaltung/zeitkonten');
            const data = await res.json();
            setZeitkonten(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error('Fehler beim Laden der Zeitkonten:', err);
            setZeitkonten([]);
        }
        setLoadingZeitkonten(false);
    };

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        loadZeitkonten();
    }, []);

    const loadStundenDaten = async () => {
        if (zeitkonten.length === 0) return;

        setLoading(true);
        const results: MitarbeiterStunden[] = [];

        for (const konto of zeitkonten) {
            try {
                const res = await fetch(
                    `/api/zeitverwaltung/kalender?mitarbeiterId=${konto.mitarbeiterId}&jahr=${jahr}&monat=${monat}`
                );
                const data = await res.json();

                // Aggregate data from the calendar response
                let arbeitsstunden = 0;
                let urlaub = 0;
                let feiertage = 0;
                let krankheit = 0;
                let fortbildung = 0;

                // Get sollstundenMonat from backend response
                const sollstundenMonat = parseFloat(data.sollStundenMonat) || 0;

                if (data.tage && Array.isArray(data.tage)) {
                    for (const tag of data.tage) {
                        // Add holiday hours - use employee's sollstunden for that weekday
                        // Backend sets sollStunden to 0 for holidays, so we calculate based on weekday
                        if (tag.istFeiertag) {
                            // wochentag: 1=Monday, 2=Tuesday, ..., 7=Sunday
                            const wochentag = tag.wochentag;
                            let feiertagsStunden = 0;
                            switch (wochentag) {
                                case 1: feiertagsStunden = konto.montagStunden; break;
                                case 2: feiertagsStunden = konto.dienstagStunden; break;
                                case 3: feiertagsStunden = konto.mittwochStunden; break;
                                case 4: feiertagsStunden = konto.donnerstagStunden; break;
                                case 5: feiertagsStunden = konto.freitagStunden; break;
                                case 6: feiertagsStunden = konto.samstagStunden; break;
                                case 7: feiertagsStunden = konto.sonntagStunden; break;
                            }
                            feiertage += feiertagsStunden || 0;
                        }

                        // Process bookings for this day
                        if (tag.buchungen && Array.isArray(tag.buchungen)) {
                            for (const buchung of tag.buchungen) {
                                const dauerMinuten = buchung.dauerMinuten || 0;
                                const stunden = dauerMinuten / 60;

                                // Check if it's an absence type or work
                                if (buchung.typ === 'URLAUB') {
                                    urlaub += stunden;
                                } else if (buchung.typ === 'KRANKHEIT') {
                                    krankheit += stunden;
                                } else if (buchung.typ === 'FORTBILDUNG') {
                                    fortbildung += stunden;
                                } else if (buchung.typ === 'ARBEIT' || !buchung.typ) {
                                    // Regular work booking (ARBEIT) or legacy bookings without typ
                                    arbeitsstunden += stunden;
                                }
                            }
                        }
                    }
                }

                results.push({
                    mitarbeiterId: konto.mitarbeiterId,
                    mitarbeiterName: konto.mitarbeiterName,
                    tagessollWoche: Math.round((konto.wochenstunden / 5) * 10) / 10,
                    sollstundenMonat: Math.round(sollstundenMonat * 10) / 10,
                    arbeitsstunden: Math.round(arbeitsstunden * 10) / 10,
                    urlaub: Math.round(urlaub * 10) / 10,
                    feiertage: Math.round(feiertage * 10) / 10,
                    krankheit: Math.round(krankheit * 10) / 10,
                    fortbildung: Math.round(fortbildung * 10) / 10,
                });
            } catch (err) {
                console.error(`Fehler beim Laden der Daten für Mitarbeiter ${konto.mitarbeiterId}:`, err);
                results.push({
                    mitarbeiterId: konto.mitarbeiterId,
                    mitarbeiterName: konto.mitarbeiterName,
                    tagessollWoche: Math.round((konto.wochenstunden / 5) * 10) / 10,
                    sollstundenMonat: 0,
                    arbeitsstunden: 0,
                    urlaub: 0,
                    feiertage: 0,
                    krankheit: 0,
                    fortbildung: 0,
                });
            }
        }

        setStundenDaten(results);
        // Auto-select all by default
        setSelectedMitarbeiter(results.map(r => r.mitarbeiterId));
        setLoading(false);
    };

    const toggleMitarbeiter = (id: number) => {
        setSelectedMitarbeiter(prev =>
            prev.includes(id)
                ? prev.filter(x => x !== id)
                : [...prev, id]
        );
    };

    const toggleAll = () => {
        if (selectedMitarbeiter.length === stundenDaten.length) {
            setSelectedMitarbeiter([]);
        } else {
            setSelectedMitarbeiter(stundenDaten.map(m => m.mitarbeiterId));
        }
    };

    const getMonthName = (m: number) => {
        const months = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
            'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
        return months[m - 1] || '';
    };

    const selectedDaten = stundenDaten.filter(m => selectedMitarbeiter.includes(m.mitarbeiterId));

    return (
        <div className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Berichte
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900">
                        STUNDENÜBERMITTLUNG
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Monatliche Stundenaufstellung an den Steuerberater übermitteln
                    </p>
                </div>
            </div>

            <div className="space-y-6">
                {/* Filter Toolbar */}
                <div className="flex gap-4 items-end bg-white p-4 rounded-lg border border-slate-200 shadow-sm flex-wrap">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Monat</label>
                        <select
                            value={monat}
                            onChange={(e) => setMonat(parseInt(e.target.value))}
                            className="h-10 px-3 border border-slate-200 rounded-lg bg-white focus:border-rose-300 focus:ring-1 focus:ring-rose-200 outline-none"
                        >
                            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map(m => (
                                <option key={m} value={m}>{getMonthName(m)}</option>
                            ))}
                        </select>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Jahr</label>
                        <select
                            value={jahr}
                            onChange={(e) => setJahr(parseInt(e.target.value))}
                            className="h-10 px-3 border border-slate-200 rounded-lg bg-white focus:border-rose-300 focus:ring-1 focus:ring-rose-200 outline-none"
                        >
                            {[2024, 2025, 2026, 2027].map(y => (
                                <option key={y} value={y}>{y}</option>
                            ))}
                        </select>
                    </div>
                    <Button
                        onClick={loadStundenDaten}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                        disabled={loading || loadingZeitkonten}
                    >
                        {loading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <BarChart3 className="w-4 h-4 mr-2" />}
                        Vorschau laden
                    </Button>

                    {stundenDaten.length > 0 && selectedMitarbeiter.length > 0 && (
                        <Button
                            onClick={() => setShowEmailModal(true)}
                            className="bg-rose-600 hover:bg-rose-700 text-white ml-auto"
                        >
                            <Mail className="w-4 h-4 mr-2" />
                            Per E-Mail senden ({selectedMitarbeiter.length})
                        </Button>
                    )}
                </div>

                {/* Results Table */}
                {loading || loadingZeitkonten ? (
                    <div className="flex justify-center py-12">
                        <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                    </div>
                ) : stundenDaten.length > 0 ? (
                    <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
                        <div className="p-4 border-b border-slate-200 bg-slate-50">
                            <h3 className="font-bold text-lg text-slate-900">
                                Stundenaufstellung {getMonthName(monat)} {jahr}
                            </h3>
                            <p className="text-slate-500 text-sm">
                                Wähle die Mitarbeiter aus, die an den Steuerberater übermittelt werden sollen
                            </p>
                        </div>
                        <table className="w-full">
                            <thead className="bg-slate-50">
                                <tr>
                                    <th className="p-3 w-12">
                                        <button
                                            onClick={toggleAll}
                                            className={`w-5 h-5 rounded border-2 flex items-center justify-center transition-colors ${selectedMitarbeiter.length === stundenDaten.length
                                                ? 'bg-rose-600 border-rose-600 text-white'
                                                : 'border-slate-300 hover:border-rose-400'
                                                }`}
                                        >
                                            {selectedMitarbeiter.length === stundenDaten.length && (
                                                <Check className="w-3 h-3" />
                                            )}
                                        </button>
                                    </th>
                                    <th className="text-left p-3 font-medium text-slate-600">Nr.</th>
                                    <th className="text-left p-3 font-medium text-slate-600">Name</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Tagessoll</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Sollstunden</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Ist-Stunden</th>
                                    <th className="text-center p-3 font-medium text-slate-600">+/- Stunden</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Urlaub</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Feiertage</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Krankheit</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Fortbildung</th>
                                </tr>
                            </thead>
                            <tbody>
                                {stundenDaten.map((m, index) => (
                                    <tr
                                        key={m.mitarbeiterId}
                                        className={`border-t border-slate-100 hover:bg-slate-50 transition-colors ${selectedMitarbeiter.includes(m.mitarbeiterId) ? 'bg-rose-50/50' : ''
                                            }`}
                                    >
                                        <td className="p-3">
                                            <button
                                                onClick={() => toggleMitarbeiter(m.mitarbeiterId)}
                                                className={`w-5 h-5 rounded border-2 flex items-center justify-center transition-colors ${selectedMitarbeiter.includes(m.mitarbeiterId)
                                                    ? 'bg-rose-600 border-rose-600 text-white'
                                                    : 'border-slate-300 hover:border-rose-400'
                                                    }`}
                                            >
                                                {selectedMitarbeiter.includes(m.mitarbeiterId) && (
                                                    <Check className="w-3 h-3" />
                                                )}
                                            </button>
                                        </td>
                                        <td className="p-3 text-slate-500">{index + 1}</td>
                                        <td className="p-3 font-medium">{m.mitarbeiterName}</td>
                                        <td className="p-3 text-center">{m.tagessollWoche}</td>
                                        <td className="p-3 text-center">{m.sollstundenMonat}</td>
                                        <td className="p-3 text-center font-medium">{m.arbeitsstunden}</td>
                                        <td className={`p-3 text-center font-bold ${(m.arbeitsstunden + m.urlaub + m.feiertage + m.krankheit + m.fortbildung - m.sollstundenMonat) >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                                            {((m.arbeitsstunden + m.urlaub + m.feiertage + m.krankheit + m.fortbildung - m.sollstundenMonat) >= 0 ? '+' : '')}
                                            {Math.round((m.arbeitsstunden + m.urlaub + m.feiertage + m.krankheit + m.fortbildung - m.sollstundenMonat) * 10) / 10}
                                        </td>
                                        <td className="p-3 text-center text-blue-600">{m.urlaub}</td>
                                        <td className="p-3 text-center text-green-600">{m.feiertage}</td>
                                        <td className="p-3 text-center text-amber-600">{m.krankheit}</td>
                                        <td className="p-3 text-center text-purple-600">{m.fortbildung}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : (
                    <div className="text-center py-16 bg-slate-50 rounded-lg border border-dashed border-slate-300">
                        <BarChart3 className="w-12 h-12 text-slate-300 mx-auto mb-3" />
                        <p className="text-slate-500 font-medium">
                            Wähle einen Monat und klicke auf "Vorschau laden"
                        </p>
                    </div>
                )}
            </div>

            {/* Email Modal */}
            {showEmailModal && (
                <SteuerberaterEmailModal
                    isOpen={showEmailModal}
                    onClose={() => setShowEmailModal(false)}
                    mitarbeiterDaten={selectedDaten}
                    monat={monat}
                    jahr={jahr}
                    onSuccess={() => {
                        setShowEmailModal(false);
                    }}
                />
            )}
        </div>
    );
}
