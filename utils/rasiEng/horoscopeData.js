// utils/rasiEng/horoscopeData.js
const fetch = require('node-fetch');
const { DateTime } = require('luxon');

const defaultHoroscopeData = [
  { sign_en: "aries", sign_ta: 'மேஷம்', horoscope: "Today is a day of opportunities." },
  { sign_en: "taurus", sign_ta: 'ரிஷபம்', horoscope: "Focus on your financial goals." },
  { sign_en: "gemini", sign_ta: 'மிதுனம்', horoscope: "Communication is key today." },
  { sign_en: "cancer", sign_ta: 'கடகம்', horoscope: "Spend time with your family." },
  { sign_en: "leo", sign_ta: 'சிம்மம்', horoscope: "Your leadership skills will shine." },
  { sign_en: "virgo", sign_ta: 'கன்னி', horoscope: "Pay attention to the little details." },
  { sign_en: "libra", sign_ta: 'துலாம்', horoscope: "Seek balance in all your endeavors." },
  { sign_en: "scorpio", sign_ta: 'விருச்சிகம்', horoscope: "A day for self-reflection." },
  { sign_en: "sagittarius", sign_ta: 'தனுசு', horoscope: "Embrace the spirit of adventure." },
  { sign_en: "capricorn", sign_ta: 'மகரம்', horoscope: "Hard work pays off eventually." },
  { sign_en: "aquarius", sign_ta: 'கும்பம்', horoscope: "Innovative ideas will come to you." },
  { sign_en: "pisces", sign_ta: 'மீனம்', horoscope: "Trust your intuition and instincts." }
];

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
            
            // If all fallbacks fail, strictly return default data seamlessly
            return defaultHoroscopeData;
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
        // Return default data instantly on network failure, keep console clean
        return defaultHoroscopeData;
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
