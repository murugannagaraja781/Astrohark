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
const GlobalSettings = require('../models/GlobalSettings');
const User = require('../models/User');
const Feedback = require('../models/Feedback');
const { broadcastAstroUpdate } = require('../services/astrologer.service');

exports.getAppConfig = async (req, res) => {
    try {
        const shareLinkRecord = await GlobalSettings.findOne({ key: 'shareLink' });
        res.json({
            ok: true,
            config: {
                shareLink: process.env.PLAYSTORE_URL || (shareLinkRecord ? shareLinkRecord.value : "https://play.google.com/store/apps/details?id=com.astrohark.app"),
                deepLinkPrefix: process.env.DEEP_LINK_PREFIX || "astrohark://referral/",
                showBanner: process.env.SHOW_BANNER === 'true',
                appBackgroundColor: process.env.APP_BG_COLOR || "#FEF9F3",
                referralText: process.env.REFERRAL_TEXT || "Refer Your Friend & Earn Upto ₹5000",
                shareBannerUrl: process.env.SHARE_BANNER_URL || "",
                initialWalletBalance: parseInt(process.env.NEW_USER_SIGNUP_BONUS) || 0,
                referralSignupBonus: parseInt(process.env.REFERRAL_SIGNUP_BONUS) || 188,
                referrerRechargeBonus: parseInt(process.env.REFERRER_RECHARGE_BONUS) || 81
            }
        });
    } catch (error) {
        res.json({ ok: false, error: 'Failed to fetch config' });
    }
};

exports.submitReview = async (req, res) => {
    try {
        const { sessionId, clientId, astrologerId, rating, comment } = req.body;
        if (!clientId || !astrologerId || !rating || !comment) {
            return res.status(400).json({ ok: false, error: 'Mandatory fields missing' });
        }

        const client = await User.findOne({ userId: clientId });
        const astrologer = await User.findOne({ userId: astrologerId });

        if (!client) return res.status(404).json({ ok: false, error: 'Client not found' });
        if (!astrologer) return res.status(404).json({ ok: false, error: 'Astrologer not found' });

        let sessionType = 'call';
        if (sessionId) {
            const Session = require('../models/Session');
            const sessionObj = await Session.findOne({ sessionId });
            if (sessionObj) {
                sessionType = sessionObj.type || 'call';
            }
        }

        const fb = await Feedback.create({
            userId: clientId,
            userName: client.name || 'User',
            astrologerId,
            astrologerName: astrologer.name || 'Astrologer',
            rating: parseInt(rating) || 5,
            comment,
            sessionType
        });

        // Recalculate average rating
        const feedbacks = await Feedback.find({ astrologerId });
        let avgRating = 5.0;
        if (feedbacks.length > 0) {
            const total = feedbacks.reduce((acc, f) => acc + (f.rating || 5), 0);
            avgRating = parseFloat((total / feedbacks.length).toFixed(1));
        } else {
            avgRating = parseInt(rating) || 5.0;
        }

        astrologer.rating = avgRating;
        await astrologer.save();

        // Broadcast astrologer update
        const io = req.app.get('io');
        if (io) {
            broadcastAstroUpdate(io, process.env.SERVER_URL);
        }

        // Send email notification (optional)
        try {
            const { sendFeedbackEmail } = require('../services/email.service');
            sendFeedbackEmail(fb).catch(err => console.error("Email send failed:", err));
        } catch (err) {}

        res.json({ ok: true, feedback: fb });
    } catch (error) {
        console.error('Error submitting review REST API:', error);
        res.status(500).json({ ok: false, error: 'Internal server error' });
    }
};

