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

        // Rule 3: Add ₹80 difference payout (188 - 108)
        user.walletBalance = (user.walletBalance || 0) + 80;
        user.referredBy = referrer.userId;
        await user.save();

        res.json({ ok: true, message: 'Referral code applied! ₹80 bonus added.', walletBalance: user.walletBalance });
    } catch (e) {
        console.error(e);
        res.status(500).json({ ok: false, error: 'Server error' });
    }
};
