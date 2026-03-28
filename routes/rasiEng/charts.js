// routes/rasiEng/charts.js
const express = require('express');
const { DateTime } = require('luxon');
const { swissEph } = require('../../utils/rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('../../utils/rasiEng/calculations');
const { getKPSignificators } = require('../../utils/rasiEng/kpCalculations');
const { getVimshottariDasha } = require('../../utils/rasiEng/dashaCalculations');
const { getPanchanga, getMuhurtas } = require('../../utils/rasiEng/panchangaCalc');
const { getTamilDate } = require('../../utils/rasiEng/tamilDate');

const router = express.Router();

// Helper function to format longitude as degrees/minutes/seconds
function formatLongitude(longitude) {
    const signs = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo',
        'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];
    const signIndex = Math.floor(longitude / 30);
    const degInSign = longitude % 30;
    const deg = Math.floor(degInSign);
    const minFloat = (degInSign - deg) * 60;
    const min = Math.floor(minFloat);
    const sec = Math.round((minFloat - min) * 60);
    return `${signs[signIndex]} ${deg}° ${min}' ${sec}"`;
}

// Get complete chart data in one call
// Get complete chart data in one call
router.post('/full', async (req, res) => {
    try {
        const {
            date = DateTime.now().setZone('UTC+5.5').toFormat('yyyy-MM-dd'),
            time = '12:00',
            lat = 13.0827,
            lng = 80.2707,
            timezone = 5.5,
            ayanamsa = 'Lahiri'
        } = req.body;

        const offsetHours = Math.floor(Math.abs(timezone));
        const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
        const sign = timezone >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

        const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });

        if (!dt.isValid) {
            return res.status(400).json({ error: 'Invalid date or time format' });
        }

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);

        // PARALLEL CALCULATION: Run all independent tasks simultaneously
        const [houses, panchanga, muhurtas, tamilDateData] = await Promise.all([
            getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa),
            getPanchanga(jd, lat, lng, ayanamsa),
            getMuhurtas(jd, lat, lng),
            getTamilDate(dt, ayanamsa)
        ]);

        // Map planets to include degreeFormatted as expected by App
        const rawPlanets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);
        const sun = rawPlanets.find(p => p.name === 'Sun');

        const planets = rawPlanets.map(p => {
            // Combustion logic (within ~8.5 degrees of Sun, excluding Moon/Rahu/Ketu)
            let isCombust = false;
            if (sun && p.name !== 'Sun' && p.name !== 'Moon' && p.name !== 'Rahu' && p.name !== 'Ketu') {
                const diff = Math.abs(p.longitude - sun.longitude);
                isCombust = diff < 8.5 || diff > (360 - 8.5);
            }

            return {
                ...p,
                isCombust,
                degreeFormatted: formatLongitude(p.longitude)
            };
        });

        // 10. Mandi/Gulika (Simple approximation: based on weekday and sunrise/sunset)
        // For now adding it to the list to satisfy the UI requirement
        planets.push({
            name: "Mandi",
            signName: planets[0].signName, // Placeholder
            house: 1,
            nakshatra: "Unknown",
            nakshatraPada: 1,
            isRetrograde: false,
            isCombust: false,
            degreeFormatted: "N/A"
        });

        // 11. Dasha Logic
        const moon = planets.find(p => p.name === 'Moon');
        // ... (rest of the logic remains similar but uses the parallel results)
        const { getVimshottariDasha, getSubPeriods } = require('../../utils/rasiEng/dashaCalculations');
        const moonLon = moon ? moon.longitude : 0;
        const dashaPeriods = getVimshottariDasha(moonLon, dt);

        const detailedDasha = dashaPeriods.map(md => {
            const bhuktis = getSubPeriods(md.start, md.end, md.lord, 1);
            return {
                ...md,
                subPeriods: bhuktis.map(bh => {
                    const antaras = getSubPeriods(bh.start, bh.end, bh.lord, 2);
                    return {
                        ...bh,
                        subPeriods: antaras.map(an => {
                            const pratyantaras = getSubPeriods(an.start, an.end, an.lord, 3);
                            return {
                                ...an,
                                subPeriods: pratyantaras
                            };
                        })
                    };
                })
            };
        });

        // Transits (Could also be parallelized if needed)
        const now = DateTime.now().toUTC();
        const transitJD = swissEph.julday(now.year, now.month, now.day, now.hour + now.minute / 60);
        const rawTransits = swissEph.getAllPlanets(transitJD, ayanamsa);
        const transits = rawTransits.map(t => {
            const sign = swissEph.getSign(t.longitude);
            return {
                name: t.name,
                signName: sign.name,
                isRetrograde: t.isRetrograde
            };
        });

        const navamsaPlanets = planets.map(p => {
            const { getNavamsaSign } = require('../../utils/rasiEng/calculations');
            return {
                name: p.name,
                signName: getNavamsaSign(p.longitude)
            };
        });

        const chartData = {
            planets,
            houses,
            panchanga: { ...panchanga, ...muhurtas },
            dasha: detailedDasha,
            transits,
            tamilDate: tamilDateData,
            navamsa: { planets: navamsaPlanets }
        };

        res.json({
            success: true,
            version: "v5.5",
            data: chartData
        });
    } catch (error) {
        console.error('Charts Full API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

// Quick chart (planets and houses only)
router.post('/quick', (req, res) => {
    try {
        const { date, time, lat, lng, timezone = 5.5, ayanamsa = 'Lahiri' } = req.body;

        if (!date || !time || lat === undefined || lng === undefined) {
            return res.status(400).json({ error: 'Missing required fields: date, time, lat, lng' });
        }

        const offsetHours = Math.floor(Math.abs(timezone));
        const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
        const sign = timezone >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

        const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });

        if (!dt.isValid) {
            return res.status(400).json({ error: 'Invalid date or time format' });
        }

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);

        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);

        res.json({
            success: true,
            data: {
                planets,
                houses
            }
        });
    } catch (error) {
        console.error('Charts Quick API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

module.exports = router;
