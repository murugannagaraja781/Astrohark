const User = require('../models/User');
const ChatMessage = require('../models/ChatMessage');
const { formatImageUrl } = require('../utils/formatImage');

exports.getProfile = async (req, res) => {
    try {
        const user = await User.findOne({ userId: req.params.userId });
        if (!user) return res.status(404).json({ ok: false, error: 'User not found' });
        
        const SERVER_URL = req.app.get('SERVER_URL');
        res.json({ 
            ok: true, 
            ...user._doc, 
            image: formatImageUrl(user.image, user.name, SERVER_URL)
        });
    } catch (e) {
        res.status(500).json({ ok: false });
    }
};

exports.getChatHistory = async (req, res) => {
    try {
        const messages = await ChatMessage.find({ sessionId: req.params.sessionId }).sort({ timestamp: 1 });
        res.json({ ok: true, messages });
    } catch (e) {
        res.status(500).json({ ok: false });
    }
};

exports.applyReferral = async (req, res) => {
    try {
        const { userId, referralCode } = req.body;
        if (!userId || !referralCode) return res.json({ ok: false, error: 'Missing required data' });

        const user = await User.findOne({ userId });
        if (!user) return res.json({ ok: false, error: 'User not found' });
        if (user.referredBy) return res.json({ ok: false, error: 'Referral already applied' });

        const referrer = await User.findOne({ referralCode: referralCode.trim().toUpperCase() });
        if (!referrer) return res.json({ ok: false, error: 'Invalid referral code' });
        if (referrer.userId === userId) return res.json({ ok: false, error: 'Cannot refer yourself' });

        const referralSignupBonus = parseInt(process.env.REFERRAL_SIGNUP_BONUS) || 188;
        const newUserSignupBonus = parseInt(process.env.NEW_USER_SIGNUP_BONUS) || 0;
        const refereeBonus = referralSignupBonus - newUserSignupBonus;
        user.walletBalance = (user.walletBalance || 0) + refereeBonus;
        user.referredBy = referrer.userId;
        await user.save();

        res.json({ ok: true, message: `Referral code applied! ₹${refereeBonus} bonus added.`, walletBalance: user.walletBalance });
    } catch (e) {
        console.error(e);
        res.status(500).json({ ok: false, error: 'Server error' });
    }
};

exports.getBirthData = async (req, res) => {
    try {
        const user = await User.findOne({ userId: req.params.userId });
        if (!user) {
            return res.status(404).json({ success: false, error: 'User not found' });
        }

        const bd = user.birthDetails || {};
        const id = user.intakeDetails || {};

        let year = 0, month = 0, day = 0;
        if (bd.dob) {
            const parts = bd.dob.split('-');
            if (parts.length === 3) {
                year = parseInt(parts[0]) || 0;
                month = parseInt(parts[1]) || 0;
                day = parseInt(parts[2]) || 0;
            }
        }

        let hour = 12, minute = 0;
        if (bd.tob) {
            const parts = bd.tob.split(':');
            if (parts.length >= 2) {
                hour = parseInt(parts[0]) || 0;
                minute = parseInt(parts[1]) || 0;
            }
        }

        let partnerData = undefined;
        if (id.partner && id.partner.name) {
            let pYear = 0, pMonth = 0, pDay = 0;
            if (id.partner.dob) {
                const parts = id.partner.dob.split('-');
                if (parts.length === 3) {
                    pYear = parseInt(parts[0]) || 0;
                    pMonth = parseInt(parts[1]) || 0;
                    pDay = parseInt(parts[2]) || 0;
                }
            }

            let pHour = 12, pMinute = 0;
            if (id.partner.tob) {
                const parts = id.partner.tob.split(':');
                if (parts.length >= 2) {
                    pHour = parseInt(parts[0]) || 0;
                    pMinute = parseInt(parts[1]) || 0;
                }
            }

            partnerData = {
                name: id.partner.name,
                gender: id.gender === 'Male' ? 'Female' : 'Male',
                day: pDay,
                month: pMonth,
                year: pYear,
                hour: pHour,
                minute: pMinute,
                city: id.partner.pob || '',
                latitude: bd.lat || 13.0827,
                longitude: bd.lon || 80.2707,
                timezone: 'Asia/Kolkata'
            };
        }

        const birthData = {
            name: user.name || 'User',
            gender: id.gender || user.gender || 'Male',
            day: day,
            month: month,
            year: year,
            hour: hour,
            minute: minute,
            city: bd.pob || user.pob || '',
            latitude: bd.lat || 13.0827,
            longitude: bd.lon || 80.2707,
            timezone: 'Asia/Kolkata',
            isMatching: !!partnerData,
            partnerData: partnerData
        };

        res.json({
            success: true,
            birthData: birthData
        });
    } catch (err) {
        console.error('getBirthData error:', err);
        res.status(500).json({ success: false, error: err.message });
    }
};
