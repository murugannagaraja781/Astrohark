const { DateTime } = require('luxon');
const { getHouseCusps, getPlanetsWithDetails } = require('./utils/rasiEng/calculations');
const { getPanchanga, getMuhurtas } = require('./utils/rasiEng/panchangaCalc');
const { getTamilDate } = require('./utils/rasiEng/tamilDate');
const { swissEph } = require('./utils/rasiEng/swisseph');
const { getVimshottariDasha, getSubPeriods } = require('./utils/rasiEng/dashaCalculations');

async function test() {
    const dt = DateTime.now().setZone('UTC+05:30');
    const utc = dt.toUTC();
    const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);
    
    const [houses, panchanga, muhurtas, tamilDateData] = await Promise.all([
        getHouseCusps(jd, 13.08, 80.27, 'Placidus', 'Lahiri'),
        getPanchanga(jd, 13.08, 80.27, 'Lahiri'),
        getMuhurtas(jd, 13.08, 80.27),
        getTamilDate(dt, 'Lahiri')
    ]);
    console.log("Tamil Date:", tamilDateData);
    console.log("Panchanga keys:", Object.keys(panchanga));
    const moonLon = 0; 
    const dashaPeriods = getVimshottariDasha(moonLon, dt);
    console.log("Dasha[0]:", dashaPeriods[0]);
}
test();
