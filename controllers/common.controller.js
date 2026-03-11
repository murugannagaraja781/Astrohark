const fetch = (...args) => import('node-fetch').then(({ default: fetch }) => fetch(...args));

exports.cityAutocomplete = async (req, res) => {
    try {
        const { query } = req.body;
        if (!query || query.trim().length < 2) return res.json({ ok: true, results: [] });

        const nominatimUrl = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)},India&format=json&limit=50&countrycodes=in`;
        const response = await fetch(nominatimUrl, { headers: { 'User-Agent': 'AstroApp/1.0' } });
        if (!response.ok) return res.json({ ok: true, results: [] });

        const data = await response.json();
        let results = data.map(item => ({
            name: item.name, state: item.address?.state || '',
            country: item.address?.country || 'India', latitude: parseFloat(item.lat),
            longitude: parseFloat(item.lon), displayName: item.display_name
        }));

        const tamilNaduCities = results.filter(r => r.state === 'Tamil Nadu');
        const otherCities = results.filter(r => r.state !== 'Tamil Nadu');
        results = [...tamilNaduCities, ...otherCities];

        const seen = new Set();
        results = results.filter(r => {
            const key = `${r.name}-${r.state}`;
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
        }).slice(0, 10);

        res.json({ ok: true, results });
    } catch (error) {
        res.json({ ok: false, error: 'Failed', results: [] });
    }
};

exports.cityTimezone = async (req, res) => {
    try {
        const { latitude, longitude } = req.body;
        if (!latitude || !longitude) return res.json({ ok: false, error: 'Coords required' });

        const geonamesUrl = `http://api.geonames.org/timezoneJSON?lat=${latitude}&lng=${longitude}&username=demo`;
        const response = await fetch(geonamesUrl).then(r => r.json());

        res.json({
            ok: true, timezone: response.timezoneId,
            gmtOffset: response.gmtOffset, dstOffset: response.dstOffset
        });
    } catch (error) {
        res.json({ ok: false, error: 'Failed' });
    }
};
