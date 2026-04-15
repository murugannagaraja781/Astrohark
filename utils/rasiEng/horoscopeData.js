// utils/rasiEng/horoscopeData.js
const fetch = require('node-fetch');
const { DateTime } = require('luxon');

const BASE_URL = 'https://raw.githubusercontent.com/abinash818/daily-horoscope-data/main/data';

// Simple in-memory cache
const cache = new Map();

/**
 * Fetch daily horoscope data for a specific date
 * @param {string} date - ISO date string (YYYY-MM-DD)
 */
async function fetchDailyHoroscope(date) {
    const fileName = `horoscope_${date}.json`;
    const url = `${BASE_URL}/${fileName}`;

    // Check cache first
    if (cache.has(date)) {
        return cache.get(date);
    }

    try {
        const response = await fetch(url);
        if (!response.ok) {
            console.warn(`[Horoscope] Failed to fetch for ${date}: ${response.status} ${response.statusText}`);
            
            // Try up to 3 previous days in sequence
            for (let i = 1; i <= 3; i++) {
                const retryDate = DateTime.fromISO(date).minus({ days: i }).toFormat('yyyy-MM-dd');
                console.log(`[Horoscope] Attempting fallback to ${retryDate} (Attempt ${i}/3)`);
                const fallbackUrl = `${BASE_URL}/horoscope_${retryDate}.json`;
                const fallbackRes = await fetch(fallbackUrl);
                
                if (fallbackRes.ok) {
                    let data = await fallbackRes.json();
                    if (Array.isArray(data) && data[0] && data[0].content && data[0].content.parts) {
                        let text = data[0].content.parts[0].text;
                        text = text.replace(/```json\n?|```/g, '').trim();
                        try {
                            data = JSON.parse(text);
                        } catch (e) {
                            console.error(`Failed to parse fallback JSON for ${retryDate}:`, e);
                            continue; // Try next day
                        }
                    }
                    cache.set(date, data);
                    return data;
                }
            }
            console.warn(`[Horoscope] All fallbacks failed. Returning default data.`);
            return require('./defaultHoroscopeData')();
        }

        let data = await response.json();

        // Handle Gemini API response format
        if (Array.isArray(data) && data[0] && data[0].content && data[0].content.parts) {
            let text = data[0].content.parts[0].text;
            // Remove markdown code blocks if present
            text = text.replace(/```json\n?|```/g, '').trim();
            try {
                data = JSON.parse(text);
            } catch (e) {
                console.error('Failed to parse inner JSON from Gemini response:', e);
                return null;
            }
        }

        // Cache the data
        cache.set(date, data);

        // Strategy: Clear cache for dates older than 2 days to prevent memory leaks
        if (cache.size > 5) {
            const keys = Array.from(cache.keys()).sort();
            while (cache.size > 5) {
                cache.delete(keys.shift());
            }
        }

        return data;
    } catch (error) {
        console.error('Error fetching horoscope data:', error.message);
        return null; // Graceful failure
    }
}

/**
 * Get horoscope for a specific sign from the day's data
 * @param {Array} dayData - Array of 12 sign objects
 * @param {string} sign - Rasi name (English)
 */
function getSignHoroscope(dayData, sign) {
    if (!dayData || !Array.isArray(dayData) || !sign) return null;

    const searchSign = sign.toLowerCase();

    // Support both English and Tamil sign names in query
    return dayData.find(item =>
        (item.sign_en && item.sign_en.toLowerCase() === searchSign) ||
        (item.sign_ta && item.sign_ta === sign)
    );
}

module.exports = {
    fetchDailyHoroscope,
    getSignHoroscope
};
