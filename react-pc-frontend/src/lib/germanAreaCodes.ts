/**
 * Lookup deutsche Festnetz-Ortsvorwahl anhand PLZ oder Ort.
 * Deckt die wichtigsten Großstädte und Regionen ab. Für unbekannte
 * PLZs wird kein Vorschlag gemacht (Funktion gibt null zurück) — der
 * Nutzer kann dann manuell eintragen.
 */

// 3-Ziffer-PLZ-Prefix → Vorwahl. Wird zuerst geprüft (genauer).
const PLZ_PREFIX3_TO_AREA_CODE: Record<string, string> = {
    // Berlin (10xxx, 12xxx, 13xxx) und einzelne 14xxx
    '101': '030', '102': '030', '103': '030', '104': '030',
    '105': '030', '106': '030', '107': '030', '108': '030', '109': '030',
    '110': '030', '111': '030', '112': '030', '113': '030', '114': '030',
    '115': '030', '116': '030', '117': '030', '118': '030', '119': '030',
    '120': '030', '121': '030', '122': '030', '123': '030', '124': '030',
    '125': '030', '126': '030', '127': '030', '128': '030', '129': '030',
    '130': '030', '131': '030', '132': '030', '133': '030', '134': '030',
    '135': '030', '136': '030',
    '140': '030', '141': '030', '142': '030', '143': '030',
    '145': '030', '146': '030', '147': '030', '148': '030', '149': '030',

    // Potsdam (PLZ 144xx)
    '144': '0331',

    // Hamburg (20xxx-22xxx)
    '200': '040', '201': '040', '202': '040', '203': '040', '204': '040',
    '205': '040', '206': '040', '207': '040', '208': '040', '209': '040',
    '210': '040', '211': '040', '212': '040', '213': '040', '214': '040',
    '215': '040', '216': '040', '217': '040', '218': '040', '219': '040',
    '220': '040', '221': '040', '222': '040', '223': '040', '224': '040',
    '225': '040', '226': '040', '227': '040',

    // Bremen
    '281': '0421', '282': '0421', '283': '0421', '284': '0421', '285': '0421',

    // Hannover
    '300': '0511', '301': '0511', '302': '0511', '303': '0511', '304': '0511',
    '305': '0511', '306': '0511', '307': '0511', '308': '0511', '309': '0511',
    '310': '0511',

    // Braunschweig (38xxx)
    '380': '0531', '381': '0531', '382': '0531',

    // Bielefeld (33xxx)
    '335': '0521', '336': '0521',

    // Osnabrück (49xxx)
    '490': '0541', '491': '0541',

    // Dortmund (44xxx, ohne 447 = Bochum)
    '441': '0231', '442': '0231', '443': '0231', '444': '0231',
    '445': '0231', '446': '0231',

    // Bochum (PLZ 447xx, 448xx)
    '447': '0234', '448': '0234',

    // Essen (45xxx)
    '450': '0201', '451': '0201', '452': '0201', '453': '0201',
    '454': '0201', '455': '0201',

    // Wuppertal (42xxx)
    '420': '0202', '421': '0202', '422': '0202',

    // Duisburg (47xxx)
    '470': '0203', '471': '0203', '472': '0203',
    '473': '0203', '474': '0203', '475': '0203',

    // Düsseldorf (40xxx)
    '402': '0211', '403': '0211', '404': '0211', '405': '0211',
    '406': '0211', '407': '0211',

    // Köln (50xxx-51xxx)
    '506': '0221', '507': '0221', '508': '0221',
    '509': '0221',
    '510': '0221', '511': '0221', '512': '0221', '513': '0221',
    '514': '0221',

    // Bonn (53xxx)
    '531': '0228', '532': '0228', '533': '0228',

    // Aachen (52xxx)
    '520': '0241', '521': '0241',

    // Trier
    '540': '0651',

    // Koblenz
    '560': '0261',

    // Mainz
    '551': '06131', '552': '06131', '553': '06131',

    // Wiesbaden (65xxx)
    '650': '0611', '651': '0611', '652': '0611',

    // Frankfurt am Main (60xxx)
    '600': '069', '601': '069', '602': '069', '603': '069', '604': '069',
    '605': '069', '606': '069', '607': '069', '608': '069', '609': '069',
    '610': '069', '611': '069',

    // Darmstadt
    '642': '06151', '643': '06151',

    // Mannheim (68xxx)
    '680': '0621', '681': '0621', '682': '0621', '683': '0621', '684': '0621',
    '685': '0621', '686': '0621',

    // Heidelberg
    '691': '06221',

    // Karlsruhe (76xxx)
    '760': '0721', '761': '0721',

    // Stuttgart (70xxx)
    '700': '0711', '701': '0711', '702': '0711', '703': '0711',
    '704': '0711', '705': '0711', '706': '0711', '707': '0711',

    // Tübingen
    '720': '07071',

    // Ulm
    '890': '0731', '891': '0731',

    // Freiburg (79xxx)
    '790': '0761', '791': '0761',

    // München (80xxx-81xxx)
    '800': '089', '801': '089', '802': '089', '803': '089', '804': '089',
    '805': '089', '806': '089', '807': '089', '808': '089', '809': '089',
    '810': '089', '811': '089', '812': '089', '813': '089', '814': '089',
    '815': '089',

    // Augsburg (86xxx)
    '861': '0821', '862': '0821',

    // Würzburg (97xxx)
    '970': '0931', '971': '0931', '972': '0931',

    // Nürnberg (90xxx)
    '900': '0911', '901': '0911', '902': '0911', '903': '0911', '904': '0911',
    '905': '0911',

    // Erlangen
    '910': '09131',

    // Regensburg
    '930': '0941',

    // Leipzig (04xxx) — siehe 2-stellig
    // Dresden (01xxx) — siehe 2-stellig
    // Magdeburg (39xxx) — siehe 2-stellig
    // Halle, Erfurt, Chemnitz etc.
};

// 2-Ziffer-PLZ-Prefix → Vorwahl als Fallback (gröber).
const PLZ_PREFIX2_TO_AREA_CODE: Record<string, string> = {
    '01': '0351', // Dresden-Region
    '04': '0341', // Leipzig-Region
    '06': '0345', // Halle/Saale
    '07': '03641', // Jena-Region
    '08': '0371', // Chemnitz/Zwickau-Region
    '09': '0371', // Chemnitz-Region
    '14': '0331', // Potsdam-Brandenburg
    '15': '0335', // Frankfurt (Oder)
    '16': '03303', // Brandenburg-Nord
    '17': '0395', // Mecklenburg/Neubrandenburg
    '18': '0381', // Rostock-Region
    '19': '0385', // Schwerin-Region
    '23': '04101', // SH-Süd / Pinneberg
    '24': '0431', // Kiel
    '25': '04821', // SH-Nordwest
    '26': '0441', // Oldenburg-Region
    '27': '0421', // Bremen-Umland
    '28': '0421', // Bremen
    '29': '05121', // Hildesheim-Region
    '32': '0521', // Bielefeld-Region
    '33': '0521', // Bielefeld
    '34': '0561', // Kassel-Region
    '35': '0641', // Gießen-Region
    '36': '03691', // Eisenach
    '37': '0551', // Göttingen
    '38': '0531', // Braunschweig
    '39': '0391', // Magdeburg
    '46': '02831', // Niederrhein
    '48': '0251', // Münster
    '49': '0541', // Osnabrück
    '54': '0651', // Trier
    '55': '06131', // Mainz
    '56': '0261', // Koblenz
    '57': '02732', // Westerwald
    '58': '02331', // Hagen
    '59': '02941', // Lippstadt-Region
    '63': '06151', // Darmstadt
    '64': '06151', // Darmstadt-Region
    '66': '0681', // Saarbrücken
    '67': '0631', // Kaiserslautern
    '69': '06221', // Heidelberg
    '71': '07141', // Ludwigsburg
    '72': '07071', // Tübingen
    '73': '0731', // Ulm
    '74': '07131', // Heilbronn
    '75': '07231', // Pforzheim
    '77': '0781', // Offenburg
    '78': '07531', // Konstanz
    '82': '08151', // Bayern-West
    '83': '08382', // Bodensee-Bayern
    '84': '0841', // Ingolstadt
    '85': '0841', // Ingolstadt
    '87': '0831', // Allgäu
    '88': '0751', // Ravensburg-Region
    '92': '0921', // Bayreuth
    '93': '09621', // Oberpfalz
    '94': '0851', // Passau
    '95': '0921', // Bayreuth-Region
    '96': '0951', // Bamberg
    '99': '0361'  // Erfurt
};

const CITY_TO_AREA_CODE: Record<string, string> = {
    'berlin': '030',
    'hamburg': '040',
    'munchen': '089', 'münchen': '089',
    'koln': '0221', 'köln': '0221',
    'frankfurt': '069', 'frankfurt am main': '069',
    'stuttgart': '0711',
    'dusseldorf': '0211', 'düsseldorf': '0211',
    'leipzig': '0341',
    'dortmund': '0231',
    'essen': '0201',
    'bremen': '0421',
    'dresden': '0351',
    'hannover': '0511',
    'nurnberg': '0911', 'nürnberg': '0911',
    'duisburg': '0203',
    'bochum': '0234',
    'wuppertal': '0202',
    'bielefeld': '0521',
    'bonn': '0228',
    'munster': '0251', 'münster': '0251',
    'mannheim': '0621',
    'karlsruhe': '0721',
    'augsburg': '0821',
    'wiesbaden': '0611',
    'monchengladbach': '02161', 'mönchengladbach': '02161',
    'gelsenkirchen': '0209',
    'aachen': '0241',
    'braunschweig': '0531',
    'kiel': '0431',
    'chemnitz': '0371',
    'halle': '0345', 'halle (saale)': '0345', 'halle saale': '0345',
    'magdeburg': '0391',
    'freiburg': '0761', 'freiburg im breisgau': '0761',
    'krefeld': '02151',
    'mainz': '06131',
    'lubeck': '0451', 'lübeck': '0451',
    'oberhausen': '0208',
    'erfurt': '0361',
    'rostock': '0381',
    'kassel': '0561',
    'hagen': '02331',
    'saarbrucken': '0681', 'saarbrücken': '0681',
    'potsdam': '0331',
    'mulheim': '0208', 'mülheim': '0208', 'mülheim an der ruhr': '0208',
    'ludwigshafen': '0621', 'ludwigshafen am rhein': '0621',
    'oldenburg': '0441',
    'osnabruck': '0541', 'osnabrück': '0541',
    'leverkusen': '0214',
    'solingen': '0212',
    'heidelberg': '06221',
    'herne': '02323',
    'neuss': '02131',
    'darmstadt': '06151',
    'paderborn': '05251',
    'regensburg': '0941',
    'ingolstadt': '0841',
    'wurzburg': '0931', 'würzburg': '0931',
    'wolfsburg': '05361',
    'fürth': '0911', 'furth': '0911',
    'ulm': '0731',
    'heilbronn': '07131',
    'pforzheim': '07231',
    'gottingen': '0551', 'göttingen': '0551',
    'bottrop': '02041',
    'trier': '0651',
    'recklinghausen': '02361',
    'reutlingen': '07121',
    'bremerhaven': '0471',
    'koblenz': '0261',
    'bergisch gladbach': '02202',
    'jena': '03641',
    'remscheid': '02191',
    'erlangen': '09131',
    'moers': '02841',
    'siegen': '0271',
    'hildesheim': '05121',
    'salzgitter': '05341',
    'cottbus': '0355',
    'kaiserslautern': '0631',
    'gera': '0365',
    'witten': '02302',
    'iserlohn': '02371',
    'gutersloh': '05241', 'gütersloh': '05241',
    'schwerin': '0385',
    'esslingen': '0711', 'esslingen am neckar': '0711',
    'ratingen': '02102',
    'hanau': '06181',
    'ludwigsburg': '07141',
    'tubingen': '07071', 'tübingen': '07071',
    'flensburg': '0461',
    'villingen-schwenningen': '07721',
    'konstanz': '07531',
    'worms': '06241',
    'marl': '02365',
    'velbert': '02051',
    'minden': '0571',
    'dessau': '0340', 'dessau-rosslau': '0340',
    'rosenheim': '08031',
    'neumunster': '04321', 'neumünster': '04321',
    'bayreuth': '0921',
    'troisdorf': '02241',
    'viersen': '02162',
    'bocholt': '02871',
    'norderstedt': '040',
    'lunen': '02306', 'lünen': '02306',
    'castrop-rauxel': '02305',
    'dorsten': '02362',
    'gladbeck': '02043',
    'aalen': '07361',
    'arnsberg': '02931',
    'rheine': '05971',
    'lippstadt': '02941',
    'unna': '02303',
    'celle': '05141',
    'dinslaken': '02064',
    'frechen': '02234',
    'frankfurt oder': '0335', 'frankfurt (oder)': '0335',
    'wesel': '0281',
    'hurth': '02233', 'hürth': '02233',
    'kerpen': '02237',
    'meerbusch': '02132',
    'pulheim': '02238',
    'gummersbach': '02261',
    'freising': '08161',
    'neu-ulm': '0731', 'neu ulm': '0731',
    'plauen': '03741',
    'fulda': '0661',
    'gießen': '0641', 'giessen': '0641',
    'eisenach': '03691',
    'baden-baden': '07221', 'baden baden': '07221',
    'pirmasens': '06331',
    'lingen': '0591', 'lingen (ems)': '0591',
    'goppingen': '07161', 'göppingen': '07161',
    'greußenheim': '09369', 'greussenheim': '09369'
};

function normalizeOrt(ort: string): string {
    return ort
        .toLowerCase()
        .trim()
        .replace(/[ß]/g, 'ss')
        .replace(/\s+/g, ' ');
}

/**
 * Liefert die deutsche Ortsvorwahl basierend auf PLZ oder Ort.
 * @returns Vorwahl mit führender 0 (z.B. "0511") oder null wenn unbekannt.
 */
export function lookupAreaCode(plz: string, ort?: string): string | null {
    const cleanPlz = (plz || '').replace(/\D/g, '');

    if (cleanPlz.length >= 3 && PLZ_PREFIX3_TO_AREA_CODE[cleanPlz.substring(0, 3)]) {
        return PLZ_PREFIX3_TO_AREA_CODE[cleanPlz.substring(0, 3)];
    }
    if (ort) {
        const key = normalizeOrt(ort);
        if (CITY_TO_AREA_CODE[key]) return CITY_TO_AREA_CODE[key];
        const altKey = key.replace(/ss/g, 'ß');
        if (CITY_TO_AREA_CODE[altKey]) return CITY_TO_AREA_CODE[altKey];
    }
    if (cleanPlz.length >= 2 && PLZ_PREFIX2_TO_AREA_CODE[cleanPlz.substring(0, 2)]) {
        return PLZ_PREFIX2_TO_AREA_CODE[cleanPlz.substring(0, 2)];
    }
    return null;
}
