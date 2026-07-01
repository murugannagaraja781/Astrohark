const { swissEph } = require('./utils/rasiEng/swisseph');
const { DateTime } = require('luxon');

const dt = DateTime.now().setZone('UTC+5.5');
const utc = dt.toUTC();
const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);

const sunriseJd = swissEph.getSunrise(jd, 13.08, 80.27);
console.log("Sunrise JD:", sunriseJd);

const sun = swissEph.calcPlanetSidereal(sunriseJd, 0, 'Lahiri');
console.log("Sun at sunrise:", sun);
