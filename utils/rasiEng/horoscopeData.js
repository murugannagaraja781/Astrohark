// utils/rasiEng/horoscopeData.js
const fetch = require('node-fetch');
const { DateTime } = require('luxon');

const defaultHoroscopeData = [
  { sign_en: "aries", sign_ta: 'மேஷம்', prediction_ta: "இன்றைய நாள் மேஷ ராசிக்கு சிறப்பாக அமையும். பொருளாதார நிலை உயரும்.", horoscope: "Today is a day of opportunities.", lucky_time: "காலை 9:30 - 10:30", unlucky_time: "மதியம் 1:30 - 3:00" },
  { sign_en: "taurus", sign_ta: 'ரிஷபம்', prediction_ta: "ரிஷப ராசிக்கு இன்று நிதானமாக இருக்க வேண்டிய நாள். வரவுக்கும் செலவுக்கும் சரியாக இருக்கும்.", horoscope: "Focus on your financial goals.", lucky_time: "காலை 10:30 - 11:30", unlucky_time: "காலை 7:30 - 9:00" },
  { sign_en: "gemini", sign_ta: 'மிதுனம்', prediction_ta: "மிதுன ராசிக்கு இன்று பேச்சுவார்த்தை வெற்றி தரும். உறவினர்களிடம் அன்பு கிடைக்கும்.", horoscope: "Communication is key today.", lucky_time: "மதியம் 12:00 - 1:00", unlucky_time: "காலை 10:30 - 12:00" },
  { sign_en: "cancer", sign_ta: 'கடகம்', prediction_ta: "கடக ராசிக்கு இன்று குடும்பத்தில் மகிழ்ச்சி நிலவும். மன அமைதி கிடைக்கும்.", horoscope: "Spend time with your family.", lucky_time: "மாலை 4:30 - 5:30", unlucky_time: "மதியம் 12:00 - 1:30" },
  { sign_en: "leo", sign_ta: 'சிம்மம்', prediction_ta: "சிம்ம ராசிக்கு இன்று தைரியம் கூடும். புதிய முயற்சிகள் வெற்றியைத் தரும்.", horoscope: "Your leadership skills will shine.", lucky_time: "இரவு 7:00 - 8:00", unlucky_time: "மதியம் 3:00 - 4:30" },
  { sign_en: "virgo", sign_ta: 'கன்னி', prediction_ta: "கன்னி ராசிக்கு இன்று பொறுமை தேவை. எதையும் ஒருமுறைக்கு இருமுறை யோசித்து செய்க.", horoscope: "Pay attention to the little details.", lucky_time: "காலை 6:00 - 7:30", unlucky_time: "காலை 9:00 - 10:30" },
  { sign_en: "libra", sign_ta: 'துலாம்', prediction_ta: "துலாம் ராசிக்கு இன்று நன்மைகள் உண்டாகும். பல வழிகளிலும் ஆதாயம் கிட்டும்.", horoscope: "Seek balance in all your endeavors.", lucky_time: "மதியம் 2:00 - 3:00", unlucky_time: "மாலை 4:30 - 6:00" },
  { sign_en: "scorpio", sign_ta: 'விருச்சிகம்', prediction_ta: "விருச்சிக ராசிக்கு இன்று மனநிறைவு தரும் நாள். தடைப்பட்ட காரியங்கள் கைகூடும்.", horoscope: "A day for self-reflection.", lucky_time: "காலை 8:00 - 9:00", unlucky_time: "மதியம் 1:30 - 3:00" },
  { sign_en: "sagittarius", sign_ta: 'தனுசு', prediction_ta: "தனுசு ராசிக்கு இன்று வெற்றி வாய்ப்புகள் தேடி வரும். பெரியவர்களின் ஆசி கிடைக்கும்.", horoscope: "Embrace the spirit of adventure.", lucky_time: "மதியம் 1:00 - 2:00", unlucky_time: "காலை 10:30 - 12:00" },
  { sign_en: "capricorn", sign_ta: 'மகரம்', prediction_ta: "மகர ராசிக்கு இன்று கடின உழைப்புக்குரிய பலன் கிடைக்கும். நிதானம் அவசியம்.", horoscope: "Hard work pays off eventually.", lucky_time: "மாலை 5:00 - 6:00", unlucky_time: "காலை 7:30 - 9:00" },
  { sign_en: "aquarius", sign_ta: 'கும்பம்', prediction_ta: "கும்ப ராசிக்கு இன்று புதிய சிந்தனைகள் உருவாகும். லாபகரமான நாளாக அமையும்.", horoscope: "Innovative ideas will come to you.", lucky_time: "இரவு 8:00 - 9:00", unlucky_time: "மதியம் 12:00 - 1:30" },
  { sign_en: "pisces", sign_ta: 'மீனம்', prediction_ta: "மீன ராசிக்கு இன்று மகிழ்ச்சியான செய்திகள் வந்து சேரும். காரிய வெற்றி உண்டாகும்.", horoscope: "Trust your intuition and instincts.", lucky_time: "காலை 7:00 - 8:00", unlucky_time: "மதியம் 3:00 - 4:30" }
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
