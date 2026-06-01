const swisseph = require('swisseph');
const luxon = require('luxon');

// Vimshottari Lords order
const LORDS_SEQUENCE = [
    'Ketu', 'Venus', 'Sun', 'Moon', 'Mars', 'Rahu', 'Jupiter', 'Saturn', 'Mercury'
];

// Vimshottari Dasha years
const DASHA_YEARS = {
    'Ketu': 7, 'Venus': 20, 'Sun': 6, 'Moon': 10, 'Mars': 7,
    'Rahu': 18, 'Jupiter': 16, 'Saturn': 19, 'Mercury': 17
};

const PLANET_MAP = {
    0: 'Sun',
    1: 'Moon',
    2: 'Mercury',
    3: 'Venus',
    4: 'Mars',
    5: 'Jupiter',
    6: 'Saturn',
    10: 'Mean Node', // Rahu mean
    11: 'True Node', // Rahu true (using True Node for KP usually, but we will calculate both and return Rahu)
};

const NAKSHATRAS = [
    "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra", "Punarvasu", "Pushya", "Ashlesha",
    "Magha", "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha",
    "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"
];

const RASI_NAMES = [
    "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
    "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
];

function getNakshatraInfo(longitude) {
    const norm = (longitude % 360 + 360) % 360;
    const nakSize = 360 / 27; // 13.333333 degrees
    
    const nakIndex = Math.floor(norm / nakSize);
    const nakName = NAKSHATRAS[nakIndex];
    
    // Pada
    const padaSize = nakSize / 4; // 3.333333 degrees
    const pada = Math.floor((norm % nakSize) / padaSize) + 1;
    
    // Star Lord
    const lordIndex = nakIndex % 9;
    const starLord = LORDS_SEQUENCE[lordIndex];
    
    // Sub Lord Calculation
    const startOfNak = nakIndex * nakSize;
    let remainder = norm - startOfNak;
    
    let subLordIndex = lordIndex;
    let subLord = LORDS_SEQUENCE[subLordIndex];
    
    for (let i = 0; i < 9; i++) {
        const subLength = (DASHA_YEARS[LORDS_SEQUENCE[subLordIndex]] / 120) * nakSize;
        if (remainder <= subLength) {
            subLord = LORDS_SEQUENCE[subLordIndex];
            break;
        }
        remainder -= subLength;
        subLordIndex = (subLordIndex + 1) % 9;
    }
    
    return {
        nakshatra: nakName,
        pada: pada,
        starLord: starLord,
        subLord: subLord,
        rasi: RASI_NAMES[Math.floor(norm / 30)]
    };
}

async function getKPChart(date, time, lat, lon) {
    return new Promise((resolve, reject) => {
        try {
            const dt = luxon.DateTime.fromISO(`${date}T${time}`, { zone: 'Asia/Kolkata' });
            const utcDt = dt.toUTC();
            
            // Calculate Julian Day in UT
            const jd = swisseph.swe_julday(utcDt.year, utcDt.month, utcDt.day, utcDt.hour + utcDt.minute / 60 + utcDt.second / 3600, swisseph.SE_GREG_CAL);
            
            // Set Krishnamurti Ayanamsa (KP)
            swisseph.swe_set_sid_mode(swisseph.SE_SIDM_KRISHNAMURTI, 0, 0);
            const flag = swisseph.SEFLG_SIDEREAL | swisseph.SEFLG_SWIEPH;

            const calcPlanet = (planetId) => {
                const pos = swisseph.swe_calc_ut(jd, planetId, flag);
                return pos.longitude;
            };

            const planetsData = [];
            const planetsToCalc = [
                { id: swisseph.SE_SUN, name: 'Sun' },
                { id: swisseph.SE_MOON, name: 'Moon' },
                { id: swisseph.SE_MARS, name: 'Mars' },
                { id: swisseph.SE_MERCURY, name: 'Mercury' },
                { id: swisseph.SE_JUPITER, name: 'Jupiter' },
                { id: swisseph.SE_VENUS, name: 'Venus' },
                { id: swisseph.SE_SATURN, name: 'Saturn' },
                { id: swisseph.SE_MEAN_NODE, name: 'Rahu' } // KP typically uses Mean Node
            ];

            const planetPositions = {};

            planetsToCalc.forEach(p => {
                const lon = calcPlanet(p.id);
                planetPositions[p.name] = lon;
            });
            // Ketu is exactly 180 deg opposite Rahu
            planetPositions['Ketu'] = (planetPositions['Rahu'] + 180) % 360;

            const allPlanetsList = ['Sun', 'Moon', 'Mars', 'Mercury', 'Jupiter', 'Venus', 'Saturn', 'Rahu', 'Ketu'];
            
            // Calculate Houses (Placidus)
            const houses = swisseph.swe_houses_ex(jd, flag, lat, lon, 'P');
            const cusps = houses.cusps; // Array of 12 elements starting from index 0 (1st house is at index 0)
            
            // Function to find which Bhava (House) a longitude falls into
            const getBhava = (longitude) => {
                for (let i = 0; i < 12; i++) {
                    let start = cusps[i];
                    let end = cusps[(i + 1) % 12];
                    if (start <= end) {
                        if (longitude >= start && longitude < end) return i + 1;
                    } else {
                        // Wraps around 360
                        if (longitude >= start || longitude < end) return i + 1;
                    }
                }
                return 1; // Fallback
            };

            // Build Planet Objects
            allPlanetsList.forEach(name => {
                const lon = planetPositions[name];
                const info = getNakshatraInfo(lon);
                planetsData.push({
                    name: name,
                    longitude: lon,
                    rasi: info.rasi,
                    nakshatra: info.nakshatra,
                    pada: info.pada,
                    starLord: info.starLord,
                    subLord: info.subLord,
                    bhavaOccupied: getBhava(lon)
                });
            });

            // Calculate Bhava connections and Ninra Natchathira Athipathi
            planetsData.forEach(p => {
                const starLordData = planetsData.find(pl => pl.name === p.starLord);
                if (starLordData) {
                    p.ninraNatchathiraAthipathi = starLordData.starLord; // The star lord of the star lord
                    p.starLordBhava = starLordData.bhavaOccupied;
                    
                    const superLordData = planetsData.find(pl => pl.name === p.ninraNatchathiraAthipathi);
                    if (superLordData) {
                        p.ninraNatchathiraAthipathiBhava = superLordData.bhavaOccupied;
                        // Basic Bhava Thodarbu format (for the UI)
                        p.bhavaThodarbu = `${p.starLordBhava}, ${p.ninraNatchathiraAthipathiBhava}`;
                    } else {
                         p.bhavaThodarbu = `${p.starLordBhava}`;
                    }
                }
            });

            const housesData = [];
            for (let i = 0; i < 12; i++) {
                const lon = cusps[i];
                const info = getNakshatraInfo(lon);
                housesData.push({
                    bhava: i + 1,
                    longitude: lon,
                    rasi: info.rasi,
                    nakshatra: info.nakshatra,
                    pada: info.pada,
                    starLord: info.starLord,
                    subLord: info.subLord
                });
            }

            // Fill house connections similarly
            housesData.forEach(h => {
                const starLordData = planetsData.find(pl => pl.name === h.starLord);
                if (starLordData) {
                    h.ninraNatchathiraAthipathi = starLordData.starLord;
                    
                    const superLordData = planetsData.find(pl => pl.name === h.ninraNatchathiraAthipathi);
                    if (superLordData) {
                        h.bhavaThodarbu = `${starLordData.bhavaOccupied}, ${superLordData.bhavaOccupied}`;
                    } else {
                        h.bhavaThodarbu = `${starLordData.bhavaOccupied}`;
                    }
                }
            });

            resolve({
                planets: planetsData,
                houses: housesData
            });
            
        } catch (err) {
            reject(err);
        }
    });
}

module.exports = { getKPChart };
