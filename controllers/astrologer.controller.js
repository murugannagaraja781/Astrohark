const User = require('../models/User');
const { broadcastAstroUpdate } = require('../services/astrologer.service');

exports.register = async (req, res) => {
    try {
        const { name, phone, email, experience, price, skills, bio } = req.body;
        if (!name || !phone) return res.status(400).json({ ok: false, error: 'Mandatory fields missing' });

        const existing = await User.findOne({ phone });
        if (existing) return res.json({ ok: false, error: 'Phone already registered' });

        const userId = 'ASTRO_' + Date.now() + Math.floor(Math.random() * 1000);
        const newUser = new User({
            userId, phone, email, name, realName: name,
            astrologyExperience: experience, ratePerMinute: price,
            profession: skills, bio, role: 'astrologer',
            approvalStatus: 'pending', isVerified: false,
            isAvailable: false, isOnline: false,
            walletBalance: 0, totalEarnings: 0
        });

        await newUser.save();
        console.log(`[Registration] New Astrologer: ${name} (${userId})`);
        res.json({ ok: true });
    } catch (error) {
        console.error(error);
        res.status(500).json({ ok: false, error: 'Server error' });
    }
};

exports.toggleOnline = async (req, res) => {
    const { userId, available, fcmToken } = req.body;
    if (!userId) return res.json({ ok: false, error: 'Missing userId' });
    const io = req.app.get('io');
    const SERVER_URL = req.app.get('SERVER_URL');

    try {
        const user = await User.findOne({ userId });
        if (!user || user.role !== 'astrologer') return res.json({ ok: false, error: 'Access denied' });
        if (user.approvalStatus !== 'approved') return res.json({ ok: false, error: 'Awaiting approval' });

        const update = {
            isAvailable: available, isOnline: available,
            isChatOnline: available, isAudioOnline: available, isVideoOnline: available,
            lastSeen: new Date()
        };
        if (fcmToken) update.fcmToken = fcmToken;

        await User.updateOne({ userId }, update);
        broadcastAstroUpdate(io, SERVER_URL);
        res.json({ ok: true });
    } catch (e) {
        console.error(e);
        res.json({ ok: false });
    }
};

exports.toggleService = async (req, res) => {
    const { userId, service, enabled } = req.body;
    if (!userId || !service) return res.json({ ok: false, error: 'Missing params' });
    const io = req.app.get('io');
    const SERVER_URL = req.app.get('SERVER_URL');

    try {
        const update = { lastSeen: new Date() };
        if (service === 'chat') update.isChatOnline = enabled;
        else if (service === 'audio') update.isAudioOnline = enabled;
        else if (service === 'video') update.isVideoOnline = enabled;

        const user = await User.findOne({ userId });
        if (user) {
            const chatOn = service === 'chat' ? enabled : user.isChatOnline;
            const audioOn = service === 'audio' ? enabled : user.isAudioOnline;
            const videoOn = service === 'video' ? enabled : user.isVideoOnline;
            update.isOnline = chatOn || audioOn || videoOn;
            update.isAvailable = update.isOnline; // Sync

            await User.updateOne({ userId }, update);
            broadcastAstroUpdate(io, SERVER_URL);
            res.json({ ok: true });
        } else {
            res.json({ ok: false, error: 'User not found' });
        }
    } catch (e) {
        console.error(e);
        res.json({ ok: false });
    }
};
