// utils/astroCalculations.js
const { DateTime } = require('luxon');
const { swissEph } = require('./rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('./rasiEng/calculations');
const { getPanchanga } = require('./rasiEng/panchangaCalc');

const { getVimshottariDasha, getFullDashaBreakdown } = require('./rasiEng/dashaCalculations');
const { getNavamsaSign } = require('./rasiEng/calculations');

/**
 * Main function to calculate all birth chart data
 */
function calculateBirthChart(dateInput, lat, lng, timezone = 'Asia/Kolkata') {
    // 1. Convert local time to GMT/UTC Julian Day in target timezone (IST default: Asia/Kolkata)
    const tz = timezone || 'Asia/Kolkata';
    let dt;
    if (typeof dateInput === 'string') {
        dt = DateTime.fromISO(dateInput, { zone: tz });
    } else if (dateInput instanceof Date) {
        dt = DateTime.fromObject({
            year: dateInput.getFullYear(),
            month: dateInput.getMonth() + 1,
            day: dateInput.getDate(),
            hour: dateInput.getHours(),
            minute: dateInput.getMinutes(),
            second: dateInput.getSeconds()
        }, { zone: tz });
    } else if (dateInput && typeof dateInput === 'object') {
        dt = DateTime.fromObject({
            year: dateInput.year,
            month: dateInput.month,
            day: dateInput.day,
            hour: dateInput.hour || 0,
            minute: dateInput.minute || 0,
            second: dateInput.second || 0
        }, { zone: tz });
    } else {
        dt = DateTime.now().setZone(tz);
    }

    const utcDt = dt.toUTC();

    const jd = swissEph.julday(
        utcDt.year,
        utcDt.month,
        utcDt.day,
        utcDt.hour + utcDt.minute / 60 + utcDt.second / 3600
    );

    // 2. Get House Cusps (Placidus system by default)
    const houseData = getHouseCusps(jd, lat, lng, 'Placidus', 'Lahiri');

    // 3. Get Planet Positions
    const planets = getPlanetsWithDetails(jd, houseData.cusps, 'Lahiri');

    // 4. Get Panchanga Data
    const panchanga = getPanchanga(jd, lat, lng, 'Lahiri');

    // 5. Get Dasha Data
    const moon = planets.find(p => p.name === 'Moon');
    const dashaObj = moon ? getFullDashaBreakdown(moon.longitude, dt) : null;
    const dasha = dashaObj ? dashaObj.mahadasha : null;

    // 6. Get Navamsa Data
    const navamsaPlanets = planets.map(p => ({
        name: p.name,
        signName: getNavamsaSign(p.longitude)
    }));
    const navamsaAscendant = getNavamsaSign(houseData.ascendant);

    // 7. Structure final response
    return {
        julianDay: jd,
        ayanamsa: houseData.ayanamsaValue,
        ascendant: houseData.ascendantDetails,
        planets: planets,
        houses: houseData.details,
        panchanga: panchanga,
        dasha: dasha,
        navamsa: {
            planets: navamsaPlanets,
            ascendant: navamsaAscendant
        }
    };
}

module.exports = {
    calculateBirthChart
};
