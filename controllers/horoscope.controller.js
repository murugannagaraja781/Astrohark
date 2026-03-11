const { DateTime } = require('luxon');
const { swissEph } = require('../utils/rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('../utils/rasiEng/calculations');
const { calculatePorutham } = require('../utils/rasiEng/matchCalculations');

exports.getBirthChart = async (req, res) => {
    try {
        const { date, time, lat, lng, timezone = 5.5, ayanamsa = 'Lahiri' } = req.body;
        const offsetHours = Math.floor(Math.abs(timezone));
        const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
        const sign = timezone >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

        const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });
        if (!dt.isValid) return res.status(400).json({ error: 'Invalid date or time' });

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60);
        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);

        res.json({ success: true, data: { planets, houses } });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
};

exports.getMatchPorutham = async (req, res) => {
    try {
        const {
            groomDate, groomTime, groomLat, groomLng, groomTimezone = 5.5,
            brideDate, brideTime, brideLat, brideLng, brideTimezone = 5.5,
            groomMoonLon, brideMoonLon
        } = req.body;

        let gMoonLon, bMoonLon;

        if (groomMoonLon !== undefined && brideMoonLon !== undefined) {
            gMoonLon = groomMoonLon;
            bMoonLon = brideMoonLon;
        } else {
            const gDt = parseDateTime(groomDate, groomTime || '12:00', groomTimezone);
            const bDt = parseDateTime(brideDate, brideTime || '12:00', brideTimezone);
            if (!gDt.isValid || !bDt.isValid) return res.status(400).json({ success: false, error: 'Invalid datetime' });

            const gJd = swissEph.julday(gDt.toUTC().year, gDt.toUTC().month, gDt.toUTC().day, gDt.toUTC().hour + gDt.toUTC().minute / 60);
            const bJd = swissEph.julday(bDt.toUTC().year, bDt.toUTC().month, bDt.toUTC().day, bDt.toUTC().hour + bDt.toUTC().minute / 60);

            const gPlanets = swissEph.getAllPlanets(gJd, 'Lahiri');
            const bPlanets = swissEph.getAllPlanets(bJd, 'Lahiri');

            gMoonLon = gPlanets.find(p => p.name === 'Moon')?.longitude;
            bMoonLon = bPlanets.find(p => p.name === 'Moon')?.longitude;
        }

        if (gMoonLon === undefined || bMoonLon === undefined) return res.status(500).json({ success: false, error: 'Moon calculation failed' });

        const result = calculatePorutham(gMoonLon, bMoonLon);
        res.json({ success: true, data: result });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
};

function parseDateTime(date, time, tz) {
    const offsetHours = Math.floor(Math.abs(tz));
    const offsetMinutes = Math.round((Math.abs(tz) - offsetHours) * 60);
    const sign = tz >= 0 ? '+' : '-';
    const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;
    return DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });
}
