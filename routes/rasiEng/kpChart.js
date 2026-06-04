const express = require('express');
const router = express.Router();
const { swissEph } = require('../../utils/rasiEng/swisseph');

// Vimshottari Lords order
const LORDS_SEQUENCE = ['Ketu', 'Venus', 'Sun', 'Moon', 'Mars', 'Rahu', 'Jupiter', 'Saturn', 'Mercury'];

// Vimshottari Dasha years
const DASHA_YEARS = {
    'Ketu': 7, 'Venus': 20, 'Sun': 6, 'Moon': 10, 'Mars': 7,
    'Rahu': 18, 'Jupiter': 16, 'Saturn': 19, 'Mercury': 17
};

function getNakshatraInfo(longitude) {
    const norm = (longitude % 360 + 360) % 360;
    const nakSize = 360 / 27; // 13.333333
    const nakIndex = Math.floor(norm / nakSize);
    
    // Nakshtras
    const naks = ["Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra", "Punarvasu", "Pushya", "Ashlesha",
    "Magha", "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha",
    "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"];
    
    // Rasis
    const rasiNames = ["Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"];

    const nakName = naks[nakIndex];
    const pada = Math.floor((norm % nakSize) / (nakSize / 4)) + 1;
    const starLord = LORDS_SEQUENCE[nakIndex % 9];
    
    // Sub Lord
    const startOfNak = nakIndex * nakSize;
    let remainder = norm - startOfNak;
    let subLordIndex = nakIndex % 9;
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
        rasi: rasiNames[Math.floor(norm / 30)]
    };
}

router.post(['/', '/kp-chart'], (req, res) => {
    try {
        const { date, time, lat, lon } = req.body;
        // date: YYYY-MM-DD, time: HH:MM:SS
        const [year, month, day] = date.split('-').map(Number);
        const timeParts = time.split(':').map(Number);
        const hour = timeParts[0] || 0;
        const min = timeParts[1] || 0;
        const sec = timeParts[2] || 0;
        
        // IST to UT offset = -5.5 hours
        const utHour = hour + (min / 60) + (sec / 3600) - 5.5;
        
        const jd = swissEph.julday(year, month, day, utHour);
        
        // Get planets
        const planetsData = swissEph.getAllPlanets(jd, 'KP');
        
        // Get houses
        const housesData = swissEph.getHouses(jd, lat, lon, 'Placidus', 'KP');
        const cusps = housesData.cusps;
        
        // Function to find Bhava based on Placidus Cusps
        const getBhava = (longitude) => {
            for (let i = 0; i < 12; i++) {
                let start = cusps[i];
                let end = cusps[(i + 1) % 12];
                if (start <= end) {
                    if (longitude >= start && longitude < end) return i + 1;
                } else {
                    if (longitude >= start || longitude < end) return i + 1;
                }
            }
            return 1;
        };

        const kpPlanets = [];
        planetsData.forEach(p => {
            const info = getNakshatraInfo(p.longitude);
            kpPlanets.push({
                name: p.name,
                longitude: p.longitude,
                rasi: info.rasi,
                nakshatra: info.nakshatra,
                pada: info.pada,
                starLord: info.starLord,
                subLord: info.subLord,
                bhavaOccupied: getBhava(p.longitude)
            });
        });

        // Ninra Natchathira Athipathi (Lord of Star occupied by Star Lord)
        kpPlanets.forEach(p => {
            const starLordData = kpPlanets.find(pl => pl.name === p.starLord);
            if (starLordData) {
                p.ninraNatchathiraAthipathi = starLordData.starLord;
                p.starLordBhava = starLordData.bhavaOccupied;
                
                const superLordData = kpPlanets.find(pl => pl.name === p.ninraNatchathiraAthipathi);
                if (superLordData) {
                    p.ninraNatchathiraAthipathiBhava = superLordData.bhavaOccupied;
                    p.bhavaThodarbu = [p.starLordBhava, p.ninraNatchathiraAthipathiBhava];
                } else {
                    p.bhavaThodarbu = [p.starLordBhava];
                }
            }
        });

        const kpHouses = [];
        for (let i = 0; i < 12; i++) {
            const info = getNakshatraInfo(cusps[i]);
            kpHouses.push({
                bhava: i + 1,
                longitude: cusps[i],
                rasi: info.rasi,
                nakshatra: info.nakshatra,
                pada: info.pada,
                starLord: info.starLord,
                subLord: info.subLord
            });
        }

        // House Connections
        kpHouses.forEach(h => {
            const starLordData = kpPlanets.find(pl => pl.name === h.starLord);
            if (starLordData) {
                h.ninraNatchathiraAthipathi = starLordData.starLord;
                const superLordData = kpPlanets.find(pl => pl.name === h.ninraNatchathiraAthipathi);
                if (superLordData) {
                    h.bhavaThodarbu = [starLordData.bhavaOccupied, superLordData.bhavaOccupied];
                } else {
                    h.bhavaThodarbu = [starLordData.bhavaOccupied];
                }
            }
        });

        res.json({
            success: true,
            data: {
                planets: kpPlanets,
                houses: kpHouses
            }
        });
        
    } catch (error) {
        console.error("KP Chart Error:", error);
        res.status(500).json({ success: false, error: error.message });
    }
});

module.exports = router;
