const User = require('../models/User');
const { broadcastAstroUpdate } = require('../services/astrologer.service');

exports.register = async (req, res) => {
    try {
        const {
            name, phone, // legacy/legacy-fallback
            realName, displayName,
            gender, dob, tob, pob,
            phone1, phone2, whatsapp,
            email, address,
            aadhar, pan,
            experience, profession,
            bankDetails, upiName, upiId
        } = req.body;

        const finalPhone = phone1 || phone;
        const finalName = displayName || name || realName;
        const finalRealName = realName || finalName;

        if (!finalName || !finalPhone) {
            return res.status(400).json({ ok: false, error: 'Registration failed: Name and Phone are required' });
        }

        const existing = await User.findOne({ phone: finalPhone });
        if (existing) {
            return res.json({ ok: false, error: 'This phone number is already registered' });
        }

        const userId = 'ASTRO_' + Date.now() + Math.floor(Math.random() * 1000);
        
        const newUser = new User({
            userId,
            phone: finalPhone,
            name: finalName,
            realName: finalRealName,
            email: email || '',
            gender: gender || '',
            dob: dob || '',
            tob: tob || '',
            pob: pob || '',
            cellNumber2: phone2 || '',
            whatsAppNumber: whatsapp || '',
            address: address || '',
            aadharNumber: aadhar || '',
            panNumber: pan || '',
            astrologyExperience: experience || '',
            profession: profession || '',
            bankDetails: bankDetails || '',
            upiNumber: upiName || '', // Mapping upiName to upiNumber in schema context
            upiId: upiId || '',
            role: 'astrologer',
            approvalStatus: 'pending',
            isVerified: false,
            isAvailable: false, 
            isOnline: false,
            walletBalance: 0,
            totalEarnings: 0,
            ratePerMinute: 10, // Default rate
            price: 10 // Sync with ratePerMinute
        });

        await newUser.save();
        console.log(`[Registration] New Astrologer: ${finalName} (${userId}) - Status: Pending Approval`);
        res.json({ ok: true, message: 'Registration submitted successfully. Awaiting admin approval.' });
    } catch (error) {
        console.error('Registration Error:', error);
        res.status(500).json({ ok: false, error: 'Deep server error during registration' });
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
